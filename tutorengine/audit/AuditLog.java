package tutorengine.audit;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import tutorengine.db.Db;

// OWNER: Bondoc, Karl B. — immutable audit trail over SQLite.
//
// PURPOSE: Every commit lands in audit_commits linked by prev_hash/row_hash
// (SHA-256 over previous hash + canonical fields). Original rows are never
// edited or deleted — corrections are compensating rows. verify() walks the
// chain; the browser reads filters + throughput off the same table.
public final class AuditLog {
    private AuditLog() {}

    public static String recordCommit(String tag, String employee, int count) throws Exception {
        String prev = lastHash();
        String hash = sha(prev + "|" + tag + "|" + employee + "|" + count);
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO audit_commits (commit_hash, commit_tag, employee_name,"
                     + "card_count, prev_hash, row_hash) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, hash);
            ps.setString(2, tag);
            ps.setString(3, employee);
            ps.setInt(4, count);
            ps.setString(5, prev);
            ps.setString(6, hash);
            ps.executeUpdate();
        }
        return hash;
    }

    public static String lastHash() throws Exception {
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT row_hash FROM audit_commits ORDER BY rowid DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    // True when every link recomputes; false at the first broken row.
    public static boolean verify() throws Exception {
        String prev = "";
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT commit_hash, commit_tag, employee_name, card_count,"
                     + "prev_hash, row_hash FROM audit_commits ORDER BY rowid");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String expect = sha(prev + "|" + rs.getString(2) + "|"
                        + rs.getString(3) + "|" + rs.getInt(4));
                if (!rs.getString(5).equals(prev) || !rs.getString(6).equals(expect)
                        || !rs.getString(1).equals(expect)) return false;
                prev = rs.getString(6);
            }
        }
        return true;
    }

    // tag | employee | count | timestamp rows, blank filters match all.
    public static List<String[]> browse(String employeeLike, String from, String to) throws Exception {
        List<String[]> out = new ArrayList<>();
        String sql = "SELECT commit_tag, employee_name, card_count, committed_at FROM audit_commits"
                + " WHERE (? = '' OR employee_name LIKE '%' || ? || '%')"
                + " AND (? = '' OR date(committed_at) >= date(?))"
                + " AND (? = '' OR date(committed_at) <= date(?))"
                + " ORDER BY committed_at DESC";
        try (Connection c = Db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            String e = employeeLike == null ? "" : employeeLike.trim();
            String f = from == null ? "" : from.trim();
            String t = to == null ? "" : to.trim();
            ps.setString(1, e); ps.setString(2, e);
            ps.setString(3, f); ps.setString(4, f);
            ps.setString(5, t); ps.setString(6, t);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new String[]{rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4)});
            }
        }
        return out;
    }

    // Total cards, distinct employees, cards/hour over the recorded span.
    public static String throughput() throws Exception {
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(card_count),0), COUNT(DISTINCT employee_name),"
                     + " MIN(committed_at), MAX(committed_at) FROM audit_commits");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next() || rs.getInt(1) == 0) return "no commits yet";
            int total = rs.getInt(1);
            int staff = rs.getInt(2);
            long spanSeconds = 0;
            try {
                spanSeconds = Math.max(1, java.time.Duration.between(
                        java.time.LocalDateTime.parse(rs.getString(3).replace(' ', 'T')),
                        java.time.LocalDateTime.parse(rs.getString(4).replace(' ', 'T'))).getSeconds());
            } catch (Exception ignored) { }
            double perHour = total * 3600.0 / Math.max(3600, spanSeconds);
            return total + " cards by " + staff + " staff (~"
                    + String.format("%.1f", perHour) + "/hour)";
        }
    }

    static String sha(String s) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public static void main(String[] args) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("tutorengine-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();
            for (int i = 1; i <= 10; i++) recordCommit("BATCH-t" + i + "-Karl_M-" + i + "cards", "Karl M.", i);
            System.out.println("10 events, verify=" + verify() + " (want true)");
            try (Connection c = Db.open();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE audit_commits SET card_count = 999 WHERE commit_tag = 'BATCH-t5-Karl_M-5cards'")) {
                ps.executeUpdate();
            }
            System.out.println("tampered, verify=" + !verify() + " (want true)");
            System.out.println("throughput: " + throughput());
            System.out.println("AuditLog OK.");
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.println("AUDIT ERROR: " + e.getMessage());
        }
    }
}
