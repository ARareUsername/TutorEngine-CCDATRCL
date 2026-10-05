package tutorengine.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tutorengine.model.Card;

// Hi Dimazana! Yours as well — same ownership rules as your other files.
//
// What this does: the project must sort real cards with two hand-written
// sorting methods (not the built-in sort). This file holds both — Insertion
// Sort and Selection Sort, ordering by card name — plus counters that record
// how many comparisons and moves each one needed. Those numbers feed the speed
// comparison table in the report, so count honestly.
//
// Your steps:
//  1. Write insertionSort(): walk the list, sliding each card left until it
//     sits among the already-sorted ones. Bump comparisons on every name
//     comparison and movements on every shift. Reset both counters at the start.
//  2. Write selectionSort(): repeatedly find the smallest remaining name and
//     swap it into place, counting the same way.
//  3. In main(), build the same 5 shuffled cards twice, sort one copy each way,
//     print both orders plus both counters, and confirm both match plain
//     List.sort (the demo should verify this itself and say so).
//  4. For the trace packet, draw one page: a full Insertion Sort run on 5 cards,
//     showing the row after every insertion.
//
// How to check your work:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.algorithms.CardSorter
//   When you're done: both methods print identical A-Z orders that agree with
//   List.sort, both print counters greater than zero, and running twice gives
//   fresh counts each time (no leftovers from the previous run).
//
// For the defense, know each method's best/worst behavior by heart (Insertion
// loves nearly-sorted input; Selection does the same work no matter what) and
// be ready to explain why tiny test sizes can make the "slower" method look
// faster. (Spec: S8 sorting, S9 sort-vs-sort, Template F.)
public class CardSorter {
    public static long comparisons, movements;
    private static final Comparator<Card> BY_NAME =
            (a, b) -> a.getName().compareToIgnoreCase(b.getName());

    public static void insertionSort(List<Card> a) {
        // TODO Dimazana: implement; reset comparisons/movements at start.
        comparisons = 0; movements = 0;
        System.out.println("TODO Dimazana: manual insertion sort by name");
    }

    public static void selectionSort(List<Card> a) {
        // TODO Dimazana: implement; reset comparisons/movements at start.
        comparisons = 0; movements = 0;
        System.out.println("TODO Dimazana: manual selection sort by name");
    }

    public static void main(String[] args) {
        // TODO Dimazana: 5-card shuffled script from step 3.
        System.out.println("CardSorter. OWNER: Dimazana. (implement demo)");
        new ArrayList<Card>().sort(null);
    }
}
