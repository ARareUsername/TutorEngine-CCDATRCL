package tutorengine.model;

// OWNER: Mate D (Graph & Benchmark). No other member edits this file.
// TASK: graph vertex for boxes/shelves/cases used by StoreNavigator adjacency.
// ACCEPT: id + label + zone; equals/hashCode by id.
// SPEC: S7 Graph representation.
public class StorageLocation {
    private final String id;
    private final String label;

    public StorageLocation(String id, String label) { this.id = id; this.label = label; }
    public String getId() { return id; }
    public String getLabel() { return label; }

    public static void main(String[] args) {
        System.out.println(new StorageLocation("BOX-GREEN-03", "Bulk Box C").getLabel());
        System.out.println("StorageLocation stub. OWNER: Mate D.");
    }
}
