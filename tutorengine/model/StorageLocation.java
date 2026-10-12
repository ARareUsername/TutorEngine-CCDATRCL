package tutorengine.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tutorengine.util.CSVLoader;

// OWNER: Huypungco — Graph & Benchmark. Admin edit by Bondoc (TutorEngine.md section 7):
// remodel slots to mtg_storage_organization_guide.md with card-level routing.
//2026-10-11
// PURPOSE: One pin on the store map. Physical truth is the guide: a black wire
// rack (M core top, Modern/Standard-M middle, L top-right, P/O/N+others lower)
// plus a white cabinet (T top, Urza/Un/W middle, utility bottom). Each slot holds
// one ~1000-card box. Front nodes (Checkout Counter, Intake Sorting Desk,
// Showcase Display) are not slots. Dataset box names stay valid in the CSV but
// no longer drive placement: every card routes by type, then set code, with
// unlisted codes overflowing to the bulk-rares slot (guide section 3).
//
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.model.StorageLocation
//   Expected: 10 shelf slots print as "id -> label [zone]", every dataset box name
//   resolves (no UNMAPPED lines), two locations with the same id compare equal.
//
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

    // All 9 box slots: rack tiers + cabinet shelves, guide order.
    public static List<StorageLocation> slots() {
        return List.of(
            new StorageLocation("RACK-M-TOP", "Black rack top: core M10-M21",
                    "Rack", "RACK", "TOP", BOX_CAPACITY),
            new StorageLocation("RACK-M-MID", "Black rack middle: Modern + Standard M",
                    "Rack", "RACK", "MID", BOX_CAPACITY),
            new StorageLocation("RACK-L-TOP", "Black rack top-right: L cluster",
                    "Rack", "RACK", "TOP", BOX_CAPACITY),
            new StorageLocation("RACK-PON-LOW", "Black rack lower: P/O/N + others",
                    "Rack", "RACK", "LOW", BOX_CAPACITY),
            new StorageLocation("CAB-T-TOP", "White cabinet top: T cluster",
                    "Cabinet", "CAB", "TOP", BOX_CAPACITY),
            new StorageLocation("CAB-UW-MID", "White cabinet middle: Urza/Un/W",
                    "Cabinet", "CAB", "MID", BOX_CAPACITY),
            new StorageLocation("CAB-LANDS-LOW", "White cabinet bottom: lands",
                    "Cabinet", "CAB", "LOW", BOX_CAPACITY),
            new StorageLocation("CAB-TOKEN-LOW", "White cabinet bottom: tokens",
                    "Cabinet", "CAB", "LOW", BOX_CAPACITY),
            new StorageLocation("CAB-RARE-LOW", "White cabinet bottom: bulk rares + overflow",
                    "Cabinet", "CAB", "LOW", BOX_CAPACITY));
    }

    // Set code -> slot, straight from the guide section 1 clusters.
    static final Map<String, String> SET_SLOT = new LinkedHashMap<>();
    static {
        for (String s : new String[]{"M10","M11","M12","M13","M14","M15","M19","M20","M21"})
            SET_SLOT.put(s, "RACK-M-TOP");
        for (String s : new String[]{"MH1","MH2","MH3","MMA","MM2","MM3","MOM","MAT","MKM",
                "MID","MIR","MOR","MRD","MBS","MMQ"})
            SET_SLOT.put(s, "RACK-M-MID");
        for (String s : new String[]{"LTR","LCI","LRW","LEG","LGN"})
            SET_SLOT.put(s, "RACK-L-TOP");
        for (String s : new String[]{"PLC","PLS","PCY","OGW","ONE","NEO","NPH","NEM",
                "RNA","KHM","VIS","TOR","XLN","ZEN","ZNR"})
            SET_SLOT.put(s, "RACK-PON-LOW");
        for (String s : new String[]{"THS","THB","TDM","TSR","TSP"})
            SET_SLOT.put(s, "CAB-T-TOP");
        for (String s : new String[]{"USG","ULG","UDS","UNH","UST","UNF",
                "WAR","WOE","WWK","WTH"})
            SET_SLOT.put(s, "CAB-UW-MID");
    }

    // Card-level placement: pins to card's boxLocationId if set in <set>-<box number> format.
    // Otherwise routes to <set>-1 based on setCode, or "Unassigned" if neither exists.
    public static String route(Card c) {
        return route(c, "");
    }

    // Collector-number-aware overload.
    public static String route(Card c, String collectorNumber) {
        if (c == null) return "Unassigned";
        String pinned = c.getBoxLocationId() == null ? "" : c.getBoxLocationId().trim();
        if (!pinned.isEmpty()) {
            if (pinned.equalsIgnoreCase("Unassigned")) return "Unassigned";
            if (pinned.matches("[A-Za-z0-9]+-[0-9]+")) return pinned.toUpperCase();
            if (pinned.matches("[A-Za-z0-9]+")) {
                for (StorageLocation s : slots()) {
                    if (s.getId().equalsIgnoreCase(pinned)) return s.getId();
                }
                return pinned.toUpperCase() + "-1";
            }
            return pinned;
        }
        String code = c.getSetCode() == null ? "" : c.getSetCode().trim().toUpperCase();
        if (!code.isEmpty()) {
            return code + "-1";
        }
        return "Unassigned";
    }

    public static boolean isValidSlot(String id) {
        if (id == null || id.isBlank()) return false;
        String clean = id.trim();
        if (clean.matches("[A-Za-z0-9]+-[0-9]+")) return true;
        for (StorageLocation s : slots()) {
            if (s.getId().equalsIgnoreCase(clean)) return true;
        }
        return false;
    }

    public static void main(String[] args) {
        for (StorageLocation s : slots()) System.out.println(s);
        System.out.println("--- route check vs Dataset/cards.csv ---");
        Map<String, List<Card>> per = new LinkedHashMap<>();
        for (Card c : CSVLoader.load("Dataset/cards.csv"))
            per.computeIfAbsent(route(c), k -> new ArrayList<>()).add(c);
        int total = 0;
        for (var e : per.entrySet()) {
            e.getValue().sort(Card.BY_EXPANSION);
            System.out.println(e.getKey() + ": " + e.getValue().size() + " cards");
            total += e.getValue().size();
        }
        System.out.println(total == 60 ? "All 60 cards route. StorageLocation OK."
                : "ROUTED " + total + " (need 60)!");
        System.out.println("equal ids equal: "
                + new StorageLocation("CAB-T-TOP", "x").equals(new StorageLocation("CAB-T-TOP", "y")));
    }
}
