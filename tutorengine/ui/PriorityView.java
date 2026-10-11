package tutorengine.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tutorengine.datastructures.CustomHeap;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;

// Priority view: Interactive Max-Heap Restock & Showcase Priority Processing Dashboard.
// Integrates student-built CustomHeap with live weight recalculations,
// line-jumping simulation, and array-backed heap inspection for defense (§7 Heap, §8 Priority).
public final class PriorityView {
    private PriorityView() {}

    // Row model for the ranked queue table
    public record PriorityRow(
            int rank,
            String sku,
            String name,
            double price,
            int quantity,
            int demand,
            boolean foil,
            double score,
            String action,
            Card card
    ) {}

    // Model for the dispatch audit log
    public record DispatchedRecord(
            String timestamp,
            String sku,
            String name,
            double score,
            String action
    ) {}

    // Model for array-backed heap inspection
    public record HeapArrayNode(
            int index,
            String name,
            double score,
            String parentText,
            String leftText,
            String rightText,
            String validText
    ) {}

    // Toolkit-free headless topN method required by specs and tests
    public static List<Card> topN(List<Card> cards, int n) {
        CustomHeap heap = new CustomHeap();
        if (cards != null) {
            for (Card c : cards) heap.offer(c);
        }
        List<Card> out = new ArrayList<>(Math.min(n, heap.size()));
        for (int i = 0; i < n; i++) {
            Card c = heap.poll();
            if (c == null) break;
            out.add(c);
        }
        return out;
    }

    public static String recommendAction(Card c) {
        if (c == null) return "None";
        if (c.getPrice() >= 1500.0 || c.getDemandScore() >= 90) {
            return "Showcase Display (High Value)";
        }
        if (c.getQuantity() <= 2 && c.getDemandScore() >= 60) {
            return "Urgent Restock (Low Stock)";
        }
        if (c.isFoil()) {
            return "Foil Humidity Protection";
        }
        if (c.getPrice() >= 500.0) {
            return "Rare Binder Filing";
        }
        return "Bulk Box Storage";
    }

