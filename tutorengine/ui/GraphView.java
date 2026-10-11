package tutorengine.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tutorengine.algorithms.StoreNavigator;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;
import tutorengine.storage.BoxCapacity;
import tutorengine.storage.DividerGuide;

// Store-map view: BFS route + hop count, DFS audit order, per-box fill level,
// capacity state badges, and Locate divider insertion guidance (§2 specs/divider-navigation/spec.md,
// §1 specs/box-capacity-guard/spec.md). All data functions below are toolkit-free.
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
        if (cards != null) {
            for (Card c : cards) {
                String slot = StorageLocation.route(c);
                if (m.containsKey(slot)) m.put(slot, m.get(slot) + 1);
            }
        }
        return m;
    }

    // Slot id -> capacity state, pure and toolkit-free.
    public static Map<String, BoxCapacity.State> slotStates(List<Card> cards) {
        Map<String, BoxCapacity.State> m = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : fillCounts(cards).entrySet()) {
            int count = Math.max(e.getValue(), BoxCapacity.getBoxCount(e.getKey()));
            m.put(e.getKey(), BoxCapacity.state(count));
        }
        return m;
    }

    public static Node build() {
        return build(CatalogView.loadCards(), null, null);
    }

    public static Node build(List<Card> cards, String preselectSlot) {
        return build(cards, preselectSlot, null);
    }

    // preselectSlot preselects the destination (Locate flow); targetCard adds divider guidance.
    public static Node build(List<Card> cards, String preselectSlot, Card targetCard) {
        List<Card> loaded = cards == null ? CatalogView.loadCards() : cards;
        Map<String, List<String>> g = storeMap();

        ChoiceBox<String> dest = new ChoiceBox<>();
        dest.getItems().addAll(g.keySet());
        if (preselectSlot != null && g.containsKey(preselectSlot)) {
            dest.getSelectionModel().select(preselectSlot);
        } else {
            dest.getSelectionModel().select("CAB-T-TOP");
        }

        Label route = new Label();
        route.setStyle("-fx-font-weight: bold;");

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

        // Locate flow divider guidance banner (Task 2.2)
        VBox guidanceBox = new VBox(4);
        if (targetCard != null) {
            DividerGuide.Guidance guidance = DividerGuide.calculateForCatalog(targetCard, loaded);
            Label guidanceTitle = new Label("📍 In-Box Divider Guidance for: " + targetCard.getName() + " [" + targetCard.getSku() + "]");
            guidanceTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #065f46;");

            Label guidanceDesc = new Label(guidance.guidanceText());
            guidanceDesc.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #047857;");

            guidanceBox.getChildren().addAll(guidanceTitle, guidanceDesc);
            guidanceBox.setPadding(new Insets(8, 12, 8, 12));
            guidanceBox.setStyle("-fx-background-color: #ecfdf5; -fx-border-color: #10b981; -fx-border-width: 1px; -fx-border-radius: 6px;");
        }

        // Box fill + Capacity State Badges (Task 1.2)
        VBox fill = new VBox(6, new Label("Box Capacity & Fill Status (1,000 Card Capacity Ceiling):"));
        Label provisionNotice = new Label();
        provisionNotice.setStyle("-fx-font-weight: bold;");

        Button provisionBtn = new Button("Provision New Volume...");
        provisionBtn.setOnAction(e -> {
            String selectedSlot = dest.getValue();
            BoxCapacity.ProvisioningSuggestion sug = BoxCapacity.suggestProvisioning(selectedSlot, selectedSlot);

            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle("Provision New Box Container");
            dlg.setHeaderText("Provision new physical volume per mtg_storage_organization_guide.md §2");

            GridPane grid = new GridPane();
            grid.setHgap(8);
            grid.setVgap(8);
            grid.setPadding(new Insets(10));

            TextField labelField = new TextField(sug.suggestedBoxLabel());
            TextField idField = new TextField(sug.suggestedBoxId());
            TextField unitField = new TextField(sug.targetShelfUnit());
            TextField tierField = new TextField(sug.targetShelfTier());

            grid.add(new Label("Display Label:"), 0, 0);
            grid.add(labelField, 1, 0);
            grid.add(new Label("Box ID:"), 0, 1);
            grid.add(idField, 1, 1);
            grid.add(new Label("Shelf Unit:"), 0, 2);
            grid.add(unitField, 1, 2);
            grid.add(new Label("Shelf Tier:"), 0, 3);
            grid.add(tierField, 1, 3);

            dlg.getDialogPane().setContent(grid);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dlg.setResultConverter(btn -> btn);

            DialogHelper.showAndWait(dlg).ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    boolean ok = BoxCapacity.recordProvisioning(idField.getText(), labelField.getText(), unitField.getText(), tierField.getText());
                    if (ok) {
                        provisionNotice.setText("✓ Provisioned box volume: " + labelField.getText() + " (" + idField.getText() + ")");
                        provisionNotice.setStyle("-fx-text-fill: #059669;");
                    } else {
                        provisionNotice.setText("⚠️ Failed to record provisioning.");
                        provisionNotice.setStyle("-fx-text-fill: #dc2626;");
                    }
                }
            });
        });

        for (Map.Entry<String, Integer> e : fillCounts(loaded).entrySet()) {
            if (e.getValue() == 0) continue;
            int count = Math.max(e.getValue(), BoxCapacity.getBoxCount(e.getKey()));
            double frac = count / (double) StorageLocation.BOX_CAPACITY;
            ProgressBar bar = new ProgressBar(Math.min(1.0, frac));
            bar.setPrefWidth(160);

            BoxCapacity.State st = BoxCapacity.state(count);
            Label badge = new Label(st.getLabel());
            badge.setStyle("-fx-background-color: " + st.getColorHex()
                    + "; -fx-text-fill: white; -fx-padding: 2px 6px; -fx-background-radius: 3px; -fx-font-weight: bold; -fx-font-size: 10px;");

            HBox row = new HBox(8,
                    new Label(e.getKey() + ": " + count + "/1000 (" + String.format("%.1f", frac * 100) + "%)"),
                    bar, badge);
            row.setAlignment(Pos.CENTER_LEFT);
            fill.getChildren().add(row);
        }

        HBox topBar = new HBox(8, dest, go, provisionBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox root;
        if (targetCard != null) {
            root = new VBox(10, guidanceBox, topBar, route, audit, fill, provisionNotice);
        } else {
            root = new VBox(10, topBar, route, audit, fill, provisionNotice);
        }
        root.getStyleClass().add("view-container");
        return root;
    }

    public static void main(String[] args) {
        Map<String, List<String>> g = storeMap();
        System.out.println("nodes=" + StoreNavigator.nodeCount(g) + " edges=" + StoreNavigator.edgeCount(g));
        System.out.println("route: " + String.join(" -> ", pathTo("CAB-T-TOP")));
        System.out.println("audit (" + auditOrder().size() + " nodes)");
        System.out.println("fill: " + fillCounts(CatalogView.loadCards()));
        System.out.println("slot states: " + slotStates(CatalogView.loadCards()));
        System.out.println("missing: " + pathTo("Narnia") + " (empty-safe)");
        System.out.println("GraphView data OK.");
    }
}
