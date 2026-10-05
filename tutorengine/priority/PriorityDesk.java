package tutorengine.priority;

import java.util.Comparator;
import java.util.PriorityQueue;
import tutorengine.model.Card;

// OWNER: Mate C (Catalog & Priority). No other member edits this file.
// TASK: urgent-listing queue = max-heap via PriorityQueue on Card.calculatePriority().
// FORMULA: (price*0.50)+(demand*0.35)-(qty*3.0)+(foil?10:0); keep + justify in report S4.
// ACCEPT: offer/peek/poll; new highest-priority card jumps to top; reweight via comparator swap.
// DEFENSE: insert/remove O(log n) trace; live-change: change weights, add top item.
// SPEC: S7 Heap, S8 priority calc (2+ attrs), S11 heap add/remove, Template H heap trace.
public class PriorityDesk {
    private final PriorityQueue<Card> heap = new PriorityQueue<>(
            Comparator.comparingDouble(Card::calculatePriority).reversed());

    public void offer(Card c) { heap.offer(c); }
    public Card peek() { return heap.peek(); }
    public Card poll() { return heap.poll(); }
    public int size() { return heap.size(); }

    public static void main(String[] args) {
        System.out.println("PriorityDesk stub. OWNER: Mate C.");
    }
}
