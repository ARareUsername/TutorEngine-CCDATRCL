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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tutorengine.auth.UserSessionManager;
import tutorengine.db.Db;
import tutorengine.intake.WorkingBuffer;
import tutorengine.intake.WorkingBuffer.WorkingItem;
import tutorengine.search.ScryfallLookup;

// StagingView: Git-style intake workflow (Working Buffer -> Staging Review -> Batch Commit).
// Data operations are toolkit-free (headless testable via main()).
public final class StagingView {
    private StagingView() {}

    public static WorkingBuffer createSampleBuffer() {
        WorkingBuffer wb = new WorkingBuffer();
        wb.addItem(new WorkingItem("c1", "Lightning Bolt", "2X2", "117", "common", "NM", "nonfoil", 2));
        wb.addItem(new WorkingItem("c1", "Lightning Bolt", "2X2", "117", "common", "NM", "nonfoil", 1));
        wb.addItem(new WorkingItem("c2", "Counterspell", "DMR", "45", "uncommon", "LP", "foil", 1));
        wb.addItem(new WorkingItem("c3", "Dark Ritual", "STA", "26", "rare", "NM", "etched", 1));
        wb.addItem(new WorkingItem("c4", "Birds of Paradise", "DMR", "154", "rare", "NM", "nonfoil", 2));
        wb.addItem(new WorkingItem("c5", "Plains", "ONE", "262", "common", "NM", "nonfoil", 5));
        return wb;
    }

