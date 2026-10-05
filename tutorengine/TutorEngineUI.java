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
        log("Loaded " + catalog.size() + " cards. Type a SKU (e.g. MTG-OTJ-055) and press Search.");
    }

    private void seed() {
        for (Card c : List.of(
                new Card("MTG-MH3-001", "Ajani, Nacatl Pariah", "Modern Horizons 3", "White", "Creature", 2024, 2150.00, 2, 92, true, "Showcase Display"),
                new Card("MTG-OTJ-055", "Mana Drain", "Outlaws of Thunder Junction", "Blue", "Instant", 2024, 2600.00, 1, 98, false, "Showcase Display"),
                new Card("MTG-FDN-101", "Llanowar Elves", "Foundations", "Green", "Creature", 2024, 25.00, 48, 45, false, "Bulk Box C")))
            catalog.put(c.getSku(), c);
    }

    private void log(String s) {
        log.append(s + "\n");
    }
}
