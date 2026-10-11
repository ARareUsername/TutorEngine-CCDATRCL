package tutorengine.intake;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tutorengine.audit.AuditLog;
import tutorengine.db.Db;
import tutorengine.model.AuditCommit;
import tutorengine.model.Card;

// Git-style Working Buffer and Staging Area (tutorengine_implementation_specification.md §3)
public class WorkingBuffer {

    public static class WorkingItem {
        private String id;
        private String scryfallId;
        private String name;
        private String setCode;
        private String collectorNumber;
        private String rarity;
        private String condition; // NM, LP, MP, HP
        private String finish;    // nonfoil, foil, etched
        private int quantity;

        public WorkingItem(String scryfallId, String name, String setCode,
                           String collectorNumber, String rarity,
                           String condition, String finish, int quantity) {
            this.id = UUID.randomUUID().toString();
            this.scryfallId = scryfallId != null ? scryfallId : "";
            this.name = name;
            this.setCode = setCode != null ? setCode.toUpperCase() : "UNK";
            this.collectorNumber = collectorNumber != null ? collectorNumber : "1";
            this.rarity = rarity != null ? rarity.toLowerCase() : "common";
            this.condition = condition != null ? condition.toUpperCase() : "NM";
            this.finish = finish != null ? finish.toLowerCase() : "nonfoil";
            this.quantity = Math.max(1, quantity);
        }

        public String getId() { return id; }
        public String getScryfallId() { return scryfallId; }
        public String getName() { return name; }
        public String getSetCode() { return setCode; }
        public String getCollectorNumber() { return collectorNumber; }
        public String getRarity() { return rarity; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition.toUpperCase(); }
        public String getFinish() { return finish; }
        public void setFinish(String finish) { this.finish = finish.toLowerCase(); }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = Math.max(1, quantity); }

        public boolean isDuplicateOf(WorkingItem other) {
            if (other == null) return false;
            return name.equalsIgnoreCase(other.name)
                    && setCode.equalsIgnoreCase(other.setCode)
                    && collectorNumber.equalsIgnoreCase(other.collectorNumber)
                    && condition.equalsIgnoreCase(other.condition)
                    && finish.equalsIgnoreCase(other.finish);
        }

