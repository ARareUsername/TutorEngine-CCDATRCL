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

    }
}
