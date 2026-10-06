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

        System.out.println("CatalogIndex. OWNER: Dimazana. (implement 5-card demo)");
        System.out.println("=== CATALOG INDEX DEMO ===");
        System.out.println();

        // 1. PUT 5 REAL CARDS

        Card card1 = new Card(
                "MTG-BLB-280",
                "Forest",
                "Bloomburrow",
                "Colorless",
                "Land",
                2024,
                0.38,
                18,
                50,
                true,
                "Bulk Box C (Green/Colorless)"
        );

            index.put(card1);
        System.out.println("put -> " + card1.getName());


        Card card2 = new Card(
                "MTG-TSP-157",
                "Fury Sliver",
                "Time Spiral",
                "Red",
                "Creature",
                2006,
                0.48,
                16,
                75,
                true,
                "Bulk Box B (Black/Red)"
        );

        index.put(card2);
        System.out.println("put -> " + card2.getName());

        Card card3 = new Card(
                "MTG-ZEN-21",
                "Kor Outfitter",
                "Zendikar",
                "White",
                "Creature",
                2009,
                0.17,
                23,
                48,
                true,
                "Bulk Box A (White/Blue)"
        );

        index.put(card3);
        System.out.println("put -> " + card3.getName());

        Card card4 = new Card(
                "MTG-TMM2-5",
                "Spirit",
                "Modern Masters 2015 Tokens",
                "White",
                "Creature",
                2015,
                0.26,
                39,
                50,
                false,
                "Bulk Box A (White/Blue)"
        );

        index.put(card4);
        System.out.println("put -> " + card4.getName());

        Card card5 = new Card(
                "MTG-3ED-229",
                "Web",
                "Revised Edition",
                "Green",
                "Enchantment",
                1994,
                1.02,
                2,
                31,
                false,
                "Bulk Box C (Green/Colorless)"
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
        // TreeMap automatically keeps the names in alphabetical order.

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

        // 6. REMOVE FIRST ENTRY

        System.out.println("=== REMOVE FIRST ENTRY ===");

        String firstKey = index.index.firstKey();

        System.out.println("First key: " + firstKey);

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
