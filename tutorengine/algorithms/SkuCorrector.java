package tutorengine.algorithms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tutorengine.model.Card;

// Hi Huypungco! Yours as well — same ownership rules as your navigator file.
//
// What this does: scanners smudge and fingers slip, so "MTG-OTJ-055" gets typed
// as "MTG-OTJ-05O" more often than anyone admits. When Karl's exact search comes
// up empty, this class suggests the closest real SKUs instead of just saying
// "not found". It treats every SKU as a dot and links dots that differ by
// exactly one character, then searches outward from the typo — the first real
// SKUs found are the best guesses.
//
// Your steps:
//  1. Write isOneCharDiff(a, b): true when both codes are the same length and
//     differ in exactly one spot. ("MTG-OTJ-055" vs "MTG-OTJ-056" is true.
//     Work out "MTG-OTJ-055" vs "MTG-OTJ-065" yourself — that's your first test.)
//  2. Write build(cards): one dot per SKU, linked to every other SKU it
//     differs from by one character. Comparing everything with everything is
//     fine even for thousands of cards — say so in the report.
//  3. Write bfsSuggest(): start from every real SKU one edit away from the
//     typo, search outward up to maxDepth, and collect real SKUs in visit
//     order. Nothing found: return an empty list and let the caller print it.
//  4. In main(), use 6 SKUs that differ by one character and ask about a
//     mistyped one like "MTG-OTJ-05O" (letter O instead of zero) — the real
//     codes should be suggested.
//  5. Karl will call your bfsSuggest() from the search box and print "Not
//     found. Did you mean: ...?" — your half is this file, his half is the UI.
//  6. For the trace packet, draw one page: the search fanning out level by
//     level from the mistyped code.
//
// How to check your work:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.algorithms.SkuCorrector
//   When you're done: the O-for-0 typo suggests the right codes within depth 2,
//   an exact correct code needs no suggestions, and pure gibberish like "ZZZ"
//   gets an empty list plus a message — never a crash.
//
// For the defense, be ready to compare the build cost (every pair checked once)
// against the search cost (each dot and link visited once), and to say what a
// max depth of 2 guarantees about your suggestions. (Spec: S8 search use,
// tests T05/T07, Template H trace.)
public class SkuCorrector {
    static boolean isOneCharDiff(String a, String b) {
        // TODO Huypungco: implement (step 1).
        return false;
    }

    public static Map<String, List<String>> build(List<Card> cards) {
        // TODO Huypungco: implement (step 2).
        System.out.println("TODO Huypungco: build 1-char-diff graph over " + cards.size() + " SKUs");
        return new HashMap<>();
    }

    public static List<String> bfsSuggest(Map<String, List<String>> g, String typed, int maxDepth) {
        // TODO Huypungco: implement (step 3). Hint: seed queue with 1-edit neighbors.
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        Deque<String> q = new ArrayDeque<>();
        System.out.println("TODO Huypungco: BFS suggest for " + typed);
        return out;
    }

    public static void main(String[] args) {
        // TODO Huypungco: demo from step 4.
        System.out.println("SkuCorrector. OWNER: Huypungco. (implement demo)");
    }
}
