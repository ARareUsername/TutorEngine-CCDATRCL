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
        if (c != null) {
            undo.push(c);
            log.log("INTAKE " + c.getSku());
        }
        return c;
    }
    public Card undo() { return undo.isEmpty() ? null : undo.pop(); }
    public int pending() { return queue.size(); }
    public TransactionLog getLog() { return log; }
    
    public TransactionLog getlog(){
        return log;
    }
    // TRACE (1 page) - queue + stack.
    // D = Bird MTG-TOTJ-7, E = Temporal Manipulation MTG-SLD-1169,
    // F = Gorion, Wise Mentor MTG-CLB-276   (Dataset/cards.csv lines 33, 54, 59)
    //
    //   step   operation             queue (front->back)   undo (top->bottom)   pending   returns
    //     0    start                 []                    []                     0       -
    //     1    stage(D)  offer       [D]                   []                     1       -
    //     2    stage(E)              [D, E]                []                     2       -
    //     3    stage(F)              [D, E, F]             []                     3       -
    //     4    processNext() poll    [E, F]                [D]                    2       D
    //     5    processNext()         [F]                   [E, D]                 1       E
    //     6    undo()  pop           [F]                   [D]                    1       E
    //     7    processNext()         []                    [F, D]                 0       F
    //     8    processNext() empty   []                    [F, D]                 0       null
    //     9    undo()                []                    [D]                    0       F
    //     10   undo()                []                    []                     0       D
    //     11   undo()  empty         []                    []                     0       null
    //
    // Queue out: D, E, F (first in, first out).  Undo lane out: E, F, D (last in, first out).
    // FIFO happens at step 4 (poll), LIFO at step 6 (pop).
    // A misread card returns at step 6 while D is still buried under it, so it can be fixed and
    // staged again without touching the waiting line.  Every processNext() also writes
    // "INTAKE <SKU>" to the TransactionLog diary (steps 4, 5, 7).  Empty ops return null, silent.

    public static void main(String[] args) {
        IntakeBuffer b = new IntakeBuffer();

        // 3 real cards from Dataset/cards.csv: Bird (line 33),
        // Temporal Manipulation (line 54), Gorion, Wise Mentor (line 59).
        Card a = new Card("MTG-TOTJ-7", "Bird", "Outlaws of Thunder Junction Tokens",
            "Blue", "Creature", 2024, 0.24, 4, 50, true, "Bulk Box A (White/Blue)");
        Card bq = new Card("MTG-SLD-1169", "Temporal Manipulation", "Secret Lair Drop",
            "Blue", "Sorcery", 2022, 16.97, 9, 93, true, "Bulk Box A (White/Blue)");
        Card cc = new Card("MTG-CLB-276", "Gorion, Wise Mentor",
            "Commander Legends: Battle for Baldur's Gate",
            "Multi", "Creature", 2022, 0.19, 9, 57, true, "Bulk Box C (Green/Colorless)");
        
        b.stage(a); b.stage(bq); b.stage(cc);
        System.out.println("pending = " + b.pending());
        // queue order (FIFO): first staged, first handed out
        System.out.println("processNext -> " + b.processNext().getName()); // Bird
        // stack order (LIFO): the card just handed out is the one undo returns
        System.out.println("undo -> " + b.undo().getName());               // Bird
        System.out.println("processNext -> " + b.processNext().getName()); // Temporal Manipulation
        
        // drain the queue, then show the empty case
        System.out.println("processNext -> " + b.processNext().getName());  // Gorion, Wise Mentor
        Card none = b.processNext();
        System.out.println(none == null ? "buffer is empty, nothing to process"
                                        : "processNext -> " + none.getName());
        System.out.println("pending = " + b.pending());                        // 0

        // empty the undo stack, then show its empty case
        System.out.println("undo -> " + b.undo().getName());   // Gorion, Wise Mentor
        System.out.println("undo -> " + b.undo().getName());   // Temporal Manipulation
        Card noUndo = b.undo();
        System.out.println(noUndo == null
                ? "buffer is empty, nothing to undo" : "undo -> " + noUndo);

        System.out.println("IntakeBuffer. OWNER: De Jesus. pending=" + b.pending());
    }
}
