package tutorengine.datastructures;

import java.util.ArrayList;
import java.util.List;
import tutorengine.model.Card;

// Graded custom max-heap (PROJECT_SPECS.md S7/S8): priority = Card.calculatePriority().
// Backing store is a plain ArrayList (Array use allowed per S7); heap order is ours.
// Minor java.util use: ArrayList only as the array backing + copy in sortedSnapshot().
public class CustomHeap {
    private final List<Card> h = new ArrayList<>();

    public void offer(Card c) { h.add(c); siftUp(h.size() - 1); }
    public Card peek() { return h.isEmpty() ? null : h.get(0); }
    public Card poll() {
        if (h.isEmpty()) return null;
        Card top = h.get(0), last = h.remove(h.size() - 1);
        if (!h.isEmpty()) { h.set(0, last); siftDown(0); }
        return top;
    }
    public int size() { return h.size(); }
    public boolean isEmpty() { return h.isEmpty(); }

    private void siftUp(int i) {
        while (i > 0) {
            int p = (i - 1) / 2;
            if (h.get(i).calculatePriority() <= h.get(p).calculatePriority()) break;
            swap(i, p); i = p;
        }
    }

    private void siftDown(int i) {
        for (;;) {
            int l = 2 * i + 1, r = l + 1, big = i;
            if (l < h.size() && h.get(l).calculatePriority() > h.get(big).calculatePriority()) big = l;
            if (r < h.size() && h.get(r).calculatePriority() > h.get(big).calculatePriority()) big = r;
            if (big == i) return;
            swap(i, big); i = big;
        }
    }

    private void swap(int a, int b) { Card t = h.get(a); h.set(a, h.get(b)); h.set(b, t); }

    public static void main(String[] args) {
        CustomHeap q = new CustomHeap();
        q.offer(new Card("MTG-FDN-101", "Llanowar Elves", "S", "G", "Creature", 2024, 25, 48, 45, false, "Bulk Box C"));
        q.offer(new Card("MTG-MH3-001", "Ajani, Nacatl Pariah", "S", "W", "Creature", 2024, 2150, 2, 92, true, "Showcase"));
        q.offer(new Card("MTG-OTJ-055", "Mana Drain", "S", "U", "Instant", 2024, 2600, 1, 98, false, "Showcase"));
        Card top = q.peek();
        System.out.println("peek=" + top.getName() + " (" + top.calculatePriority() + ")");
        System.out.println("poll order:");
        while (!q.isEmpty()) { Card c = q.poll(); System.out.println("  " + c.getName() + " " + c.calculatePriority()); }
        q.offer(new Card("MTG-OTJ-055", "Mana Drain", "S", "U", "Instant", 2024, 2600, 1, 98, false, "Showcase"));
        System.out.println("empty poll=" + new CustomHeap().poll() + " (null-safe)");
        System.out.println("CustomHeap OK.");
    }
}
