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
import tutorengine.algorithms.StoreNavigator;
import tutorengine.model.Card;
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
//     [2] Located: <first card> + a graceful "Not found" line for NOPE-000
//     [3] BFS Traversal Route from Checkout Counter over the 18-node cabinet map.
//   "Loaded 0" plus fallback means Dataset/cards.csv was not found from the
//   working directory — run from the project root.
public class Main {
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

        System.out.println("\n[2] Instant SKU Search (HashMap O(1)):");
        String hitSku = cards.get(0).getSku();
        Card found = catalogTable.get(hitSku);
        System.out.println("Located: " + (found != null ? found.getName() + " | Price: PHP " + found.getPrice() : "Not Found"));
        Card missing = catalogTable.get("NOPE-000");
        System.out.println("Missing SKU lookup: " + (missing == null ? "Not found (handled gracefully)" : missing.getName()));

        System.out.println("\n[3] Physical Retrieval Navigation (Graph BFS, "
                + StoreNavigator.nodeCount(storeFloor) + " nodes / "
                + StoreNavigator.edgeCount(storeFloor) + " edges):");
        bfs(storeFloor, "Checkout Counter");
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
