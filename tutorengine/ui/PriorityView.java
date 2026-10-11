package tutorengine.ui;

import java.util.ArrayList;
import java.util.List;
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

// Priority view: Glass Display Wall — Showcase Priority Allocator.
// Uses student-built CustomHeap (Max-Heap) to automatically calculate which cards
// earn physical placement on the store's glass display wall based on Price,
// Demand, Stock Count, and Foil status (§7 Heap, §8 Priority).
public final class PriorityView {
    private PriorityView() {}

    // Model for the showcase allocation table
    public record ShowcaseRow(
            int rank,
            String slotStatus,
            String sku,
            String name,
            double price,
            int quantity,
            int demand,
            boolean foil,
            double score,
            String currentLocation,
            Card card
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

    public static Node build(List<Card> cards) {
        // State
        int[] capacityHolder = new int[]{12}; // Default 12 physical slots in glass wall
        double[] weights = new double[]{0.50, 0.35, 3.00, 10.00}; // price, demand, qty penalty, foil bonus

        // Header view
        Label titleLabel = new Label("Glass Display Wall — Showcase Priority Allocator");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label subtitleLabel = new Label("Student-Built CustomHeap (Max-Heap) — Automatically calculates which cards from active storage belong on the physical glass display wall.");
        subtitleLabel.setStyle("-fx-text-fill: -color-fg-muted;");

        Label slotsBadge = new Label("Glass Wall: 12 Display Slots");
        slotsBadge.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-font-weight: bold;");

        Label topScoreBadge = new Label();
        topScoreBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-font-weight: bold;");

        Label invariantBadge = new Label("Heap Invariant: ✓ Verified (Parent >= Children)");
        invariantBadge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-font-weight: bold;");

        HBox badgesRow = new HBox(8, slotsBadge, topScoreBadge, invariantBadge);
        badgesRow.setAlignment(Pos.CENTER_LEFT);

        VBox headerBox = new VBox(4, titleLabel, subtitleLabel, badgesRow);

        // ==========================================
        // 1. LIVE FORMULA REWEIGHTING & CAPACITY TOOLBAR
        // ==========================================
        VBox formulaTunerBox = new VBox(8);
        formulaTunerBox.setPadding(new Insets(10, 12, 10, 12));
        formulaTunerBox.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label tunerTitle = new Label("Priority Scoring Weights & Showcase Capacity (Live Max-Heap Re-allocation):");
        tunerTitle.setStyle("-fx-font-weight: bold;");

        // Price Slider
        Label priceValLabel = new Label("Price (x0.50):");
        Slider priceSlider = new Slider(0.0, 1.5, weights[0]);
        priceSlider.setPrefWidth(110);

        // Demand Slider
        Label demandValLabel = new Label("Demand (x0.35):");
        Slider demandSlider = new Slider(0.0, 1.5, weights[1]);
        demandSlider.setPrefWidth(110);

        // Quantity Penalty Slider
        Label qtyValLabel = new Label("Qty Penalty (-3.0):");
        Slider qtySlider = new Slider(0.0, 10.0, weights[2]);
        qtySlider.setPrefWidth(110);

        // Foil Bonus Slider
        Label foilValLabel = new Label("Foil Bonus (+10.0):");
        Slider foilSlider = new Slider(0.0, 50.0, weights[3]);
        foilSlider.setPrefWidth(110);

        // Capacity Choice
        Label capacityLabel = new Label("Display Slots:");
        ChoiceBox<Integer> capacityChoice = new ChoiceBox<>();
        capacityChoice.getItems().addAll(8, 12, 16, 20, 24);
        capacityChoice.setValue(12);

        Button resetWeightsBtn = new Button("Reset Defaults");
        resetWeightsBtn.setStyle("-fx-font-size: 11px;");

        Label reweightFeedback = new Label();
        reweightFeedback.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #059669;");

        HBox slidersRow = new HBox(12,
                new VBox(2, priceValLabel, priceSlider),
                new VBox(2, demandValLabel, demandSlider),
                new VBox(2, qtyValLabel, qtySlider),
                new VBox(2, foilValLabel, foilSlider),
                new VBox(2, capacityLabel, capacityChoice),
                new VBox(4, resetWeightsBtn, reweightFeedback)
        );
        slidersRow.setAlignment(Pos.CENTER_LEFT);

        formulaTunerBox.getChildren().addAll(tunerTitle, slidersRow);

        // ==========================================
        // 2. VISUAL GLASS DISPLAY WALL CONTAINER
        // ==========================================
        VBox glassWallSection = new VBox(6);
        Label glassWallTitle = new Label("Active Glass Display Wall (Automatically Allocated by Max-Heap):");
        glassWallTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        FlowPane glassWallGrid = new FlowPane();
        glassWallGrid.setHgap(10);
        glassWallGrid.setVgap(10);
        glassWallGrid.setPadding(new Insets(8));
        glassWallGrid.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: #0284c7; -fx-border-width: 1.5px; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        ScrollPane glassWallScroll = new ScrollPane(glassWallGrid);
        glassWallScroll.setFitToWidth(true);
        glassWallScroll.setPrefHeight(230);
        glassWallScroll.setMaxHeight(260);
        glassWallScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

        glassWallSection.getChildren().addAll(glassWallTitle, glassWallScroll);

        // ==========================================
        // 3. TABPANE: ALLOCATION TABLE & HEAP INSPECTOR
        // ==========================================
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab A: Full Ranked Showcase Allocation Table
        TableView<ShowcaseRow> showcaseTable = new TableView<>();
        showcaseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ShowcaseRow, Integer> colRank = new TableColumn<>("Rank");
        colRank.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().rank()).asObject());
        colRank.setMaxWidth(55); colRank.setMinWidth(45);

