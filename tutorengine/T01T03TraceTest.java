package tutorengine;

import tutorengine.model.TransactionLog;
import tutorengine.intake.IntakeBuffer;
import tutorengine.model.Card;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

// OWNER: De Jesus - History & Intake. Tests T01-T03 (TransactionLog) + queue/stack (IntakeBuffer).
// Run: mvn -q exec:java -Dexec.mainClass=tutorengine.T01T03TraceTest
// Prints one PASS/FAIL line per case and exits non-zero if any case fails.
//
// ========================= Template G - Test Cases =========================
// Test ID | Input / Condition         | Expected Result           | Actual             | Status
// -------+----------------------------+----------------------------+---------------------+------
// T101   | Empty structure:          | Returns null, no          | null / null        | PASS
//        | undoLast() on a new log;  | exception (main prints    | pending = 0        |
//        | processNext()/undo() on a | the nothing-to-undo /     |                    |
//        | new buffer                | nothing-to-process msg)   |                    |
// T102   | Single record: log(E1)    | One numbered line; the    | 1. INTAKE          | PASS
//        | then printAll();          | staged card is returned   | MTG-TSP-157        |
//        | stage(Bird) then          | first (FIFO), pending     | Bird / pending = 0 |
//        | processNext()             | -> 0                      |                    |
// T103   | Duplicate record: log(E1) | Both kept - 2 diary       | 2 lines / Bird,    | PASS
//        | twice; stage(Bird) twice  | lines and 2 pickups;      | Bird, 2 diary lines|
//        | then 2x processNext()     | no dedup, no error        |                    |
// ============================================================================
public class T01T03TraceTest {

    private static final String E1 = "INTAKE MTG-TSP-157";   // Fury Sliver, cards.csv line 3
    private static final String E2 = "DELETE MTG-UGIN-146";  // Wildcall, cards.csv line 12
    private static final String E3 = "INTAKE MTG-WOC-129";   // Paradise Druid, cards.csv line 35

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        t01_threeInsertionsPrintInInsertionOrder();
        t02_undoReturnsThirdEntryAndLeavesFirstTwo();
        t03_emptyUndoReturnsNullInsteadOfThrowing();
        intakeQueueHandsOutCardsInFifoOrder();
        intakeUndoReturnsMostRecentlyProcessedCard();
        intakeEmptyBufferIsSafe();
        intakeProcessNextOnEmptyQueueDoesNotThrow();
        intakeWritesOneDiaryLinePerProcessedCard();

