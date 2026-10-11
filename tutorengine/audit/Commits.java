package tutorengine.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tutorengine.auth.Roles;
import tutorengine.auth.UserSessionManager;
import tutorengine.db.Db;
import tutorengine.model.Card;

// OWNER: Bondoc, Karl B. — batch commits with grace rollback.
//
// PURPOSE: A commit snapshots cards into card_instances (UNSTORED) under one
// audit tag. Rollback inside 5 minutes, by the committing clerk, while all
// cards are still UNSTORED, writes a compensating ROLLBACK row and deletes the
// instances (audit rows themselves are never touched).
public final class Commits {
    private Commits() {}

    public static String commitBatch(String employeeDisplay, String username, List<Card> cards) throws Exception {
        String tag = UserSessionManager.commitTag(employeeDisplay, cards.size());
        String hash = AuditLog.recordCommit(tag, employeeDisplay, cards.size());
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO card_instances (id, scryfall_id, name, set_code,"
                     + "collector_number, rarity, condition, finish, language, status,"
                     + "box_id, commit_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (Card k : cards) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, k.getSku());
                ps.setString(3, k.getName());
                ps.setString(4, k.getSetCode() == null ? "" : k.getSetCode());
                ps.setString(5, "");
                ps.setString(6, "");
                ps.setString(7, "NM");
                ps.setString(8, k.isFoil() ? "Foil" : "Non-foil");
                ps.setString(9, "EN");
                ps.setString(10, "UNSTORED");
                ps.setString(11, null);
                ps.setString(12, hash);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return hash;
    }

    public static boolean rollback(String commitHash, String requesterUsername) throws Exception {
        return rollback(commitHash, requesterUsername, Instant.now());
    }

    static boolean rollback(String commitHash, String requesterUsername, Instant now) throws Exception {
        String ownerDisplay;
        Instant at;
        int total;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT employee_name, committed_at,"
                     + " (SELECT COUNT(*) FROM card_instances WHERE commit_id = ?) FROM audit_commits"
                     + " WHERE commit_hash = ?")) {
            ps.setString(1, commitHash);
            ps.setString(2, commitHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                ownerDisplay = rs.getString(1);
                at = Instant.parse(rs.getString(2).replace(' ', 'T') + "Z");
                total = rs.getInt(3);
            }
        }
        int unstored;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM card_instances WHERE commit_id = ? AND status = 'UNSTORED'")) {
            ps.setString(1, commitHash);
            try (ResultSet rs = ps.executeQuery()) {
                unstored = rs.next() ? rs.getInt(1) : 0;
            }
        }
        String ownerUsername = usernameOfDisplay(ownerDisplay);
        if (ownerUsername == null) return false; // owner left the roster: no one may roll back
        if (!Roles.canRollback(EmployeesAdapter.of(requesterUsername), ownerUsername, at, unstored == total, now))
            return false;
        String tag = "ROLLBACK-" + commitHash.substring(0, 8) + "-" + requesterUsername + "-" + now.getEpochSecond();
        AuditLog.recordCommit(tag, displayOf(requesterUsername), total);
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM card_instances WHERE commit_id = ?")) {
            ps.setString(1, commitHash);
            ps.executeUpdate();
        }
        return true;
    }

    private static String usernameOfDisplay(String display) throws Exception {
        for (tutorengine.auth.Employees.Employee e : tutorengine.auth.Employees.listActive())
            if (e.displayName().equals(display)) return e.username();
        return null;
    }

    private static String displayOf(String username) throws Exception {
        for (tutorengine.auth.Employees.Employee e : tutorengine.auth.Employees.listActive())
            if (e.username().equalsIgnoreCase(username)) return e.displayName();
        return username;
    }

    public static void main(String[] args) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("tutorengine-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();
            tutorengine.auth.Employees.register("karl_m", "Karl M.", "1234", "CLERK", null, null);
            List<Card> five = new ArrayList<>();
            for (int i = 1; i <= 5; i++)
                five.add(new Card("T-" + i, "Card " + i, "S", "White", "Creature",
                        2024, 1.0, 1, 50, false, "Bulk Box A (White/Blue)"));
            String h = commitBatch("Karl M.", "karl_m", five);
            System.out.println("committed, rollback 60s=" + rollback(h, "karl_m",
                    Instant.now().plusSeconds(60)) + " (want true)");
            String h2 = commitBatch("Karl M.", "karl_m", five);
            System.out.println("rollback other=" + !rollback(h2, "sarah_t", Instant.now()) + " (want true)");
            System.out.println("rollback late=" + !rollback(h2, "karl_m",
                    Instant.now().plusSeconds(400)) + " (want true)");
            System.out.println("chain intact=" + AuditLog.verify());
            // 5000-row ingest sanity (scale arm).
            List<Card> big = new ArrayList<>();
            for (int i = 1; i <= 5000; i++)
                big.add(new Card("G-" + i, "Gen " + i, "S", "White", "Creature",
                        2024, 1.0, 1, 50, false, "Bulk Box A (White/Blue)"));
            long t0 = System.nanoTime();
            commitBatch("Karl M.", "karl_m", big);
            System.out.println("5000-row ingest ms=" + ((System.nanoTime() - t0) / 1000000));
            System.out.println("Commits OK.");
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.println("COMMIT ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Adapter so Roles.canRollback (typed on Employees.Employee) accepts a bare username.
    private static final class EmployeesAdapter {
        static tutorengine.auth.Employees.Employee of(String username) {
            return new tutorengine.auth.Employees.Employee("", username, username,
                    "CLERK", true, "#06b6d4", null);
        }
    }
}
