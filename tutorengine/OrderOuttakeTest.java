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
import tutorengine.outtake.OrderOuttake.LocationInfo;
import tutorengine.outtake.OrderOuttake.OrderItem;
import tutorengine.outtake.OrderOuttake.OrderSummary;
import tutorengine.storage.StorageScaffolding;
import tutorengine.storage.StorageScaffolding.NeighborBreadcrumb;
import tutorengine.ui.CatalogView;

public class OrderOuttakeTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== ORDER OUTTAKE & CHECKOUT TEST SUITE ===");
        Path tmpDb = null;
        try {
            tmpDb = Files.createTempFile("tutorengine-outtake-test-", ".db");
            System.setProperty("tutorengine.db", tmpDb.toString());
            Db.migrate();

            testCsvImport();
            testSingleCardShortcutParsing();
            testPhysicalLocationResolution();
            testPickPathOrdering();
            testBreadcrumbCalculation();
            testOrderSummary();
            testOrderCheckoutExecution();

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

    private static void testCsvImport() throws Exception {
        System.out.println("1. Testing Order CSV import parsing...");

        String csvData = "Card Name,Set,Qty,Price\n"
                + "Black Cat,M14,2,15.50\n"
                + "Ajani Caller of the Pride,M14,1,120.00\n"
                + "\"Teferi, Hero of Dominaria\",DOM,3,450.00\n";

        List<OrderItem> items = OrderOuttake.importOrderCsv(csvData);
        check("CSV parses 3 order line items", items.size() == 3);

        OrderItem i1 = items.get(0);
        check("Item 1 name is Black Cat", "Black Cat".equals(i1.getName()));
        check("Item 1 set is M14", "M14".equals(i1.getSetCode()));
        check("Item 1 quantity is 2", i1.getQuantity() == 2);
        check("Item 1 price is 15.50", Math.abs(i1.getPrice() - 15.50) < 0.001);
        check("Item 1 total is 31.00", Math.abs(i1.getTotalPrice() - 31.00) < 0.001);

        OrderItem i3 = items.get(2);
        check("Item 3 handled quoted name with comma", "Teferi, Hero of Dominaria".equals(i3.getName()));
        check("Item 3 quantity is 3", i3.getQuantity() == 3);
        check("Item 3 total is 1350.00", Math.abs(i3.getTotalPrice() - 1350.00) < 0.001);

        // Test semicolon delimiter
        String semiCsv = "card_name;set_code;quantity;price\nLightning Bolt;M10;4;25.00\n";
        List<OrderItem> semiItems = OrderOuttake.importOrderCsv(semiCsv);
        check("Semicolon delimited CSV parses correctly", semiItems.size() == 1 && semiItems.get(0).getQuantity() == 4);
    }

    private static void testSingleCardShortcutParsing() {
        System.out.println("2. Testing Rapid single-card shortcut parsing...");

        // Collector number shortcut
        OrderItem s1 = OrderOuttake.parseSingleCardInput("blb 1 x2");
        check("Shortcut 'blb 1 x2' parsed", s1 != null);
        if (s1 != null) {
            check("Shortcut set code is BLB", "BLB".equals(s1.getSetCode()));
            check("Shortcut quantity is 2", s1.getQuantity() == 2);
        }

        // Card name with quantity
        OrderItem s2 = OrderOuttake.parseSingleCardInput("Black Cat x3");
        check("Name shortcut 'Black Cat x3' parsed", s2 != null);
        if (s2 != null) {
            check("Name is Black Cat", "Black Cat".equals(s2.getName()));
            check("Quantity is 3", s2.getQuantity() == 3);
        }

        // Empty input handled gracefully
        OrderItem sEmpty = OrderOuttake.parseSingleCardInput("");
        check("Empty shortcut returns null gracefully", sEmpty == null);
    }

    private static void testPhysicalLocationResolution() throws Exception {
        System.out.println("3. Testing Physical shelf location resolution...");

        // Provision a container in DB
        StorageScaffolding.createBox("DOM-1", "Shelf Unit A", "TOP");

        // Insert stored instance
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, language, status, box_id, created_at, stored_at)"
                     + " VALUES ('inst-dom-1', 'scry-dom', 'Teferi, Hero of Dominaria', 'DOM', '207', 'mythic', 'NM', 'foil', 'EN', 'STORED', 'DOM-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.executeUpdate();
        }

        LocationInfo loc = OrderOuttake.resolvePhysicalLocation("Teferi, Hero of Dominaria", "DOM");
        check("Resolves box location DOM-1", "DOM-1".equals(loc.boxId));
        check("Resolves shelf unit Shelf Unit A", "Shelf Unit A".equals(loc.shelfUnit));
        check("Resolves shelf tier TOP", "TOP".equals(loc.shelfTier));

        // Unstored set fallback
        LocationInfo fallback = OrderOuttake.resolvePhysicalLocation("Unknown Wand", "M14");
        check("Fallback routes unknown M14 card to M14-1", "M14-1".equals(fallback.boxId));
    }

    private static void testPickPathOrdering() {
        System.out.println("4. Testing Forward-pass pick route ordering...");

        OrderItem i1 = new OrderItem("1", "Zendikar Resurgent", "OGW", "", 1, 50.0, "OGW-1", "Shelf Unit B", "MID", 2);
        OrderItem i2 = new OrderItem("2", "Avacyn, Angel of Hope", "AVR", "", 1, 300.0, "AVR-1", "Shelf Unit A", "TOP", 1);
        OrderItem i3 = new OrderItem("3", "Black Cat", "M14", "", 2, 10.0, "M14-1", "Shelf Unit A", "MID", 5);

        List<OrderItem> unordered = List.of(i1, i2, i3);
        List<OrderItem> sorted = OrderOuttake.sortPickPath(unordered);

        check("First pick path item is on Shelf Unit A", "Shelf Unit A".equals(sorted.get(0).getShelfUnit()));
        check("Second pick path item is on Shelf Unit A", "Shelf Unit A".equals(sorted.get(1).getShelfUnit()));
        check("Last pick path item is on Shelf Unit B", "Shelf Unit B".equals(sorted.get(2).getShelfUnit()));
        check("Within Shelf Unit A, AVR-1 comes before M14-1", "AVR-1".equals(sorted.get(0).getBoxId()) && "M14-1".equals(sorted.get(1).getBoxId()));
    }

    private static void testBreadcrumbCalculation() {
        System.out.println("5. Testing In-Box Breadcrumbs calculation...");

        NeighborBreadcrumb bc = OrderOuttake.calculateBreadcrumb("DOM-1", "Teferi, Hero of Dominaria");
        check("Breadcrumb generated for DOM-1", bc != null);
        check("Breadcrumb text is non-empty", bc.getBreadcrumbText() != null && !bc.getBreadcrumbText().isBlank());
        check("Breadcrumb percent is between 0 and 100", bc.getEstimatedPercent() >= 0.0 && bc.getEstimatedPercent() <= 100.0);
    }

    private static void testOrderSummary() {
        System.out.println("6. Testing Order summary computation...");

        OrderItem i1 = new OrderItem("1", "Card A", "SET", "", 2, 25.0, "SET-1", "Shelf Unit A", "MID", 5);
        OrderItem i2 = new OrderItem("2", "Card B", "SET", "", 3, 10.0, "SET-2", "Shelf Unit B", "MID", 1);

        OrderSummary sum = OrderOuttake.computeSummary(List.of(i1, i2));
        check("Total lines is 2", sum.getTotalLines() == 2);
        check("Total cards is 5 (2 + 3)", sum.getTotalCards() == 5);
        check("Total amount is ₱80.00 (2*25 + 3*10)", Math.abs(sum.getTotalAmount() - 80.0) < 0.001);
        check("Distinct boxes count is 2", sum.getDistinctBoxes() == 2);
        check("allInStock is false because Card B needs 3 but only has 1", !sum.isAllInStock());
    }

    private static void testOrderCheckoutExecution() throws Exception {
        System.out.println("7. Testing Order checkout and inventory deduction...");

        // Set up in-memory CatalogView
        List<Card> activeCards = new ArrayList<>();
        Map<String, Card> activeCatalog = new HashMap<>();
        CatalogView.activeCards = activeCards;
        CatalogView.activeCatalog = activeCatalog;

        Card catCard = new Card("SKU-CHECKOUT", "Checkout Test Knight", "Core 2020", "White", "Creature",
                2019, 75.0, 4, 70, false, "M20-1", "{2}{W}", "3", "3", "M20");
        activeCards.add(catCard);
        activeCatalog.put(catCard.getSku(), catCard);

        // Provision box in DB
        StorageScaffolding.createBox("M20-1", "Shelf Unit A", "MID");

        // Insert 3 STORED card instances in SQLite
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, language, status, box_id, created_at, stored_at)"
                     + " VALUES (?, 'scry-m20', 'Checkout Test Knight', 'M20', '1', 'rare', 'NM', 'nonfoil', 'EN', 'STORED', 'M20-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setString(1, "inst-chk-1");
            ps.executeUpdate();
            ps.setString(1, "inst-chk-2");
            ps.executeUpdate();
            ps.setString(1, "inst-chk-3");
            ps.executeUpdate();
        }

        // Check out 2 copies
        OrderItem orderItem = new OrderItem("SKU-CHECKOUT", "Checkout Test Knight", "M20", "1",
                2, 75.0, "M20-1", "Shelf Unit A", "MID", 3);

        CheckoutResult result = OrderOuttake.checkoutOrder(List.of(orderItem));
        check("Checkout succeeds", result.isSuccess());
        check("Receipt ID generated", result.getReceiptId() != null && result.getReceiptId().startsWith("REC-"));
        check("Total cards checked out is 2", result.getTotalCardsCheckedOut() == 2);
        check("Total amount is ₱150.00", Math.abs(result.getTotalAmount() - 150.0) < 0.001);

        // Verify SQLite card_instances state
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status, COUNT(*) FROM card_instances WHERE name = 'Checkout Test Knight' GROUP BY status")) {
            try (ResultSet rs = ps.executeQuery()) {
                int storedCount = 0;
                int checkedOutCount = 0;
                while (rs.next()) {
                    String st = rs.getString(1);
                    int count = rs.getInt(2);
                    if ("STORED".equals(st)) storedCount = count;
                    if ("CHECKED_OUT".equals(st)) checkedOutCount = count;
                }
                check("1 instance remains STORED in DB", storedCount == 1);
                check("2 instances transitioned to CHECKED_OUT in DB", checkedOutCount == 2);
            }
        }

        // Verify CatalogView quantity decremented live (4 -> 2)
        check("CatalogView active card quantity decremented from 4 to 2", catCard.getQuantity() == 2);
    }
}
