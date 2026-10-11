package tutorengine.auth;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import tutorengine.db.Db;

// OWNER: Bondoc, Karl B. — employee accounts. PBKDF2 stdlib crypto (no dep).
//
// PURPOSE: Register/verify/deactivate employees; 4-digit PINs verified against
// salted hashes (never stored); 5-strike temporary lockout; badge-barcode
// match; login/logout session rows. Role checks live with the callers
// (task 2.1); this file only moves rows.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.auth.Employees
//   Expected: register → verify ok → wrong PIN fails ×5 locks → badge match →
//   reset → deactivate hides from active list. Runs on a temp database;
//   the real tutorengine.db is never touched by the demo.
public final class Employees {
    private static final int ITERATIONS = 200_000;
    private static final int MAX_FAILS = 5;
    private static final long LOCK_MS = 5L * 60 * 1000;

    private Employees() {}

    public record Employee(String id, String username, String displayName,
                           String role, boolean active, String color, String lastLogin) {}

    public static String register(String username, String displayName, String pin,
                                  String role, String badge, String color) throws Exception {
        if (username == null || username.isBlank() || pin == null || !pin.matches("\\d{4}"))
            throw new IllegalArgumentException("username required; PIN must be 4 digits");
        String id = UUID.randomUUID().toString();
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO employees (employee_id, username, display_name, pin_hash,"
                     + "badge_barcode, role, avatar_color_hex) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, username.trim().toLowerCase());
            ps.setString(3, displayName == null || displayName.isBlank() ? username : displayName.trim());
            ps.setString(4, hash(pin));
            ps.setString(5, badge == null || badge.isBlank() ? null : badge.trim());
            ps.setString(6, role == null || role.isBlank() ? "CLERK" : role.trim().toUpperCase());
            ps.setString(7, color == null || color.isBlank() ? "#06b6d4" : color.trim());
            ps.executeUpdate();
        }
        return id;
    }

    // Null = wrong PIN, unknown user, locked, or deactivated (never says which).
    public static Employee verifyPin(String username, String pin) throws Exception {
        if (username == null || pin == null) return null;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM employees WHERE username = ?")) {
            ps.setString(1, username.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                if (rs.getInt("is_active") != 1) return null;
                if (rs.getLong("lock_until") > System.currentTimeMillis()) return null;
                if (check(pin, rs.getString("pin_hash"))) {
                    clearFails(c, username.trim().toLowerCase());
                    touchLogin(c, username.trim().toLowerCase());
                    return row(rs);
                }
                noteFail(c, rs);
                return null;
            }
        }
    }

    public static Employee findByBadge(String barcode) throws Exception {
        if (barcode == null || barcode.isBlank()) return null;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM employees WHERE badge_barcode = ? AND is_active = 1")) {
            ps.setString(1, barcode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getLong("lock_until") > System.currentTimeMillis()) return null;
                touchLogin(c, rs.getString("username"));
                return row(rs);
            }
        }
    }

    public static boolean resetPin(String username, String newPin) throws Exception {
        if (newPin == null || !newPin.matches("\\d{4}")) return false;
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE employees SET pin_hash = ?, fail_count = 0, lock_until = 0 WHERE username = ?")) {
            ps.setString(1, hash(newPin));
            ps.setString(2, username.trim().toLowerCase());
            return ps.executeUpdate() == 1;
        }
    }

    public static void deactivate(String username) throws Exception {
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE employees SET is_active = 0 WHERE username = ?")) {
            ps.setString(1, username.trim().toLowerCase());
            ps.executeUpdate();
        }
    }

    public static List<Employee> listActive() throws Exception {
        List<Employee> out = new ArrayList<>();
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM employees WHERE is_active = 1 ORDER BY display_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(row(rs));
        }
        return out;
    }

    public static boolean empty() throws Exception {
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM employees");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    public static String openSession(String employeeId) throws Exception {
        String id = UUID.randomUUID().toString();
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO employee_sessions (session_id, employee_id) VALUES (?,?)")) {
            ps.setString(1, id);
            ps.setString(2, employeeId);
            ps.executeUpdate();
        }
        return id;
    }

    public static void closeSession(String sessionId) throws Exception {
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE employee_sessions SET logout_time = CURRENT_TIMESTAMP WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        }
    }

    static String hash(String pin) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)).getEncoded();
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(key);
    }

    static boolean check(String pin, String stored) throws Exception {
        String[] parts = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(parts[1]);
        byte[] want = Base64.getDecoder().decode(parts[2]);
        byte[] got = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(pin.toCharArray(), salt,
                        Integer.parseInt(parts[0]), want.length * 8)).getEncoded();
        return java.security.MessageDigest.isEqual(want, got);
    }

    private static void noteFail(Connection c, ResultSet rs) throws Exception {
        int fails = rs.getInt("fail_count") + 1;
        long lock = fails >= MAX_FAILS ? System.currentTimeMillis() + LOCK_MS : 0;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE employees SET fail_count = ?, lock_until = ? WHERE username = ?")) {
            ps.setInt(1, fails);
            ps.setLong(2, lock);
            ps.setString(3, rs.getString("username"));
            ps.executeUpdate();
        }
    }

    private static void clearFails(Connection c, String username) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE employees SET fail_count = 0, lock_until = 0 WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        }
    }

    private static void touchLogin(Connection c, String username) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE employees SET last_login = CURRENT_TIMESTAMP WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
        }
    }

    private static Employee row(ResultSet rs) throws Exception {
        return new Employee(rs.getString("employee_id"), rs.getString("username"),
                rs.getString("display_name"), rs.getString("role"),
                rs.getInt("is_active") == 1, rs.getString("avatar_color_hex"),
                rs.getString("last_login"));
    }

    public static void main(String[] args) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("tutorengine-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();
            System.out.println("empty=" + empty() + " (want true)");
            register("karl_m", "Karl M.", "1234", "CLERK", "BADGE-1", "#059669");
            System.out.println("verify ok=" + (verifyPin("karl_m", "1234") != null));
            System.out.println("verify wrong=" + (verifyPin("karl_m", "0000") == null) + " (want true)");
            System.out.println("badge=" + (findByBadge("BADGE-1") != null) + " (want true, pre-lockout)");
            for (int i = 0; i < 4; i++) verifyPin("karl_m", "0000");
            System.out.println("locked out=" + (verifyPin("karl_m", "1234") == null) + " (want true after 5 fails)");
            System.out.println("reset=" + resetPin("karl_m", "4321")
                    + " verify=" + (verifyPin("karl_m", "4321") != null));
            String s = openSession(verifyPin("karl_m", "4321").id());
            closeSession(s);
            System.out.println("session open/close ok");
            deactivate("karl_m");
            System.out.println("active=" + listActive().size() + " (want 0)");
            System.out.println("Employees OK.");
            java.nio.file.Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.println("EMPLOYEES ERROR: " + e.getMessage());
        }
    }
}
