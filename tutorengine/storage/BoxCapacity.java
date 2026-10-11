package tutorengine.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import tutorengine.db.Db;
import tutorengine.model.StorageLocation;

// BoxCapacity: Capacity states, locked overflow rerouting, and new-box provisioning
// (§3 specs/box-capacity-guard/spec.md, mtg_storage_organization_guide.md §2).
// OWNER: Bondoc, Karl B.
public class BoxCapacity {

    public static final int NOMINAL_THRESHOLD = 900;
    public static final int MAX_CAPACITY = 1000;
    public static final String OVERFLOW_SLOT = "CAB-RARE-LOW";

    public enum State {
        NOMINAL("Nominal", "#059669"),
        NEAR_CAPACITY("Near Capacity", "#d97706"),
        FULL("FULL (Locked)", "#dc2626");

        private final String label;
        private final String colorHex;

        State(String label, String colorHex) {
            this.label = label;
            this.colorHex = colorHex;
        }

        public String getLabel() { return label; }
        public String getColorHex() { return colorHex; }
        public boolean isLocked() { return this == FULL; }
    }

    public record ProvisioningSuggestion(
            String suggestedBoxLabel,
            String suggestedBoxId,
            String targetShelfUnit,
            String targetShelfTier,
            String reason) {
        @Override
        public String toString() {
            return suggestedBoxLabel + " (" + targetShelfUnit + " " + targetShelfTier + "): " + reason;
        }
    }

    public record RoutingDecision(
            String targetBoxId,
            boolean rerouted,
            String reason,
            boolean provisioningRequired,
            ProvisioningSuggestion suggestion) {
        @Override
        public String toString() {
            return "Target: " + targetBoxId + (rerouted ? " [REROUTED: " + reason + "]" : " [OK]")
                    + (provisioningRequired ? " [PROVISION REQUIRED: " + suggestion + "]" : "");
        }
    }

    // Pure state function (stored + pending)
    public static State state(int stored, int pending) {
        int total = stored + pending;
        if (total < NOMINAL_THRESHOLD) return State.NOMINAL;
        if (total < MAX_CAPACITY) return State.NEAR_CAPACITY;
        return State.FULL;
    }

    public static State state(int total) {
        return state(total, 0);
    }

    // Resolves routing decision considering capacity limits and overflow roll-forward
    public static RoutingDecision routeWithCapacity(String primaryBoxId, int primaryCount,
                                                    int overflowCount, String setCode) {
        State primaryState = state(primaryCount);
        if (!primaryState.isLocked()) {
            return new RoutingDecision(primaryBoxId, false, "Nominal routing under capacity limit", false, null);
        }

        // Primary box is locked (>= 1000). Check overflow slot.
        State overflowState = state(overflowCount);
        if (primaryBoxId.equalsIgnoreCase(OVERFLOW_SLOT) || overflowState.isLocked()) {
            // Both primary and overflow are FULL -> trigger provisioning prompt
            ProvisioningSuggestion suggestion = suggestProvisioning(primaryBoxId, setCode);
            String reason = "Box " + primaryBoxId + " is FULL (" + primaryCount + " cards) and overflow "
                    + OVERFLOW_SLOT + " is FULL (" + overflowCount + " cards). Provisioning required.";
            return new RoutingDecision(OVERFLOW_SLOT, true, reason, true, suggestion);
        }

        // Roll-forward to overflow slot
        String reason = "Box " + primaryBoxId + " is FULL (" + primaryCount + " cards, limit "
                + MAX_CAPACITY + "). Rerouted to overflow slot " + OVERFLOW_SLOT + ".";
        return new RoutingDecision(OVERFLOW_SLOT, true, reason, false, null);
    }

    // Generates next volume label per guide §2 syntax
    public static ProvisioningSuggestion suggestProvisioning(String originalBoxId, String setCode) {
        String set = (setCode != null && !setCode.isBlank()) ? setCode.toUpperCase() : "BULK";
        String label = set + " ② (Encircled Index: " + set + " +1)";
        String boxId = set + "-VOL2";
        String shelfUnit = "White Cabinet";
        String shelfTier = "LOW";

        for (StorageLocation s : StorageLocation.slots()) {
            if (s.getId().equalsIgnoreCase(originalBoxId)) {
                shelfUnit = s.getZone().equals("Rack") ? "Metal Wire Rack" : "White Cabinet";
                shelfTier = s.getShelf();
                break;
            }
        }

        String reason = "All existing volumes for " + originalBoxId + " and overflow are at maximum capacity.";
        return new ProvisioningSuggestion(label, boxId, shelfUnit, shelfTier, reason);
    }

