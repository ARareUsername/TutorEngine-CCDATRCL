package tutorengine.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tutorengine.algorithms.StoreNavigator;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;

// Store-map view: BFS route + hop count, DFS audit order, per-box fill level.
// The map is StoreNavigator.sampleMap() (canonical cabinet map); the interim
// 6-node map is retired. All data functions below are toolkit-free.
public final class GraphView {
    private GraphView() {}

    public static Map<String, List<String>> storeMap() {
        return StoreNavigator.sampleMap();
    }

    public static List<String> pathTo(String goal) {
        return StoreNavigator.bfsPath(storeMap(), StoreNavigator.CHECKOUT, goal);
    }

    public static List<String> auditOrder() {
        return StoreNavigator.dfs(storeMap(), StoreNavigator.CHECKOUT);
    }

    // Slot id -> stored card count, from the loaded dataset via the alias table.
    public static Map<String, Integer> fillCounts(List<Card> cards) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (StorageLocation s : StorageLocation.slots()) m.put(s.getId(), 0);
        for (Card c : cards) {
            String slot = StorageLocation.resolve(c.getBoxLocationId());
            if (slot != null && m.containsKey(slot)) m.put(slot, m.get(slot) + 1);
        }
        return m;
    }

    public static Node build() {
        List<Card> cards = CatalogView.loadCards();
        Map<String, List<String>> g = storeMap();
        ChoiceBox<String> dest = new ChoiceBox<>();
        dest.getItems().addAll(g.keySet());
        dest.getSelectionModel().select("CAB-5-UPPER");
        Label route = new Label();
        Label audit = new Label("Audit order (" + auditOrder().size() + " nodes): "
                + String.join(" | ", auditOrder()));
        audit.setWrapText(true);
        Button go = new Button("Show route");
        go.setOnAction(e -> {
            List<String> p = pathTo(dest.getValue());
            route.setText(p.isEmpty() ? "No route (unknown or unreachable)."
                    : String.join(" -> ", p) + " (" + (p.size() - 1) + " hops)");
        });
        go.fire();

        VBox fill = new VBox(4, new Label("Box fill (sample of 1000 capacity):"));
        for (Map.Entry<String, Integer> e : fillCounts(cards).entrySet()) {
            if (e.getValue() == 0) continue;
            double frac = e.getValue() / (double) StorageLocation.BOX_CAPACITY;
            ProgressBar bar = new ProgressBar(frac);
            bar.setPrefWidth(160);
            fill.getChildren().add(new HBox(8,
                    new Label(e.getKey() + ": " + e.getValue() + "/1000 (" + String.format("%.1f", frac * 100) + "%)"), bar));
        }
        return new VBox(8, new HBox(8, dest, go), route, audit, fill);
    }

    public static void main(String[] args) {
        Map<String, List<String>> g = storeMap();
        System.out.println("nodes=" + StoreNavigator.nodeCount(g) + " edges=" + StoreNavigator.edgeCount(g));
        System.out.println("route: " + String.join(" -> ", pathTo("CAB-5-UPPER")));
        System.out.println("audit (" + auditOrder().size() + " nodes)");
        System.out.println("fill: " + fillCounts(CatalogView.loadCards()));
        System.out.println("missing: " + pathTo("Narnia") + " (empty-safe)");
        System.out.println("GraphView data OK.");
    }
}
