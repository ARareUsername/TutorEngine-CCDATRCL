package tutorengine.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

// Benchmarks view: Template F table fed by BenchmarkResults/bench.txt plus the
// Template E complexity table. rows()/complexityRows() are toolkit-free; the
// view keeps an honest empty-state when bench.txt is missing.
public final class BenchmarkView {
    private BenchmarkView() {}

    // Columns: size | operation | algorithm | ns | comparisons | observation.
    // Lenient: header/NOTE lines and malformed rows are skipped, never fatal.
    public static List<String[]> rows() {
        return rows(Path.of("BenchmarkResults/bench.txt"));
    }

    static List<String[]> rows(Path benchFile) {
        List<String[]> out = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(benchFile);
        } catch (Exception e) {
            return out; // missing/unreadable: caller shows the placeholder
        }
        for (String line : lines) {
            String[] cells = line.split("\\|");
            if (cells.length != 6) continue;
            for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim();
            if (!cells[0].matches("\\d+")) continue; // header + NOTE lines
            out.add(cells);
        }
        return out;
    }

    // Template E actuals, abbreviated for the side table.
    public static List<String[]> complexityRows() {
        return List.of(
            new String[]{"SKU lookup", "HashMap.get", "O(1)", "O(1)", "O(n)"},
            new String[]{"Catalog search", "TreeMap.get", "O(log n)", "O(log n)", "O(log n)"},
            new String[]{"Heap peek/poll", "PriorityQueue", "O(1)/O(log n)", "O(1)/O(log n)", "O(1)/O(log n)"},
            new String[]{"Log append/undo", "LinkedList", "O(1)", "O(1)", "O(1)"},
            new String[]{"Queue/stack ops", "ArrayDeque", "O(1)*", "O(1)*", "O(1)*"},
            new String[]{"BFS / DFS", "adjacency graph", "O(V+E)", "O(V+E)", "O(V+E)"},
            new String[]{"Insertion sort", "manual", "O(n)", "O(n^2)", "O(n^2)"},
            new String[]{"Selection sort", "manual", "O(n^2)", "O(n^2)", "O(n^2)"},
            new String[]{"Fuzzy fan-out", "multiSearch scan", "O(n)", "O(n·L)", "O(n·L)"});
    }

    public static Node build() {
        List<String[]> data = rows();
        VBox benchBox;
        if (data.isEmpty()) {
            benchBox = new VBox(8, new Label("No benchmark data yet — run "
                    + "tutorengine.util.BenchmarkSuite to write BenchmarkResults/bench.txt "
                    + "(sort-vs-sort + search-vs-search at 100/500/1000/5000)."));
        } else {
            TableView<String[]> table = new TableView<>();
            String[] titles = {"Size", "Operation", "Algorithm", "Time (ns)", "Comparisons", "Observation"};
            for (int i = 0; i < titles.length; i++) {
                final int k = i;
                TableColumn<String[], String> c = new TableColumn<>(titles[k]);
                c.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[k]));
                if (k == 0 || k == 3 || k == 4) c.getStyleClass().add("col-num");
                table.getColumns().add(c);
            }
            table.getItems().addAll(data);
            benchBox = new VBox(8, new Label("Measured (System.nanoTime):"), table);
        }
        TableView<String[]> ctx = new TableView<>();
        String[] ct = {"Operation", "Structure", "Best", "Average", "Worst"};
        for (int i = 0; i < ct.length; i++) {
            final int k = i;
            TableColumn<String[], String> c = new TableColumn<>(ct[i]);
            c.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[k]));
            ctx.getColumns().add(c);
        }
        VBox root = new VBox(10, benchBox, new Label("Complexity (Template E):"), ctx);
        root.getStyleClass().add("view-container");
        return root;
    }

    public static void main(String[] args) {
        System.out.println("rows=" + rows().size() + " (need 16 race rows)");
        System.out.println("complexity rows=" + complexityRows().size());
        System.out.println("missing file rows=" + rows(Path.of("BenchmarkResults/nope.txt")) + " (need [] placeholder path)");
        System.out.println("BenchmarkView data OK.");
    }
}
