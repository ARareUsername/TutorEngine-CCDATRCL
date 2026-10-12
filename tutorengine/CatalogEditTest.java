package tutorengine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import tutorengine.db.Db;
import tutorengine.model.Card;
import tutorengine.ui.CatalogView;

public class CatalogEditTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== CATALOG EDIT QUANTITIES TEST SUITE ===");
        try {
            Db.migrate();
            testInlineQuantityEditSavesToCardInstances();
            testEditPersistsWithoutAudit();
            testDedupPreventsDuplicateSaves();
            testUndoRedoFunctionality();

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

    private static void testInlineQuantityEditSavesToCardInstances() throws Exception {
        System.out.println("6.1 Testing inline quantity edit saves to card_instances...");
        String cardName = "Test Card Alpha";

        // Insert card_instance (no audit scaffolding needed)
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, status, quantity) "
                     + "VALUES ('test-inst-1', 'scry-1', ?, 'TST', '001', 'Rare', 'NM', 'NONFOIL', 'STORED', 1)")) {
            ps.setString(1, cardName);
            ps.executeUpdate();
        } catch (Exception ignored) {}

        Card card = new Card("SKU-TST-001", cardName, "TST", "Blue", "Instant", 2024, 25.0, 1, 5, false, "CAB-T-TOP");

        // Perform edit
        CatalogView.recordEdit(card, "quantity", 1, 15);
        check("Card model quantity updated", card.getQuantity() == 15);

        // Verify DB update
        int dbQty = -1;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT quantity FROM card_instances WHERE name = ?")) {
            ps.setString(1, cardName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dbQty = rs.getInt(1);
                }
            }
        }
        check("card_instances table updated with new quantity", dbQty == 15);
    }

    private static void testEditPersistsWithoutAudit() throws Exception {
        System.out.println("6.2 Testing edit persists with no audit system...");
        Card card = new Card("SKU-TST-002", "Test Card Beta", "TST", "Red", "Sorcery", 2024, 10.0, 2, 8, false, "CAB-T-TOP");

        CatalogView.recordEdit(card, "quantity", 2, 20);
        check("Card model quantity updated", card.getQuantity() == 20);

        boolean auditTableGone = false;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM audit_commits");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
        } catch (Exception expected) {
            auditTableGone = true;
        }
        check("audit_commits table is gone", auditTableGone);
    }

    private static void testDedupPreventsDuplicateSaves() throws Exception {
        System.out.println("6.3 Testing dedup prevents duplicate saves...");
        Card card = new Card("SKU-TST-003", "Test Card Gamma", "TST", "Green", "Creature", 2024, 5.0, 10, 4, false, "CAB-T-TOP");

        int initialStackSize = CatalogView.undoStack.size();

        // Attempt edit with identical value
        CatalogView.recordEdit(card, "quantity", 10, 10);

        check("Dedup skips recording identical value", CatalogView.undoStack.size() == initialStackSize);
    }

    private static void testUndoRedoFunctionality() throws Exception {
        System.out.println("6.4 Testing undo/redo functionality...");
        String cardName = "Test Card Delta";
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, status, quantity) "
                     + "VALUES ('test-inst-4', 'scry-4', ?, 'TST', '004', 'Rare', 'NM', 'NONFOIL', 'STORED', 4)")) {
            ps.setString(1, cardName);
            ps.executeUpdate();
        } catch (Exception ignored) {}

        Card card = new Card("SKU-TST-004", cardName, "TST", "White", "Enchantment", 2024, 12.0, 4, 3, false, "CAB-T-TOP");

        CatalogView.recordEdit(card, "quantity", 4, 8);
        check("Quantity before undo is 8", card.getQuantity() == 8);

        boolean undone = CatalogView.undoLastEdit();
        check("Undo succeeds", undone);
        check("Quantity restored to 4 after undo", card.getQuantity() == 4);

        int dbQty = -1;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT quantity FROM card_instances WHERE name = ?")) {
            ps.setString(1, cardName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) dbQty = rs.getInt(1);
            }
        }
        check("DB quantity reverted to 4 after undo", dbQty == 4);

        boolean redone = CatalogView.redoLastEdit();
        check("Redo succeeds", redone);
        check("Quantity reapplied to 8 after redo", card.getQuantity() == 8);
    }
}
