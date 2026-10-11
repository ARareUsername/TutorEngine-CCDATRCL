package tutorengine.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

// OWNER: Bondoc, Karl B. — shared SQLite store. Admin-owned integration file.
//
// PURPOSE: One embedded database (`tutorengine.db`, git-ignored) for all
// operational tables. Migrations are numbered; later changes add their DDL as
// a new version below (single registry, single migration path).
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.db.Db
//   Expected: "migrated to version 1" and the file exists; rerun is a no-op.
//   Tests use -Dtutorengine.db=<tempfile> to avoid touching the real database.
public final class Db {
    private Db() {}

    public static String file() {
        String override = System.getProperty("tutorengine.db");
        return override == null || override.isBlank() ? "tutorengine.db" : override;
    }

    public static Connection open() throws Exception {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA busy_timeout = 5000");
        }
        return c;
    }

    // Later changes: add `if (v < N) { ...; v = N; }` blocks here in order.
    public static synchronized int migrate() throws Exception {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version (version INT NOT NULL)");
            int v = 0;
            var rs = s.executeQuery("SELECT version FROM schema_version");
            if (rs.next()) v = rs.getInt(1);
            rs.close();
            if (v < 1) {
                s.execute("CREATE TABLE IF NOT EXISTS employees ("
                        + "employee_id VARCHAR(36) PRIMARY KEY,"
                        + "username VARCHAR(50) UNIQUE NOT NULL,"
                        + "display_name VARCHAR(100) NOT NULL,"
                        + "pin_hash VARCHAR(256) NOT NULL,"
                        + "badge_barcode VARCHAR(64) UNIQUE,"
                        + "role VARCHAR(20) NOT NULL DEFAULT 'CLERK',"
                        + "is_active INT DEFAULT 1,"
                        + "avatar_color_hex VARCHAR(7) DEFAULT '#06b6d4',"
                        + "lock_until BIGINT DEFAULT 0,"
                        + "fail_count INT DEFAULT 0,"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "last_login TIMESTAMP NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS employee_sessions ("
                        + "session_id VARCHAR(36) PRIMARY KEY,"
                        + "employee_id VARCHAR(36) NOT NULL,"
                        + "login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "logout_time TIMESTAMP NULL,"
                        + "cards_staged_count INT DEFAULT 0,"
                        + "commits_completed_count INT DEFAULT 0,"
                        + "FOREIGN KEY (employee_id) REFERENCES employees(employee_id))");
                v = 1;
            }
            s.execute("DELETE FROM schema_version");
            s.execute("INSERT INTO schema_version (version) VALUES (" + v + ")");
            return v;
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("db=" + Path.of(file()).toAbsolutePath());
            System.out.println("migrated to version " + migrate());
            System.out.println("Db OK.");
        } catch (Exception e) {
            System.out.println("DB ERROR: " + e.getMessage());
        }
    }
}
