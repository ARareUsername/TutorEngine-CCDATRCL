package tutorengine.catalog;

import java.util.Collection;
import java.util.TreeMap;
import tutorengine.model.Card;

// Hi Dimazana! This file is yours. Nobody else should edit it, and please
// don't edit anyone else's — comments on pull requests for anything cross-file.
//
// What this does: it's the store's A-Z card catalog. Cards are filed by name
// (ignoring capital letters), so asking for everything in order prints a neat
// alphabetical sheet for the counter or the website. Lookups and removals stay
// fast even as the catalog grows, because names are kept in a balanced tree
// instead of a flat list.
//
// Your steps:
//  1. put(), get(), and remove() already work. For a name that isn't there,
//     get() returns null — in the demo, print "Not found: <name>" for that
//     case, the same kindness Karl shows for missing SKUs.
//  2. Extend main() with 5 real cards: file all 5, look up one that exists and
//     one that doesn't, print everything (must come out A-Z), then remove one
//     ordinary entry AND the very first entry (the root), and print again.
//  3. Both deletions must work — the tree handles the rearranging, you just
//     have to show both cases happening.
//  4. For the trace packet, draw one page: the tree growing as you file the 5
//     names, then the hit, the miss, and the root deletion. Real card names.
//  5. Rehearse this: the instructor may point at you and say "delete the root"
//     live. Practice doing index.remove(index.firstKey()) without hesitating.
//
// How to check your work:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.catalog.CatalogIndex
//   When you're done: the full listing is A-Z, the missing name prints its
//   friendly message without crashing, and after both deletions the remaining
//   names still print A-Z with nothing lost or doubled.
//
// For the defense, be ready to explain that lookups stay quick (they halve the
// search space each step on average), and to describe the three listing orders:
// alphabetical, top-down, and bottom-up. The tree gives you alphabetical for
// free; know the other two by heart. (Spec: S7 tree, S11 catalog operations,
// Template H trace, test T09.)
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
