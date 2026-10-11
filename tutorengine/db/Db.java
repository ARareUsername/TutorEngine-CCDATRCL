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
            if (v < 2) {
                // audit-reconciliation: source-spec schema verbatim (+ hash pair
                // for chain verification beside the spec DDL).
                s.execute("CREATE TABLE IF NOT EXISTS box_containers ("
                        + "box_id VARCHAR(50) PRIMARY KEY,"
                        + "display_label VARCHAR(100) NOT NULL,"
                        + "shelf_unit VARCHAR(50) NOT NULL,"
                        + "shelf_tier VARCHAR(50) NOT NULL,"
                        + "column_index INT NOT NULL,"
                        + "stack_position INT NOT NULL,"
                        + "max_capacity INT DEFAULT 1000,"
                        + "current_card_count INT DEFAULT 0,"
                        + "sort_policy VARCHAR(30) DEFAULT 'NAME_ASC')");
                s.execute("CREATE TABLE IF NOT EXISTS audit_commits ("
                        + "commit_hash VARCHAR(64) PRIMARY KEY,"
                        + "commit_tag VARCHAR(120) NOT NULL,"
                        + "employee_name VARCHAR(100) NOT NULL,"
                        + "card_count INT NOT NULL,"
                        + "committed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "prev_hash VARCHAR(64) NOT NULL DEFAULT '',"
                        + "row_hash VARCHAR(64) NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS card_instances ("
                        + "id VARCHAR(36) PRIMARY KEY,"
                        + "scryfall_id VARCHAR(36) NOT NULL,"
                        + "name VARCHAR(255) NOT NULL,"
                        + "set_code VARCHAR(10) NOT NULL,"
                        + "collector_number VARCHAR(20) NOT NULL,"
                        + "rarity VARCHAR(20) NOT NULL,"
                        + "condition VARCHAR(10) NOT NULL,"
                        + "finish VARCHAR(20) NOT NULL,"
                        + "language VARCHAR(10) DEFAULT 'EN',"
                        + "status VARCHAR(20) NOT NULL,"
                        + "box_id VARCHAR(50),"
                        + "commit_id VARCHAR(64) NOT NULL,"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "stored_at TIMESTAMP NULL,"
                        + "FOREIGN KEY (box_id) REFERENCES box_containers(box_id),"
                        + "FOREIGN KEY (commit_id) REFERENCES audit_commits(commit_hash))");
                s.execute("CREATE TABLE IF NOT EXISTS inventory_exceptions ("
                        + "exception_id VARCHAR(36) PRIMARY KEY,"
                        + "card_instance_id VARCHAR(36) NOT NULL,"
                        + "reported_by VARCHAR(100) NOT NULL,"
                        + "target_box_id VARCHAR(50) NOT NULL,"
                        + "reason_code VARCHAR(50) NOT NULL,"
                        + "notes TEXT,"
                        + "resolved INT DEFAULT 0,"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "resolved_at TIMESTAMP NULL,"
                        + "resolved_by VARCHAR(100) NULL,"
                        + "FOREIGN KEY (card_instance_id) REFERENCES card_instances(id),"
                        + "FOREIGN KEY (target_box_id) REFERENCES box_containers(box_id))");
                // Seed the 9 guide slots as containers (counts land via intake).
                String[][] seed = {
                    {"RACK-M-TOP", "Black rack top: core M10-M21", "Black Rack", "TOP"},
                    {"RACK-M-MID", "Black rack middle: Modern + Standard M", "Black Rack", "MID"},
                    {"RACK-L-TOP", "Black rack top-right: L cluster", "Black Rack", "TOP"},
                    {"RACK-PON-LOW", "Black rack lower: P/O/N + others", "Black Rack", "LOW"},
                    {"CAB-T-TOP", "White cabinet top: T cluster", "White Cabinet", "TOP"},
                    {"CAB-UW-MID", "White cabinet middle: Urza/Un/W", "White Cabinet", "MID"},
                    {"CAB-LANDS-LOW", "White cabinet bottom: lands", "White Cabinet", "LOW"},
                    {"CAB-TOKEN-LOW", "White cabinet bottom: tokens", "White Cabinet", "LOW"},
                    {"CAB-RARE-LOW", "White cabinet bottom: bulk rares + overflow", "White Cabinet", "LOW"},
                };
                for (int i = 0; i < seed.length; i++) {
                    s.execute("INSERT OR IGNORE INTO box_containers (box_id, display_label,"
                            + "shelf_unit, shelf_tier, column_index, stack_position)"
                            + " VALUES ('" + seed[i][0] + "','" + seed[i][1].replace("'", "''")
                            + "','" + seed[i][2] + "','" + seed[i][3] + "'," + i + ",0)");
                }
                v = 2;
            }
            if (v < 3) {
                // card-version-priority: version fields on card_instances.
                // ALTER TABLE is additive (safe for existing DBs).
                s.execute("CREATE TABLE IF NOT EXISTS card_instances_tmp ("
                        + "id VARCHAR(36) PRIMARY KEY,"
                        + "scryfall_id VARCHAR(36) NOT NULL,"
                        + "name VARCHAR(255) NOT NULL,"
                        + "set_code VARCHAR(10) NOT NULL,"
                        + "collector_number VARCHAR(20) NOT NULL,"
                        + "rarity VARCHAR(20) NOT NULL,"
                        + "condition VARCHAR(10) NOT NULL,"
                        + "finish VARCHAR(20) NOT NULL,"
                        + "language VARCHAR(10) DEFAULT 'EN',"
                        + "status VARCHAR(20) NOT NULL,"
                        + "box_id VARCHAR(50),"
                        + "commit_id VARCHAR(64) NOT NULL,"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "stored_at TIMESTAMP NULL,"
                        + "version_seq INT DEFAULT 0,"
                        + "version_label VARCHAR(50) DEFAULT '',"
                        + "FOREIGN KEY (box_id) REFERENCES box_containers(box_id),"
                        + "FOREIGN KEY (commit_id) REFERENCES audit_commits(commit_hash))");
                // If old table exists without the columns, rebuild.
                try {
                    s.execute("ALTER TABLE card_instances ADD COLUMN version_seq INT DEFAULT 0");
                } catch (Exception ignored) { }
                try {
                    s.execute("ALTER TABLE card_instances ADD COLUMN version_label VARCHAR(50) DEFAULT ''");
                } catch (Exception ignored) { }
                s.execute("DROP TABLE IF EXISTS card_instances_tmp");
                v = 3;
            }
            if (v < 3) {
                s.execute("CREATE INDEX IF NOT EXISTS idx_card_instances_box_status_name "
                        + "ON card_instances (box_id, status, name)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_card_instances_status "
                        + "ON card_instances (status)");
                v = 3;
            }
            if (v < 4) {
                s.execute("CREATE TABLE IF NOT EXISTS trip_sessions ("
                        + "trip_id VARCHAR(36) PRIMARY KEY,"
                        + "box_id VARCHAR(50) NOT NULL,"
                        + "clerk_name VARCHAR(100) NOT NULL,"
                        + "total_cards INT NOT NULL,"
                        + "status VARCHAR(20) DEFAULT 'ACTIVE',"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "completed_at TIMESTAMP NULL,"
                        + "FOREIGN KEY (box_id) REFERENCES box_containers(box_id))");
                s.execute("CREATE TABLE IF NOT EXISTS trip_cards ("
                        + "trip_id VARCHAR(36) NOT NULL,"
                        + "card_id VARCHAR(36) NOT NULL,"
                        + "card_name VARCHAR(255) NOT NULL,"
                        + "set_code VARCHAR(10),"
                        + "sort_order INT NOT NULL,"
                        + "state VARCHAR(20) DEFAULT 'PENDING',"
                        + "exclude_reason VARCHAR(50) NULL,"
                        + "notes TEXT,"
                        + "PRIMARY KEY (trip_id, card_id),"
                        + "FOREIGN KEY (trip_id) REFERENCES trip_sessions(trip_id))");
                v = 4;
            }
            if (v < 5) {
                s.execute("CREATE TABLE IF NOT EXISTS intake_drafts ("
                        + "draft_id VARCHAR(36) PRIMARY KEY,"
                        + "employee_handle VARCHAR(100) NOT NULL,"
                        + "batch_id VARCHAR(100),"
                        + "scryfall_id VARCHAR(36) NOT NULL,"
                        + "name VARCHAR(255) NOT NULL,"
                        + "set_code VARCHAR(10) NOT NULL,"
                        + "collector_number VARCHAR(20) NOT NULL,"
                        + "condition VARCHAR(10) DEFAULT 'NM',"
                        + "finish VARCHAR(20) DEFAULT 'NONFOIL',"
                        + "quantity INT DEFAULT 1,"
                        + "price_php DOUBLE DEFAULT 0.0,"
                        + "image_uri TEXT,"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_intake_drafts_handle "
                        + "ON intake_drafts (employee_handle, created_at)");
                v = 5;
            }
            if (v < 6) {
                // rapid-intake-improvements: soft-delete flag on intake_drafts.
                try {
                    s.execute("ALTER TABLE intake_drafts ADD COLUMN removed INT DEFAULT 0");
                } catch (Exception ignored) { }
                v = 6;
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
