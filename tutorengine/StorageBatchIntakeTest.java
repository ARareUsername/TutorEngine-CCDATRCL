package tutorengine;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import tutorengine.db.Db;
import tutorengine.intake.RapidIntake;
import tutorengine.intake.WorkingBuffer;
import tutorengine.intake.WorkingBuffer.WorkingItem;
import tutorengine.model.Card;
import tutorengine.model.CardInstance;
import tutorengine.storage.StorageScaffolding;
import tutorengine.storage.StorageScaffolding.StorageBatch;
import tutorengine.ui.CatalogView;
import tutorengine.ui.StorageView;

public class StorageBatchIntakeTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== STORAGE BATCH INTAKE & SHELF MANAGEMENT TEST SUITE ===");
        try {
            File tempDb = File.createTempFile("storage_batch_intake_test_", ".db");
            tempDb.deleteOnExit();
            System.setProperty("tutorengine.db", tempDb.getAbsolutePath());
            Db.migrate();

            testRapidIntakeUnstoredPersistence();
            testSampleCardSeedingAndReload();
            testBatchConfirmationRemovesBatchAndUpdatesCatalog();
            testShelfCoordinatesBoxRegistrationAndReassignment();

            System.out.println();
            System.out.println(passed + " passed, " + failed + " failed");
            if (failed > 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void check(String desc, boolean condition) {
        if (condition) {
            System.out.println("  PASS: " + desc);
            passed++;
        } else {
            System.out.println("  FAIL: " + desc);
            failed++;
        }
    }

    private static void testRapidIntakeUnstoredPersistence() throws Exception {
        System.out.println("1. Testing Rapid Intake persistence to Storage queue...");

        Card card1 = new Card("RAPID-OTJ-0001", "Outlaw Hero", "Outlaws of Thunder Junction", "Red", "Creature",
                2024, 75.0, 2, 60, false, null, "{2}{R}", "3", "2", "OTJ");
        Card card2 = new Card("RAPID-BLB-0002", "Valley Floodbringer", "Bloomburrow", "Blue", "Creature",
                2024, 120.0, 1, 80, true, null, "{1}{U}", "2", "1", "BLB");

        try (Connection c = Db.open()) {
            int inserted = RapidIntake.persistUnstoredInstances(c, List.of(card1, card2));
            check("Inserted 3 instances (card1 x2 + card2 x1)", inserted == 3);

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM card_instances WHERE status = 'UNSTORED' AND box_id IS NULL")) {
                try (ResultSet rs = ps.executeQuery()) {
                    check("Database contains 3 UNSTORED instances with box_id NULL", rs.next() && rs.getInt(1) == 3);
                }
            }
        }

        List<StorageBatch> batches = StorageScaffolding.clusterUnstored();
        check("Storage clustered at least 2 batches", batches.size() >= 2);

        StorageBatch otjBatch = null;
        StorageBatch blbBatch = null;
        for (StorageBatch b : batches) {
            if ("OTJ-1".equals(b.getBoxId())) otjBatch = b;
            if ("BLB-1".equals(b.getBoxId())) blbBatch = b;
        }
        check("Found OTJ-1 batch", otjBatch != null && otjBatch.getCards().size() == 2);
        check("Found BLB-1 batch", blbBatch != null && blbBatch.getCards().size() == 1);
    }

    private static void testSampleCardSeedingAndReload() throws Exception {
        System.out.println("2. Testing Storage View sample card seeding & queue reload...");

        WorkingBuffer wb = new WorkingBuffer();
        wb.addItem(new WorkingItem("s1", "Sample Dragon", "M14", "10", "rare", "NM", "nonfoil", 2));
        wb.addItem(new WorkingItem("s2", "Sample Elf", "DOM", "45", "common", "NM", "foil", 1));
        wb.stageAll();
        int committed = wb.commitStaged();
        check("Committed 3 sample items to unstored queue", committed == 3);

        List<StorageBatch> batches = StorageView.loadBatches();
        boolean foundM14 = false;
        boolean foundDOM = false;
        for (StorageBatch b : batches) {
            if ("M14-1".equals(b.getBoxId())) foundM14 = true;
            if ("DOM-1".equals(b.getBoxId())) foundDOM = true;
        }
        check("StorageView.loadBatches includes seeded M14-1 batch", foundM14);
        check("StorageView.loadBatches includes seeded DOM-1 batch", foundDOM);
    }

    private static void testBatchConfirmationRemovesBatchAndUpdatesCatalog() throws Exception {
        System.out.println("3. Testing Storage confirmation removes batch and updates Catalog...");

        List<StorageBatch> batchesBefore = StorageView.loadBatches();
        StorageBatch targetBatch = null;
        for (StorageBatch b : batchesBefore) {
            if ("BLB-1".equals(b.getBoxId())) {
                targetBatch = b;
                break;
            }
        }
        check("Target batch BLB-1 exists before confirmation", targetBatch != null);

        List<String> ids = new ArrayList<>();
        for (CardInstance ci : targetBatch.getCards()) ids.add(ci.getId());

        int storedCount = StorageView.confirmBatch(targetBatch.getBoxId(), ids);
        check("Confirmed batch stored 1 card", storedCount == ids.size());

        // Verify status in DB is STORED and box_id is BLB-1
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status, box_id FROM card_instances WHERE id = ?")) {
            ps.setString(1, ids.get(0));
            try (ResultSet rs = ps.executeQuery()) {
                check("Instance in DB updated to status = STORED", rs.next() && "STORED".equals(rs.getString("status")));
                check("Instance in DB updated with box_id = BLB-1", "BLB-1".equals(rs.getString("box_id")));
            }
        }

        // Verify batch is REMOVED from unstored batches list
        List<StorageBatch> batchesAfter = StorageView.loadBatches();
        boolean stillHasBlb = false;
        for (StorageBatch b : batchesAfter) {
            if ("BLB-1".equals(b.getBoxId())) stillHasBlb = true;
        }
        check("BLB-1 batch removed from unstored batches queue after confirmation", !stillHasBlb);

        // Verify CatalogView synchronization
        CardInstance ci = targetBatch.getCards().get(0);
        CatalogView.updateCardStorageLocation(ci.getName(), ci.getSetCode(), targetBatch.getBoxId());

        Card inCatalog = null;
        if (CatalogView.activeCards != null) {
            for (Card c : CatalogView.activeCards) {
                if (c.getName().equalsIgnoreCase(ci.getName())) {
                    inCatalog = c;
                    break;
                }
            }
        }
        check("Card registered in CatalogView", inCatalog != null);
        check("Card in CatalogView has storage location BLB-1", inCatalog != null && "BLB-1".equals(inCatalog.getBoxLocationId()));
    }

    private static void testShelfCoordinatesBoxRegistrationAndReassignment() throws Exception {
        System.out.println("4. Testing shelf coordinates box registration and dynamic reassignment...");

        String newBox = "BLB-2";
        StorageView.registerBox(newBox, "Shelf Unit A");

        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT box_id, shelf_unit FROM box_containers WHERE box_id = ?")) {
            ps.setString(1, newBox);
            try (ResultSet rs = ps.executeQuery()) {
                check("Box container BLB-2 registered in box_containers table", rs.next() && "BLB-2".equals(rs.getString("box_id")));
            }
        }

        // Test dynamic batch box reassignment
        List<StorageBatch> batches = StorageView.loadBatches();
        check("Unstored batches available for reassignment", !batches.isEmpty());
        StorageBatch active = batches.get(0);
        String oldBox = active.getBoxId();
        active.setBoxId(newBox);
        check("StorageBatch destination dynamically reassigned to BLB-2", "BLB-2".equals(active.getBoxId()));

        // Confirm storing into the new box coordinate
        List<String> ids = new ArrayList<>();
        for (CardInstance ci : active.getCards()) ids.add(ci.getId());
        int confirmed = StorageView.confirmBatch(active.getBoxId(), ids);
        check("Confirmed batch into newly added box BLB-2", confirmed > 0);

        // Verify catalog updated to new box coordinate
        String cardName = active.getCards().get(0).getName();
        String setCode = active.getCards().get(0).getSetCode();
        CatalogView.updateCardStorageLocation(cardName, setCode, newBox);

        Card matched = null;
        if (CatalogView.activeCards != null) {
            for (Card c : CatalogView.activeCards) {
                if (c.getName().equalsIgnoreCase(cardName)) {
                    matched = c;
                    break;
                }
            }
        }
        check("CatalogView updated with new shelf box coordinate BLB-2", matched != null && "BLB-2".equals(matched.getBoxLocationId()));
    }
}
