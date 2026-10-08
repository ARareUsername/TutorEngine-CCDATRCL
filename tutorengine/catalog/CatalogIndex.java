package tutorengine.catalog;

import java.util.Collection;
import java.util.TreeMap;
import tutorengine.model.Card;

// OWNER: Dimazana — Catalog & Priority. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: Alphabetical card catalog. Cards are filed by name ignoring capital
// letters, so inorder() prints a neat A-Z sheet for the counter or website.
// Names are kept in a balanced tree, so lookup and removal stay fast as the
// catalog grows instead of slowing down like a flat list scan.
//
// HOW TO IMPLEMENT:
//   1. Keep put(), get(), and remove() as written. get() returns null for a name
//      that is not filed — in main(), print "Not found: <name>" for that case,
//      the same courtesy Karl's SKU search shows for missing codes.
//   2. Extend main() with 5 real cards. Script, printing every step:
//        put 5 -> get 1 existing name -> get 1 missing name ->
//        inorder() print (must come out A-Z) ->
//        remove 1 ordinary entry -> remove the FIRST entry (the minimum key) ->
//        inorder() print again.
//      Removal is a single remove(name) call in both cases; the tree rearranges
//      itself. Showing both proves leaf and first-key deletion work.
//   3. Trace packet (1 page): tree shape after each of the 5 filings, then the
//      hit, the miss, and the minimum key deletion. Use real card names.
//   4. Rehearse this line until it is reflex: index.remove(index.firstKey())
//      deletes the minimum key live — the instructor may ask for exactly that.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.catalog.CatalogIndex
//   Expected: full listing A-Z; missing name prints its message without crashing;
//   after both deletions the rest still print A-Z with nothing lost or doubled.
//
// DEFENSE: lookups halve the search space each step on average (logarithmic),
// degrading only when input arrives pre-sorted; name the three listing orders
// (alphabetical, top-down, bottom-up) — the tree yields alphabetical directly,
// know the other two cold. Note: firstKey() retrieves the minimum key in
// alphabetical order (the leftmost node in the tree structure), not the
// internal tree root, which is managed dynamically by Java's TreeMap.
// SPEC: S7 tree, S11 catalog operations, Template H trace, test T09.
public class CatalogIndex {
    private final TreeMap<String, Card> index = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public void put(Card c) {
        index.put(c.getName(), c);
    }

    public Card get(String name) {
        return index.get(name);
    }

    public Card remove(String name) {
        return index.remove(name);
    }

    public Collection<Card> inorder() {
        return index.values();
    }

    public int size() {
        return index.size();
    }

    public String firstKey() {
        return index.firstKey();
    }

    public static void main(String[] args) {
        CatalogIndex index = new CatalogIndex();

        System.out.println("CatalogIndex. OWNER: Dimazana. (implement 5-card demo)");
        System.out.println("=== CATALOG INDEX DEMO ===");
        System.out.println();

        // 1. PUT 5 REAL CARDS
        Card card1 = new Card(
                "MTG-BLB-280", "Forest", "Bloomburrow", "Colorless",
                "Land", 2024, 0.38, 18, 50, true, "Bulk Box C (Green/Colorless)"
        );
        index.put(card1);
        System.out.println("put -> " + card1.getName());

        Card card2 = new Card(
                "MTG-TSP-157", "Fury Sliver", "Time Spiral", "Red",
                "Creature", 2006, 0.48, 16, 75, true, "Bulk Box B (Black/Red)"
        );
        index.put(card2);
        System.out.println("put -> " + card2.getName());

        Card card3 = new Card(
                "MTG-ZEN-21", "Kor Outfitter", "Zendikar", "White",
                "Creature", 2009, 0.17, 23, 48, true, "Bulk Box A (White/Blue)"
        );
        index.put(card3);
        System.out.println("put -> " + card3.getName());

        Card card4 = new Card(
                "MTG-TMM2-5", "Spirit", "Modern Masters 2015 Tokens", "White",
                "Creature", 2015, 0.26, 39, 50, false, "Bulk Box A (White/Blue)"
        );
        index.put(card4);
        System.out.println("put -> " + card4.getName());

        Card card5 = new Card(
                "MTG-3ED-229", "Web", "Revised Edition", "Green",
                "Enchantment", 1994, 1.02, 2, 31, false, "Bulk Box C (Green/Colorless)"
        );
        index.put(card5);
        System.out.println("put -> " + card5.getName());

        System.out.println();

        // 2. GET 1 EXISTING CARD
        System.out.println("=== GET EXISTING CARD ===");
        Card found = index.get("Kor Outfitter");
        if (found != null) {
            System.out.println("Found: " + found.getName());
        } else {
            System.out.println("Not found: Kor Outfitter");
        }

        System.out.println();

        // 3. GET 1 MISSING CARD
        System.out.println("=== GET MISSING CARD ===");
        String missingName = "Black Lotus";
        Card missing = index.get(missingName);
        if (missing != null) {
            System.out.println("Found: " + missing.getName());
        } else {
            System.out.println("Not found: " + missingName);
        }

        System.out.println();

        // 4. INORDER PRINT
        System.out.println("=== INORDER / A-Z ===");
        for (Card card : index.inorder()) {
            System.out.println(card.getName());
        }

        System.out.println();

        // 5. REMOVE 1 ORDINARY ENTRY
        System.out.println("=== REMOVE ORDINARY ENTRY ===");
        String removeName = "Kor Outfitter";
        Card removed = index.remove(removeName);
        if (removed != null) {
            System.out.println("Removed: " + removed.getName());
        } else {
            System.out.println("Not found: " + removeName);
        }

        System.out.println();

        // 6. REMOVE FIRST ENTRY (MINIMUM KEY)
        System.out.println("=== REMOVE FIRST ENTRY (MINIMUM KEY) ===");
        String firstKey = index.firstKey();
        System.out.println("First key (minimum): " + firstKey);
        Card firstRemoved = index.remove(firstKey);
        if (firstRemoved != null) {
            System.out.println("Removed: " + firstRemoved.getName());
        } else {
            System.out.println("Not found: " + firstKey);
        }

        System.out.println();

        // 7. INORDER PRINT AGAIN
        System.out.println("=== FINAL INORDER / A-Z ===");
        for (Card card : index.inorder()) {
            System.out.println(card.getName());
        }

        System.out.println();
        System.out.println("Remaining cards: " + index.size());
    }
}
