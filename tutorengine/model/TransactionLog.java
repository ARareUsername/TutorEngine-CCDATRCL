package tutorengine.model;

import java.util.LinkedList;
import java.util.List;

// OWNER: De Jesus — History & Intake. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: Append-only diary of everything the app does (scans, additions,
// deletions), newest entry at the end. Answers "what happened to that card?".
// Backed by a LinkedList: appending or removing at either end costs the same no
// matter how long the diary grows, because no elements are ever shifted.
//
// HOW TO IMPLEMENT:
//   1. Keep the LinkedList<String> field plus log() (addLast) and undoLast()
//     (removeLast) as implemented. One entry per event, referencing a real SKU:
//       "INTAKE MTG-OTJ-055 x1", "DELETE MTG-FDN-101", "UNDO INTAKE MTG-OTJ-055 x1"
//   2. undoLast() on an empty log returns null. Add the user-facing message in
//      the demo ("log is empty, nothing to undo"), not inside undoLast(), so the
//      method stays usable by other classes without printing.
//   3. Add printAll(): iterate entries front to back with an index counter and
//      print "1. ...", "2. ...". An enhanced for-loop over the LinkedList is the
//      traversal this spec item requires — do not copy into an array first.
//   4. Extend main(): log 3 real SKUs, call printAll(), call undoLast() and print
//      the returned entry, call printAll() again (entry 3 must be gone), then call
//      undoLast() on the emptied log to show the safe empty case.
//   5. Trace packet (1 page): 3 insertions showing head/tail after each, then 1
//      deletion. Use real SKUs.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.model.TransactionLog
//   Expected: entries 1, 2, 3 in insertion order; undo returns entry 3 and the
//   second printout shows only 1 and 2; empty undo prints the friendly message
//   and returns null instead of throwing.
//
// DEFENSE: why LinkedList outperforms ArrayList here (no element shifting on
// either-end insert/remove).
// SPEC: S7 Linked List, S11 list traversal, Template H trace, tests T01-T03.
public class TransactionLog {
    private final LinkedList<String> entries = new LinkedList<>();

    public void log(String entry) { entries.addLast(entry); }
    public String undoLast() { return entries.isEmpty() ? null : entries.removeLast(); }
    public List<String> all() { return entries; }

    // TODO De Jesus: implement printAll() here (numbered traversal).
    public void printAll(){
        int n = 1;
        for(String e:entries){
            System.out.println(n++ +". "+e);
        }
    }

    public static void main(String[] args) {
        // TODO De Jesus: extend this demo to 3 entries + undo + empty-case.
        TransactionLog t = new TransactionLog();
        t.log("INTAKE MTG-TSP-157"); //Card.csv (line 3)
        t.log("DELETE MTG-UGIN-146");//Card.csv (line 12)
        t.log("INTAKE MTG-WOC-129");//Card.csv (line 35)

        System.out.println("All entries:"); t.printAll();
        System.out.println();
        
        //undo demo
        String undo = t.undoLast();
        System.out.println("Undo: "+undo);
        System.out.println("After undone: "); t.printAll();
        
        System.out.println();
        //safe empty case demo
        t.all().clear();
        String empty = t.undoLast();
        System.out.println(empty == null ? "log is empty, nothing to undo" : "Undo: " + empty);
        System.out.println();
        
        System.out.println("TransactionLog. OWNER: De Jesus.");
        
    }
}

// TRACE (1 page) - LinkedList diary, 3 insertions -> traversal -> deletions.
    // E1 = "INTAKE MTG-TSP-157"   E2 = "DELETE MTG-UGIN-146"   E3 = "INTAKE MTG-WOC-129"
    //
    //   step   operation     entries (head -> tail)        returns / output
    //     0    start         []                            -
    //     1    log(E1)       [E1]                          addLast: tail = 1
    //     2    log(E2)       [E1, E2]                      addLast: tail = 2
    //     3    log(E3)       [E1, E2, E3]                  addLast: tail = 3
    //     4    printAll()    [E1, E2, E3]                  prints "1. E1 / 2. E2 / 3. E3"
    //     5    undoLast()    [E1, E2]                      E3 (removeLast: tail = 2)
    //     6    undoLast()    []                            null - safe, never throws
    //
    // Only head/tail are ever relinked, so addLast/removeLast are O(1) at any size:
    // an ArrayList would shift elements for end removals on a shrinking array.
    // Undo deletes the record only - it does not reverse what the entry described.
