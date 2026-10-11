package tutorengine.intake;

import java.util.ArrayDeque;
import java.util.Deque;
import tutorengine.model.Card;
import tutorengine.model.TransactionLog;

// OWNER: De Jesus — History & Intake. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: Models the scanning desk. Fresh scans wait in line and are processed
// first-in-first-out (queue); when the scanner misreads a card, the most recently
// processed card pops back out for correction, last-in-first-out (stack). A single
// ArrayDeque serves both roles: offer/poll at one end behave as a queue,
// push/pop at the same end behave as a stack, all in constant time.
//
// HOW TO IMPLEMENT:
//   1. Keep stage() (queue.offer), processNext() (queue.poll, then push the card
//      onto the undo stack), and undo() (undo.pop or null when empty) as written.
//   2. Keep the methods silent on empty input (return null). Print the "buffer is
//      empty" message in main(), not inside the methods, so other classes can
//      reuse them without console noise.
//   3. Extend main() with 3 real Card objects built inline, e.g.
//        new Card("MTG-OTJ-055", "Mana Drain", "Outlaws of Thunder Junction",
//                 "Blue", "Instant", 2024, 2600.00, 1, 92, false, "Showcase Display")
//      Script: stage A, B, C; processNext() must return A (queue order); undo()
//      must return A (stack order); processNext() again must return B. Print the
//      returned card name at every step so the order is visible on screen.
//   4. Each successful processNext() also calls TransactionLog.log("INTAKE <SKU>"),
//      keeping the diary and the desk consistent.
//   5. Trace packet (1 page): queue contents after each stage, process, and undo.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.intake.IntakeBuffer
//   Expected: first pickup A, undo returns A, next pickup B; no card skipped or
//   duplicated; empty operations print a message and return null.
//
// DEFENSE: demonstrate queue vs stack behavior in one run; why ArrayDeque is
// constant time at both ends.
// SPEC: S7 Stack/Queue, S11 stack-or-queue op, Template H trace.
public class IntakeBuffer {
    private final TransactionLog log = new TransactionLog();
    private final Deque<Card> queue = new ArrayDeque<>();
    private final Deque<Card> undo = new ArrayDeque<>();

    public void stage(Card c) { queue.offer(c); }
    public Card processNext() {
        Card c = queue.poll();
        if (c != null){
            undo.push(c); log.log("INTAKE " + c.getSku()); 
        }
        return c;
    }
    public Card undo() { return undo.isEmpty() ? null : undo.pop(); }
    public int pending() { return queue.size(); }

    public static void main(String[] args) {
        
        IntakeBuffer b = new IntakeBuffer();
        // 3 real cards from Dataset/cards.csv: Bird (line 33),
        // Temporal Manipulation (line 54), Gorion, Wise Mentor (line 59).
        Card a = new Card("MTG-TOTJ-7", "Bird", "Outlaws of Thunder Junction",
            "Blue", "Creature", 2024, 0.24, 4, 50, true, "Bulk Box A (White/Blue)");
        Card bq = new Card("MTG-SLD-1169", "Temporal Manipulation", "Secret Lair Drop",
            "Blue", "Sorcery", 2022, 16.97, 9, 93, true, "Bulk Box A (White/Blue)");
        Card cc = new Card("CLB-276", "Gorion, Wise Mentor", "Commander Legends: Battle for Baldur's Gate",
            "Multi", "Creature", 2024, 0.19, 9, 57, true, "Bulk Box C (Green/Colorless)");
        
        b.stage(a); b.stage(bq); b.stage(cc);
        System.out.println("pending = " + b.pending());
        // queue order (FIFO): first staged, first handed out
        System.out.println("processNext -> " + b.processNext().getName()); // Bird (A)
        // stack order (LIFO): the card just handed out is the one undo returns
        System.out.println("undo -> " + b.undo().getName());               // Bird (A)
        System.out.println("processNext -> " + b.processNext().getName()); // Temporal Manipulation (B)
        System.out.println();
        
        // drain the queue, then show the empty case
        System.out.println("processNext -> " + b.processNext().getName()); // Gorion, Wise Mentor (C)
        Card none = b.processNext();
        System.out.println(none == null ? "buffer is empty, nothing to process" : "processNext -> " + none.getName());
        System.out.println("pending = " + b.pending());                   // 0
        System.out.println();
        
        // empty the undo stack, then show its empty case
        System.out.println("undo -> " + b.undo().getName());             // Gorion, Wise Mentor (C, LIFO)
        System.out.println("undo -> " + b.undo().getName());            // Temporal Manipulation (B, LIFO)
        Card noUndo = b.undo();
        System.out.println(noUndo == null ? "buffer is empty, nothing to undo" : "undo -> " + noUndo);
        System.out.println("IntakeBuffer. OWNER: De Jesus.");
    }
}
//  2 + 3. Queue (FIFO) and Stack (LIFO) — `IntakeBuffer`, one shared trace packet

