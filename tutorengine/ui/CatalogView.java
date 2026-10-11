package tutorengine.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;
import tutorengine.util.CSVLoader;

// Catalog view: SKU search (hit/miss) + full sortable table.
// indexOf()/lookup()/loadCards() are toolkit-free (headless main() below covers them).
// locateHandler is set by FxApp: Locate buttons send a slot id to Store Map.
public final class CatalogView {
    private CatalogView() {}

    public static java.util.function.Consumer<String> locateHandler;

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
        result.setWrapText(true);
        javafx.scene.image.ImageView preview = new javafx.scene.image.ImageView();
        preview.setFitWidth(DisplaySettings.cards().preview);
        preview.setPreserveRatio(true);
        Label stats = new Label("Select a card to see its details.");
        stats.setWrapText(true);
        Button searchBtn = new Button("Search SKU");
        searchBtn.setDefaultButton(true);
        Button locateBtn = new Button("Locate on shelf");
        locateBtn.setVisible(false);
        VBox linksBox = new VBox(4);
        Card[] found = new Card[1];
        int[] searchGen = new int[1]; // latest search wins; stale workers stay silent
        locateBtn.setOnAction(e -> {
            if (locateHandler != null && found[0] != null)
                locateHandler.accept(StorageLocation.route(found[0]));
        });
        searchBtn.setOnAction(e -> {
            Card c = lookup(catalog, skuField.getText());
            result.getStyleClass().removeAll(Theme.BADGE_OK, Theme.BADGE_LOW_STOCK, Theme.BADGE_FOIL);
            linksBox.getChildren().clear();
            locateBtn.setVisible(false);
            found[0] = c;
            if (c == null) {
                String q = skuField.getText() == null ? "" : skuField.getText().trim();
                List<Card> near = tutorengine.algorithms.SkuCorrector.multiSearch(catalog, skuGraph, q);
                if (near.isEmpty()) {
                    result.setText("Not found: " + q);
                } else {
                    result.setText("No exact match — closest (click to open):");
                    for (int i = 0; i < Math.min(3, near.size()); i++) {
                        Card n = near.get(i);
                        String field = tutorengine.algorithms.SkuCorrector
                                .matchedField(catalog, skuGraph, q, n);
                        Hyperlink link = new Hyperlink(
                                n.getName() + " [" + n.getSku() + "] (" + field + ")");
                        link.setOnAction(ev -> {
                            skuField.setText(n.getSku());
                            searchBtn.fire();
                        });
                        linksBox.getChildren().add(link);
                    }
                }
            } else {
                result.setText("Found: " + c.getName() + " [" + c.getSku() + "]");
                result.getStyleClass().add(Theme.badgeFor(c.getQuantity(), c.isFoil()));
                stats.setText(c.getName() + " [" + c.getSku() + "]\n"
                        + c.getSetName() + " · " + c.getColor() + " " + c.getCardType() + " " + c.getReleaseYear() + "\n"
                        + String.format("PHP %.2f x%d | Demand %d%s\n", c.getPrice(), c.getQuantity(),
                                c.getDemandScore(), c.isFoil() ? " | FOIL" : "")
                        + "Shelf: " + StorageLocation.route(c) + " (box: " + c.getBoxLocationId() + ")\n"
                        + String.format("Priority %.2f", c.calculatePriority()));
                // Thumbnail off the UI thread; offline/unknown keeps the placeholder.
                // Source fits the tier: S renders small, M normal, L large.
                preview.setFitWidth(DisplaySettings.cards().preview);
                preview.setImage(null);
                final CardImageCache.Variant pv = switch (DisplaySettings.cards()) {
                    case L -> CardImageCache.Variant.LARGE;
                    case M -> CardImageCache.Variant.NORMAL;
                    default -> CardImageCache.Variant.SMALL;
                };
                final int w = DisplaySettings.cards().preview;
                final int gen = ++searchGen[0];
                javafx.concurrent.Task<javafx.scene.image.Image> load = new javafx.concurrent.Task<>() {
                    @Override protected javafx.scene.image.Image call() {
                        return CardImageCache.getFx(c.getSku(), c.getName(), w, pv, c.getSetCode());
                    }
                };
                load.setOnSucceeded(done -> {
                    if (gen == searchGen[0] && load.getValue() != null) preview.setImage(load.getValue());
                });
                new Thread(load, "card-image").start();
                locateBtn.setVisible(true);
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
        // Row click selects the card's SKU (reuses search: details + picture + Locate).
        table.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> {
            if (sel != null && !sel.getSku().equals(skuField.getText().trim())) {
                skuField.setText(sel.getSku());
                searchBtn.fire();
            }
        });
        // Hover a row 600ms -> card picture tooltip (worker thread, cached first).
        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Card> row = new javafx.scene.control.TableRow<>();
            javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip();
            tip.setShowDelay(javafx.util.Duration.millis(600));
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView();
            iv.setFitWidth(DisplaySettings.cards().tooltip);
            iv.setPreserveRatio(true);
            tip.setGraphic(iv);
            row.setTooltip(tip);
            row.hoverProperty().addListener((ho, was, is) -> {
                if (is && !row.isEmpty()) {
                    Card c = row.getItem();
                    final int tw = DisplaySettings.cards().tooltip;
                    // Tooltips never upscale a smaller source: S uses small, else normal.
                    final CardImageCache.Variant src = DisplaySettings.cards() == DisplaySettings.CardTier.S
                            ? CardImageCache.Variant.SMALL : CardImageCache.Variant.NORMAL;
                    javafx.concurrent.Task<javafx.scene.image.Image> load =
                            new javafx.concurrent.Task<>() {
                        @Override protected javafx.scene.image.Image call() {
                            return CardImageCache.getFx(c.getSku(), c.getName(), tw, src, c.getSetCode());
                        }
                    };
                    load.setOnSucceeded(done -> {
                        if (load.getValue() != null && row.isHover() && row.getItem() == c)
                            iv.setImage(load.getValue());
                    });
                    new Thread(load, "card-hover").start();
                }
            });
            return row;
        });
        // Session catalog editing (in-memory only, never written back to CSV).
        TextField nameField = new TextField();
        nameField.setPromptText("Name (for Add)");
        nameField.setPrefWidth(140);
        TextField priceField = new TextField();
        priceField.setPromptText("Price");
        priceField.setPrefWidth(70);
        TextField qtyField = new TextField();
        qtyField.setPromptText("Qty");
        qtyField.setPrefWidth(50);
        TextField slotField = new TextField();
        slotField.setPromptText("Slot (blank=auto)");
        slotField.setPrefWidth(130);
        Card[] lastRemoved = new Card[1];
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
            int qty;
            try {
                qty = Integer.parseInt(qtyField.getText() == null ? "" : qtyField.getText().trim());
                if (qty <= 0) throw new NumberFormatException();
            } catch (Exception ex) {
                result.setText("Invalid quantity: use a whole number of 1 or more.");
                return;
            }
            String slot = slotField.getText() == null ? "" : slotField.getText().trim();
            if (!slot.isEmpty() && StorageLocation.slots().stream().noneMatch(s -> s.getId().equalsIgnoreCase(slot))) {
                result.setText("Unknown slot: use a slot id like CAB-T-TOP (blank = auto).");
                return;
            }
            String name = nameField.getText() == null || nameField.getText().trim().isEmpty()
                    ? sku : nameField.getText().trim();
            Card c = new Card(sku, name, "Session Add", "Colorless", "Creature",
                    2026, price, qty, 50, false, slot.isEmpty() ? "Intake Sorting Desk" : slot.toUpperCase());
            catalog.put(sku, c);
            table.getItems().add(c);
            result.setText("Added (session-only): " + c + " -> " + StorageLocation.route(c));
        });
        Button removeBtn = new Button("Remove");
        removeBtn.setOnAction(e -> {
            String sku = skuField.getText() == null ? "" : skuField.getText().trim();
            Card target = catalog.get(sku);
            if (target == null) {
                result.setText("Not in catalog: " + sku);
                return;
            }
            javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION,
                    "Remove " + target.getName() + " [" + sku + "] from inventory?",
                    javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
            confirm.setHeaderText("Confirm remove");
            if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                    != javafx.scene.control.ButtonType.OK) {
                result.setText("Remove cancelled.");
                return;
            }
            catalog.remove(sku);
            table.getItems().removeIf(k -> k.getSku().equals(sku));
            lastRemoved[0] = target;
            result.setText("Removed: " + target.getName() + " [" + sku + "] (Undo remove restores it)");
        });
        Button undoBtn = new Button("Undo remove");
        undoBtn.setOnAction(e -> {
            if (lastRemoved[0] == null) {
                result.setText("Nothing to undo.");
                return;
            }
            catalog.put(lastRemoved[0].getSku(), lastRemoved[0]);
            table.getItems().add(lastRemoved[0]);
            result.setText("Restored: " + lastRemoved[0].getName() + " [" + lastRemoved[0].getSku() + "]");
            lastRemoved[0] = null;
        });

        // Detail dock under the table: the table never shifts when selecting.
        HBox formRow = new HBox(8, nameField, priceField, qtyField, slotField);
        Button newCardBtn = new Button("New card");
        newCardBtn.setOnAction(e -> IntakeDialog.prompt(catalog, table, result));
        HBox btnRow = new HBox(8, addBtn, removeBtn, undoBtn, newCardBtn);
        VBox detail = new VBox(8, stats, formRow, btnRow);
        HBox bottom = new HBox(12, preview, detail);

        return new VBox(8, new HBox(8, skuField, searchBtn, locateBtn),
                result, linksBox, table, bottom);
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
