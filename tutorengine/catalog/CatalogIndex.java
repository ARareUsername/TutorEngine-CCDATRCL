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
//        remove 1 ordinary entry -> remove the FIRST entry (the root) ->
//        inorder() print again.
//      Removal is a single remove(name) call in both cases; the tree rearranges
//      itself. Showing both proves leaf and root deletion work.
//   3. Trace packet (1 page): tree shape after each of the 5 filings, then the
//      hit, the miss, and the root deletion. Use real card names.
//   4. Rehearse this line until it is reflex: index.remove(index.firstKey())
//      deletes the root live — the instructor may ask for exactly that.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.catalog.CatalogIndex
//   Expected: full listing A-Z; missing name prints its message without crashing;
//   after both deletions the rest still print A-Z with nothing lost or doubled.
//
// DEFENSE: lookups halve the search space each step on average (logarithmic),
// degrading only when input arrives pre-sorted; name the three listing orders
// (alphabetical, top-down, bottom-up) — the tree yields alphabetical directly,
// know the other two cold.
// SPEC: S7 tree, S11 catalog operations, Template H trace, test T09.
public class CatalogIndex {
    private final TreeMap<String, Card> index = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public void put(Card c) { index.put(c.getName(), c); }
    public Card get(String name) { return index.get(name); }
    public Card remove(String name) { return index.remove(name); }
    public Collection<Card> inorder() { return index.values(); }
    public int size() { return index.size(); }

    public static void main(String[] args) {
        // TODO Dimazana: 5-card script from step 2. Print every step.
        System.out.println("CatalogIndex. OWNER: Dimazana. (implement 5-card demo)");
    }
}
