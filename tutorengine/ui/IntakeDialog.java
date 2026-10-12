package tutorengine.ui;

import java.util.List;
import java.util.Map;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;
import tutorengine.search.ScryfallLookup;

// OWNER: Bondoc, Karl B. — verified intake of uncataloged cards.
// Lookup logic lives in ScryfallLookup (tested); this file is dialog glue:
// debounced suggestions, verify image, prefilled editable fields, Confirm/Cancel.
public final class IntakeDialog {
    private IntakeDialog() {}

    // Adds the confirmed card to catalog + table; messages go to result.
    // Offline/API-down: notice line, manual Add form stays usable.
    public static void prompt(Map<String, Card> catalog,
                              javafx.scene.control.TableView<Card> table, Label result) {
        var dlg = new Dialog<Card>();
        dlg.setTitle("Add new card (live lookup)");
        dlg.setHeaderText("Find the real card, verify its picture, confirm.");

        TextField nameField = new TextField();
        nameField.setPromptText("Start typing a card name (min 2 letters)");
        ListView<String> suggestions = new ListView<>();
        suggestions.setPrefHeight(90);
        Label status = new Label("");
        status.setWrapText(true);
        ImageView image = new ImageView();
        image.setFitWidth(200);
        image.setPreserveRatio(true);
        Label fields = new Label("Pick a suggestion to load its details.");
        fields.setWrapText(true);
        TextField priceField = new TextField();
        priceField.setPromptText("Price PHP");
        priceField.setPrefWidth(90);
        TextField qtyField = new TextField("1");
        qtyField.setPrefWidth(50);
        TextField slotField = new TextField();
        slotField.setPromptText("Slot (blank=auto)");
        slotField.setPrefWidth(130);
        ScryfallLookup.Draft[] draft = new ScryfallLookup.Draft[1];
        int[] queryGen = new int[1];

        var debounce = new javafx.animation.PauseTransition(Duration.millis(300));
        debounce.setOnFinished(e -> {
            String q = nameField.getText() == null ? "" : nameField.getText().trim();
            if (q.length() < 2) {
                suggestions.getItems().clear();
                return;
            }
            final int gen = ++queryGen[0];
            var task = new javafx.concurrent.Task<List<String>>() {
                @Override protected List<String> call() {
                    return ScryfallLookup.autocomplete(q);
                }
            };
            task.setOnSucceeded(done -> {
                if (gen != queryGen[0]) return; // stale query stays silent
                suggestions.getItems().setAll(task.getValue());
                if (task.getValue().isEmpty())
                    status.setText("No suggestions (offline or no match) — use the manual Add form.");
            });
            new Thread(task, "scryfall-ac").start();
        });
        nameField.textProperty().addListener((o, old, now) -> debounce.playFromStart());

        suggestions.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> {
            if (sel == null) return;
            status.setText("Loading " + sel + "…");
            var task = new javafx.concurrent.Task<ScryfallLookup.Draft>() {
                @Override protected ScryfallLookup.Draft call() {
                    return ScryfallLookup.fetchDetail(sel);
                }
            };
            task.setOnSucceeded(done -> {
                ScryfallLookup.Draft d = task.getValue();
                if (d == null) {
                    status.setText("Card detail unavailable (offline?).");
                    return;
                }
                draft[0] = d;
                Double php = ScryfallLookup.toPhp(d.usdPrice());
                priceField.setText(php == null ? "" : String.format("%.2f", php));
                slotField.setText("");
                fields.setText(d.name() + " [" + d.setCode() + "] " + d.setName() + "\n"
                        + d.color() + " " + d.cardType() + " " + d.releaseYear()
                        + (php == null ? "\nPrice unknown — check and type it." : ""));
                status.setText(php == null ? "No USD price published — type the price." : "Verify the picture, then confirm.");
                var imgTask = new javafx.concurrent.Task<javafx.scene.image.Image>() {
                    @Override protected javafx.scene.image.Image call() {
                        return CardImageCache.getFx("LIVE-" + d.setCode(), d.name(), 200,
                                CardImageCache.Variant.NORMAL, d.setCode());
                    }
                };
                imgTask.setOnSucceeded(done2 -> {
                    if (imgTask.getValue() != null) image.setImage(imgTask.getValue());
                });
                new Thread(imgTask, "scryfall-verify").start();
            });
            new Thread(task, "scryfall-detail").start();
        });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.add(new Label("Price PHP:"), 0, 0);
        form.add(priceField, 1, 0);
        form.add(new Label("Qty:"), 0, 1);
        form.add(qtyField, 1, 1);
        form.add(new Label("Slot:"), 0, 2);
        form.add(slotField, 1, 2);
        var body = new VBox(8, nameField, suggestions, status,
                new HBox(12, image, fields), form);
        dlg.getDialogPane().setContent(body);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dlg.getDialogPane().lookupButton(ButtonType.OK)).setText("Add to catalog");
        // Validate inline so Confirm never closes on bad input (specific message stays).
        ((Button) dlg.getDialogPane().lookupButton(ButtonType.OK)).addEventFilter(
                javafx.event.ActionEvent.ACTION, e -> {
            if (draft[0] == null) {
                status.setText("Pick a suggested card first.");
                e.consume();
                return;
            }
            try {
                Double.parseDouble(priceField.getText().trim());
            } catch (Exception ex) {
                status.setText("Invalid price: use a number like 12.50 (blank when unknown is not allowed here).");
                e.consume();
                return;
            }
            try {
                int q = Integer.parseInt(qtyField.getText() == null ? "" : qtyField.getText().trim());
                if (q <= 0) throw new NumberFormatException();
            } catch (Exception ex) {
                status.setText("Invalid quantity: use a whole number of 1 or more.");
                e.consume();
                return;
            }
            String s = slotField.getText() == null ? "" : slotField.getText().trim();
            if (!s.isEmpty() && StorageLocation.slots().stream()
                    .noneMatch(sl -> sl.getId().equalsIgnoreCase(s))) {
                status.setText("Unknown slot: use a slot id like CAB-T-TOP (blank = auto).");
                e.consume();
            }
        });
        dlg.setResultConverter(b -> {
            if (b != ButtonType.OK || draft[0] == null) return null;
            double price;
            try {
                price = Double.parseDouble(priceField.getText().trim());
            } catch (Exception ex) {
                return null; // caller reports invalid price
            }
            int qty;
            try {
                qty = Integer.parseInt(qtyField.getText() == null ? "" : qtyField.getText().trim());
                if (qty <= 0) throw new NumberFormatException();
            } catch (Exception ex) {
                return null;
            }
            String slot = slotField.getText() == null ? "" : slotField.getText().trim();
            if (!slot.isEmpty() && StorageLocation.slots().stream()
                    .noneMatch(s -> s.getId().equalsIgnoreCase(slot))) return null;
            return ScryfallLookup.toCard(draft[0],
                    ScryfallLookup.nextSku(draft[0].setCode()), qty,
                    slot.isEmpty() ? "" : slot.toUpperCase(), price);
        });
        DialogHelper.showAndWait(dlg).ifPresent(c -> {
            if (catalog.containsKey(c.getSku())) {
                result.setText("Already in catalog: " + c.getSku());
                return;
            }
            catalog.put(c.getSku(), c);
            table.getItems().add(c);
            result.setText("Added live card (session-only): " + c.getName()
                    + " [" + c.getSku() + "] -> " + StorageLocation.route(c));
        });
    }

    public static void main(String[] args) {
        System.out.println("=== IntakeDialog Headless Demo ===");
        System.out.println("IntakeDialog OK.");
    }
}
