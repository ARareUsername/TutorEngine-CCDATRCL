# TutorEngine: Magic: The Gathering Singles & Storage Optimization System

A Java-based Data Structures and Algorithms decision system designed for local game stores (LGS) to optimize card singles intake, cataloging, priority-based web fulfillment, and physical storage retrieval.

> Per `PROJECT_SPECS.md` §10 (black text): if any built-in structure is used
> for a minor non-graded feature, it must be disclosed and justified.
> Graded structures stay custom-built; use `java.util` only for minor
> non-graded support with disclosure.

## 1. Domain Workflow to DSA Architecture Mapping

| LGS Physical Workflow Step | Software / Decision Feature | Data Structure / Algorithm | Technical Justification & Complexity |
|---|---|---|---|
| Intake & Scanning | Batch card parsing & dataset staging | `java.util.ArrayList<Card>` (`Card[]` also fine) | Resizable raw batch staging for loading, sorting, and benchmarking. |
| Audit & Transaction History | Sequential card intake / change logs | `java.util.LinkedList` | Efficient `O(1)` insertion at head/tail for real-time audit trails. |
| Intake Staging & Undo Buffer | Scanner intake buffer & error reversal | `java.util.ArrayDeque` (as `Queue` / `Stack`) | FIFO processing into inventory; LIFO rollback for scanner misreads. |
| Grouping & Catalog Index | Alphabetical card catalog & set ordering | `java.util.TreeMap<String, Card>` | Keeps records ordered; `O(log n)` search/deletion and ordered traversal for catalog sheets. |
| Instant POS / Clerk Lookup | Direct SKU/Card ID search | `java.util.HashMap<String, Card>` | Average `O(1)` lookup for rapid counter requests. |
| Restock & Web-Order Priority | High-value and urgent listing queue | `java.util.PriorityQueue<Card>` (max-heap via reversed comparator on priority formula) | Dynamically surfaces high-priority cards for listing/fulfillment. |
| Store Bulk Boxes & Shelving Layout | Store mapping & card retrieval path | `java.util.Map<String, List<String>>` adjacency list | Nodes represent counter, sorting tables, showcase cases, and bulk boxes; edges represent aisle pathways. |
| Box Retrieval Navigation | Shortest physical retrieval route | BFS with `java.util.ArrayDeque` | Computes the minimum-hop path from the checkout desk to target storage boxes. |
| Storage Zone Audit | Section reachability & dependency audit | DFS (iterative with `ArrayDeque` or recursive) | Explores connected shelf zones to verify reachability and detect cyclic layout dependencies. |
| Intake Batch Sorting | Alphabetical and chronological sorting | `java.util.Collections.sort` / `List.sort` / `Arrays.sort` (compare Insertion vs Selection manually in benchmarks) | In-place comparisons across varied batch sizes. |

## 2. Mathematical Priority Formula (Heap)

To rank which cards require immediate web listing, grading, or secure display case storage, the custom heap uses a justified multi-attribute formula:

```text
Priority Score = (Market Price x 0.50) + (Demand Index x 0.35) - (Stock Quantity x 3.0) + (Foil Multiplier x 10.0)
```

- **Market Price (0.00 – 1000.00+):** Prioritizes high-value cards requiring immediate safe keeping or insurance tracking.
- **Demand Index (1 – 100):** Sourced from meta-game velocity (e.g., tournament playability).
- **Stock Quantity:** Penalizes overstocked bulk singles to prioritize scarce inventory.
- **Foil Multiplier (0 or 1):** Flat bonus for premium versions needing specialized storage to prevent curling.

## 3. Directory Layout (Package Structure)

```text
tutorengine/
├── model/
│   ├── Card.java                      // Core single record (SKU, name, set, price, condition)
│   ├── StorageLocation.java           // Graph vertex representing box, shelf, or display case
│   └── TransactionLog.java            // Wrapper for linked-list transaction history (java.util.LinkedList)
├── algorithms/
│   ├── CardSorter.java                // Sorting via List.sort / Arrays.sort (Insertion vs Selection for benchmarks)
│   └── StoreNavigator.java            // Graph BFS (minimum hops) & DFS (reachability) over java.util adjacency list
├── util/
│   ├── CSVLoader.java                 // Parser for scanner CSV exports
│   └── BenchmarkSuite.java            // System.nanoTime() comparative timing harness
└── Main.java                          // CLI driver with interactive workflows
```

