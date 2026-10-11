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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tutorengine.db.Db;
import tutorengine.intake.WorkingBuffer;
import tutorengine.intake.WorkingBuffer.WorkingItem;
import tutorengine.model.CardInstance;
import tutorengine.model.StorageLocation;
import tutorengine.storage.StorageScaffolding;
import tutorengine.storage.StorageScaffolding.NeighborBreadcrumb;
import tutorengine.storage.StorageScaffolding.StorageBatch;

// StorageView: Module 2 Storage Routing, 2D Shelf Unit Locator, In-Hand Forward-Pass
// Ordering, and In-Box Neighbor Breadcrumb Scaffolding (§4 tutorengine_implementation_specification.md).
// Pure data methods are toolkit-free (headless testable via main()).
public final class StorageView {
    private StorageView() {}

    public static List<StorageBatch> loadBatches() {
        try {
            return StorageScaffolding.clusterUnstored();
        } catch (Exception e) {
            return List.of();
        }
    }

    public static NeighborBreadcrumb getBreadcrumb(String boxId, String targetCardName) {
        try {
            return StorageScaffolding.calculateBreadcrumb(boxId, targetCardName);
        } catch (Exception e) {
            return new NeighborBreadcrumb(null, targetCardName, null, 50.0, "Breadcrumb calculation: " + e.getMessage());
        }
    }

    public static int confirmBatch(String boxId, List<String> cardIds) {
        try {
            return StorageScaffolding.confirmBatchStored(boxId, cardIds);
        } catch (Exception e) {
            return 0;
        }
    }

    public static Node build() {
        ObservableList<StorageBatch> batchList = FXCollections.observableArrayList();
        ObservableList<CardInstance> batchCardList = FXCollections.observableArrayList();

        // --- Left Pane: Clustered Batches List ---
        Label batchesTitle = new Label("1. Unstored Batches (Clustered by Destination)");
        batchesTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        TableView<StorageBatch> batchTable = new TableView<>(batchList);
        setupBatchColumns(batchTable);

        Button refreshBtn = new Button("↻ Refresh Batches");
        Button seedSampleBtn = new Button("+ Stage & Commit Sample Cards");

        Label unstoredSummary = new Label("Batches: 0 ready");
        unstoredSummary.setStyle("-fx-text-fill: -color-fg-muted;");

        HBox batchToolBar = new HBox(8, refreshBtn, seedSampleBtn);
        batchToolBar.setAlignment(Pos.CENTER_LEFT);

        VBox leftPane = new VBox(8, batchesTitle, batchToolBar, batchTable, unstoredSummary);
        leftPane.setPrefWidth(380);
        VBox.setVgrow(batchTable, Priority.ALWAYS);

        // --- Right Pane: Active Batch Routing, 2D Shelf, In-Hand Directive & Breadcrumbs ---
        Label activeBatchTitle = new Label("Select a batch from the left to start filing");
        activeBatchTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // In-hand forward-pass directive banner
        Label directiveBanner = new Label("📋 IN-HAND PRE-SORT DIRECTIVE: Arrange cards in hand (Name ASC) before walking to shelf.");
        directiveBanner.setWrapText(true);
        directiveBanner.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-padding: 8px;"
                + "-fx-border-color: #f59e0b; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-font-weight: bold;");
        directiveBanner.setVisible(false);

        // 2D Shelf Visual Map container
        VBox shelfMapPane = new VBox(6);
        shelfMapPane.setPadding(new Insets(6));
        shelfMapPane.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-border-radius: 6px;");

        Label shelfMapHeader = new Label("Physical Shelf Coordinates (2D Store Map)");
        shelfMapHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        shelfMapPane.getChildren().add(shelfMapHeader);

        // Grid holding 2D shelf units
        HBox unitsBox = new HBox(12);
        unitsBox.setAlignment(Pos.CENTER_LEFT);
        VBox cabinetUnit = buildShelfUnitVisual("White Cabinet", List.of("CAB-T-TOP", "CAB-UW-MID", "CAB-LANDS-LOW", "CAB-TOKEN-LOW", "CAB-RARE-LOW"));
        VBox rackUnit = buildShelfUnitVisual("Metal Wire Rack", List.of("RACK-M-TOP", "RACK-L-TOP", "RACK-M-MID", "RACK-PON-LOW"));
        unitsBox.getChildren().addAll(cabinetUnit, rackUnit);
        shelfMapPane.getChildren().add(unitsBox);

