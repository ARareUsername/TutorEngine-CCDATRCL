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
import tutorengine.model.Card;

// OWNER: Bondoc, Karl B. — command-line demo and final integration point.
//
// PURPOSE: Stitches the pieces together on sample cards so the whole story is
// visible in one run: which card gets listed first, instant lookup by code, and
// the store walk. As members finish their classes, this demo grows to call them.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.Main
//   Expected sections:
//     [1] Highest Action Priority: Mana Drain (Priority Score: 1331.30)
//     [2] Located: Mana Drain | Price: PHP 2600.0
//     [3] BFS Traversal Route from Checkout Counter: ... -> END
//   A different card in [1] implicates the priority comparison; "Not Found" in
//   [2] means the lookup key does not match Card.getSku().
public class Main {
    public static void main(String[] args) {
        System.out.println("=============================================");
        System.out.println("   TUTORENGINE: TCG SINGLES INVENTORY ADT    ");
        System.out.println("=============================================");

        Map<String, Card> catalogTable = new HashMap<>(101);
        PriorityQueue<Card> priorityHeap = new PriorityQueue<>(
                Comparator.comparingDouble(Card::calculatePriority).reversed());
        Map<String, List<String>> storeFloor = new HashMap<>();

        for (String node : List.of("Checkout Counter", "Intake Sorting Desk",
                "Showcase Display (High Value)", "Bulk Box A (White/Blue)",
                "Bulk Box B (Black/Red)", "Bulk Box C (Green/Colorless)")) {
            storeFloor.put(node, new ArrayList<>());
        }
        addEdge(storeFloor, "Checkout Counter", "Intake Sorting Desk");
        addEdge(storeFloor, "Checkout Counter", "Showcase Display (High Value)");
        addEdge(storeFloor, "Intake Sorting Desk", "Bulk Box A (White/Blue)");
        addEdge(storeFloor, "Intake Sorting Desk", "Bulk Box B (Black/Red)");
        addEdge(storeFloor, "Intake Sorting Desk", "Bulk Box C (Green/Colorless)");

        Card c1 = new Card("MTG-MH3-001", "Ajani, Nacatl Pariah", "Modern Horizons 3",
                           "White", "Creature", 2024, 2150.00, 2, 92, true, "Showcase Display");
        Card c2 = new Card("MTG-OTJ-055", "Mana Drain", "Outlaws of Thunder Junction",
                           "Blue", "Instant", 2024, 2600.00, 1, 98, false, "Showcase Display");
        Card c3 = new Card("MTG-FDN-101", "Llanowar Elves", "Foundations",
                           "Green", "Creature", 2024, 25.00, 48, 45, false, "Bulk Box C");

        catalogTable.put(c1.getSku(), c1);
        catalogTable.put(c2.getSku(), c2);
        catalogTable.put(c3.getSku(), c3);

        priorityHeap.offer(c1);
        priorityHeap.offer(c2);
        priorityHeap.offer(c3);

        System.out.println("\n[1] Immediate Web Listing Priority (PriorityQueue Top):");
        Card urgentCard = priorityHeap.poll();
        System.out.printf("Highest Action Priority: %s (Priority Score: %.2f)%n",
                          urgentCard.getName(), urgentCard.calculatePriority());

        System.out.println("\n[2] Instant SKU Search (HashMap O(1)):");
        Card found = catalogTable.get("MTG-OTJ-055");
        System.out.println("Located: " + (found != null ? found.getName() + " | Price: PHP " + found.getPrice() : "Not Found"));

        System.out.println("\n[3] Physical Retrieval Navigation (Graph BFS):");
        bfs(storeFloor, "Checkout Counter");
    }

    private static void addEdge(Map<String, List<String>> graph, String a, String b) {
        graph.get(a).add(b);
        graph.get(b).add(a);
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
