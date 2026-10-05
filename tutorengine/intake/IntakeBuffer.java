package tutorengine.intake;

import java.util.ArrayDeque;
import java.util.Deque;
import tutorengine.model.Card;

// OWNER: Mate B (History & Intake). No other member edits this file.
// TASK: scanner intake FIFO buffer + misread LIFO undo via ArrayDeque.
// ACCEPT: stage(), processNext() FIFO order; pushUndo()/undo() LIFO; empty-safe.
// DEFENSE: Queue vs Stack trace with real cards; why ArrayDeque O(1) both ends.
// SPEC: S7 Stack/Queue, S11 stack-or-queue op, Template H stack/queue trace.
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

    public static void main(String[] args) {
        System.out.println("IntakeBuffer stub. OWNER: Mate B. Run: javac tutorengine/model/Card.java tutorengine/intake/IntakeBuffer.java && java tutorengine.intake.IntakeBuffer");
    }
}
