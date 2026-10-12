package tutorengine.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tutorengine.model.Card;

// OWNER: David — Data & Loader. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: Read Dataset/cards.csv and convert each row into a Card object.
// Every other feature (search, sorting, benchmarks) consumes the list built here,
// so a crash in this file breaks the whole app. The CSV header must stay exactly:
//   sku,name,setName,color,cardType,releaseYear,price,quantity,demandScore,isFoil,boxLocationId,manaCost,power,toughness,setCode
// (11-col legacy rows still load, with the 4 new fields blank.)
//
// HOW TO IMPLEMENT:
//   1. In load(path): read all lines with Files.readAllLines(Path.of(path)).
//      Line 0 is the header — start the loop at line 1 and skip blank lines.
//   2. Split each line on commas with split(",", -1) so trailing empty fields survive.
//      Card names containing commas are wrapped in double quotes in the file, so
//      strip one surrounding pair of quotes from the name field when present.
//   3. Convert and validate every field before building the Card:
//        double price = Double.parseDouble(p[6].trim());
//        int qty = Integer.parseInt(p[7].trim());
//        int demand = Integer.parseInt(p[8].trim());
//        boolean foil = Boolean.parseBoolean(p[9].trim());
//      Reject the row when price < 0, quantity < 0, or demand is outside 1-100.
//      Accepted USD prices convert to PHP via toPhp() (USD_TO_PHP, Oct 2026).
//   4. A rejected or unparsable row prints "SKIP line N: <reason>" where N is the
//      real file line number (loop index + 1), then the loop continues. Wrap the
//      per-row parsing in try/catch (NumberFormatException) so one malformed
//      number is skipped, never thrown — a single bad row must never abort the load.
//   5. A good row becomes new Card(sku, name, setName, color, cardType, releaseYear,
//      price, qty, demand, foil, boxId) and is appended to the result list.
//   6. In main(): call load("Dataset/cards.csv"), print the list size, then print
//      the first 3 cards via toString(). Keep one deliberately broken row in the
//      CSV (or a copy) to demonstrate the SKIP path in the demo.
//   7. Report: describe every CSV column (Template C) and the data origin
//      (Scryfall Default Cards 2026-10-04, converted 2026-10-05) in section S2.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.util.CSVLoader
//   Expected: "Loaded 60 cards (need >= 50).", 3 sample card lines, and (with a
//   deliberately broken row planted) one "SKIP line N: ..." line while the
//   count still prints. Implemented 2026-10-05; David owns the trace + report rows.
//
// DEFENSE: why an ArrayList stages the data (positional access the sorts rely on).
// SPEC: S6 dataset, S11 load/generate.
public class CSVLoader {
    // Scryfall prices are USD; the store sells in Philippine Peso. Mid-market
    // rate 1 USD = 62.80 PHP (Oct 2026, documented for the report §2/S2).
    public static final double USD_TO_PHP = 62.80;

    // USD dollars -> PHP centavos, half-up.

// Cache: lower-case name -> {collectorNumber, versionSeq, versionLabel}
// Populated on first access; avoids a separate Scryfall call for every card.
    private static final java.util.HashMap<String, String[]> SCO_FALLBACK_CACHE = new java.util.HashMap<>();

    // Cache: set code -> next box number (for per-set box naming like FRA-1, TSR-1)
    // Populated on first use; increments each time a box is assigned for that set.
    private static final java.util.HashMap<String, Integer> BOX_NAME_COUNTER = new java.util.HashMap<>();
    static double toPhp(double usd) {
        return Math.round(usd * USD_TO_PHP * 100.0) / 100.0;
    }