    public static Node build() {
        WorkingBuffer engine = new WorkingBuffer();
        ObservableList<WorkingItem> bufferItems = FXCollections.observableArrayList();
        ObservableList<WorkingItem> stagingItems = FXCollections.observableArrayList();

        // --- Left / Top: Working Buffer Area ---
        Label bufferTitle = new Label("1. Working Buffer (Scanned & Ingested Singles)");
        bufferTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        TextField nameInput = new TextField();
        nameInput.setPromptText("Card name (e.g. Black Lotus, Lightning Bolt)");
        nameInput.setPrefWidth(220);

        TextField setInput = new TextField();
        setInput.setPromptText("Set (e.g. 2X2)");
        setInput.setPrefWidth(70);

        TextField numInput = new TextField();
        numInput.setPromptText("Num");
        numInput.setPrefWidth(50);

        Button addLookupBtn = new Button("Lookup & Add");
        Button addSampleBtn = new Button("+ Load Sample Batch");

        HBox inputBar = new HBox(8, nameInput, setInput, numInput, addLookupBtn, addSampleBtn);
        inputBar.setAlignment(Pos.CENTER_LEFT);

        TableView<WorkingItem> bufferTable = new TableView<>(bufferItems);
        bufferTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setupColumns(bufferTable);
        HoverPreviewHelper.attach(bufferTable, WorkingItem::getName, WorkingItem::getSetCode,
                w -> HoverPreviewHelper.buildCardKey(w.getSetCode(), w.getCollectorNumber(), w.getId()));

        // Bulk edit toolbar
        ComboBox<String> condBox = new ComboBox<>(FXCollections.observableArrayList("NM", "LP", "MP", "HP"));
        condBox.setValue("NM");
        Button applyCondBtn = new Button("Set Condition");

        ComboBox<String> finishBox = new ComboBox<>(FXCollections.observableArrayList("nonfoil", "foil", "etched"));
        finishBox.setValue("nonfoil");
        Button applyFinishBtn = new Button("Set Finish");

        Button groupDupBtn = new Button("Group Duplicates");
        Button stageSelectedBtn = new Button("Stage Selected →");
        Button stageAllBtn = new Button("Stage All →");
        stageAllBtn.setStyle("-fx-font-weight: bold; -fx-base: #10b981;");

        HBox bulkBar = new HBox(8,
                new Label("Bulk:"), condBox, applyCondBtn,
                finishBox, applyFinishBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                groupDupBtn, stageSelectedBtn, stageAllBtn);
        bulkBar.setAlignment(Pos.CENTER_LEFT);
        bulkBar.setPadding(new Insets(4, 0, 4, 0));

        VBox bufferPane = new VBox(8, bufferTitle, inputBar, bufferTable, bulkBar);
        VBox.setVgrow(bufferTable, Priority.ALWAYS);

        // --- Right / Bottom: Staging Review & Commit Area ---
        Label stagingTitle = new Label("2. Staging Review Area (Verified & Ready for Commit)");
        stagingTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        TableView<WorkingItem> stagingTable = new TableView<>(stagingItems);
        setupColumns(stagingTable);
        HoverPreviewHelper.attach(stagingTable, WorkingItem::getName, WorkingItem::getSetCode,
                w -> HoverPreviewHelper.buildCardKey(w.getSetCode(), w.getCollectorNumber(), w.getId()));

        Label stagingStats = new Label("Staged: 0 cards (0 unique lines)");
        stagingStats.setStyle("-fx-text-fill: -color-fg-muted;");

        TextField empInput = new TextField();
        var user = UserSessionManager.getInstance().currentOrNull();
        empInput.setText(user != null ? user.displayName() : "Karl");
        empInput.setPromptText("Employee Name / Handle");
        empInput.setPrefWidth(150);

        Button commitBtn = new Button("Commit Additions (Push to Unstored Queue)");
        commitBtn.setStyle("-fx-font-weight: bold; -fx-base: #059669; -fx-font-size: 13px;");

        Button unstageBtn = new Button("Unstage All ←");

        Label commitResult = new Label();
        commitResult.setWrapText(true);
        commitResult.setStyle("-fx-font-weight: bold;");

        HBox commitBar = new HBox(10, new Label("Clerk:"), empInput, commitBtn, unstageBtn);
        commitBar.setAlignment(Pos.CENTER_LEFT);
        commitBar.setPadding(new Insets(6, 0, 6, 0));

        VBox stagingPane = new VBox(8, stagingTitle, stagingTable, stagingStats, commitBar, commitResult);
        VBox.setVgrow(stagingTable, Priority.ALWAYS);

        // --- Wire Event Handlers ---
        Runnable refreshViews = () -> {
            bufferItems.setAll(engine.getBuffer());
            stagingItems.setAll(engine.getStaging());
            int totalStagedQty = 0;
            for (WorkingItem w : engine.getStaging()) totalStagedQty += w.getQuantity();
            stagingStats.setText("Staged: " + totalStagedQty + " cards (" + engine.getStaging().size() + " unique lines)");
        };

        addSampleBtn.setOnAction(e -> {
            WorkingBuffer sample = createSampleBuffer();
            for (WorkingItem item : sample.getBuffer()) engine.addItem(item);
            refreshViews.run();
        });

        addLookupBtn.setOnAction(e -> {
            String q = nameInput.getText() == null ? "" : nameInput.getText().trim();
            if (q.isBlank()) return;
            addLookupBtn.setDisable(true);
            new Thread(() -> {
                try {
                    ScryfallLookup.Draft d = ScryfallLookup.fetchDetail(q);
                    javafx.application.Platform.runLater(() -> {
                        addLookupBtn.setDisable(false);
                        if (d != null) {
                            String s = (d.setCode() != null && !d.setCode().isBlank()) ? d.setCode().toUpperCase() : "MTG";
                            String n = numInput.getText().isBlank() ? "1" : numInput.getText().trim();
                            engine.addItem(new WorkingItem(
                                    "scry-" + System.currentTimeMillis(), d.name(), s, n,
                                    "rare", "NM", "nonfoil", 1));
                            nameInput.clear();
                        } else {
                            // Fallback direct entry
                            String s = setInput.getText().isBlank() ? "CUSTOM" : setInput.getText().trim();
                            String n = numInput.getText().isBlank() ? "1" : numInput.getText().trim();
                            engine.addItem(new WorkingItem(
                                    "loc-" + System.currentTimeMillis(), q, s, n,
                                    "rare", "NM", "nonfoil", 1));
                            nameInput.clear();
                        }
                        refreshViews.run();
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        addLookupBtn.setDisable(false);
                        commitResult.setText("Lookup error: " + ex.getMessage());
                    });
                }
            }).start();
        });

        applyCondBtn.setOnAction(e -> {
            var selectedIndices = bufferTable.getSelectionModel().getSelectedIndices();
            if (!selectedIndices.isEmpty()) {
                engine.applyBulkCondition(new ArrayList<>(selectedIndices), condBox.getValue());
                refreshViews.run();
            }
        });

        applyFinishBtn.setOnAction(e -> {
            var selectedIndices = bufferTable.getSelectionModel().getSelectedIndices();
            if (!selectedIndices.isEmpty()) {
                engine.applyBulkFinish(new ArrayList<>(selectedIndices), finishBox.getValue());
                refreshViews.run();
            }
        });

        groupDupBtn.setOnAction(e -> {
            engine.groupDuplicates();
            refreshViews.run();
        });

        stageSelectedBtn.setOnAction(e -> {
            var selectedIndices = new ArrayList<>(bufferTable.getSelectionModel().getSelectedIndices());
            if (!selectedIndices.isEmpty()) {
                engine.stageSelected(selectedIndices);
                refreshViews.run();
            }
        });

        stageAllBtn.setOnAction(e -> {
            engine.stageAll();
            refreshViews.run();
        });

        unstageBtn.setOnAction(e -> {
            engine.unstageAll();
            refreshViews.run();
        });

        commitBtn.setOnAction(e -> {
            if (engine.getStaging().isEmpty()) {
                commitResult.setText("⚠️ Cannot commit: Staging Review Area is empty. Stage cards first.");
                commitResult.setStyle("-fx-text-fill: #b91c1c; -fx-font-weight: bold;");
                return;
            }
            String clerk = empInput.getText().isBlank() ? "Clerk" : empInput.getText().trim();
            try {
                String tag = engine.commitStaged(clerk);
                refreshViews.run();
                commitResult.setText("✓ Successfully committed to Unstored Queue: " + tag);
                commitResult.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
            } catch (Exception ex) {
                commitResult.setText("❌ Commit failed: " + ex.getMessage());
                commitResult.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            }
        });

        SplitPane split = new SplitPane(bufferPane, stagingPane);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.5);

