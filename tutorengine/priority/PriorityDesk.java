package tutorengine.priority;

import java.util.Comparator;
import java.util.PriorityQueue;
import tutorengine.model.Card;

// OWNER: Dimazana — Catalog & Priority. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: Answers "which card should the associate handle next?". Every card
// carries a priority score and this queue always hands out the highest-scoring
// card first.
//
// SCORE FORMULA (keep it, and justify it in report section S4 — it must combine
// at least two attributes):
//   (price x 0.50) + (demandScore x 0.35) - (quantity x 3.0) + (foil ? 10 : 0)
// Expensive, in-demand cards rank up; overstocked bulk sinks; foil adds a flat
// bonus for curl protection.
//
// HOW TO IMPLEMENT:
//   1. Keep offer(), peek(), and poll() as written. peek() looks without removing;
//      poll() removes and returns the top card.
//   2. Extend main() with 5 real cards: one obvious star (Mana Drain, price 2600,
//      demand 98, quantity 1) and one obvious bulk card (Llanowar Elves, price 25,
//      quantity 48). Print name + score at every step (call calculatePriority()).
//   3. Script: peek() must name Mana Drain; poll() removes it; the next peek()
//      must name the runner-up.
//   4. Then offer an even hotter card LAST and show peek() switching to it at once.
//      A newcomer jumping the line is the proof the queue truly prioritizes.
//   5. Rehearse reweighting: change one formula weight (e.g. double the foil bonus),
//      rebuild the queue by re-offering the cards, and show the new order — the
//      instructor may request exactly this live.
//   6. Trace packet (1 page): 3 arrivals bubbling upward step by step, then 1
//      removal sinking downward.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.priority.PriorityDesk
//   Expected: peek names Mana Drain first; poll removes it; next peek is the
//   runner-up; the late hot card jumps the line; every printed score matches a
//   hand calculation of the formula.
//
// DEFENSE: walk one card bubbling upward on paper; adding/removing costs
// logarithmic steps, peeking is instant.
// SPEC: S7 heap, S8 priority calc, S11 heap add/remove, Template H trace, test T10.
public class PriorityDesk {
    private final PriorityQueue<Card> heap = new PriorityQueue<>(
            Comparator.comparingDouble(Card::calculatePriority).reversed());

    public void offer(Card c) { heap.offer(c); }
    public Card peek() { return heap.peek(); }
    public Card poll() { return heap.poll(); }
    public int size() { return heap.size(); }

    public static void main(String[] args) {
        // TODO Dimazana: 5-card script from steps 1-3. Print name + priority each step.
        System.out.println("PriorityDesk. OWNER: Dimazana. (implement 5-card demo)");
    }
}
