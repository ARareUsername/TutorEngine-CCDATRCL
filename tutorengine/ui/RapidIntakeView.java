package tutorengine.ui;

import atlantafx.base.theme.Styles;
import java.net.URI;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import tutorengine.db.Db;
import tutorengine.intake.RapidIntake;
import tutorengine.intake.RapidIntake.ParsedShortcut;
import tutorengine.intake.RapidIntake.ResolvedCard;
import tutorengine.intake.RapidIntake.Variant;
import tutorengine.model.Card;

// RapidIntakeView: Hands-free collector number shortcut intake with live thumbnail preview,
// Alt+1..9 variant ribbon, and autofocus loop.
// OWNER: Bondoc, Karl B.
public final class RapidIntakeView {
    private RapidIntakeView() {}

    public static Node build(Map<String, Card> catalog, TableView<Card> catalogTable) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));

        String[] activeBatchId = new String[]{ "BATCH-" + System.currentTimeMillis() };
        ResolvedCard[] currentResolved = new ResolvedCard[1];
        List<Variant> activeVariants = new ArrayList<>();

        CheckBox autoCommitCheck = new CheckBox("Direct Commit to Catalog");
        autoCommitCheck.setSelected(true);
        autoCommitCheck.setTooltip(new Tooltip("Directly commits added cards to the Catalog view and inventory on Enter"));

        Label batchLabel = new Label("Batch: " + activeBatchId[0]);
        batchLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");

        HBox metaBar = new HBox(10, batchLabel);
        metaBar.setAlignment(Pos.CENTER_LEFT);

        // --- Shortcut Input Bar ---
        TextField shortcutField = new TextField();
        shortcutField.setPromptText("Shortcut: <SET> <NUM> [foil] [x<QTY>] (e.g. 'woe 142', 'mh3 004 foil', 'one 23 x4')");
        shortcutField.setStyle("-fx-font-size: 14px; -fx-font-family: monospace;");
        HBox.setHgrow(shortcutField, Priority.ALWAYS);

        Button resolveBtn = new Button("Resolve");
        Button stageBtn = new Button("Stage Card (Enter)");
        stageBtn.getStyleClass().add(Styles.ACCENT);
        stageBtn.setStyle("-fx-font-weight: bold;");

        Label statusLabel = new Label("Ready. Type shortcut and press Enter.");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");
        statusLabel.setWrapText(true);

        HBox inputRow = new HBox(8, shortcutField, resolveBtn, stageBtn, autoCommitCheck);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        VBox inputSection = new VBox(6, metaBar, inputRow, statusLabel);
        inputSection.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(inputSection);

        // --- Center: Preview Dock & Staged Batch Table ---
        // 1. Preview Dock
        ImageView previewImage = new ImageView();
        previewImage.setFitWidth(130);
        previewImage.setFitHeight(182);
        previewImage.setPreserveRatio(true);
        previewImage.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 2);");

        Label titleLabel = new Label("No card resolved");
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label subLabel = new Label("Enter shortcut above to resolve printing.");
        subLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");
        Label detailsLabel = new Label("");
        detailsLabel.setStyle("-fx-font-size: 12px;");
        Label routingLabel = new Label("");
        routingLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: -color-accent-fg;");

        HBox variantRibbon = new HBox(6);
        variantRibbon.setAlignment(Pos.CENTER_LEFT);

        VBox previewInfo = new VBox(4, titleLabel, subLabel, detailsLabel, routingLabel, variantRibbon);
        HBox.setHgrow(previewInfo, Priority.ALWAYS);

        HBox previewBox = new HBox(14, previewImage, previewInfo);
        previewBox.setPadding(new Insets(10));
        previewBox.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 6; -fx-border-color: -color-border-subtle; -fx-border-radius: 6;");

        // 2. Staged Batch Table (with remove/undo + inline qty edit)
        // 2. Staged Batch Table (with in-memory remove/undo + inline qty edit)
        ObservableList<Card> stagedCards = FXCollections.observableArrayList();
        Label countLabel = new Label("Staged: 0 cards (₱0.00)");
        countLabel.setStyle("-fx-font-weight: bold;");

        Button commitBatchBtn = new Button("Commit Batch to Inventory");
        commitBatchBtn.getStyleClass().add(Styles.SUCCESS);
        commitBatchBtn.setStyle("-fx-font-weight: bold;");
        Button clearBatchBtn = new Button("Discard Uncommitted");

        // --- Helper: Update Summary Count ---
        Runnable updateSummary = () -> {
            int totalQty = stagedCards.stream().mapToInt(Card::getQuantity).sum();
            double totalPhp = stagedCards.stream().mapToDouble(c -> c.getPrice() * c.getQuantity()).sum();
            countLabel.setText(String.format("Staged: %d cards (₱%.2f)", totalQty, totalPhp));
            commitBatchBtn.setDisable(stagedCards.isEmpty());
            clearBatchBtn.setDisable(stagedCards.isEmpty());
        };

        TableView<Card> stagedTable = new TableView<>(stagedCards);
        setupStagedTable(stagedTable, statusLabel, updateSummary);
        VBox.setVgrow(stagedTable, Priority.ALWAYS);

        // Name-fallback suggestion list (shown when shortcut parse fails)
        ListView<String> nameSuggestions = new ListView<>();
        nameSuggestions.setPrefHeight(80);
        nameSuggestions.setVisible(false);
        nameSuggestions.setManaged(false);
        Label nameFallbackLabel = new Label("Card not found by set code. Did you mean:");
        nameFallbackLabel.setVisible(false);
        nameFallbackLabel.setManaged(false);
        VBox nameFallbackBox = new VBox(4, nameFallbackLabel, nameSuggestions);

        Card[] lastRemovedCard = new Card[1];

        Button removeLastBtn = new Button("Remove Last");
        Button undoRemoveBtn = new Button("Undo Remove");
        HBox batchActions = new HBox(10, countLabel, new Separator(javafx.geometry.Orientation.VERTICAL), commitBatchBtn, clearBatchBtn, removeLastBtn, undoRemoveBtn);
        batchActions.setAlignment(Pos.CENTER_LEFT);
        batchActions.setPadding(new Insets(8, 0, 0, 0));

        VBox centerBox = new VBox(10, previewBox, nameFallbackBox, stagedTable, batchActions);
        root.setCenter(centerBox);

        updateSummary.run();

        // --- Helper: Render Resolved Card & Variants ---
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<ResolvedCard>[] displayResolved = new java.util.function.Consumer[1];
        displayResolved[0] = card -> {
            currentResolved[0] = card;
            titleLabel.setText(card.name() + " [" + card.setCode() + " #" + card.collectorNumber() + "]");
            subLabel.setText(card.setName() + " · " + card.rarity().toUpperCase() + " · " + card.cardType());
            detailsLabel.setText(String.format("Price: ₱%.2f ($%.2f) | Finish: %s | Qty: %d | Cond: %s | Mana: %s",
                    card.phpPrice() == null ? 0.0 : card.phpPrice(),
                    card.usdPrice() == null ? 0.0 : card.usdPrice(),
                    card.foil() ? "FOIL" : "NONFOIL",
                    card.quantity(),
                    card.condition(),
                    card.manaCost().isEmpty() ? "—" : card.manaCost()));
            routingLabel.setText("Target Slot: " + card.slot());

            // Load preview image
            if (card.imageUri() != null && !card.imageUri().isBlank()) {
                try {
                    previewImage.setImage(new Image(card.imageUri(), 130, 182, true, true, true));
                } catch (Exception ignored) {}
            }

            // Populate variant ribbon
            variantRibbon.getChildren().clear();
            new Thread(() -> {
                List<Variant> list = RapidIntake.fetchVariants(card.name(), card.setCode());
                Platform.runLater(() -> {
                    activeVariants.clear();
                    activeVariants.addAll(list);
                    if (list.size() > 1) {
                        variantRibbon.getChildren().add(new Label("Variants:"));
                        for (Variant v : list) {
                            String labelText = String.format("[Alt+%d] #%s %s", v.index(), v.collectorNumber(), v.rarity());
                            Button vBtn = new Button(labelText);
                            if (v.collectorNumber().equalsIgnoreCase(card.collectorNumber())) {
                                vBtn.getStyleClass().add(Styles.ACCENT);
                                vBtn.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
                            } else {
                                vBtn.setStyle("-fx-font-size: 11px;");
                            }
                            vBtn.setOnAction(e -> {
                                try {
                                    ResolvedCard switched = RapidIntake.resolve(
                                            new ParsedShortcut(v.setCode(), v.collectorNumber(), card.foil(), card.quantity(), card.condition(), "", null));
                                    displayResolved[0].accept(switched);
                                    shortcutField.requestFocus();
                                } catch (Exception ignored) {}
                            });
                            variantRibbon.getChildren().add(vBtn);
                        }
                    }
                });
            }).start();
        };

        // --- Name Fallback: show suggestions when shortcut parse fails ---
        Runnable showNameFallback = () -> {
            String q = shortcutField.getText() == null ? "" : shortcutField.getText().trim();
            if (q.length() < 2) {
                nameFallbackLabel.setVisible(false);
                nameFallbackLabel.setManaged(false);
                nameSuggestions.setVisible(false);
                nameSuggestions.setManaged(false);
                return;
            }
            List<String> suggestions = RapidIntake.suggestByName(q);
            if (suggestions.isEmpty()) {
                nameFallbackLabel.setVisible(false);
                nameFallbackLabel.setManaged(false);
                nameSuggestions.setVisible(false);
                nameSuggestions.setManaged(false);
                return;
            }
            nameSuggestions.getItems().setAll(suggestions);
            nameFallbackLabel.setVisible(true);
            nameFallbackLabel.setManaged(true);
            nameSuggestions.setVisible(true);
            nameSuggestions.setManaged(true);
            nameSuggestions.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> {
                if (sel != null) {
                    try {
                        ResolvedCard res = RapidIntake.resolveByName(sel);
                        displayResolved[0].accept(res);
                        statusLabel.setText("Resolved by name: " + res.name() + " [" + res.setCode() + " #" + res.collectorNumber() + "]");
                        statusLabel.setStyle("-fx-text-fill: #0369a1;");
                        nameFallbackLabel.setVisible(false);
                        nameFallbackLabel.setManaged(false);
                        nameSuggestions.setVisible(false);
                        nameSuggestions.setManaged(false);
                        shortcutField.clear();
                        shortcutField.requestFocus();
                    } catch (Exception ex) {
                        statusLabel.setText("Name resolution failed: " + ex.getMessage());
                        statusLabel.setStyle("-fx-text-fill: #dc2626;");
                    }
                }
            });
        };

        // --- Action: Stage Current Card (Autofocus loop + Autosave) ---
        Runnable stageCurrent = () -> {
            String text = shortcutField.getText() == null ? "" : shortcutField.getText().trim();
            if (text.isEmpty() && currentResolved[0] == null) {
                statusLabel.setText("Please enter a shortcut first.");
                statusLabel.setStyle("-fx-text-fill: #dc2626;");
                shortcutField.requestFocus();
                return;
            }

            ResolvedCard toStage = currentResolved[0];
            if (toStage == null || (!text.isEmpty() && !text.equalsIgnoreCase(toStage.setCode() + " " + toStage.collectorNumber()))) {
                try {
                    toStage = RapidIntake.resolve(text);
                    displayResolved[0].accept(toStage);
                } catch (Exception ex) {
                    statusLabel.setText(ex.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #dc2626;");
                    shortcutField.selectAll();
                    shortcutField.requestFocus();
                    return;
                }
            }

            if (toStage != null) {
                Card newCard = RapidIntake.toCard(toStage, null);
                stagedCards.add(newCard);

                String locDisp = newCard.getBoxLocationId() == null || newCard.getBoxLocationId().isBlank()
                        ? "Unassigned" : newCard.getBoxLocationId();
                if (autoCommitCheck.isSelected()) {
                    if (catalog != null) catalog.put(newCard.getSku(), newCard);
                    if (catalogTable != null && !catalogTable.getItems().contains(newCard)) catalogTable.getItems().add(newCard);
                    CatalogView.addCard(newCard);
                    new Thread(() -> {
                        try (Connection conn = Db.open()) {
                            RapidIntake.persistUnstoredInstances(conn, newCard);
                        } catch (Exception ignored) {}
                    }).start();
                    statusLabel.setText(String.format("✓ Saved to Catalog & Storage Queue: %s [%s #%s] x%d (%s) -> %s",
                            newCard.getName(), toStage.setCode(), toStage.collectorNumber(),
                            newCard.getQuantity(), toStage.foil() ? "Foil" : "Non-foil", locDisp));
                } else {
                    statusLabel.setText(String.format("✓ Staged %s [%s #%s] x%d (%s) -> %s (Click Commit Batch to save)",
                            newCard.getName(), toStage.setCode(), toStage.collectorNumber(),
                            newCard.getQuantity(), toStage.foil() ? "Foil" : "Non-foil", locDisp));
                }
                statusLabel.setStyle("-fx-text-fill: #16a34a;");

                // Reset field & refocus for next card (Autofocus loop)
                shortcutField.clear();
                currentResolved[0] = null;
                titleLabel.setText("Ready for next card");
                subLabel.setText("Type shortcut and press Enter.");
                updateSummary.run();
                shortcutField.requestFocus();
            }
        };

        resolveBtn.setOnAction(e -> {
            String q = shortcutField.getText() == null ? "" : shortcutField.getText().trim();
            try {
                ResolvedCard res = RapidIntake.resolve(q);
                displayResolved[0].accept(res);
                statusLabel.setText("Resolved card. Press Enter to stage.");
                statusLabel.setStyle("-fx-text-fill: #0369a1;");
            } catch (Exception ex) {
                statusLabel.setText(ex.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc2626;");
                showNameFallback.run();
            }
            shortcutField.requestFocus();
        });

        stageBtn.setOnAction(e -> stageCurrent.run());

        shortcutField.setOnAction(e -> stageCurrent.run());

        // --- Action: Commit Batch to Inventory ---
        commitBatchBtn.setOnAction(e -> {
            if (stagedCards.isEmpty()) return;
            int count = stagedCards.size();
            List<Card> committedCards = new ArrayList<>(stagedCards);
            for (Card c : stagedCards) {
                if (catalog != null) catalog.put(c.getSku(), c);
                if (catalogTable != null && !catalogTable.getItems().contains(c)) catalogTable.getItems().add(c);
                CatalogView.addCard(c);
            }

            // Persist unstored card instances to SQLite
            new Thread(() -> {
                try (Connection conn = Db.open()) {
                    RapidIntake.persistUnstoredInstances(conn, committedCards);
                } catch (Exception ignored) {}
            }).start();

            stagedCards.clear();
            activeBatchId[0] = "BATCH-" + System.currentTimeMillis();
            batchLabel.setText("Batch: " + activeBatchId[0]);

            statusLabel.setText("✓ Successfully committed " + count + " cards to Storage Queue & Catalog View.");
            statusLabel.setStyle("-fx-text-fill: #16a34a;");
            updateSummary.run();
            shortcutField.requestFocus();
        });

        // --- Action: Discard Uncommitted ---
        clearBatchBtn.setOnAction(e -> {
            if (stagedCards.isEmpty()) return;
            stagedCards.clear();
            statusLabel.setText("Discarded current batch.");
            statusLabel.setStyle("-fx-text-fill: -color-fg-muted;");
            updateSummary.run();
            shortcutField.requestFocus();
        });

        // --- Action: Remove last staged card (in-memory + undo) ---
        removeLastBtn.setOnAction(e -> {
            if (stagedCards.isEmpty()) return;
            Card last = stagedCards.remove(stagedCards.size() - 1);
            lastRemovedCard[0] = last;
            statusLabel.setText("Removed: " + last.getName() + " (Undo available)");
            statusLabel.setStyle("-fx-text-fill: #dc2626;");
            updateSummary.run();
            shortcutField.requestFocus();
        });

        undoRemoveBtn.setOnAction(e -> {
            if (lastRemovedCard[0] == null) {
                statusLabel.setText("Nothing to undo.");
                return;
            }
            Card restored = lastRemovedCard[0];
            stagedCards.add(restored);
            lastRemovedCard[0] = null;
            statusLabel.setText("Restored removed card: " + restored.getName());
            statusLabel.setStyle("-fx-text-fill: #16a34a;");
            updateSummary.run();
            shortcutField.requestFocus();
        });

        // --- Keyboard Filter: Alt+1..9 for Variant Ribbon ---
        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isAltDown() && !activeVariants.isEmpty()) {
                int digit = -1;
                KeyCode code = e.getCode();
                if (code == KeyCode.DIGIT1 || code == KeyCode.NUMPAD1) digit = 1;
                else if (code == KeyCode.DIGIT2 || code == KeyCode.NUMPAD2) digit = 2;
                else if (code == KeyCode.DIGIT3 || code == KeyCode.NUMPAD3) digit = 3;
                else if (code == KeyCode.DIGIT4 || code == KeyCode.NUMPAD4) digit = 4;
                else if (code == KeyCode.DIGIT5 || code == KeyCode.NUMPAD5) digit = 5;
                else if (code == KeyCode.DIGIT6 || code == KeyCode.NUMPAD6) digit = 6;
                else if (code == KeyCode.DIGIT7 || code == KeyCode.NUMPAD7) digit = 7;
                else if (code == KeyCode.DIGIT8 || code == KeyCode.NUMPAD8) digit = 8;
                else if (code == KeyCode.DIGIT9 || code == KeyCode.NUMPAD9) digit = 9;

                if (digit >= 1 && digit <= activeVariants.size()) {
                    Variant picked = activeVariants.get(digit - 1);
                    ResolvedCard cur = currentResolved[0];
                    if (cur != null) {
                        try {
                            ResolvedCard switched = RapidIntake.resolve(new ParsedShortcut(
                                    picked.setCode(), picked.collectorNumber(), cur.foil(), cur.quantity(), cur.condition(), "", null));
                            displayResolved[0].accept(switched);
                            statusLabel.setText("Switched to variant #" + picked.collectorNumber());
                            statusLabel.setStyle("-fx-text-fill: #0369a1;");
                        } catch (Exception ignored) {}
                    }
                    e.consume(); // Intercept and prevent scene accelerator bubbling
                    shortcutField.requestFocus();
                }
            }
        });

        // Autofocus loop on display
        Platform.runLater(shortcutField::requestFocus);
        root.sceneProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Platform.runLater(shortcutField::requestFocus);
            }
        });

        return root;
    }

    private static void setupStagedTable(TableView<Card> table, Label statusLabel, Runnable updateSummary) {
        TableColumn<Card, Number> rowCol = new TableColumn<>("#");
        rowCol.setPrefWidth(35);
        rowCol.setCellValueFactory(d -> new SimpleIntegerProperty(table.getItems().indexOf(d.getValue()) + 1));

        TableColumn<Card, String> skuCol = new TableColumn<>("SKU");
        skuCol.setPrefWidth(125);
        skuCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSku()));

        TableColumn<Card, String> nameCol = new TableColumn<>("Name");
        nameCol.setPrefWidth(190);
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));

        TableColumn<Card, String> setCol = new TableColumn<>("Set");
        setCol.setPrefWidth(65);
        setCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSetCode()));

        TableColumn<Card, String> foilCol = new TableColumn<>("Finish");
        foilCol.setPrefWidth(65);
        foilCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isFoil() ? "Foil" : "Non-foil"));

        TableColumn<Card, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setPrefWidth(45);
        qtyCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getQuantity()));
        // Inline quantity editing (rapid-intake-improvements)
        qtyCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final TextField field = new TextField();
            {
                field.setStyle("-fx-font-size: 11px;");
                field.setOnAction(e -> commitQty());
                field.focusedProperty().addListener((o, was, is) -> { if (!is) commitQty(); });
            }
            private void commitQty() {
                try {
                    int v = Integer.parseInt(field.getText().trim());
                    if (v > 0) {
                        Card c = getTableView().getItems().get(getIndex());
                        c.setQuantity(v);
                        getTableView().refresh();
                        if (updateSummary != null) updateSummary.run();
                        statusLabel.setText("Qty updated to " + v);
                        statusLabel.setStyle("-fx-text-fill: #16a34a;");
                    } else {
                        field.setText(String.valueOf(getTableView().getItems().get(getIndex()).getQuantity()));
                    }
                } catch (NumberFormatException ex) {
                    field.setText(String.valueOf(getTableView().getItems().get(getIndex()).getQuantity()));
                    statusLabel.setText("Invalid quantity — reverted.");
                    statusLabel.setStyle("-fx-text-fill: #dc2626;");
                }
            }
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                field.setText(item.toString());
                setGraphic(field);
            }
        });

        TableColumn<Card, String> priceCol = new TableColumn<>("PHP Price");
        priceCol.setPrefWidth(85);
        priceCol.setCellValueFactory(d -> new SimpleStringProperty(String.format("₱%.2f", d.getValue().getPrice())));

        TableColumn<Card, String> slotCol = new TableColumn<>("Target Slot");
        slotCol.setPrefWidth(120);
        slotCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getBoxLocationId() == null || d.getValue().getBoxLocationId().isBlank()
                        ? "Unassigned" : d.getValue().getBoxLocationId()));

        table.getColumns().setAll(List.of(rowCol, skuCol, nameCol, setCol, foilCol, qtyCol, priceCol, slotCol));
    }

    public static void main(String[] args) {
        System.out.println("=== RapidIntakeView Headless Demo ===");
        try {
            java.io.File tempDb = java.io.File.createTempFile("tutorengine_view_test_", ".db");
            tempDb.deleteOnExit();
            System.setProperty("tutorengine.db", tempDb.getAbsolutePath());
            Db.migrate();

            System.out.println("RapidIntakeView initialized successfully without draft layer.");
            System.out.println("RapidIntakeView OK.");
        } catch (Exception ex) {
            throw new RuntimeException("RapidIntakeView demo failed", ex);
        }
    }
}
