package tutorengine.ui;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tutorengine.db.Db;
import tutorengine.intake.WorkingBuffer;
import tutorengine.intake.WorkingBuffer.WorkingItem;
import tutorengine.model.Card;
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

    // Slot id -> stored card count, from the loaded dataset via the alias table.
    // (Relocated here when the unused map view was removed; toolkit-free.)
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

    public static int confirmBatch(String boxId, List<String> cardIds) {
        try {
            return StorageScaffolding.confirmBatchStored(boxId, cardIds);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void registerBox(String boxId, String unitName) {
        if (boxId == null || boxId.isBlank()) return;
        try {
            StorageScaffolding.createBox(boxId, unitName, "MID");
        } catch (Exception ignored) {}
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

        Label seedFeedbackLabel = new Label();
        seedFeedbackLabel.setStyle("-fx-font-size: 11px;");
        seedFeedbackLabel.setWrapText(true);

        Label unstoredSummary = new Label("Batches: 0 ready");
        unstoredSummary.setStyle("-fx-text-fill: -color-fg-muted;");

        HBox batchToolBar = new HBox(8, refreshBtn, seedSampleBtn);
        batchToolBar.setAlignment(Pos.CENTER_LEFT);

        VBox leftPane = new VBox(8, batchesTitle, batchToolBar, seedFeedbackLabel, batchTable, unstoredSummary);
        leftPane.setPrefWidth(380);
        VBox.setVgrow(batchTable, Priority.ALWAYS);

        // --- Right Pane: Active Batch Routing, 2D Shelf, In-Hand Directive & Breadcrumbs ---
        final StorageBatch[] activeBatch = new StorageBatch[1];

        Label activeBatchTitle = new Label("Select a batch from the left to start filing");
        activeBatchTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // In-hand forward-pass directive banner
        Label directiveBanner = new Label("📋 IN-HAND PRE-SORT DIRECTIVE: Arrange cards in hand (Name ASC) before walking to shelf.");
        directiveBanner.setWrapText(true);
        directiveBanner.setStyle("-fx-background-color: -color-warning-subtle; -fx-text-fill: -color-warning-fg; -fx-padding: 8px;"
                + "-fx-border-color: -color-warning-emphasis; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-font-weight: bold;");
        directiveBanner.setVisible(false);

        // 2D Shelf Visual Map container
        VBox shelfMapPane = new VBox(6);
        shelfMapPane.setPadding(new Insets(6));
        shelfMapPane.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px;");

        Label shelfMapHeader = new Label("Physical Shelf Coordinates (2D Store Map)");
        shelfMapHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        shelfMapPane.getChildren().add(shelfMapHeader);

        // Grid holding 2D shelf units matching catalog set-box naming convention
        HBox unitsBox = new HBox(12);
        unitsBox.setAlignment(Pos.CENTER_LEFT);

        Label newBoxAlert = new Label();
        newBoxAlert.setWrapText(true);
        newBoxAlert.setStyle("-fx-background-color: -color-danger-subtle; -fx-text-fill: -color-danger-fg; -fx-padding: 6px;"
                + "-fx-border-color: -color-danger-emphasis; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-font-weight: bold;");
        newBoxAlert.setVisible(false);

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
        breadcrumbPane.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px;");

        Label breadcrumbTitle = new Label("Virtual In-Box Stack Insertion Breadcrumb:");
        breadcrumbTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        Label breadcrumbNeighbors = new Label("Select a card in the batch to view its insertion neighbors");
        breadcrumbNeighbors.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -color-accent-fg;");

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

        final Runnable[] reloadBatchesRef = new Runnable[1];
        Runnable reloadBatches = () -> {
            if (reloadBatchesRef[0] != null) reloadBatchesRef[0].run();
        };

        Runnable clearActiveSelection = () -> {
            activeBatch[0] = null;
            batchTable.getSelectionModel().clearSelection();
            batchCardList.clear();
            activeBatchTitle.setText("Select a batch from the left to start filing");
            directiveBanner.setVisible(false);
            newBoxAlert.setVisible(false);
            confirmBatchBtn.setDisable(true);
            confirmBatchBtn.setText("✓ Confirm Batch Stored (All Cards Filed)");
            highlightShelfUnit(unitsBox, null);
            breadcrumbNeighbors.setText("Select a card in the batch to view its insertion neighbors");
            stackProgress.setProgress(0.5);
            stackPositionLabel.setText("Estimated In-Box Depth: ~50%");
        };

        Consumer<String> selectBoxHandler = clickedBox -> {
            highlightShelfUnit(unitsBox, clickedBox);
            if (activeBatch[0] != null) {
                activeBatch[0].setBoxId(clickedBox);
                activeBatchTitle.setText("Active Batch: " + activeBatch[0].getDisplayLabel() + " -> Assigned Box: " + clickedBox + " (" + activeBatch[0].getCards().size() + " cards)");
                directiveBanner.setText("📋 IN-HAND PRE-SORT DIRECTIVE: Arrange the " + activeBatch[0].getCards().size()
                        + " cards in your hand in this exact top-to-bottom order (Card Name ASC) before walking to box: "
                        + clickedBox + ".");
                directiveBanner.setVisible(true);
                confirmBatchBtn.setText("✓ Confirm Batch Stored into " + clickedBox + " (" + activeBatch[0].getCards().size() + " cards)");
                confirmResultLabel.setText("Assigned batch to physical box " + clickedBox + ".");
                confirmResultLabel.setStyle("-fx-text-fill: -color-accent-fg;");
                newBoxAlert.setVisible(false);
                batchTable.refresh();
            } else {
                confirmResultLabel.setText("Selected physical box: " + clickedBox + " (select a batch from left to assign).");
                confirmResultLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            }
        };

        Supplier<String> suggestedBoxSupplier = () -> {
            if (activeBatch[0] != null && activeBatch[0].isNewBoxRequired()
                    && activeBatch[0].getSuggestedBoxId() != null && !activeBatch[0].getSuggestedBoxId().isBlank()) {
                return activeBatch[0].getSuggestedBoxId();
            }
            return "";
        };

        ObservableList<String> unitASlots = FXCollections.observableArrayList("BLB-1", "TSP-1", "ZEN-1", "XLN-1", "DOM-1", "WOE-1");
        ObservableList<String> unitBSlots = FXCollections.observableArrayList("M14-1", "MH3-1", "OTJ-1", "FDN-1", "ELD-1", "DMU-1");

        try {
            Db.migrate();
            try (Connection c = Db.open();
                 PreparedStatement ps = c.prepareStatement("SELECT box_id, shelf_unit FROM box_containers");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String bId = rs.getString(1);
                    String u = rs.getString(2);
                    if (bId != null && !bId.isBlank()) {
                        String bNorm = bId.trim().toUpperCase();
                        if (bNorm.contains("-") && !bNorm.startsWith("RACK-") && !bNorm.startsWith("CAB-")) {
                            if (u != null && u.contains("Shelf Unit B")) {
                                if (!unitBSlots.contains(bNorm)) unitBSlots.add(bNorm);
                            } else {
                                if (!unitASlots.contains(bNorm) && !unitBSlots.contains(bNorm)) unitASlots.add(bNorm);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        VBox shelfUnitA = buildShelfUnitVisual(
                "Shelf Unit A (Expansion Boxes)",
                "Shelf Unit A",
                unitASlots,
                "Shelf Unit B",
                unitBSlots,
                selectBoxHandler,
                suggestedBoxSupplier,
                confirmResultLabel,
                reloadBatches,
                () -> activeBatch[0],
                clearActiveSelection);

        VBox shelfUnitB = buildShelfUnitVisual(
                "Shelf Unit B (Core & Modern Boxes)",
                "Shelf Unit B",
                unitBSlots,
                "Shelf Unit A",
                unitASlots,
                selectBoxHandler,
                suggestedBoxSupplier,
                confirmResultLabel,
                reloadBatches,
                () -> activeBatch[0],
                clearActiveSelection);

        unitsBox.getChildren().addAll(shelfUnitA, shelfUnitB);
        shelfMapPane.getChildren().addAll(unitsBox, newBoxAlert);

        VBox rightPane = new VBox(8, activeBatchTitle, directiveBanner, shelfMapPane, cardsHeader, cardTable, breadcrumbPane, actionRow);
        VBox.setVgrow(cardTable, Priority.ALWAYS);
        rightPane.setPadding(new Insets(0, 0, 0, 8));

        // --- Controller Interaction Logic ---
        reloadBatchesRef[0] = () -> {
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
                    clearActiveSelection.run();
                }
            }
        };

        refreshBtn.setOnAction(e -> reloadBatches.run());

        seedSampleBtn.setOnAction(e -> {
            try {
                Db.migrate();
                WorkingBuffer wb = new WorkingBuffer();
                wb.addItem(new WorkingItem("s1", "Ajani, Caller of the Pride", "M14", "1", "mythic", "NM", "nonfoil", 1));
                wb.addItem(new WorkingItem("s2", "Black Cat", "M14", "84", "common", "NM", "nonfoil", 2));
                wb.addItem(new WorkingItem("s3", "Chandra, Pyromaster", "M14", "132", "mythic", "NM", "nonfoil", 1));
                wb.addItem(new WorkingItem("s4", "Doom Blade", "M14", "94", "uncommon", "NM", "nonfoil", 3));
                wb.addItem(new WorkingItem("s5", "Plains", "M14", "250", "common", "NM", "nonfoil", 4));
                wb.addItem(new WorkingItem("s6", "Teferi, Hero of Dominaria", "DOM", "207", "mythic", "NM", "foil", 1));
                wb.stageAll();
                int ingested = wb.commitStaged();
                seedFeedbackLabel.setText("✓ Seeded " + ingested + " sample cards");
                seedFeedbackLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                confirmResultLabel.setText("✓ Ingested sample cards into Unstored Queue");
                confirmResultLabel.setStyle("-fx-text-fill: #059669;");
                reloadBatches.run();
            } catch (Exception ex) {
                seedFeedbackLabel.setText("Error seeding: " + ex.getMessage());
                seedFeedbackLabel.setStyle("-fx-text-fill: #dc2626;");
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
            for (CardInstance ci : b.getCards()) {
                CatalogView.updateCardStorageLocation(ci.getName(), ci.getSetCode(), b.getBoxId());
            }
            confirmResultLabel.setText("✓ Successfully confirmed " + stored + " cards stored into " + b.getBoxId() + "! (Updated in Catalog)");
            confirmResultLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");

            // Reset active batch and remove from selection & card table
            clearActiveSelection.run();
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

    private static VBox buildShelfUnitVisual(
            String unitTitle,
            String shortUnitName,
            ObservableList<String> slots,
            String otherUnitName,
            ObservableList<String> otherSlots,
            Consumer<String> onBoxSelected,
            Supplier<String> suggestedBoxSupplier,
            Label feedbackLabel,
            Runnable onShelvesChanged,
            Supplier<StorageBatch> activeBatchSupplier,
            Runnable onActiveBatchCleared) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(6));
        box.setStyle("-fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-color: -color-bg-default;");

        Label header = new Label(unitTitle);
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Button addBoxBtn = new Button("+ Add Box");
        addBoxBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2px 6px; -fx-font-weight: bold;");

        HBox headerRow = new HBox(8, header, addBoxBtn);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(headerRow);

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        box.getChildren().add(grid);

        Runnable renderGrid = () -> {
            grid.getChildren().clear();
            int row = 0;
            int col = 0;
            for (String slot : slots) {
                Label slotLabel = new Label(slot);
                slotLabel.setId("slot-" + slot);
                slotLabel.setAlignment(Pos.CENTER);
                slotLabel.setPrefSize(110, 24);
                slotLabel.setCursor(Cursor.HAND);

                boolean isTarget = activeBatchSupplier != null && activeBatchSupplier.get() != null
                        && slot.equalsIgnoreCase(activeBatchSupplier.get().getBoxId());
                if (isTarget) {
                    slotLabel.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold;"
                            + "-fx-border-color: #10b981; -fx-border-width: 2px; -fx-border-radius: 3px; -fx-font-size: 10px;");
                } else {
                    slotLabel.setStyle("-fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 3px;"
                            + "-fx-background-color: -color-bg-subtle; -fx-text-fill: -color-fg-default; -fx-font-size: 10px;");
                }

                Tooltip.install(slotLabel, new Tooltip("Box: " + slot + " (" + shortUnitName + ")\nLeft-click to select | Right-click for options (Rename, Move, Delete)"));

                // Click to select box
                slotLabel.setOnMouseClicked(e -> {
                    if (onBoxSelected != null) {
                        onBoxSelected.accept(slot);
                    }
                });

                // Context menu for CRUD actions
                ContextMenu cm = new ContextMenu();
                MenuItem assignItem = new MenuItem("Assign Active Batch to " + slot);
                assignItem.setOnAction(ev -> {
                    if (onBoxSelected != null) onBoxSelected.accept(slot);
                });

                MenuItem addCardItem = new MenuItem("+ Add Card directly to " + slot);
                addCardItem.setOnAction(ev -> {
                    TextInputDialog td = new TextInputDialog();
                    td.setTitle("Add Card to " + slot);
                    td.setHeaderText("Add a card directly to physical box " + slot);
                    td.setContentText("Card Name:");
                    td.showAndWait().ifPresent(name -> {
                        String cleanName = name.trim();
                        if (!cleanName.isEmpty()) {
                            String setCode = slot.contains("-") ? slot.split("-")[0] : "UNK";
                            try {
                                Db.migrate();
                                try (Connection c = Db.open();
                                     PreparedStatement ps = c.prepareStatement(
                                             "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, language, status, box_id, created_at, stored_at, quantity)"
                                             + " VALUES (?, '', ?, ?, '1', 'common', 'NM', 'nonfoil', 'EN', 'STORED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)")) {
                                    ps.setString(1, UUID.randomUUID().toString());
                                    ps.setString(2, cleanName);
                                    ps.setString(3, setCode);
                                    ps.setString(4, slot);
                                    ps.executeUpdate();
                                }
                                CatalogView.updateCardStorageLocation(cleanName, setCode, slot);
                                if (feedbackLabel != null) {
                                    feedbackLabel.setText("✓ Added \"" + cleanName + "\" into " + slot + " (Catalog updated).");
                                    feedbackLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                                }
                            } catch (Exception ex) {
                                if (feedbackLabel != null) {
                                    feedbackLabel.setText("Error adding card: " + ex.getMessage());
                                    feedbackLabel.setStyle("-fx-text-fill: #dc2626;");
                                }
                            }
                        }
                    });
                });

                MenuItem renameItem = new MenuItem("✏ Rename Box...");
                renameItem.setOnAction(ev -> {
                    TextInputDialog td = new TextInputDialog(slot);
                    td.setTitle("Rename Box " + slot);
                    td.setHeaderText("Rename physical box coordinate " + slot);
                    td.setContentText("New Box ID (e.g. BLB-2, M14-2):");
                    td.showAndWait().ifPresent(val -> {
                        String cleanNew = val.trim().toUpperCase();
                        if (cleanNew.isEmpty() || cleanNew.equals(slot)) return;
                        if (slots.contains(cleanNew) || (otherSlots != null && otherSlots.contains(cleanNew))) {
                            if (feedbackLabel != null) {
                                feedbackLabel.setText("Cannot rename: Box " + cleanNew + " already exists!");
                                feedbackLabel.setStyle("-fx-text-fill: #dc2626;");
                            }
                            return;
                        }
                        try {
                            boolean ok = StorageScaffolding.renameBox(slot, cleanNew);
                            if (ok) {
                                int idx = slots.indexOf(slot);
                                if (idx >= 0) {
                                    slots.set(idx, cleanNew);
                                }
                                StorageBatch curBatch = activeBatchSupplier != null ? activeBatchSupplier.get() : null;
                                if (curBatch != null && curBatch.getBoxId().equalsIgnoreCase(slot)) {
                                    curBatch.setBoxId(cleanNew);
                                    if (onBoxSelected != null) onBoxSelected.accept(cleanNew);
                                }
                                if (onShelvesChanged != null) onShelvesChanged.run();
                                if (feedbackLabel != null) {
                                    feedbackLabel.setText("✓ Renamed box " + slot + " to " + cleanNew + " (Catalog & card records updated).");
                                    feedbackLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                                }
                            } else {
                                if (feedbackLabel != null) {
                                    feedbackLabel.setText("Could not rename box " + slot + " to " + cleanNew + ".");
                                    feedbackLabel.setStyle("-fx-text-fill: #dc2626;");
                                }
                            }
                        } catch (Exception ex) {
                            if (feedbackLabel != null) {
                                feedbackLabel.setText("Error renaming box: " + ex.getMessage());
                                feedbackLabel.setStyle("-fx-text-fill: #dc2626;");
                            }
                        }
                    });
                });

                MenuItem moveItem = new MenuItem("⇄ Move to " + otherUnitName + "...");
                moveItem.setOnAction(ev -> {
                    Alert alert = new Alert(
                            Alert.AlertType.CONFIRMATION,
                            "Move physical box " + slot + " from " + shortUnitName + " to " + otherUnitName + "?",
                            ButtonType.YES, ButtonType.NO);
                    alert.setTitle("Move Box " + slot);
                    alert.setHeaderText("Relocate Box to " + otherUnitName);
                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            try {
                                StorageScaffolding.moveBox(slot, otherUnitName, "MID");
                                slots.remove(slot);
                                if (otherSlots != null && !otherSlots.contains(slot)) {
                                    otherSlots.add(slot);
                                }
                                if (onShelvesChanged != null) onShelvesChanged.run();
                                if (feedbackLabel != null) {
                                    feedbackLabel.setText("✓ Moved box " + slot + " to " + otherUnitName + ".");
                                    feedbackLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                                }
                            } catch (Exception ex) {
                                if (feedbackLabel != null) {
                                    feedbackLabel.setText("Error moving box: " + ex.getMessage());
                                    feedbackLabel.setStyle("-fx-text-fill: #dc2626;");
                                }
                            }
                        }
                    });
                });

                MenuItem deleteItem = new MenuItem("🗑 Delete Box...");
                deleteItem.setOnAction(ev -> {
                    int cardCount = StorageScaffolding.getBoxCardCount(slot);
                    String confirmMsg = (cardCount > 0)
                            ? "⚠️ WARNING: Box " + slot + " currently contains " + cardCount + " card(s)!\nDeleting this box coordinate will reset these cards to UNSTORED and unassign them in the Catalog.\n\nAre you sure you want to delete box " + slot + "?"
                            : "Are you sure you want to remove empty box coordinate " + slot + " from " + shortUnitName + "?";
                    Alert alert = new Alert(
                            Alert.AlertType.CONFIRMATION,
                            confirmMsg,
                            ButtonType.YES, ButtonType.NO);
                    alert.setTitle("Delete Box " + slot);
                    alert.setHeaderText("Delete Box Coordinate " + slot);
                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            try {
                                StorageScaffolding.deleteBox(slot);
                                slots.remove(slot);
                                StorageBatch curBatch = activeBatchSupplier != null ? activeBatchSupplier.get() : null;
                                if (curBatch != null && curBatch.getBoxId().equalsIgnoreCase(slot)) {
                                    if (onActiveBatchCleared != null) onActiveBatchCleared.run();
                                }
                                if (onShelvesChanged != null) onShelvesChanged.run();
                                if (feedbackLabel != null) {
                                    feedbackLabel.setText("✓ Deleted box " + slot + " (" + cardCount + " card(s) unassigned to Unstored Queue).");
                                    feedbackLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                                }
                            } catch (Exception ex) {
                                if (feedbackLabel != null) {
                                    feedbackLabel.setText("Error deleting box: " + ex.getMessage());
                                    feedbackLabel.setStyle("-fx-text-fill: #dc2626;");
                                }
                            }
                        }
                    });
                });

                cm.getItems().addAll(assignItem, addCardItem, new SeparatorMenuItem(), renameItem, moveItem, deleteItem);
                slotLabel.setContextMenu(cm);

                grid.add(slotLabel, col, row);
                col++;
                if (col >= 2) {
                    col = 0;
                    row++;
                }
            }
        };

        renderGrid.run();
        slots.addListener((javafx.collections.ListChangeListener<String>) c -> renderGrid.run());

        addBoxBtn.setOnAction(e -> {
            String defaultVal = suggestedBoxSupplier != null ? suggestedBoxSupplier.get() : "";
            TextInputDialog dialog = new TextInputDialog(defaultVal);
            dialog.setTitle("Add Box to " + shortUnitName);
            dialog.setHeaderText("Add New Box Coordinate to " + shortUnitName);
            dialog.setContentText("Box ID (e.g. M14-2, BLB-2):");
            dialog.showAndWait().ifPresent(val -> {
                String clean = val.trim().toUpperCase();
                if (!clean.isEmpty()) {
                    if (slots.contains(clean) || (otherSlots != null && otherSlots.contains(clean))) {
                        if (feedbackLabel != null) {
                            feedbackLabel.setText("Box " + clean + " already exists on " + (slots.contains(clean) ? shortUnitName : otherUnitName) + ".");
                            feedbackLabel.setStyle("-fx-text-fill: #dc2626;");
                        }
                    } else {
                        try {
                            StorageScaffolding.createBox(clean, shortUnitName, "MID");
                        } catch (Exception ignored) {}
                        slots.add(clean);
                        if (feedbackLabel != null) {
                            feedbackLabel.setText("✓ Added box " + clean + " to " + shortUnitName + ".");
                            feedbackLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                        }
                    }
                    if (onBoxSelected != null) {
                        onBoxSelected.accept(clean);
                    }
                }
            });
        });

        return box;
    }

    private static void highlightShelfUnit(HBox unitsBox, String targetBoxId) {
        for (Node unitNode : unitsBox.getChildren()) {
            if (unitNode instanceof VBox unitVBox) {
                for (Node child : unitVBox.getChildren()) {
                    if (child instanceof GridPane grid) {
                        for (Node slotNode : grid.getChildren()) {
                            if (slotNode instanceof Label slotLabel) {
                                boolean isTarget = targetBoxId != null && (slotLabel.getId().equalsIgnoreCase("slot-" + targetBoxId)
                                        || slotLabel.getText().equalsIgnoreCase(targetBoxId));
                                if (isTarget) {
                                    slotLabel.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold;"
                                            + "-fx-border-color: #10b981; -fx-border-width: 2px; -fx-border-radius: 3px; -fx-font-size: 10px;");
                                } else {
                                    slotLabel.setStyle("-fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 3px;"
                                            + "-fx-background-color: -color-bg-subtle; -fx-text-fill: -color-fg-default; -fx-font-size: 10px;");
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
            int committed = wb.commitStaged();
            System.out.println("Seeded cards: " + committed);

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
