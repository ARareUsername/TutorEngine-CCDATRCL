package tutorengine.model;

import java.util.Objects;

// Hi Huypungco! Yours as well — same ownership rules as your other files.
//
// What this does: it's one pin on the store map — a box, shelf, display case,
// desk, or counter. Every card's boxLocationId points at one of these pins, so
// this class is the glue between the cards and your navigator's map. If the
// names here don't match the map and the cards exactly, the "where do I walk?"
// feature silently breaks.
//
// Your steps:
//  1. Add a zone field (like "Showcase", "Bulk", or "Front") plus a getter.
//     The id/label pair and the equals/hashCode below stay as they are.
//  2. In main(), create all 10 map locations and print them as
//     "id -> label [zone]", one per line. Reuse these exact ids everywhere:
//     as Card box ids and as dots in StoreNavigator's map. All three lists
//     must agree word for word.
//  3. For the report, list the 10 pins and 15 aisles in the design matrix's
//     graph row.
//
// How to check your work:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.model.StorageLocation
//   When you're done: 10 lines print, every id exists in all three places, and
//   two locations built with the same id compare as equal.
//
// (Spec: S7 graph representation.)
public class StorageLocation {
    private final String id;
    private final String label;
    // TODO Huypungco: add zone field + getter (step 1).

    public StorageLocation(String id, String label) { this.id = id; this.label = label; }
    public String getId() { return id; }
    public String getLabel() { return label; }

    @Override public boolean equals(Object o) {
        return o instanceof StorageLocation s && Objects.equals(id, s.id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }

    public static void main(String[] args) {
        // TODO Huypungco: print the 10 locations (step 3).
        System.out.println(new StorageLocation("BOX-GREEN-03", "Bulk Box C").getLabel());
        System.out.println("StorageLocation. OWNER: Huypungco.");
    }
}
