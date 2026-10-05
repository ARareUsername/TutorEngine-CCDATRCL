package tutorengine.priority;

import java.util.Comparator;
import java.util.PriorityQueue;
import tutorengine.model.Card;

// Hi Dimazana! Yours as well — same ownership rules as your catalog file.
//
// What this does: it answers "which card should the associate handle next?"
// Every card gets a priority score, and this queue always hands out the
// highest-scoring card first — like an emergency room triage, but for cardboard.
//
// The score (keep this formula and defend it in report section S4 — it must
// combine at least two attributes):
//   (price x 0.50) + (demand x 0.35) - (quantity x 3.0) + (foil ? 10 : 0)
// In English: expensive and in-demand cards jump the line, overstocked bulk
// sinks down, and foils get a flat bonus because they need curl protection.
//
// Your steps:
//  1. offer(), peek(), and poll() already work — keep them.
//  2. Extend main() with 5 real cards: one obvious star (Mana Drain at 2600,
//     demand 98, only 1 in stock) and one obvious bulk card (Llanowar Elves,
//     cheap with 48 in stock).
//  3. Show peek() naming Mana Drain without removing it, poll() taking it off,
//     and the runner-up surfacing next.
//  4. Then offer an even hotter card LAST and show it jumping straight to the
//     front — that's the moment that proves the queue really prioritizes.
//  5. Rehearse swapping the formula weights (say, doubling the foil bonus) and
//     rebuilding the queue, because the instructor may ask for exactly that live.
//  6. For the trace packet, draw one page: 3 arrivals bubbling up, then one
//     removal sinking down.
//
// How to check your work:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.priority.PriorityDesk
//   When you're done: peek names Mana Drain first, poll removes it, the next
//   peek is the runner-up, the late hot card jumps the line, and every printed
//   score matches Card.calculatePriority() if you check by hand.
//
// For the defense, be ready to walk one card bubbling up the heap on paper,
// and to say that adding or removing takes roughly logarithmic steps while
// peeking is instant. (Spec: S7 heap, S8 priority formula, S11 heap ops,
// Template H trace, test T10.)
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
