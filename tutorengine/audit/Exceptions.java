package tutorengine.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tutorengine.db.Db;

// OWNER: Bondoc, Karl B. — exception queue with two resolution paths.
public final class Exceptions {
    private Exceptions() {}

    public static String report(String cardInstanceId, String reportedBy, String targetBox,
                                String reason, String notes) throws Exception {
        String id = UUID.randomUUID().toString();
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO inventory_exceptions (exception_id, card_instance_id,"
                     + "reported_by, target_box_id, reason_code, notes) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, cardInstanceId);
            ps.setString(3, reportedBy);
            ps.setString(4, targetBox);
            ps.setString(5, reason);
            ps.setString(6, notes == null ? "" : notes);
            ps.executeUpdate();
        }
        return id;
    }

    // reason | card | box | notes rows for open exceptions, newest first.
    public static List<String[]> openQueue() throws Exception {
        List<String[]> out = new ArrayList<>();
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT e.exception_id, i.name, e.target_box_id, e.reason_code,"
                     + " e.notes, e.reported_by, e.created_at FROM inventory_exceptions e"
                     + " JOIN card_instances i ON i.id = e.card_instance_id"
                     + " WHERE e.resolved = 0 ORDER BY e.created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(new String[]{rs.getString(1), rs.getString(2),
                    rs.getString(3), rs.getString(4), rs.getString(5),
                    rs.getString(6), rs.getString(7)});
        }
        return out;
    }

    // BOX_FULL auto path: lock the box at 1000, reroute the card to the new
    // volume, resolve with both references. Returns false when misapplied.
    public static boolean resolveBoxFull(String exceptionId, String newBoxId, String resolver) throws Exception {
        String cardId;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT card_instance_id, target_box_id, reason_code FROM inventory_exceptions"
                     + " WHERE exception_id = ? AND resolved = 0")) {
            ps.setString(1, exceptionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !"BOX_FULL".equals(rs.getString(3))) return false;
                cardId = rs.getString(1);
                String box = rs.getString(2);
                try (PreparedStatement lock = c.prepareStatement(
                        "UPDATE box_containers SET current_card_count = 1000 WHERE box_id = ?")) {
                    lock.setString(1, box);
                    lock.executeUpdate();
                }
            }
        }
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE card_instances SET box_id = ?, status = 'UNSTORED' WHERE id = ?")) {
            ps.setString(1, newBoxId);
            ps.setString(2, cardId);
            ps.executeUpdate();
        }
        resolve(exceptionId, resolver);
        return true;
    }

    public static void resolve(String exceptionId, String resolver) throws Exception {
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE inventory_exceptions SET resolved = 1,"
                     + " resolved_at = CURRENT_TIMESTAMP, resolved_by = ? WHERE exception_id = ?")) {
            ps.setString(1, resolver);
            ps.setString(2, exceptionId);
            ps.executeUpdate();
        }
    }

    public static void main(String[] args) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("tutorengine-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();
            String commit = AuditLog.recordCommit("BATCH-t1-Karl_M-1cards", "Karl M.", 1);
            String cardId;
            try (Connection c = Db.open();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO card_instances (id, scryfall_id, name, set_code,"
                         + "collector_number, rarity, condition, finish, status, box_id, commit_id)"
                         + " VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                cardId = UUID.randomUUID().toString();
                ps.setString(1, cardId);
                ps.setString(2, "MTG-TSP-157");
                ps.setString(3, "Fury Sliver");
                ps.setString(4, "TSP");
                ps.setString(5, "157");
                ps.setString(6, "common");
                ps.setString(7, "NM");
                ps.setString(8, "Non-foil");
                ps.setString(9, "UNSTORED");
                ps.setString(10, "RACK-PON-LOW");
                ps.setString(11, commit);
                ps.executeUpdate();
            }
            String ex = report(cardId, "Karl M.", "RACK-PON-LOW", "BOX_FULL", "Box is full");
            System.out.println("open=" + openQueue().size() + " (want 1)");
            System.out.println("boxfull path=" + resolveBoxFull(ex, "CAB-RARE-LOW", "Sarah T."));
            System.out.println("open=" + openQueue().size() + " (want 0)");
            String ex2 = report(cardId, "Karl M.", "CAB-T-TOP", "NEIGHBOR_MISMATCH", "Misfiled?");
            resolve(ex2, "Sarah T.");
            System.out.println("manual path open=" + openQueue().size() + " (want 0)");
            System.out.println("Exceptions OK.");
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.println("EXCEPTIONS ERROR: " + e.getMessage());
        }
    }
}
