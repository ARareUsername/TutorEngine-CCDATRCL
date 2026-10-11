package tutorengine.ui;

import java.util.function.Function;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

// HoverPreviewHelper: Attaches on-hover card artwork preview tooltips to JavaFX TableViews
// with 600ms delay, daemon thread dispatch, and strict cancellation on hover-end or row recycling
// (§1 specs/table-hover-card-preview/spec.md).
public final class HoverPreviewHelper {
    public static final int HOVER_DELAY_MS = 600;

    private HoverPreviewHelper() {}

    // Attaches hover preview row factory to a TableView
    public static <T> void attach(
            TableView<T> table,
            Function<T, String> nameExtractor,
            Function<T, String> setCodeExtractor,
            Function<T, String> idExtractor) {
        if (table == null) return;
        table.setRowFactory(tv -> createRow(nameExtractor, setCodeExtractor, idExtractor));
    }

    public static <T> void attach(
            TableView<T> table,
            Function<T, String> nameExtractor,
            Function<T, String> setCodeExtractor) {
        attach(table, nameExtractor, setCodeExtractor, null);
    }

    public static <T> void attachHoverPreview(
            TableView<T> table,
            Function<T, String> nameExtractor,
            Function<T, String> setCodeExtractor,
            Function<T, String> idExtractor) {
        attach(table, nameExtractor, setCodeExtractor, idExtractor);
    }

    public static <T> void attachHoverPreview(
            TableView<T> table,
            Function<T, String> nameExtractor,
            Function<T, String> setCodeExtractor) {
        attach(table, nameExtractor, setCodeExtractor, null);
    }

    // Resolves a canonical SKU identifier when set code and collector number are known,
    // otherwise falling back to provided identifier or card name.
    public static String buildCardKey(String setCode, String collectorNumber, String fallbackId) {
        if (setCode != null && !setCode.isBlank() && !"UNK".equalsIgnoreCase(setCode.trim())
                && collectorNumber != null && !collectorNumber.isBlank()) {
            return "MTG-" + setCode.trim().toUpperCase() + "-" + collectorNumber.trim();
        }
        return fallbackId != null && !fallbackId.isBlank() ? fallbackId : "";
    }

    public static <T> TableRow<T> createRow(
            Function<T, String> nameExtractor,
            Function<T, String> setCodeExtractor,
            Function<T, String> idExtractor) {
        TableRow<T> row = new TableRow<>();
        Tooltip tip = new Tooltip();
        tip.setShowDelay(Duration.millis(HOVER_DELAY_MS));
        ImageView iv = new ImageView();
        iv.setFitWidth(DisplaySettings.cards().tooltip);
        iv.setPreserveRatio(true);
        tip.setGraphic(iv);
        row.setTooltip(tip);

        @SuppressWarnings("unchecked")
        javafx.concurrent.Task<Image>[] currentTask = new javafx.concurrent.Task[1];

        Runnable startLoad = () -> {
            if (currentTask[0] != null) {
                currentTask[0].cancel();
                currentTask[0] = null;
            }
            iv.setImage(null);
            if (row.isHover() && !row.isEmpty()) {
                T item = row.getItem();
                if (item == null) return;
                String name = nameExtractor != null ? nameExtractor.apply(item) : null;
                if (!isValidHoverItem(name)) return;
                String setCode = setCodeExtractor != null ? setCodeExtractor.apply(item) : "";
                String resolvedId = idExtractor != null ? idExtractor.apply(item) : name;
                final String id = (resolvedId != null && !resolvedId.isBlank()) ? resolvedId : name;

                final int tw = DisplaySettings.cards().tooltip;
                iv.setFitWidth(tw);
                final CardImageCache.Variant src = resolveVariant(DisplaySettings.cards());

                javafx.concurrent.Task<Image> load = new javafx.concurrent.Task<>() {
                    @Override protected Image call() {
                        if (isCancelled()) return null;
                        return CardImageCache.getFx(id, name, tw, src, setCode);
                    }
                };
                load.setOnSucceeded(done -> {
                    if (!load.isCancelled() && load.getValue() != null && row.isHover() && row.getItem() == item) {
                        iv.setImage(load.getValue());
                    }
                });
                currentTask[0] = load;
                Thread t = new Thread(load, "table-hover-card");
                t.setDaemon(true);
                t.start();
            }
        };

        row.hoverProperty().addListener((ho, was, is) -> {
            if (is) {
                startLoad.run();
            } else {
                if (currentTask[0] != null) {
                    currentTask[0].cancel();
                    currentTask[0] = null;
                }
                iv.setImage(null);
            }
        });

        row.itemProperty().addListener((io, oldItem, newItem) -> {
            if (row.isHover()) {
                startLoad.run();
            } else {
                if (currentTask[0] != null) {
                    currentTask[0].cancel();
                    currentTask[0] = null;
                }
                iv.setImage(null);
            }
        });

        return row;
    }

    public static boolean isValidHoverItem(String name) {
        return name != null && !name.isBlank();
    }

    public static CardImageCache.Variant resolveVariant(DisplaySettings.CardTier tier) {
        return tier == DisplaySettings.CardTier.S
                ? CardImageCache.Variant.SMALL
                : CardImageCache.Variant.NORMAL;
    }

    public static void main(String[] args) {
        System.out.println("=== HoverPreviewHelper Headless Demo ===");
        if (HOVER_DELAY_MS != 600) {
            throw new AssertionError("Hover delay must be 600ms, got: " + HOVER_DELAY_MS);
        }
        System.out.println("  ✓ Hover delay: " + HOVER_DELAY_MS + "ms");

        if (!isValidHoverItem("Lightning Bolt")) throw new AssertionError("Valid name failed");
        if (isValidHoverItem("")) throw new AssertionError("Empty name passed");
        if (isValidHoverItem(null)) throw new AssertionError("Null name passed");
        if (isValidHoverItem("   ")) throw new AssertionError("Blank name passed");
        System.out.println("  ✓ Item validity checks passed");

        CardImageCache.Variant vSmall = resolveVariant(DisplaySettings.CardTier.S);
        CardImageCache.Variant vMed = resolveVariant(DisplaySettings.CardTier.M);
        CardImageCache.Variant vLarge = resolveVariant(DisplaySettings.CardTier.L);
        if (vSmall != CardImageCache.Variant.SMALL || vMed != CardImageCache.Variant.NORMAL || vLarge != CardImageCache.Variant.NORMAL) {
            throw new AssertionError("Variant resolution mismatch");
        }
        System.out.println("  ✓ Variant resolution passed (S=SMALL, M=NORMAL, L=NORMAL)");

        String key1 = buildCardKey("2X2", "117", "fallback-id");
        if (!"MTG-2X2-117".equals(key1)) throw new AssertionError("buildCardKey failed: " + key1);
        String key2 = buildCardKey("UNK", "1", "fallback-id");
        if (!"fallback-id".equals(key2)) throw new AssertionError("buildCardKey UNK failed: " + key2);
        String key3 = buildCardKey(null, null, "fallback-id");
        if (!"fallback-id".equals(key3)) throw new AssertionError("buildCardKey null failed: " + key3);
        System.out.println("  ✓ buildCardKey resolution passed");

        System.out.println("HoverPreviewHelper OK.");
    }
}
