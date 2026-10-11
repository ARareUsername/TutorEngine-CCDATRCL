package tutorengine.ui;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tutorengine.model.Card;

// Demos view: one button per §11 structure demo, rendered as text.
// All logic lives in DemoTexts (toolkit-free); this file is layout only.
public final class DemoView {
    private DemoView() {}

    public static Node build(List<Card> cards) {
        Label out = new Label("Pick a demo (fixed 5-card scripts).");
        out.setWrapText(true);
        HBox row = new HBox(8);
        demoBtn(row, out, "List", () -> DemoTexts.listDemo(cards));
        demoBtn(row, out, "Stack/Q", () -> DemoTexts.stackQueueDemo(cards));
        demoBtn(row, out, "BST", () -> DemoTexts.bstDemo(cards));
        demoBtn(row, out, "Heap", () -> DemoTexts.heapDemo(cards));
        demoBtn(row, out, "Sorts", () -> DemoTexts.sortsDemo(cards));
        demoBtn(row, out, "Collision", DemoTexts::collisionDemo);
        return new VBox(8, row, out);
    }

    private static void demoBtn(HBox row, Label out, String title, java.util.function.Supplier<String> demo) {
        Button b = new Button(title);
        b.setOnAction(e -> out.setText("--- " + title + " ---\n" + demo.get()));
        row.getChildren().add(b);
    }

    public static void main(String[] args) {
        List<Card> cards = CatalogView.loadCards();
        boolean ok = !DemoTexts.listDemo(cards).isBlank()
                && !DemoTexts.stackQueueDemo(cards).isBlank()
                && !DemoTexts.bstDemo(cards).isBlank()
                && !DemoTexts.heapDemo(cards).isBlank()
                && !DemoTexts.sortsDemo(cards).isBlank()
                && !DemoTexts.collisionDemo().isBlank();
        System.out.println("DemoView builders " + (ok ? "non-blank. DemoView data OK." : "BLANK (bug)!"));
    }
}
