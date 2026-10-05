package tutorengine.intake;

import java.util.ArrayDeque;
import java.util.Deque;
import tutorengine.model.Card;

// Hi De Jesus! This file is yours too. Same deal: nobody else edits it, and
// you don't edit theirs — comments on pull requests for anything cross-file.
//
// What this does: picture the scanning desk. Cards arrive in a pile and must
// be processed first-in-first-out (a queue), but when the scanner misreads a
// card, the last processed card must pop right back for correction
// (last-in-first-out, a stack). One ArrayDeque plays both roles beautifully,
// and every operation on either end is instant.
//
// Your steps:
//  1. stage(), processNext(), and undo() already work — keep all three.
//  2. Make the demo (not the methods) print a message when the buffer is empty.
//     The methods themselves just return null; the talking happens in main().
//  3. Extend main() with 3 real cards (there's a ready-made example below):
//     stage A, B, C — then processNext() must hand you A first (queue order),
//     undo() must hand you A back (stack order), then processNext() gives B.
//     Print every step so the whole dance is visible on screen.
//  4. Each processNext() should also write a line to your TransactionLog, so
//     the diary and the desk always agree.
//  5. For the trace packet, draw one page showing the queue's contents after
//     each stage, process, and undo.
//
// How to check your work:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.intake.IntakeBuffer
//   When you're done: first pickup is A, undo returns A, next pickup is B, no
//   card is ever skipped or duplicated, and empty operations print a message
//   instead of crashing.
//
// For the defense, be ready to demo queue vs stack behavior in one run and to
// explain why ArrayDeque is instant at both ends. (Spec: S7 Stack/Queue, S11
// stack-or-queue operation, Template H trace.)
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
