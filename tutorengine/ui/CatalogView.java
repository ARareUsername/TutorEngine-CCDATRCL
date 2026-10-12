package tutorengine.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.sql.Timestamp;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;
import tutorengine.util.CSVLoader;

// Catalog view: SKU search (hit/miss) + full sortable table.
// indexOf()/lookup()/loadCards() are toolkit-free (headless main() below covers them).
// locateHandler is set by FxApp: Locate buttons send a slot id to the Storage view.
public final class CatalogView {
    private CatalogView() {}

    public static java.util.function.Consumer<String> locateHandler;
    public static Card lastLocatedCard;
    public static Map<String, Card> activeCatalog;
    public static TableView<Card> activeTable;
    public static List<Card> activeCards;
    public static Map<String, List<String>> activeSkuGraph;
    private static String lastImportBatch;
    private static List<String> lastImportSkus = new ArrayList<>();
    public static CardDetailPanel activeDetailPanel;
    private static final List<Card> pendingCards = new java.util.ArrayList<>();

    public enum ActionType {
        PROPERTY_CHANGE,
        ADD_CARD,
        REMOVE_CARD
    }

    public static class CatalogAction {
        public final ActionType type;
        public final Card card;
        public final String field;
        public final Object oldValue;
        public final Object newValue;

        public CatalogAction(ActionType type, Card card, String field, Object oldValue, Object newValue) {
            this.type = type;
            this.card = card;
            this.field = field;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public static CatalogAction property(Card card, String field, Object oldVal, Object newVal) {
            return new CatalogAction(ActionType.PROPERTY_CHANGE, card, field, oldVal, newVal);
        }

        public static CatalogAction add(Card card) {
            return new CatalogAction(ActionType.ADD_CARD, card, null, null, card);
        }

        public static CatalogAction remove(Card card) {
            return new CatalogAction(ActionType.REMOVE_CARD, card, null, card, null);
        }
    }

    public static class EditRecord extends CatalogAction {
        public EditRecord(Card card, String field, Object oldValue, Object newValue) {
            super(ActionType.PROPERTY_CHANGE, card, field, oldValue, newValue);
        }
    }

    public static final java.util.Deque<CatalogAction> undoStack = new java.util.ArrayDeque<>();
    public static final java.util.Deque<CatalogAction> redoStack = new java.util.ArrayDeque<>();
    public static final javafx.collections.ObservableList<String> recentSearches = javafx.collections.FXCollections.observableArrayList();
    public static java.util.function.Consumer<String> searchTrigger;

    public static void recordRecentSku(String sku) {
        if (sku == null || sku.isBlank()) return;
        String clean = sku.trim();
        recentSearches.remove(clean);
        recentSearches.add(0, clean);
        while (recentSearches.size() > 10) {
            recentSearches.remove(recentSearches.size() - 1);
        }
    }

    public static void selectCardBySku(String sku) {
        if (sku == null || sku.isBlank()) return;
        if (searchTrigger != null) {
            searchTrigger.accept(sku.trim());
        }
    }

    public static void addCard(Card c) {
        if (c == null) return;
        if (activeCards == null) {
            activeCards = new ArrayList<>();
        }
        if (!activeCards.contains(c)) {
            activeCards.add(c);
        }
        if (activeCatalog == null) {
            activeCatalog = new LinkedHashMap<>();
        }
        activeCatalog.put(c.getSku(), c);
        Runnable r = () -> {
            if (activeTable != null) {
                if (!activeTable.getItems().contains(c)) {
                    activeTable.getItems().add(c);
                    activeTable.scrollTo(c);
                    activeTable.getSelectionModel().select(c);
                }
            } else {
                if (!pendingCards.contains(c)) {
                    pendingCards.add(c);
                }
            }
        };
        try {
            if (javafx.application.Platform.isFxApplicationThread()) {
                r.run();
            } else {
                javafx.application.Platform.runLater(r);
            }
        } catch (IllegalStateException ignored) {
            r.run();
        }
    }

