package tutorengine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tutorengine.db.Db;
import tutorengine.model.Card;
import tutorengine.model.CardInstance;
import tutorengine.storage.StorageScaffolding;
import tutorengine.ui.CatalogView;
import tutorengine.ui.StorageView;

public class StorageBoxCrudTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== STORAGE BOX CRUD TEST SUITE ===");
        Path tmpDb = null;
        try {
            tmpDb = Files.createTempFile("tutorengine-box-crud-test-", ".db");
            System.setProperty("tutorengine.db", tmpDb.toString());
            Db.migrate();

            testBoxCreation();
            testCardCountLookup();
            testBoxRenamingWithCascade();
            testBoxRelocation();
            testBoxDeletionWithCardReconciliation();
            testStorageViewRegistration();

            System.out.println();
            System.out.println(passed + " passed, " + failed + " failed");
            if (failed > 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (tmpDb != null) {
                try {
                    Files.deleteIfExists(tmpDb);
                } catch (Exception ignored) {}
            }
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

    private static void testBoxCreation() throws Exception {
        System.out.println("1. Testing Box Creation (createBox)...");

        boolean created = StorageScaffolding.createBox("TEST-BOX-1", "Shelf Unit A", "TOP");
        check("StorageScaffolding.createBox creates new container", created);

        // Verify record in box_containers table
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT shelf_unit, shelf_tier, display_label FROM box_containers WHERE box_id = ?")) {
            ps.setString(1, "TEST-BOX-1");
            try (ResultSet rs = ps.executeQuery()) {
                check("Container exists in DB", rs.next());
                check("Container shelf_unit is Shelf Unit A", "Shelf Unit A".equals(rs.getString("shelf_unit")));
                check("Container shelf_tier is TOP", "TOP".equals(rs.getString("shelf_tier")));
                check("Container display_label is TEST-BOX-1 Box", "TEST-BOX-1 Box".equals(rs.getString("display_label")));
            }
        }

        // Creating duplicate returns false gracefully
        boolean duplicate = StorageScaffolding.createBox("TEST-BOX-1", "Shelf Unit A", "TOP");
        check("Duplicate createBox does not crash and returns false", !duplicate);
    }

    private static void testCardCountLookup() throws Exception {
        System.out.println("2. Testing Card Count Lookup (getBoxCardCount)...");

        int initialCount = StorageScaffolding.getBoxCardCount("TEST-BOX-1");
        check("Initial card count in empty box is 0", initialCount == 0);

        // Insert 2 card instances into TEST-BOX-1
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, language, status, box_id, created_at, stored_at)"
                     + " VALUES (?, ?, ?, 'TEST', '1', 'rare', 'NM', 'nonfoil', 'EN', 'STORED', 'TEST-BOX-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setString(1, "card-inst-1");
            ps.setString(2, "scry-1");
            ps.setString(3, "Alpha Test Dragon");
            ps.executeUpdate();

            ps.setString(1, "card-inst-2");
            ps.setString(2, "scry-2");
            ps.setString(3, "Beta Test Drake");
            ps.executeUpdate();
        }

        int updatedCount = StorageScaffolding.getBoxCardCount("TEST-BOX-1");
        check("getBoxCardCount returns 2 after storing cards", updatedCount == 2);
    }

    private static void testBoxRenamingWithCascade() throws Exception {
        System.out.println("3. Testing Box Renaming and Cascading (renameBox)...");

        // Set up in-memory CatalogView state
        List<Card> activeCards = new ArrayList<>();
        Map<String, Card> activeCatalog = new HashMap<>();
        CatalogView.activeCards = activeCards;
        CatalogView.activeCatalog = activeCatalog;

        Card c1 = new Card("SKU-DRAGON", "Alpha Test Dragon", "Test Set", "Red", "Creature",
                2024, 100.0, 1, 80, true, "TEST-BOX-1", "{3}{R}{R}", "5", "5", "TEST");
        Card c2 = new Card("SKU-DRAKE", "Beta Test Drake", "Test Set", "Blue", "Creature",
                2024, 50.0, 1, 60, false, "TEST-BOX-1", "{2}{U}", "2", "3", "TEST");
        activeCards.add(c1);
        activeCards.add(c2);
        activeCatalog.put(c1.getSku(), c1);
        activeCatalog.put(c2.getSku(), c2);

        check("Catalog card c1 initial location is TEST-BOX-1", "TEST-BOX-1".equals(c1.getBoxLocationId()));

        // Rename TEST-BOX-1 -> TEST-BOX-RENAMED
        boolean renamed = StorageScaffolding.renameBox("TEST-BOX-1", "TEST-BOX-RENAMED");
        check("StorageScaffolding.renameBox succeeded", renamed);

        // Verify SQLite box_containers table
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT shelf_unit, shelf_tier FROM box_containers WHERE box_id = ?")) {
            ps.setString(1, "TEST-BOX-RENAMED");
            try (ResultSet rs = ps.executeQuery()) {
                check("New container TEST-BOX-RENAMED exists in DB", rs.next());
                check("New container retained shelf_unit", "Shelf Unit A".equals(rs.getString("shelf_unit")));
                check("New container retained shelf_tier", "TOP".equals(rs.getString("shelf_tier")));
            }
        }

        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM box_containers WHERE box_id = 'TEST-BOX-1'")) {
            try (ResultSet rs = ps.executeQuery()) {
                check("Old container TEST-BOX-1 deleted from box_containers", rs.next() && rs.getInt(1) == 0);
            }
        }

        // Verify card_instances table updated
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM card_instances WHERE box_id = 'TEST-BOX-RENAMED'")) {
            try (ResultSet rs = ps.executeQuery()) {
                check("Card instances in DB cascaded to TEST-BOX-RENAMED", rs.next() && rs.getInt(1) == 2);
            }
        }

        // Verify live CatalogView synchronized
        check("CatalogView activeCards c1 updated to TEST-BOX-RENAMED", "TEST-BOX-RENAMED".equals(c1.getBoxLocationId()));
        check("CatalogView activeCards c2 updated to TEST-BOX-RENAMED", "TEST-BOX-RENAMED".equals(c2.getBoxLocationId()));
        check("CatalogView activeCatalog map updated to TEST-BOX-RENAMED", "TEST-BOX-RENAMED".equals(activeCatalog.get("SKU-DRAGON").getBoxLocationId()));

        // Renaming to already existing box returns false
        StorageScaffolding.createBox("EXISTING-BOX", "Shelf Unit A", "MID");
        boolean conflict = StorageScaffolding.renameBox("TEST-BOX-RENAMED", "EXISTING-BOX");
        check("renameBox to already existing box returns false", !conflict);
    }

    private static void testBoxRelocation() throws Exception {
        System.out.println("4. Testing Box Relocation between shelf units (moveBox)...");

        boolean moved = StorageScaffolding.moveBox("TEST-BOX-RENAMED", "Shelf Unit B", "LOW");
        check("StorageScaffolding.moveBox returned true", moved);

        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT shelf_unit, shelf_tier FROM box_containers WHERE box_id = ?")) {
            ps.setString(1, "TEST-BOX-RENAMED");
            try (ResultSet rs = ps.executeQuery()) {
                check("Container exists after move", rs.next());
                check("Container moved to Shelf Unit B", "Shelf Unit B".equals(rs.getString("shelf_unit")));
                check("Container tier updated to LOW", "LOW".equals(rs.getString("shelf_tier")));
            }
        }
    }

    private static void testBoxDeletionWithCardReconciliation() throws Exception {
        System.out.println("5. Testing Box Deletion with Card Reconciliation (deleteBox)...");

        boolean deleted = StorageScaffolding.deleteBox("TEST-BOX-RENAMED");
        check("StorageScaffolding.deleteBox returned true", deleted);

        // Verify box is deleted from box_containers
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM box_containers WHERE box_id = 'TEST-BOX-RENAMED'")) {
            try (ResultSet rs = ps.executeQuery()) {
                check("Container removed from box_containers", rs.next() && rs.getInt(1) == 0);
            }
        }

        // Verify card instances in SQLite are safely unlinked and marked UNSTORED
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM card_instances WHERE status = 'UNSTORED' AND box_id IS NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                check("Cards safely unlinked (box_id NULL, status UNSTORED)", rs.next() && rs.getInt(1) == 2);
            }
        }

        // Verify CatalogView updated matching cards to "Unassigned"
        Card c1 = CatalogView.activeCatalog.get("SKU-DRAGON");
        Card c2 = CatalogView.activeCatalog.get("SKU-DRAKE");
        check("Catalog card c1 storage location reset to Unassigned", "Unassigned".equals(c1.getBoxLocationId()));
        check("Catalog card c2 storage location reset to Unassigned", "Unassigned".equals(c2.getBoxLocationId()));

        // Verify clustered unstored batches now includes the unassigned cards
        List<StorageScaffolding.StorageBatch> unassignedBatches = StorageScaffolding.clusterUnstored();
        check("Unassigned cards clustered into unstored batches", !unassignedBatches.isEmpty());
    }

    private static void testStorageViewRegistration() {
        System.out.println("6. Testing StorageView.registerBox delegation...");

        StorageView.registerBox("VIEW-REG-1", "Shelf Unit A");
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM box_containers WHERE box_id = 'VIEW-REG-1'")) {
            try (ResultSet rs = ps.executeQuery()) {
                check("StorageView.registerBox provisions container in DB", rs.next() && rs.getInt(1) == 1);
            }
        } catch (Exception ex) {
            check("StorageView.registerBox provisions container in DB", false);
        }
    }
}
