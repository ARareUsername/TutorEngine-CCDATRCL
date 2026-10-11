package tutorengine.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tutorengine.model.Card;
import tutorengine.util.CSVLoader;

// Catalog view: SKU search (hit/miss) + full sortable table.
// indexOf()/lookup()/loadCards() are toolkit-free (headless main() below covers them).
public final class CatalogView {
    private CatalogView() {}

    public static List<Card> loadCards() {
        List<Card> loaded = CSVLoader.load("Dataset/cards.csv");
        if (!loaded.isEmpty()) return loaded;
        return List.of(
            new Card("MTG-MH3-001", "Ajani, Nacatl Pariah", "Modern Horizons 3", "White", "Creature", 2024, 2150.00, 2, 92, true, "Showcase Display"),
            new Card("MTG-OTJ-055", "Mana Drain", "Outlaws of Thunder Junction", "Blue", "Instant", 2024, 2600.00, 1, 98, false, "Showcase Display"),
            new Card("MTG-FDN-101", "Llanowar Elves", "Foundations", "Green", "Creature", 2024, 25.00, 48, 45, false, "Bulk Box C"));
    }

    public static Map<String, Card> indexOf(List<Card> cards) {
        Map<String, Card> m = new HashMap<>(Math.max(101, cards.size() * 2));
        for (Card c : cards) m.put(c.getSku(), c);
        return m;
    }

    public static Card lookup(Map<String, Card> catalog, String sku) {
        if (sku == null || sku.isBlank()) return null;
        return catalog.get(sku.trim());
    }

    public static Node build(List<Card> cards) {
        Map<String, Card> catalog = indexOf(cards);
        TextField skuField = new TextField();
        skuField.setPromptText("SKU e.g. MTG-OTJ-055");
        Label result = new Label("Type an SKU and press Search.");
        Button searchBtn = new Button("Search SKU");
        searchBtn.setDefaultButton(true);
        searchBtn.setOnAction(e -> {
            Card c = lookup(catalog, skuField.getText());
            result.getStyleClass().removeAll(Theme.BADGE_OK, Theme.BADGE_LOW_STOCK, Theme.BADGE_FOIL);
            if (c == null) {
                result.setText("Not found: " + skuField.getText().trim());
            } else {
                result.setText("Found: " + c);
                result.getStyleClass().add(Theme.badgeFor(c.getQuantity(), c.isFoil()));
            }
        });

        TableView<Card> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(
            col("Name", c -> c.getName()),
            col("SKU", c -> c.getSku()),
            col("Price", c -> String.format("%.2f", c.getPrice())),
            col("Qty", c -> String.valueOf(c.getQuantity())),
            col("Demand", c -> String.valueOf(c.getDemandScore())),
            col("Flag", c -> c.isFoil() ? "FOIL" : (c.getQuantity() <= 3 ? "LOW" : "OK")),
            col("Location", c -> c.getBoxLocationId()),
            col("Priority", c -> String.format("%.2f", c.calculatePriority())));
        table.getItems().addAll(cards);

        return new VBox(8, new HBox(8, skuField, searchBtn), result, table);
    }

    private static TableColumn<Card, String> col(String title, java.util.function.Function<Card, String> f) {
        TableColumn<Card, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(f.apply(d.getValue())));
        return c;
    }

    public static void main(String[] args) {
        List<Card> cards = loadCards();
        System.out.println("cards=" + cards.size() + " (need >= 50)");
        Map<String, Card> idx = indexOf(cards);
        System.out.println("hit: " + lookup(idx, cards.get(0).getSku()).getName());
        System.out.println("miss: " + lookup(idx, "NOPE-000") + " blank: " + lookup(idx, "  "));
        System.out.println("CatalogView data OK.");
    }
}
