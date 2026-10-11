package tutorengine;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import tutorengine.algorithms.SkuCorrector;
import tutorengine.algorithms.StoreNavigator;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;
import tutorengine.priority.PriorityDesk;
import tutorengine.search.SkuFuzzy;
import tutorengine.ui.CardImageCache;
import tutorengine.ui.GraphView;
import tutorengine.util.CSVLoader;

// OWNER: Bondoc, Karl B. — the window the store clerk sees. Finished member
// features land here as new buttons and panels.
//
// NOTE: requires a real screen; nothing renders over a text-only connection.
// That is expected, not a bug.
//
// HOW TO TEST:
//   Run: mvn exec:java (window titled "TutorEngine" opens, dataset-driven)
//   Expected:
//     - Status line reads "Loaded 60 cards. Try SKU <real-code> ...".
//     - Searching the suggested SKU prints its "Found: ..." line + card picture.
//     - Empty search prints the empty-guard; "MTG ##" prints the invalid-guard.
//     - BOGUS-1 prints "Not found: BOGUS-1" + fuzzy "Did you mean" (if any).
//     - List Catalog scrolls one line per card (60 lines with the dataset).
//     - Top Priority names the priciest in-demand card; Store Map prints the
//       retrieval route + box fill.
public class TutorEngineUI {
    private final Map<String, Card> catalog = new HashMap<>(101);
    private final Map<String, List<String>> skuGraph;
    private final JTextArea log = new JTextArea(20, 60);
    private final JLabel preview = new JLabel();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TutorEngineUI().show());
    }

    private TutorEngineUI() {
        seed();
        skuGraph = SkuCorrector.build(new java.util.ArrayList<>(catalog.values()));
    }

    // Null = searchable; otherwise the message to show. Same rule as Main.
    static String checkSku(String raw) {
        if (raw == null || raw.trim().isEmpty())
            return "Type a SKU first (e.g. MTG-BLB-280).";
        if (!raw.trim().matches("[A-Za-z0-9-]+"))
            return "Invalid SKU: letters, digits, hyphens only.";
        return null;
    }

    private void show() {
        JFrame f = new JFrame("TutorEngine");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        log.setEditable(false);

        JTextField skuField = new JTextField(14);
        JButton searchBtn = new JButton("Search SKU");
        searchBtn.addActionListener(e -> search(skuField.getText()));
        skuField.addActionListener(e -> search(skuField.getText()));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(skuField);
        top.add(searchBtn);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton listBtn = new JButton("List Catalog");
        listBtn.addActionListener(e -> {
            if (catalog.isEmpty()) log("Catalog empty.");
            else catalog.values().forEach(c -> log(c.toString()));
        });
        JButton topBtn = new JButton("Top Priority");
        topBtn.addActionListener(e -> {
            PriorityDesk desk = new PriorityDesk();
            catalog.values().forEach(desk::offer);
            Card star = desk.peek();
            log(star == null ? "No cards to prioritize." : "Top priority: " + star);
        });
        JButton mapBtn = new JButton("Store Map");
        mapBtn.addActionListener(e -> {
            Map<String, List<String>> g = StoreNavigator.sampleMap();
            Card sample = catalog.isEmpty() ? null : catalog.values().iterator().next();
            String goal = sample == null ? "CAB-T-TOP" : StorageLocation.route(sample);
            log("Map: " + StoreNavigator.nodeCount(g) + " nodes / "
                    + StoreNavigator.edgeCount(g) + " edges; e.g. Checkout -> " + goal + ": "
                    + String.join(" -> ", StoreNavigator.bfsPath(g, StoreNavigator.CHECKOUT, goal)));
            GraphView.fillCounts(new java.util.ArrayList<>(catalog.values())).forEach((slot, n) -> {
                if (n > 0) log("  " + slot + ": " + n + "/" + StorageLocation.BOX_CAPACITY + " cards");
            });
        });
        bottom.add(listBtn);
        bottom.add(topBtn);
        bottom.add(mapBtn);

        preview.setText("[card image appears here]");
        JPanel east = new JPanel(new FlowLayout(FlowLayout.LEFT));
        east.add(preview);

        f.add(top, BorderLayout.NORTH);
        f.add(new JScrollPane(log), BorderLayout.CENTER);
        f.add(east, BorderLayout.EAST);
        f.add(bottom, BorderLayout.SOUTH);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        String example = catalog.isEmpty() ? "MTG-XXXX-000" : catalog.keySet().iterator().next();
        log("Loaded " + catalog.size() + " cards. Try SKU " + example + " and press Search.");
    }

    private void search(String raw) {
        String guard = checkSku(raw);
        if (guard != null) { log(guard); return; }
        String sku = raw.trim();
        Card c = catalog.get(sku);
        if (c != null) {
            log("Found: " + c);
            showPreview(c);
            return;
        }
        log("Not found: " + sku);
        List<Card> near = SkuCorrector.multiSearch(catalog, skuGraph, sku);
        if (!near.isEmpty()) {
            log("No exact match — closest:");
            near.stream().limit(3).forEach(n -> log("  " + n.getName() + " [" + n.getSku() + "]"
                    + " (" + SkuCorrector.matchedField(catalog, skuGraph, sku, n) + ")"));
        }
    }

    // Thumbnail loads off the UI thread so typing never freezes; offline or
    // unknown cards keep the placeholder instead of throwing.
    private void showPreview(Card c) {
        preview.setIcon(null);
        preview.setText("[loading image...]");
        new SwingWorker<ImageIcon, Void>() {
            @Override protected ImageIcon doInBackground() {
                return CardImageCache.get(c.getSku(), c.getName(), 180);
            }
            @Override protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) { preview.setIcon(icon); preview.setText(""); }
                    else preview.setText("[image unavailable offline]");
                } catch (Exception ex) {
                    preview.setText("[image unavailable]");
                }
            }
        }.execute();
    }

    private void seed() {
        List<Card> loaded = CSVLoader.load("Dataset/cards.csv");
        if (loaded.isEmpty()) {
            System.out.println("WARNING: dataset empty or missing, using 3 fallback cards.");
            loaded = List.of(
                new Card("MTG-MH3-001", "Ajani, Nacatl Pariah", "Modern Horizons 3", "White", "Creature", 2024, 2150.00, 2, 92, true, "Showcase Display"),
                new Card("MTG-OTJ-055", "Mana Drain", "Outlaws of Thunder Junction", "Blue", "Instant", 2024, 2600.00, 1, 98, false, "Showcase Display"),
                new Card("MTG-FDN-101", "Llanowar Elves", "Foundations", "Green", "Creature", 2024, 25.00, 48, 45, false, "Bulk Box C"));
        }
        for (Card c : loaded) catalog.put(c.getSku(), c);
    }

    private void log(String s) {
        log.append(s + "\n");
    }
}
