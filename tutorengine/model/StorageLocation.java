package tutorengine.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tutorengine.util.CSVLoader;

// OWNER: Huypungco — Graph & Benchmark. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: One pin on the store map. The storage area is a single 1x5 cabinet row
// (CAB-1..CAB-5, left to right), each cabinet with an UPPER and LOWER shelf holding
// one ~1000-card horizontal box. Front nodes (Checkout Counter, Intake Sorting Desk,
// Showcase Display) are not in a cabinet. Current box names stay valid via ALIAS:
// every dataset boxLocationId resolves to exactly one slot id.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.model.StorageLocation
//   Expected: 10 shelf slots print as "id -> label [zone]", every dataset box name
//   resolves (no UNMAPPED lines), two locations with the same id compare equal.
//
// SPEC: S7 graph representation.
public class StorageLocation {
    public static final int BOX_CAPACITY = 1000;

    private final String id;
    private final String label;
    private final String zone;      // "Front" | "Cabinet"
    private final String cabinet;   // "CAB-1".."CAB-5", "" for front nodes
    private final String shelf;     // "UPPER" | "LOWER", "" for front nodes
    private final int capacity;     // cards per box; 0 for non-box nodes

    public StorageLocation(String id, String label) {
        this(id, label, "Front", "", "", 0);
    }

    public StorageLocation(String id, String label, String zone, String cabinet, String shelf, int capacity) {
        this.id = id;
        this.label = label;
        this.zone = zone;
        this.cabinet = cabinet;
        this.shelf = shelf;
        this.capacity = capacity;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getZone() { return zone; }
    public String getCabinet() { return cabinet; }
    public String getShelf() { return shelf; }
    public int getCapacity() { return capacity; }

    @Override public boolean equals(Object o) {
        return o instanceof StorageLocation s && Objects.equals(id, s.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }

    @Override public String toString() { return id + " -> " + label + " [" + zone + "]"; }

    // All 10 shelf slots, left to right, upper then lower per cabinet.
    public static List<StorageLocation> slots() {
        List<StorageLocation> out = new ArrayList<>(10);
        for (int n = 1; n <= 5; n++)
            for (String s : List.of("UPPER", "LOWER"))
                out.add(new StorageLocation("CAB-" + n + "-" + s, "Cabinet " + n + " " + s.toLowerCase() + " shelf",
                        "Cabinet", "CAB-" + n, s, BOX_CAPACITY));
        return out;
    }

    // Current box name -> slot id. Color-grouped left to right; showcase stays front.
    // Empty shelves are expansion space (honest: the 60-card sample fills ~6%/box).
    public static Map<String, String> aliasTable() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Bulk Box A (White/Blue)", "CAB-1-UPPER");
        m.put("Bulk Box B (Black/Red)", "CAB-3-UPPER");
        m.put("Bulk Box C (Green/Colorless)", "CAB-5-UPPER");
        m.put("Showcase Display", "Showcase Display");
        m.put("Showcase Display (High Value)", "Showcase Display");
        m.put("Intake Sorting Desk", "Intake Sorting Desk");
        m.put("Checkout Counter", "Checkout Counter");
        return m;
    }

    public static String resolve(String boxName) { return aliasTable().get(boxName); }

    public static void main(String[] args) {
        for (StorageLocation s : slots()) System.out.println(s);
        System.out.println("--- alias check vs Dataset/cards.csv ---");
        int bad = 0;
        for (Card c : CSVLoader.load("Dataset/cards.csv")) {
            if (resolve(c.getBoxLocationId()) == null) {
                System.out.println("UNMAPPED: " + c.getBoxLocationId());
                bad++;
            }
        }
        System.out.println(bad == 0 ? "All box names resolve. StorageLocation OK."
                : bad + " UNMAPPED box names!");
        System.out.println("equal ids equal: "
                + new StorageLocation("CAB-1-UPPER", "x").equals(new StorageLocation("CAB-1-UPPER", "y")));
    }
}
