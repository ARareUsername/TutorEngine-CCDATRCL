package tutorengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tutorengine.algorithms.SkuCorrector;
import tutorengine.db.Db;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;
import tutorengine.ui.CatalogView;
import tutorengine.util.CSVLoader;

public class CatalogUnifiedUndoTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== CATALOG UNIFIED UNDO & FUNCTIONALITY TEST SUITE ===");
        try {
            Db.migrate();
            testPropertyChangeUndoRedo();
            testAddCardUndoRedo();
            testRemoveCardUndoRedo();
            testMixedActionStack();
            testSearchAndLocateRouting();

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

    private static void testPropertyChangeUndoRedo() {
        System.out.println("1. Testing property change undo/redo (price, quantity, demand)...");
        CatalogView.undoStack.clear();
        CatalogView.redoStack.clear();

        Card card = new Card("SKU-PROP-001", "Property Card", "TestSet", "Red", "Sorcery",
                2024, 100.0, 5, 40, false, "CAB-T-TOP");

        // Price change
        CatalogView.recordEdit(card, "price", 100.0, 150.0);
        check("Price updated to 150.0", card.getPrice() == 150.0);
        check("Undo stack has 1 action", CatalogView.undoStack.size() == 1);

        // Demand change
        CatalogView.recordEdit(card, "demandScore", 40, 85);
        check("Demand updated to 85", card.getDemandScore() == 85);
        check("Undo stack has 2 actions", CatalogView.undoStack.size() == 2);

        // Quantity change
        CatalogView.recordEdit(card, "quantity", 5, 12);
        check("Quantity updated to 12", card.getQuantity() == 12);
        check("Undo stack has 3 actions", CatalogView.undoStack.size() == 3);

        // Undo quantity
        boolean u1 = CatalogView.undoLastEdit();
        check("Undo quantity succeeds", u1 && card.getQuantity() == 5);
        check("Redo stack has 1 action", CatalogView.redoStack.size() == 1);

        // Undo demand
        boolean u2 = CatalogView.undoLastEdit();
        check("Undo demand succeeds", u2 && card.getDemandScore() == 40);

        // Undo price
        boolean u3 = CatalogView.undoLastEdit();
        check("Undo price succeeds", u3 && card.getPrice() == 100.0);

        // Redo price
        boolean r1 = CatalogView.redoLastEdit();
        check("Redo price succeeds", r1 && card.getPrice() == 150.0);

        // Redo demand
        boolean r2 = CatalogView.redoLastEdit();
        check("Redo demand succeeds", r2 && card.getDemandScore() == 85);

        // Redo quantity
        boolean r3 = CatalogView.redoLastEdit();
        check("Redo quantity succeeds", r3 && card.getQuantity() == 12);
    }

    private static void testAddCardUndoRedo() {
        System.out.println("2. Testing new card addition undo/redo...");
        CatalogView.undoStack.clear();
        CatalogView.redoStack.clear();

        List<Card> cards = new ArrayList<>();
        Map<String, Card> catalog = new HashMap<>();
        CatalogView.activeCards = cards;
        CatalogView.activeCatalog = catalog;

        Card newCard = new Card("SKU-ADD-001", "New Session Card", "Session Add", "Green", "Instant",
                2026, 45.0, 3, 50, false, "Intake Sorting Desk");

        catalog.put(newCard.getSku(), newCard);
        cards.add(newCard);
        CatalogView.recordAddCard(newCard);

        check("Card added to catalog", catalog.containsKey("SKU-ADD-001"));
        check("Card added to cards list", cards.contains(newCard));
        check("Undo stack has add action", CatalogView.undoStack.peek().type == CatalogView.ActionType.ADD_CARD);

        // Undo addition
        boolean undone = CatalogView.undoLastEdit();
        check("Undo add card succeeds", undone);
        check("Card removed from catalog index after undo", !catalog.containsKey("SKU-ADD-001"));
        check("Card removed from cards list after undo", !cards.contains(newCard));

        // Redo addition
        boolean redone = CatalogView.redoLastEdit();
        check("Redo add card succeeds", redone);
        check("Card restored to catalog index after redo", catalog.containsKey("SKU-ADD-001"));
        check("Card restored to cards list after redo", cards.contains(newCard));
    }

    private static void testRemoveCardUndoRedo() {
        System.out.println("3. Testing card removal undo/redo...");
        CatalogView.undoStack.clear();
        CatalogView.redoStack.clear();

        List<Card> cards = new ArrayList<>();
        Map<String, Card> catalog = new HashMap<>();
        CatalogView.activeCards = cards;
        CatalogView.activeCatalog = catalog;

        Card deadCard = new Card("SKU-DEL-001", "Out of Stock Card", "TestSet", "Black", "Artifact",
                2023, 200.0, 0, 10, false, "Bulk Box A");
        catalog.put(deadCard.getSku(), deadCard);
        cards.add(deadCard);

        // Simulate clerk confirming delete of 0-qty card
        catalog.remove(deadCard.getSku());
        cards.remove(deadCard);
        CatalogView.recordRemoveCard(deadCard);

        check("Card removed from catalog", !catalog.containsKey("SKU-DEL-001"));
        check("Undo stack has remove action", CatalogView.undoStack.peek().type == CatalogView.ActionType.REMOVE_CARD);

        // Undo removal -> restores card
        boolean undone = CatalogView.undoLastEdit();
        check("Undo remove card succeeds", undone);
        check("Card restored to catalog after undo", catalog.containsKey("SKU-DEL-001"));
        check("Card restored to cards list after undo", cards.contains(deadCard));

        // Redo removal -> removes card again
        boolean redone = CatalogView.redoLastEdit();
        check("Redo remove card succeeds", redone);
        check("Card removed from catalog after redo", !catalog.containsKey("SKU-DEL-001"));
        check("Card removed from cards list after redo", !cards.contains(deadCard));
    }

    private static void testMixedActionStack() {
        System.out.println("4. Testing mixed action stack (interleaved edit, add, adjust, remove)...");
        CatalogView.undoStack.clear();
        CatalogView.redoStack.clear();

        List<Card> cards = new ArrayList<>();
        Map<String, Card> catalog = new HashMap<>();
        CatalogView.activeCards = cards;
        CatalogView.activeCatalog = catalog;

        Card c1 = new Card("SKU-MIX-001", "Card One", "Set1", "Blue", "Creature", 2024, 10.0, 4, 30, false, "CAB-T-TOP");
        catalog.put(c1.getSku(), c1);
        cards.add(c1);

        // Action 1: edit price of c1 (10.0 -> 25.0)
        CatalogView.recordEdit(c1, "price", 10.0, 25.0);

        // Action 2: add c2
        Card c2 = new Card("SKU-MIX-002", "Card Two", "Set2", "White", "Enchantment", 2025, 50.0, 1, 60, false, "CAB-M-MID");
        catalog.put(c2.getSku(), c2);
        cards.add(c2);
        CatalogView.recordAddCard(c2);

        // Action 3: adjust quantity of c1 (4 -> 9)
        CatalogView.adjustCardQuantity(c1, 5);

        // Action 4: remove c2
        catalog.remove(c2.getSku());
        cards.remove(c2);
        CatalogView.recordRemoveCard(c2);

        check("Stack size is 4 actions", CatalogView.undoStack.size() == 4);

        // Undo 4: remove c2 undone -> c2 restored
        CatalogView.undoLastEdit();
        check("Undo 4: c2 restored to catalog", catalog.containsKey("SKU-MIX-002"));

        // Undo 3: qty adjustment undone -> c1 qty back to 4
        CatalogView.undoLastEdit();
        check("Undo 3: c1 qty restored to 4", c1.getQuantity() == 4);

        // Undo 2: add c2 undone -> c2 removed from catalog
        CatalogView.undoLastEdit();
        check("Undo 2: c2 removed from catalog", !catalog.containsKey("SKU-MIX-002"));

        // Undo 1: price edit undone -> c1 price back to 10.0
        CatalogView.undoLastEdit();
        check("Undo 1: c1 price restored to 10.0", c1.getPrice() == 10.0);

        // Stack empty
        check("Undo stack empty after 4 undos", CatalogView.undoStack.isEmpty());
        check("Redo stack has 4 actions", CatalogView.redoStack.size() == 4);

        // Redo all 4
        CatalogView.redoLastEdit(); // redo price -> 25.0
        check("Redo 1: c1 price is 25.0", c1.getPrice() == 25.0);

        CatalogView.redoLastEdit(); // redo add c2
        check("Redo 2: c2 added to catalog", catalog.containsKey("SKU-MIX-002"));

        CatalogView.redoLastEdit(); // redo qty c1 -> 9
        check("Redo 3: c1 qty is 9", c1.getQuantity() == 9);

        CatalogView.redoLastEdit(); // redo remove c2
        check("Redo 4: c2 removed again", !catalog.containsKey("SKU-MIX-002"));
    }

    private static void testSearchAndLocateRouting() {
        System.out.println("5. Testing search (exact & fuzzy) and locate routing...");
        List<Card> cards = CSVLoader.load("Dataset/cards.csv");
        if (cards.isEmpty()) cards = CSVLoader.load("../Dataset/cards.csv");
        if (cards.isEmpty()) cards = CatalogView.loadCards();
        if (cards.stream().noneMatch(c -> "MTG-OTJ-055".equals(c.getSku()))) {
            cards.add(new Card("MTG-OTJ-055", "Mana Drain", "Outlaws of Thunder Junction", "Blue", "Instant", 2024, 2600.00, 1, 98, false, "OTJ-1"));
        }
        Map<String, Card> catalog = CatalogView.indexOf(cards);
        Map<String, List<String>> skuGraph = SkuCorrector.build(cards);

        // Exact lookup
        Card found = CatalogView.lookup(catalog, "MTG-OTJ-055");
        check("Exact lookup finds Mana Drain", found != null && "Mana Drain".equals(found.getName()));

        // SkuCorrector fuzzy search
        List<Card> fuzzy = SkuCorrector.multiSearch(catalog, skuGraph, "mana drain");
        check("Fuzzy multiSearch finds Mana Drain", !fuzzy.isEmpty() && "MTG-OTJ-055".equals(fuzzy.get(0).getSku()));

        // StorageLocation routing
        String route = StorageLocation.route(found);
        check("StorageLocation routes valid shelf location", route != null && !route.isBlank());

        // Locate handler simulation
        final String[] located = new String[1];
        CatalogView.locateHandler = r -> located[0] = r;
        CatalogView.locateHandler.accept(route);
        check("Locate handler receives shelf route", route.equals(located[0]));
    }
}