    // DB-first catalog: SQLite is the source of truth. CSV is only imported
    // explicitly via the Catalog menu, never read at launch. An empty table
    // opens an empty catalog; the 3-card fallback covers a missing DB only.
    public static List<Card> loadCards() {
        try {
            return new java.util.ArrayList<>(loadStoredCards());
        } catch (Exception e) {
            return new java.util.ArrayList<>(List.of(
                new Card("MTG-MH3-001", "Ajani, Nacatl Pariah", "Modern Horizons 3", "White", "Creature", 2024, 2150.00, 2, 92, true, "MH3-1"),
                new Card("MTG-OTJ-055", "Mana Drain", "Outlaws of Thunder Junction", "Blue", "Instant", 2024, 2600.00, 1, 98, false, "OTJ-1"),
                new Card("MTG-FDN-101", "Llanowar Elves", "Foundations", "Green", "Creature", 2024, 25.00, 48, 45, false, "FDN-1")));
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public static List<Card> loadStoredCards() throws Exception {
        List<Card> out = new ArrayList<>();
        try (java.sql.Connection c = tutorengine.db.Db.open();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "SELECT sku, name, set_name, color, card_type, release_year, price_php,"
                     + " quantity, demand, finish, box_id, mana_cost, power, toughness,"
                     + " set_code, collector_number, version_seq, version_label"
                     + " FROM card_instances ORDER BY name")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String setCode = nz(rs.getString(15));
                    String sku = nz(rs.getString(1));
                    if (sku.isEmpty()) sku = "MTG-" + setCode + "-" + nz(rs.getString(16));
                    String setName = nz(rs.getString(3));
                    if (setName.isEmpty()) setName = setCode;
                    String rawBox = rs.getString(11);
                    String boxId = (rawBox != null && !rawBox.isBlank())
                            ? rawBox.trim()
                            : (setCode.isEmpty() ? "Unassigned" : setCode.toUpperCase() + "-1");
                    out.add(new Card(
                            sku, nz(rs.getString(2)), setName, nz(rs.getString(4)), nz(rs.getString(5)),
                            rs.getInt(6), rs.getDouble(7), rs.getInt(8), rs.getInt(9),
                            "foil".equalsIgnoreCase(nz(rs.getString(10))), boxId,
                            nz(rs.getString(12)), nz(rs.getString(13)), nz(rs.getString(14)),
                            setCode, "",
                            nz(rs.getString(16)), rs.getInt(17), nz(rs.getString(18))));
                }
            }
        }
        return out;
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

    // Standalone tool: no login gate; mutations always allowed.
    static boolean needLogin(Label result) {
        return false;
    }

    static String formatStats(Card c, String status) {
        String base = c.getName() + " [" + c.getSku() + "]\n"
                + c.getSetName() + " · " + c.getColor() + " " + c.getCardType() + " " + c.getReleaseYear() + "\n"
                + String.format("PHP %.2f x%d | Demand %d%s\n", c.getPrice(), c.getQuantity(),
                        c.getDemandScore(), c.isFoil() ? " | FOIL" : "")
                + "Shelf: " + StorageLocation.route(c) + " (box: " + c.getBoxLocationId() + ")\n"
                + String.format("Priority %.2f", c.calculatePriority());
        if (status != null && !status.isEmpty()) {
            return base + "\n[" + status + "]";
        }
        return base;
    }

    static void renderStats(Label stats, Card c, String status) {
        stats.setText(formatStats(c, status));
    }