        TableColumn<ShowcaseRow, String> colStatus = new TableColumn<>("Glass Wall Status");
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().slotStatus()));
        colStatus.setMaxWidth(150); colStatus.setMinWidth(120);

        TableColumn<ShowcaseRow, String> colSku = new TableColumn<>("SKU");
        colSku.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().sku()));
        colSku.setMaxWidth(120); colSku.setMinWidth(90);

        TableColumn<ShowcaseRow, String> colName = new TableColumn<>("Card Name");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));

        TableColumn<ShowcaseRow, String> colPrice = new TableColumn<>("Price (PHP)");
        colPrice.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().price())));
        colPrice.getStyleClass().add("col-num");

        TableColumn<ShowcaseRow, Integer> colQty = new TableColumn<>("Stock");
        colQty.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().quantity()).asObject());
        colQty.getStyleClass().add("col-num");
        colQty.setMaxWidth(55);

        TableColumn<ShowcaseRow, Integer> colDemand = new TableColumn<>("Demand");
        colDemand.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().demand()).asObject());
        colDemand.getStyleClass().add("col-num");
        colDemand.setMaxWidth(65);

        TableColumn<ShowcaseRow, String> colFoil = new TableColumn<>("Foil");
        colFoil.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().foil() ? "YES" : "NO"));
        colFoil.setMaxWidth(55);

        TableColumn<ShowcaseRow, String> colScore = new TableColumn<>("Priority Score");
        colScore.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().score())));
        colScore.getStyleClass().add("col-num");
        colScore.setStyle("-fx-font-weight: bold;");

        TableColumn<ShowcaseRow, String> colLocation = new TableColumn<>("Current Physical Storage");
        colLocation.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().currentLocation()));

        showcaseTable.getColumns().addAll(colRank, colStatus, colSku, colName, colPrice, colQty, colDemand, colFoil, colScore, colLocation);
        Tab showcaseTab = new Tab("All Cards & Allocation Ranking", showcaseTable);

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
                new Label("Max-Heap Binary Tree in ArrayList: A[0] is Root. For index i: Left = 2i+1, Right = 2i+2, Parent = (i-1)/2."),
                heapArrayTable
        );
        VBox.setVgrow(heapArrayTable, Priority.ALWAYS);
        Tab heapTab = new Tab("Max-Heap Array Inspector (DSA Defense)", heapInspectorBox);

        tabPane.getTabs().addAll(showcaseTab, heapTab);

        // ==========================================
        // 4. MASTER RECALCULATION & RENDER FUNCTION
        // ==========================================
        Runnable refreshAll = () -> {
            // Source active cards (live synced with Catalog/CSV)
            List<Card> activeSource = CatalogView.activeCards != null && !CatalogView.activeCards.isEmpty()
                    ? CatalogView.activeCards
                    : (cards != null && !cards.isEmpty() ? cards : CatalogView.loadCards());

            long start = System.nanoTime();
            CustomHeap heap = new CustomHeap(c -> c.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]));
            for (Card c : activeSource) heap.offer(c);
            long elapsedMicros = (System.nanoTime() - start) / 1000;

            int capacity = capacityHolder[0];
            slotsBadge.setText("Glass Wall: " + capacity + " Display Slots");

            boolean valid = heap.verifyHeapProperty();
            invariantBadge.setText("Heap Invariant: " + (valid ? "✓ Verified (Parent >= Children)" : "⚠️ Invariant Broken"));
            invariantBadge.setStyle("-fx-background-color: " + (valid ? "#dcfce7" : "#fee2e2") + "; -fx-text-fill: " + (valid ? "#15803d" : "#b91c1c") + "; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-font-weight: bold;");

            Card rootCard = heap.peek();
            if (rootCard != null) {
                double rootScore = rootCard.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]);
                topScoreBadge.setText(String.format("Top Card: %s (PHP %.2f | Score: %.2f)", rootCard.getName(), rootCard.getPrice(), rootScore));
            } else {
                topScoreBadge.setText("Queue Empty");
            }

            reweightFeedback.setText(String.format("✓ Recalculated %d items in %d µs (O(n log n))", heap.size(), elapsedMicros));

            // Extract sorted priority order
            List<Card> sorted = heap.sortedSnapshot();

            // Populate Visual Glass Wall Grid
            glassWallGrid.getChildren().clear();
            int displayCount = Math.min(capacity, sorted.size());
            for (int i = 0; i < displayCount; i++) {
                Card c = sorted.get(i);
                double s = c.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]);
                int slotNum = i + 1;

                VBox slotCard = new VBox(4);
                slotCard.setPrefWidth(210);
                slotCard.setPadding(new Insets(8, 10, 8, 10));

                boolean isTop = (i == 0);
                String borderStyle = isTop
                        ? "-fx-border-color: #f59e0b; -fx-border-width: 2px; -fx-background-color: white;"
                        : "-fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-background-color: white;";
                slotCard.setStyle(borderStyle + " -fx-border-radius: 6px; -fx-background-radius: 6px;");

                Label slotBadge = new Label(isTop ? "★ SLOT #1 (CENTERPIECE)" : "SLOT #" + slotNum);
                slotBadge.setStyle(isTop
                        ? "-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 2px 5px; -fx-background-radius: 3px;"
                        : "-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 2px 5px; -fx-background-radius: 3px;");

                Label nameLbl = new Label(c.getName());
                nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                nameLbl.setWrapText(false);

                Label priceLbl = new Label(String.format("PHP %.2f", c.getPrice()) + (c.isFoil() ? " · FOIL" : ""));
                priceLbl.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");

                Label scoreLbl = new Label(String.format("Score: %.2f | Demand: %d/100", s, c.getDemandScore()));
                scoreLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

                String currentBox = StorageLocation.route(c);
                Label locationLbl = new Label("📍 Pull from: " + currentBox);
                locationLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0369a1;");

                slotCard.getChildren().addAll(slotBadge, nameLbl, priceLbl, scoreLbl, locationLbl);
                glassWallGrid.getChildren().add(slotCard);
            }

            // Populate Table
            ObservableList<ShowcaseRow> tableRows = FXCollections.observableArrayList();
            for (int i = 0; i < sorted.size(); i++) {
                Card c = sorted.get(i);
                double s = c.calculatePriorityWithWeights(weights[0], weights[1], weights[2], weights[3]);
                String status = (i < capacity) ? "★ On Glass Wall (Slot #" + (i + 1) + ")" : "In Storage (On Deck #" + (i - capacity + 1) + ")";
                tableRows.add(new ShowcaseRow(i + 1, status, c.getSku(), c.getName(), c.getPrice(), c.getQuantity(), c.getDemandScore(), c.isFoil(), s, StorageLocation.route(c), c));
            }
            showcaseTable.setItems(tableRows);

            // Populate Max-Heap Inspector
            List<Card> raw = heap.getRawList();
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
        };

        // Event Listeners for live recalculation
        Runnable recomputeWeights = () -> {
            weights[0] = priceSlider.getValue();
            weights[1] = demandSlider.getValue();
            weights[2] = qtySlider.getValue();
            weights[3] = foilSlider.getValue();

            priceValLabel.setText(String.format("Price (x%.2f):", weights[0]));
            demandValLabel.setText(String.format("Demand (x%.2f):", weights[1]));
            qtyValLabel.setText(String.format("Qty Penalty (-%.1f):", weights[2]));
            foilValLabel.setText(String.format("Foil Bonus (+%.1f):", weights[3]));

            refreshAll.run();
        };

        priceSlider.valueProperty().addListener((obs, o, n) -> recomputeWeights.run());
        demandSlider.valueProperty().addListener((obs, o, n) -> recomputeWeights.run());
        qtySlider.valueProperty().addListener((obs, o, n) -> recomputeWeights.run());
        foilSlider.valueProperty().addListener((obs, o, n) -> recomputeWeights.run());

        capacityChoice.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                capacityHolder[0] = n;
                refreshAll.run();
            }
        });

        resetWeightsBtn.setOnAction(e -> {
            priceSlider.setValue(0.50);
            demandSlider.setValue(0.35);
            qtySlider.setValue(3.00);
            foilSlider.setValue(10.00);
            capacityChoice.setValue(12);
            recomputeWeights.run();
        });

        // Initial render
        refreshAll.run();

        VBox root = new VBox(10, headerBox, formulaTunerBox, glassWallSection, tabPane);
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
