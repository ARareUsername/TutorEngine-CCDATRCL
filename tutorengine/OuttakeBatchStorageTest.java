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
import tutorengine.db.Db;
import tutorengine.model.Card;
import tutorengine.outtake.OrderOuttake;
import tutorengine.outtake.OrderOuttake.CheckoutResult;
import tutorengine.outtake.OrderOuttake.OrderItem;
import tutorengine.outtake.OrderOuttake.OuttakeAction;
import tutorengine.outtake.OrderOuttake.OuttakeBoxBatch;
import tutorengine.outtake.OrderOuttake.OuttakeHistory;
import tutorengine.storage.StorageScaffolding;
import tutorengine.ui.CatalogView;

// OuttakeBatchStorageTest: Automated headless test suite for Outtake Box Batches,
// Storage UI grouping, 2D shelf coordinate mapping, granular and batch pick confirmations,
// live database and catalog inventory deduction, and multi-level Undo/Redo command engine.
public class OuttakeBatchStorageTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== OUTTAKE BATCH STORAGE & UNDO/REDO TEST SUITE ===");
        Path tmpDb = null;
        try {
            tmpDb = Files.createTempFile("tutorengine-outtake-batch-test-", ".db");
            System.setProperty("tutorengine.db", tmpDb.toString());
            Db.migrate();

            testBoxBatchModelAndGrouping();
            testConfirmCardPickAndCatalogSync();
            testConfirmBoxBatchPick();
            testPickReversal();
            testOuttakeHistoryUndoRedo();
            testOrderItemMutationUndoRedo();
            testPartialAndCompleteCheckoutWithPickedItems();

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

    private static void testBoxBatchModelAndGrouping() {
        System.out.println("1. Testing OuttakeBoxBatch model & reactive order clustering...");

        OrderItem i1 = new OrderItem("1", "Ajani, Caller of the Pride", "M14", "1", 2, 120.0, "M14-1", "Shelf Unit B", "MID", 5);
        OrderItem i2 = new OrderItem("2", "Black Cat", "M14", "84", 3, 15.0, "M14-1", "Shelf Unit B", "MID", 10);
        OrderItem i3 = new OrderItem("3", "Teferi, Hero of Dominaria", "DOM", "207", 1, 450.0, "DOM-1", "Shelf Unit A", "TOP", 2);
        OrderItem i4 = new OrderItem("4", "Chandra, Pyromaster", "M14", "132", 1, 95.0, "M14-1", "Shelf Unit B", "MID", 3);
        OrderItem i5 = new OrderItem("5", "Basking Rootwalla", "TOR", "110", 4, 30.0, "TOR-1", "Shelf Unit A", "LOW", 4);

        List<OrderItem> items = List.of(i1, i2, i3, i4, i5);
        List<OuttakeBoxBatch> batches = OrderOuttake.groupOrderIntoBatches(items);

        check("Groups 5 order items into 3 distinct box batches", batches.size() == 3);

        // Forward-pass pick route check: Shelf Unit A batches come before Shelf Unit B
        OuttakeBoxBatch b1 = batches.get(0);
        OuttakeBoxBatch b2 = batches.get(1);
        OuttakeBoxBatch b3 = batches.get(2);

        check("First batch is on Shelf Unit A", "Shelf Unit A".equals(b1.getShelfUnit()));
        check("Second batch is on Shelf Unit A", "Shelf Unit A".equals(b2.getShelfUnit()));
        check("Third batch is on Shelf Unit B", "Shelf Unit B".equals(b3.getShelfUnit()));

        check("Batch 1 is DOM-1 (alphabetical box order within Shelf Unit A)", "DOM-1".equals(b1.getBoxId()));
        check("Batch 2 is TOR-1", "TOR-1".equals(b2.getBoxId()));
        check("Batch 3 is M14-1", "M14-1".equals(b3.getBoxId()));

        // Check M14-1 aggregation
        check("M14-1 contains 3 line items", b3.getItems().size() == 3);
        check("M14-1 total card count is 6 (2 + 3 + 1)", b3.getTotalCardCount() == 6);
        check("M14-1 picked card count initially 0", b3.getPickedCardCount() == 0);
        check("M14-1 remaining card count is 6", b3.getRemainingCardCount() == 6);
        check("M14-1 isFullyPicked() is false", !b3.isFullyPicked());
        check("M14-1 status display starts as Pending", b3.getStatusDisplay().contains("Pending"));
        check("M14-1 coordinates display formatted", "Shelf Unit B • MID".equals(b3.getCoordinatesDisplay()));
    }

    private static void testConfirmCardPickAndCatalogSync() throws Exception {
        System.out.println("2. Testing Granular card pick confirmation & live catalog sync...");

        // Setup CatalogView in-memory cache
        List<Card> activeCards = new ArrayList<>();
        Map<String, Card> activeCatalog = new HashMap<>();
        CatalogView.activeCards = activeCards;
        CatalogView.activeCatalog = activeCatalog;

        Card catCard = new Card("SKU-PICK-1", "Pick Test Drake", "Core 2019", "Blue", "Creature",
                2018, 25.0, 5, 20, false, "M19-1", "{1}{U}", "2", "2", "M19");
        activeCards.add(catCard);
        activeCatalog.put(catCard.getSku(), catCard);

        StorageScaffolding.createBox("M19-1", "Shelf Unit A", "MID");

        // Seed 3 STORED instances in SQLite
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, language, status, box_id, created_at, stored_at)"
                     + " VALUES (?, 'scry-1', 'Pick Test Drake', 'M19', '1', 'common', 'NM', 'nonfoil', 'EN', 'STORED', 'M19-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setString(1, "inst-pick-1");
            ps.executeUpdate();
            ps.setString(1, "inst-pick-2");
            ps.executeUpdate();
            ps.setString(1, "inst-pick-3");
            ps.executeUpdate();
        }

        OrderItem item = new OrderItem("SKU-PICK-1", "Pick Test Drake", "M19", "1",
                2, 25.0, "M19-1", "Shelf Unit A", "MID", 3);

        check("Item initially unpicked", !item.isPicked());

        // Confirm pick for 2 cards
        List<String> pickedIds = OrderOuttake.confirmCardPick(item);
        check("Returns 2 checked-out instance IDs", pickedIds.size() == 2);
        check("Item flagged as picked", item.isPicked());
        check("Item stores checked out instance IDs", item.getCheckedOutInstanceIds().size() == 2);

        // Verify DB status
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status, COUNT(*) FROM card_instances WHERE name = 'Pick Test Drake' GROUP BY status")) {
            try (ResultSet rs = ps.executeQuery()) {
                int stored = 0;
                int checkedOut = 0;
                while (rs.next()) {
                    if ("STORED".equals(rs.getString(1))) stored = rs.getInt(2);
                    if ("CHECKED_OUT".equals(rs.getString(1))) checkedOut = rs.getInt(2);
                }
                check("1 instance remains STORED in DB", stored == 1);
                check("2 instances transitioned to CHECKED_OUT in DB", checkedOut == 2);
            }
        }

        // Verify CatalogView quantity decremented live from 5 to 3
        check("CatalogView quantity decremented live (5 -> 3)", catCard.getQuantity() == 3);
    }

    private static void testConfirmBoxBatchPick() throws Exception {
        System.out.println("3. Testing Batch-level pick confirmation...");

        // Seed 2 distinct cards in box BLB-1
        StorageScaffolding.createBox("BLB-1", "Shelf Unit A", "MID");

        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, language, status, box_id, created_at, stored_at)"
                     + " VALUES (?, 'scry-blb', ?, 'BLB', '1', 'rare', 'NM', 'nonfoil', 'EN', 'STORED', 'BLB-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setString(1, "inst-blb-1");
            ps.setString(2, "Valley Questcaller");
            ps.executeUpdate();

            ps.setString(1, "inst-blb-2");
            ps.setString(2, "Hop to It");
            ps.executeUpdate();
        }

        OrderItem it1 = new OrderItem("BLB-1", "Valley Questcaller", "BLB", "1", 1, 80.0, "BLB-1", "Shelf Unit A", "MID", 1);
        OrderItem it2 = new OrderItem("BLB-2", "Hop to It", "BLB", "2", 1, 40.0, "BLB-1", "Shelf Unit A", "MID", 1);

        OuttakeBoxBatch batch = new OuttakeBoxBatch("BLB-1", "Shelf Unit A", "MID");
        batch.getItems().add(it1);
        batch.getItems().add(it2);

        check("Batch initially not fully picked", !batch.isFullyPicked());

        Map<OrderItem, List<String>> batchMap = OrderOuttake.confirmBoxBatchPick(batch);
        check("Batch pick map confirms both items", batchMap.size() == 2);
        check("Item 1 picked", it1.isPicked());
        check("Item 2 picked", it2.isPicked());
        check("Batch isFullyPicked() is now true", batch.isFullyPicked());
        check("Batch status display shows ✓ Picked (2/2)", batch.getStatusDisplay().startsWith("✓ Picked (2/2)"));
    }

    private static void testPickReversal() throws Exception {
        System.out.println("4. Testing Reverse pick action (restoring STORED status & catalog)...");

        Card catCard = new Card("SKU-REV", "Reversal Test Beast", "WOE", "Green", "Creature",
                2023, 50.0, 3, 10, false, "WOE-1", "{2}{G}", "3", "3", "WOE");
        CatalogView.activeCards = new ArrayList<>(List.of(catCard));
        CatalogView.activeCatalog = new HashMap<>(Map.of(catCard.getSku(), catCard));

        StorageScaffolding.createBox("WOE-1", "Shelf Unit A", "LOW");

        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, language, status, box_id, created_at, stored_at)"
                     + " VALUES ('inst-rev-1', 'scry-woe', 'Reversal Test Beast', 'WOE', '1', 'common', 'NM', 'nonfoil', 'EN', 'STORED', 'WOE-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.executeUpdate();
        }

        OrderItem item = new OrderItem("SKU-REV", "Reversal Test Beast", "WOE", "1", 1, 50.0, "WOE-1", "Shelf Unit A", "LOW", 1);

        // Pick item
        List<String> ids = OrderOuttake.confirmCardPick(item);
        check("Item picked", item.isPicked());
        check("Catalog quantity decremented to 2", catCard.getQuantity() == 2);

        // Revert pick
        OrderOuttake.revertCardPick(item, ids);
        check("Item isPicked() reset to false", !item.isPicked());
        check("Catalog quantity restored to 3", catCard.getQuantity() == 3);

        // Verify DB status restored to STORED
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status FROM card_instances WHERE id = 'inst-rev-1'")) {
            try (ResultSet rs = ps.executeQuery()) {
                check("DB instance restored to STORED", rs.next() && "STORED".equals(rs.getString(1)));
            }
        }
    }

    private static void testOuttakeHistoryUndoRedo() throws Exception {
        System.out.println("5. Testing OuttakeHistory multi-level Undo & Redo engine...");

        Card catCard = new Card("SKU-HIST", "History Goblin", "DOM", "Red", "Creature",
                2018, 30.0, 10, 50, false, "DOM-1", "{R}", "1", "1", "DOM");
        CatalogView.activeCards = new ArrayList<>(List.of(catCard));
        CatalogView.activeCatalog = new HashMap<>(Map.of(catCard.getSku(), catCard));

        StorageScaffolding.createBox("DOM-1", "Shelf Unit A", "TOP");

        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, language, status, box_id, created_at, stored_at)"
                     + " VALUES ('inst-hist-1', 'scry-dom', 'History Goblin', 'DOM', '1', 'common', 'NM', 'nonfoil', 'EN', 'STORED', 'DOM-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.executeUpdate();
        }

        OrderItem item = new OrderItem("SKU-HIST", "History Goblin", "DOM", "1", 1, 30.0, "DOM-1", "Shelf Unit A", "TOP", 1);
        List<OrderItem> orderList = new ArrayList<>(List.of(item));

        OuttakeHistory history = new OuttakeHistory();
        check("Initial history cannot undo", !history.canUndo());
        check("Initial history cannot redo", !history.canRedo());

        // Perform pick
        List<String> ids = OrderOuttake.confirmCardPick(item);
        history.push(new OuttakeAction(OuttakeAction.Type.PICK_CARD, item, ids));

        check("History can undo after pick", history.canUndo());
        check("History cannot redo yet", !history.canRedo());
        check("Item is picked", item.isPicked());
        check("Catalog decremented to 9", catCard.getQuantity() == 9);

        // Perform UNDO
        OuttakeAction undid = history.undo(orderList);
        check("Undo returns PICK_CARD action", undid != null && undid.type == OuttakeAction.Type.PICK_CARD);
        check("Item is unpicked after undo", !item.isPicked());
        check("Catalog quantity restored to 10 after undo", catCard.getQuantity() == 10);
        check("History can now redo", history.canRedo());
        check("History cannot undo anymore", !history.canUndo());

        // Perform REDO
        OuttakeAction redid = history.redo(orderList);
        check("Redo returns PICK_CARD action", redid != null && redid.type == OuttakeAction.Type.PICK_CARD);
        check("Item is picked again after redo", item.isPicked());
        check("Catalog quantity decremented to 9 after redo", catCard.getQuantity() == 9);
        check("History can undo again", history.canUndo());
        check("History cannot redo anymore", !history.canRedo());
    }

    private static void testOrderItemMutationUndoRedo() throws Exception {
        System.out.println("6. Testing Add/Remove/Modify quantity Undo & Redo...");

        OrderItem i1 = new OrderItem("SKU-A", "Card A", "SET", "1", 1, 10.0, "SET-1", "Shelf Unit A", "MID", 5);
        OrderItem i2 = new OrderItem("SKU-B", "Card B", "SET", "2", 2, 20.0, "SET-1", "Shelf Unit A", "MID", 5);

        List<OrderItem> orderList = new ArrayList<>();
        orderList.add(i1);

        OuttakeHistory history = new OuttakeHistory();

        // 1. ADD_ITEM test
        orderList.add(i2);
        history.push(new OuttakeAction(OuttakeAction.Type.ADD_ITEM, i2, null));
        check("Order list has 2 items", orderList.size() == 2);

        history.undo(orderList);
        check("Undo ADD_ITEM removes i2 from order list", orderList.size() == 1 && !orderList.contains(i2));

        history.redo(orderList);
        check("Redo ADD_ITEM restores i2 to order list", orderList.size() == 2 && orderList.contains(i2));

        // 2. MODIFY_QTY test
        int oldQty = i1.getQuantity();
        i1.setQuantity(4);
        history.push(new OuttakeAction(OuttakeAction.Type.MODIFY_QTY, i1, 0, oldQty, 4));
        check("i1 quantity modified to 4", i1.getQuantity() == 4);

        history.undo(orderList);
        check("Undo MODIFY_QTY restores original quantity 1", i1.getQuantity() == 1);

        history.redo(orderList);
        check("Redo MODIFY_QTY re-applies quantity 4", i1.getQuantity() == 4);

        // 3. REMOVE_ITEM test
        int idx = orderList.indexOf(i2);
        orderList.remove(i2);
        history.push(new OuttakeAction(OuttakeAction.Type.REMOVE_ITEM, i2, idx, i2.getQuantity(), 0));
        check("i2 removed from order list", orderList.size() == 1);

        history.undo(orderList);
        check("Undo REMOVE_ITEM restores i2 to order list", orderList.size() == 2 && orderList.contains(i2));

        history.redo(orderList);
        check("Redo REMOVE_ITEM removes i2 again", orderList.size() == 1 && !orderList.contains(i2));
    }

    private static void testPartialAndCompleteCheckoutWithPickedItems() throws Exception {
        System.out.println("7. Testing Complete Checkout handling pre-picked items seamlessly...");

        Card catCard = new Card("SKU-FIN", "Final Knight", "M15", "White", "Creature",
                2014, 100.0, 2, 10, false, "M15-1", "{3}{W}", "3", "3", "M15");
        CatalogView.activeCards = new ArrayList<>(List.of(catCard));
        CatalogView.activeCatalog = new HashMap<>(Map.of(catCard.getSku(), catCard));

        StorageScaffolding.createBox("M15-1", "Shelf Unit B", "TOP");

        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, language, status, box_id, created_at, stored_at)"
                     + " VALUES (?, 'scry-m15', 'Final Knight', 'M15', '1', 'rare', 'NM', 'nonfoil', 'EN', 'STORED', 'M15-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setString(1, "inst-fin-1");
            ps.executeUpdate();
            ps.setString(1, "inst-fin-2");
            ps.executeUpdate();
        }

        OrderItem it1 = new OrderItem("SKU-FIN-1", "Final Knight", "M15", "1", 1, 100.0, "M15-1", "Shelf Unit B", "TOP", 2);
        OrderItem it2 = new OrderItem("SKU-FIN-2", "Final Knight", "M15", "1", 1, 100.0, "M15-1", "Shelf Unit B", "TOP", 2);

        // Pre-pick item 1
        OrderOuttake.confirmCardPick(it1);
        check("Item 1 is pre-picked", it1.isPicked());

        // Complete checkout for the order (it1 is pre-picked, it2 is unpicked)
        CheckoutResult res = OrderOuttake.checkoutOrder(List.of(it1, it2));
        check("Checkout succeeds with mixed picked/unpicked items", res.isSuccess());
        check("Total lines checked out is 2", res.getLinesCheckedOut() == 2);
        check("Total cards checked out is 2", res.getTotalCardsCheckedOut() == 2);
        check("Total amount is ₱200.00", Math.abs(res.getTotalAmount() - 200.0) < 0.001);

        // Verify both instances are now CHECKED_OUT in DB
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM card_instances WHERE name = 'Final Knight' AND status = 'CHECKED_OUT'")) {
            try (ResultSet rs = ps.executeQuery()) {
                check("Both card instances CHECKED_OUT in database", rs.next() && rs.getInt(1) == 2);
            }
        }
    }
}
