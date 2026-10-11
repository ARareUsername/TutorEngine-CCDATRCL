package tutorengine.datastructures;

import java.util.ArrayList;
import java.util.List;
import tutorengine.model.Card;

// Graded custom BST (PROJECT_SPECS.md S7): alphabetical catalog by card name.
// Minor java.util use: ArrayList only to return traversal snapshots.
public class CustomBST {
    private static class Node {
        Card card; Node left, right;
        Node(Card c) { card = c; }
    }

    private Node root;
    private int size;

    private static int cmp(String a, String b) { return a.compareToIgnoreCase(b); }

    public void insert(Card c) { root = insert(root, c); }
    private Node insert(Node n, Card c) {
        if (n == null) { size++; return new Node(c); }
        int d = cmp(c.getName(), n.card.getName());
        if (d < 0) n.left = insert(n.left, c);
        else if (d > 0) n.right = insert(n.right, c);
        else n.card = c; // same name: replace
        return n;
    }

    public Card search(String name) {
        Node n = root;
        while (n != null) {
            int d = cmp(name, n.card.getName());
            if (d == 0) return n.card;
            n = d < 0 ? n.left : n.right;
        }
        return null;
    }

    public Card delete(String name) {
        Card[] gone = new Card[1];
        root = delete(root, name, gone);
        if (gone[0] != null) size--;
        return gone[0];
    }

    private Node delete(Node n, String name, Card[] gone) {
        if (n == null) return null;
        int d = cmp(name, n.card.getName());
        if (d < 0) n.left = delete(n.left, name, gone);
        else if (d > 0) n.right = delete(n.right, name, gone);
        else {
            gone[0] = n.card;
            if (n.left == null) return n.right;
            if (n.right == null) return n.left;
            Node m = n.right;
            while (m.left != null) m = m.left;
            n.card = m.card;
            n.right = delete(n.right, m.card.getName(), new Card[1]);
        }
        return n;
    }

    public List<Card> inorder() { List<Card> o = new ArrayList<>(size); in(root, o); return o; }
    private void in(Node n, List<Card> o) { if (n == null) return; in(n.left, o); o.add(n.card); in(n.right, o); }

    public List<Card> preorder() { List<Card> o = new ArrayList<>(size); pre(root, o); return o; }
    private void pre(Node n, List<Card> o) { if (n == null) return; o.add(n.card); pre(n.left, o); pre(n.right, o); }

    public List<Card> postorder() { List<Card> o = new ArrayList<>(size); post(root, o); return o; }
    private void post(Node n, List<Card> o) { if (n == null) return; post(n.left, o); post(n.right, o); o.add(n.card); }

    public int size() { return size; }

    public static void main(String[] args) {
        CustomBST t = new CustomBST();
        String[][] d = {
            {"MTG-OTJ-055", "Mana Drain"}, {"MTG-MH3-001", "Ajani, Nacatl Pariah"},
            {"MTG-FDN-101", "Llanowar Elves"}, {"MTG-BLB-280", "Forest"},
            {"MTG-TSP-157", "Fury Sliver"}};
        for (String[] r : d)
            t.insert(new Card(r[0], r[1], "S", "W", "Creature", 2024, 10, 1, 50, false, "Bulk Box C"));
        System.out.println("inorder (A-Z):");
        for (Card c : t.inorder()) System.out.println("  " + c.getName());
        System.out.println("search hit: " + t.search("Mana Drain").getSku());
        System.out.println("search miss: " + t.search("Nope") + " (null-safe)");
        t.delete("Forest"); // leaf-ish
        System.out.println("after delete Forest, size=" + t.size());
        t.delete("Ajani, Nacatl Pariah"); // root-ish
        System.out.println("after delete root-ish, size=" + t.size());
        for (Card c : t.inorder()) System.out.println("  " + c.getName());
        System.out.println("preorder=" + t.preorder().size() + " postorder=" + t.postorder().size());
        System.out.println("CustomBST OK.");
    }
}