> `datastructures/` (CustomLinkedList, CustomStack, CustomQueue, CustomBST,
> CustomHeap, CustomHashTable, CustomGraph) holds the graded custom
> structures. `java.util` only for minor non-graded helpers, disclosed
> per §10.

## 4. Core Implementation Skeletons

### 4.1 Card Entity (Card.java)

```java
package tutorengine.model;

public class Card implements Comparable<Card> {
    private String sku;            // Unique identifier (e.g., "MTG-OTJ-001")
    private String name;           // e.g., "Oko, the Ringleader"
    private String setName;        // e.g., "Outlaws of Thunder Junction"
    private String color;          // W, U, B, R, G, Multi, Colorless
    private String cardType;       // Creature, Instant, Land, etc.
    private int releaseYear;       // e.g., 2024
    private double price;          // Market value in PHP/USD
    private int quantity;          // Physical copies in stock
    private int demandScore;       // 1 - 100 popularity metric
    private boolean isFoil;        // Premium finish flag
    private String boxLocationId;  // Target graph node (e.g., "BOX-GREEN-03")

    public Card(String sku, String name, String setName, String color, String cardType,
                int releaseYear, double price, int quantity, int demandScore, boolean isFoil, String boxLocationId) {
        this.sku = sku;
        this.name = name;
        this.setName = setName;
        this.color = color;
        this.cardType = cardType;
        this.releaseYear = releaseYear;
        this.price = price;
        this.quantity = quantity;
        this.demandScore = demandScore;
        this.isFoil = isFoil;
        this.boxLocationId = boxLocationId;
    }

    public double calculatePriority() {
        double foilBonus = isFoil ? 10.0 : 0.0;
        return (price * 0.50) + (demandScore * 0.35) - (quantity * 3.0) + foilBonus;
    }

    @Override
    public int compareTo(Card other) {
        return this.name.compareToIgnoreCase(other.name);
    }

    // Getters and standard string formatters
    public String getSku() { return sku; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getBoxLocationId() { return boxLocationId; }
}
```

### 4.2 SKU Lookup (java.util.HashMap)

```java
package tutorengine;

import java.util.HashMap;
import java.util.Map;
import tutorengine.model.Card;

Map<String, Card> catalogTable = new HashMap<>(101);
catalogTable.put(c1.getSku(), c1);   // O(1) average insert
Card found = catalogTable.get(sku);  // O(1) average lookup
catalogTable.remove(sku);
```

### 4.3 Priority Queue (java.util.PriorityQueue as Max-Heap)

```java
import java.util.Comparator;
import java.util.PriorityQueue;
import tutorengine.model.Card;

PriorityQueue<Card> priorityHeap = new PriorityQueue<>(
        Comparator.comparingDouble(Card::calculatePriority).reversed());

priorityHeap.offer(card);       // insert
Card urgent = priorityHeap.poll(); // extractMax
Card top = priorityHeap.peek();    // view max without removing
```

### 4.4 Store Graph & BFS (java.util adjacency list + ArrayDeque)

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

Map<String, List<String>> storeFloor = new HashMap<>();
java.util.function.Consumer<String> addNode = n -> storeFloor.putIfAbsent(n, new ArrayList<>());
java.util.function.BiConsumer<String, String> addEdge = (a, b) -> {
    storeFloor.get(a).add(b);
    storeFloor.get(b).add(a); // undirected store paths
};

// BFS traversal from start
Set<String> visited = new HashSet<>();
Deque<String> queue = new ArrayDeque<>();
visited.add(start);
queue.offer(start);
while (!queue.isEmpty()) {
    String current = queue.poll();
    System.out.print(current + " -> ");
    for (String next : storeFloor.getOrDefault(current, List.of())) {
        if (visited.add(next)) queue.offer(next);
    }
}
System.out.println("END");
```

## 5. Main Execution Flow (Main.java)

```java
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

