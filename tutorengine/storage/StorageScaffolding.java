package tutorengine.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tutorengine.db.Db;
import tutorengine.model.CardInstance;
import tutorengine.model.StorageLocation;

// Storage routing, destination clustering, in-hand pre-sorting, and in-box neighbor scaffolding
// (tutorengine_implementation_specification.md §4)
public class StorageScaffolding {

    public static class StorageBatch {
        private final String boxId;
        private final String displayLabel;
        private final String shelfUnit;
        private final String shelfTier;
        private final boolean newBoxRequired;
        private final String suggestedBoxId;
        private final List<CardInstance> cards = new ArrayList<>();

        public StorageBatch(String boxId, String displayLabel, String shelfUnit,
                            String shelfTier, boolean newBoxRequired, String suggestedBoxId) {
            this.boxId = boxId;
            this.displayLabel = displayLabel;
            this.shelfUnit = shelfUnit;
            this.shelfTier = shelfTier;
            this.newBoxRequired = newBoxRequired;
            this.suggestedBoxId = suggestedBoxId;
        }

        public String getBoxId() { return boxId; }
        public String getDisplayLabel() { return displayLabel; }
        public String getShelfUnit() { return shelfUnit; }
        public String getShelfTier() { return shelfTier; }
        public boolean isNewBoxRequired() { return newBoxRequired; }
        public String getSuggestedBoxId() { return suggestedBoxId; }
        public List<CardInstance> getCards() { return cards; }

        public void sortInHandForwardPass() {
            cards.sort(Comparator.comparing(CardInstance::getName, String.CASE_INSENSITIVE_ORDER));
        }

        @Override
        public String toString() {
            return displayLabel + " [" + boxId + "] (" + cards.size() + " cards, "
                    + shelfUnit + " " + shelfTier + (newBoxRequired ? ", NEW_BOX_REQ" : "") + ")";
        }
    }

    public static class NeighborBreadcrumb {
        private final String precedingCard;
        private final String targetCard;
        private final String succeedingCard;
        private final double estimatedPercent;
        private final String breadcrumbText;

        public NeighborBreadcrumb(String precedingCard, String targetCard,
                                  String succeedingCard, double estimatedPercent,
                                  String breadcrumbText) {
            this.precedingCard = precedingCard;
            this.targetCard = targetCard;
            this.succeedingCard = succeedingCard;
            this.estimatedPercent = estimatedPercent;
            this.breadcrumbText = breadcrumbText;
        }

        public String getPrecedingCard() { return precedingCard; }
        public String getTargetCard() { return targetCard; }
        public String getSucceedingCard() { return succeedingCard; }
        public double getEstimatedPercent() { return estimatedPercent; }
        public String getBreadcrumbText() { return breadcrumbText; }

        @Override
        public String toString() { return breadcrumbText; }
    }

    // Resolves target box ID for a card instance
    public static String routeInstance(CardInstance ci) {
        if (ci.getBoxId() != null && !ci.getBoxId().isBlank()) return ci.getBoxId();
        String name = ci.getName().toLowerCase();
        if (name.equals("plains") || name.equals("island") || name.equals("swamp")
                || name.equals("mountain") || name.equals("forest")) {
            return "CAB-LANDS-LOW";
        }
        String set = ci.getSetCode() != null ? ci.getSetCode().toUpperCase() : "";
        for (StorageLocation s : StorageLocation.slots()) {
            if (s.getId().equalsIgnoreCase(set)) return s.getId();
        }
        // Known set mapping check
        String routed = StorageLocation.route(new tutorengine.model.Card(
                "TEMP", ci.getName(), "Set", "Colorless", "Creature", 2024, 1.0, 1, 50, false, "",
                "", "", "", set));
        return routed;
    }

