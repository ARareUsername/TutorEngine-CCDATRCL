package tutorengine.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import tutorengine.model.Card;
import tutorengine.priority.PriorityDesk;

// Priority view: top card + poll-and-next flow. topN() is toolkit-free.
public final class PriorityView {
    private PriorityView() {}

    public static List<Card> topN(List<Card> cards, int n) {
        PriorityDesk desk = new PriorityDesk();
        for (Card c : cards) desk.offer(c);
        List<Card> out = new ArrayList<>(Math.min(n, desk.size()));
        for (int i = 0; i < n; i++) {
            Card c = desk.poll();
            if (c == null) break;
            out.add(c);
        }
        return out;
    }

    public static Node build(List<Card> cards) {
        PriorityDesk desk = new PriorityDesk();
        for (Card c : cards) desk.offer(c);
        Label top = new Label();
        Label log = new Label("Listed: (none yet)");
        StringBuilder listed = new StringBuilder();
        Runnable refresh = () -> {
            Card c = desk.peek();
            top.setText(c == null ? "Queue empty." : String.format("Next: %s [%.2f]", c, c.calculatePriority()));
        };
        Button next = new Button("List next");
        next.setOnAction(e -> {
            Card c = desk.poll();
            if (c == null) { top.setText("Queue empty."); return; }
            if (listed.length() > 0) listed.append("; ");
            listed.append(c.getName());
            log.setText("Listed: " + listed);
            refresh.run();
        });
        refresh.run();
        return new VBox(8, top, next, log);
    }

    public static void main(String[] args) {
        List<Card> cards = CatalogView.loadCards();
        List<Card> top3 = topN(cards, 3);
        System.out.println("top3 of " + cards.size() + ":");
        for (Card c : top3) System.out.println("  " + c.getName() + " " + c.calculatePriority());
        System.out.println("PriorityView data OK.");
    }
}