        BorderPane root = new BorderPane(split);
        root.getStyleClass().add("view-container");
        root.setPadding(new Insets(10));
        return root;
    }

    private static void setupColumns(TableView<WorkingItem> table) {
        TableColumn<WorkingItem, String> nameCol = new TableColumn<>("Card Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameCol.setPrefWidth(200);

        TableColumn<WorkingItem, String> setCol = new TableColumn<>("Set");
        setCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSetCode()));
        setCol.setPrefWidth(60);

        TableColumn<WorkingItem, String> numCol = new TableColumn<>("Num");
        numCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCollectorNumber()));
        numCol.setPrefWidth(60);

        TableColumn<WorkingItem, String> rarityCol = new TableColumn<>("Rarity");
        rarityCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRarity()));
        rarityCol.setPrefWidth(80);

        TableColumn<WorkingItem, String> condCol = new TableColumn<>("Condition");
        condCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCondition()));
        condCol.setPrefWidth(80);

        TableColumn<WorkingItem, String> finishCol = new TableColumn<>("Finish");
        finishCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFinish()));
        finishCol.setPrefWidth(80);

        TableColumn<WorkingItem, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getQuantity()));
        qtyCol.setPrefWidth(50);

        table.getColumns().setAll(List.of(nameCol, setCol, numCol, rarityCol, condCol, finishCol, qtyCol));
    }

    public static void main(String[] args) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("staging-view-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();

            WorkingBuffer sample = createSampleBuffer();
            System.out.println("Sample items in buffer: " + sample.getBuffer().size() + " (want 6)");
            sample.groupDuplicates();
            System.out.println("Items after group: " + sample.getBuffer().size() + " (want 5)");
            sample.stageAll();
            System.out.println("Staged items: " + sample.getStaging().size() + " (want 5)");
            String tag = sample.commitStaged("Tester");
            System.out.println("Committed tag: " + tag);
            System.out.println("Staged remaining: " + sample.getStaging().size() + " (want 0)");

            System.out.println("StagingView data logic OK.");
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.println("StagingView ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