        // Template G rows T101-T103 (table in the header comment).
        t101_emptyStructure();
        t102_singleRecord();
        t103_duplicateRecord();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---- tiny check helpers -------------------------------------------------

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }

    private static void checkEquals(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            System.out.println("     expected: " + expected);
            System.out.println("     actual:   " + actual);
        }
        check(name, ok);
    }

    private static String capture(Runnable r) {
        PrintStream orig = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf));
        try {
            r.run();
        } finally {
            System.setOut(orig);
        }
        return buf.toString().replace("\r\n", "\n");
    }

    private static TransactionLog threeEntries() {
        TransactionLog log = new TransactionLog();
        log.log(E1);
        log.log(E2);
        log.log(E3);
        return log;
    }

    // ---- T01-T03 : TransactionLog (LinkedList) ------------------------------

    // T01: 3 insertions keep insertion order and printAll numbers them 1..3.
    private static void t01_threeInsertionsPrintInInsertionOrder() {
        TransactionLog log = threeEntries();
        checkEquals("T01 list order after 3 log()",
                "[" + E1 + ", " + E2 + ", " + E3 + "]", log.all().toString());
        checkEquals("T01 printAll numbering",
                "1. " + E1 + "\n2. " + E2 + "\n3. " + E3 + "\n", capture(log::printAll));
    }

    // T02: undoLast removes the newest entry; the two older ones stay in order.
    private static void t02_undoReturnsThirdEntryAndLeavesFirstTwo() {
        TransactionLog log = threeEntries();
        checkEquals("T02 undoLast returns newest", E3, log.undoLast());
        checkEquals("T02 older entries untouched",
                "[" + E1 + ", " + E2 + "]", log.all().toString());
    }

    // T03: undo on an emptied log is safe - returns null, never throws.
    private static void t03_emptyUndoReturnsNullInsteadOfThrowing() {
        TransactionLog log = threeEntries();
        log.all().clear();
        String empty = log.undoLast();
        check("T03 empty undo returns null", empty == null);
        checkEquals("T03 friendly message",
                "log is empty, nothing to undo",
                empty == null ? "log is empty, nothing to undo" : "Undo: " + empty);
    }

    // ---- queue / stack : IntakeBuffer (ArrayDeque) --------------------------

    // queue: FIFO - staged A, B, C are handed out in the same order.
    private static void intakeQueueHandsOutCardsInFifoOrder() {
        IntakeBuffer b = new IntakeBuffer();
        Card a = card("MTG-TOTJ-7", "Bird");
        Card c = card("MTG-SLD-1169", "Temporal Manipulation");
        Card d = card("MTG-CLB-276", "Gorion, Wise Mentor");

        b.stage(a); b.stage(c); b.stage(d);
        checkEquals("queue pending after 3 stages", 3, b.pending());
        checkEquals("FIFO first out", "Bird", b.processNext().getName());
        checkEquals("FIFO second out", "Temporal Manipulation", b.processNext().getName());
        checkEquals("FIFO third out", "Gorion, Wise Mentor", b.processNext().getName());
        checkEquals("queue empty at end", 0, b.pending());
    }

    // stack: LIFO - the card that just came out is the one undo gives back.
    private static void intakeUndoReturnsMostRecentlyProcessedCard() {
        IntakeBuffer b = new IntakeBuffer();
        Card a = card("MTG-TOTJ-7", "Bird");
        Card c = card("MTG-SLD-1169", "Temporal Manipulation");

        b.stage(a); b.stage(c);
        checkEquals("process next -> Bird", "Bird", b.processNext().getName());
        checkEquals("process next -> Temporal", "Temporal Manipulation", b.processNext().getName());
        checkEquals("undo pops newest first", "Temporal Manipulation", b.undo().getName());
        checkEquals("undo pops older next", "Bird", b.undo().getName());
    }

    // empty-safe: queue and undo stack both return null instead of throwing.
    private static void intakeEmptyBufferIsSafe() {
        IntakeBuffer b = new IntakeBuffer();
        check("empty processNext returns null", b.processNext() == null);
        check("empty undo returns null", b.undo() == null);
    }

    // regression: polling an empty queue must not NPE inside processNext().
    private static void intakeProcessNextOnEmptyQueueDoesNotThrow() {
        IntakeBuffer b = new IntakeBuffer();
        boolean noThrow;
        Card returned = null;
        try {
            returned = b.processNext();
            noThrow = true;
        } catch (Exception e) {
            noThrow = false;
        }
        check("T-NPE empty processNext does not throw", noThrow);
        check("T-NPE empty processNext returns null", returned == null);
        checkEquals("T-NPE no diary line on failed pickup", "[]", b.getLog().all().toString());
    }

    // cross-class link: each pickup writes one line to the TransactionLog diary.
    private static void intakeWritesOneDiaryLinePerProcessedCard() {
        IntakeBuffer b = new IntakeBuffer();
        b.stage(card("MTG-TOTJ-7", "Bird"));
        b.stage(card("MTG-SLD-1169", "Temporal Manipulation"));
        b.processNext();
        b.processNext();
        checkEquals("diary records both intakes",
                "[INTAKE MTG-TOTJ-7, INTAKE MTG-SLD-1169]", b.getLog().all().toString());
    }

    // ---- Template G : T101-T103 (rows in the header comment) ----------------

    // T101 - empty structure case.
    private static void t101_emptyStructure() {
        TransactionLog log = new TransactionLog();
        IntakeBuffer b = new IntakeBuffer();
        check("T101 empty undoLast returns null", log.undoLast() == null);
        check("T101 empty processNext returns null", b.processNext() == null);
        check("T101 empty undo returns null", b.undo() == null);
        checkEquals("T101 empty buffer pending", 0, b.pending());
    }

    // T102 - single record case.
    private static void t102_singleRecord() {
        TransactionLog log = new TransactionLog();
        log.log(E1);
        checkEquals("T102 single diary line", "1. " + E1 + "\n", capture(log::printAll));

        IntakeBuffer b = new IntakeBuffer();
        b.stage(card("MTG-TOTJ-7", "Bird"));
        checkEquals("T102 single pickup is FIFO", "Bird", b.processNext().getName());
        checkEquals("T102 pending after pickup", 0, b.pending());
    }

    // T103 - duplicate record case.
    private static void t103_duplicateRecord() {
        TransactionLog log = new TransactionLog();
        log.log(E1);
        log.log(E1);
        checkEquals("T103 diary keeps both duplicates",
                "[" + E1 + ", " + E1 + "]", log.all().toString());

        IntakeBuffer b = new IntakeBuffer();
        b.stage(card("MTG-TOTJ-7", "Bird"));
        b.stage(card("MTG-TOTJ-7", "Bird"));
        checkEquals("T103 duplicate pickup 1", "Bird", b.processNext().getName());
        checkEquals("T103 duplicate pickup 2", "Bird", b.processNext().getName());
        checkEquals("T103 diary logs both intakes",
                "[INTAKE MTG-TOTJ-7, INTAKE MTG-TOTJ-7]", b.getLog().all().toString());
    }

    private static Card card(String sku, String name) {
        return new Card(sku, name, "Dataset/cards.csv", "Colorless", "Creature",
                2024, 1.00, 1, 50, false, "Bulk Box C");
    }
}
