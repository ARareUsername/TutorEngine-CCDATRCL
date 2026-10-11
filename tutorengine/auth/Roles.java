package tutorengine.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tutorengine.db.Db;

// OWNER: Bondoc, Karl B. — role boundaries. Pure logic + CSV export; the UI
// calls these before privileged acts. Rollback/reconcile screens (later
// changes) reuse canRollback; PIN-reset UI calls resetPin through isManager.
public final class Roles {
    private static final long ROLLBACK_WINDOW_SECONDS = 300;

    private Roles() {}

    public static boolean isManager(Employees.Employee e) {
        if (e == null) return false;
        String r = e.role() == null ? "" : e.role().trim().toUpperCase();
        return r.equals("MANAGER") || r.equals("ADMIN");
    }

    // Own-commit rollback only: same user, within 5 minutes, all still unstored.
    public static boolean canRollback(Employees.Employee requester, String commitOwnerUsername,
                                      Instant commitTime, boolean allUnstored) {
        return canRollback(requester, commitOwnerUsername, commitTime, allUnstored, Instant.now());
    }

    public static boolean canRollback(Employees.Employee requester, String commitOwnerUsername,
                                      Instant commitTime, boolean allUnstored, Instant now) {        if (requester == null || commitOwnerUsername == null || commitTime == null) return false;
        if (!requester.username().equalsIgnoreCase(commitOwnerUsername)) return false;
        if (!allUnstored) return false;
        long age = Duration.between(commitTime, now).getSeconds();
        return age >= 0 && age <= ROLLBACK_WINDOW_SECONDS;
    }

    public static boolean canResetPin(Employees.Employee e) { return isManager(e); }

    public static boolean canReconcile(Employees.Employee e) { return isManager(e); }

    public static boolean canExport(Employees.Employee e) { return isManager(e); }

    // Cards-per-shift/week per employee from session rows. Returns data rows.
    public static List<String[]> metricsRows() throws Exception {
        List<String[]> out = new ArrayList<>();
        try (Connection c = Db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT e.display_name, e.username,"
                     + " COUNT(s.session_id) AS shifts,"
                     + " COALESCE(SUM(s.cards_staged_count),0) AS staged,"
                     + " COALESCE(SUM(s.commits_completed_count),0) AS commits"
                     + " FROM employees e LEFT JOIN employee_sessions s"
                     + " ON s.employee_id = e.employee_id"
                     + " WHERE e.is_active = 1 GROUP BY e.employee_id ORDER BY e.display_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(new String[]{rs.getString(1), rs.getString(2),
                    rs.getString(3), rs.getString(4), rs.getString(5)});
        }
        return out;
    }

    public static int exportCsv(Path dest) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("display_name,username,shifts,cards_staged,commits_completed");
        List<String[]> rows = metricsRows();
        for (String[] r : rows) lines.add(String.join(",", r));
        Files.write(dest, lines);
        return rows.size();
    }

    public static void main(String[] args) {
        try {
            Path tmp = Files.createTempFile("tutorengine-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();
            Employees.register("karl_m", "Karl M.", "1234", "CLERK", null, null);
            Employees.register("sarah_t", "Sarah T.", "5678", "MANAGER", null, null);
            Employees.Employee clerk = Employees.verifyPin("karl_m", "1234");
            Employees.Employee mgr = Employees.verifyPin("sarah_t", "5678");
            Instant now = Instant.now();
            System.out.println("own 60s=" + canRollback(clerk, "karl_m", now.minusSeconds(60), true) + " (want true)");
            System.out.println("other=" + !canRollback(clerk, "sarah_t", now.minusSeconds(60), true) + " (want true)");
            System.out.println("late=" + !canRollback(clerk, "karl_m", now.minusSeconds(400), true) + " (want true)");
            System.out.println("stored=" + !canRollback(clerk, "karl_m", now.minusSeconds(60), false) + " (want true)");
            System.out.println("mgr reset=" + canResetPin(mgr) + " clerk reset=" + !canResetPin(clerk));
            System.out.println("mgr export=" + canExport(mgr) + " clerk export=" + !canExport(clerk));
            Path csv = Files.createTempFile("metrics-", ".csv");
            System.out.println("export rows=" + exportCsv(csv) + " (want 2)");
            System.out.println("Roles OK.");
            Files.deleteIfExists(csv);
            Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.println("ROLES ERROR: " + e.getMessage());
        }
    }
}
