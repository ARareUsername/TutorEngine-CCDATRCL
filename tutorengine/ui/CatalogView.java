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
        Map<String, List<String>> skuGraph = tutorengine.algorithms.SkuCorrector.build(cards);
        TextField skuField = new TextField();
        skuField.setPromptText("SKU e.g. MTG-OTJ-055");
        Label result = new Label("Type an SKU and press Search.");
        Button searchBtn = new Button("Search SKU");
        searchBtn.setDefaultButton(true);
        searchBtn.setOnAction(e -> {
            Card c = lookup(catalog, skuField.getText());
            result.getStyleClass().removeAll(Theme.BADGE_OK, Theme.BADGE_LOW_STOCK, Theme.BADGE_FOIL);
            if (c == null) {
                String q = skuField.getText() == null ? "" : skuField.getText().trim();
                List<Card> near = tutorengine.algorithms.SkuCorrector.multiSearch(catalog, skuGraph, q);
                if (near.isEmpty()) {
                    result.setText("Not found: " + q);
                } else {
                    StringBuilder sb = new StringBuilder("No exact match — closest:");
                    for (int i = 0; i < Math.min(3, near.size()); i++) {
                        Card n = near.get(i);
                        sb.append(" | ").append(n.getName()).append(" [")
                          .append(tutorengine.algorithms.SkuCorrector.matchedField(catalog, skuGraph, q, n)).append("]");
                    }
                    result.setText(sb.toString());
                }
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
            numCol("Price", c -> String.format("%.2f", c.getPrice())),
            numCol("Qty", c -> String.valueOf(c.getQuantity())),
            numCol("Demand", c -> String.valueOf(c.getDemandScore())),
            col("Flag", c -> c.isFoil() ? "FOIL" : (c.getQuantity() <= 3 ? "LOW" : "OK")),
            col("Location", c -> c.getBoxLocationId()),
            numCol("Priority", c -> String.format("%.2f", c.calculatePriority())));
        table.getItems().addAll(cards);
        // Session catalog editing (in-memory only, never written back to CSV).
        TextField nameField = new TextField();
        nameField.setPromptText("Name (for Add)");
        nameField.setPrefWidth(140);
        TextField priceField = new TextField();
        priceField.setPromptText("Price");
        priceField.setPrefWidth(70);
        Button addBtn = new Button("Add");
        addBtn.setOnAction(e -> {
            String sku = skuField.getText() == null ? "" : skuField.getText().trim();
            if (sku.isEmpty() || !sku.matches("[A-Za-z0-9-]+")) {
                result.setText("Invalid SKU: letters, digits, hyphens only.");
                return;
            }
            if (catalog.containsKey(sku)) {
                result.setText("Already in catalog: " + sku);
                return;
            }
            double price;
            try {
                price = Double.parseDouble(priceField.getText().trim());
            } catch (Exception ex) {
                result.setText("Invalid price: use a number like 12.50.");
                return;
            }
            String name = nameField.getText() == null || nameField.getText().trim().isEmpty()
                    ? sku : nameField.getText().trim();
            Card c = new Card(sku, name, "Session Add", "Colorless", "Creature",
                    2026, price, 1, 50, false, "Intake Sorting Desk");
            catalog.put(sku, c);
            table.getItems().add(c);
            result.setText("Added (session-only): " + c);
        });
        Button delBtn = new Button("Delete");
        delBtn.setOnAction(e -> {
            String sku = skuField.getText() == null ? "" : skuField.getText().trim();
            Card removed = catalog.remove(sku);
            if (removed == null) {
                result.setText("Not in catalog: " + sku);
            } else {
                table.getItems().removeIf(k -> k.getSku().equals(sku));
                result.setText("Deleted: " + removed.getName() + " [" + sku + "]");
            }
        });

        return new VBox(8, new HBox(8, skuField, searchBtn),
                new HBox(8, nameField, priceField, addBtn, delBtn), result, table);
    }

    private static TableColumn<Card, String> col(String title, java.util.function.Function<Card, String> f) {
        TableColumn<Card, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(f.apply(d.getValue())));
        return c;
    }

    // Right-aligned numeric column (tabular figures don't jitter on scroll).
    private static TableColumn<Card, String> numCol(String title, java.util.function.Function<Card, String> f) {
        TableColumn<Card, String> c = col(title, f);
        c.getStyleClass().add("col-num");
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