    public static Node build(List<Card> cards) {
        Map<String, Card> catalog = indexOf(cards);
        activeCatalog = catalog;
        activeCards = cards;
        Map<String, List<String>> skuGraph = tutorengine.algorithms.SkuCorrector.build(cards);
        activeSkuGraph = skuGraph;
        javafx.scene.control.ComboBox<String> skuField = new javafx.scene.control.ComboBox<>(recentSearches);
        skuField.setEditable(true);
        skuField.getEditor().setPromptText("SKU e.g. MTG-OTJ-055");
        skuField.setPrefWidth(220);
        Label result = new Label("Type an SKU and press Search.");
        result.setWrapText(true);
        CardDetailPanel detailPanel = new CardDetailPanel();
        activeDetailPanel = detailPanel;
        Button searchBtn = new Button("Search SKU");
        searchBtn.setDefaultButton(true);
        searchTrigger = s -> {
            skuField.getEditor().setText(s);
            searchBtn.fire();
        };
        skuField.setOnAction(e -> {
            String val = skuField.getValue();
            if (val != null && !val.isBlank() && !val.equals(skuField.getEditor().getText())) {
                skuField.getEditor().setText(val);
                searchBtn.fire();
            }
        });
        skuField.getEditor().setOnAction(e -> searchBtn.fire());
        Button locateBtn = new Button("Locate on shelf");
        locateBtn.setVisible(false);
        VBox linksBox = new VBox(4);
        Card[] found = new Card[1];
        locateBtn.setOnAction(e -> {
            lastLocatedCard = found[0];
            if (locateHandler != null && found[0] != null)
                locateHandler.accept(StorageLocation.route(found[0]));
        });
        searchBtn.setOnAction(e -> {
            String q = skuField.getEditor().getText() == null ? "" : skuField.getEditor().getText().trim();
            if (!q.isEmpty()) {
                recordRecentSku(q);
            }
            Card c = lookup(catalog, q);
            result.getStyleClass().removeAll(Theme.BADGE_OK, Theme.BADGE_LOW_STOCK, Theme.BADGE_FOIL);
            linksBox.getChildren().clear();
            locateBtn.setVisible(false);
            found[0] = c;
            if (c == null) {
                detailPanel.clear();
                List<Card> near = tutorengine.algorithms.SkuCorrector.multiSearch(catalog, activeSkuGraph, q);
                if (near.isEmpty()) {
                    result.setText("Not found: " + q);
                } else {
                    result.setText("No exact match — closest (click to open):");
                    for (int i = 0; i < Math.min(3, near.size()); i++) {
                        Card n = near.get(i);
                        String field = tutorengine.algorithms.SkuCorrector
                                .matchedField(catalog, activeSkuGraph, q, n);
                        Hyperlink link = new Hyperlink(
                                n.getName() + " [" + n.getSku() + "] (" + field + ")");
                        link.setOnAction(ev -> {
                            skuField.getEditor().setText(n.getSku());
                            searchBtn.fire();
                        });
                        linksBox.getChildren().add(link);
                    }
                }
            } else {
                result.setText("Found: " + c.getName() + " [" + c.getSku() + "]");
                result.getStyleClass().add(Theme.badgeFor(c.getQuantity(), c.isFoil()));
                detailPanel.showCard(c);
                locateBtn.setVisible(true);
            }
        });

        TableView<Card> table = new TableView<>();
        activeTable = table;
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        table.setPrefHeight(260);
        table.setMinHeight(180);
        // Table columns - captured from col()/numCol() for inline editing
        TableColumn<Card, String> colName = col("Name", c -> c.getName());
        TableColumn<Card, String> colSKU = col("SKU", c -> c.getSku());
        TableColumn<Card, String> colCollector = col("Collector #", 
            c -> c.getCollectorNumber().isEmpty() ? "—" : c.getCollectorNumber());
        TableColumn<Card, String> colVersion = col("Version", 
            c -> c.getVersionLabel().isEmpty() ? "—" : c.getVersionLabel());
        TableColumn<Card, String> colPrice = numCol("Price", c -> String.format("%.2f", c.getPrice()));
        TableColumn<Card, String> colQty = numCol("Qty", c -> String.valueOf(c.getQuantity()));
        TableColumn<Card, String> colDemand = numCol("Demand", c -> String.valueOf(c.getDemandScore()));
        TableColumn<Card, String> colFlag = col("Flag", c -> c.isFoil() ? "FOIL" : (c.getQuantity() <= 3 ? "LOW" : "OK"));
        TableColumn<Card, String> colLocation = col("Location", c -> c.getBoxLocationId() == null || c.getBoxLocationId().isBlank() ? "Unassigned" : c.getBoxLocationId());
        TableColumn<Card, String> colPriority = numCol("Priority", c -> String.format("%.2f", c.calculatePriority()));

        colPrice.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        colQty.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        colDemand.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());

        table.getColumns().addAll(
            colName, colSKU, colCollector, colVersion, colPrice, colQty, colDemand,
            colFlag, colLocation, colPriority);
        table.getItems().addAll(cards);

        // Inline editing handlers with dedup and validation
        colPrice.setOnEditCommit(event -> {
            Card card = event.getRowValue();
            String oldVal = event.getOldValue();
            String newVal = event.getNewValue();
            if (newVal == null || newVal.trim().equals(oldVal != null ? oldVal.trim() : "")) return;
            try {
                double price = Double.parseDouble(newVal.trim());
                if (price < 0) throw new NumberFormatException();
                double oldPrice = Double.parseDouble(oldVal);
                card.setPrice(price);
                recordEdit(card, "price", oldPrice, price);
                result.setText(String.format("Price updated: %.2f PHP", price));
                table.refresh();
            } catch (NumberFormatException e) {
                result.setText("Invalid price format (must be positive number).");
                card.setPrice(Double.parseDouble(oldVal));
                table.refresh();
            }
        });
        colQty.setOnEditCommit(event -> {
            Card card = event.getRowValue();
            String oldVal = event.getOldValue();
            String newVal = event.getNewValue();
            if (newVal == null || newVal.trim().equals(oldVal != null ? oldVal.trim() : "")) return;
            try {
                int qty = Integer.parseInt(newVal.trim());
                if (qty < 0 || qty > 999) throw new NumberFormatException();
                int oldQty = Integer.parseInt(oldVal);
                card.setQuantity(qty);
                recordEdit(card, "quantity", oldQty, qty);
                result.setText("Quantity updated: " + qty);
                table.refresh();
            } catch (NumberFormatException e) {
                result.setText("Invalid quantity format (min 0, max 999).");
                card.setQuantity(Integer.parseInt(oldVal));
                table.refresh();
            }
        });
        colDemand.setOnEditCommit(event -> {
            Card card = event.getRowValue();
            String oldVal = event.getOldValue();
            String newVal = event.getNewValue();
            if (newVal == null || newVal.trim().equals(oldVal != null ? oldVal.trim() : "")) return;
            try {
                int demand = Integer.parseInt(newVal.trim());
                if (demand < 0) throw new NumberFormatException();
                int oldDemand = Integer.parseInt(oldVal);
                card.setDemandScore(demand);
                recordEdit(card, "demandScore", oldDemand, demand);
                result.setText("Demand updated: " + demand);
                table.refresh();
            } catch (NumberFormatException e) {
                result.setText("Invalid demand format.");
                card.setDemandScore(Integer.parseInt(oldVal));
                table.refresh();
            }
        });