// D = MTG-TOTJ-7 Bird · E = MTG-PLST-WWK90 Searing Blaze · F = MTG-NPH-109 Fresh Meat (rows 33, 54, 59 of `Dataset/cards.csv`)

/*
 * ============================================================
 *  TRACE: Packet Queue + Undo Stack   (before → after)
 * ============================================================
 *  queue : FRONT is on the left (next out)
 *  undo  : TOP is on the left (next undo)
 *
 *  START
 *    queue [] | undo [] | pending 0
 *
 *  1. stage(D)
 *       queue   : [] → [D]
 *       undo    : [] (no change)
 *       pending : 0 → 1
 *       returns : void
 *
 *  2. stage(E)
 *       queue   : [D] → [D, E]
 *       undo    : [] (no change)
 *       pending : 1 → 2
 *       returns : void
 *
 *  3. stage(F)
 *       queue   : [D, E] → [D, E, F]
 *       undo    : [] (no change)
 *       pending : 2 → 3
 *       returns : void
 *
 *  4. processNext()      D leaves the queue → lands on the undo stack
 *       queue   : [D, E, F] → [E, F]
 *       undo    : [] → [D]
 *       pending : 3 → 2
 *       returns : D
 *
 *  5. processNext()      E leaves the queue → lands on top of D
 *       queue   : [E, F] → [F]
 *       undo    : [D] → [E, D]
 *       pending : 2 → 1
 *       returns : E
 *
 *  6. undo()             E is popped from the stack (newest out, LIFO)
 *       queue   : [F] (no change)
 *       undo    : [E, D] → [D]
 *       pending : 1 (no change)
 *       returns : E
 *
 *  7. processNext()      F leaves the queue → lands on top of D
 *       queue   : [F] → []
 *       undo    : [D] → [F, D]
 *       pending : 1 → 0
 *       returns : F
 *
 *  8. processNext()      queue is empty → nothing happens
 *       queue   : [] (no change)
 *       undo    : [F, D] (no change)
 *       pending : 0 (no change)
 *       returns : null (silent)
 *
 *  9. undo()             F is popped from the stack
 *       queue   : [] (no change)
 *       undo    : [F, D] → [D]
 *       pending : 0 (no change)
 *       returns : F
 *
 * 10. undo()             D is popped from the stack
 *       queue   : [] (no change)
 *       undo    : [D] → []
 *       pending : 0 (no change)
 *       returns : D
 *
 * 11. undo()             stack is empty → nothing happens
 *       queue   : [] (no change)
 *       undo    : [] (no change)
 *       pending : 0 (no change)
 *       returns : null (silent)
 *
 *  ------------------------------------------------------------
 *  FLOW OF A PACKET
 *
 *     stage(x)          processNext()          undo()
 *    ──────────▶  QUEUE  ──────────▶  UNDO STACK  ──────────▶  gone
 *                (FIFO)                  (LIFO)
 *
 *  KEY IDEA
 *    The queue is FIFO: first in, first out.
 *    The undo stack is LIFO: last in, first out.
 *    Empty queue or stack → returns null, no error.
 * ============================================================
 */
