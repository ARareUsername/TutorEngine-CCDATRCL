package tutorengine.algorithms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tutorengine.model.Card;

// OWNER: Huypungco — Graph & Benchmark. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: Typo-tolerant SKU lookup. Scanner smudges and mistypes are the
// associate's top error, so when exact search misses, this suggests the nearest
// valid SKUs instead of a bare "not found". Every SKU is a node; two SKUs share
// an edge when they differ in exactly one character. Searching outward from the
// typo finds the closest real SKUs first.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.algorithms.SkuCorrector
//   Expected: the O-for-0 typo suggests the real codes within depth 2; an exact
//   code needs no suggestions; "ZZZ" yields an empty list plus a message.
//
// DEFENSE: build cost vs search cost O(V+E); what maxDepth=2 guarantees about
// suggestion quality.
// SPEC: S8 search application, tests T05/T07, Template H trace.
//
// For the defense, be ready to compare the build cost (every pair checked once)
// against the search cost (each dot and link visited once), and to say what a
// max depth of 2 guarantees about your suggestions. (Spec: S8 search use,
// tests T05/T07, Template H trace.)
public class SkuCorrector {
    // True only when same length and exactly one position differs.
    // "MTG-OTJ-055" vs "MTG-OTJ-056" -> true (last char).
    // "MTG-OTJ-055" vs "MTG-OTJ-065" -> true (one char: 5 vs 6 at index 8).
    // Different lengths -> false. Identical strings -> false (0 diffs).
    static boolean isOneCharDiff(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int diffs = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                diffs++;
                if (diffs > 1) return false;
            }
        }
        return diffs == 1;
    }

    // One node per SKU; link every pair with isOneCharDiff true.
    // Cost O(n^2 * L) where L = SKU length — acceptable even at n=5000.
    public static Map<String, List<String>> build(List<Card> cards) {
        Map<String, List<String>> g = new HashMap<>();
        for (Card c : cards) g.putIfAbsent(c.getSku(), new ArrayList<>());
        List<String> skus = new ArrayList<>(g.keySet());
        for (int i = 0; i < skus.size(); i++) {
            for (int j = i + 1; j < skus.size(); j++) {
                String a = skus.get(i), b = skus.get(j);
                if (isOneCharDiff(a, b)) {
                    g.get(a).add(b);
                    g.get(b).add(a);
                }
            }
        }
        return g;
    }

    // BFS outward from the typo up to maxDepth, collecting valid SKUs in visit order.
    // Seeds the queue with every graph node one edit away from typed, then walks
    // graph edges. Depth 1 = direct 1-char neighbors, depth 2 = neighbors-of-neighbors.
    // Exact match -> empty (no suggestions needed). No match -> empty list.
    // Search cost O(V+E): each node and edge visited once.
    public static List<String> bfsSuggest(Map<String, List<String>> g, String typed, int maxDepth) {
        List<String> out = new ArrayList<>();
        if (typed == null || g == null || g.isEmpty() || maxDepth < 1) return out;
        if (g.containsKey(typed)) return out; // exact SKU: no suggestions needed
        Set<String> seen = new HashSet<>();
        Map<String, Integer> depth = new LinkedHashMap<>();
        Deque<String> q = new ArrayDeque<>();
        // Seed: every node one edit away from the typo (depth 1).
        for (String sku : g.keySet()) {
            if (isOneCharDiff(sku, typed) && seen.add(sku)) {
                depth.put(sku, 1);
                q.offer(sku);
                out.add(sku);
            }
        }
        // Fan out along graph edges to maxDepth.
        while (!q.isEmpty()) {
            String cur = q.poll();
            int d = depth.get(cur);
            if (d >= maxDepth) continue;
            for (String nxt : g.getOrDefault(cur, List.of())) {
                if (seen.add(nxt)) {
                    depth.put(nxt, d + 1);
                    q.offer(nxt);
                    out.add(nxt);
                }
            }
        }
        return out;
    }

    private static Card card(String sku) {
        return new Card(sku, "Demo " + sku, "Demo Set", "Colorless", "Creature",
                2024, 10.0, 1, 50, false, "Bulk Box A (White/Blue)");
    }

    public static void main(String[] args) {
        // 6 SKUs differing by 1 character along the tail.
        List<Card> cards = new ArrayList<>();
        for (String sku : List.of("MTG-OTJ-050", "MTG-OTJ-051", "MTG-OTJ-052",
                "MTG-OTJ-053", "MTG-OTJ-054", "MTG-OTJ-055")) {
            cards.add(card(sku));
        }
        Map<String, List<String>> g = build(cards);
        System.out.println("nodes=" + g.size() + " (need 6)");

        // T07: O-for-0 typo -> real codes within depth 2.
        String typo = "MTG-OTJ-05O"; // letter O instead of zero
        List<String> s1 = bfsSuggest(g, typo, 2);
        System.out.println("typo '" + typo + "' suggestions(depth<=2): " + s1);
        System.out.println("T07 " + (!s1.isEmpty() ? "PASS" : "FAIL") + ": typo suggests real SKUs");

        // Exact SKU -> no suggestions needed.
        List<String> s2 = bfsSuggest(g, "MTG-OTJ-055", 2);
        System.out.println("exact 'MTG-OTJ-055' suggestions: " + s2 + " (need suggestions: none)");
        System.out.println("T08-part " + (s2.isEmpty() ? "PASS" : "FAIL") + ": exact needs none");

        // Gibberish -> empty list + message.
        List<String> s3 = bfsSuggest(g, "ZZZ", 2);
        System.out.println("gibberish 'ZZZ' suggestions: " + s3
                + (s3.isEmpty() ? " (no match — show 'not found' message)" : ""));
        System.out.println("T08 " + (s3.isEmpty() ? "PASS" : "FAIL") + ": gibberish empty-safe");

        // Unit spot-check from spec: 055 vs 056 true, 055 vs 065 true (one char).
        System.out.println("isOneCharDiff(055,056)=" + isOneCharDiff("MTG-OTJ-055", "MTG-OTJ-056")
                + " (expect true); (055,065)=" + isOneCharDiff("MTG-OTJ-055", "MTG-OTJ-065")
                + " (expect true); (055,055)=" + isOneCharDiff("MTG-OTJ-055", "MTG-OTJ-055")
                + " (expect false)");
        System.out.println("SkuCorrector OK.");
    }
}
