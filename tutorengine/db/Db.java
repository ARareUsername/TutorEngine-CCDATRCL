package tutorengine.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
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
                // remove-auth-audit: employees / employee_sessions creation deleted
                // with the login system; pre-existing files cleaned in v<9 below.
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
                // remove-auth-audit: audit_commits / inventory_exceptions creation
                // deleted with the audit system; pre-existing files cleaned in v<9.
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
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "stored_at TIMESTAMP NULL,"
                        + "sku VARCHAR(50) DEFAULT '',"
                        + "set_name VARCHAR(100) DEFAULT '',"
                        + "color VARCHAR(20) DEFAULT '',"
                        + "card_type VARCHAR(50) DEFAULT '',"
                        + "release_year INT DEFAULT 0,"
                        + "price_php DOUBLE DEFAULT 0.0,"
                        + "demand INT DEFAULT 50,"
                        + "mana_cost VARCHAR(50) DEFAULT '',"
                        + "power VARCHAR(10) DEFAULT '',"
                        + "toughness VARCHAR(10) DEFAULT '',"
                        + "import_batch VARCHAR(100) NULL,"
                        + "FOREIGN KEY (box_id) REFERENCES box_containers(box_id))");
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
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                        + "stored_at TIMESTAMP NULL,"
                        + "version_seq INT DEFAULT 0,"
                        + "version_label VARCHAR(50) DEFAULT '',"
                        + "FOREIGN KEY (box_id) REFERENCES box_containers(box_id))");
                // If old table exists without the columns, rebuild.
                try {
                    s.execute("ALTER TABLE card_instances ADD COLUMN version_seq INT DEFAULT 0");
                } catch (Exception ignored) { }
                try {
                    s.execute("ALTER TABLE card_instances ADD COLUMN version_label VARCHAR(50) DEFAULT ''");
                } catch (Exception ignored) { }
                try {
                    s.execute("ALTER TABLE card_instances ADD COLUMN quantity INT DEFAULT 1");
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
            // remove-unused-views: trip_sessions / trip_cards creation deleted
            // with the Trips view; cleanup of pre-existing files in v<8 below.
            if (v < 5) {
                s.execute("CREATE TABLE IF NOT EXISTS intake_drafts ("
                        + "draft_id VARCHAR(36) PRIMARY KEY,"
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
                s.execute("CREATE INDEX IF NOT EXISTS idx_intake_drafts_created "
                        + "ON intake_drafts (created_at)");
                v = 5;
            }
            if (v < 6) {
                // rapid-intake-improvements: soft-delete flag on intake_drafts.
                try {
                    s.execute("ALTER TABLE intake_drafts ADD COLUMN removed INT DEFAULT 0");
                } catch (Exception ignored) { }
                v = 6;
            }
            if (v < 7) {
                // catalog-edit-quantities: add quantity column to card_instances
                try {
                    s.execute("ALTER TABLE card_instances ADD COLUMN quantity INT DEFAULT 1");
                } catch (Exception ignored) { }
                v = 7;
            }
            if (v < 8) {
                // remove-unused-views: drop trip tables left by the removed Trips view.
                s.execute("DROP TABLE IF EXISTS trip_cards");
                s.execute("DROP TABLE IF EXISTS trip_sessions");
                v = 8;
            }
            if (v < 9) {
                // remove-auth-audit: drop auth/audit tables and attribution columns.
                // card_instances carries NOT NULL commit_id + FK, so rebuild it.
                // Column list is intersected with the live table: partial-history DBs
                // may lack version_seq/version_label/quantity; missing ones keep defaults.
                var hasCards = false;
                try (var chk = s.executeQuery(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'card_instances'")) {
                    if (chk.next()) hasCards = chk.getInt(1) > 0;
                }
                s.execute("PRAGMA foreign_keys = OFF");
                try {
                    if (hasCards) {
                        s.execute("DROP TABLE IF EXISTS card_instances_tmp");
                        s.execute("CREATE TABLE card_instances_tmp ("
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
                                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                                + "stored_at TIMESTAMP NULL,"
                                + "version_seq INT DEFAULT 0,"
                                + "version_label VARCHAR(50) DEFAULT '',"
                                + "quantity INT DEFAULT 1,"
                                + "FOREIGN KEY (box_id) REFERENCES box_containers(box_id))");
                        var liveCols = new java.util.HashSet<String>();
                        try (var cols = s.executeQuery("PRAGMA table_info(card_instances)")) {
                            while (cols.next()) liveCols.add(cols.getString("name"));
                        }
                        String[] carry = {"id", "scryfall_id", "name", "set_code",
                                "collector_number", "rarity", "condition", "finish", "language",
                                "status", "box_id", "created_at", "stored_at",
                                "version_seq", "version_label", "quantity"};
                        var keep = new ArrayList<String>();
                        for (String col : carry) if (liveCols.contains(col)) keep.add(col);
                        String cols = String.join(", ", keep);
                        s.execute("INSERT INTO card_instances_tmp (" + cols + ")"
                                + " SELECT " + cols + " FROM card_instances");
                        s.execute("DROP TABLE card_instances");
                        s.execute("ALTER TABLE card_instances_tmp RENAME TO card_instances");
                        s.execute("CREATE INDEX IF NOT EXISTS idx_card_instances_box_status_name "
                                + "ON card_instances (box_id, status, name)");
                        s.execute("CREATE INDEX IF NOT EXISTS idx_card_instances_status "
                                + "ON card_instances (status)");
                    }
                } finally {
                    s.execute("PRAGMA foreign_keys = ON");
                }
                s.execute("DROP TABLE IF EXISTS inventory_exceptions");
                s.execute("DROP TABLE IF EXISTS audit_commits");
                s.execute("DROP TABLE IF EXISTS employee_sessions");
                s.execute("DROP TABLE IF EXISTS employees");
                s.execute("DROP INDEX IF EXISTS idx_intake_drafts_handle");
                try {
                    s.execute("ALTER TABLE intake_drafts DROP COLUMN employee_handle");
                } catch (Exception ignored) { }
                v = 9;
            }
            if (v < 10) {
                // db-backed-catalog: display columns + import-batch marker on card_instances.
                String[] adds = {
                    "ALTER TABLE card_instances ADD COLUMN sku VARCHAR(50) DEFAULT ''",
                    "ALTER TABLE card_instances ADD COLUMN set_name VARCHAR(100) DEFAULT ''",
                    "ALTER TABLE card_instances ADD COLUMN color VARCHAR(20) DEFAULT ''",
                    "ALTER TABLE card_instances ADD COLUMN card_type VARCHAR(50) DEFAULT ''",
                    "ALTER TABLE card_instances ADD COLUMN release_year INT DEFAULT 0",
                    "ALTER TABLE card_instances ADD COLUMN price_php DOUBLE DEFAULT 0.0",
                    "ALTER TABLE card_instances ADD COLUMN demand INT DEFAULT 50",
                    "ALTER TABLE card_instances ADD COLUMN mana_cost VARCHAR(50) DEFAULT ''",
                    "ALTER TABLE card_instances ADD COLUMN power VARCHAR(10) DEFAULT ''",
                    "ALTER TABLE card_instances ADD COLUMN toughness VARCHAR(10) DEFAULT ''",
                    "ALTER TABLE card_instances ADD COLUMN import_batch VARCHAR(100) NULL",
                };
                for (String ddl : adds) {
                    try {
                        s.execute(ddl);
                    } catch (Exception ignored) { }
                }
                v = 10;
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