    // Queries UNSTORED cards from DB and clusters into batches
    public static List<StorageBatch> clusterUnstored() throws Exception {
        List<CardInstance> unstored = new ArrayList<>();
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, scryfall_id, name, set_code, collector_number, rarity,"
                     + " condition, finish, language, status, box_id, commit_id, created_at, stored_at"
                     + " FROM card_instances WHERE status = 'UNSTORED' ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                unstored.add(new CardInstance(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getString(13), rs.getString(14)));
            }
        }

        Map<String, StorageBatch> batchMap = new LinkedHashMap<>();
        for (CardInstance ci : unstored) {
            String boxId = routeInstance(ci);
            StorageBatch batch = batchMap.computeIfAbsent(boxId, k -> {
                String label = k;
                String unit = "Cabinet";
                String tier = "LOW";
                for (StorageLocation s : StorageLocation.slots()) {
                    if (s.getId().equalsIgnoreCase(k)) {
                        label = s.getLabel();
                        unit = s.getZone().equals("Rack") ? "Metal Wire Rack" : "White Cabinet";
                        tier = s.getShelf();
                        break;
                    }
                }
                boolean newReq = k.equals("CAB-RARE-LOW") && ci.getSetCode() != null && !ci.getSetCode().isBlank();
                String suggested = newReq ? ci.getSetCode() + " ①" : "";
                return new StorageBatch(k, label, unit, tier, newReq, suggested);
            });
            batch.getCards().add(ci);
        }

        List<StorageBatch> out = new ArrayList<>(batchMap.values());
        for (StorageBatch b : out) {
            b.sortInHandForwardPass();
        }
        out.sort((a, b) -> Integer.compare(b.getCards().size(), a.getCards().size()));
        return out;
    }

    // Calculates immediate preceding and succeeding alphabetical neighbors from stored inventory
    public static NeighborBreadcrumb calculateBreadcrumb(String boxId, String targetCardName) throws Exception {
        String prev = null;
        String next = null;
        int totalStoredInBox = 0;
        int rank = 0;

        try (Connection c = Db.open()) {
            // Count total stored in this box
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM card_instances WHERE box_id = ? AND status = 'STORED'")) {
                ps.setString(1, boxId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) totalStoredInBox = rs.getInt(1);
                }
            }

            // Find immediate preceding neighbor
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name FROM card_instances WHERE box_id = ? AND status = 'STORED' AND name <= ?"
                    + " ORDER BY name DESC LIMIT 1")) {
                ps.setString(1, boxId);
                ps.setString(2, targetCardName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) prev = rs.getString(1);
                }
            }

            // Find immediate succeeding neighbor
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name FROM card_instances WHERE box_id = ? AND status = 'STORED' AND name >= ?"
                    + " ORDER BY name ASC LIMIT 1")) {
                ps.setString(1, boxId);
                ps.setString(2, targetCardName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) next = rs.getString(1);
                }
            }

            // Approximate position rank
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM card_instances WHERE box_id = ? AND status = 'STORED' AND name < ?")) {
                ps.setString(1, boxId);
                ps.setString(2, targetCardName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) rank = rs.getInt(1);
                }
            }
        }

        double percent = (totalStoredInBox > 0) ? ((double) rank / totalStoredInBox * 100.0) : 50.0;
        percent = Math.max(0.0, Math.min(100.0, percent));

        String text;
        if (prev != null && next != null) {
            if (prev.equalsIgnoreCase(next)) {
                text = "Insert beside identical copy: \"" + prev + "\" (~" + String.format("%.0f%%", percent) + ")";
            } else {
                text = "Insert between \"" + prev + "\" ↔ \"" + next + "\" (~" + String.format("%.0f%%", percent) + ")";
            }
        } else if (next != null) {
            text = "Insert before \"" + next + "\" at start of box (0%)";
        } else if (prev != null) {
            text = "Insert after \"" + prev + "\" at end of box (100%)";
        } else {
            text = "First card in box (empty container)";
        }

        return new NeighborBreadcrumb(prev, targetCardName, next, percent, text);
    }

    // Confirms batch stored and updates card statuses
    public static int confirmBatchStored(String boxId, List<String> cardIds) throws Exception {
        if (cardIds == null || cardIds.isEmpty()) return 0;
        int count = 0;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE card_instances SET status = 'STORED', box_id = ?, stored_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            for (String id : cardIds) {
                ps.setString(1, boxId);
                ps.setString(2, id);
                ps.addBatch();
            }
            int[] res = ps.executeBatch();
            for (int r : res) if (r > 0) count++;
        }
        // Update box container count
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE box_containers SET current_card_count = "
                     + "(SELECT COUNT(*) FROM card_instances WHERE box_id = ? AND status = 'STORED') WHERE box_id = ?")) {
            ps.setString(1, boxId);
            ps.setString(2, boxId);
            ps.executeUpdate();
        }
        return count;
    }

    public static void main(String[] args) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("storage-scaffolding-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();

            // Insert dummy audit commits to satisfy foreign key
            try (Connection c = Db.open();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO audit_commits (commit_hash, commit_tag, employee_name, card_count, committed_at, prev_hash, row_hash)"
                         + " VALUES (?,?,?,?,CURRENT_TIMESTAMP,'','hash')")) {
                ps.setString(1, "C01"); ps.setString(2, "BATCH-1"); ps.setString(3, "Tester"); ps.setInt(4, 2);
                ps.executeUpdate();
                ps.setString(1, "C02"); ps.setString(2, "BATCH-2"); ps.setString(3, "Tester"); ps.setInt(4, 1);
                ps.executeUpdate();
            }

            // Insert 2 STORED cards
            try (Connection c = Db.open();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number,"
                         + " rarity, condition, finish, language, status, box_id, commit_id, created_at, stored_at)"
                         + " VALUES (?,?,?,?,?,?,?,?,?,'STORED',?,'C01',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                ps.setString(1, "s1"); ps.setString(2, "scry1"); ps.setString(3, "Ajani, Caller of the Pride");
                ps.setString(4, "M14"); ps.setString(5, "1"); ps.setString(6, "mythic"); ps.setString(7, "NM");
                ps.setString(8, "nonfoil"); ps.setString(9, "EN"); ps.setString(10, "RACK-M-TOP");
                ps.executeUpdate();

                ps.setString(1, "s2"); ps.setString(2, "scry2"); ps.setString(3, "Chandra, Pyromaster");
                ps.setString(4, "M14"); ps.setString(5, "132"); ps.setString(6, "mythic"); ps.setString(7, "NM");
                ps.setString(8, "nonfoil"); ps.setString(9, "EN"); ps.setString(10, "RACK-M-TOP");
                ps.executeUpdate();
            }

            // Test neighbor calculation for "Black Cat" (between Ajani and Chandra)
            NeighborBreadcrumb b1 = calculateBreadcrumb("RACK-M-TOP", "Black Cat");
            System.out.println("Breadcrumb (middle): " + b1.getBreadcrumbText());

            // Test neighbor calculation for "Abzan Battle Priest" (before Ajani)
            NeighborBreadcrumb b2 = calculateBreadcrumb("RACK-M-TOP", "Abzan Battle Priest");
            System.out.println("Breadcrumb (start): " + b2.getBreadcrumbText());

            // Test neighbor calculation for "Doom Blade" (after Chandra)
            NeighborBreadcrumb b3 = calculateBreadcrumb("RACK-M-TOP", "Doom Blade");
            System.out.println("Breadcrumb (end): " + b3.getBreadcrumbText());

            // Test empty box
            NeighborBreadcrumb bEmpty = calculateBreadcrumb("CAB-T-TOP", "Tormod's Crypt");
            System.out.println("Breadcrumb (empty): " + bEmpty.getBreadcrumbText());

            // Insert UNSTORED card and test clustering
            try (Connection c = Db.open();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number,"
                         + " rarity, condition, finish, language, status, box_id, commit_id, created_at)"
                         + " VALUES (?,?,?,?,?,?,?,?,?,'UNSTORED',NULL,'C02',CURRENT_TIMESTAMP)")) {
                ps.setString(1, "u1"); ps.setString(2, "scry3"); ps.setString(3, "Baneslayer Angel");
                ps.setString(4, "M10"); ps.setString(5, "4"); ps.setString(6, "mythic"); ps.setString(7, "NM");
                ps.setString(8, "nonfoil"); ps.setString(9, "EN");
                ps.executeUpdate();
            }

            List<StorageBatch> batches = clusterUnstored();
            System.out.println("Clustered batches: " + batches.size() + " (want >= 1)");
            System.out.println("Batch cards count: " + batches.get(0).getCards().size());

            // Test confirm stored
            int storedCount = confirmBatchStored("RACK-M-TOP", List.of("u1"));
            System.out.println("Confirmed stored count: " + storedCount + " (want 1)");

            List<StorageBatch> afterBatches = clusterUnstored();
            System.out.println("Unstored batches after confirm: " + afterBatches.size() + " (want 0)");

            System.out.println("StorageScaffolding OK.");
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.println("StorageScaffolding ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
