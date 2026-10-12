package tutorengine.ui;

import atlantafx.base.theme.Styles;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import tutorengine.db.Db;
import tutorengine.model.Card;
import tutorengine.outtake.OrderOuttake;
import tutorengine.outtake.OrderOuttake.CheckoutResult;
import tutorengine.outtake.OrderOuttake.OrderItem;
import tutorengine.outtake.OrderOuttake.OrderSummary;
import tutorengine.outtake.OrderOuttake.OuttakeAction;
import tutorengine.outtake.OrderOuttake.OuttakeBoxBatch;
import tutorengine.outtake.OrderOuttake.OuttakeHistory;
import tutorengine.storage.StorageScaffolding.NeighborBreadcrumb;

// OuttakeView: Module for Order Fulfillment & Customer Checkout.
// Batch-centric storage layout mirroring StorageView: clusters orders into physical box batches,
// displays 2D visual shelf coordinates (Shelf Unit A & B), provides granular per-card & per-box pick
// confirmations with live database and catalog inventory deduction, in-box alphabetical depth
// breadcrumbs, and full multi-level Undo/Redo command history engine.
public final class OuttakeView {
    private OuttakeView() {}

    public static Node build(Map<String, Card> catalog, TableView<Card> catalogTable) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        ObservableList<OrderItem> orderItems = FXCollections.observableArrayList();
        ObservableList<OuttakeBoxBatch> batchList = FXCollections.observableArrayList();
        ObservableList<OrderItem> batchCardList = FXCollections.observableArrayList();

        OuttakeHistory history = new OuttakeHistory();
        final OuttakeBoxBatch[] activeBatch = new OuttakeBoxBatch[1];

        // --- Top Bar: Dual Input Controls (Rapid Shortcut + CSV Import + Undo/Redo + Actions) ---
        TextField shortcutField = new TextField();
        shortcutField.setPromptText("Shortcut or Card: <SET> <NUM> [x<QTY>] (e.g. 'blb 1 x2', 'm14 84', 'Black Cat x3')...");
        shortcutField.setStyle("-fx-font-size: 13px; -fx-font-family: monospace;");
        HBox.setHgrow(shortcutField, Priority.ALWAYS);

        Button addCardBtn = new Button("+ Add Card");
        addCardBtn.getStyleClass().add(Styles.ACCENT);
        addCardBtn.setStyle("-fx-font-weight: bold;");

        Button importCsvBtn = new Button("📂 Import Order CSV");
        importCsvBtn.setStyle("-fx-font-weight: bold;");

        Button undoBtn = new Button("⤺ Undo (Ctrl+Z)");
        undoBtn.setTooltip(new Tooltip("Undo last pick or order mutation (Ctrl+Z)"));
        undoBtn.setDisable(true);

        Button redoBtn = new Button("⤻ Redo (Ctrl+Y)");
        redoBtn.setTooltip(new Tooltip("Redo last undone action (Ctrl+Y)"));
        redoBtn.setDisable(true);

        Button sortPickPathBtn = new Button("⇄ Sort Route");
        sortPickPathBtn.setTooltip(new Tooltip("Organize items and batches into a forward-pass physical walking route (Shelf Unit -> Box ID -> Name ASC)"));

        Button clearOrderBtn = new Button("🗑 Clear Order");

        Label statusLabel = new Label("Ready. Enter card shortcut above or import an order CSV file.");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");
        statusLabel.setWrapText(true);

        HBox topInputRow = new HBox(8, shortcutField, addCardBtn, importCsvBtn, undoBtn, redoBtn, sortPickPathBtn, clearOrderBtn);
        topInputRow.setAlignment(Pos.CENTER_LEFT);

        VBox topSection = new VBox(6, topInputRow, statusLabel);
        topSection.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(topSection);

        // --- Left Pane: Destination Box Batches (Grouped by Physical Shelf Box) ---
        Label batchesTitle = new Label("1. Destination Box Batches (Physical Pick Route)");
        batchesTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        TableView<OuttakeBoxBatch> batchTable = new TableView<>(batchList);
        setupBatchColumns(batchTable);

        Label unstoredSummary = new Label("Batches: 0 boxes to visit (0 picked)");
        unstoredSummary.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");

        VBox leftPane = new VBox(8, batchesTitle, batchTable, unstoredSummary);
        leftPane.setPrefWidth(340);
        VBox.setVgrow(batchTable, Priority.ALWAYS);

        // --- Center Pane: Active Box Batch, 2D Shelf Visual Map & Pick Checklist ---
        Label activeBatchTitle = new Label("Select a box batch from the left to start picking");
        activeBatchTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // Directive banner mirroring StorageView
        Label directiveBanner = new Label("📋 PICK DIRECTIVE: Go to shelf unit, pull box, and pick cards in order.");
        directiveBanner.setWrapText(true);
        directiveBanner.setStyle("-fx-background-color: -color-accent-subtle; -fx-text-fill: -color-accent-fg; -fx-padding: 8px;"
                + "-fx-border-color: -color-accent-emphasis; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-font-weight: bold;");
        directiveBanner.setVisible(false);

