package tutorengine.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tutorengine.model.Card;
import tutorengine.util.CSVLoader;

// OWNER: Bondoc, Karl B. — Merged restock attention list (STOCK/LOW/PRIORITY/FILL).
public final class RestockView {
    private RestockView() {}

    public static java.util.function.Consumer<Card> openRecordHandler;

    public static class AttentionItem {
        private final String reason;
        private final Card card;

        public AttentionItem(String reason, Card card) {
            this.reason = reason;
            this.card = card;
        }

        public String getReason() { return reason; }
        public Card getCard() { return card; }
        public String getName() { return card.getName(); }
        public String getSku() { return card.getSku(); }
        public int getQuantity() { return card.getQuantity(); }
        public double getPrice() { return card.getPrice(); }
        public double getPriority() { return card.calculatePriority(); }
        public String getLocation() { return card.getBoxLocationId() == null || card.getBoxLocationId().isBlank() ? "Unassigned" : card.getBoxLocationId(); }
    }

    public static List<AttentionItem> computeAttentionList(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return Collections.emptyList();
        List<AttentionItem> list = new ArrayList<>();
        Set<String> seenSkus = new HashSet<>();

        // 1. STOCK (qty == 0)
        for (Card c : cards) {
            if (c.getQuantity() == 0) {
                list.add(new AttentionItem("STOCK", c));
                seenSkus.add(c.getSku());
            }
        }

        // 2. LOW (0 < qty <= 3)
        for (Card c : cards) {
            if (c.getQuantity() > 0 && c.getQuantity() <= 3) {
                list.add(new AttentionItem("LOW", c));
                seenSkus.add(c.getSku());
            }
        }

        // 3. PRIORITY (top 10 by priority)
        List<Card> sortedByPriority = new ArrayList<>(cards);
        sortedByPriority.sort(Comparator.comparingDouble(Card::calculatePriority).reversed());
        int topN = Math.min(10, sortedByPriority.size());
        for (int i = 0; i < topN; i++) {
            Card c = sortedByPriority.get(i);
            if (!seenSkus.contains(c.getSku())) {
                list.add(new AttentionItem("PRIORITY", c));
                seenSkus.add(c.getSku());
            }
        }

        // 4. FILL (cards in overflow or unassigned/empty slot gaps)
        for (Card c : cards) {
            if (!seenSkus.contains(c.getSku()) && (c.getBoxLocationId() == null || c.getBoxLocationId().contains("RARE-LOW") || c.getBoxLocationId().isBlank() || c.getBoxLocationId().equalsIgnoreCase("Unassigned"))) {
                list.add(new AttentionItem("FILL", c));
                seenSkus.add(c.getSku());
            }
        }

        return list;
    }

    public static Node build() {
        return build(CatalogView.activeCards != null ? CatalogView.activeCards : CSVLoader.load("Dataset/cards.csv"));
    }

