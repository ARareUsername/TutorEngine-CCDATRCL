package tutorengine.ui;

import java.util.Optional;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

// DialogHelper: Centralized modal dialog presentation with window ownership binding,
// in-app modal overlay support (bypassing Linux/Wayland window manager glitches),
// stage geometry preservation (maximized state, width, height), and JDK-8325549 mitigation
// (§1 specs/modal-dialog-lifecycle/spec.md).
public final class DialogHelper {
    private static Stage primaryStage;
    private static StackPane rootContainer;

    private DialogHelper() {}

    // Registers the primary application Stage as the default dialog owner
    public static void registerPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    // Registers the primary application StackPane root container for in-app modal overlays
    public static void registerRootContainer(StackPane container) {
        rootContainer = container;
    }

    public static StackPane getRootContainer() {
        if (rootContainer != null) return rootContainer;
        if (primaryStage != null && primaryStage.getScene() != null
                && primaryStage.getScene().getRoot() instanceof StackPane sp) {
            return sp;
        }
        return null;
    }

    // Displays an in-app confirmation modal dialog (immune to Wayland/KWin OS window resize bugs)
    public static void showConfirmation(String title, String message, Runnable onConfirm, Runnable onCancel) {
        StackPane root = getRootContainer();
        if (root == null) {
            if (onConfirm != null) onConfirm.run();
            return;
        }

        StackPane backdrop = new StackPane();
        backdrop.setStyle("-fx-background-color: rgba(15, 23, 42, 0.65);");
        backdrop.setAlignment(Pos.CENTER);

        VBox card = new VBox(14);
        card.setMaxWidth(420);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: -color-bg-default; -fx-background-radius: 8; -fx-border-color: -color-border-subtle; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 16, 0, 0, 4);");
        StackPane.setAlignment(card, Pos.CENTER);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 13px;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> {
            root.getChildren().remove(backdrop);
            if (onCancel != null) onCancel.run();
        });

        Button confirmBtn = new Button("Confirm");
        confirmBtn.getStyleClass().add("danger");
        confirmBtn.setStyle("-fx-font-weight: bold;");
        confirmBtn.setOnAction(e -> {
            root.getChildren().remove(backdrop);
            if (onConfirm != null) onConfirm.run();
        });

        HBox btnRow = new HBox(10, cancelBtn, confirmBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(titleLabel, messageLabel, btnRow);
        backdrop.getChildren().add(card);

        backdrop.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                root.getChildren().remove(backdrop);
                if (onCancel != null) onCancel.run();
            }
        });

        root.getChildren().add(backdrop);
        confirmBtn.requestFocus();
    }

    // Resolves an effective window owner (explicit or default primary stage)
    public static Window resolveOwner(Window explicitOwner) {
        if (explicitOwner != null) return explicitOwner;
        return primaryStage;
    }

    // Displays a dialog or alert modal with owner stage geometry safeguards
    public static <R> Optional<R> showAndWait(Dialog<R> dialog) {
        return showAndWait(dialog, null);
    }

    // Displays a dialog or alert with explicit owner window and geometry safeguards
    public static <R> Optional<R> showAndWait(Dialog<R> dialog, Window owner) {
        if (dialog == null) return Optional.empty();

        Window targetOwner = resolveOwner(owner);
        if (targetOwner != null && dialog.getOwner() == null) {
            try {
                dialog.initOwner(targetOwner);
                dialog.initModality(Modality.WINDOW_MODAL);
            } catch (IllegalStateException ignored) {
                // Modality or owner already set
            }
        }

        Stage stageOwner = (targetOwner instanceof Stage s) ? s : null;
        final boolean wasMaximized = stageOwner != null && stageOwner.isMaximized();
        final double prevW = stageOwner != null ? stageOwner.getWidth() : -1;
        final double prevH = stageOwner != null ? stageOwner.getHeight() : -1;

        if (stageOwner != null) {
            EventHandler<DialogEvent> prevShown = dialog.getOnShown();
            dialog.setOnShown(e -> {
                if (prevShown != null) prevShown.handle(e);
                restoreGeometry(stageOwner, wasMaximized, prevW, prevH);
            });

            EventHandler<DialogEvent> prevHidden = dialog.getOnHidden();
            dialog.setOnHidden(e -> {
                if (prevHidden != null) prevHidden.handle(e);
                restoreGeometry(stageOwner, wasMaximized, prevW, prevH);
            });
        }

        // Avoid forcing resizability on simple Alert dialogs
        if (!(dialog instanceof javafx.scene.control.Alert)) {
            dialog.setResizable(true);
        }

        Optional<R> result = dialog.showAndWait();
        if (stageOwner != null) {
            restoreGeometry(stageOwner, wasMaximized, prevW, prevH);
        }
        return result;
    }

    private static void restoreGeometry(Stage stage, boolean wasMaximized, double prevW, double prevH) {
        if (stage == null) return;
        Platform.runLater(() -> {
            if (stage.isIconified()) {
                stage.setIconified(false);
                stage.toFront();
            }
            if (wasMaximized) {
                // JDK-8325549 / JDK-8332352 mitigation on KDE/KWin:
                // JavaFX often still reports isMaximized() == true even after KWin un-maximizes it.
                // Toggling false -> true forces GTK to re-issue the _NET_WM_STATE_MAXIMIZED atom.
                stage.setMaximized(false);
                stage.setMaximized(true);
            } else if (shouldRestoreDimensions(wasMaximized, prevW, prevH, stage.getWidth(), stage.getHeight())) {
                stage.setWidth(prevW);
                stage.setHeight(prevH);
            }
        });
    }

    // Pure logic helper for maximized restoration
    public static boolean shouldRestoreMaximized(boolean wasMaximized, boolean isCurrentlyMaximized) {
        return wasMaximized && !isCurrentlyMaximized;
    }

    // Pure logic helper for dimension restoration
    public static boolean shouldRestoreDimensions(
            boolean wasMaximized, double prevW, double prevH, double currW, double currH) {
        if (wasMaximized) return false;
        return prevW > 0 && prevH > 0 && (Double.compare(prevW, currW) != 0 || Double.compare(prevH, currH) != 0);
    }

    public static void main(String[] args) {
        System.out.println("=== DialogHelper Headless Unit Verification ===");

        // Test 1: Maximized state restoration checks
        if (!shouldRestoreMaximized(true, false)) {
            throw new AssertionError("Should restore maximized when previously maximized and now unmaximized");
        }
        if (shouldRestoreMaximized(true, true)) {
            throw new AssertionError("Should not restore maximized when already maximized");
        }
        if (shouldRestoreMaximized(false, false)) {
            throw new AssertionError("Should not restore maximized when not previously maximized");
        }
        System.out.println("  ✓ Maximized state restoration calculations passed");

        // Test 2: Dimension restoration checks
        if (!shouldRestoreDimensions(false, 1200, 800, 1000, 640)) {
            throw new AssertionError("Should restore dimensions when floating size shifted");
        }
        if (shouldRestoreDimensions(false, 1200, 800, 1200, 800)) {
            throw new AssertionError("Should not restore dimensions when floating size unchanged");
        }
        if (shouldRestoreDimensions(true, 1200, 800, 1000, 640)) {
            throw new AssertionError("Should not restore dimensions when previously maximized");
        }
        System.out.println("  ✓ Dimension restoration calculations passed");

        // Test 3: Owner resolution fallback
        if (resolveOwner(null) != null) {
            throw new AssertionError("Unregistered owner resolution should return null");
        }
        System.out.println("  ✓ Unregistered fallback owner resolution passed");

        System.out.println("DialogHelper OK.");
    }
}
