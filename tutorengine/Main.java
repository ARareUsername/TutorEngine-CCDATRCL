package tutorengine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import tutorengine.algorithms.CardSorter;
import tutorengine.algorithms.SkuCorrector;
import tutorengine.algorithms.StoreNavigator;
import tutorengine.catalog.CatalogIndex;
import tutorengine.intake.IntakeBuffer;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;
import tutorengine.model.TransactionLog;
import tutorengine.priority.PriorityDesk;
import tutorengine.search.SkuFuzzy;
import tutorengine.ui.StorageView;
import tutorengine.util.CSVLoader;

// OWNER: Bondoc, Karl B. — command-line demo and final integration point.
//
// PURPOSE: Stitches the pieces together on sample cards so the whole story is
// visible in one run: which card gets listed first, instant lookup by code, and
// the store walk. As members finish their classes, this demo grows to call them.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.Main
//   Expected sections (dataset-driven; the priority winner depends on the CSV):
//     [0] "Loaded N cards" with N >= 50 (or a WARNING + 3 fallback cards)
//     [1] Highest Action Priority: <top card> (Priority Score: <number>)
//     [2] guarded search: hit, empty-guard, invalid-guard, graceful miss + fuzzy
//     [3] BFS Traversal Route from Checkout Counter over the 12-node guide map
//         + box fill counts + retrieval path to the first card's slot.
//     [4] collision demo: MTG-OTJ-001 and MTG-OTJ-009 share bucket 1 of a
//         HashMap(7); both entries print as one bucket chain, both get() fine.
//     [5] one smoke line per member module (catalog, heap, sorts, log, intake,
//         typo suggest) — full demos stay in each owner's main().
//   "Loaded 0" plus fallback means Dataset/cards.csv was not found from the
//   working directory — run from the project root.
public class Main {
    // Blank -> why; characters outside letters/digits/hyphen -> why; else null.
    static String checkSku(String raw) {
        if (raw == null || raw.trim().isEmpty())
            return "empty — type a SKU like MTG-BLB-280";
        if (!raw.trim().matches("[A-Za-z0-9-]+"))
            return "invalid characters — SKUs use letters, digits, hyphens only";
        return null;
    }

