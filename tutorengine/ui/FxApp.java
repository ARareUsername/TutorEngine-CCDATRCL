package tutorengine.ui;

import java.awt.GraphicsEnvironment;
import java.util.List;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import tutorengine.model.Card;
import tutorengine.util.CSVLoader;

// JavaFX entry point (run: mvn javafx:run). Left-nav shell; one view per module.
// Views are built from headless-safe static data functions so their logic is
// testable without a display (see each view's main()).
public class FxApp extends Application {
    public static void main(String[] args) {
        if (!displayReachable()) {
            System.out.println("FxApp needs a display. Headless? Use tutorengine.Main or the Swing UI instead.");
            return;
        }
        try {
            launch(args);
        } catch (Exception e) {
            System.out.println("FxApp could not open a window (" + e.getMessage() + "). Headless? Use tutorengine.Main instead.");
        }
    }

    // Nav items, toolkit-free for headless checks. Demos hides unless dev flag.
    public static java.util.List<String> navItems() {
        var items = new java.util.ArrayList<>(
                java.util.List.of("Catalog", "Priority", "Store Map", "Benchmarks"));
        if (Boolean.getBoolean("tutorengine.dev")) items.add("Demos");
        return items;
    }

    private static javafx.scene.control.Label sectionLabel(String text) {
        var l = new javafx.scene.control.Label(text);
        l.getStyleClass().add("section-header");
        return l;
    }

    private static Button navBtn(javafx.scene.layout.VBox nav, String title, String dotColor) {
        var dot = new javafx.scene.shape.Circle(5);
        dot.setFill(javafx.scene.paint.Color.web(dotColor));
        var b = new Button(title, dot);
        b.getStyleClass().add("nav-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(javafx.geometry.Pos.BASELINE_LEFT);
        nav.getChildren().add(b);
        return b;
    }

    private static void setActive(Button[] active, Button btn) {
        if (active[0] != null) active[0].getStyleClass().remove("nav-active");
        active[0] = btn;
        if (!btn.getStyleClass().contains("nav-active")) btn.getStyleClass().add("nav-active");
    }

    // Accessibility tiers dialog (text + card size), persisted per machine.
    private static void openSettings(Stage owner) {
        var dlg = new javafx.scene.control.Dialog<javafx.scene.control.ButtonType>();
        dlg.setTitle("Display settings");
        dlg.setHeaderText("Text and card sizes (saved on this machine)");
        var textBox = new javafx.scene.control.ChoiceBox<DisplaySettings.TextTier>();
        textBox.getItems().addAll(DisplaySettings.TextTier.values());
        textBox.setValue(DisplaySettings.text());
        var cardBox = new javafx.scene.control.ChoiceBox<DisplaySettings.CardTier>();
        cardBox.getItems().addAll(DisplaySettings.CardTier.values());
        cardBox.setValue(DisplaySettings.cards());
        var grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new javafx.scene.control.Label("Text size:"), 0, 0);
        grid.add(textBox, 1, 0);
        grid.add(new javafx.scene.control.Label("Card size:"), 0, 1);
        grid.add(cardBox, 1, 1);
        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dlg.setResultConverter(b -> b);
        dlg.initOwner(owner);
        var ok = dlg.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                == javafx.scene.control.ButtonType.OK;
        if (!ok) return;
        DisplaySettings.setText(textBox.getValue());
        DisplaySettings.setCards(cardBox.getValue());
        owner.getScene().getRoot().setStyle("-fx-font-size: " + textBox.getValue().px + "px;");
    }

