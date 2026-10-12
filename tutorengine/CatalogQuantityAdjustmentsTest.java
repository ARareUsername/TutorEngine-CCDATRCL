package tutorengine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import tutorengine.db.Db;
import tutorengine.model.Card;
import tutorengine.ui.CatalogView;

public class CatalogQuantityAdjustmentsTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== CATALOG QUANTITY ADJUSTMENTS TEST SUITE ===");
        try {
            Db.migrate();
            testQuantityIncrementOnAdd();
            testQuantityDecrementOnRemove();
            testQuantityBoundsAndNegativeProtection();
            testUndoRedoForQuantityAdjustments();
            testZeroQuantityCardRetention();

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

    private static void testQuantityIncrementOnAdd() throws Exception {
        System.out.println("1. Testing quantity increment on Add...");
        String cardName = "Adjustment Card Alpha";
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, status, quantity) "
                     + "VALUES ('adj-inst-1', 'scry-adj-1', ?, 'TST', '101', 'Rare', 'NM', 'NONFOIL', 'STORED', 2)")) {
            ps.setString(1, cardName);
            ps.executeUpdate();
        } catch (Exception ignored) {}

        Card card = new Card("SKU-ADJ-001", cardName, "TST", "White", "Creature", 2024, 50.0, 2, 80, false, "CAB-T-TOP");
        
        // Add +3 copies
        int newQty = CatalogView.adjustCardQuantity(card, 3);
        check("Quantity incremented to 5", newQty == 5 && card.getQuantity() == 5);

        // Verify DB update
        int dbQty = -1;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT quantity FROM card_instances WHERE name = ?")) {
            ps.setString(1, cardName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) dbQty = rs.getInt(1);
            }
        }
        check("Database quantity updated to 5", dbQty == 5);
    }

    private static void testQuantityDecrementOnRemove() throws Exception {
        System.out.println("2. Testing quantity decrement on Remove...");
        String cardName = "Adjustment Card Beta";
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, status, quantity) "
                     + "VALUES ('adj-inst-2', 'scry-adj-2', ?, 'TST', '102', 'Rare', 'NM', 'NONFOIL', 'STORED', 10)")) {
            ps.setString(1, cardName);
            ps.executeUpdate();
        } catch (Exception ignored) {}

        Card card = new Card("SKU-ADJ-002", cardName, "TST", "Blue", "Instant", 2024, 75.0, 10, 85, false, "CAB-T-TOP");

        // Decrement by 4 copies
        int newQty = CatalogView.adjustCardQuantity(card, -4);
        check("Quantity decremented to 6 (not deleted)", newQty == 6 && card.getQuantity() == 6);

        // Verify DB update
        int dbQty = -1;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT quantity FROM card_instances WHERE name = ?")) {
            ps.setString(1, cardName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) dbQty = rs.getInt(1);
            }
        }
        check("Database quantity updated to 6", dbQty == 6);
    }

    private static void testQuantityBoundsAndNegativeProtection() {
        System.out.println("3. Testing quantity bounds (0 to 999)...");
        Card card = new Card("SKU-ADJ-003", "Bounds Card", "TST", "Red", "Sorcery", 2024, 15.0, 5, 60, false, "CAB-T-TOP");

        // Decrement beyond zero
        int decQty = CatalogView.adjustCardQuantity(card, -20);
        check("Decrement below zero floors at 0", decQty == 0 && card.getQuantity() == 0);

        // Increment beyond 999
        int incQty = CatalogView.adjustCardQuantity(card, 2000);
        check("Increment beyond 999 caps at 999", incQty == 999 && card.getQuantity() == 999);
    }

    private static void testUndoRedoForQuantityAdjustments() throws Exception {
        System.out.println("4. Testing undo and redo for quantity adjustments...");
        String cardName = "UndoRedo Adjustment Card";
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, status, quantity) "
                     + "VALUES ('adj-inst-4', 'scry-adj-4', ?, 'TST', '104', 'Rare', 'NM', 'NONFOIL', 'STORED', 7)")) {
            ps.setString(1, cardName);
            ps.executeUpdate();
        } catch (Exception ignored) {}

        Card card = new Card("SKU-ADJ-004", cardName, "TST", "Green", "Creature", 2024, 30.0, 7, 70, false, "CAB-T-TOP");

        // Perform quantity change: 7 -> 12 (+5)
        CatalogView.adjustCardQuantity(card, 5);
        check("Initial adjustment applied (7 -> 12)", card.getQuantity() == 12);

        // Undo
        boolean undid = CatalogView.undoLastEdit();
        check("Undo succeeds", undid);
        check("Quantity restored to 7 after undo", card.getQuantity() == 7);

        // Redo
        boolean redid = CatalogView.redoLastEdit();
        check("Redo succeeds", redid);
        check("Quantity reapplied to 12 after redo", card.getQuantity() == 12);
    }

    private static void testZeroQuantityCardRetention() {
        System.out.println("5. Testing zero-quantity card retention in catalog...");
        Map<String, Card> catalog = new HashMap<>();
        Card card = new Card("SKU-ADJ-005", "Zero Retention Card", "TST", "Colorless", "Artifact", 2024, 100.0, 1, 90, false, "CAB-T-TOP");
        catalog.put(card.getSku(), card);

        // Decrement by 1 down to 0
        CatalogView.adjustCardQuantity(card, -1);
        check("Card quantity reached 0", card.getQuantity() == 0);
        check("Card is STILL present in catalog map at qty 0", catalog.containsKey("SKU-ADJ-005"));
        check("Retrieved catalog card has qty 0", catalog.get("SKU-ADJ-005").getQuantity() == 0);
    }
}
