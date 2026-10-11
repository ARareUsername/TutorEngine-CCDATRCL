package tutorengine.ui;

import java.awt.GraphicsEnvironment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import tutorengine.auth.UserSessionManager;
import tutorengine.db.Db;
import tutorengine.model.CardInstance;
import tutorengine.model.StorageLocation;
import tutorengine.storage.StorageScaffolding;
import tutorengine.trip.TripServer;
import tutorengine.trip.TripSession;
import tutorengine.trip.TripSession.CardState;
import tutorengine.trip.TripSession.ExcludeReason;
import tutorengine.trip.TripSession.TripCard;

// TripView: Desktop interface for generating shelf trips, serving mobile LAN URLs,
// printing 80mm routing slips, and managing partial batch resolution (§1-§2 specs/shelf-trip-viewer/spec.md).
// OWNER: Bondoc, Karl B.
public final class TripView {
    private TripView() {}

    public static List<String[]> loadActiveSessions() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT trip_id, box_id, clerk_name, total_cards, status, created_at FROM trip_sessions ORDER BY created_at DESC LIMIT 20");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new String[]{
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        String.valueOf(rs.getInt(4)), rs.getString(5), rs.getString(6)
                });
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static boolean printSlipText(String slipText) {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Headless print slip simulation:\n" + slipText);
            return true;
        }
        try {
            java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
            job.setPrintable((graphics, pageFormat, pageIndex) -> {
                if (pageIndex > 0) return java.awt.print.Printable.NO_SUCH_PAGE;
                graphics.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 10));
                int y = 20;
                for (String line : slipText.split("\n")) {
                    graphics.drawString(line, 20, y);
                    y += 14;
                }
                return java.awt.print.Printable.PAGE_EXISTS;
            });
            if (job.printDialog()) {
                job.print();
                return true;
            }
        } catch (Throwable t) {
            System.out.println("Print dialog hook: " + t.getMessage());
        }
        return false;
    }

    public static Node build() {
        // Ensure TripServer is running
        try {
            TripServer server = TripServer.getInstance();
            if (!server.isRunning()) {
                server.start();
            }
        } catch (Exception ex) {
            System.err.println("Could not start TripServer: " + ex.getMessage());
        }

        ObservableList<String[]> sessionRows = FXCollections.observableArrayList();
        ObservableList<TripCard> cardRows = FXCollections.observableArrayList();
        TripSession[] currentSession = new TripSession[1];

        // --- Left Pane: Recent Trips & Session Builder ---
        Label leftTitle = new Label("1. Shelf Filing Trips (LAN & Mobile Sessions)");
        leftTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        TableView<String[]> sessionTable = new TableView<>(sessionRows);
        setupSessionColumns(sessionTable);

        // Builder bar
        ComboBox<String> boxChoice = new ComboBox<>();
        for (StorageLocation s : StorageLocation.slots()) boxChoice.getItems().add(s.getId());
        if (!boxChoice.getItems().isEmpty()) boxChoice.setValue(boxChoice.getItems().get(0));

        Button newTripBtn = new Button("+ Build Trip for Box");
        newTripBtn.setStyle("-fx-font-weight: bold; -fx-base: #0284c7;");

        HBox builderRow = new HBox(8, new Label("Box:"), boxChoice, newTripBtn);
        builderRow.setAlignment(Pos.CENTER_LEFT);

        Button refreshSessionsBtn = new Button("↻ Refresh Trips");

        VBox leftPane = new VBox(8, leftTitle, builderRow, refreshSessionsBtn, sessionTable);
        leftPane.setPrefWidth(380);
        VBox.setVgrow(sessionTable, Priority.ALWAYS);

        // --- Right Pane: Active Trip Walkthrough & Actions ---
        Label rightTitle = new Label("Select or generate a trip to view card sequence");
        rightTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // LAN Mobile Link bar
        TextField urlField = new TextField();
        urlField.setEditable(false);
        urlField.setPrefWidth(300);
        urlField.setStyle("-fx-font-family: monospace; -fx-font-weight: bold;");

        Button copyUrlBtn = new Button("Copy URL");
        Button printSlipBtn = new Button("🖨 Print 80mm Slip");
        printSlipBtn.setStyle("-fx-font-weight: bold; -fx-base: #475569;");

        HBox urlBar = new HBox(8, new Label("Mobile LAN URL:"), urlField, copyUrlBtn, printSlipBtn);
        urlBar.setAlignment(Pos.CENTER_LEFT);
        urlBar.setPadding(new Insets(6, 8, 6, 8));
        urlBar.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-width: 1px; -fx-border-radius: 4px;");

        // Directive Banner
        Label directiveBanner = new Label("📋 IN-HAND FORWARD-PASS DIRECTIVE: Arrange cards strictly 1..N (Name ASC) before walking to shelf.");
        directiveBanner.setWrapText(true);
        directiveBanner.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-padding: 8px;"
                + "-fx-border-color: #f59e0b; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-font-weight: bold;");

        // Cards Table
        TableView<TripCard> cardTable = new TableView<>(cardRows);
        setupCardColumns(cardTable);
        HoverPreviewHelper.attach(cardTable, TripCard::getCardName, TripCard::getSetCode, TripCard::getCardId);

        // Bottom Actions Bar
        Button checkCardBtn = new Button("✓ Toggle Check");
        Button excludeCardBtn = new Button("⚠️ Exclude Card...");
        Button confirmTripBtn = new Button("Complete Trip Batch (Save to Inventory)");
        confirmTripBtn.setStyle("-fx-font-weight: bold; -fx-base: #059669; -fx-padding: 8px 16px;");

        Label actionResult = new Label();
        actionResult.setStyle("-fx-font-weight: bold;");

        HBox actionRow = new HBox(10, checkCardBtn, excludeCardBtn, new Separator(javafx.geometry.Orientation.VERTICAL), confirmTripBtn, actionResult);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.setPadding(new Insets(6, 0, 6, 0));

        VBox rightPane = new VBox(8, rightTitle, urlBar, directiveBanner, cardTable, actionRow);
        VBox.setVgrow(cardTable, Priority.ALWAYS);
        rightPane.setPadding(new Insets(0, 0, 0, 8));

        // --- Controller Logic ---
        Runnable reloadSessions = () -> {
            sessionRows.setAll(loadActiveSessions());
            if (!sessionRows.isEmpty() && currentSession[0] == null) {
                sessionTable.getSelectionModel().select(0);
            }
        };

        refreshSessionsBtn.setOnAction(e -> reloadSessions.run());

        sessionTable.getSelectionModel().selectedItemProperty().addListener((o, oldRow, sel) -> {
            if (sel == null) return;
            String tripId = sel[0];
            try {
                TripSession s = TripSession.load(tripId);
                if (s != null) {
                    currentSession[0] = s;
                    rightTitle.setText("Trip Session: " + s.getBoxLabel() + " (" + s.getShelfUnit() + " " + s.getShelfTier() + ") - Status: " + s.getStatus());
                    urlField.setText(TripServer.getInstance().getUrl(s.getTripId()));
                    cardRows.setAll(s.getCards());
                    confirmTripBtn.setDisable(s.getStatus() != TripSession.SessionStatus.ACTIVE);
                    checkCardBtn.setDisable(s.getStatus() != TripSession.SessionStatus.ACTIVE);
                    excludeCardBtn.setDisable(s.getStatus() != TripSession.SessionStatus.ACTIVE);
                }
            } catch (Exception ex) {
                actionResult.setText("Error loading trip: " + ex.getMessage());
            }
        });

        newTripBtn.setOnAction(e -> {
            String targetBox = boxChoice.getValue();
            if (targetBox == null) return;
            var user = UserSessionManager.getInstance().currentOrNull();
            String clerk = (user != null) ? user.displayName() : "Karl";

            try {
                // Fetch unstored cards for this box
                List<CardInstance> unstored = new ArrayList<>();
                try (Connection c = Db.open();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, language, status, box_id, commit_id, created_at, stored_at "
                             + "FROM card_instances WHERE status = 'UNSTORED'")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            CardInstance ci = new CardInstance(
                                    rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                                    rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                                    rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                                    rs.getString(13), rs.getString(14));
                            if (StorageScaffolding.routeInstance(ci).equalsIgnoreCase(targetBox)) {
                                unstored.add(ci);
                            }
                        }
                    }
                }

                if (unstored.isEmpty()) {
                    actionResult.setText("⚠️ No UNSTORED cards queued for box " + targetBox + ".");
                    actionResult.setStyle("-fx-text-fill: #b91c1c;");
                    return;
                }

                TripSession newSession = TripSession.create(targetBox, clerk, unstored);
                actionResult.setText("✓ Generated trip session for " + targetBox + " (" + newSession.getCards().size() + " cards)");
                actionResult.setStyle("-fx-text-fill: #059669;");
                reloadSessions.run();
                sessionTable.getSelectionModel().select(0);
            } catch (Exception ex) {
                actionResult.setText("Failed to build trip: " + ex.getMessage());
                actionResult.setStyle("-fx-text-fill: #dc2626;");
            }
        });

        copyUrlBtn.setOnAction(e -> {
            String u = urlField.getText();
            if (u != null && !u.isBlank()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(u);
                Clipboard.getSystemClipboard().setContent(content);
                actionResult.setText("✓ Copied URL to clipboard!");
                actionResult.setStyle("-fx-text-fill: #059669;");
            }
        });

        printSlipBtn.setOnAction(e -> {
            if (currentSession[0] == null) return;
            String slip = TripSession.renderPlainSlip(currentSession[0]);

            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle("Print Routing Slip (80mm Preview)");
            dlg.setHeaderText("Plain-Text Slip Preview for " + currentSession[0].getBoxLabel());

            TextArea ta = new TextArea(slip);
            ta.setEditable(false);
            ta.setWrapText(false);
            ta.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
            ta.setPrefSize(400, 420);

            dlg.getDialogPane().setContent(ta);
            ButtonType printBtnType = new ButtonType("Print to System Printer", ButtonData.OK_DONE);
            dlg.getDialogPane().getButtonTypes().addAll(printBtnType, ButtonType.CANCEL);

            DialogHelper.showAndWait(dlg).ifPresent(bt -> {
                if (bt == printBtnType) {
                    boolean ok = printSlipText(slip);
                    actionResult.setText(ok ? "✓ Sent slip to print dialog" : "Print cancelled");
                }
            });
        });

        checkCardBtn.setOnAction(e -> {
            TripCard tc = cardTable.getSelectionModel().getSelectedItem();
            if (tc == null || currentSession[0] == null) return;
            try {
                TripSession.toggleCheck(currentSession[0].getTripId(), tc.getCardId());
                TripSession refreshed = TripSession.load(currentSession[0].getTripId());
                currentSession[0] = refreshed;
                cardRows.setAll(refreshed.getCards());
            } catch (Exception ex) {
                actionResult.setText("Toggle error: " + ex.getMessage());
            }
        });

        excludeCardBtn.setOnAction(e -> {
            TripCard tc = cardTable.getSelectionModel().getSelectedItem();
            if (tc == null || currentSession[0] == null) return;

            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle("Exclude Card from Batch");
            dlg.setHeaderText("Exclude " + tc.getCardName() + " (will return flagged to Unstored Queue)");

            GridPane grid = new GridPane();
            grid.setHgap(8);
            grid.setVgap(8);
            grid.setPadding(new Insets(10));

            ComboBox<ExcludeReason> reasonBox = new ComboBox<>(FXCollections.observableArrayList(ExcludeReason.values()));
            reasonBox.setValue(ExcludeReason.BOX_FULL);

            TextField notesField = new TextField();
            notesField.setPromptText("Notes / explanation");

            grid.add(new Label("Reason:"), 0, 0);
            grid.add(reasonBox, 1, 0);
            grid.add(new Label("Notes:"), 0, 1);
            grid.add(notesField, 1, 1);

            dlg.getDialogPane().setContent(grid);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            DialogHelper.showAndWait(dlg).ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    try {
                        TripSession.excludeCard(currentSession[0].getTripId(), tc.getCardId(), reasonBox.getValue(), notesField.getText());
                        TripSession refreshed = TripSession.load(currentSession[0].getTripId());
                        currentSession[0] = refreshed;
                        cardRows.setAll(refreshed.getCards());
                        actionResult.setText("⚠️ Card excluded: " + tc.getCardName() + " (" + reasonBox.getValue() + ")");
                        actionResult.setStyle("-fx-text-fill: #b91c1c;");
                    } catch (Exception ex) {
                        actionResult.setText("Exclude error: " + ex.getMessage());
                    }
                }
            });
        });

        confirmTripBtn.setOnAction(e -> {
            if (currentSession[0] == null) return;
            try {
                int stored = TripSession.confirmCompletion(currentSession[0].getTripId());
                TripSession refreshed = TripSession.load(currentSession[0].getTripId());
                currentSession[0] = refreshed;
                cardRows.setAll(refreshed.getCards());
                rightTitle.setText("Trip Session: " + refreshed.getBoxLabel() + " - Status: " + refreshed.getStatus());
                confirmTripBtn.setDisable(true);
                actionResult.setText("✓ Finished trip! Confirmed " + stored + " stored into " + refreshed.getBoxId() + " (Status: " + refreshed.getStatus() + ")");
                actionResult.setStyle("-fx-text-fill: #059669;");
                reloadSessions.run();
            } catch (Exception ex) {
                actionResult.setText("Completion error: " + ex.getMessage());
                actionResult.setStyle("-fx-text-fill: #dc2626;");
            }
        });

        reloadSessions.run();

        SplitPane split = new SplitPane(leftPane, rightPane);
        split.setDividerPositions(0.38);

        BorderPane root = new BorderPane(split);
        root.setPadding(new Insets(10));
        return root;
    }

    private static void setupSessionColumns(TableView<String[]> table) {
        TableColumn<String[], String> boxCol = new TableColumn<>("Box");
        boxCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
        boxCol.setPrefWidth(95);

        TableColumn<String[], String> clerkCol = new TableColumn<>("Clerk");
        clerkCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));
        clerkCol.setPrefWidth(75);

        TableColumn<String[], String> cardsCol = new TableColumn<>("Cards");
        cardsCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[3]));
        cardsCol.setPrefWidth(50);

        TableColumn<String[], String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[4]));
        statusCol.setPrefWidth(75);

        table.getColumns().setAll(List.of(boxCol, clerkCol, cardsCol, statusCol));
    }

    private static void setupCardColumns(TableView<TripCard> table) {
        TableColumn<TripCard, Number> numCol = new TableColumn<>("#");
        numCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getSortOrder()));
        numCol.setPrefWidth(35);

        TableColumn<TripCard, String> nameCol = new TableColumn<>("Card Name (ASC)");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCardName()));
        nameCol.setPrefWidth(180);

        TableColumn<TripCard, String> setCol = new TableColumn<>("Set");
        setCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSetCode()));
        setCol.setPrefWidth(50);

        TableColumn<TripCard, String> dividerCol = new TableColumn<>("Divider Guidance");
        dividerCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDividerGuidance()));
        dividerCol.setPrefWidth(220);

        TableColumn<TripCard, String> stateCol = new TableColumn<>("State");
        stateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getState() == CardState.EXCLUDED
                        ? "EXCLUDED (" + c.getValue().getExcludeReason() + ")"
                        : c.getValue().getState().name()
        ));
        stateCol.setPrefWidth(120);

        table.getColumns().setAll(List.of(numCol, nameCol, setCol, dividerCol, stateCol));
    }

    public static void main(String[] args) {
        System.out.println("=== TripView Headless Demo ===");
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("trip-view-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();

            List<String[]> sessions = loadActiveSessions();
            System.out.println("Active sessions loaded: " + sessions.size() + " (want 0)");

            boolean printed = printSlipText("SAMPLE SLIP\nTrip: RACK-M-TOP\nCards: 6");
            System.out.println("Slip printed hook result: " + printed);

            java.nio.file.Files.deleteIfExists(tmp);
            System.out.println("TripView data logic OK.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
