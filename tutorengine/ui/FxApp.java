package tutorengine.ui;

import java.awt.GraphicsEnvironment;
import java.util.List;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import tutorengine.auth.Employees;
import tutorengine.auth.UserSessionManager;
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
                java.util.List.of("Catalog", "Rapid Intake", "Staging", "Storage", "Trips", "Priority", "Store Map", "Benchmarks", "Audit"));
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
        StackPane root = DialogHelper.getRootContainer();
        if (root == null && owner != null && owner.getScene() != null
                && owner.getScene().getRoot() instanceof StackPane sp) {
            root = sp;
        }

        var textBox = new javafx.scene.control.ChoiceBox<DisplaySettings.TextTier>();
        textBox.getItems().addAll(DisplaySettings.TextTier.values());
        textBox.setValue(DisplaySettings.text());
        var cardBox = new javafx.scene.control.ChoiceBox<DisplaySettings.CardTier>();
        cardBox.getItems().addAll(DisplaySettings.CardTier.values());
        cardBox.setValue(DisplaySettings.cards());

        var grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new javafx.scene.control.Label("Text size:"), 0, 0);
        grid.add(textBox, 1, 0);
        grid.add(new javafx.scene.control.Label("Card size:"), 0, 1);
        grid.add(cardBox, 1, 1);

        if (root != null) {
            StackPane backdrop = new StackPane();
            backdrop.setStyle("-fx-background-color: rgba(15, 23, 42, 0.65);");
            backdrop.setAlignment(javafx.geometry.Pos.CENTER);

            VBox card = new VBox(16);
            card.setMaxWidth(420);
            card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            card.setPadding(new javafx.geometry.Insets(20));
            card.setStyle("-fx-background-color: -color-bg-default; -fx-background-radius: 8; -fx-border-color: -color-border-subtle; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 16, 0, 0, 4);");
            StackPane.setAlignment(card, javafx.geometry.Pos.CENTER);

            Label titleLabel = new Label("Display settings");
            titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

            Label subLabel = new Label("Text and card sizes (saved on this machine)");
            subLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");

            Button cancelBtn = new Button("Cancel");
            StackPane finalRoot = root;
            cancelBtn.setOnAction(e -> finalRoot.getChildren().remove(backdrop));

            Button saveBtn = new Button("Save");
            saveBtn.getStyleClass().add("accent");
            saveBtn.setStyle("-fx-font-weight: bold;");
            saveBtn.setOnAction(e -> {
                finalRoot.getChildren().remove(backdrop);
                DisplaySettings.setText(textBox.getValue());
                DisplaySettings.setCards(cardBox.getValue());
                if (owner != null && owner.getScene() != null) {
                    owner.getScene().getRoot().setStyle("-fx-font-size: " + textBox.getValue().px + "px;");
                }
            });

            HBox btnRow = new HBox(10, cancelBtn, saveBtn);
            btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

            card.getChildren().addAll(titleLabel, subLabel, grid, btnRow);
            backdrop.getChildren().add(card);

            backdrop.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    finalRoot.getChildren().remove(backdrop);
                }
            });

            root.getChildren().add(backdrop);
            saveBtn.requestFocus();
            return;
        }

        var dlg = new javafx.scene.control.Dialog<javafx.scene.control.ButtonType>();
        dlg.setTitle("Display settings");
        dlg.setHeaderText("Text and card sizes (saved on this machine)");
        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dlg.setResultConverter(b -> b);
        var ok = DialogHelper.showAndWait(dlg, owner).orElse(javafx.scene.control.ButtonType.CANCEL)
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
        content.getStyleClass().add("content-area");
        content.setPadding(new javafx.geometry.Insets(12, 14, 12, 14));
        java.util.Map<String, javafx.scene.Node> viewCache = new java.util.HashMap<>();
        java.util.function.Function<String, javafx.scene.Node> viewFor = name -> viewCache.computeIfAbsent(name, n -> switch (n) {
            case "Rapid Intake" -> RapidIntakeView.build(CatalogView.activeCatalog, CatalogView.activeTable);
            case "Staging" -> StagingView.build();
            case "Storage" -> StorageView.build();
            case "Trips" -> TripView.build();
            case "Priority" -> PriorityView.build(cards);
            case "Store Map" -> GraphView.build();
            case "Benchmarks" -> BenchmarkView.build();
            case "Audit" -> AuditView.build();
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
        Button rapidIntakeBtn = navBtn(nav, "Rapid Intake", "#0ea5e9");
        Button stagingBtn = navBtn(nav, "Staging", "#10b981");
        Button storageBtn = navBtn(nav, "Storage", "#0284c7");
        Button tripsBtn = navBtn(nav, "Trips", "#6366f1");
        Button priorityBtn = navBtn(nav, "Priority", "#d97706");
        Button mapBtn = navBtn(nav, "Store Map", "#2563eb");
        nav.getChildren().add(sectionLabel("MANAGER"));
        Button benchBtn = navBtn(nav, "Benchmarks", "#7c3aed");
        Button auditBtn = navBtn(nav, "Audit", "#0e7490");
        Button demosBtn = null;
        if (Boolean.getBoolean("tutorengine.dev")) {
            nav.getChildren().add(sectionLabel("DEV"));
            demosBtn = navBtn(nav, "Demos", "#64748b");
            final Button dev = demosBtn;
            dev.setOnAction(e -> show.accept(dev, "Demos"));
        }
        catalogBtn.setOnAction(e -> show.accept(catalogBtn, "Catalog"));
        rapidIntakeBtn.setOnAction(e -> show.accept(rapidIntakeBtn, "Rapid Intake"));
        stagingBtn.setOnAction(e -> show.accept(stagingBtn, "Staging"));
        storageBtn.setOnAction(e -> show.accept(storageBtn, "Storage"));
        tripsBtn.setOnAction(e -> show.accept(tripsBtn, "Trips"));
        priorityBtn.setOnAction(e -> show.accept(priorityBtn, "Priority"));
        mapBtn.setOnAction(e -> show.accept(mapBtn, "Store Map"));
        benchBtn.setOnAction(e -> show.accept(benchBtn, "Benchmarks"));
        auditBtn.setOnAction(e -> show.accept(auditBtn, "Audit"));
        String[] mapPreselect = new String[1]; // set by CatalogView Locate
        CatalogView.locateHandler = slot -> {
            viewCache.put("Store Map", GraphView.build(cards, slot, CatalogView.lastLocatedCard));
            setActive(activeBtn, mapBtn);
            content.getChildren().setAll(viewCache.get("Store Map"));
            crossfade(content);
        };
        BorderPane root = new BorderPane();

        // Session widget: who is on the counter, plus switch/lock.
        Label activeLabel = new Label("Not logged in");
        Button switchBtn = new Button("Switch User");
        Button lockBtn = new Button("Lock");
        Button themeBtn = new Button("Dark mode");
        themeBtn.setOnAction(e -> {
            Theme.toggle();
            themeBtn.setText(Theme.isDark() ? "Light mode" : "Dark mode");
        });
        Button settingsBtn = new Button("Settings");
        settingsBtn.setOnAction(e -> openSettings(stage));

        BorderPane top = new BorderPane();
        top.getStyleClass().add("top-bar");
        top.setPadding(new javafx.geometry.Insets(8, 14, 8, 14));
        var topLeft = new javafx.scene.layout.HBox(8, activeLabel, switchBtn, lockBtn);
        topLeft.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var topRight = new javafx.scene.layout.HBox(8, settingsBtn, themeBtn);
        topRight.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        top.setLeft(topLeft);
        top.setRight(topRight);
        root.setTop(top);

        // Lock overlay covers the whole shell until someone authenticates.
        StackPane shell = new StackPane(root);
        javafx.scene.layout.StackPane overlay = new javafx.scene.layout.StackPane();
        overlay.setStyle("-fx-background-color: rgba(15,23,42,0.55);");
        Runnable refreshActive = () -> {
            var who = UserSessionManager.getInstance().currentOrNull();
            activeLabel.setText(who == null ? "Not logged in"
                    : "Active: " + who.displayName() + " (" + who.role() + ")");
        };
        Runnable showLock = () -> {
            boolean firstRun = false;
            try {
                firstRun = Employees.empty();
            } catch (Exception ex) {
                overlay.getChildren().setAll(new Label("Employee store unavailable: " + ex.getMessage()));
                overlay.setVisible(true);
                return;
            }
            overlay.getChildren().setAll(firstRun
                    ? AuthView.setupForm(() -> {
                        overlay.setVisible(false);
                        refreshActive.run();
                    })
                    : AuthView.loginForm(() -> {
                        overlay.setVisible(false);
                        refreshActive.run();
                    }));
            overlay.setVisible(true);
        };
        switchBtn.setOnAction(e -> {
            try {
                UserSessionManager.getInstance().logout();
            } catch (Exception ex) {
                // logout best-effort; lock screen still shows
            }
            refreshActive.run();
            showLock.run();
        });
        lockBtn.setOnAction(e -> showLock.run());
        shell.getChildren().add(overlay);

        // 10-minute idle auto-lock; any input resets the clock.
        var idle = new javafx.animation.PauseTransition(javafx.util.Duration.minutes(10));
        idle.setOnFinished(e -> {
            if (UserSessionManager.getInstance().isAuthenticated()) showLock.run();
        });

        catalogBtn.fire(); // initial view (also sets the active highlight)

        SplitPane split = new SplitPane(nav, content);
        split.setDividerPositions(0.22);
        SplitPane.setResizableWithParent(nav, false);
        root.setCenter(split);

        stage.setTitle("TutorEngine");
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        DialogHelper.registerPrimaryStage(stage);
        DialogHelper.registerRootContainer(shell);
        javafx.scene.Scene scene = new javafx.scene.Scene(shell, 1000, 640);
        // Any input resets the idle clock (registered before first show).
        scene.addEventFilter(javafx.scene.input.InputEvent.ANY, e -> idle.playFromStart());
        idle.play();
        // Clerk shortcuts: Alt+1..6 jump straight to a tab, no mouse needed.
        Button[] order = demosBtn == null
                ? new Button[]{catalogBtn, priorityBtn, mapBtn, benchBtn, auditBtn}
                : new Button[]{catalogBtn, priorityBtn, mapBtn, benchBtn, auditBtn, demosBtn};
        javafx.scene.input.KeyCode[] keys = {
                javafx.scene.input.KeyCode.DIGIT1, javafx.scene.input.KeyCode.DIGIT2,
                javafx.scene.input.KeyCode.DIGIT3, javafx.scene.input.KeyCode.DIGIT4,
                javafx.scene.input.KeyCode.DIGIT5, javafx.scene.input.KeyCode.DIGIT6};
        for (int i = 0; i < order.length; i++) {
            Button target = order[i];
            scene.getAccelerators().put(
                    new javafx.scene.input.KeyCodeCombination(keys[i],
                            javafx.scene.input.KeyCombination.ALT_DOWN),
                    target::fire);
        }
        scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.R,
                        javafx.scene.input.KeyCombination.ALT_DOWN),
                rapidIntakeBtn::fire);
        try {
            var res = FxApp.class.getResource("tutorengine.css");
            if (res != null) {
                scene.getStylesheets().add(res.toExternalForm());
            } else {
                java.nio.file.Path[] candidates = {
                    java.nio.file.Paths.get("tutorengine/ui/tutorengine.css"),
                    java.nio.file.Paths.get("TutorEngine/tutorengine/ui/tutorengine.css")
                };
                for (var p : candidates) {
                    if (java.nio.file.Files.exists(p)) {
                        scene.getStylesheets().add(p.toUri().toURL().toExternalForm());
                        break;
                    }
                }
            }
        } catch (Exception ignored) { }
        stage.setScene(scene);
        // Restore the saved text tier (default Standard when unset).
        root.setStyle("-fx-font-size: " + DisplaySettings.text().px + "px;");
        stage.show();
        // Employee store first: gated startup (setup on empty DB, else login).
        try {
            tutorengine.db.Db.migrate();
        } catch (Exception ex) {
            overlay.getChildren().setAll(new Label("Employee store unavailable: " + ex.getMessage()));
            overlay.setVisible(true);
            return;
        }
        showLock.run();
    }
}
