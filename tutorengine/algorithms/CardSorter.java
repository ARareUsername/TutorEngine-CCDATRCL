package tutorengine.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tutorengine.model.Card;

// OWNER: Dimazana — Catalog & Priority. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: The two required hand-written sorts over real cards, ordered by name
// ignoring capitals. Both count their own comparisons and moves; those counters
// feed the speed-comparison table in the report, so count honestly.
//
// HOW TO IMPLEMENT:
//  1. insertionSort(a): for each position i from 1 to end, hold card i aside and
//     shift every larger earlier card one slot right until the held card's slot
//     opens, then place it. Increment comparisons on each name comparison
//     (use c1.compareTo(c2)) and movements on each shift and placement. Reset
//     both counters to 0 on entry.
//  2. selectionSort(a): for each position i, scan positions i..end for the
//     smallest name, then swap it into i (skip the swap when it is already i,
//     but still count the comparisons). Same counter discipline as above.
//  3. In main(): build one 5-card list in shuffled order, copy it twice with
//     new ArrayList<>(shuffled), sort one copy each way, print both name orders
//     plus both counters, then print whether both equal a plain List.sort of the
//     original (the demo verifies itself and reports the verdict).
//  4. Trace packet (1 page): one full Insertion Sort run on 5 cards, showing the
//     row contents after every insertion.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.algorithms.CardSorter
//   Expected: both methods print identical A-Z orders that agree with List.sort;
//   both print counters greater than zero; running twice prints fresh counts
//   each time (no leftovers from the previous run).
//
// DEFENSE: Insertion Sort's behavior on nearly-sorted input vs worst case;
// Selection Sort doing the same work regardless; why tiny test sizes can make
// the theoretically slower method look faster.
// SPEC: S8 sorting, S9 sort-vs-sort, Template F.
public class CardSorter {
    public static long comparisons, movements;

    private static final Comparator<Card> BY_NAME =
            (a, b) -> a.getName().compareToIgnoreCase(b.getName());

    // INSERTION SORT
    public static void insertionSort(List<Card> a) {
        // TODO Dimazana: implement; reset comparisons/movements at start.
        comparisons = 0;
        movements = 0;
        System.out.println("TODO Dimazana: manual insertion sort by name");

        for (int i = 1; i < a.size(); i++) {
            Card current = a.get(i);
            int j = i - 1;

            while (j >= 0) {
                comparisons++;

                if (BY_NAME.compare(a.get(j), current) > 0) {
                    a.set(j + 1, a.get(j));
                    movements++;
                    j--;
                } else {
                    break;
                }
            }

            a.set(j + 1, current);
            movements++;
        }
    }

    // SELECTION SORT
    public static void selectionSort(List<Card> a) {
        // TODO Dimazana: implement; reset comparisons/movements at start.
        comparisons = 0;
        movements = 0;
        System.out.println("TODO Dimazana: manual selection sort by name");

        for (int i = 0; i < a.size() - 1; i++) {
            int smallest = i;

            for (int j = i + 1; j < a.size(); j++) {
                comparisons++;

                if (BY_NAME.compare(a.get(j), a.get(smallest)) < 0) {
                    smallest = j;
                }
            }

            if (smallest != i) {
                Card temp = a.get(i);
                a.set(i, a.get(smallest));
                a.set(smallest, temp);
                movements += 3;
            }
        }
    }

    // PRINT CARD NAMES
    private static void printNames(String label, List<Card> cards) {
        System.out.print(label + ": ");

        for (int i = 0; i < cards.size(); i++) {
            System.out.print(cards.get(i).getName());

            if (i < cards.size() - 1) {
                System.out.print(" -> ");
            }
        }

        System.out.println();
    }

    public static void main(String[] args) {
        
        List<Card> shuffled = new ArrayList<>();

        // Five real cards from cards.csv
        shuffled.add(new Card(
                "MTG-3ED-229", "Web", "Revised Edition",
                "Green", "Enchantment", 1994,
                1.02, 2, 31, false,
                "Bulk Box C (Green/Colorless)"
        ));

        shuffled.add(new Card(
                "MTG-ZEN-21", "Kor Outfitter", "Zendikar",
                "White", "Creature", 2009,
                0.17, 23, 48, true,
                "Bulk Box A (White/Blue)"
        ));

        shuffled.add(new Card(
                "MTG-BLB-280", "Forest", "Bloomburrow",
                "Colorless", "Land", 2024,
                0.38, 18, 50, true,
                "Bulk Box C (Green/Colorless)"
        ));

        shuffled.add(new Card(
                "MTG-TMM2-5", "Spirit",
                "Modern Masters 2015 Tokens",
                "White", "Creature", 2015,
                0.26, 39, 50, false,
                "Bulk Box A (White/Blue)"
        ));

        shuffled.add(new Card(
                "MTG-TSP-157", "Fury Sliver", "Time Spiral",
                "Red", "Creature", 2006,
                0.48, 16, 75, true,
                "Bulk Box B (Black/Red)"
        ));

        // TODO Dimazana: 5-card shuffled script from step 3.
        System.out.println("CardSorter. OWNER: Dimazana. (implement demo)");
        System.out.println("=== SORTING DEMO ===");

        printNames("Original order", shuffled);

        // Keep separate copies for both algorithms.
        List<Card> insertionList = new ArrayList<>(shuffled);
        List<Card> selectionList = new ArrayList<>(shuffled);
        List<Card> expected = new ArrayList<>(shuffled);

        // Test insertion sort.
        insertionSort(insertionList);
        long insertionComparisons = comparisons;
        long insertionMovements = movements;

        printNames("Insertion Sort", insertionList);
        System.out.println("Comparisons: " + insertionComparisons);
        System.out.println("Movements: " + insertionMovements);
        System.out.println();

        // Test selection sort.
        selectionSort(selectionList);
        long selectionComparisons = comparisons;
        long selectionMovements = movements;

        printNames("Selection Sort", selectionList);
        System.out.println("Comparisons: " + selectionComparisons);
        System.out.println("Movements: " + selectionMovements);
        System.out.println();

        // Verify both results using Java's built-in sort.
        expected.sort(BY_NAME);

        boolean insertionCorrect = insertionList.equals(expected);
        boolean selectionCorrect = selectionList.equals(expected);

        System.out.println("Insertion Sort matches List.sort(): "
                + insertionCorrect);
        System.out.println("Selection Sort matches List.sort(): "
                + selectionCorrect);
        System.out.println("Both sorts correct: "
                + (insertionCorrect && selectionCorrect));
    }
}
