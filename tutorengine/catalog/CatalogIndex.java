package tutorengine.catalog;

import java.util.Collection;
import java.util.TreeMap;
import tutorengine.model.Card;

public class CatalogIndex {
    private final TreeMap<String, Card> index =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

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

    public static void main(String[] args) {
        CatalogIndex index = new CatalogIndex();

        System.out.println("=== CATALOG INDEX DEMO ===");
        System.out.println();

        System.out.println("CatalogIndex. OWNER: Dimazana. (implement 5-card demo)");
    }
}
