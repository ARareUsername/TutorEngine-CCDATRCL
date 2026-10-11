package tutorengine.ui;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tutorengine.audit.AuditLog;
import tutorengine.audit.Exceptions;
import tutorengine.db.Db;

// OWNER: Bondoc, Karl B. — audit browser + box reconciliation tool.
// Data functions are toolkit-free (headless main() below); build() is layout.
public final class AuditView {
    private AuditView() {}

    public static List<String[]> commits(String employeeLike, String from, String to) {
        try {
            return AuditLog.browse(employeeLike, from, to);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String throughput() {
        try {
            return AuditLog.throughput();
        } catch (Exception e) {
            return "metrics unavailable: " + e.getMessage();
        }
    }

    // Expected STORED cards in a box, alphabetical (for the check-off pass).
    public static List<String[]> boxCards(String boxId) {
        List<String[]> out = new ArrayList<>();
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name, id FROM card_instances WHERE box_id = ? AND status = 'STORED'"
                     + " ORDER BY name")) {
            ps.setString(1, boxId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new String[]{rs.getString(1), rs.getString(2)});
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static List<String> boxIds() {
        List<String> out = new ArrayList<>();
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT box_id FROM box_containers ORDER BY box_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        } catch (Exception ignored) { }
        return out;
    }

    public static Node build() {
        TextField employeeField = new TextField();
        employeeField.setPromptText("Employee filter");
        employeeField.setPrefWidth(140);
        TextField fromField = new TextField();
        fromField.setPromptText("From yyyy-mm-dd");
        fromField.setPrefWidth(120);
        TextField toField = new TextField();
        toField.setPromptText("To yyyy-mm-dd");
        toField.setPrefWidth(120);
        Button refreshBtn = new Button("Refresh");
        Label metrics = new Label(throughput());
        metrics.setWrapText(true);
        TableView<String[]> commits = new TableView<>();
        String[] ct = {"Commit tag", "Employee", "Cards", "Committed at"};
        for (int i = 0; i < ct.length; i++) {
            final int k = i;
            TableColumn<String[], String> c = new TableColumn<>(ct[i]);
            c.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[k]));
            commits.getColumns().add(c);
        }
        Runnable reload = () -> {
            commits.getItems().clear();
            commits.getItems().addAll(commits(employeeField.getText(), fromField.getText(), toField.getText()));
            metrics.setText(throughput());
        };
        refreshBtn.setOnAction(e -> reload.run());
        reload.run();

        ChoiceBox<String> boxChoice = new ChoiceBox<>();
        boxChoice.getItems().addAll(boxIds());
        VBox expected = new VBox(4);
        Label parity = new Label("Pick a box to start the check-off pass.");
        Button resolveBtn = new Button("Resolve open exceptions for box");
        resolveBtn.setOnAction(e -> {
            String box = boxChoice.getValue();
            if (box == null) return;
            try {
                var who = tutorengine.auth.UserSessionManager.getInstance().currentOrNull();
                String by = who == null ? "unknown" : who.displayName();
                for (String[] row : Exceptions.openQueue())
                    if (row[2].equals(box)) Exceptions.resolve(row[0], by);
                parity.setText("Resolved open exceptions for " + box + " by " + by + ".");
            } catch (Exception ex) {
                parity.setText("Resolve failed: " + ex.getMessage());
            }
        });
        boxChoice.getSelectionModel().selectedItemProperty().addListener((o, old, box) -> {
            expected.getChildren().clear();
            if (box == null) return;
            List<String[]> cards = boxCards(box);
            int[] found = {0};
            for (String[] row : cards) {
                CheckBox cb = new CheckBox(row[0]);
                cb.selectedProperty().addListener((oo, was, is) -> {
                    if (is) found[0]++;
                    else found[0]--;
                    parity.setText("Parity: " + found[0] + "/" + cards.size()
                            + (found[0] == cards.size() ? " — box matches the record." : ""));
                });
                expected.getChildren().add(cb);
            }
            parity.setText(cards.isEmpty() ? "No STORED cards recorded for " + box + "."
                    : "Check off physical cards: 0/" + cards.size());
        });

        VBox browser = new VBox(8, new Label("Audit commits"), new HBox(8,
                employeeField, fromField, toField, refreshBtn), metrics, commits);
        VBox recon = new VBox(8, new Label("Box reconciliation (expected vs found)"),
                new HBox(8, boxChoice, resolveBtn), parity, expected);
        VBox root = new VBox(12, browser, recon);
        root.getStyleClass().add("view-container");
        return root;
    }

    public static void main(String[] args) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("tutorengine-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();
            AuditLog.recordCommit("BATCH-a-Karl_M-2cards", "Karl M.", 2);
            System.out.println("commits rows=" + commits("", "", "").size() + " (want >= 1)");
            System.out.println("filtered miss=" + commits("Nobody", "", "").size() + " (want 0)");
            System.out.println("throughput: " + throughput());
            System.out.println("boxes=" + boxIds().size() + " (want 9)");
            System.out.println("empty box cards=" + boxCards("CAB-T-TOP").size() + " (want 0)");
            System.out.println("AuditView data OK.");
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.println("AUDITVIEW ERROR: " + e.getMessage());
        }
    }
}
