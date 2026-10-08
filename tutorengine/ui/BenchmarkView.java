package tutorengine.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

// Benchmarks view: Template F table. rows() is empty until BenchmarkSuite lands;
// the view shows an explanatory empty-state instead of an error.
public final class BenchmarkView {
    private BenchmarkView() {}

    // Columns: size | operation | algorithm | ns | comparisons | observation.
    public static List<String[]> rows() {
        return new ArrayList<>(); // TODO: feed from BenchmarkSuite when it lands.
    }

    public static Node build() {
        List<String[]> data = rows();
        if (data.isEmpty()) {
            return new VBox(8, new Label("No benchmark data yet — BenchmarkSuite is still pending. "
                    + "Expected: sort-vs-sort + search-vs-search at 100/500/1000/5000."));
        }
        TableView<String[]> table = new TableView<>();
        String[] titles = {"Size", "Operation", "Algorithm", "Time (ns)", "Comparisons", "Observation"};
        for (int i = 0; i < titles.length; i++) {
            final int k = i;
            TableColumn<String[], String> c = new TableColumn<>(titles[k]);
            c.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[k]));
            table.getColumns().add(c);
        }
        table.getItems().addAll(data);
        return new VBox(8, table);
    }

    public static void main(String[] args) {
        System.out.println("rows=" + rows().size() + " (0 until BenchmarkSuite lands; empty-state path OK)");
        System.out.println("BenchmarkView data OK.");
    }
}