    public static List<Card> load(String path) {
        // Implemented 2026-10-05 by Bondoc, Karl B. (David's file — unblocking).
        List<Card> out = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (Exception e) {
            System.out.println("LOAD ERROR: cannot read " + path + " (" + e.getMessage() + ")");
            return out; // empty, never null — callers stay simple
        }
        for (int i = 1; i < lines.size(); i++) { // line 0 is the header
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] p = splitCsv(line);
            if (p.length != 11 && p.length != 15 && p.length != 16) {
                System.out.println("SKIP line " + (i + 1) + ": expected 11, 15, or 16 columns, got " + p.length);
                continue;
            }
            try {
                String sku = p[0].trim();
                String name = p[1].trim();
                if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
                    name = name.substring(1, name.length() -1);
                }
                String setName = p[2].trim();
                String color = p[3].trim();
                String cardType = p[4].trim();
                int year = Integer.parseInt(p[5].trim());
                double price = Double.parseDouble(p[6].trim());
                int qty = Integer.parseInt(p[7].trim());
                int demand = Integer.parseInt(p[8].trim());
                boolean foil = Boolean.parseBoolean(p[9].trim());
                // Use <set>-<box number> as box location ID (e.g. BLB-1, TSP-1) matching unified naming
                String code = p.length >= 15 ? p[14].trim().toUpperCase() : "";
                String box = code.isEmpty() ? "Unassigned" : code + "-1";
                String mana = p.length >= 12 ? p[11].trim() : "";
                String pw = p.length >= 13 ? p[12].trim() : "";
                String tw = p.length >= 14 ? p[13].trim() : "";
                String imageUrl = p.length >= 16 ? p[15].trim() : "";
                if (sku.isEmpty() || name.isEmpty()) {
                    System.out.println("SKIP line " + (i + 1) + ": empty SKU or name");
                    continue;
                }
                if (price < 0 || qty < 0 || demand < 1 || demand > 100) {
                    System.out.println("SKIP line " + (i + 1) + ": price/qty/demand out of range");
                    continue;
                }
                double php = toPhp(price); // CSV holds USD; Cards carry PHP
                // Fetch collector number and version label from Scryfall (cached)
                String[] scryfallInfo = fetchScryfallInfo(name);
                String collectorNumber = scryfallInfo[0];
                String versionLabel = scryfallInfo[2];
                out.add(new Card(sku, name, setName, color, cardType,
                        year, php, qty, demand, foil, box, mana, pw, tw, code, imageUrl,
                        collectorNumber, 1, versionLabel));
            } catch (NumberFormatException e) {
                System.out.println("SKIP line " + (i + 1) + ": bad number");
            }
        }
        return out;
    }

    // Split on commas, ignoring commas inside double quotes (the quotes themselves
    // are dropped). Limitation: a literal "" escape inside a quoted name is kept
    // as-is instead of unescaped — no card name in our data needs it.
    static String[] splitCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') inQuotes = !inQuotes;
            else if (ch == ',' && !inQuotes) { fields.add(cur.toString()); cur.setLength(0); }
            else cur.append(ch);
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    // Fetch collector number and printing variant from Scryfall for a given card name.
// Results are cached in SCO_FALLBACK_CACHE so each card is only looked up once.
// Returns an array of [collectorNumber, versionSeq, versionLabel] or blank strings.
public static String[] fetchScryfallInfo(String name) {
        // Return cached result if available.
        String key = name == null ? "" : name.trim().toLowerCase();
        if (SCO_FALLBACK_CACHE.containsKey(key)) {
            return SCO_FALLBACK_CACHE.get(key);
        }
        String[] result = {"", "1", "Standard"}; // collectorNumber, versionSeq, versionLabel
        try {
            // Scryfall API: /cards/named?exact=<name>&format=json
            String url = "https://api.scryfall.com/cards/named?exact=" +
                    java.net.URLEncoder.encode(name.trim(), java.nio.charset.StandardCharsets.UTF_8) +
                    "&format=json";
            String body = tutorengine.search.ScryfallLookup.get(url);
            if (body == null) {
                SCO_FALLBACK_CACHE.put(key, result);
                return result;
            }
            // Parse collector_number using ScryfallLookup.str()
            String cNum = tutorengine.search.ScryfallLookup.str(body, "collector_number");
            // Parse printing variant from the "series" field
            String series = tutorengine.search.ScryfallLookup.str(body, "series");
            String vLabel = "Standard"; // default
            if (series != null) {
                switch (series.trim().toLowerCase()) {
                    case "promo":
                        vLabel = "Promo";
                        break;
                    case "extended-art":
                        vLabel = "Extended Art";
                        break;
                    case "borderless":
                        vLabel = "Borderless";
                        break;
                    default:
                        vLabel = "Standard";
                }
            }
            // versionSeq is typically 1 for most cards
            result[0] = cNum != null ? cNum : "";
            result[1] = "1";
            result[2] = vLabel;
            // Cache the result.
            SCO_FALLBACK_CACHE.put(key, result);
        } catch (Exception e) {
            // On error, cache the default result so we don't retry.
            SCO_FALLBACK_CACHE.put(key, result);
        }
        return result;
    }

    // Return next box name for a set code (e.g., "FRA-1", "TSR-1").
    // Counter is per-set and increments each time this is called.
    public static String nextBoxName(String setCode) {
        if (setCode == null || setCode.isBlank()) return null;
        String code = setCode.trim().toUpperCase();
        int current = BOX_NAME_COUNTER.getOrDefault(code, 0) + 1;
        BOX_NAME_COUNTER.put(code, current);
        return code + "-" + current;
    }

    /** Result of a menu-driven CSV import: rows added + rows skipped as duplicates. */
    public record ImportResult(List<Card> imported, int skipped) {}

    private static String nz(String s) { return s == null ? "" : s; }

    /**
     * Insert parsed cards as full STORED rows tagged with batchId.
     * Dedup by sku (fallback name+set+collector): existing rows are skipped, never duplicated.
     */
    public static ImportResult importBatch(Connection c, List<Card> cards, String batchId) throws Exception {
        List<Card> imported = new ArrayList<>();
        int skipped = 0;
        String dupSql = "SELECT id FROM card_instances WHERE sku = ? LIMIT 1";
        String dupFallback = "SELECT id FROM card_instances"
                + " WHERE name = ? AND set_code = ? AND collector_number = ? LIMIT 1";
        String ins = "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number,"
                + " rarity, condition, finish, language, status, box_id, created_at, quantity,"
                + " sku, set_name, color, card_type, release_year, price_php, demand,"
                + " mana_cost, power, toughness, import_batch)"
                + " VALUES (?,?,?,?,?,?,?,?,?,'STORED',NULL,CURRENT_TIMESTAMP,?,"
                + " ?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement dup = c.prepareStatement(dupSql);
             PreparedStatement dupFb = c.prepareStatement(dupFallback);
             PreparedStatement ps = c.prepareStatement(ins)) {
            for (Card card : cards) {
                String sku = nz(card.getSku());
                boolean exists;
                if (!sku.isEmpty()) {
                    dup.setString(1, sku);
                    try (ResultSet rs = dup.executeQuery()) { exists = rs.next(); }
                } else {
                    dupFb.setString(1, nz(card.getName()));
                    dupFb.setString(2, nz(card.getSetCode()));
                    dupFb.setString(3, nz(card.getCollectorNumber()));
                    try (ResultSet rs = dupFb.executeQuery()) { exists = rs.next(); }
                }
                if (exists) { skipped++; continue; }
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, "");
                ps.setString(3, nz(card.getName()));
                ps.setString(4, nz(card.getSetCode()));
                ps.setString(5, nz(card.getCollectorNumber()));
                ps.setString(6, "common");
                ps.setString(7, "NM");
                ps.setString(8, card.isFoil() ? "foil" : "nonfoil");
                ps.setString(9, "EN");
                ps.setInt(10, Math.max(0, card.getQuantity()));
                ps.setString(11, sku);
                ps.setString(12, nz(card.getSetName()));
                ps.setString(13, nz(card.getColor()));
                ps.setString(14, nz(card.getCardType()));
                ps.setInt(15, card.getReleaseYear());
                ps.setDouble(16, card.getPrice());
                ps.setInt(17, card.getDemandScore());
                ps.setString(18, nz(card.getManaCost()));
                ps.setString(19, nz(card.getPower()));
                ps.setString(20, nz(card.getToughness()));
                ps.setString(21, batchId);
                ps.executeUpdate();
                imported.add(card);
            }
        }
        return new ImportResult(imported, skipped);
    }

    /** Result of the reconcile pass over stored rows. */
    public record ReconcileResult(int refilled, int corrected, int skuRegen, int unresolvable) {}

    private static final java.util.Map<String, String> SET_SCOPED_CACHE = new java.util.HashMap<>();

    // Authoritative collector for one printing: name + set constrained.
    // Blank when Scryfall 404s (custom printings) or the lookup fails.
    static String fetchCollectorForSet(String name, String setCode) {
        String key = (name == null ? "" : name.trim().toLowerCase())
                + "|" + (setCode == null ? "" : setCode.trim().toLowerCase());
        if (SET_SCOPED_CACHE.containsKey(key)) return SET_SCOPED_CACHE.get(key);
        String found = "";
        try {
            Thread.sleep(300); // Gentle pacing: bulk reconcile trips Scryfall 429s at 100 ms
            String url = "https://api.scryfall.com/cards/named?exact="
                    + java.net.URLEncoder.encode(name.trim(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&set=" + java.net.URLEncoder.encode(setCode.trim().toLowerCase(),
                            java.nio.charset.StandardCharsets.UTF_8)
                    + "&format=json";
            String body = tutorengine.search.ScryfallLookup.get(url);
            if (body != null) {
                String cNum = tutorengine.search.ScryfallLookup.str(body, "collector_number");
                if (cNum != null) found = cNum.trim();
            }
        } catch (Exception ignored) {
            // Offline/rate-limited: blank means "leave the row alone".
        }
        SET_SCOPED_CACHE.put(key, found);
        return found;
    }

    /**
     * Reconcile stored rows headlessly: refill blank collectors from the
     * authoritative set-scoped lookup, correct wrong-printing numbers, and
     * regenerate MTG-shaped SKUs that disagree with their triple.
     * Dry run changes nothing. Never writes a number Scryfall didn't confirm.
     */
    public static ReconcileResult reconcile(Connection c, boolean dryRun) throws Exception {
        int refilled = 0, corrected = 0, skuRegen = 0, unresolvable = 0;
        String sel = "SELECT id, sku, name, set_code, collector_number FROM card_instances";
        try (PreparedStatement q = c.prepareStatement(sel);
             ResultSet rs = q.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString(1);
                String sku = rs.getString(2) == null ? "" : rs.getString(2);
                String name = rs.getString(3) == null ? "" : rs.getString(3);
                String set = rs.getString(4) == null ? "" : rs.getString(4).trim().toUpperCase();
                String coll = rs.getString(5) == null ? "" : rs.getString(5).trim();
                if (name.isEmpty() || set.isEmpty() || "UNK".equals(set)) {
                    if (coll.isEmpty()) unresolvable++;
                    continue;
                }
                String auth = fetchCollectorForSet(name, set);
                String finalColl = coll;
                if (coll.isEmpty()) {
                    if (auth.isEmpty()) {
                        unresolvable++;
                    } else {
                        finalColl = auth;
                        refilled++;
                        if (!dryRun) updateCollector(c, id, auth);
                    }
                } else if (!auth.isEmpty() && !auth.equals(coll)) {
                    finalColl = auth;
                    corrected++;
                    if (!dryRun) updateCollector(c, id, auth);
                }
                if (!dryRun && needsSkuRegen(sku, set, finalColl)) {
                    updateSku(c, id, "MTG-" + set + "-" + finalColl.toUpperCase());
                    skuRegen++;
                } else if (dryRun && needsSkuRegen(sku, set, finalColl)) {
                    skuRegen++;
                }
            }
        }
        return new ReconcileResult(refilled, corrected, skuRegen, unresolvable);
    }

    private static boolean needsSkuRegen(String sku, String set, String coll) {
        if (sku == null || coll == null || coll.isEmpty()) return false;
        String[] parts = sku.split("-");
        if (parts.length < 3 || !"MTG".equalsIgnoreCase(parts[0])) return false; // RAPID-/LIVE- untouched
        if (!set.equalsIgnoreCase(parts[1])) return true;
        String num = String.join("-", java.util.Arrays.copyOfRange(parts, 2, parts.length));
        if (num.equalsIgnoreCase(coll)) return false;
        // Tolerate one trailing promo letter (e.g. 268P for collector 268p).
        return !(num.length() == coll.length() + 1
                && num.regionMatches(true, 0, coll, 0, coll.length()));
    }

    private static void updateCollector(Connection c, String id, String coll) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE card_instances SET collector_number = ? WHERE id = ?")) {
            ps.setString(1, coll);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    private static void updateSku(Connection c, String id, String sku) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE card_instances SET sku = ? WHERE id = ?")) {
            ps.setString(1, sku);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    private static final String SEED_FINGERPRINT =
            "import_batch IS NULL AND (scryfall_id GLOB 's[0-9]'"
            + " OR scryfall_id GLOB 'c[0-9]' OR scryfall_id GLOB 'scry-[0-9]*')";

    /**
     * Delete untagged demo-seed rows (test scryfall ids from seed mains).
     * Real UUIDs (36-char) and SKU-carrying intake rows can never match the
     * 2-char / scry-N shapes. Dry run counts without deleting.
     */
    public static int purgeSeedRows(Connection c, boolean dryRun) throws Exception {
        if (dryRun) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM card_instances WHERE " + SEED_FINGERPRINT);
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM card_instances WHERE " + SEED_FINGERPRINT)) {
            return ps.executeUpdate();
        }
    }

    /** Result of duplicate collapsing: groups merged, rows removed. */
    public record DedupeResult(int groups, int removed) {}

    /**
     * Collapse duplicate rows sharing (name, set, collector, status, box,
     * import-batch) into the earliest row with summed quantity. Location splits
     * and batch provenance survive (different box/batch never merge), so Undo
     * import stays exact. Dry run counts without changing anything.
     */
    public static DedupeResult dedupeRows(Connection c, boolean dryRun) throws Exception {
        int groups = 0, removed = 0;
        String grp = "SELECT name, set_code, collector_number, status,"
                + " IFNULL(box_id, ''), IFNULL(import_batch, ''), COUNT(*), SUM(quantity), MIN(id)"
                + " FROM card_instances"
                + " GROUP BY name, set_code, collector_number, status, box_id, import_batch"
                + " HAVING COUNT(*) > 1";
        try (PreparedStatement q = c.prepareStatement(grp);
             ResultSet rs = q.executeQuery()) {
            while (rs.next()) {
                groups++;
                int n = rs.getInt(7);
                int total = rs.getInt(8);
                String keep = rs.getString(9);
                removed += n - 1;
                if (!dryRun) {
                    try (PreparedStatement u = c.prepareStatement(
                            "UPDATE card_instances SET quantity = ? WHERE id = ?")) {
                        u.setInt(1, total);
                        u.setString(2, keep);
                        u.executeUpdate();
                    }
                    try (PreparedStatement d = c.prepareStatement(
                            "DELETE FROM card_instances WHERE IFNULL(name, '') = ?"
                            + " AND IFNULL(set_code, '') = ?"
                            + " AND IFNULL(collector_number, '') = ? AND IFNULL(status, '') = ?"
                            + " AND IFNULL(box_id, '') = ? AND IFNULL(import_batch, '') = ?"
                            + " AND id != ?")) {
                        d.setString(1, nz(rs.getString(1)));
                        d.setString(2, nz(rs.getString(2)));
                        d.setString(3, nz(rs.getString(3)));
                        d.setString(4, nz(rs.getString(4)));
                        d.setString(5, rs.getString(5));
                        d.setString(6, rs.getString(6));
                        d.setString(7, keep);
                        d.executeUpdate();
                    }
                }
            }
        }
        return new DedupeResult(groups, removed);
    }

    /** Delete exactly the rows written by one import batch. Returns rows removed. */
    public static int deleteImportBatch(Connection c, String batchId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM card_instances WHERE import_batch = ?")) {
            ps.setString(1, batchId);
            return ps.executeUpdate();
        }
    }

    public static void main(String[] args) {
        // DEMO for presentation: shows count + first 3 cards.
        try {
            List<Card> cards = load("Dataset/cards.csv");
            System.out.println("Loaded " + cards.size() + " cards (need >= 50).");
            for (int i = 0; i < Math.min(3, cards.size()); i++)
                System.out.println("  sample: " + cards.get(i));
            System.out.println("CSVLoader. OWNER: David; load() implemented 2026-10-05 by Bondoc, Karl B.");
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }
}