public class Main {
    public static void main(String[] args) {
        System.out.println("=============================================");
        System.out.println("   TUTORENGINE: TCG SINGLES INVENTORY ADT    ");
        System.out.println("=============================================");

        // 1. java.util structures (no custom collections)
        Map<String, Card> catalogTable = new HashMap<>(101);
        PriorityQueue<Card> priorityHeap = new PriorityQueue<>(
                Comparator.comparingDouble(Card::calculatePriority).reversed());
        Map<String, List<String>> storeFloor = new HashMap<>();

        // 2. Configure Store Layout Graph
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

        // 3. Ingest Sample Card Singles
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

        // 4. Demonstrate Decisions
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
```

## 6. Verification and Benchmarking Plan

- **Dataset Scaling:** Benchmark `HashMap.get()` vs. `LinkedList` search across 100, 500, 1,000, and 5,000 generated card records using `System.nanoTime()`.
- **Sorting Comparison:** Measure execution time and movements between Insertion Sort and Selection Sort (manual implementations for the benchmark) vs. `List.sort` using duplicate and reverse-ordered card name arrays.
- **Defense Preparation:** Ensure the code base supports live instructor modifications, such as dynamically updating priority weights (swap the `PriorityQueue` comparator), deleting entries from the `TreeMap` catalog, or injecting collision-forcing keys into the `HashMap`.

## 7. Member Assignments (conflict-free: one owner per file)

> Rule: edit ONLY your files. Each file header names its owner. Every file has a `main()` demo so it presents standalone (`javac <file> && java <class>`). Need ≥15 meaningful commits total, all members, across days.

| Member | Owns (do not touch others') | Must deliver | Presents |
|---|---|---|---|
| Bondoc, Karl B. (done) | `tutorengine/TutorEngineUI.java`, `tutorengine/Main.java`, `tutorengine/model/Card.java` | SKU search (`HashMap`), List Catalog, seed data | Search hit/miss + collision note |
| David, Abraham John D. — Data & Loader | `tutorengine/util/CSVLoader.java`, `Dataset/cards.csv` | 50+ rows, bad-row handling, generation rules | Dataset + Template C |
| De Jesus, Aeon Miles J. — History & Intake | `tutorengine/model/TransactionLog.java`, `tutorengine/intake/IntakeBuffer.java` | `LinkedList` log, `ArrayDeque` FIFO+LIFO, empty-safe | List + queue/stack traces |
| Dimazana, Amiel Benedict R. — Catalog/Priority/Sort | `tutorengine/catalog/CatalogIndex.java`, `tutorengine/priority/PriorityDesk.java`, `tutorengine/algorithms/CardSorter.java` | `TreeMap` ops, heap + formula, Insertion vs Selection + counts | BST / heap / sort traces |
| Huypungco, Matthew James M. — Graph & Benchmark | `tutorengine/model/StorageLocation.java`, `tutorengine/algorithms/StoreNavigator.java`, `tutorengine/algorithms/SkuCorrector.java`, `tutorengine/util/BenchmarkSuite.java` | 10 nodes/15 edges, BFS path + DFS, typo suggest, `nanoTime` 100/500/1000/5000 table | BFS/DFS + benchmark |

### Per-member checklist (copy into your PR)
1. Implement TODOs in your files only; keep `main()` demo passing.
2. Add tests: David: load 50; De Jesus: T01-T03; Dimazana: T04/T09/T10 + sort pass; Huypungco: T07/T08 + benchmark.
3. Add trace (1 page, real data) to `TracePacket/` named after your file.
4. Commit on your own branch (`david/...`, `de-jesus/...`, `dimazana/...`, `huypungco/...`), open PR, meaningful messages (no "update"/"fix").
5. Fill your Template I row: files, commit links, defense Qs.

### Future assignments
Add new work as a NEW file with an OWNER header (never split one file across members). Register it in the table above + `## 3 Directory Layout` in the same PR.