    private static void crossfade(StackPane content) {        // Subtle 200ms crossfade on view switch (clerk-speed motion).
        var ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), content);
        ft.setFromValue(0.35);
        ft.setToValue(1.0);
        ft.play();
    }

    // A stale DISPLAY (e.g. ":0" with no X server) makes the toolkit hang instead
    // of failing, so check the local X socket before launching. Remote DISPLAY
    // values are attempted normally and caught above if they fail.
    static boolean displayReachable() {
        if (GraphicsEnvironment.isHeadless()) return false;
        String d = System.getenv("DISPLAY");
        if (d == null || d.isBlank()) return false;
        var m = java.util.regex.Pattern.compile("^:([0-9]+)(\\.[0-9]+)?$").matcher(d.trim());
        if (m.matches()) return new java.io.File("/tmp/.X11-unix/X" + m.group(1)).exists();
        return true;
    }

    @Override
    public void start(Stage stage) {
        Theme.apply(Theme.LIGHT);
        List<Card> cards = CatalogView.loadCards();

        // Grouped sidebar: Counter workflows, manager views, dev tooling (flagged).
        // Tabs cache their nodes so switching back preserves results.
        VBox nav = new VBox(2);
        nav.getStyleClass().add("side-nav");
        StackPane content = new StackPane();
        java.util.Map<String, javafx.scene.Node> viewCache = new java.util.HashMap<>();
        java.util.function.Function<String, javafx.scene.Node> viewFor = name -> viewCache.computeIfAbsent(name, n -> switch (n) {
            case "Priority" -> PriorityView.build(cards);
            case "Store Map" -> GraphView.build();
            case "Benchmarks" -> BenchmarkView.build();
            case "Demos" -> DemoView.build(cards);
            default -> CatalogView.build(cards);
        });
        Button[] activeBtn = new Button[1];
        java.util.function.BiConsumer<Button, String> show = (btn, name) -> {
            setActive(activeBtn, btn);
            content.getChildren().setAll(viewFor.apply(name));
            crossfade(content);
        };
        nav.getChildren().add(sectionLabel("COUNTER"));
        Button catalogBtn = navBtn(nav, "Catalog", "#059669");
        Button priorityBtn = navBtn(nav, "Priority", "#d97706");
        Button mapBtn = navBtn(nav, "Store Map", "#2563eb");
        nav.getChildren().add(sectionLabel("MANAGER"));
        Button benchBtn = navBtn(nav, "Benchmarks", "#7c3aed");
        Button demosBtn = null;
        if (Boolean.getBoolean("tutorengine.dev")) {
            nav.getChildren().add(sectionLabel("DEV"));
            demosBtn = navBtn(nav, "Demos", "#64748b");
            final Button dev = demosBtn;
            dev.setOnAction(e -> show.accept(dev, "Demos"));
        }
        catalogBtn.setOnAction(e -> show.accept(catalogBtn, "Catalog"));
        priorityBtn.setOnAction(e -> show.accept(priorityBtn, "Priority"));
        mapBtn.setOnAction(e -> show.accept(mapBtn, "Store Map"));
        benchBtn.setOnAction(e -> show.accept(benchBtn, "Benchmarks"));
        String[] mapPreselect = new String[1]; // set by CatalogView Locate
        CatalogView.locateHandler = slot -> {
            viewCache.put("Store Map", GraphView.build(cards, slot));
            setActive(activeBtn, mapBtn);
            content.getChildren().setAll(viewCache.get("Store Map"));
            crossfade(content);
        };
        BorderPane root = new BorderPane();

        Button themeBtn = new Button("Dark mode");
        themeBtn.setOnAction(e -> {
            Theme.toggle();
            themeBtn.setText(Theme.isDark() ? "Light mode" : "Dark mode");
        });
        Button settingsBtn = new Button("Settings");
        settingsBtn.setOnAction(e -> openSettings(stage));

        BorderPane top = new BorderPane();
        top.setRight(new javafx.scene.layout.HBox(8, settingsBtn, themeBtn));
        root.setTop(top);

        catalogBtn.fire(); // initial view (also sets the active highlight)

        SplitPane split = new SplitPane(nav, content);
        split.setDividerPositions(0.22);
        root.setCenter(split);

        stage.setTitle("TutorEngine");
        javafx.scene.Scene scene = new javafx.scene.Scene(root, 1000, 640);
        // Clerk shortcuts: Alt+1..5 jump straight to a tab, no mouse needed.
        Button[] order = demosBtn == null
                ? new Button[]{catalogBtn, priorityBtn, mapBtn, benchBtn}
                : new Button[]{catalogBtn, priorityBtn, mapBtn, benchBtn, demosBtn};
        javafx.scene.input.KeyCode[] keys = {
                javafx.scene.input.KeyCode.DIGIT1, javafx.scene.input.KeyCode.DIGIT2,
                javafx.scene.input.KeyCode.DIGIT3, javafx.scene.input.KeyCode.DIGIT4,
                javafx.scene.input.KeyCode.DIGIT5};
        for (int i = 0; i < order.length; i++) {
            Button target = order[i];
            scene.getAccelerators().put(
                    new javafx.scene.input.KeyCodeCombination(keys[i],
                            javafx.scene.input.KeyCombination.ALT_DOWN),
                    target::fire);
        }
        try {
            // Project-root relative; missing file degrades to Primer defaults.
            var css = java.nio.file.Paths.get("tutorengine/ui/tutorengine.css").toUri().toURL();
            scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) { }
        stage.setScene(scene);
        // Restore the saved text tier (default Standard when unset).
        root.setStyle("-fx-font-size: " + DisplaySettings.text().px + "px;");
        stage.show();
    }
}
