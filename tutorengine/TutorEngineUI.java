package tutorengine;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import tutorengine.model.Card;
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
//     - Searching the suggested SKU prints its "Found: ..." line.
//     - BOGUS-1 prints "Not found: BOGUS-1" and the app keeps running.
//     - List Catalog scrolls one line per card (60 lines with the dataset).
public class TutorEngineUI {
    private final Map<String, Card> catalog = new HashMap<>(101);
    private final JTextArea log = new JTextArea(20, 60);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TutorEngineUI().show());
    }

    private TutorEngineUI() {
        seed();
    }

    private void show() {
        JFrame f = new JFrame("TutorEngine");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        log.setEditable(false);

        JTextField skuField = new JTextField(14);
        JButton searchBtn = new JButton("Search SKU");
        searchBtn.addActionListener(e -> {
            Card c = catalog.get(skuField.getText().trim());
            log(c == null ? "Not found: " + skuField.getText().trim() : "Found: " + c);
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(skuField);
        top.add(searchBtn);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton listBtn = new JButton("List Catalog");
        listBtn.addActionListener(e -> {
            if (catalog.isEmpty()) log("Catalog empty.");
            else catalog.values().forEach(c -> log(c.toString()));
        });
        bottom.add(listBtn);

        f.add(top, BorderLayout.NORTH);
        f.add(new JScrollPane(log), BorderLayout.CENTER);
        f.add(bottom, BorderLayout.SOUTH);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        String example = catalog.isEmpty() ? "MTG-XXXX-000" : catalog.keySet().iterator().next();
        log("Loaded " + catalog.size() + " cards. Try SKU " + example + " and press Search.");
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
