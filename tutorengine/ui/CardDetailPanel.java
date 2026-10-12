package tutorengine.ui;

import java.util.List;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;
import tutorengine.util.CSVLoader;

// CardDetailPanel: Visual detail display of all 16 card fields with real card art,
// Theme badges, and offline placeholder fallback.
// Headless methods (formatCardDetails, main) are toolkit-free.
public class CardDetailPanel {
    private final VBox root;
    private final ImageView imageView;
    private final Label placeholderLabel;
    private final Label titleLabel;
    private final Label subtitleLabel;
    private final Label priceQtyLabel;
    private final Label locationLabel;
    private final Label statsLabel;
    private final Label priorityLabel;
    private final FlowPane badgesPane;
    private final Label foilBadge;
    private final Label stockBadge;
    private final Label statusLabel;

    private Card currentCard;
    private int currentGen = 0;
    private Task<Image> currentTask = null;

    public CardDetailPanel() {
        imageView = new ImageView();
        imageView.setFitWidth(DisplaySettings.cards().preview);
        imageView.setPreserveRatio(true);

        placeholderLabel = new Label("No card selected");
        placeholderLabel.setWrapText(true);
        placeholderLabel.setAlignment(Pos.CENTER);
        placeholderLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-style: italic;");

        StackPane imageContainer = new StackPane(placeholderLabel, imageView);
        imageContainer.setPrefWidth(DisplaySettings.cards().preview);
        imageContainer.setMinHeight(160);
        imageContainer.setAlignment(Pos.CENTER);
        imageContainer.setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 6px; -fx-border-color: -color-border-subtle; -fx-border-radius: 6px;");
        imageContainer.setCursor(javafx.scene.Cursor.HAND);
        javafx.scene.control.Tooltip.install(imageContainer, new javafx.scene.control.Tooltip("Click to enlarge"));
        imageContainer.setOnMouseClicked(e -> {
            if (currentCard != null) {
                DialogHelper.showImageLightbox(currentCard);
            }
        });

        titleLabel = new Label("Select a card");
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        titleLabel.setWrapText(true);

        subtitleLabel = new Label("Choose a card from the catalog or search by SKU.");
        subtitleLabel.setStyle("-fx-text-fill: -color-fg-muted;");
        subtitleLabel.setWrapText(true);

        badgesPane = new FlowPane(6, 6);
        foilBadge = new Label("FOIL");
        foilBadge.getStyleClass().addAll("badge", Theme.BADGE_FOIL);
        stockBadge = new Label("IN STOCK");
        stockBadge.getStyleClass().addAll("badge", Theme.BADGE_OK);

        priceQtyLabel = new Label("");
        priceQtyLabel.setWrapText(true);

        locationLabel = new Label("");
        locationLabel.setWrapText(true);

        statsLabel = new Label("");
        statsLabel.setWrapText(true);

        priorityLabel = new Label("");
        priorityLabel.setStyle("-fx-font-weight: bold;");

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");

        VBox metaBox = new VBox(4, titleLabel, subtitleLabel, badgesPane,
                priceQtyLabel, locationLabel, statsLabel, priorityLabel, statusLabel);
        metaBox.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(metaBox, Priority.ALWAYS);

        HBox layout = new HBox(12, imageContainer, metaBox);
        layout.setAlignment(Pos.TOP_LEFT);

        root = new VBox(8, layout);
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color: -color-bg-default; -fx-background-radius: 6px; -fx-border-color: -color-border-subtle; -fx-border-radius: 6px;");
    }

    public Node getNode() {
        return root;
    }

    public Card getCurrentCard() {
        return currentCard;
    }

    // Displays all 16 fields, applies badges, and loads image asynchronously.
    public void showCard(Card c) {
        if (c == null) {
            clear();
            return;
        }
        currentCard = c;
        final int gen = ++currentGen;

        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }

        titleLabel.setText(c.getName() + " [" + c.getSku() + "]");
        String setInfo = c.getSetName() + (c.getSetCode().isEmpty() ? "" : " (" + c.getSetCode() + ")")
                + " · " + c.getColor() + " " + c.getCardType() + " · " + c.getReleaseYear();
        subtitleLabel.setText(setInfo);

        badgesPane.getChildren().clear();
        stockBadge.getStyleClass().removeAll(Theme.BADGE_OK, Theme.BADGE_LOW_STOCK, Theme.BADGE_FOIL);
        if (c.getQuantity() <= 3) {
            stockBadge.setText("LOW STOCK (" + c.getQuantity() + ")");
            stockBadge.getStyleClass().add(Theme.BADGE_LOW_STOCK);
        } else {
            stockBadge.setText("IN STOCK (" + c.getQuantity() + ")");
            stockBadge.getStyleClass().add(Theme.BADGE_OK);
        }
        badgesPane.getChildren().add(stockBadge);

        if (c.isFoil()) {
            badgesPane.getChildren().add(foilBadge);
        }

        priceQtyLabel.setText(String.format("Price: PHP %.2f  |  Quantity: %d  |  Demand Score: %d/100",
                c.getPrice(), c.getQuantity(), c.getDemandScore()));

        String boxStr = (c.getBoxLocationId() == null || c.getBoxLocationId().isBlank()) ? "Unassigned" : c.getBoxLocationId();
        locationLabel.setText("Shelf: " + StorageLocation.route(c) + " (Box: " + boxStr + ")");

