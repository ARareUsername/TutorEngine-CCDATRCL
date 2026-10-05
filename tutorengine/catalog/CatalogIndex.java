package tutorengine.catalog;

import java.util.Collection;
import java.util.TreeMap;
import tutorengine.model.Card;

// OWNER: Mate C (Catalog & Priority). No other member edits this file.
// TASK: alphabetical catalog index via TreeMap<String(name), Card>.
// ACCEPT: put/get/remove + inorder print; delete of root works; missing key -> null + message.
// DEFENSE: O(log n) search/delete + ordered traversal trace; live-change: delete root.
// SPEC: S7 BST/Tree, S11 BST ops, Template H BST trace.
public class CatalogIndex {
    private final TreeMap<String, Card> index = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public void put(Card c) { index.put(c.getName(), c); }
    public Card get(String name) { return index.get(name); }
    public Card remove(String name) { return index.remove(name); }
    public Collection<Card> inorder() { return index.values(); }

    public static void main(String[] args) {
        System.out.println("CatalogIndex stub. OWNER: Mate C.");
    }
}
