package tutorengine.model;

import java.util.LinkedList;
import java.util.List;

// OWNER: Mate B (History & Intake). No other member edits this file.
// TASK: sequential intake/change log via java.util.LinkedList.
// ACCEPT: log(), undoLast(), printAll() work; empty-log handled gracefully.
// DEFENSE: O(1) head/tail insert trace with real SKU data.
// SPEC: S7 Linked List, S11 list traversal, Template H list trace.
public class TransactionLog {
    private final LinkedList<String> entries = new LinkedList<>();

    public void log(String entry) { entries.addLast(entry); }
    public String undoLast() { return entries.isEmpty() ? null : entries.removeLast(); }
    public List<String> all() { return entries; }

    public static void main(String[] args) {
        TransactionLog t = new TransactionLog();
        t.log("INTAKE MTG-OTJ-055 x1");
        System.out.println(t.all());
        System.out.println("TransactionLog stub. OWNER: Mate B.");
    }
}