        StringBuilder statsSb = new StringBuilder();
        if (!c.getManaCost().isEmpty()) {
            statsSb.append("Mana: ").append(c.getManaCost()).append("  ");
        }
        if (!c.getPower().isEmpty() || !c.getToughness().isEmpty()) {
            statsSb.append("P/T: ").append(c.getPower()).append("/").append(c.getToughness());
        }
        statsLabel.setText(statsSb.toString());

        priorityLabel.setText(String.format("Listing Priority: %.2f", c.calculatePriority()));

        // Start async image loading
        imageView.setImage(null);
        placeholderLabel.setText("Loading card art…");
        statusLabel.setText("Fetching card art…");

        final int w = DisplaySettings.cards().preview;
        final CardImageCache.Variant pv = switch (DisplaySettings.cards()) {
            case L -> CardImageCache.Variant.LARGE;
            case M -> CardImageCache.Variant.NORMAL;
            default -> CardImageCache.Variant.SMALL;
        };

        Task<Image> loadTask = new Task<>() {
            @Override
            protected Image call() {
                if (isCancelled()) return null;
                return CardImageCache.getFx(c.getSku(), c.getName(), w, pv, c.getSetCode(), c.getCollectorNumber());
            }
        };

        loadTask.setOnSucceeded(e -> {
            if (gen != currentGen) return;
            Image img = loadTask.getValue();
            if (img != null) {
                imageView.setImage(img);
                placeholderLabel.setText("");
                statusLabel.setText("");
            } else {
                imageView.setImage(null);
                placeholderLabel.setText("Art unavailable offline");
                statusLabel.setText("Image unavailable offline / uncached");
            }
        });

        loadTask.setOnFailed(e -> {
            if (gen != currentGen) return;
            imageView.setImage(null);
            placeholderLabel.setText("Art unavailable");
            statusLabel.setText("Image load failed");
        });

        currentTask = loadTask;
        new Thread(loadTask, "card-detail-image").start();
    }

    // Clears the panel on search miss to prevent displaying stale card details.
    public void clear() {
        currentCard = null;
        currentGen++;
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        imageView.setImage(null);
        placeholderLabel.setText("No card selected");
        titleLabel.setText("Select a card");
        subtitleLabel.setText("Choose a card from the catalog or search by SKU.");
        badgesPane.getChildren().clear();
        priceQtyLabel.setText("");
        locationLabel.setText("");
        statsLabel.setText("");
        priorityLabel.setText("");
        statusLabel.setText("");
    }

    // Toolkit-free string representation covering all 16 card fields.
    public static String formatCardDetails(Card c) {
        if (c == null) return "No card selected";
        return String.format(
            "1. Name: %s\n" +
            "2. SKU: %s\n" +
            "3. Set: %s\n" +
            "4. SetCode: %s\n" +
            "5. Color: %s\n" +
            "6. Type: %s\n" +
            "7. Year: %d\n" +
            "8. Price: PHP %.2f\n" +
            "9. Quantity: %d\n" +
            "10. Demand: %d\n" +
            "11. Foil: %s\n" +
            "12. Box: %s\n" +
            "13. Shelf: %s\n" +
            "14. ManaCost: %s\n" +
            "15. P/T: %s/%s\n" +
            "16. Priority: %.2f",
            c.getName(),
            c.getSku(),
            c.getSetName(),
            c.getSetCode(),
            c.getColor(),
            c.getCardType(),
            c.getReleaseYear(),
            c.getPrice(),
            c.getQuantity(),
            c.getDemandScore(),
            c.isFoil() ? "FOIL" : "NON-FOIL",
            c.getBoxLocationId(),
            StorageLocation.route(c),
            c.getManaCost().isEmpty() ? "N/A" : c.getManaCost(),
            c.getPower().isEmpty() ? "-" : c.getPower(),
            c.getToughness().isEmpty() ? "-" : c.getToughness(),
            c.calculatePriority()
        );
    }

    public static void main(String[] args) {
        List<Card> cards = CSVLoader.load("Dataset/cards.csv");
        if (cards.isEmpty()) {
            System.out.println("No cards found in Dataset/cards.csv!");
            return;
        }

        Card sample = cards.get(0);
        String formatted = formatCardDetails(sample);
        System.out.println("--- CardDetailPanel 16-field assembly check ---");
        System.out.println(formatted);

        boolean hasAllFields = formatted.contains("1. Name:")
                && formatted.contains("2. SKU:")
                && formatted.contains("3. Set:")
                && formatted.contains("4. SetCode:")
                && formatted.contains("5. Color:")
                && formatted.contains("6. Type:")
                && formatted.contains("7. Year:")
                && formatted.contains("8. Price:")
                && formatted.contains("9. Quantity:")
                && formatted.contains("10. Demand:")
                && formatted.contains("11. Foil:")
                && formatted.contains("12. Box:")
                && formatted.contains("13. Shelf:")
                && formatted.contains("14. ManaCost:")
                && formatted.contains("15. P/T:")
                && formatted.contains("16. Priority:");

        System.out.println("16 fields verified=" + hasAllFields);

        // Cache-hit test
        java.nio.file.Path p = CardImageCache.pathFor(sample.getSku());
        boolean cacheHit = java.nio.file.Files.exists(p);
        System.out.println("Cache hit for " + sample.getSku() + "=" + cacheHit);

        // Placeholder test for null/unknown card
        String nullDetail = formatCardDetails(null);
        System.out.println("Null card placeholder=" + "No card selected".equals(nullDetail));

        System.out.println("CardDetailPanel OK.");
    }
}
