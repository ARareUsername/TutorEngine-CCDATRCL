package tutorengine.util;

import java.util.ArrayList;
import java.util.List;
import tutorengine.algorithms.CardSorter;
import tutorengine.model.Card;

// Hi Huypungco! Yours as well — same ownership rules as your other files.
//
// What this does: the report can't just claim "this sort is faster" — it has
// to prove it with timings. This class is the stopwatch: it races Insertion
// Sort against Selection Sort, and instant SKU lookup against a slow
// one-by-one scan, at 100, 500, 1000, and 5000 cards, using the nanosecond
// clock. Its table goes straight into the report.
//
// Your steps:
//  1. Write makeCards(n): invent n fake cards on the spot (SKUs MTG-GEN-0001
//     upward, shuffled names, plus a reverse-sorted copy). Write the recipe
//     down for report section S2 — invented data is allowed, but only with
//     documented rules.
//  2. For each size 100, 500, 1000, 5000:
//     a. Sorting race: copy the list twice, time Dimazana's insertionSort
//        against selectionSort, and note time plus both counters.
//     b. Search race: time Karl-style instant lookup against a slow linear
//        scan for the same SKU in the same list.
//  3. Print one report-ready row per race: size, what was tested, which method,
//     nanoseconds, comparisons, and your one-line observation. Also save the
//     whole table to BenchmarkResults/bench.txt.
//  4. Write two honest sentences per pairing: who won, and where the numbers
//     disagree with theory on small inputs (startup jitters, CPU caches, and
//     the clock itself all muddy tiny measurements — that's expected, say it).
//
// How to check your work:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.util.BenchmarkSuite
//   When you're done: 16 rows print (4 sizes x Insertion, Selection, instant
//   search, linear search), every row has all six columns, times clearly grow
//   with size, and bench.txt contains the same table.
//
// For the defense, be ready to say why the nanosecond clock beats the
// millisecond one here, and why both sides of each race must run on the exact
// same data or the comparison is meaningless. (Spec: S9 measurements, S11
// benchmark display, Templates F and E.)
public class BenchmarkSuite {
    static List<Card> makeCards(int n) {
        // TODO Huypungco: implement generator (step 1).
        return new ArrayList<>();
    }

    public static void run() {
        // TODO Huypungco: implement loops (step 2) + print rows (step 3).
        System.out.println("TODO Huypungco: nanoTime loops over 100/500/1000/5000");
        CardSorter.comparisons = 0;
    }

    public static void main(String[] args) {
        run();
        System.out.println("BenchmarkSuite. OWNER: Huypungco.");
    }
}