    // Records provisioned box container in SQLite
    public static boolean recordProvisioning(String boxId, String displayLabel,
                                             String shelfUnit, String shelfTier) {
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO box_containers (box_id, display_label, shelf_unit, shelf_tier, column_index, stack_position, current_card_count) "
                     + "VALUES (?, ?, ?, ?, 99, 1, 0) "
                     + "ON CONFLICT(box_id) DO UPDATE SET display_label = excluded.display_label")) {
            ps.setString(1, boxId);
            ps.setString(2, displayLabel);
            ps.setString(3, shelfUnit);
            ps.setString(4, shelfTier);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("Failed to record provisioning for " + boxId + ": " + e.getMessage());
            return false;
        }
    }

    // Query current stored card count for a box from DB
    public static int getBoxCount(String boxId) {
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT current_card_count FROM box_containers WHERE box_id = ?")) {
            ps.setString(1, boxId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static void main(String[] args) {
        System.out.println("=== BoxCapacity Synthetic-Count Demo ===");

        // 1. Verify threshold transitions: 899, 900, 999, 1000, 1001
        System.out.println("\n[1] Testing threshold transitions:");
        assertState(899, State.NOMINAL);
        assertState(900, State.NEAR_CAPACITY);
        assertState(999, State.NEAR_CAPACITY);
        assertState(1000, State.FULL);
        assertState(1001, State.FULL);
        assertState(850, 50, State.NEAR_CAPACITY); // stored 850 + pending 50 = 900
        assertState(950, 50, State.FULL);          // stored 950 + pending 50 = 1000

        // 2. Locked reroute scenario: primary is FULL, overflow has headroom
        System.out.println("\n[2] Testing locked reroute to overflow:");
        RoutingDecision d1 = routeWithCapacity("RACK-M-TOP", 1000, 250, "M14");
        System.out.println("  " + d1);
        if (!d1.rerouted() || !d1.targetBoxId().equals(OVERFLOW_SLOT) || d1.provisioningRequired()) {
            throw new AssertionError("Expected reroute to " + OVERFLOW_SLOT + " without provisioning prompt, got: " + d1);
        }
        System.out.println("  ✓ Locked slot routed to overflow with reason: " + d1.reason());

        // 3. Full overflow scenario: primary is FULL and overflow is FULL
        System.out.println("\n[3] Testing full overflow triggering provisioning prompt:");
        RoutingDecision d2 = routeWithCapacity("RACK-M-TOP", 1020, 1000, "M14");
        System.out.println("  " + d2);
        if (!d2.provisioningRequired() || d2.suggestion() == null) {
            throw new AssertionError("Expected provisioning prompt when overflow is full, got: " + d2);
        }
        System.out.println("  ✓ Provisioning prompt raised: " + d2.suggestion().suggestedBoxLabel()
                + " on " + d2.suggestion().targetShelfUnit() + " " + d2.suggestion().targetShelfTier());

        // 4. Record provisioning confirmation in DB
        System.out.println("\n[4] Testing recording provisioning confirmation in DB:");
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("box-capacity-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();

            ProvisioningSuggestion sug = d2.suggestion();
            boolean ok = recordProvisioning(sug.suggestedBoxId(), sug.suggestedBoxLabel(), sug.targetShelfUnit(), sug.targetShelfTier());
            System.out.println("  Provisioning recorded: " + ok);
            int count = getBoxCount(sug.suggestedBoxId());
            System.out.println("  Initial card count in DB: " + count + " (want 0)");
            if (!ok || count != 0) throw new AssertionError("Failed to persist provisioned box in DB");

            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        System.out.println("\n✓ BoxCapacity verification passed!");
    }

    private static void assertState(int count, State expected) {
        State actual = state(count);
        if (actual != expected) {
            throw new AssertionError("Count " + count + ": expected " + expected + ", got " + actual);
        }
        System.out.println("  ✓ Count " + count + " -> " + actual.name() + " (" + actual.getLabel() + ")");
    }

    private static void assertState(int stored, int pending, State expected) {
        State actual = state(stored, pending);
        if (actual != expected) {
            throw new AssertionError("Stored " + stored + " + Pending " + pending + ": expected " + expected + ", got " + actual);
        }
        System.out.println("  ✓ Stored " + stored + " + Pending " + pending + " -> " + actual.name());
    }
}
