package tutorengine.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import tutorengine.db.Db;
import tutorengine.intake.WorkingBuffer;
import tutorengine.intake.WorkingBuffer.WorkingItem;
import tutorengine.model.CardInstance;
import tutorengine.storage.StorageScaffolding.NeighborBreadcrumb;
import tutorengine.storage.StorageScaffolding.StorageBatch;

// Comprehensive end-to-end verification harness for the complete intake-to-shelf lifecycle:
// Working Buffer -> Bulk Edit & Grouping -> Staging Review -> BATCH Commit ->
// Destination Clustering Cascade -> In-Hand Pre-Sort -> 2D Shelf Coordinate Resolution ->
// Virtual In-Box Breadcrumbs -> Batch Storage Confirmation (tutorengine_implementation_specification.md §3-§4).
public class LifecycleHarness {

    public static void runVerification() throws Exception {
        Path tmpDb = Files.createTempFile("tutorengine-lifecycle-test-", ".db");
        System.setProperty("tutorengine.db", tmpDb.toString());
        Db.migrate();
        System.out.println("=== TUTORENGINE INTAKE-TO-SHELF LIFECYCLE VERIFICATION ===");
        System.out.println("Database initialized at: " + tmpDb);

        // Step 1: Working Buffer Ingestion
        System.out.println("\n[Step 1] Ingesting cards into Working Buffer...");
        WorkingBuffer wb = new WorkingBuffer();
        wb.addItem(new WorkingItem("c1", "Ajani, Caller of the Pride", "M14", "1", "mythic", "NM", "nonfoil", 1));
        wb.addItem(new WorkingItem("c2", "Black Cat", "M14", "84", "common", "HP", "nonfoil", 1));
        wb.addItem(new WorkingItem("c2", "Black Cat", "M14", "84", "common", "HP", "nonfoil", 2)); // duplicate scan
        wb.addItem(new WorkingItem("c3", "Chandra, Pyromaster", "M14", "132", "mythic", "NM", "nonfoil", 1));
        wb.addItem(new WorkingItem("c4", "Plains", "M14", "250", "common", "LP", "nonfoil", 4));
        wb.addItem(new WorkingItem("c5", "Custom Unknown Card", "XYZ", "99", "rare", "NM", "nonfoil", 1));

        check("Buffer size before consolidation", wb.getBuffer().size(), 5);

        // Step 2: Duplicate Grouping & Multi-Select Bulk Editing
        System.out.println("\n[Step 2] Testing duplicate grouping and bulk attribute editing...");
        wb.groupDuplicates();
        // Index 1 is Black Cat (should have consolidated to qty 3)
        check("Consolidated Black Cat quantity", wb.getBuffer().get(1).getQuantity(), 3);

        // Bulk edit condition on Black Cat from HP to NM
        wb.applyBulkCondition(List.of(1), "NM");
        check("Bulk edited Black Cat condition", wb.getBuffer().get(1).getCondition(), "NM");

        // Step 3: Staging Review and Automated Commit
        System.out.println("\n[Step 3] Staging items and generating automated batch commit...");
        wb.stageAll();
        check("Buffer cleared after stageAll", wb.getBuffer().size(), 0);
        check("Staging populated", wb.getStaging().size(), 5);

        int committed = wb.commitStaged();
        System.out.println("  Committed cards: " + committed);
        check("Committed card count", committed, 10);
        check("Staging area cleared after commit", wb.getStaging().size(), 0);

        // Verify SQLite state: card_instances (UNSTORED)
        int unstoredCount = 0;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM card_instances WHERE status = 'UNSTORED'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) unstoredCount = rs.getInt(1);
        }
        // Total cards: 1 (Ajani) + 3 (Black Cat) + 1 (Chandra) + 4 (Plains) + 1 (Custom) = 10 cards
        check("Persisted UNSTORED card_instances in DB", unstoredCount, 10);

        // Step 4: Destination Clustering Cascade (Trip Minimization)
        System.out.println("\n[Step 4] Clustering unstored cards into destination batches...");
        List<StorageBatch> batches = StorageScaffolding.clusterUnstored();
        System.out.println("  Clustered batch count: " + batches.size());
        for (StorageBatch b : batches) {
            System.out.println("    " + b);
        }
        // Expecting:
        // - Plains -> CAB-LANDS-LOW (4 cards)
        // - M14 cards (Ajani, Black Cat, Chandra) -> RACK-M-TOP (5 cards)
        // - XYZ card (rare) -> CAB-RARE-LOW (1 card, newBoxRequired = true, suggested = XYZ ①)
        check("Number of destination batches", batches.size(), 3);

        StorageBatch landsBatch = findBatch(batches, "CAB-LANDS-LOW");
        check("Lands batch card count", landsBatch.getCards().size(), 4);
        check("Lands shelf unit", landsBatch.getShelfUnit(), "White Cabinet");
        check("Lands shelf tier", landsBatch.getShelfTier(), "LOW");

        StorageBatch m14Batch = findBatch(batches, "RACK-M-TOP");
        check("M14 batch card count", m14Batch.getCards().size(), 5);
        check("M14 shelf unit", m14Batch.getShelfUnit(), "Metal Wire Rack");
        check("M14 shelf tier", m14Batch.getShelfTier(), "TOP");

        StorageBatch newSetBatch = findBatch(batches, "CAB-RARE-LOW");
        check("New set newBoxRequired flag", newSetBatch.isNewBoxRequired(), true);
        check("New set suggestedBoxId", newSetBatch.getSuggestedBoxId(), "XYZ ①");

        // Step 5: In-Hand Forward-Pass Pre-Sorting Directive
        System.out.println("\n[Step 5] Enforcing in-hand forward-pass ordering (Name ASC)...");
        m14Batch.sortInHandForwardPass();
        List<CardInstance> sortedCards = m14Batch.getCards();
        System.out.println("  In-hand card sequence:");
        for (CardInstance ci : sortedCards) {
            System.out.println("    - " + ci.getName() + " (" + ci.getSetCode() + " #" + ci.getCollectorNumber() + ")");
        }
        check("First card in hand", sortedCards.get(0).getName(), "Ajani, Caller of the Pride");
        check("Middle cards in hand", sortedCards.get(1).getName(), "Black Cat");
        check("Last card in hand", sortedCards.get(4).getName(), "Chandra, Pyromaster");

        // Step 6: Visual In-Box Stack Scaffolding & Alphabetical Neighbor Breadcrumbs
        System.out.println("\n[Step 6] Calculating alphabetical neighbor breadcrumbs...");

        // Initially RACK-M-TOP is empty of STORED cards
        NeighborBreadcrumb bEmpty = StorageScaffolding.calculateBreadcrumb("RACK-M-TOP", "Ajani, Caller of the Pride");
        System.out.println("  Empty box breadcrumb: " + bEmpty.getBreadcrumbText());
        check("Empty box breadcrumb text", bEmpty.getBreadcrumbText(), "First card in box (empty container)");

        // Now store Ajani and Chandra into RACK-M-TOP to test boundary conditions
        List<String> ajaniIds = List.of(sortedCards.get(0).getId());
        List<String> chandraIds = List.of(sortedCards.get(4).getId());
        StorageScaffolding.confirmBatchStored("RACK-M-TOP", ajaniIds);
        StorageScaffolding.confirmBatchStored("RACK-M-TOP", chandraIds);

        // Test insertion between Ajani and Chandra (e.g. Black Cat)
        NeighborBreadcrumb bBetween = StorageScaffolding.calculateBreadcrumb("RACK-M-TOP", "Black Cat");
        System.out.println("  Between breadcrumb: " + bBetween.getBreadcrumbText());
        if (!bBetween.getBreadcrumbText().contains("Ajani, Caller of the Pride")
                || !bBetween.getBreadcrumbText().contains("Chandra, Pyromaster")) {
            throw new AssertionError("Expected between Ajani and Chandra: " + bBetween.getBreadcrumbText());
        }

        // Test insertion before Ajani (e.g. Abzan Battle Priest)
        NeighborBreadcrumb bBefore = StorageScaffolding.calculateBreadcrumb("RACK-M-TOP", "Abzan Battle Priest");
        System.out.println("  Before start breadcrumb: " + bBefore.getBreadcrumbText());
        if (!bBefore.getBreadcrumbText().startsWith("Insert before \"Ajani")) {
            throw new AssertionError("Expected insert before start: " + bBefore.getBreadcrumbText());
        }

        // Test insertion after Chandra (e.g. Doom Blade)
        NeighborBreadcrumb bAfter = StorageScaffolding.calculateBreadcrumb("RACK-M-TOP", "Doom Blade");
        System.out.println("  After end breadcrumb: " + bAfter.getBreadcrumbText());
        if (!bAfter.getBreadcrumbText().startsWith("Insert after \"Chandra")) {
            throw new AssertionError("Expected insert after end: " + bAfter.getBreadcrumbText());
        }

        // Test identical copy (e.g. Ajani, Caller of the Pride)
        NeighborBreadcrumb bIdentical = StorageScaffolding.calculateBreadcrumb("RACK-M-TOP", "Ajani, Caller of the Pride");
        System.out.println("  Identical copy breadcrumb: " + bIdentical.getBreadcrumbText());
        if (!bIdentical.getBreadcrumbText().contains("identical copy")) {
            throw new AssertionError("Expected identical copy breadcrumb: " + bIdentical.getBreadcrumbText());
        }

        // Step 7: Batch Placement Confirmation
        System.out.println("\n[Step 7] Confirming complete batch storage placement...");
        // Store remaining Black Cat cards
        List<String> blackCatIds = List.of(sortedCards.get(1).getId(), sortedCards.get(2).getId(), sortedCards.get(3).getId());
        int confirmed = StorageScaffolding.confirmBatchStored("RACK-M-TOP", blackCatIds);
        check("Confirmed Black Cat stored count", confirmed, 3);

        // Confirm Lands batch
        List<String> landIds = new ArrayList<>();
        for (CardInstance ci : landsBatch.getCards()) landIds.add(ci.getId());
        int confirmedLands = StorageScaffolding.confirmBatchStored("CAB-LANDS-LOW", landIds);
        check("Confirmed Lands stored count", confirmedLands, 4);

        // Confirm New Set batch
        List<String> newSetIds = new ArrayList<>();
        for (CardInstance ci : newSetBatch.getCards()) newSetIds.add(ci.getId());
        int confirmedNew = StorageScaffolding.confirmBatchStored("CAB-RARE-LOW", newSetIds);
        check("Confirmed New Set stored count", confirmedNew, 1);

        // Verify remaining UNSTORED in DB
        int remainingUnstored = 0;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM card_instances WHERE status = 'UNSTORED'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) remainingUnstored = rs.getInt(1);
        }
        check("Remaining UNSTORED in database", remainingUnstored, 0);

        // Verify total STORED in DB
        int totalStored = 0;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM card_instances WHERE status = 'STORED'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) totalStored = rs.getInt(1);
        }
        check("Total STORED in database", totalStored, 10);

        // Verify updated box counts in box_containers table
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT box_id, current_card_count FROM box_containers WHERE current_card_count > 0");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                System.out.println("  Box " + rs.getString(1) + " count in DB: " + rs.getInt(2));
            }
        }

        Files.deleteIfExists(tmpDb);
        System.out.println("\n=======================================================");
        System.out.println("✓ ALL 7 INTAKE-TO-SHELF LIFECYCLE PHASES PASSED VERIFICATION!");
        System.out.println("=======================================================");
    }

    private static StorageBatch findBatch(List<StorageBatch> batches, String boxId) {
        for (StorageBatch b : batches) {
            if (b.getBoxId().equals(boxId)) return b;
        }
        throw new AssertionError("Batch with boxId " + boxId + " not found!");
    }

    private static void check(String label, Object actual, Object expected) {
        if (!actual.equals(expected)) {
            throw new AssertionError(label + " FAIL: expected <" + expected + "> but got <" + actual + ">");
        }
        System.out.println("  ✓ " + label + ": " + actual);
    }

    public static void main(String[] args) {
        try {
            runVerification();
        } catch (Throwable t) {
            System.err.println("Verification Failed: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }
}