        @Override
        public String toString() {
            return name + " (" + setCode + " #" + collectorNumber + ") [" + condition + "/" + finish + "] x" + quantity;
        }
    }

    private final List<WorkingItem> buffer = new ArrayList<>();
    private final List<WorkingItem> staging = new ArrayList<>();

    public synchronized List<WorkingItem> getBuffer() { return new ArrayList<>(buffer); }
    public synchronized List<WorkingItem> getStaging() { return new ArrayList<>(staging); }

    public synchronized void addItem(WorkingItem item) {
        if (item == null) return;
        for (WorkingItem existing : buffer) {
            if (existing.isDuplicateOf(item)) {
                existing.setQuantity(existing.getQuantity() + item.getQuantity());
                return;
            }
        }
        buffer.add(item);
    }

    public synchronized void addFromCard(Card c, String condition, String finish, int quantity) {
        if (c == null) return;
        String set = c.getSetCode() != null && !c.getSetCode().isBlank() ? c.getSetCode() : "MTG";
        addItem(new WorkingItem(c.getSku(), c.getName(), set, "1", "common", condition, finish, quantity));
    }

    public synchronized void applyBulkCondition(List<Integer> indices, String condition) {
        if (indices == null || condition == null) return;
        for (int idx : indices) {
            if (idx >= 0 && idx < buffer.size()) {
                buffer.get(idx).setCondition(condition);
            }
        }
    }

    public synchronized void applyBulkFinish(List<Integer> indices, String finish) {
        if (indices == null || finish == null) return;
        for (int idx : indices) {
            if (idx >= 0 && idx < buffer.size()) {
                buffer.get(idx).setFinish(finish);
            }
        }
    }

    public synchronized void groupDuplicates() {
        List<WorkingItem> consolidated = new ArrayList<>();
        for (WorkingItem item : buffer) {
            boolean merged = false;
            for (WorkingItem c : consolidated) {
                if (c.isDuplicateOf(item)) {
                    c.setQuantity(c.getQuantity() + item.getQuantity());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                consolidated.add(item);
            }
        }
        buffer.clear();
        buffer.addAll(consolidated);
    }

    public synchronized void stageSelected(List<Integer> indices) {
        if (indices == null || indices.isEmpty()) return;
        List<WorkingItem> toStage = new ArrayList<>();
        List<WorkingItem> remaining = new ArrayList<>();
        for (int i = 0; i < buffer.size(); i++) {
            if (indices.contains(i)) toStage.add(buffer.get(i));
            else remaining.add(buffer.get(i));
        }
        buffer.clear();
        buffer.addAll(remaining);
        staging.addAll(toStage);
    }

    public synchronized void stageAll() {
        staging.addAll(buffer);
        buffer.clear();
    }

    public synchronized void unstageAll() {
        buffer.addAll(staging);
        staging.clear();
    }

    public synchronized String commitStaged(String employeeName) throws Exception {
        if (staging.isEmpty()) return null;
        int totalCards = 0;
        for (WorkingItem item : staging) totalCards += item.getQuantity();

        String tag = AuditCommit.generateTag(employeeName, totalCards);
        String commitHash = AuditLog.recordCommit(tag, employeeName, totalCards);

        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number,"
                     + " rarity, condition, finish, language, status, box_id, commit_id, created_at)"
                     + " VALUES (?,?,?,?,?,?,?,?,?,'UNSTORED',NULL,?,CURRENT_TIMESTAMP)")) {
            for (WorkingItem item : staging) {
                for (int i = 0; i < item.getQuantity(); i++) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, item.getScryfallId());
                    ps.setString(3, item.getName());
                    ps.setString(4, item.getSetCode());
                    ps.setString(5, item.getCollectorNumber());
                    ps.setString(6, item.getRarity());
                    ps.setString(7, item.getCondition());
                    ps.setString(8, item.getFinish());
                    ps.setString(9, "EN");
                    ps.setString(10, commitHash);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }

        staging.clear();
        return tag;
    }

    public static void main(String[] args) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("working-buffer-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();

            WorkingBuffer wb = new WorkingBuffer();
            wb.addItem(new WorkingItem("id1", "Lightning Bolt", "2X2", "117", "common", "NM", "nonfoil", 2));
            wb.addItem(new WorkingItem("id1", "Lightning Bolt", "2X2", "117", "common", "NM", "nonfoil", 1));
            wb.addItem(new WorkingItem("id2", "Counterspell", "DMR", "45", "uncommon", "LP", "foil", 1));

            System.out.println("Buffer items before group: " + wb.getBuffer().size() + " (want 2)");
            wb.groupDuplicates();
            System.out.println("Lightning Bolt qty after auto-group: " + wb.getBuffer().get(0).getQuantity() + " (want 3)");

            // Bulk edit test
            wb.applyBulkCondition(List.of(1), "NM");
            System.out.println("Counterspell condition after bulk edit: " + wb.getBuffer().get(1).getCondition() + " (want NM)");

            // Stage and commit
            wb.stageAll();
            System.out.println("Staging size: " + wb.getStaging().size() + ", Buffer size: " + wb.getBuffer().size());

            String tag = wb.commitStaged("Karl");
            System.out.println("Committed tag: " + tag);
            System.out.println("Staging size after commit: " + wb.getStaging().size() + " (want 0)");

            // Verify SQLite persistence
            try (Connection conn = Db.open();
                 java.sql.PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM card_instances WHERE status = 'UNSTORED'");
                 java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("Persisted UNSTORED card count: " + rs.getInt(1) + " (want 4)");
                }
            }

            System.out.println("WorkingBuffer OK.");
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.println("WorkingBuffer ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