    public static Node build(List<Card> cards) {
        List<Card> loaded = cards != null && !cards.isEmpty() ? cards : CatalogView.loadCards();
        List<Card> activeCards = new ArrayList<>(loaded);

        // Formula weights state (default values per §8 & PriorityDesk)
        double[] weights = new double[]{0.50, 0.35, 3.00, 10.00}; // price, demand, qty, foil
        ObservableList<DispatchedRecord> dispatchedHistory = FXCollections.observableArrayList();

        // Initialize student-built CustomHeap
        CustomHeap[] heapHolder = new CustomHeap[]{
                new CustomHeap(c -> c.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]))
        };
        for (Card c : activeCards) heapHolder[0].offer(c);

        // Header view
        Label titleLabel = new Label("Priority Dispatch & Restock Desk");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label subtitleLabel = new Label("Student-Built CustomHeap (Max-Heap) — Real-time priority ranking combining Price, Demand, Stock Count, and Foil status.");
        subtitleLabel.setStyle("-fx-text-fill: -color-fg-muted;");

        Label queueSizeBadge = new Label("Queue Size: " + heapHolder[0].size());
        queueSizeBadge.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-font-weight: bold;");

        Label rootScoreBadge = new Label();
        rootScoreBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-font-weight: bold;");

        Label invariantBadge = new Label("Heap Invariant: ✓ Verified (Parent >= Children)");
        invariantBadge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-font-weight: bold;");

        HBox badgesRow = new HBox(8, queueSizeBadge, rootScoreBadge, invariantBadge);
        badgesRow.setAlignment(Pos.CENTER_LEFT);

        VBox headerBox = new VBox(4, titleLabel, subtitleLabel, badgesRow);

        // ==========================================
        // 1. SPOTLIGHT CARD CONTAINER (Top of Heap)
        // ==========================================
        VBox spotlightBox = new VBox(8);
        spotlightBox.setPadding(new Insets(12, 14, 12, 14));
        spotlightBox.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: #d97706; -fx-border-width: 1.5px; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label spotlightTitle = new Label();
        spotlightTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #b45309;");

        Label spotlightDetails = new Label();
        spotlightDetails.setStyle("-fx-font-size: 13px;");

        Label spotlightStats = new Label();
        spotlightStats.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label formulaBreakdown = new Label();
        formulaBreakdown.setStyle("-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");

        Label actionBadge = new Label();
        actionBadge.setStyle("-fx-background-color: #d97706; -fx-text-fill: white; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-font-weight: bold; -fx-font-size: 11px;");

        HBox actionRow = new HBox(8, actionBadge);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Button dispatchBtn = new Button("✓ Dispatch Top Card (poll())");
        dispatchBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6px 12px; -fx-background-radius: 5px;");

        Button batch3Btn = new Button("⏩ Batch Dispatch Top 3");
        batch3Btn.setStyle("-fx-padding: 6px 12px;");

        Button locateBtn = new Button("📍 Locate on Shelf");
        locateBtn.setStyle("-fx-padding: 6px 12px;");

        Label dispatchStatus = new Label();
        dispatchStatus.setStyle("-fx-font-weight: bold;");

        HBox buttonBar = new HBox(10, dispatchBtn, batch3Btn, locateBtn, dispatchStatus);
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        spotlightBox.getChildren().addAll(spotlightTitle, spotlightDetails, spotlightStats, formulaBreakdown, actionRow, buttonBar);

        // ==========================================
        // 2. LIVE FORMULA REWEIGHTING PANEL (§12)
        // ==========================================
        VBox formulaTunerBox = new VBox(8);
        formulaTunerBox.setPadding(new Insets(10, 12, 10, 12));
        formulaTunerBox.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label tunerTitle = new Label("Priority Formula Weights (Live Heap Re-organization for Defense)");
        tunerTitle.setStyle("-fx-font-weight: bold;");

        // Price Slider
        Label priceValLabel = new Label("Price (x0.50):");
        Slider priceSlider = new Slider(0.0, 1.5, weights[0]);
        priceSlider.setPrefWidth(120);

        // Demand Slider
        Label demandValLabel = new Label("Demand (x0.35):");
        Slider demandSlider = new Slider(0.0, 1.5, weights[1]);
        demandSlider.setPrefWidth(120);

        // Quantity Penalty Slider
        Label qtyValLabel = new Label("Qty Penalty (-3.0):");
        Slider qtySlider = new Slider(0.0, 10.0, weights[2]);
        qtySlider.setPrefWidth(120);

        // Foil Bonus Slider
        Label foilValLabel = new Label("Foil Bonus (+10.0):");
        Slider foilSlider = new Slider(0.0, 50.0, weights[3]);
        foilSlider.setPrefWidth(120);

        Button resetWeightsBtn = new Button("Reset Defaults (0.50 / 0.35 / 3.0 / 10.0)");
        resetWeightsBtn.setStyle("-fx-font-size: 11px;");

        Label reweightFeedback = new Label();
        reweightFeedback.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #059669;");

        HBox slidersRow = new HBox(14,
                new VBox(2, priceValLabel, priceSlider),
                new VBox(2, demandValLabel, demandSlider),
                new VBox(2, qtyValLabel, qtySlider),
                new VBox(2, foilValLabel, foilSlider),
                new VBox(4, resetWeightsBtn, reweightFeedback)
        );
        slidersRow.setAlignment(Pos.CENTER_LEFT);

        formulaTunerBox.getChildren().addAll(tunerTitle, slidersRow);

        // ==========================================
        // 3. LINE-JUMPING SIMULATION (Step 4 of Demo)
        // ==========================================
        HBox lineJumpBox = new HBox(8);
        lineJumpBox.setAlignment(Pos.CENTER_LEFT);
        lineJumpBox.setPadding(new Insets(8, 12, 8, 12));
        lineJumpBox.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-radius: 6px;");

        Label lineJumpLabel = new Label("Simulate Hot Arrival (Line-Jumping Test):");
        lineJumpLabel.setStyle("-fx-font-weight: bold;");

        ComboBox<String> hotCardCombo = new ComboBox<>();
        hotCardCombo.getItems().addAll(
                "Black Lotus [CUSTOM-003] (Price: 50,000 | Demand: 100 | FOIL)",
                "The One Ring [MTG-LTR-001] (Price: 18,500 | Demand: 99 | FOIL)",
                "Mox Diamond [MTG-ST-001] (Price: 32,000 | Demand: 95)",
                "Ragavan, Nimble Pilferer [MTG-MH2-138] (Price: 3,500 | Demand: 96 | FOIL)"
        );
        hotCardCombo.getSelectionModel().select(0);

        Button lineJumpBtn = new Button("🔥 Push Hot Card (offer())");
        lineJumpBtn.setStyle("-fx-background-color: #ea580c; -fx-text-fill: white; -fx-font-weight: bold;");

        Label lineJumpFeedback = new Label();
        lineJumpFeedback.setStyle("-fx-font-weight: bold; -fx-text-fill: #ea580c;");

        lineJumpBox.getChildren().addAll(lineJumpLabel, hotCardCombo, lineJumpBtn, lineJumpFeedback);

        // ==========================================
        // 4. TABPANE: QUEUE, HEAP INSPECTOR, LOG
        // ==========================================
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab A: Ranked Priority Queue Table
        TableView<PriorityRow> queueTable = new TableView<>();
        queueTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<PriorityRow, Integer> colRank = new TableColumn<>("Rank");
        colRank.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().rank()).asObject());
        colRank.setMaxWidth(60); colRank.setMinWidth(50);

        TableColumn<PriorityRow, String> colSku = new TableColumn<>("SKU");
        colSku.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().sku()));
        colSku.setMaxWidth(120); colSku.setMinWidth(90);

        TableColumn<PriorityRow, String> colName = new TableColumn<>("Card Name");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));

        TableColumn<PriorityRow, String> colPrice = new TableColumn<>("Price (PHP)");
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().price())));
        colPrice.getStyleClass().add("col-num");

        TableColumn<PriorityRow, Integer> colQty = new TableColumn<>("Qty");
        colQty.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().quantity()).asObject());
        colQty.getStyleClass().add("col-num");
        colQty.setMaxWidth(55);

        TableColumn<PriorityRow, Integer> colDemand = new TableColumn<>("Demand");
        colDemand.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().demand()).asObject());
        colDemand.getStyleClass().add("col-num");
        colDemand.setMaxWidth(70);

        TableColumn<PriorityRow, String> colFoil = new TableColumn<>("Foil");
        colFoil.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().foil() ? "YES" : "NO"));
        colFoil.setMaxWidth(60);

        TableColumn<PriorityRow, String> colScore = new TableColumn<>("Score");
        colScore.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().score())));
        colScore.getStyleClass().add("col-num");
        colScore.setStyle("-fx-font-weight: bold;");

        TableColumn<PriorityRow, String> colAction = new TableColumn<>("Recommended Action");
        colAction.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().action()));

        queueTable.getColumns().addAll(colRank, colSku, colName, colPrice, colQty, colDemand, colFoil, colScore, colAction);

        Tab queueTab = new Tab("Live Priority Queue", queueTable);

        // Tab B: Max-Heap Array & Tree Structure Inspector (Defense DSA Feature)
        TableView<HeapArrayNode> heapArrayTable = new TableView<>();
        heapArrayTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<HeapArrayNode, Integer> colIdx = new TableColumn<>("Array Index [i]");
        colIdx.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().index()).asObject());
        colIdx.setMaxWidth(110);

        TableColumn<HeapArrayNode, String> colNodeName = new TableColumn<>("Stored Card");
        colNodeName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));

        TableColumn<HeapArrayNode, String> colNodeScore = new TableColumn<>("Priority Score");
        colNodeScore.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().score())));
        colNodeScore.getStyleClass().add("col-num");

        TableColumn<HeapArrayNode, String> colParent = new TableColumn<>("Parent [(i-1)/2]");
        colParent.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().parentText()));

        TableColumn<HeapArrayNode, String> colLeft = new TableColumn<>("Left Child [2i+1]");
        colLeft.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().leftText()));

        TableColumn<HeapArrayNode, String> colRight = new TableColumn<>("Right Child [2i+2]");
        colRight.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().rightText()));

        TableColumn<HeapArrayNode, String> colValid = new TableColumn<>("Heap Order Invariant");
        colValid.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().validText()));

        heapArrayTable.getColumns().addAll(colIdx, colNodeName, colNodeScore, colParent, colLeft, colRight, colValid);

        VBox heapInspectorBox = new VBox(6,
                new Label("Complete Binary Tree in ArrayList: A[0] is Root. For index i: Left = 2i+1, Right = 2i+2, Parent = (i-1)/2."),
                heapArrayTable
        );
        VBox.setVgrow(heapArrayTable, Priority.ALWAYS);
        Tab heapTab = new Tab("Max-Heap Array Inspector", heapInspectorBox);

        // Tab C: Dispatched History Log
        TableView<DispatchedRecord> dispatchedTable = new TableView<>(dispatchedHistory);
        dispatchedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<DispatchedRecord, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().timestamp()));
        colTime.setMaxWidth(100);

        TableColumn<DispatchedRecord, String> colDName = new TableColumn<>("Card Name");
        colDName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));

        TableColumn<DispatchedRecord, String> colDSku = new TableColumn<>("SKU");
        colDSku.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().sku()));
        colDSku.setMaxWidth(120);

        TableColumn<DispatchedRecord, String> colDScore = new TableColumn<>("Dispatched Score");
        colDScore.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().score())));
        colDScore.getStyleClass().add("col-num");

        TableColumn<DispatchedRecord, String> colDAction = new TableColumn<>("Action Taken");
        colDAction.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().action()));

        dispatchedTable.getColumns().addAll(colTime, colDSku, colDName, colDScore, colDAction);
        Tab historyTab = new Tab("Dispatched History (" + dispatchedHistory.size() + ")", dispatchedTable);

        tabPane.getTabs().addAll(queueTab, heapTab, historyTab);

        // Master UI refresh runnable
        Card[] currentTop = new Card[1];
        Runnable refreshUI = () -> {
            CustomHeap h = heapHolder[0];
            queueSizeBadge.setText("Queue Size: " + h.size() + " cards");
            boolean valid = h.verifyHeapProperty();
            invariantBadge.setText("Heap Invariant: " + (valid ? "✓ Verified (Parent >= Children)" : "⚠️ Invariant Broken"));
            invariantBadge.setStyle("-fx-background-color: " + (valid ? "#dcfce7" : "#fee2e2") + "; -fx-text-fill: " + (valid ? "#15803d" : "#b91c1c") + "; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-font-weight: bold;");

            Card top = h.peek();
            currentTop[0] = top;
            if (top == null) {
                rootScoreBadge.setText("Root Score: 0.00");
                spotlightTitle.setText("Queue Empty");
                spotlightDetails.setText("All high-priority cards have been dispatched.");
                spotlightStats.setText("");
                formulaBreakdown.setText("");
                actionBadge.setText("No pending action");
                dispatchBtn.setDisable(true);
                batch3Btn.setDisable(true);
                locateBtn.setDisable(true);
            } else {
                double topScore = top.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]);
                rootScoreBadge.setText(String.format("Root Score: %.2f", topScore));
                spotlightTitle.setText("🌟 #1 DISPATCH SPOTLIGHT: " + top.getName() + " [" + top.getSku() + "]");
                spotlightDetails.setText(top.getSetName() + " · " + top.getColor() + " " + top.getCardType() + " (" + top.getReleaseYear() + ") · Shelf: " + StorageLocation.route(top));
                spotlightStats.setText(String.format("Price: PHP %.2f | Stock: %d | Demand: %d/100 | %s",
                        top.getPrice(), top.getQuantity(), top.getDemandScore(), top.isFoil() ? "FOIL" : "Normal"));

                formulaBreakdown.setText(String.format("Formula: (%.2f x %.2f) + (%d x %.2f) - (%d x %.2f) + %.2f = %.2f",
                        top.getPrice(), weights[0],
                        top.getDemandScore(), weights[1],
                        top.getQuantity(), weights[2],
                        top.isFoil() ? weights[3] : 0.0,
                        topScore));

                String act = recommendAction(top);
                actionBadge.setText("RECOMMENDED ACTION: " + act);
                dispatchBtn.setDisable(false);
                batch3Btn.setDisable(false);
                locateBtn.setDisable(false);
            }

            // Update queue table
            List<Card> sorted = h.sortedSnapshot();
            ObservableList<PriorityRow> tableRows = FXCollections.observableArrayList();
            for (int i = 0; i < sorted.size(); i++) {
                Card c = sorted.get(i);
                double s = c.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]);
                tableRows.add(new PriorityRow(i + 1, c.getSku(), c.getName(), c.getPrice(), c.getQuantity(), c.getDemandScore(), c.isFoil(), s, recommendAction(c), c));
            }
            queueTable.setItems(tableRows);

            // Update heap array inspector
            List<Card> raw = h.getRawList();
            ObservableList<HeapArrayNode> inspectorRows = FXCollections.observableArrayList();
            for (int i = 0; i < raw.size(); i++) {
                Card c = raw.get(i);
                double s = c.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]);
                int p = (i - 1) / 2;
                int l = 2 * i + 1;
                int r = 2 * i + 2;

                String pText = i == 0 ? "(Root)" : "Index " + p + " (" + String.format("%.1f", raw.get(p).calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3])) + ")";
                String lText = l < raw.size() ? "Index " + l + " (" + String.format("%.1f", raw.get(l).calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3])) + ")" : "(None)";
                String rText = r < raw.size() ? "Index " + r + " (" + String.format("%.1f", raw.get(r).calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3])) + ")" : "(None)";

                boolean nodeValid = true;
                if (l < raw.size() && raw.get(l).calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]) > s) nodeValid = false;
                if (r < raw.size() && raw.get(r).calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]) > s) nodeValid = false;

                inspectorRows.add(new HeapArrayNode(i, c.getName(), s, pText, lText, rText, nodeValid ? "✓ Valid" : "❌ Broken"));
            }
            heapArrayTable.setItems(inspectorRows);

            historyTab.setText("Dispatched History (" + dispatchedHistory.size() + ")");
        };

        // Dispatch handlers
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        dispatchBtn.setOnAction(e -> {
            Card c = heapHolder[0].poll();
            if (c != null) {
                activeCards.remove(c);
                double s = c.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]);
                dispatchedHistory.add(0, new DispatchedRecord(sdf.format(new Date()), c.getSku(), c.getName(), s, recommendAction(c)));
                dispatchStatus.setText("✓ Dispatched " + c.getName() + " to counter.");
                dispatchStatus.setStyle("-fx-text-fill: #059669;");
                refreshUI.run();
            }
        });

        batch3Btn.setOnAction(e -> {
            int dispatchedCount = 0;
            for (int i = 0; i < 3; i++) {
                Card c = heapHolder[0].poll();
                if (c == null) break;
                activeCards.remove(c);
                double s = c.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]);
                dispatchedHistory.add(0, new DispatchedRecord(sdf.format(new Date()), c.getSku(), c.getName(), s, recommendAction(c)));
                dispatchedCount++;
            }
            dispatchStatus.setText("✓ Batch-dispatched " + dispatchedCount + " card(s).");
            dispatchStatus.setStyle("-fx-text-fill: #059669;");
            refreshUI.run();
        });

        locateBtn.setOnAction(e -> {
            if (currentTop[0] != null && CatalogView.locateHandler != null) {
                CatalogView.lastLocatedCard = currentTop[0];
                CatalogView.locateHandler.accept(StorageLocation.route(currentTop[0]));
            }
        });

        // Live weight adjustment handler
        Runnable recomputeWeights = () -> {
            weights[0] = priceSlider.getValue();
            weights[1] = demandSlider.getValue();
            weights[2] = qtySlider.getValue();
            weights[3] = foilSlider.getValue();

            priceValLabel.setText(String.format("Price (x%.2f):", weights[0]));
            demandValLabel.setText(String.format("Demand (x%.2f):", weights[1]));
            qtyValLabel.setText(String.format("Qty Penalty (-%.1f):", weights[2]));
            foilValLabel.setText(String.format("Foil Bonus (+%.1f):", weights[3]));

            long start = System.nanoTime();
            CustomHeap fresh = new CustomHeap(c -> c.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]));
            for (Card c : activeCards) fresh.offer(c);
            heapHolder[0] = fresh;
            long elapsedMicros = (System.nanoTime() - start) / 1000;

            reweightFeedback.setText(String.format("✓ Re-heapified %d items in %d µs (O(n log n))", fresh.size(), elapsedMicros));
            refreshUI.run();
        };

        priceSlider.valueProperty().addListener((obs, o, n) -> recomputeWeights.run());
        demandSlider.valueProperty().addListener((obs, o, n) -> recomputeWeights.run());
        qtySlider.valueProperty().addListener((obs, o, n) -> recomputeWeights.run());
        foilSlider.valueProperty().addListener((obs, o, n) -> recomputeWeights.run());

        resetWeightsBtn.setOnAction(e -> {
            priceSlider.setValue(0.50);
            demandSlider.setValue(0.35);
            qtySlider.setValue(3.00);
            foilSlider.setValue(10.00);
            recomputeWeights.run();
        });

        // Line-jumping hot card test handler
        lineJumpBtn.setOnAction(e -> {
            int selectedIdx = hotCardCombo.getSelectionModel().getSelectedIndex();
            Card hotCard;
            switch (selectedIdx) {
                case 1 -> hotCard = new Card("MTG-LTR-001", "The One Ring", "The Lord of the Rings", "Colorless", "Artifact", 2023, 18500.0, 1, 99, true, "Showcase Display");
                case 2 -> hotCard = new Card("MTG-ST-001", "Mox Diamond", "Stronghold", "Colorless", "Artifact", 1998, 32000.0, 1, 95, false, "Showcase Display");
                case 3 -> hotCard = new Card("MTG-MH2-138", "Ragavan, Nimble Pilferer", "Modern Horizons 2", "Red", "Creature", 2021, 3500.0, 2, 96, true, "Showcase Display");
                default -> hotCard = new Card("CUSTOM-003", "Black Lotus", "Collector Demo", "Colorless", "Artifact", 1993, 50000.0, 1, 100, true, "Showcase Display");
            }

            activeCards.add(hotCard);
            heapHolder[0].offer(hotCard);
            refreshUI.run();

            if (heapHolder[0].peek() == hotCard) {
                lineJumpFeedback.setText("⚡ " + hotCard.getName() + " jumped the line directly to Root (Rank #1) in O(log n)!");
            } else {
                lineJumpFeedback.setText("Added " + hotCard.getName() + " into heap via siftUp.");
            }
        });

        // Selecting a row in queue table updates spotlight
        queueTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null && sel.card() != null) {
                currentTop[0] = sel.card();
                spotlightTitle.setText("INSPECTING (Rank #" + sel.rank() + "): " + sel.name() + " [" + sel.sku() + "]");
                spotlightDetails.setText(sel.card().getSetName() + " · " + sel.card().getColor() + " " + sel.card().getCardType() + " (" + sel.card().getReleaseYear() + ") · Shelf: " + StorageLocation.route(sel.card()));
                spotlightStats.setText(String.format("Price: PHP %.2f | Stock: %d | Demand: %d/100 | %s",
                        sel.price(), sel.quantity(), sel.demand(), sel.foil() ? "FOIL" : "Normal"));
                formulaBreakdown.setText(String.format("Formula: (%.2f x %.2f) + (%d x %.2f) - (%d x %.2f) + %.2f = %.2f",
                        sel.price(), weights[0], sel.demand(), weights[1], sel.quantity(), weights[2], sel.foil() ? weights[3] : 0.0, sel.score()));
                actionBadge.setText("RECOMMENDED ACTION: " + sel.action());
            }
        });

        // Initial render
        refreshUI.run();

        VBox root = new VBox(10, headerBox, spotlightBox, formulaTunerBox, lineJumpBox, tabPane);
        root.setPadding(new Insets(10));
        root.getStyleClass().add("view-container");
        return root;
    }

    public static void main(String[] args) {
        List<Card> cards = CatalogView.loadCards();
        List<Card> top3 = topN(cards, 3);
        System.out.println("top3 of " + cards.size() + ":");
        for (Card c : top3) System.out.println("  " + c.getName() + " " + c.calculatePriority());
        System.out.println("PriorityView data OK.");
    }
}