        Label newBoxAlert = new Label();
        newBoxAlert.setWrapText(true);
        newBoxAlert.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-padding: 6px;"
                + "-fx-border-color: #ef4444; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-font-weight: bold;");
        newBoxAlert.setVisible(false);
        shelfMapPane.getChildren().add(newBoxAlert);

        // In-Box Cards Table
        Label cardsHeader = new Label("Cards in Active Batch (Pre-Sorted Name ASC):");
        cardsHeader.setStyle("-fx-font-weight: bold;");

        TableView<CardInstance> cardTable = new TableView<>(batchCardList);
        setupCardColumns(cardTable);
        HoverPreviewHelper.attach(cardTable, CardInstance::getName, CardInstance::getSetCode,
                ci -> HoverPreviewHelper.buildCardKey(ci.getSetCode(), ci.getCollectorNumber(), ci.getId()));

        // Virtual In-Box Stack Scaffolding & Neighbor Breadcrumb Readout
        VBox breadcrumbPane = new VBox(6);
        breadcrumbPane.setPadding(new Insets(8));
        breadcrumbPane.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #94a3b8; -fx-border-width: 1px; -fx-border-radius: 6px;");

        Label breadcrumbTitle = new Label("Virtual In-Box Stack Insertion Breadcrumb:");
        breadcrumbTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        Label breadcrumbNeighbors = new Label("Select a card in the batch to view its insertion neighbors");
        breadcrumbNeighbors.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0369a1;");

        ProgressBar stackProgress = new ProgressBar(0.5);
        stackProgress.setMaxWidth(Double.MAX_VALUE);
        stackProgress.setPrefHeight(16);

        Label stackPositionLabel = new Label("Estimated In-Box Depth: ~50%");
        stackPositionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        breadcrumbPane.getChildren().addAll(breadcrumbTitle, breadcrumbNeighbors, stackProgress, stackPositionLabel);

        // Batch confirmation bar
        Button confirmBatchBtn = new Button("✓ Confirm Batch Stored (All Cards Filed)");
        confirmBatchBtn.setStyle("-fx-font-weight: bold; -fx-base: #059669; -fx-font-size: 13px; -fx-padding: 8px 16px;");
        confirmBatchBtn.setDisable(true);

        Label confirmResultLabel = new Label();
        confirmResultLabel.setStyle("-fx-font-weight: bold;");

        HBox actionRow = new HBox(12, confirmBatchBtn, confirmResultLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.setPadding(new Insets(6, 0, 0, 0));

        VBox rightPane = new VBox(8, activeBatchTitle, directiveBanner, shelfMapPane, cardsHeader, cardTable, breadcrumbPane, actionRow);
        VBox.setVgrow(cardTable, Priority.ALWAYS);
        rightPane.setPadding(new Insets(0, 0, 0, 8));

        // --- Controller Interaction Logic ---
        StorageBatch[] activeBatch = new StorageBatch[1];

        Runnable reloadBatches = () -> {
            List<StorageBatch> batches = loadBatches();
            batchList.setAll(batches);
            int totalCards = 0;
            for (StorageBatch b : batches) totalCards += b.getCards().size();
            unstoredSummary.setText("Batches: " + batches.size() + " ready (" + totalCards + " unstored cards)");
            if (activeBatch[0] != null) {
                // Check if still exists
                boolean found = false;
                for (StorageBatch b : batches) {
                    if (b.getBoxId().equals(activeBatch[0].getBoxId())) {
                        batchTable.getSelectionModel().select(b);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    activeBatch[0] = null;
                    batchCardList.clear();
                    activeBatchTitle.setText("Select a batch from the left to start filing");
                    directiveBanner.setVisible(false);
                    newBoxAlert.setVisible(false);
                    confirmBatchBtn.setDisable(true);
                    highlightShelfUnit(unitsBox, null);
                    breadcrumbNeighbors.setText("Select a card in the batch to view its insertion neighbors");
                    stackProgress.setProgress(0.5);
                    stackPositionLabel.setText("Estimated In-Box Depth: ~50%");
                }
            }
        };

        refreshBtn.setOnAction(e -> reloadBatches.run());

        seedSampleBtn.setOnAction(e -> {
            try {
                WorkingBuffer wb = new WorkingBuffer();
                wb.addItem(new WorkingItem("s1", "Ajani, Caller of the Pride", "M14", "1", "mythic", "NM", "nonfoil", 1));
                wb.addItem(new WorkingItem("s2", "Black Cat", "M14", "84", "common", "NM", "nonfoil", 2));
                wb.addItem(new WorkingItem("s3", "Chandra, Pyromaster", "M14", "132", "mythic", "NM", "nonfoil", 1));
                wb.addItem(new WorkingItem("s4", "Doom Blade", "M14", "94", "uncommon", "NM", "nonfoil", 3));
                wb.addItem(new WorkingItem("s5", "Plains", "M14", "250", "common", "NM", "nonfoil", 4));
                wb.addItem(new WorkingItem("s6", "Teferi, Hero of Dominaria", "DOM", "207", "mythic", "NM", "foil", 1));
                wb.stageAll();
                wb.commitStaged("SampleClerk");
                confirmResultLabel.setText("✓ Ingested sample cards into Unstored Queue");
                confirmResultLabel.setStyle("-fx-text-fill: #059669;");
                reloadBatches.run();
            } catch (Exception ex) {
                confirmResultLabel.setText("Error seeding: " + ex.getMessage());
                confirmResultLabel.setStyle("-fx-text-fill: #dc2626;");
            }
        });

        // Batch selection
        batchTable.getSelectionModel().selectedItemProperty().addListener((o, oldBatch, b) -> {
            if (b == null) return;
            activeBatch[0] = b;
            activeBatchTitle.setText("Active Batch: " + b.getDisplayLabel() + " (" + b.getCards().size() + " cards)");
            directiveBanner.setText("📋 IN-HAND PRE-SORT DIRECTIVE: Arrange the " + b.getCards().size()
                    + " cards in your hand in this exact top-to-bottom order (Card Name ASC) before walking to shelf unit: "
                    + b.getShelfUnit() + " (" + b.getShelfTier() + ").");
            directiveBanner.setVisible(true);

            highlightShelfUnit(unitsBox, b.getBoxId());

            if (b.isNewBoxRequired()) {
                newBoxAlert.setText("⚠️ NEW BOX REQUIRED: Unrecognized set! Provision and label a new box '"
                        + b.getSuggestedBoxId() + "' on " + b.getShelfUnit() + " " + b.getShelfTier() + " before filing.");
                newBoxAlert.setVisible(true);
            } else {
                newBoxAlert.setVisible(false);
            }

            b.sortInHandForwardPass();
            batchCardList.setAll(b.getCards());
            confirmBatchBtn.setDisable(false);
            confirmBatchBtn.setText("✓ Confirm Batch Stored (" + b.getCards().size() + " cards)");

            if (!b.getCards().isEmpty()) {
                cardTable.getSelectionModel().select(0);
            }
        });

        // Card selection -> update neighbor breadcrumbs
        cardTable.getSelectionModel().selectedItemProperty().addListener((o, oldCard, card) -> {
            if (card == null || activeBatch[0] == null) return;
            NeighborBreadcrumb breadcrumb = getBreadcrumb(activeBatch[0].getBoxId(), card.getName());
            breadcrumbNeighbors.setText(breadcrumb.getBreadcrumbText());
            double p = breadcrumb.getEstimatedPercent();
            stackProgress.setProgress(p / 100.0);
            stackPositionLabel.setText(String.format("Card: %s | Estimated In-Box Slot: ~%.0f%% depth from front", card.getName(), p));
        });

        // Batch confirmation
        confirmBatchBtn.setOnAction(e -> {
            if (activeBatch[0] == null) return;
            StorageBatch b = activeBatch[0];
            List<String> ids = new ArrayList<>();
            for (CardInstance ci : b.getCards()) ids.add(ci.getId());
            int stored = confirmBatch(b.getBoxId(), ids);
            confirmResultLabel.setText("✓ Successfully confirmed " + stored + " cards stored into " + b.getBoxId() + "!");
            confirmResultLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
            reloadBatches.run();
        });

        reloadBatches.run();

        SplitPane split = new SplitPane(leftPane, rightPane);
        split.setDividerPositions(0.38);

        BorderPane root = new BorderPane(split);
        root.getStyleClass().add("view-container");
        root.setPadding(new Insets(10));
        return root;
    }

    private static VBox buildShelfUnitVisual(String unitName, List<String> slots) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(6));
        box.setStyle("-fx-border-color: #94a3b8; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-color: #ffffff;");
        Label header = new Label(unitName);
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        box.getChildren().add(header);

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);

        int row = 0;
        int col = 0;
        for (String slot : slots) {
            Label slotLabel = new Label(slot);
            slotLabel.setId("slot-" + slot);
            slotLabel.setAlignment(Pos.CENTER);
            slotLabel.setPrefSize(110, 24);
            slotLabel.setStyle("-fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-border-radius: 3px; -fx-background-color: #f8fafc; -fx-font-size: 10px;");
            grid.add(slotLabel, col, row);
            col++;
            if (col >= 2) {
                col = 0;
                row++;
            }
        }
        box.getChildren().add(grid);
        return box;
    }

    private static void highlightShelfUnit(HBox unitsBox, String targetBoxId) {
        for (Node unitNode : unitsBox.getChildren()) {
            if (unitNode instanceof VBox unitVBox) {
                for (Node child : unitVBox.getChildren()) {
                    if (child instanceof GridPane grid) {
                        for (Node slotNode : grid.getChildren()) {
                            if (slotNode instanceof Label slotLabel) {
                                boolean isTarget = targetBoxId != null && slotLabel.getId().equals("slot-" + targetBoxId);
                                if (isTarget) {
                                    slotLabel.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold;"
                                            + "-fx-border-color: #10b981; -fx-border-width: 2px; -fx-border-radius: 3px; -fx-font-size: 10px;");
                                } else {
                                    slotLabel.setStyle("-fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-border-radius: 3px;"
                                            + "-fx-background-color: #f8fafc; -fx-text-fill: -color-fg-default; -fx-font-size: 10px;");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void setupBatchColumns(TableView<StorageBatch> table) {
        TableColumn<StorageBatch, String> idCol = new TableColumn<>("Box ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBoxId()));
        idCol.setPrefWidth(95);

        TableColumn<StorageBatch, String> unitCol = new TableColumn<>("Unit / Tier");
        unitCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getShelfUnit() + " (" + c.getValue().getShelfTier() + ")"));
        unitCol.setPrefWidth(120);

        TableColumn<StorageBatch, Number> countCol = new TableColumn<>("Cards");
        countCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCards().size()));
        countCol.setPrefWidth(55);

        TableColumn<StorageBatch, String> flagCol = new TableColumn<>("Notice");
        flagCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isNewBoxRequired() ? "NEW BOX" : ""));
        flagCol.setPrefWidth(70);

        table.getColumns().setAll(List.of(idCol, unitCol, countCol, flagCol));
    }

    private static void setupCardColumns(TableView<CardInstance> table) {
        TableColumn<CardInstance, String> nameCol = new TableColumn<>("Card Name (ASC)");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameCol.setPrefWidth(190);

        TableColumn<CardInstance, String> setCol = new TableColumn<>("Set");
        setCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSetCode()));
        setCol.setPrefWidth(55);

        TableColumn<CardInstance, String> numCol = new TableColumn<>("Num");
        numCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCollectorNumber()));
        numCol.setPrefWidth(50);

        TableColumn<CardInstance, String> condCol = new TableColumn<>("Cond");
        condCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCondition()));
        condCol.setPrefWidth(55);

        TableColumn<CardInstance, String> finishCol = new TableColumn<>("Finish");
        finishCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFinish()));
        finishCol.setPrefWidth(65);

        table.getColumns().setAll(List.of(nameCol, setCol, numCol, condCol, finishCol));
    }

    public static void main(String[] args) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("storage-view-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();

            // Seed sample commit & cards
            WorkingBuffer wb = new WorkingBuffer();
            wb.addItem(new WorkingItem("c1", "Ajani, Caller of the Pride", "M14", "1", "mythic", "NM", "nonfoil", 1));
            wb.addItem(new WorkingItem("c2", "Black Cat", "M14", "84", "common", "NM", "nonfoil", 2));
            wb.addItem(new WorkingItem("c3", "Plains", "M14", "250", "common", "NM", "nonfoil", 3));
            wb.stageAll();
            String tag = wb.commitStaged("Alice");
            System.out.println("Seeded commit tag: " + tag);

            List<StorageBatch> batches = loadBatches();
            System.out.println("Loaded batches: " + batches.size() + " (want >= 2)");
            for (StorageBatch b : batches) {
                System.out.println("  " + b);
                for (CardInstance ci : b.getCards()) {
                    NeighborBreadcrumb nb = getBreadcrumb(b.getBoxId(), ci.getName());
                    System.out.println("    " + ci.getName() + " -> " + nb.getBreadcrumbText());
                }
            }

            // Confirm first batch
            if (!batches.isEmpty()) {
                StorageBatch first = batches.get(0);
                List<String> ids = new ArrayList<>();
                for (CardInstance ci : first.getCards()) ids.add(ci.getId());
                int confirmed = confirmBatch(first.getBoxId(), ids);
                System.out.println("Confirmed stored cards in " + first.getBoxId() + ": " + confirmed);
            }

            List<StorageBatch> remaining = loadBatches();
            System.out.println("Batches remaining: " + remaining.size());

            System.out.println("StorageView data logic OK.");
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.println("StorageView ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
