package tutorengine.intake;

import java.util.ArrayDeque;
import java.util.Deque;
import tutorengine.model.Card;

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
    private final Deque<Card> queue = new ArrayDeque<>();
    private final Deque<Card> undo = new ArrayDeque<>();

    public void stage(Card c) { queue.offer(c); }
    public Card processNext() {
        Card c = queue.poll();
        if (c != null) undo.push(c);
        return c;
    }
    public Card undo() { return undo.isEmpty() ? null : undo.pop(); }
    public int pending() { return queue.size(); }

    public static void main(String[] args) {
        // TODO De Jesus: build 3 real Cards here and run the FIFO+LIFO script above.
        // Example: new Card("MTG-OTJ-055","Mana Drain","Outlaws of Thunder Junction",
        //   "Blue","Instant",2024,2600.00,1,98,false,"Showcase Display")
        System.out.println("IntakeBuffer. OWNER: De Jesus. pending=0 (implement demo).");
    }
}
