package tutorengine.algorithms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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

    // ---- field-fuzzy-search (OpenSpec change fuzzy-card-search) ----
    // Case-insensitive substring; null-safe on both sides, blank query never matches.
    static boolean matchesSubstring(String haystack, String query) {
        if (haystack == null || query == null || query.isEmpty()) return false;
        return haystack.toLowerCase().contains(query.toLowerCase());
    }

    // Queries shorter than 2 chars match nothing (spec short-query guard).
    static boolean tooShort(String q) {
        return q == null || q.trim().length() < 2;
    }

    static List<Card> cap(List<Card> hits) {
        return hits.size() <= 10 ? hits : new ArrayList<>(hits.subList(0, 10));
    }

    static void collectName(List<Card> cards, String q, List<Card> prefix, List<Card> substr) {
        for (Card c : cards) {
            String name = c.getName() == null ? "" : c.getName();
            if (name.toLowerCase().startsWith(q.toLowerCase())) prefix.add(c);
            else if (matchesSubstring(name, q)) substr.add(c);
        }
        prefix.sort(Comparator.comparing(Card::getName, String::compareToIgnoreCase));
        substr.sort(Comparator.comparing(Card::getName, String::compareToIgnoreCase));
    }

    // 1.1 Name: prefix hits rank before mid-name hits.
    public static List<Card> searchByName(List<Card> cards, String q) {
        List<Card> out = new ArrayList<>();
        if (cards == null || tooShort(q)) return out;
        List<Card> prefix = new ArrayList<>(), substr = new ArrayList<>();
        collectName(cards, q.trim(), prefix, substr);
        out.addAll(prefix); out.addAll(substr);
        return cap(out);
    }

    // 1.2 Mana: strip braces/whitespace + uppercase, so "2G", "2g", "{2}{G}" agree.
    public static String normalizeMana(String mana) {
        if (mana == null) return "";
        return mana.replaceAll("[\\{\\}\\s]", "").toUpperCase();
    }

    public static List<Card> searchByManaCost(List<Card> cards, String q) {
        List<Card> out = new ArrayList<>();
        if (cards == null || tooShort(q)) return out;
        String want = normalizeMana(q.trim());
        if (want.isEmpty()) return out;
        for (Card c : cards) {
            String have = normalizeMana(c.getManaCost());
            if (!have.isEmpty() && have.equals(want)) out.add(c); // blank mana never matches
        }
        return cap(out);
    }

    // 1.3 Type substring, e.g. "creature" -> all 30 creatures, no lands.
    public static List<Card> searchByType(List<Card> cards, String q) {
        List<Card> out = new ArrayList<>();
        if (cards == null || tooShort(q)) return out;
        for (Card c : cards)
            if (matchesSubstring(c.getCardType(), q.trim())) out.add(c);
        return cap(out);
    }

    // 1.4 Stats as literal strings ("*" included, no arithmetic); a blank side
    // is a wildcard; both blank matches nothing (no constraint given at all).
    public static List<Card> searchByStats(List<Card> cards, String power, String toughness) {
        List<Card> out = new ArrayList<>();
        if (cards == null) return out;
        String p = power == null ? "" : power.trim();
        String t = toughness == null ? "" : toughness.trim();
        if (p.isEmpty() && t.isEmpty()) return out;
        for (Card c : cards) {
            String cp = c.getPower() == null ? "" : c.getPower();
            String ct = c.getToughness() == null ? "" : c.getToughness();
            if (!p.isEmpty() && !cp.equals(p)) continue;
            if (!t.isEmpty() && !ct.equals(t)) continue;
            out.add(c);
        }
        return cap(out);
    }

    // 1.5 Set: code OR name, so "tsp" and "time spiral" both hit Fury Sliver.
    public static List<Card> searchBySet(List<Card> cards, String q) {
        List<Card> out = new ArrayList<>();
        if (cards == null || tooShort(q)) return out;
        for (Card c : cards)
            if (matchesSubstring(c.getSetCode(), q.trim()) || matchesSubstring(c.getSetName(), q.trim()))
                out.add(c);
        return cap(out);
    }

    // ---- multi-field-search: one query, fixed ranking, deduplicated ----
    // SKU-exact > SKU-1diff > name-prefix > name-substring > type/mana/set.
    public static List<Card> multiSearch(Map<String, Card> catalog,
                                         Map<String, List<String>> graph, String query) {
        List<Card> out = new ArrayList<>();
        if (catalog == null || tooShort(query)) return out;
        String q = query.trim();
        List<Card> cards = new ArrayList<>(catalog.values());
        Card exact = catalog.get(q);
        if (exact != null) { out.add(exact); return out; } // exact hit stands alone
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.function.Consumer<Card> add = c -> {
            if (out.size() < 10 && seen.add(c.getSku())) out.add(c);
        };
        if (graph != null)
            for (String sku : bfsSuggest(graph, q, 1)) {
                Card c = catalog.get(sku);
                if (c != null) add.accept(c);
            }
        List<Card> prefix = new ArrayList<>(), substr = new ArrayList<>();
        collectName(cards, q, prefix, substr);
        prefix.forEach(add);
        substr.forEach(add);
        searchByType(cards, q).forEach(add);
        searchByManaCost(cards, q).forEach(add);
        searchBySet(cards, q).forEach(add);
        return out;
    }

    // Which tier a multiSearch hit came from (for "closest: name [field]" lines).
    public static String matchedField(Map<String, Card> catalog,
                                      Map<String, List<String>> graph, String query, Card hit) {
        if (catalog == null || hit == null || tooShort(query)) return "?";
        String q = query.trim();
        if (hit.getSku().equals(q)) return "SKU";
        if (graph != null && bfsSuggest(graph, q, 1).contains(hit.getSku())) return "SKU~1";
        List<Card> prefix = new ArrayList<>(), substr = new ArrayList<>();
        collectName(new ArrayList<>(catalog.values()), q, prefix, substr);
        if (prefix.contains(hit)) return "name-prefix";
        if (substr.contains(hit)) return "name";
        if (matchesSubstring(hit.getCardType(), q)) return "type";
        if (!normalizeMana(q).isEmpty() && normalizeMana(hit.getManaCost()).equals(normalizeMana(q))) return "mana";
        if (matchesSubstring(hit.getSetCode(), q) || matchesSubstring(hit.getSetName(), q)) return "set";
        return "?";
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

        // 2.2 Per-field demo over the real 60-row dataset, printing the matched field.
        System.out.println("\n--- field-fuzzy-search over Dataset/cards.csv ---");
        List<Card> real = tutorengine.util.CSVLoader.load("Dataset/cards.csv");
        Map<String, Card> catalog = new HashMap<>();
        for (Card c : real) catalog.put(c.getSku(), c);
        Map<String, List<String>> realGraph = build(real);
        String[][] fieldQueries = {
            {"name 'forest'", "forest"}, {"mana '2g'", "2g"}, {"type 'creature'", "creature"},
            {"set 'tsp'", "tsp"}, {"short 'a'", "a"}, {"typo 'MTG-BLB-28O'", "MTG-BLB-28O"},
            {"gibberish 'ZZZ'", "ZZZ"},
        };
        for (String[] fq : fieldQueries) {
            List<Card> hits = multiSearch(catalog, realGraph, fq[1]);
            StringBuilder sb = new StringBuilder(fq[0] + " -> " + hits.size() + " hit(s)");
            for (int i = 0; i < Math.min(3, hits.size()); i++)
                sb.append("; ").append(hits.get(i).getName())
                  .append(" [").append(matchedField(catalog, realGraph, fq[1], hits.get(i))).append("]");
            System.out.println(sb);
        }
        System.out.println("stats power=3 -> " + searchByStats(real, "3", "").size()
                + " hit(s) (toughness wildcard)");
        System.out.println("SkuCorrector OK.");
    }
}
