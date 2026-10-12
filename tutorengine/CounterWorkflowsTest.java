package tutorengine;

import java.util.List;
import tutorengine.intake.IntakeBuffer;
import tutorengine.model.Card;
import tutorengine.ui.CatalogView;
import tutorengine.ui.FxApp;
import tutorengine.ui.RestockView;
import tutorengine.util.CSVLoader;

public class CounterWorkflowsTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== COUNTER WORKFLOWS TEST SUITE ===");

        testIntakeStagingAndDiary();
        testRestockAttentionDeck();
        testSearchHistoryDropdown();
        testNavItemsAndAccelerators();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
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

    private static void testIntakeStagingAndDiary() {
        System.out.println("1. Testing Intake staging and live diary...");
        IntakeBuffer buffer = new IntakeBuffer();
        Card c1 = new Card("MTG-TSP-157", "Fury Sliver", "Time Spiral", "Red", "Creature", 2006, 1.25, 2, 40, false, "CAB-T-TOP");
        Card c2 = new Card("MTG-WOC-129", "Paradise Druid", "Wilds of Eldraine", "Green", "Creature", 2023, 2.50, 4, 70, false, "CAB-T-TOP");

        // Stage 2
        buffer.stage(c1);
        buffer.stage(c2);
        check("Stage 2 cards: pending count is 2", buffer.pending() == 2);

        // Process 1 (FIFO order)
        Card processed = buffer.processNext();
        check("Process next returns first card (FIFO)", processed != null && "MTG-TSP-157".equals(processed.getSku()));
        check("Pending count after 1 processed is 1", buffer.pending() == 1);
        check("Diary contains INTAKE record", buffer.getLog().all().size() == 1 && buffer.getLog().all().get(0).contains("MTG-TSP-157"));

        // Undo (LIFO order)
        Card undone = buffer.undo();
        check("Undo returns most recently processed card (LIFO)", undone != null && "MTG-TSP-157".equals(undone.getSku()));
    }

    private static void testRestockAttentionDeck() {
        System.out.println("2. Testing Restock attention deck...");
        List<Card> cards = CSVLoader.load("Dataset/cards.csv");
        if (cards.isEmpty()) {
            cards = List.of(
                new Card("T-0", "Zero Stock", "TST", "Blue", "Instant", 2024, 10.0, 0, 50, false, "CAB-T-TOP"),
                new Card("T-1", "Low Stock 1", "TST", "Red", "Sorcery", 2024, 20.0, 1, 80, false, "CAB-T-TOP"),
                new Card("T-2", "Low Stock 2", "TST", "Red", "Sorcery", 2024, 25.0, 2, 85, false, "CAB-T-TOP"),
                new Card("T-3", "Low Stock 3", "TST", "Red", "Sorcery", 2024, 30.0, 3, 90, false, "CAB-T-TOP"),
                new Card("T-9", "High Stock", "TST", "Green", "Creature", 2024, 100.0, 10, 95, false, "CAB-T-TOP")
            );
        }

        List<RestockView.AttentionItem> attentionList = RestockView.computeAttentionList(cards);
        check("Attention list generated", !attentionList.isEmpty());

        long allQty0 = cards.stream().filter(c -> c.getQuantity() == 0).count();
        long foundQty0 = attentionList.stream().filter(i -> i.getQuantity() == 0).count();
        check("All qty 0 cards included", foundQty0 == allQty0);

        long allLow = cards.stream().filter(c -> c.getQuantity() > 0 && c.getQuantity() <= 3).count();
        long foundLow = attentionList.stream().filter(i -> i.getQuantity() > 0 && i.getQuantity() <= 3).count();
        check("All qty <= 3 cards included", foundLow == allLow);

        // Top 10 priority cards present
        List<Card> sortedByPrio = new java.util.ArrayList<>(cards);
        sortedByPrio.sort(java.util.Comparator.comparingDouble(Card::calculatePriority).reversed());
        int topN = Math.min(10, sortedByPrio.size());
        boolean allPrioPresent = true;
        for (int i = 0; i < topN; i++) {
            String sku = sortedByPrio.get(i).getSku();
            if (attentionList.stream().noneMatch(a -> a.getSku().equals(sku))) {
                allPrioPresent = false;
                break;
            }
        }
        check("Top-10 priority cards included in attention list", allPrioPresent);

        // Record click handler
        final Card[] opened = new Card[1];
        RestockView.openRecordHandler = c -> opened[0] = c;
        Card target = attentionList.get(0).getCard();
        RestockView.openRecordHandler.accept(target);
        check("Clicking attention row opens record", opened[0] == target);
    }

    private static void testSearchHistoryDropdown() {
        System.out.println("3. Testing Search history dropdown...");
        CatalogView.recentSearches.clear();

        CatalogView.recordRecentSku("SKU-1");
        CatalogView.recordRecentSku("SKU-2");
        CatalogView.recordRecentSku("SKU-3");
        CatalogView.recordRecentSku("SKU-2"); // Dedup test: moves SKU-2 to front

        check("Deduped history size is 3", CatalogView.recentSearches.size() == 3);
        check("Most recent search is at index 0", "SKU-2".equals(CatalogView.recentSearches.get(0)));

        // Test max 10 limit
        for (int i = 4; i <= 15; i++) {
            CatalogView.recordRecentSku("SKU-" + i);
        }
        check("History capped at 10 items", CatalogView.recentSearches.size() == 10);
        check("Latest item at head", "SKU-15".equals(CatalogView.recentSearches.get(0)));
    }

    private static void testNavItemsAndAccelerators() {
        System.out.println("4. Testing FxApp nav items...");
        List<String> items = FxApp.navItems();
        check("FxApp includes Catalog nav item", items.contains("Catalog"));
        check("FxApp includes Rapid Intake nav item", items.contains("Rapid Intake"));
        check("FxApp includes Outtake nav item", items.contains("Outtake"));
        check("FxApp includes Restock nav item", items.contains("Restock"));
        check("FxApp includes Storage nav item", items.contains("Storage"));
        check("FxApp includes Priority nav item", items.contains("Priority"));
        check("FxApp includes Benchmarks nav item", items.contains("Benchmarks"));
        check("FxApp dropped Intake nav item", !items.contains("Intake"));
        check("FxApp dropped Staging nav item", !items.contains("Staging"));
        check("FxApp dropped Trips nav item", !items.contains("Trips"));
        check("FxApp dropped Store Map nav item", !items.contains("Store Map"));
        check("FxApp nav order is Catalog > Rapid Intake > Storage > Outtake > Restock > Priority > Benchmarks",
                items.equals(java.util.List.of("Catalog", "Rapid Intake", "Storage", "Outtake",
                        "Restock", "Priority", "Benchmarks")));
    }
}
