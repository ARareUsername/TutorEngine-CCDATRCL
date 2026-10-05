package tutorengine.model;

import java.util.LinkedList;
import java.util.List;

// Hi De Jesus! This file is yours. Please don't let anyone else edit it, and
// please don't edit anyone else's — if you need a change somewhere else,
// leave a comment on that person's pull request instead.
//
// What this does: it's the store's diary. Every scan, addition, and deletion
// appends one line here, newest at the end. If anyone asks "what happened to
// that card?", this log is the answer. Under the hood it's a LinkedList, which
// is perfect for a diary: adding to the end (or front) is instant, no matter
// how long the diary gets.
//
// Your steps:
//  1. The log itself (a LinkedList of text lines) plus log() and undoLast()
//     already work — keep them. Write entries like "INTAKE MTG-OTJ-055 x1"
//     or "DELETE MTG-FDN-101" so every line names a real card.
//  2. Add a printAll() method that walks the log front to back and numbers
//     each line ("1. ...", "2. ...").
//  3. Extend the main() demo: log 3 real SKUs, print everything, undo the last
//     one, print again. Also show the empty case — calling undo on an empty
//     log should print a friendly message, never crash.
//  4. For the trace packet, draw one page: 3 inserts step by step (show where
//     the head and tail point), then one delete. Use real SKUs.
//
// How to check your work:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.model.TransactionLog
//   When you're done you should see 3 numbered entries in the order you added
//   them, then after the undo only entries 1 and 2, then a polite message for
//   the empty-log undo.
//
// For the defense, be ready to explain why a LinkedList beats an ArrayList
// here: appending is always instant, while an array has to shift things around.
// (Spec: S7 Linked List, S11 list traversal, Template H trace, tests T01-T03.)
public class TransactionLog {
    private final LinkedList<String> entries = new LinkedList<>();

    public void log(String entry) { entries.addLast(entry); }
    public String undoLast() { return entries.isEmpty() ? null : entries.removeLast(); }
    public List<String> all() { return entries; }

    // TODO De Jesus: implement printAll() here (numbered traversal).

    public static void main(String[] args) {
        // TODO De Jesus: extend this demo to 3 entries + undo + empty-case.
        TransactionLog t = new TransactionLog();
        t.log("INTAKE MTG-OTJ-055 x1");
        System.out.println(t.all());
        System.out.println("TransactionLog. OWNER: De Jesus.");
    }
}
