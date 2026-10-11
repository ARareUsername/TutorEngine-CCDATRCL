package tutorengine.ui;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import tutorengine.auth.Employees;
import tutorengine.auth.UserSessionManager;

// OWNER: Bondoc, Karl B. — login/lock/setup forms. DB calls run on the UI
// thread today (SQLite local calls are sub-millisecond at counter scale).
public final class AuthView {
    private AuthView() {}

    public static boolean validPinShape(String s) {
        return s != null && s.matches("\\d{4}");
    }

    // Profile grid + PIN pad + badge field. onSuccess fires once per login.
    public static Node loginForm(Runnable onSuccess) {
        Label title = new Label("Select Active Employee Profile");
        title.getStyleClass().add("view-title");
        FlowPane grid = new FlowPane(12, 12);
        grid.setAlignment(Pos.CENTER);
        Label selected = new Label("Selected: (none)");
        String[] picked = new String[1];
        try {
            List<Employees.Employee> actives = Employees.listActive();
            if (actives.isEmpty()) selected.setText("No active employees — register one first.");
            for (Employees.Employee e : actives) {
                Button tile = profileTile(e);
                tile.setOnAction(ev -> {
                    picked[0] = e.username();
                    selected.setText("Selected: " + e.displayName() + " (" + e.role() + ")");
                });
                grid.getChildren().add(tile);
            }
        } catch (Exception ex) {
            selected.setText("Employee list unavailable: " + ex.getMessage());
        }

        PasswordField pin = new PasswordField();
        pin.setPromptText("4-digit PIN");
        pin.setMaxWidth(160);
        TextField badge = new TextField();
        badge.setPromptText("Scan badge or type barcode + Enter");
        badge.setMaxWidth(260);
        Label error = new Label("");
        error.setWrapText(true);

        GridPane pad = new GridPane();
        pad.setHgap(6);
        pad.setVgap(6);
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"CLR", "0", "⌫"}};
        for (int r = 0; r < keys.length; r++)
            for (int c = 0; c < keys[r].length; c++) {
                Button k = new Button(keys[r][c]);
                k.setPrefWidth(52);
                final String key = keys[r][c];
                k.setOnAction(ev -> {
                    if (key.equals("CLR")) pin.clear();
                    else if (key.equals("⌫")) {
                        String t = pin.getText();
                        if (!t.isEmpty()) pin.setText(t.substring(0, t.length() - 1));
                    } else if (pin.getText().length() < 12) pin.appendText(key);
                });
                pad.add(k, c, r);
            }

        Button loginBtn = new Button("Log In / Start Shift");
        loginBtn.setDefaultButton(true);
        Runnable attemptPin = () -> {
            if (picked[0] == null) {
                error.setText("Pick a profile tile first.");
                return;
            }
            if (!validPinShape(pin.getText())) {
                error.setText("PIN must be 4 digits.");
                return;
            }
            try {
                if (UserSessionManager.getInstance().login(picked[0], pin.getText())) onSuccess.run();
                else error.setText("Wrong PIN or unknown user.");
            } catch (Exception ex) {
                error.setText("Login unavailable: " + ex.getMessage());
            }
        };
        loginBtn.setOnAction(e -> attemptPin.run());
        pin.setOnAction(e -> attemptPin.run());
        badge.setOnAction(e -> {
            try {
                if (UserSessionManager.getInstance().loginBadge(badge.getText().trim())) onSuccess.run();
                else error.setText("Unknown badge.");
            } catch (Exception ex) {
                error.setText("Login unavailable: " + ex.getMessage());
            }
        });

        VBox box = new VBox(12, title, grid, selected, pin, pad, loginBtn, badge, error);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(440);
        box.getStyleClass().add("auth-card");
        box.setPadding(new javafx.geometry.Insets(24));
        return box;
    }

    // First-launch: empty DB gets its first manager instead of a login wall.
    public static Node setupForm(Runnable onDone) {
        Label title = new Label("First launch — register the store manager");
        title.getStyleClass().add("view-title");
        TextField display = new TextField();
        display.setPromptText("Display name (e.g. Karl M.)");
        TextField username = new TextField();
        username.setPromptText("Username (e.g. karl_m)");
        PasswordField pin = new PasswordField();
        pin.setPromptText("4-digit PIN");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm PIN");
        TextField badge = new TextField();
        badge.setPromptText("Badge barcode (optional)");
        Label error = new Label("");
        Button go = new Button("Register manager");
        go.setDefaultButton(true);
        go.setOnAction(e -> {
            if (display.getText().trim().isEmpty() || username.getText().trim().isEmpty()) {
                error.setText("Display name and username are required.");
                return;
            }
            if (!validPinShape(pin.getText()) || !pin.getText().equals(confirm.getText())) {
                error.setText("PINs must match and be 4 digits.");
                return;
            }
            try {
                Employees.register(username.getText(), display.getText(),
                        pin.getText(), "MANAGER", badge.getText(), "#059669");
                onDone.run();
            } catch (Exception ex) {
                error.setText("Registration failed: " + ex.getMessage());
            }
        });
        VBox box = new VBox(12, title, display, username, pin, confirm, badge, go, error);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(380);
        box.getStyleClass().add("auth-card");
        box.setPadding(new javafx.geometry.Insets(24));
        return box;
    }

    static Button profileTile(Employees.Employee e) {
        String initials = initialsOf(e.displayName());
        Circle dot = new Circle(22);
        try {
            dot.setFill(Color.web(e.color() == null ? "#06b6d4" : e.color()));
        } catch (Exception ex) {
            dot.setFill(Color.web("#06b6d4"));
        }
        Text glyph = new Text(initials);
        glyph.setFill(Color.WHITE);
        var avatar = new javafx.scene.layout.StackPane(dot, glyph);
        var name = new Label(e.displayName());
        var role = new Label("(" + e.role() + ")");
        var tile = new VBox(4, avatar, name, role);
        tile.setAlignment(Pos.CENTER);
        Button b = new Button();
        b.setGraphic(tile);
        b.setPrefSize(140, 120);
        return b;
    }

    static String initialsOf(String displayName) {
        if (displayName == null) return "?";
        String[] parts = displayName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++)
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        return sb.length() == 0 ? "?" : sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("pin shape 1234=" + validPinShape("1234") + " (want true)");
        System.out.println("pin shape 12a4=" + !validPinShape("12a4") + " (want true)");
        System.out.println("pin shape 12345=" + !validPinShape("12345") + " (want true)");
        System.out.println("initials 'Karl M.'=" + initialsOf("Karl M.") + " (want KM)");
        System.out.println("AuthView OK.");
    }
}