        // 2D Shelf Visual Map container
        VBox shelfMapPane = new VBox(6);
        shelfMapPane.setPadding(new Insets(6));
        shelfMapPane.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px;");

        Label shelfMapHeader = new Label("Physical Shelf Coordinates (2D Store Map)");
        shelfMapHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        shelfMapPane.getChildren().add(shelfMapHeader);

        HBox unitsBox = new HBox(12);
        unitsBox.setAlignment(Pos.CENTER_LEFT);

        ObservableList<String> unitASlots = FXCollections.observableArrayList("BLB-1", "TSP-1", "ZEN-1", "XLN-1", "DOM-1", "WOE-1");
        ObservableList<String> unitBSlots = FXCollections.observableArrayList("M14-1", "MH3-1", "OTJ-1", "FDN-1", "ELD-1", "ELD-2", "DMU-1");

        // Load configured boxes from database
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

        Consumer<String> selectBoxHandler = clickedBox -> {
            highlightShelfUnit(unitsBox, clickedBox);
            // If a batch corresponds to this clicked box, select it in the batchTable
            boolean found = false;
            for (OuttakeBoxBatch b : batchList) {
                if (b.getBoxId().equalsIgnoreCase(clickedBox)) {
                    batchTable.getSelectionModel().select(b);
                    found = true;
                    break;
                }
            }
            if (!found) {
                statusLabel.setText("Physical box: " + clickedBox + ". No items in the current order belong to this box.");
                statusLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            }
        };

        VBox shelfUnitA = buildShelfUnitVisual("Shelf Unit A (Expansion Boxes)", "Shelf Unit A", unitASlots, selectBoxHandler);
        VBox shelfUnitB = buildShelfUnitVisual("Shelf Unit B (Core & Modern Boxes)", "Shelf Unit B", unitBSlots, selectBoxHandler);
        unitsBox.getChildren().addAll(shelfUnitA, shelfUnitB);
        shelfMapPane.getChildren().add(unitsBox);

        // Checklist Table for Active Box Batch
        Label cardsHeader = new Label("Cards in Active Box Batch (Pre-Sorted by Pick Depth):");
        cardsHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        TableView<OrderItem> batchItemTable = new TableView<>(batchCardList);

        // Batch-level confirmation bar
        Button confirmBatchBtn = new Button("✓ Confirm Box Picked (All Cards in Box)");
        confirmBatchBtn.getStyleClass().add(Styles.SUCCESS);
        confirmBatchBtn.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 6px 14px;");
        confirmBatchBtn.setDisable(true);

        Label batchActionFeedback = new Label();
        batchActionFeedback.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        HBox batchActionRow = new HBox(10, confirmBatchBtn, batchActionFeedback);
        batchActionRow.setAlignment(Pos.CENTER_LEFT);
        batchActionRow.setPadding(new Insets(4, 0, 0, 0));

        VBox centerPane = new VBox(8, activeBatchTitle, directiveBanner, shelfMapPane, cardsHeader, batchItemTable, batchActionRow);
        VBox.setVgrow(batchItemTable, Priority.ALWAYS);
        centerPane.setPadding(new Insets(0, 6, 0, 6));

        // --- Right Dock: Pick Scaffolding, Breadcrumbs, Order Summary & Checkout ---
        VBox rightDock = new VBox(10);
        rightDock.setPrefWidth(310);
        rightDock.setPadding(new Insets(10));
        rightDock.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 6; -fx-border-color: -color-border-subtle; -fx-border-radius: 6;");

