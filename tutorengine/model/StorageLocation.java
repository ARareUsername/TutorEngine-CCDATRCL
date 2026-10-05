package tutorengine.model;

import java.util.Objects;

// OWNER: Huypungco — Graph & Benchmark. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: One pin on the store map — a box, shelf, display case, desk, or
// counter. Every card's boxLocationId points at one of these pins, so this class
// is the glue between the cards and the navigator's map. If the identifiers here
// do not match the map and the cards character-for-character, the "where do I
// walk?" feature fails silently.
//
// HOW TO IMPLEMENT:
//   1. Add a zone field (e.g. "Showcase", "Bulk", "Front") plus its getter.
//      Keep the id/label constructor and the id-based equals()/hashCode() below.
//   2. In main(): construct all 10 map locations and print them as
//      "id -> label [zone]", one per line. Reuse these exact identifiers as Card
//      boxLocationIds and as nodes in StoreNavigator.sampleMap() — all three
//      lists must agree word for word.
//   3. Report: copy the 10 nodes and 15 edges into the design matrix graph row.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.model.StorageLocation
//   Expected: 10 lines print; every id exists in all three places (this demo,
//   the cards, the map); two locations built with the same id compare equal.
//
// SPEC: S7 graph representation.
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
