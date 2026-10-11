package tutorengine.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tutorengine.Main;
import tutorengine.algorithms.CardSorter;
import tutorengine.catalog.CatalogIndex;
import tutorengine.intake.IntakeBuffer;
import tutorengine.model.Card;
import tutorengine.model.TransactionLog;
import tutorengine.priority.PriorityDesk;

// OWNER: Bondoc, Karl B. — shared demo text for both UIs. Member modules are
// only *called* here (public APIs), never modified.
//
// PURPOSE: One-click §11 demos as plain strings, so the Swing log and JavaFX
// labels render the same output. Fixed 5-card scripts (never the full 60),
// mirroring each owner's main() on the same data.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.ui.DemoTexts
//   Expected: six sections print; sorts agree with List.sort; collision shows
//   the shared bucket; empty input prints the empty-safe lines, never throws.
public final class DemoTexts {
    private DemoTexts() {}

    static List<Card> five(List<Card> cards) {
        return new ArrayList<>(cards.subList(0, Math.min(5, cards.size())));
    }

    public static String listDemo(List<Card> cards) {
        List<Card> c = five(cards);
        if (c.isEmpty()) return "log is empty, nothing to undo";
        TransactionLog t = new TransactionLog();
        for (Card k : c) t.log("INTAKE " + k.getSku());
        StringBuilder sb = new StringBuilder();
        int n = 1;
        for (String e : t.all()) sb.append(n++).append(". ").append(e).append("\n");
        sb.append("undo -> ").append(t.undoLast());
        return sb.toString().trim();
    }

    public static String stackQueueDemo(List<Card> cards) {
        List<Card> c = five(cards);
        if (c.size() < 2) return "buffer is empty, nothing to process";
        IntakeBuffer b = new IntakeBuffer();
        for (Card k : c) b.stage(k);
        return "processNext -> " + b.processNext().getName()
                + "\nundo -> " + b.undo().getName()
                + "\npending = " + b.pending();
    }

    public static String bstDemo(List<Card> cards) {
        List<Card> c = five(cards);
        if (c.isEmpty()) return "catalog is empty";
        CatalogIndex idx = new CatalogIndex();
        for (Card k : c) idx.put(k);
        StringBuilder sb = new StringBuilder();
        sb.append("size=").append(idx.size());
        sb.append("\nhit=").append(idx.get(c.get(0).getName()) != null);
        sb.append("\nmiss=").append(idx.get("Black Lotus") == null ? "graceful" : "BUG");
        List<String> order = new ArrayList<>();
        for (Card k : idx.inorder()) order.add(k.getName());
        sb.append("\ninorder=").append(String.join(" | ", order));
        idx.remove(c.get(0).getName());
        idx.remove(idx.firstKey());
        sb.append("\nafter 2 deletes size=").append(idx.size());
        return sb.toString();
    }

    public static String heapDemo(List<Card> cards) {
        List<Card> c = five(cards);
        if (c.isEmpty()) return "no cards to prioritize";
        PriorityDesk desk = new PriorityDesk();
        for (Card k : c) desk.offer(k);
        Card top = desk.peek();
        Card poll = desk.poll();
        Card runner = desk.peek();
        return "peek=" + top.getName() + " (" + String.format("%.2f", top.calculatePriority()) + ")"
                + "\npoll=" + poll.getName()
                + "\nrunner-up=" + (runner == null ? "none" : runner.getName());
    }

    public static String sortsDemo(List<Card> cards) {
        List<Card> c = five(cards);
        if (c.isEmpty()) return "nothing to sort";
        List<Card> a = new ArrayList<>(c);
        List<Card> b = new ArrayList<>(c);
        CardSorter.insertionSort(a);
        long insCmp = CardSorter.comparisons, insMov = CardSorter.movements;
        CardSorter.selectionSort(b);
        List<Card> expected = new ArrayList<>(c);
        expected.sort(null);
        boolean agree = a.equals(expected) && b.equals(expected);
        List<String> names = new ArrayList<>();
        for (Card k : a) names.add(k.getName());
        return "order=" + String.join(" | ", names)
                + "\ninsertion cmp=" + insCmp + " mov=" + insMov
                + "\nselection cmp=" + CardSorter.comparisons + " mov=" + CardSorter.movements
                + "\nmatch List.sort=" + agree;
    }

    public static String collisionDemo() {
        Map<String, Card> tiny = new HashMap<>(7);
        Card a = new Card("MTG-OTJ-001", "Collision Demo A", "Demo Set", "Colorless", "Creature",
                2024, 1.0, 1, 50, false, "Bulk Box A (White/Blue)");
        Card b = new Card("MTG-OTJ-009", "Collision Demo B", "Demo Set", "Colorless", "Creature",
                2024, 1.0, 1, 50, false, "Bulk Box A (White/Blue)");
        tiny.put(a.getSku(), a);
        tiny.put(b.getSku(), b);
        List<String> chain = new ArrayList<>();
        for (Map.Entry<String, Card> e : tiny.entrySet()) chain.add("[" + e.getKey() + "]");
        return "bucket(" + a.getSku() + ")=" + Main.bucketOf(a.getSku())
                + " bucket(" + b.getSku() + ")=" + Main.bucketOf(b.getSku()) + " (shared chain)"
                + "\nchain=" + String.join(" ", chain)
                + "\nget=" + tiny.get(a.getSku()).getName() + "/" + tiny.get(b.getSku()).getName();
    }

    public static void main(String[] args) {
        List<Card> cards = CatalogView.loadCards();
        System.out.println("--- list ---\n" + listDemo(cards));
        System.out.println("--- stack/queue ---\n" + stackQueueDemo(cards));
        System.out.println("--- bst ---\n" + bstDemo(cards));
        System.out.println("--- heap ---\n" + heapDemo(cards));
        System.out.println("--- sorts ---\n" + sortsDemo(cards));
        System.out.println("--- collision ---\n" + collisionDemo());
        System.out.println("--- empty-safe ---");
        System.out.println(listDemo(List.of()) + " / " + stackQueueDemo(List.of()));
        System.out.println("DemoTexts OK.");
    }
}
