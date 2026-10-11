package tutorengine.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tutorengine.algorithms.SkuCorrector;
import tutorengine.model.Card;
import tutorengine.util.CSVLoader;

// OWNER: Bondoc, Karl B. — typo-tolerant search. Do not edit files owned by
// others; SkuCorrector is only *called* here, never modified.
//
// PURPOSE: The clerk mistypes. Exact SKU lookup misses, so this fans out:
// direct field matches first (SKU prefix, SKU part, name part, set part),
// then Huypungco's 1-char-diff graph suggestions for true typos. Ranked,
// de-duplicated, capped — the search box shows the top few, never a dump.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.search.SkuFuzzy
//   Expected: exact SKU returns 1 card; "MTG-BLB-28O" (letter O) suggests the
//   real MTG-BLB-280; "forest" matches by name; "ZZZ" returns [] (empty-safe).
public final class SkuFuzzy {
    private SkuFuzzy() {}

    // Ranked suggestions for raw input; empty list = nothing close (never null).
    // Thin wrapper over SkuCorrector.multiSearch (single ranking source; the
    // extra limit caps the 10-result multiSearch output further for callers).
    public static List<Card> suggest(Map<String, Card> catalog,
                                     Map<String, List<String>> skuGraph,
                                     String raw, int limit) {
        List<Card> hits = SkuCorrector.multiSearch(catalog, skuGraph, raw);
        if (limit <= 0) return new ArrayList<>();
        return new ArrayList<>(hits.subList(0, Math.min(limit, hits.size())));
    }

    public static void main(String[] args) {
        List<Card> cards = CSVLoader.load("Dataset/cards.csv");
        Map<String, Card> catalog = new java.util.HashMap<>();
        for (Card c : cards) catalog.put(c.getSku(), c);
        Map<String, List<String>> g = SkuCorrector.build(cards);
        System.out.println("catalog=" + catalog.size() + " graph built over " + g.size() + " SKUs");
        System.out.println("exact MTG-BLB-280 -> " + names(suggest(catalog, g, "MTG-BLB-280", 5)));
        System.out.println("typo  MTG-BLB-28O -> " + names(suggest(catalog, g, "MTG-BLB-28O", 5)));
        System.out.println("name  forest      -> " + names(suggest(catalog, g, "forest", 5)));
        System.out.println("bogus ZZZ         -> " + names(suggest(catalog, g, "ZZZ", 5)) + " (empty-safe)");
        System.out.println("SkuFuzzy OK.");
    }

    private static List<String> names(List<Card> cards) {
        List<String> n = new ArrayList<>();
        for (Card c : cards) n.add(c.getName() + " [" + c.getSku() + "]");
        return n;
    }
}
