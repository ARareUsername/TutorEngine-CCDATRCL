package tutorengine.ui;

import java.awt.GraphicsEnvironment;
import java.util.List;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
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
    // of failing, so check the local X socket before launching. Remote DISPLAY
    // values are attempted normally and caught above if they fail.
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

        ListView<String> nav = new ListView<>();
        nav.getItems().addAll(navItems());
        nav.getStyleClass().add("main-nav");
        nav.setFixedCellSize(44);
        StackPane content = new StackPane();
        String[] mapPreselect = new String[1]; // set by CatalogView Locate
        CatalogView.locateHandler = slot -> {
            mapPreselect[0] = slot;
            nav.getSelectionModel().select("Store Map");
            // Reselect fires even when already there (Locate is only reachable
            // from Catalog, so selection always changes here).
            content.getChildren().setAll(GraphView.build(cards, mapPreselect[0]));
            mapPreselect[0] = null;
        };
        BorderPane root = new BorderPane();

        Button themeBtn = new Button("Dark mode");
        themeBtn.setOnAction(e -> {
            Theme.toggle();
            themeBtn.setText(Theme.isDark() ? "Light mode" : "Dark mode");
        });

        BorderPane top = new BorderPane();
        top.setRight(themeBtn);
        root.setTop(top);

        nav.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> {
            if (sel == null) return;
            switch (sel) {
                case "Priority" -> content.getChildren().setAll(PriorityView.build(cards));
                case "Store Map" -> content.getChildren().setAll(GraphView.build());
                case "Benchmarks" -> content.getChildren().setAll(BenchmarkView.build());
                case "Demos" -> content.getChildren().setAll(DemoView.build(cards));
                default -> content.getChildren().setAll(CatalogView.build(cards));
            }
            // Subtle 200ms crossfade on view switch (clerk-speed motion).
            javafx.animation.FadeTransition ft =
                    new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), content);
            ft.setFromValue(0.35);
            ft.setToValue(1.0);
            ft.play();
        });
        nav.getSelectionModel().select("Catalog");

        SplitPane split = new SplitPane(nav, content);
        split.setDividerPositions(0.22);
        root.setCenter(split);

        stage.setTitle("TutorEngine");
        javafx.scene.Scene scene = new javafx.scene.Scene(root, 1000, 640);
        try {
            // Project-root relative; missing file degrades to Primer defaults.
            var css = java.nio.file.Paths.get("tutorengine/ui/tutorengine.css").toUri().toURL();
            scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) { }
        stage.setScene(scene);
        stage.show();
    }
}