    // Mirrors HashMap's spread ((h ^ (h >>> 16))) and bucket ((n-1) & hash).
    // new HashMap<>(7) sizes its table to 8, so n = 8 here.
    public static int bucketOf(String key) {
        int h = key.hashCode();
        return (8 - 1) & (h ^ (h >>> 16));
    }
    public static void main(String[] args) {
        System.out.println("=============================================");
        System.out.println("   TUTORENGINE: TCG SINGLES INVENTORY ADT    ");
        System.out.println("=============================================");

        Map<String, Card> catalogTable = new HashMap<>(101);
        PriorityQueue<Card> priorityHeap = new PriorityQueue<>(
                Comparator.comparingDouble(Card::calculatePriority).reversed());
        // Canonical cabinet map: front nodes + 1x5 cabinets + 10 shelf slots.
        Map<String, List<String>> storeFloor = StoreNavigator.sampleMap();

        List<Card> cards = CSVLoader.load("Dataset/cards.csv");
        if (cards.isEmpty()) {
            System.out.println("WARNING: dataset empty or missing, using 3 fallback cards.");
            cards = new ArrayList<>(List.of(
                new Card("MTG-MH3-001", "Ajani, Nacatl Pariah", "Modern Horizons 3",
                         "White", "Creature", 2024, 2150.00, 2, 92, true, "Showcase Display"),
                new Card("MTG-OTJ-055", "Mana Drain", "Outlaws of Thunder Junction",
                         "Blue", "Instant", 2024, 2600.00, 1, 98, false, "Showcase Display"),
                new Card("MTG-FDN-101", "Llanowar Elves", "Foundations",
                         "Green", "Creature", 2024, 25.00, 48, 45, false, "Bulk Box C")));
        }
        System.out.println("\n[0] Dataset: loaded " + cards.size() + " cards.");

        for (Card c : cards) {
            catalogTable.put(c.getSku(), c);
            priorityHeap.offer(c);
        }

        System.out.println("\n[1] Immediate Web Listing Priority (PriorityQueue Top):");
        Card urgentCard = priorityHeap.poll();
        System.out.printf("Highest Action Priority: %s (Priority Score: %.2f)%n",
                          urgentCard.getName(), urgentCard.calculatePriority());

        System.out.println("\n[2] Instant SKU Search (HashMap O(1)) + guards:");
        String hitSku = cards.get(0).getSku();
        Card found = catalogTable.get(hitSku);
        System.out.println("Located: " + (found != null ? found.getName() + " | Price: PHP " + found.getPrice() : "Not Found"));
        String emptyGuard = checkSku("   ");
        System.out.println("Empty input guard: " + (emptyGuard == null ? "MISSED (bug)" : emptyGuard));
        String badGuard = checkSku("MTG ## 12!!");
        System.out.println("Invalid input guard: " + (badGuard == null ? "MISSED (bug)" : badGuard));
        Card missing = catalogTable.get("NOPE-000");
        System.out.println("Missing SKU lookup: " + (missing == null ? "Not found (handled gracefully)" : missing.getName()));
        Map<String, List<String>> skuGraph = SkuCorrector.build(cards);
        System.out.println("Fuzzy for NOPE-000: " + SkuFuzzy.suggest(catalogTable, skuGraph, "NOPE-000", 3).size()
                + " close match(es); typo MTG-BLB-28O -> " + SkuFuzzy.suggest(catalogTable, skuGraph, "MTG-BLB-28O", 3));

        System.out.println("\n[3] Physical Retrieval Navigation (Graph BFS, "
                + StoreNavigator.nodeCount(storeFloor) + " nodes / "
                + StoreNavigator.edgeCount(storeFloor) + " edges):");
        bfs(storeFloor, "Checkout Counter");
        String slot = StorageLocation.route(cards.get(0));
        System.out.println("Retrieve " + cards.get(0).getSku() + " from " + slot + ": "
                + String.join(" -> ", StoreNavigator.bfsPath(storeFloor, StoreNavigator.CHECKOUT, slot)));
        System.out.println("Box fill (slot: cards stored): " + StorageView.fillCounts(cards));

        System.out.println("\n[4] Hash collision demo (new HashMap<>(7) -> 8 buckets):");
        Map<String, Card> tiny = new HashMap<>(7);
        Card colA = new Card("MTG-OTJ-001", "Collision Demo A", "Demo Set", "Colorless", "Creature",
                2024, 1.0, 1, 50, false, "Bulk Box A (White/Blue)");
        Card colB = new Card("MTG-OTJ-009", "Collision Demo B", "Demo Set", "Colorless", "Creature",
                2024, 1.0, 1, 50, false, "Bulk Box A (White/Blue)");
        tiny.put(colA.getSku(), colA);
        tiny.put(colB.getSku(), colB);
        System.out.println("bucket(" + colA.getSku() + ") = " + bucketOf(colA.getSku())
                + ", bucket(" + colB.getSku() + ") = " + bucketOf(colB.getSku()) + " (same -> shared chain)");
        System.out.print("bucket chain: ");
        tiny.entrySet().forEach(e -> System.out.print("[" + e.getKey() + "] "));
        System.out.println("\nget both back: " + tiny.get(colA.getSku()).getName()
                + " / " + tiny.get(colB.getSku()).getName() + " (collision costs a short scan, not a loss)");

        System.out.println("\n[5] Member-module smoke (full demos live in each owner's main()):");
        CatalogIndex ci = new CatalogIndex();
        for (int i = 0; i < Math.min(5, cards.size()); i++) ci.put(cards.get(i));
        System.out.println("catalog: size=" + ci.size() + ", hit=" + (ci.get(cards.get(0).getName()) != null)
                + ", miss=" + (ci.get("Black Lotus") == null ? "graceful" : "BUG"));
        PriorityDesk pd = new PriorityDesk();
        for (int i = 0; i < Math.min(5, cards.size()); i++) pd.offer(cards.get(i));
        System.out.println("heap: peek=" + pd.peek().getName() + ", poll=" + pd.poll().getName()
                + ", runner-up=" + pd.peek().getName());
        List<Card> five = new ArrayList<>(cards.subList(0, Math.min(5, cards.size())));
        CardSorter.insertionSort(new ArrayList<>(five));
        long insCmp = CardSorter.comparisons;
        CardSorter.selectionSort(new ArrayList<>(five));
        System.out.println("sorts: insertion cmp=" + insCmp + ", selection cmp=" + CardSorter.comparisons + " (both verified in CardSorter demo)");
        TransactionLog tl = new TransactionLog();
        tl.log("INTAKE " + cards.get(0).getSku());
        System.out.println("log: entries=" + tl.all().size() + ", undo=" + tl.undoLast());
        IntakeBuffer ib = new IntakeBuffer();
        ib.stage(cards.get(0)); ib.stage(cards.get(1));
        System.out.println("intake: processNext=" + ib.processNext().getName() + ", undo=" + ib.undo().getName()
                + ", pending=" + ib.pending());
        String firstSku = cards.get(0).getSku();
        String typo = firstSku.substring(0, firstSku.length() - 1) + (firstSku.endsWith("0") ? "O" : "0");
        System.out.println("typo: '" + typo + "' suggests -> "
                + SkuCorrector.bfsSuggest(skuGraph, typo, 2));
    }

    private static void bfs(Map<String, List<String>> graph, String start) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.add(start);
        queue.offer(start);
        System.out.println("BFS Traversal Route from " + start + ":");
        while (!queue.isEmpty()) {
            String current = queue.poll();
            System.out.print(current + " -> ");
            for (String next : graph.getOrDefault(current, List.of())) {
                if (visited.add(next)) queue.offer(next);
            }
        }
        System.out.println("END");
    }
}