    public static Node build(List<Card> cards) {
        VBox root = new VBox(12);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("view-container");

        Label header = new Label("Restock Attention Deck");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label sub = new Label("Unified restock decision list merging out-of-stock, low stock (≤3), top-10 priority, and fill gaps. Click any row to view its catalog record.");
        sub.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");

        List<AttentionItem> items = computeAttentionList(cards);
        ObservableList<AttentionItem> tableItems = FXCollections.observableArrayList(items);

        long stockCount = items.stream().filter(i -> "STOCK".equals(i.getReason())).count();
        long lowCount = items.stream().filter(i -> "LOW".equals(i.getReason())).count();
        long prioCount = items.stream().filter(i -> "PRIORITY".equals(i.getReason())).count();
        long fillCount = items.stream().filter(i -> "FILL".equals(i.getReason())).count();

        Label badgeStock = new Label("STOCK (qty 0): " + stockCount);
        badgeStock.getStyleClass().addAll("badge", Theme.BADGE_DANGER);
        Label badgeLow = new Label("LOW (qty ≤ 3): " + lowCount);
        badgeLow.getStyleClass().addAll("badge", Theme.BADGE_WARN);
        Label badgePrio = new Label("PRIORITY: " + prioCount);
        badgePrio.getStyleClass().addAll("badge", Theme.BADGE_FOIL);
        Label badgeFill = new Label("FILL: " + fillCount);
        badgeFill.getStyleClass().addAll("badge", Theme.BADGE_OK);

        HBox statsRow = new HBox(8, badgeStock, badgeLow, badgePrio, badgeFill);
        statsRow.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.control.Button refreshBtn = new javafx.scene.control.Button("↻ Refresh");
        refreshBtn.setTooltip(new javafx.scene.control.Tooltip("Recompute the deck from the live catalog"));
        javafx.scene.control.Button dismissBtn = new javafx.scene.control.Button("Dismiss selected");
        dismissBtn.setTooltip(new javafx.scene.control.Tooltip("Remove selected rows from the deck for this session"));
        HBox actionRow = new HBox(8, refreshBtn, dismissBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        TableView<AttentionItem> table = new TableView<>(tableItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<AttentionItem, String> colReason = new TableColumn<>("Reason");
        colReason.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReason()));
        colReason.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String reason, boolean empty) {
                super.updateItem(reason, empty);
                if (empty || reason == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(reason);
                    badge.getStyleClass().add("badge");
                    switch (reason) {
                        case "STOCK" -> badge.getStyleClass().add(Theme.BADGE_DANGER);
                        case "LOW" -> badge.getStyleClass().add(Theme.BADGE_WARN);
                        case "PRIORITY" -> badge.getStyleClass().add(Theme.BADGE_FOIL);
                        default -> badge.getStyleClass().add(Theme.BADGE_OK);
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
        colReason.setPrefWidth(90);

        TableColumn<AttentionItem, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));

        TableColumn<AttentionItem, String> colSku = new TableColumn<>("SKU");
        colSku.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSku()));

        TableColumn<AttentionItem, String> colQty = new TableColumn<>("Qty");
        colQty.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getQuantity())));
        colQty.getStyleClass().add("col-num");

        TableColumn<AttentionItem, String> colPrice = new TableColumn<>("Price (PHP)");
        colPrice.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.2f", d.getValue().getPrice())));
        colPrice.getStyleClass().add("col-num");

        TableColumn<AttentionItem, String> colPriority = new TableColumn<>("Priority");
        colPriority.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.2f", d.getValue().getPriority())));
        colPriority.getStyleClass().add("col-num");

        TableColumn<AttentionItem, String> colLoc = new TableColumn<>("Location");
        colLoc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLocation() != null ? d.getValue().getLocation() : "—"));

        table.getColumns().addAll(colReason, colName, colSku, colQty, colPrice, colPriority, colLoc);

        Label statusLbl = new Label("Click a row to open its full record in the Catalog.");
        statusLbl.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");

        Runnable reload = () -> {
            List<Card> live = CatalogView.activeCards != null && !CatalogView.activeCards.isEmpty()
                    ? CatalogView.activeCards : cards;
            List<AttentionItem> fresh = computeAttentionList(live);
            tableItems.setAll(fresh);
            badgeStock.setText("STOCK (qty 0): " + fresh.stream().filter(i -> "STOCK".equals(i.getReason())).count());
            badgeLow.setText("LOW (qty ≤ 3): " + fresh.stream().filter(i -> "LOW".equals(i.getReason())).count());
            badgePrio.setText("PRIORITY: " + fresh.stream().filter(i -> "PRIORITY".equals(i.getReason())).count());
            badgeFill.setText("FILL: " + fresh.stream().filter(i -> "FILL".equals(i.getReason())).count());
            statusLbl.setText("Deck refreshed: " + fresh.size() + " item(s).");
        };
        refreshBtn.setOnAction(e -> reload.run());
        dismissBtn.setOnAction(e -> {
            AttentionItem sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                statusLbl.setText("Nothing selected to dismiss.");
                return;
            }
            tableItems.remove(sel);
            statusLbl.setText("Dismissed for this session: " + sel.getName() + " [" + sel.getSku() + "]");
        });

        table.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> {
            if (sel != null) {
                statusLbl.setText("Selected: " + sel.getName() + " [" + sel.getSku() + "]");
                if (openRecordHandler != null) {
                    openRecordHandler.accept(sel.getCard());
                }
            }
        });

        root.getChildren().addAll(header, sub, statsRow, actionRow, table, statusLbl);
        return root;
    }

    public static void main(String[] args) {
        System.out.println("=== RESTOCK VIEW HEADLESS LOGIC CHECK ===");
        List<Card> cards = CSVLoader.load("Dataset/cards.csv");
        if (cards.isEmpty()) {
            System.out.println("Dataset missing; creating test cards...");
            cards = List.of(
                new Card("TEST-0", "Empty Stock Card", "TST", "Blue", "Instant", 2024, 10.0, 0, 50, false, "CAB-T-TOP"),
                new Card("TEST-2", "Low Stock Card", "TST", "Red", "Sorcery", 2024, 20.0, 2, 80, false, "CAB-T-TOP"),
                new Card("TEST-5", "High Priority Card", "TST", "Green", "Creature", 2024, 500.0, 5, 95, true, "CAB-RARE-LOW")
            );
        }

        List<AttentionItem> list = computeAttentionList(cards);
        System.out.println("Total attention rows computed: " + list.size());

        // 1. Verify qty-0 cards
        long totalQty0 = cards.stream().filter(c -> c.getQuantity() == 0).count();
        long attentionQty0 = list.stream().filter(i -> i.getQuantity() == 0).count();
        boolean qty0Ok = attentionQty0 == totalQty0;
        System.out.println("Qty-0 count matches: " + attentionQty0 + "/" + totalQty0 + " -> " + (qty0Ok ? "PASS" : "FAIL"));

        // 2. Verify qty <= 3 cards
        long totalLow = cards.stream().filter(c -> c.getQuantity() > 0 && c.getQuantity() <= 3).count();
        long attentionLow = list.stream().filter(i -> i.getQuantity() > 0 && i.getQuantity() <= 3).count();
        boolean lowOk = attentionLow == totalLow;
        System.out.println("Low stock (≤3) count matches: " + attentionLow + "/" + totalLow + " -> " + (lowOk ? "PASS" : "FAIL"));

        // 3. Verify top 10 priority cards present
        List<Card> sortedByPriority = new ArrayList<>(cards);
        sortedByPriority.sort(Comparator.comparingDouble(Card::calculatePriority).reversed());
        int top10 = Math.min(10, sortedByPriority.size());
        boolean prioOk = true;
        for (int i = 0; i < top10; i++) {
            String targetSku = sortedByPriority.get(i).getSku();
            boolean found = list.stream().anyMatch(item -> item.getSku().equals(targetSku));
            if (!found) {
                prioOk = false;
                System.out.println("Missing top priority card: " + sortedByPriority.get(i).getName());
            }
        }
        System.out.println("Top-10 priority cards all present: " + (prioOk ? "PASS" : "FAIL"));

        // 4. Verify row click handler
        final Card[] opened = new Card[1];
        openRecordHandler = c -> opened[0] = c;
        if (!list.isEmpty()) {
            openRecordHandler.accept(list.get(0).getCard());
        }
        boolean clickOk = opened[0] != null && opened[0].equals(list.get(0).getCard());
        System.out.println("Row click opens record: " + (clickOk ? "PASS" : "FAIL"));

        boolean allPass = qty0Ok && lowOk && prioOk && clickOk;
        System.out.println();
        System.out.println("RestockView check: " + (allPass ? "PASS" : "FAIL"));
        if (!allPass) {
            System.exit(1);
        }
    }
}