        if (!pendingCards.isEmpty()) {
            for (Card pc : pendingCards) {
                catalog.put(pc.getSku(), pc);
                if (!table.getItems().contains(pc)) table.getItems().add(pc);
                if (!cards.contains(pc)) cards.add(pc);
            }
            pendingCards.clear();
        }
        // Row click selects the card's SKU (reuses search: details + picture + Locate).
        table.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> {
            if (sel != null) {
                if (!sel.getSku().equals(skuField.getEditor().getText().trim())) {
                    skuField.getEditor().setText(sel.getSku());
                    searchBtn.fire();
                } else {
                    detailPanel.showCard(sel);
                }
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

            @SuppressWarnings("unchecked")
            javafx.concurrent.Task<javafx.scene.image.Image>[] currentTask = new javafx.concurrent.Task[1];

            Runnable startLoad = () -> {
                if (currentTask[0] != null) {
                    currentTask[0].cancel();
                    currentTask[0] = null;
                }
                iv.setImage(null);
                if (row.isHover() && !row.isEmpty()) {
                    Card c = row.getItem();
                    final int tw = DisplaySettings.cards().tooltip;
                    // Tooltips never upscale a smaller source: S uses small, else normal.
                    final CardImageCache.Variant src = DisplaySettings.cards() == DisplaySettings.CardTier.S
                            ? CardImageCache.Variant.SMALL : CardImageCache.Variant.NORMAL;
                    javafx.concurrent.Task<javafx.scene.image.Image> load =
                            new javafx.concurrent.Task<>() {
                        @Override protected javafx.scene.image.Image call() {
                            if (isCancelled()) return null;
                            return CardImageCache.getFx(c.getSku(), c.getName(), tw, src, c.getSetCode(), c.getCollectorNumber());
                        }
                    };
                    load.setOnSucceeded(done -> {
                        if (!load.isCancelled() && load.getValue() != null && row.isHover() && row.getItem() == c)
                            iv.setImage(load.getValue());
                    });
                    currentTask[0] = load;
                    new Thread(load, "card-hover").start();
                }
            };

            row.hoverProperty().addListener((ho, was, is) -> {
                if (is) {
                    startLoad.run();
                } else {
                    if (currentTask[0] != null) {
                        currentTask[0].cancel();
                        currentTask[0] = null;
                    }
                    iv.setImage(null);
                }
            });

            row.itemProperty().addListener((io, oldCard, newCard) -> {
                if (row.isHover()) {
                    startLoad.run();
                } else {
                    if (currentTask[0] != null) {
                        currentTask[0].cancel();
                        currentTask[0] = null;
                    }
                    iv.setImage(null);
                }
            });

            return row;
        });
        // Session catalog editing (in-memory only, never written back to CSV).
        TextField nameField = new TextField();
        nameField.setPromptText("Name (for new)");
        nameField.setPrefWidth(130);
        TextField priceField = new TextField();
        priceField.setPromptText("Price");
        priceField.setPrefWidth(70);
        TextField qtyField = new TextField();
        qtyField.setPromptText("Qty (±)");
        qtyField.setPrefWidth(65);
        qtyField.setTooltip(new Tooltip("Quantity to add or remove (defaults to 1 if blank)"));
        TextField slotField = new TextField();
        slotField.setPromptText("Slot (blank=auto)");
        slotField.setPrefWidth(130);
        Button addBtn = new Button("Add");
        addBtn.setTooltip(new Tooltip("Increment quantity for existing card, or add new session card"));
        addBtn.setOnAction(e -> {
            if (needLogin(result)) return;
            String sku = skuField.getEditor().getText() == null ? "" : skuField.getEditor().getText().trim();
            if (sku.isEmpty() && table.getSelectionModel().getSelectedItem() != null) {
                sku = table.getSelectionModel().getSelectedItem().getSku();
            }
            if (sku.isEmpty() || !sku.matches("[A-Za-z0-9-]+")) {
                result.setText("Invalid SKU: letters, digits, hyphens only.");
                return;
            }
            Card existing = catalog.get(sku);
            if (existing == null && table.getSelectionModel().getSelectedItem() != null
                    && sku.equals(table.getSelectionModel().getSelectedItem().getSku())) {
                existing = table.getSelectionModel().getSelectedItem();
            }

            if (existing != null) {
                int delta = 1;
                String qStr = qtyField.getText() == null ? "" : qtyField.getText().trim();
                if (!qStr.isEmpty()) {
                    try {
                        delta = Integer.parseInt(qStr);
                        if (delta <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException ex) {
                        result.setText("Invalid quantity: use a positive whole number.");
                        return;
                    }
                }
                int oldQty = existing.getQuantity();
                if (oldQty >= 999) {
                    result.setText("Quantity already at maximum (999).");
                    return;
                }
                int newQty = adjustCardQuantity(existing, delta);
                result.setText(String.format("Added +%d to %s [%s] (Qty: %d -> %d)",
                        newQty - oldQty, existing.getName(), existing.getSku(), oldQty, newQty));
                table.refresh();
                detailPanel.showCard(existing);
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
            if (!slot.isEmpty() && !StorageLocation.isValidSlot(slot)) {
                result.setText("Unknown slot: use a box id like BLB-1 (blank = auto).");
                return;
            }
            String name = nameField.getText() == null || nameField.getText().trim().isEmpty()
                    ? sku : nameField.getText().trim();
            Card c = new Card(sku, name, "Session Add", "Colorless", "Creature",
                    2026, price, qty, 50, false, slot.isEmpty() ? "Intake Sorting Desk" : slot.toUpperCase());
            catalog.put(sku, c);
            table.getItems().add(c);
            if (activeCards != null && !activeCards.contains(c)) activeCards.add(c);
            recordAddCard(c);
            result.setText("Added (session-only): " + c + " -> " + StorageLocation.route(c));
            detailPanel.showCard(c);
        });
        Button removeBtn = new Button("Remove");
        removeBtn.setTooltip(new Tooltip("Decrement quantity, or remove record if out of stock"));
        removeBtn.setOnAction(e -> {
            if (needLogin(result)) return;
            String sku = skuField.getEditor().getText() == null ? "" : skuField.getEditor().getText().trim();
            if (sku.isEmpty() && table.getSelectionModel().getSelectedItem() != null) {
                sku = table.getSelectionModel().getSelectedItem().getSku();
            }
            Card target = catalog.get(sku);
            if (target == null && table.getSelectionModel().getSelectedItem() != null
                    && sku.equals(table.getSelectionModel().getSelectedItem().getSku())) {
                target = table.getSelectionModel().getSelectedItem();
            }
            if (target == null) {
                result.setText("Not in catalog: " + sku);
                return;
            }

            int delta = 1;
            String qStr = qtyField.getText() == null ? "" : qtyField.getText().trim();
            if (!qStr.isEmpty()) {
                try {
                    delta = Integer.parseInt(qStr);
                    if (delta <= 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    result.setText("Invalid quantity: use a positive whole number.");
                    return;
                }
            }

            int currentQty = target.getQuantity();
            if (currentQty > 0) {
                int newQty = adjustCardQuantity(target, -delta);
                result.setText(String.format("Decremented -%d from %s [%s] (Qty: %d -> %d)",
                        currentQty - newQty, target.getName(), target.getSku(), currentQty, newQty));
                table.refresh();
                detailPanel.showCard(target);
            } else {
                final Card toRemove = target;
                final String removeSku = target.getSku();
                DialogHelper.showConfirmation(
                        "Confirm remove",
                        "Card is out of stock (qty 0). Remove " + toRemove.getName() + " [" + removeSku + "] completely from catalog?",
                        () -> {
                            catalog.remove(removeSku);
                            table.getItems().removeIf(k -> k.getSku().equals(removeSku));
                            if (activeCards != null) activeCards.removeIf(k -> k.getSku().equals(removeSku));
                            recordRemoveCard(toRemove);
                            result.setText("Removed: " + toRemove.getName() + " [" + removeSku + "] (Undo restores it)");
                            detailPanel.clear();
                        },
                        () -> {
                            result.setText("Remove cancelled.");
                        }
                );
            }
        });
        Button undoBtn = new Button("Undo");
        undoBtn.setTooltip(new Tooltip("Undo last catalog action (Ctrl+Z)"));
        undoBtn.setOnAction(e -> {
            if (needLogin(result)) return;
            if (undoLastEdit()) {
                result.setText("Last action undone.");
            } else {
                result.setText("Nothing to undo.");
            }
        });

        Button redoBtn = new Button("Redo");
        redoBtn.setTooltip(new Tooltip("Redo last undone catalog action (Ctrl+Y)"));
        redoBtn.setOnAction(e -> {
            if (needLogin(result)) return;
            if (redoLastEdit()) {
                result.setText("Last action redone.");
            } else {
                result.setText("Nothing to redo.");
            }
        });

        Button undoImportBtn = new Button("Undo import");
        undoImportBtn.setTooltip(new Tooltip("Remove exactly the last import batch"));
        undoImportBtn.setDisable(lastImportSkus.isEmpty());
        undoImportBtn.setOnAction(e -> {
            if (lastImportBatch == null) {
                result.setText("Nothing to undo.");
                return;
            }
            String batchId = lastImportBatch;
            List<String> skus = new ArrayList<>(lastImportSkus);
            new Thread(() -> {
                try {
                    int removed;
                    try (java.sql.Connection conn = tutorengine.db.Db.open()) {
                        removed = CSVLoader.deleteImportBatch(conn, batchId);
                    }
                    javafx.application.Platform.runLater(() -> {
                        for (String sku : skus) removeCardBySku(catalog, table, sku);
                        activeSkuGraph = tutorengine.algorithms.SkuCorrector.build(
                                new ArrayList<>(catalog.values()));
                        lastImportBatch = null;
                        lastImportSkus = new ArrayList<>();
                        undoImportBtn.setDisable(true);
                        result.setText("Import undone: " + removed + " row(s) removed.");
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() ->
                            result.setText("Undo import failed: " + ex.getMessage()));
                }
            }).start();
        });

        Button importBtn = new Button("Import CSV…");
        importBtn.setTooltip(new Tooltip("Load a CSV file into the database"));
        importBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Import cards CSV");
            fc.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("CSV files", "*.csv"));
            java.io.File file = fc.showOpenDialog(table.getScene() != null
                    ? table.getScene().getWindow() : null);
            if (file == null) return;
            result.setText("Importing " + file.getName() + "…");
            new Thread(() -> {
                try {
                    List<Card> parsed = CSVLoader.load(file.getAbsolutePath());
                    String batchId = "IMPORT-" + System.currentTimeMillis();
                    CSVLoader.ImportResult r;
                    try (java.sql.Connection conn = tutorengine.db.Db.open()) {
                        r = CSVLoader.importBatch(conn, parsed, batchId);
                    }
                    javafx.application.Platform.runLater(() -> {
                        for (Card c : r.imported()) {
                            catalog.put(c.getSku(), c);
                            addCard(c);
                        }
                        activeSkuGraph = tutorengine.algorithms.SkuCorrector.build(
                                new ArrayList<>(catalog.values()));
                        lastImportBatch = batchId;
                        lastImportSkus = new ArrayList<>();
                        for (Card c : r.imported()) lastImportSkus.add(c.getSku());
                        undoImportBtn.setDisable(lastImportSkus.isEmpty());
                        result.setText("Imported " + r.imported().size()
                                + ", skipped " + r.skipped() + " duplicate(s).");
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() ->
                            result.setText("Import failed: " + ex.getMessage()));
                }
            }).start();
        });

        // Detail dock under the table: the table never shifts when selecting.
        HBox formRow = new HBox(8, nameField, priceField, qtyField, slotField);
        HBox btnRow = new HBox(8, addBtn, removeBtn, undoBtn, redoBtn, importBtn, undoImportBtn);
        VBox controls = new VBox(8, formRow, btnRow);
        VBox bottom = new VBox(8, detailPanel.getNode(), controls);
        bottom.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        VBox root = new VBox(10, new HBox(8, skuField, searchBtn, locateBtn),
                result, linksBox, table, bottom);
        root.getStyleClass().add("view-container");

        root.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ke -> {
            if (new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.Z, javafx.scene.input.KeyCombination.CONTROL_DOWN).match(ke)) {
                undoBtn.fire();
                ke.consume();
            } else if (new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.Y, javafx.scene.input.KeyCombination.CONTROL_DOWN).match(ke)) {
                redoBtn.fire();
                ke.consume();
            }
        });
        return root;
    }

    /** Remove one SKU from the live catalog map, shared statics, and table. */
    private static void removeCardBySku(Map<String, Card> catalog, TableView<Card> table, String sku) {
        Card gone = catalog.remove(sku);
        if (activeCatalog != null) activeCatalog.remove(sku);
        if (activeCards != null) activeCards.removeIf(c -> c.getSku().equals(sku));
        if (table != null) table.getItems().removeIf(c -> c.getSku().equals(sku));
        if (gone == null && activeTable != null && activeTable != table) {
            activeTable.getItems().removeIf(c -> c.getSku().equals(sku));
        }
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
        System.out.println("stats loading=" + formatStats(cards.get(0), "loading…").contains("[loading…]"));
        activeCatalog = idx;
        activeCards = cards;
        Card testCard = new Card("TEST-SKU-999", "Test Card", "Test Set", "Colorless", "Artifact", 2026, 100.0, 1, 50, false, "CAB-T-TOP");
        addCard(testCard);
        System.out.println("addCard test=" + (lookup(activeCatalog, "TEST-SKU-999") != null));
        // Verify recent searches history (last 10, deduped, most-recent-first)
        recentSearches.clear();
        recordRecentSku("SKU-ALPHA");
        recordRecentSku("SKU-BETA");
        recordRecentSku("SKU-ALPHA"); // should move to front without duplicate
        boolean historyOk = recentSearches.size() == 2 && "SKU-ALPHA".equals(recentSearches.get(0));
        System.out.println("recent searches test=" + (historyOk ? "PASS" : "FAIL") + " (items: " + recentSearches + ")");
        System.out.println("CatalogView data OK.");
    }

    /**
     * Increment or decrement quantity on a card, bounded between 0 and 999.
     * Records the edit and updates SQLite DB.
     * Returns the updated quantity.
     */
    public static int adjustCardQuantity(Card card, int delta) {
        if (card == null) return -1;
        int oldQty = card.getQuantity();
        int newQty = Math.max(0, Math.min(999, oldQty + delta));
        if (newQty != oldQty) {
            recordEdit(card, "quantity", oldQty, newQty);
        }
        return newQty;
    }

    /**
     * Register a catalog edit in the history for undo/redo support.
     */
    public static void recordEdit(Card card, String field, Object oldValue, Object newValue) {
        if (oldValue != null && oldValue.equals(newValue)) return;
        applyEditValue(card, field, newValue);
        undoStack.push(CatalogAction.property(card, field, oldValue, newValue));
        redoStack.clear();

        if ("quantity".equals(field) && newValue instanceof Integer) {
            saveQuantityToDb(card.getName(), (Integer) newValue);
        }

        syncDetailPanel(card);
    }

    public static void recordAddCard(Card card) {
        if (card == null) return;
        undoStack.push(CatalogAction.add(card));
        redoStack.clear();
        syncDetailPanel(card);
    }

    public static void recordRemoveCard(Card card) {
        if (card == null) return;
        undoStack.push(CatalogAction.remove(card));
        redoStack.clear();
        syncDetailPanel(card);
    }

    public static void saveQuantityToDb(String cardName, int quantity) {
        try (java.sql.Connection c = tutorengine.db.Db.open();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "UPDATE card_instances SET quantity = ? WHERE name = ?")) {
            ps.setInt(1, quantity);
            ps.setString(2, cardName);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    /**
     * Undo the last catalog action (property edit, addition, or removal).
     */
    public static boolean undoLastEdit() {
        if (undoStack.isEmpty()) return false;
        CatalogAction action = undoStack.pop();
        applyUndo(action);
        redoStack.push(action);
        if (activeTable != null) activeTable.refresh();
        syncDetailPanel(action.card);
        return true;
    }

    private static void applyUndo(CatalogAction action) {
        if (action == null) return;
        switch (action.type) {
            case PROPERTY_CHANGE -> {
                applyEditValue(action.card, action.field, action.oldValue);
                if ("quantity".equals(action.field) && action.oldValue instanceof Integer) {
                    saveQuantityToDb(action.card.getName(), (Integer) action.oldValue);
                }
            }
            case ADD_CARD -> {
                if (activeCatalog != null) activeCatalog.remove(action.card.getSku());
                if (activeCards != null) activeCards.removeIf(c -> c.getSku().equals(action.card.getSku()));
                if (activeTable != null) activeTable.getItems().removeIf(c -> c.getSku().equals(action.card.getSku()));
            }
            case REMOVE_CARD -> {
                if (activeCatalog != null) activeCatalog.put(action.card.getSku(), action.card);
                if (activeCards != null && !activeCards.contains(action.card)) activeCards.add(action.card);
                if (activeTable != null && !activeTable.getItems().contains(action.card)) {
                    activeTable.getItems().add(action.card);
                    activeTable.getSelectionModel().select(action.card);
                    activeTable.scrollTo(action.card);
                }
            }
        }
    }

    /**
     * Redo the last undone catalog action.
     */
    public static boolean redoLastEdit() {
        if (redoStack.isEmpty()) return false;
        CatalogAction action = redoStack.pop();
        applyRedo(action);
        undoStack.push(action);
        if (activeTable != null) activeTable.refresh();
        syncDetailPanel(action.card);
        return true;
    }

    private static void applyRedo(CatalogAction action) {
        if (action == null) return;
        switch (action.type) {
            case PROPERTY_CHANGE -> {
                applyEditValue(action.card, action.field, action.newValue);
                if ("quantity".equals(action.field) && action.newValue instanceof Integer) {
                    saveQuantityToDb(action.card.getName(), (Integer) action.newValue);
                }
            }
            case ADD_CARD -> {
                if (activeCatalog != null) activeCatalog.put(action.card.getSku(), action.card);
                if (activeCards != null && !activeCards.contains(action.card)) activeCards.add(action.card);
                if (activeTable != null && !activeTable.getItems().contains(action.card)) {
                    activeTable.getItems().add(action.card);
                    activeTable.getSelectionModel().select(action.card);
                    activeTable.scrollTo(action.card);
                }
            }
            case REMOVE_CARD -> {
                if (activeCatalog != null) activeCatalog.remove(action.card.getSku());
                if (activeCards != null) activeCards.removeIf(c -> c.getSku().equals(action.card.getSku()));
                if (activeTable != null) activeTable.getItems().removeIf(c -> c.getSku().equals(action.card.getSku()));
            }
        }
    }

    private static void syncDetailPanel(Card card) {
        if (activeDetailPanel == null || card == null) return;
        if (activeTable != null && !activeTable.getItems().contains(card)) {
            if (activeDetailPanel.getCurrentCard() != null && activeDetailPanel.getCurrentCard().getSku().equals(card.getSku())) {
                activeDetailPanel.clear();
            }
            return;
        }
        Card current = activeDetailPanel.getCurrentCard();
        if (current != null && current.getSku().equals(card.getSku())) {
            activeDetailPanel.showCard(card);
        } else if (activeTable != null && activeTable.getSelectionModel().getSelectedItem() != null
                && activeTable.getSelectionModel().getSelectedItem().getSku().equals(card.getSku())) {
            activeDetailPanel.showCard(card);
        }
    }

    private static void applyEditValue(Card card, String field, Object value) {
        if (card == null || field == null || value == null) return;
        if ("quantity".equals(field)) {
            card.setQuantity((Integer) value);
        } else if ("price".equals(field)) {
            card.setPrice((Double) value);
        } else if ("demandScore".equals(field)) {
            card.setDemandScore((Integer) value);
        }
    }

    /**
     * Update storage location for cards matching the specified name and set code.
     * Synchronizes in-memory catalog, table rows, and active detail dock.
     */
    public static void updateCardStorageLocation(String name, String setCode, String newBoxLocationId) {
        if (name == null || newBoxLocationId == null) return;
        if (activeCards == null) activeCards = new ArrayList<>();
        if (activeCatalog == null) activeCatalog = new LinkedHashMap<>();
        Card matched = null;
        if (activeCards != null) {
            for (Card c : activeCards) {
                if (c.getName().equalsIgnoreCase(name.trim())) {
                    if (setCode == null || setCode.isBlank() || setCode.equalsIgnoreCase(c.getSetCode())) {
                        c.setBoxLocationId(newBoxLocationId);
                        matched = c;
                    }
                }
            }
        }
        if (activeCatalog != null) {
            for (Card c : activeCatalog.values()) {
                if (c.getName().equalsIgnoreCase(name.trim())) {
                    if (setCode == null || setCode.isBlank() || setCode.equalsIgnoreCase(c.getSetCode())) {
                        c.setBoxLocationId(newBoxLocationId);
                        matched = c;
                    }
                }
            }
        }
        if (matched != null) {
            syncDetailPanel(matched);
        } else {
            String normSet = setCode != null && !setCode.isBlank() ? setCode.toUpperCase() : "MTG";
            String sku = "STORED-" + normSet + "-" + Math.abs((name.trim() + normSet).hashCode() % 10000);
            Card newCard = new Card(
                    sku,
                    name.trim(),
                    normSet,
                    "Colorless",
                    "Card",
                    2026,
                    50.0,
                    1,
                    50,
                    false,
                    newBoxLocationId,
                    "",
                    "",
                    "",
                    normSet
            );
            addCard(newCard);
            syncDetailPanel(newCard);
        }
        if (activeTable != null) {
            activeTable.refresh();
        }
    }

    /**
     * Rename storage location for all cards matching oldBoxId to newBoxId.
     * Synchronizes in-memory catalog, table rows, and active detail dock.
     */
    public static void renameCardStorageLocation(String oldBoxId, String newBoxId) {
        if (oldBoxId == null || newBoxId == null) return;
        String cleanOld = oldBoxId.trim();
        String cleanNew = newBoxId.trim();
        if (cleanOld.isEmpty() || cleanNew.isEmpty()) return;
        if (activeCards != null) {
            for (Card c : activeCards) {
                if (cleanOld.equalsIgnoreCase(c.getBoxLocationId())) {
                    c.setBoxLocationId(cleanNew);
                    syncDetailPanel(c);
                }
            }
        }
        if (activeCatalog != null) {
            for (Card c : activeCatalog.values()) {
                if (cleanOld.equalsIgnoreCase(c.getBoxLocationId())) {
                    c.setBoxLocationId(cleanNew);
                    syncDetailPanel(c);
                }
            }
        }
        if (activeTable != null) {
            activeTable.refresh();
        }
    }

    /**
     * Remove storage location for all cards matching oldBoxId, resetting them to "Unassigned".
     * Synchronizes in-memory catalog, table rows, and active detail dock.
     */
    public static void removeCardStorageLocation(String oldBoxId) {
        if (oldBoxId == null) return;
        String cleanOld = oldBoxId.trim();
        if (cleanOld.isEmpty()) return;
        if (activeCards != null) {
            for (Card c : activeCards) {
                if (cleanOld.equalsIgnoreCase(c.getBoxLocationId())) {
                    c.setBoxLocationId("Unassigned");
                    syncDetailPanel(c);
                }
            }
        }
        if (activeCatalog != null) {
            for (Card c : activeCatalog.values()) {
                if (cleanOld.equalsIgnoreCase(c.getBoxLocationId())) {
                    c.setBoxLocationId("Unassigned");
                    syncDetailPanel(c);
                }
            }
        }
        if (activeTable != null) {
            activeTable.refresh();
        }
    }
}