        Label rightHeader = new Label("Physical Location & Pick Guidance");
        rightHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        // Thumbnail image & selected item info
        ImageView previewImage = new ImageView();
        previewImage.setFitWidth(90);
        previewImage.setFitHeight(126);
        previewImage.setPreserveRatio(true);
        previewImage.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 2);");

        Label selTitle = new Label("Select a card to view pick coordinates");
        selTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        selTitle.setWrapText(true);

        Label selLocationBadge = new Label("Location: —");
        selLocationBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -color-accent-fg;");

        Label selStockBadge = new Label("Stock: —");
        selStockBadge.setStyle("-fx-font-size: 11px;");

        HBox selCardHeader = new HBox(8, previewImage, new VBox(4, selTitle, selLocationBadge, selStockBadge));
        selCardHeader.setAlignment(Pos.CENTER_LEFT);

        // Virtual In-Box Stack Breadcrumb Scaffolding
        VBox breadcrumbBox = new VBox(6);
        breadcrumbBox.setPadding(new Insets(8));
        breadcrumbBox.setStyle("-fx-background-color: -color-bg-default; -fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px;");

        Label breadcrumbHeader = new Label("In-Box Shelf Placement / Breadcrumbs:");
        breadcrumbHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Label breadcrumbText = new Label("Select an item to view neighboring cards in box");
        breadcrumbText.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");
        breadcrumbText.setWrapText(true);

        ProgressBar depthProgress = new ProgressBar(0.5);
        depthProgress.setMaxWidth(Double.MAX_VALUE);
        depthProgress.setPrefHeight(14);

        Label depthLabel = new Label("Estimated In-Box Depth: ~50%");
        depthLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");

        breadcrumbBox.getChildren().addAll(breadcrumbHeader, breadcrumbText, depthProgress, depthLabel);

        // Pick route metrics
        VBox routeSummaryBox = new VBox(4);
        routeSummaryBox.setPadding(new Insets(6));
        routeSummaryBox.setStyle("-fx-background-color: -color-bg-default; -fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 4px;");

        Label routeHeader = new Label("Pick Route Metrics:");
        routeHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Label boxesCountLabel = new Label("Boxes to visit: 0");
        boxesCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        routeSummaryBox.getChildren().addAll(routeHeader, boxesCountLabel);

        // Order Total Summary
        VBox totalSummaryBox = new VBox(4);
        totalSummaryBox.setPadding(new Insets(8));
        totalSummaryBox.setStyle("-fx-background-color: -color-bg-default; -fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 6px;");

        Label orderSummaryHeader = new Label("Order Fulfillment Summary:");
        orderSummaryHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Label itemsCountLabel = new Label("Total Cards: 0 (0 line items)");
        itemsCountLabel.setStyle("-fx-font-size: 11px;");

        Label orderTotalLabel = new Label("Total Amount: ₱0.00");
        orderTotalLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: -color-accent-fg;");

        Label stockStatusLabel = new Label("Order is empty");
        stockStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        totalSummaryBox.getChildren().addAll(orderSummaryHeader, itemsCountLabel, orderTotalLabel, stockStatusLabel);

        // Checkout Button & Feedback
        Button checkoutBtn = new Button("✓ Complete Checkout (Fulfill Order)");
        checkoutBtn.getStyleClass().add(Styles.SUCCESS);
        checkoutBtn.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8px 16px;");
        checkoutBtn.setMaxWidth(Double.MAX_VALUE);
        checkoutBtn.setDisable(true);

        Label checkoutFeedback = new Label();
        checkoutFeedback.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        checkoutFeedback.setWrapText(true);

        rightDock.getChildren().addAll(
                rightHeader, selCardHeader, breadcrumbBox, routeSummaryBox, totalSummaryBox, checkoutBtn, checkoutFeedback);

        // Assemble 3-way SplitPane
        SplitPane split = new SplitPane(leftPane, centerPane, rightDock);
        split.setDividerPositions(0.30, 0.72);
        root.setCenter(split);

        // --- Interaction Logic & Controller Helpers ---

        final Runnable[] updateSummaryRef = new Runnable[1];
        final Consumer<String>[] refreshBatchesRef = new Consumer[1];

        Runnable updateSummary = () -> {
            OrderSummary sum = OrderOuttake.computeSummary(orderItems);
            itemsCountLabel.setText("Total Cards: " + sum.getTotalCards() + " (" + sum.getTotalLines() + " line items)");
            orderTotalLabel.setText("Total Amount: ₱" + String.format("%.2f", sum.getTotalAmount()));

            int fullyPickedBoxes = 0;
            int totalBoxes = batchList.size();
            for (OuttakeBoxBatch b : batchList) {
                if (b.isFullyPicked()) fullyPickedBoxes++;
            }

            boxesCountLabel.setText("Boxes: " + totalBoxes + " to visit (" + fullyPickedBoxes + " fully picked)");
            unstoredSummary.setText("Batches: " + totalBoxes + " boxes to visit (" + fullyPickedBoxes + " picked)");

            if (sum.getTotalCards() > 0) {
                checkoutBtn.setDisable(false);
                checkoutBtn.setText("✓ Complete Checkout (" + sum.getTotalCards() + " cards - ₱" + String.format("%.2f", sum.getTotalAmount()) + ")");
                if (sum.isAllInStock()) {
                    stockStatusLabel.setText("All items available in stock ✓");
                    stockStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #059669; -fx-font-weight: bold;");
                } else {
                    stockStatusLabel.setText("⚠️ Some items exceed stored inventory");
                    stockStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #dc2626; -fx-font-weight: bold;");
                }
            } else {
                checkoutBtn.setDisable(true);
                checkoutBtn.setText("✓ Complete Checkout (Fulfill Order)");
                stockStatusLabel.setText("Order is empty");
                stockStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");
            }

            undoBtn.setDisable(!history.canUndo());
            redoBtn.setDisable(!history.canRedo());
        };
        updateSummaryRef[0] = updateSummary;

        Consumer<String> refreshBatches = (preferredBoxId) -> {
            List<OuttakeBoxBatch> batches = OrderOuttake.groupOrderIntoBatches(orderItems);
            batchList.setAll(batches);

            // Add any boxes from batches to shelf slots if not present
            for (OuttakeBoxBatch b : batches) {
                String bId = b.getBoxId();
                if (bId != null && !bId.isBlank() && !bId.equals("Unassigned")) {
                    if (b.getShelfUnit().contains("Shelf Unit B")) {
                        if (!unitBSlots.contains(bId)) unitBSlots.add(bId);
                    } else {
                        if (!unitASlots.contains(bId)) unitASlots.add(bId);
                    }
                }
            }

            // Check if the currently selected box is full and suggest provisioning next box
            OuttakeBoxBatch currentBox = null;
            if (preferredBoxId != null) {
                for (OuttakeBoxBatch b : batches) {
                    if (b.getBoxId().equalsIgnoreCase(preferredBoxId)) {
                        currentBox = b;
                        break;
                    }
                }
            }
            if (currentBox == null && !batches.isEmpty()) {
                currentBox = batches.get(0);
            }

            if (currentBox != null && currentBox.getTotalCardCount() >= 1000) {
                // Box is full; suggest provisioning the next volume slot
                String reason = "Box " + currentBox.getBoxId() + " is full (" + currentBox.getTotalCardCount() + "/1000 cards). ";
                if (currentBox.getBoxId().contains("ELD")) {
                    reason += "Suggestion: Create " + currentBox.getBoxId().replace("ELD", "ELD-2") + " at " + currentBox.getShelfUnit() + " " + currentBox.getShelfTier() + " tier.";
                } else {
                    reason += "Suggestion: Provision next volume slot at same shelf.";
                }
                statusLabel.setText("⚠ " + reason);
                statusLabel.setStyle("-fx-text-fill: #d97706;");
            } else {
                statusLabel.setStyle("-fx-text-fill: -color-fg-default;");
            }

            OuttakeBoxBatch toSelect = null;
            if (preferredBoxId != null) {
                for (OuttakeBoxBatch b : batches) {
                    if (b.getBoxId().equalsIgnoreCase(preferredBoxId)) {
                        toSelect = b;
                        break;
                    }
                }
            }
            if (toSelect == null && !batches.isEmpty()) {
                toSelect = batches.get(0);
            }

            if (toSelect != null) {
                batchTable.getSelectionModel().select(toSelect);
                activeBatch[0] = toSelect;
                batchCardList.setAll(toSelect.getItems());
                activeBatchTitle.setText("Active Box: " + toSelect.getBoxId() + " (" + toSelect.getCoordinatesDisplay() + ") - " + toSelect.getItems().size() + " card(s)");
                directiveBanner.setText("📋 PICK DIRECTIVE: Go to " + toSelect.getShelfUnit() + " (" + toSelect.getShelfTier()
                        + " tier), locate box " + toSelect.getBoxId() + ", and retrieve the " + toSelect.getTotalCardCount() + " card(s) listed below:");
                directiveBanner.setVisible(true);
                highlightShelfUnit(unitsBox, toSelect.getBoxId());

                if (toSelect.isFullyPicked()) {
                    confirmBatchBtn.setDisable(true);
                    confirmBatchBtn.setText("✓ Box Fully Picked (" + toSelect.getTotalCardCount() + "/" + toSelect.getTotalCardCount() + ")");
                } else {
                    confirmBatchBtn.setDisable(false);
                    confirmBatchBtn.setText("✓ Confirm Box Picked (" + toSelect.getRemainingCardCount() + " cards remaining)");
                }
            } else {
                activeBatch[0] = null;
                batchCardList.clear();
                activeBatchTitle.setText("Select a box batch from the left to start picking");
                directiveBanner.setVisible(false);
                confirmBatchBtn.setDisable(true);
                confirmBatchBtn.setText("✓ Confirm Box Picked (All Cards in Box)");
                highlightShelfUnit(unitsBox, null);
            }

            batchTable.refresh();
            batchItemTable.refresh();
            updateSummary.run();
        };
        refreshBatchesRef[0] = refreshBatches;

        // --- Setup Table Columns & Actions ---
        Consumer<OrderItem> onConfirmPick = (item) -> {
            try {
                List<String> ids = OrderOuttake.confirmCardPick(item);
                history.push(new OuttakeAction(OuttakeAction.Type.PICK_CARD, item, ids));
                batchItemTable.refresh();
                batchTable.refresh();
                if (activeBatch[0] != null) {
                    if (activeBatch[0].isFullyPicked()) {
                        confirmBatchBtn.setDisable(true);
                        confirmBatchBtn.setText("✓ Box Fully Picked (" + activeBatch[0].getTotalCardCount() + "/" + activeBatch[0].getTotalCardCount() + ")");
                    } else {
                        confirmBatchBtn.setDisable(false);
                        confirmBatchBtn.setText("✓ Confirm Box Picked (" + activeBatch[0].getRemainingCardCount() + " cards remaining)");
                    }
                }
                statusLabel.setText("✓ Picked \"" + item.getName() + "\" x" + item.getQuantity() + " from " + item.getBoxId() + " (Catalog stock decremented)");
                statusLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                updateSummary.run();
                if (catalogTable != null) catalogTable.refresh();
            } catch (Exception ex) {
                statusLabel.setText("Error confirming pick: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc2626;");
            }
        };

        Consumer<OrderItem> onRevertPick = (item) -> {
            try {
                OrderOuttake.revertCardPick(item, item.getCheckedOutInstanceIds());
                batchItemTable.refresh();
                batchTable.refresh();
                if (activeBatch[0] != null) {
                    confirmBatchBtn.setDisable(false);
                    confirmBatchBtn.setText("✓ Confirm Box Picked (" + activeBatch[0].getRemainingCardCount() + " cards remaining)");
                }
                statusLabel.setText("↩ Reverted pick for \"" + item.getName() + "\" (Restored to STORED & Catalog incremented)");
                statusLabel.setStyle("-fx-text-fill: #0284c7;");
                updateSummary.run();
                if (catalogTable != null) catalogTable.refresh();
            } catch (Exception ex) {
                statusLabel.setText("Error reverting pick: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc2626;");
            }
        };

        setupBatchItemColumns(batchItemTable, onConfirmPick, onRevertPick);
        HoverPreviewHelper.attach(batchItemTable, OrderItem::getName, OrderItem::getSetCode,
                item -> HoverPreviewHelper.buildCardKey(item.getSetCode(), item.getCollectorNumber(), item.getName()));

        // Context menu to remove item from active order
        ContextMenu itemCm = new ContextMenu();
        MenuItem removeItem = new MenuItem("🗑 Remove Card from Order");
        removeItem.setOnAction(e -> {
            OrderItem sel = batchItemTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                try {
                    if (sel.isPicked()) {
                        OrderOuttake.revertCardPick(sel, sel.getCheckedOutInstanceIds());
                    }
                    int idx = orderItems.indexOf(sel);
                    orderItems.remove(sel);
                    history.push(new OuttakeAction(OuttakeAction.Type.REMOVE_ITEM, sel, idx, sel.getQuantity(), 0));
                    refreshBatches.accept(activeBatch[0] != null ? activeBatch[0].getBoxId() : null);
                    statusLabel.setText("✓ Removed \"" + sel.getName() + "\" from order.");
                    statusLabel.setStyle("-fx-text-fill: -color-fg-muted;");
                } catch (Exception ex) {
                    statusLabel.setText("Error removing item: " + ex.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #dc2626;");
                }
            }
        });
        itemCm.getItems().add(removeItem);
        batchItemTable.setContextMenu(itemCm);

        // Batch table selection listener
        batchTable.getSelectionModel().selectedItemProperty().addListener((o, oldBatch, b) -> {
            if (b == null) return;
            activeBatch[0] = b;
            activeBatchTitle.setText("Active Box: " + b.getBoxId() + " (" + b.getCoordinatesDisplay() + ") - " + b.getItems().size() + " card(s)");
            directiveBanner.setText("📋 PICK DIRECTIVE: Go to " + b.getShelfUnit() + " (" + b.getShelfTier()
                    + " tier), locate box " + b.getBoxId() + ", and retrieve the " + b.getTotalCardCount() + " card(s) listed below:");
            directiveBanner.setVisible(true);

            highlightShelfUnit(unitsBox, b.getBoxId());
            batchCardList.setAll(b.getItems());

            if (b.isFullyPicked()) {
                confirmBatchBtn.setDisable(true);
                confirmBatchBtn.setText("✓ Box Fully Picked (" + b.getTotalCardCount() + "/" + b.getTotalCardCount() + ")");
            } else {
                confirmBatchBtn.setDisable(false);
                confirmBatchBtn.setText("✓ Confirm Box Picked (" + b.getRemainingCardCount() + " cards remaining)");
            }

            if (!b.getItems().isEmpty()) {
                batchItemTable.getSelectionModel().select(0);
            }
        });

        // Batch Item Selection -> Update right dock preview & in-box breadcrumbs
        batchItemTable.getSelectionModel().selectedItemProperty().addListener((o, oldVal, item) -> {
            if (item == null) {
                selTitle.setText("Select a card to view pick coordinates");
                selLocationBadge.setText("Location: —");
                selStockBadge.setText("Stock: —");
                breadcrumbText.setText("Select an item to view neighboring cards in box");
                depthProgress.setProgress(0.5);
                depthLabel.setText("Estimated In-Box Depth: ~50%");
                previewImage.setImage(null);
                return;
            }

            selTitle.setText(item.getName() + (!item.getSetCode().isEmpty() ? " [" + item.getSetCode() + "]" : ""));
            selLocationBadge.setText("Location: " + item.getLocationDisplay());
            selStockBadge.setText("Stored Stock: " + item.getAvailableStock() + " | Requested: " + item.getQuantity()
                    + (item.isSufficientStock() ? " (Available ✓)" : " (INSUFFICIENT ⚠️)"));
            selStockBadge.setStyle(item.isSufficientStock() ? "-fx-text-fill: #059669; -fx-font-weight: bold;" : "-fx-text-fill: #dc2626; -fx-font-weight: bold;");

            // Calculate breadcrumbs in physical box
            NeighborBreadcrumb bc = OrderOuttake.calculateBreadcrumb(item.getBoxId(), item.getName());
            breadcrumbText.setText(bc.getBreadcrumbText());
            double p = bc.getEstimatedPercent();
            depthProgress.setProgress(p / 100.0);
            depthLabel.setText(String.format("Box: %s | Estimated Slot: ~%.0f%% depth from front", item.getBoxId(), p));

            // Load card preview image asynchronously
            String cardKey = HoverPreviewHelper.buildCardKey(item.getSetCode(), item.getCollectorNumber(), item.getName());
            javafx.concurrent.Task<Image> loadTask = new javafx.concurrent.Task<>() {
                @Override protected Image call() {
                    return CardImageCache.getFx(cardKey, item.getName(), 100, CardImageCache.Variant.NORMAL, item.getSetCode());
                }
            };
            loadTask.setOnSucceeded(ev -> {
                if (loadTask.getValue() != null) {
                    previewImage.setImage(loadTask.getValue());
                } else {
                    previewImage.setImage(null);
                }
            });
            Thread t = new Thread(loadTask, "outtake-batch-preview-load");
            t.setDaemon(true);
            t.start();
        });

        // Batch-level confirmation action
        confirmBatchBtn.setOnAction(e -> {
            if (activeBatch[0] == null) return;
            OuttakeBoxBatch b = activeBatch[0];
            try {
                Map<OrderItem, List<String>> batchMap = OrderOuttake.confirmBoxBatchPick(b);
                history.push(new OuttakeAction(OuttakeAction.Type.PICK_BATCH, b, batchMap));
                batchItemTable.refresh();
                batchTable.refresh();
                confirmBatchBtn.setDisable(true);
                confirmBatchBtn.setText("✓ Box Fully Picked (" + b.getTotalCardCount() + "/" + b.getTotalCardCount() + ")");
                batchActionFeedback.setText("✓ Confirmed all " + b.getTotalCardCount() + " cards picked from box " + b.getBoxId() + "!");
                batchActionFeedback.setStyle("-fx-text-fill: #059669;");
                statusLabel.setText("✓ Confirmed all cards in box " + b.getBoxId() + " picked (Catalog stock decremented).");
                statusLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                updateSummary.run();
                if (catalogTable != null) catalogTable.refresh();
            } catch (Exception ex) {
                batchActionFeedback.setText("Error confirming box pick: " + ex.getMessage());
                batchActionFeedback.setStyle("-fx-text-fill: #dc2626;");
                statusLabel.setText("Error confirming box pick: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc2626;");
            }
        });

        // Add single card from shortcut field
        Runnable addCardAction = () -> {
            String raw = shortcutField.getText();
            if (raw == null || raw.isBlank()) return;
            OrderItem item = OrderOuttake.parseSingleCardInput(raw);
            if (item != null) {
                boolean found = false;
                for (OrderItem existing : orderItems) {
                    if (existing.getName().equalsIgnoreCase(item.getName())
                            && (item.getSetCode().isEmpty() || existing.getSetCode().equalsIgnoreCase(item.getSetCode()))) {
                        int oldQty = existing.getQuantity();
                        existing.setQuantity(oldQty + item.getQuantity());
                        history.push(new OuttakeAction(OuttakeAction.Type.MODIFY_QTY, existing, -1, oldQty, existing.getQuantity()));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    orderItems.add(item);
                    history.push(new OuttakeAction(OuttakeAction.Type.ADD_ITEM, item, null));
                }
                shortcutField.clear();
                refreshBatches.accept(item.getBoxId());
                statusLabel.setText("✓ Added \"" + item.getName() + "\" x" + item.getQuantity() + " to order -> " + item.getLocationDisplay());
                statusLabel.setStyle("-fx-text-fill: #059669; -fx-font-size: 12px;");
            } else {
                statusLabel.setText("Could not resolve card: " + raw);
                statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
            }
        };

        addCardBtn.setOnAction(e -> addCardAction.run());
        shortcutField.setOnAction(e -> addCardAction.run());

        // Import Order CSV
        importCsvBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Import Customer Order CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
            File file = fc.showOpenDialog(root.getScene() != null ? root.getScene().getWindow() : null);
            if (file != null) {
                try {
                    List<OrderItem> imported = OrderOuttake.importOrderCsvFile(file);
                    if (!imported.isEmpty()) {
                        orderItems.addAll(imported);
                        refreshBatches.accept(null);
                        statusLabel.setText("✓ Successfully imported " + imported.size() + " line items from " + file.getName() + ".");
                        statusLabel.setStyle("-fx-text-fill: #059669; -fx-font-size: 12px;");
                    } else {
                        statusLabel.setText("CSV file was empty or had no recognizable order items.");
                        statusLabel.setStyle("-fx-text-fill: #d97706; -fx-font-size: 12px;");
                    }
                } catch (Exception ex) {
                    statusLabel.setText("Error reading CSV file: " + ex.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
                }
            }
        });

        // Undo Action
        Runnable undoAction = () -> {
            try {
                if (history.canUndo()) {
                    OuttakeAction undid = history.undo(orderItems);
                    refreshBatches.accept(activeBatch[0] != null ? activeBatch[0].getBoxId() : null);
                    statusLabel.setText("⤺ Undid action (" + undid.type + ")");
                    statusLabel.setStyle("-fx-text-fill: #0284c7; -fx-font-size: 12px;");
                    if (catalogTable != null) catalogTable.refresh();
                }
            } catch (Exception ex) {
                statusLabel.setText("Error undoing action: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
            }
        };

        // Redo Action
        Runnable redoAction = () -> {
            try {
                if (history.canRedo()) {
                    OuttakeAction redid = history.redo(orderItems);
                    refreshBatches.accept(activeBatch[0] != null ? activeBatch[0].getBoxId() : null);
                    statusLabel.setText("⤻ Redid action (" + redid.type + ")");
                    statusLabel.setStyle("-fx-text-fill: #0284c7; -fx-font-size: 12px;");
                    if (catalogTable != null) catalogTable.refresh();
                }
            } catch (Exception ex) {
                statusLabel.setText("Error redoing action: " + ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
            }
        };

        undoBtn.setOnAction(e -> undoAction.run());
        redoBtn.setOnAction(e -> redoAction.run());

        // Sort by forward-pass pick path
        sortPickPathBtn.setOnAction(e -> {
            if (!orderItems.isEmpty()) {
                List<OrderItem> sorted = OrderOuttake.sortPickPath(orderItems);
                orderItems.setAll(sorted);
                refreshBatches.accept(activeBatch[0] != null ? activeBatch[0].getBoxId() : null);
                statusLabel.setText("✓ Order batches sorted by physical forward-pass pick route (Shelf Unit -> Box ID -> Name ASC).");
                statusLabel.setStyle("-fx-text-fill: #0284c7; -fx-font-size: 12px;");
            }
        });

        // Clear order
        clearOrderBtn.setOnAction(e -> {
            orderItems.clear();
            history.clear();
            refreshBatches.accept(null);
            checkoutFeedback.setText("");
            batchActionFeedback.setText("");
            statusLabel.setText("Order cleared.");
            statusLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        });

        // Complete checkout action
        checkoutBtn.setOnAction(e -> {
            if (orderItems.isEmpty()) return;
            OrderSummary sum = OrderOuttake.computeSummary(orderItems);

            if (!sum.isAllInStock()) {
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION,
                        "⚠️ Some line items request more cards than currently stored in inventory!\n\nDo you wish to proceed with checking out available units?",
                        ButtonType.YES, ButtonType.NO);
                confirmAlert.setTitle("Confirm Order Checkout");
                confirmAlert.setHeaderText("Stock Warning");
                var res = confirmAlert.showAndWait().orElse(ButtonType.NO);
                if (res != ButtonType.YES) return;
            }

            try {
                CheckoutResult result = OrderOuttake.checkoutOrder(new ArrayList<>(orderItems));
                if (result.isSuccess()) {
                    checkoutFeedback.setText(result.getMessage());
                    checkoutFeedback.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                    statusLabel.setText(result.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");

                    orderItems.clear();
                    history.clear();
                    refreshBatches.accept(null);

                    if (catalogTable != null) {
                        catalogTable.refresh();
                    }
                } else {
                    checkoutFeedback.setText("Checkout failed: " + result.getMessage());
                    checkoutFeedback.setStyle("-fx-text-fill: #dc2626;");
                }
            } catch (Exception ex) {
                checkoutFeedback.setText("Checkout error: " + ex.getMessage());
                checkoutFeedback.setStyle("-fx-text-fill: #dc2626;");
            }
        });

        // Scene key event filter for Ctrl+Z and Ctrl+Y
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                    if ((ev.isControlDown() || ev.isShortcutDown()) && ev.getCode() == KeyCode.Z) {
                        if (ev.isShiftDown()) {
                            redoAction.run();
                        } else {
                            undoAction.run();
                        }
                        ev.consume();
                    } else if ((ev.isControlDown() || ev.isShortcutDown()) && ev.getCode() == KeyCode.Y) {
                        redoAction.run();
                        ev.consume();
                    }
                });
            }
        });

        updateSummary.run();
        return root;
    }

    private static VBox buildShelfUnitVisual(
            String unitTitle,
            String shortUnitName,
            ObservableList<String> slots,
            Consumer<String> onBoxSelected) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(6));
        box.setStyle("-fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-color: -color-bg-default;");

        Label header = new Label(unitTitle);
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        box.getChildren().add(header);

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
                slotLabel.setPrefSize(100, 22);
                slotLabel.setCursor(Cursor.HAND);
                slotLabel.setStyle("-fx-border-color: -color-border-subtle; -fx-border-width: 1px; -fx-border-radius: 3px;"
                        + "-fx-background-color: -color-bg-subtle; -fx-text-fill: -color-fg-default; -fx-font-size: 10px;");

                Tooltip.install(slotLabel, new Tooltip("Box: " + slot + " (" + shortUnitName + ")"));

                slotLabel.setOnMouseClicked(e -> {
                    if (onBoxSelected != null) {
                        onBoxSelected.accept(slot);
                    }
                });

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
        return box;
    }

    public static void highlightShelfUnit(HBox unitsBox, String targetBoxId) {
        if (unitsBox == null) return;
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

    private static void setupBatchColumns(TableView<OuttakeBoxBatch> table) {
        TableColumn<OuttakeBoxBatch, String> boxCol = new TableColumn<>("Box ID");
        boxCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBoxId()));
        boxCol.setPrefWidth(85);

        TableColumn<OuttakeBoxBatch, String> coordsCol = new TableColumn<>("Coordinates");
        coordsCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCoordinatesDisplay()));
        coordsCol.setPrefWidth(125);

        TableColumn<OuttakeBoxBatch, Number> countCol = new TableColumn<>("Cards");
        countCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getTotalCardCount()));
        countCol.setPrefWidth(50);

        TableColumn<OuttakeBoxBatch, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));
        statusCol.setPrefWidth(105);
        statusCol.setCellFactory(col -> new TableCell<OuttakeBoxBatch, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("✓")) {
                        setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                    } else if (item.startsWith("Partial")) {
                        setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: -color-fg-muted;");
                    }
                }
            }
        });

        table.getColumns().addAll(List.of(boxCol, coordsCol, countCol, statusCol));
    }

    private static void setupBatchItemColumns(
            TableView<OrderItem> table,
            Consumer<OrderItem> onConfirmPick,
            Consumer<OrderItem> onRevertPick) {
        TableColumn<OrderItem, String> nameCol = new TableColumn<>("Card Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameCol.setPrefWidth(155);

        TableColumn<OrderItem, String> setCol = new TableColumn<>("Set");
        setCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSetCode()));
        setCol.setPrefWidth(45);

        TableColumn<OrderItem, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getQuantity()));
        qtyCol.setPrefWidth(40);

        TableColumn<OrderItem, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("₱%.2f", c.getValue().getPrice())));
        priceCol.setPrefWidth(65);

        TableColumn<OrderItem, String> depthCol = new TableColumn<>("In-Box Depth");
        depthCol.setCellValueFactory(c -> {
            NeighborBreadcrumb bc = OrderOuttake.calculateBreadcrumb(c.getValue().getBoxId(), c.getValue().getName());
            return new SimpleStringProperty(String.format("~%.0f%% depth", bc.getEstimatedPercent()));
        });
        depthCol.setPrefWidth(90);

        TableColumn<OrderItem, String> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isSufficientStock()
                        ? "✓ " + c.getValue().getAvailableStock()
                        : "⚠️ " + c.getValue().getAvailableStock() + "/" + c.getValue().getQuantity()));
        stockCol.setPrefWidth(75);
        stockCol.setCellFactory(col -> new TableCell<OrderItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("✓")) {
                        setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<OrderItem, Void> actionCol = new TableColumn<>("Pick Action");
        actionCol.setPrefWidth(125);
        actionCol.setCellFactory(col -> new TableCell<OrderItem, Void>() {
            private final Button pickBtn = new Button("✓ Confirm Pick");
            private final Label pickedBadge = new Label("✓ Picked");
            private final HBox pickedBox = new HBox(4);
            private final Button undoItemBtn = new Button("↩");

            {
                pickBtn.getStyleClass().add(Styles.SUCCESS);
                pickBtn.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2px 8px;");
                pickBtn.setOnAction(e -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        OrderItem item = getTableView().getItems().get(getIndex());
                        if (item != null && onConfirmPick != null) {
                            onConfirmPick.accept(item);
                        }
                    }
                });

                pickedBadge.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold; -fx-font-size: 11px;");
                undoItemBtn.setStyle("-fx-font-size: 10px; -fx-padding: 1px 4px;");
                undoItemBtn.setTooltip(new Tooltip("Revert pick for this card"));
                undoItemBtn.setOnAction(e -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        OrderItem item = getTableView().getItems().get(getIndex());
                        if (item != null && onRevertPick != null) {
                            onRevertPick.accept(item);
                        }
                    }
                });
                pickedBox.setAlignment(Pos.CENTER_LEFT);
                pickedBox.getChildren().addAll(pickedBadge, undoItemBtn);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    OrderItem orderItem = getTableView().getItems().get(getIndex());
                    if (orderItem.isPicked()) {
                        setGraphic(pickedBox);
                    } else {
                        setGraphic(pickBtn);
                    }
                }
            }
        });

        table.getColumns().addAll(List.of(nameCol, setCol, qtyCol, priceCol, depthCol, stockCol, actionCol));
    }
}
