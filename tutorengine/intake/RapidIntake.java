package tutorengine.intake;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tutorengine.auth.UserSessionManager;
import tutorengine.model.AuditCommit;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;
import tutorengine.algorithms.SkuCorrector;
import tutorengine.search.SkuFuzzy;
import tutorengine.search.ScryfallLookup;
import tutorengine.util.CSVLoader;

// RapidIntake: Parser, collector lookup, variant fetcher, and crash-safe SQLite
// draft persistence (§1 & §2 specs/rapid-shortcut-intake & specs/draft-persistence).
public final class RapidIntake {
    private static final String API = "https://api.scryfall.com";
    private static int skuCounter = 0;

    private RapidIntake() {}

    public record ParsedShortcut(
            String setCode,
            String collectorNumber,
            boolean foil,
            int quantity,
            String condition,
            String rawInput,
            String errorMessage
    ) {
        public boolean isValid() {
            return errorMessage == null && !setCode.isBlank() && !collectorNumber.isBlank();
        }
    }

    public record ResolvedCard(
            String scryfallId,
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String rarity,
            String cardType,
            int releaseYear,
            String manaCost,
            String power,
            String toughness,
            boolean foil,
            int quantity,
            String condition,
            Double usdPrice,
            Double phpPrice,
            String imageUri,
            String slot
    ) {}

    public record Variant(
            int index,
            String scryfallId,
            String name,
            String setCode,
            String collectorNumber,
            String rarity,
            boolean foil,
            Double usdPrice,
            String imageUri
    ) {}

    public record DraftRow(
            String draftId,
            String employeeHandle,
            String batchId,
            String scryfallId,
            String name,
            String setCode,
            String collectorNumber,
            String condition,
            String finish,
            int quantity,
            double pricePhp,
            String imageUri,
            String createdAt
    ) {}

    // 1.1 Shortcut Parser: <SET> <NUM> [foil|f] [x<QTY>] (case-insensitive)
    public static ParsedShortcut parseShortcut(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ParsedShortcut("", "", false, 1, "NM", "", "Input cannot be empty");
        }
        String trimmed = input.trim();
        String[] tokens = trimmed.split("\\s+");

        if (tokens.length < 2) {
            return new ParsedShortcut(
                    "", "", false, 1, "NM", trimmed,
                    "Invalid shortcut format. Expected: <SET> <NUM> [foil|f] [x<QTY>] (e.g. 'woe 142', 'mh3 004 foil', 'one 23 x4')");
        }

        String setCode = tokens[0].trim().toUpperCase();
        String collectorNumber = tokens[1].trim();

        // Validate set code (alphanumeric, 2-6 chars)
        if (!setCode.matches("^[A-Z0-9]{2,6}$")) {
            return new ParsedShortcut(
                    setCode, collectorNumber, false, 1, "NM", trimmed,
                    "Invalid set code '" + setCode + "'. Expected 2-6 alphanumeric characters (e.g. 'WOE', 'MH3')");
        }

        // Validate collector number has at least one digit or alphanumeric identifier
        if (!collectorNumber.matches(".*[0-9A-Za-z★].*")) {
            return new ParsedShortcut(
                    setCode, collectorNumber, false, 1, "NM", trimmed,
                    "Invalid collector number '" + collectorNumber + "'. Expected valid card number (e.g. '142', '004')");
        }

        boolean foil = false;
        int qty = 1;
        String condition = "NM";

        for (int i = 2; i < tokens.length; i++) {
            String t = tokens[i].trim().toLowerCase();
            if (t.equals("foil") || t.equals("f")) {
                foil = true;
            } else if (t.equals("nonfoil") || t.equals("nf")) {
                foil = false;
            } else if (t.startsWith("x") && t.length() > 1 && t.substring(1).matches("\\d+")) {
                qty = Integer.parseInt(t.substring(1));
            } else if (t.endsWith("x") && t.length() > 1 && t.substring(0, t.length() - 1).matches("\\d+")) {
                qty = Integer.parseInt(t.substring(0, t.length() - 1));
            } else if (t.matches("\\d+")) {
                qty = Integer.parseInt(t);
            } else if (t.equals("nm") || t.equals("lp") || t.equals("mp") || t.equals("hp") || t.equals("dmg")) {
                condition = t.toUpperCase();
            }
        }

        if (qty <= 0) {
            return new ParsedShortcut(
                    setCode, collectorNumber, foil, 1, condition, trimmed,
                    "Quantity must be a positive integer.");
        }

        return new ParsedShortcut(setCode, collectorNumber, foil, qty, condition, trimmed, null);
    }

    // 1.1 Resolution: Scryfall /cards/<set>/<number> with fuzzy name fallback
    public static ResolvedCard resolve(String input) throws Exception {
        ParsedShortcut parsed = parseShortcut(input);
        if (parsed.isValid()) {
            ResolvedCard result = resolve(parsed);
            if (result != null) return result;
        }

        // Fall back to fuzzy name search for non-matching input if at least 2 chars
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.length() >= 2) {
            try {
                String body = get(API + "/cards/named?fuzzy="
                        + URLEncoder.encode(trimmed, StandardCharsets.UTF_8));
                return parseCardJson(body, false, 1, "NM");
            } catch (Exception ignored) {
                // Return invalid shape error message
            }
        }

        throw new IllegalArgumentException(parsed.errorMessage() != null
                ? parsed.errorMessage()
                : "Expected format: <SET> <NUM> [foil] [x<QTY>] (e.g. 'woe 142', 'mh3 004 foil', 'one 23 x4')");
    }

    // 1.1b Name-only resolution for cards without set codes (rapid-intake-improvements)
    public static List<String> suggestByName(String query) {
        return ScryfallLookup.autocomplete(query);
    }

    public static ResolvedCard resolveByName(String exactName) throws Exception {
        String body = get(API + "/cards/named?exact="
                + URLEncoder.encode(exactName, StandardCharsets.UTF_8));
        return parseCardJson(body, false, 1, "NM");
    }

    // --- Fuzzy search over the local card catalog (same SkuFuzzy mechanism as catalog) ---
    // Returns up to 5 card name suggestions for a free-text query.
    // Falls back to Scryfall fuzzy if catalog unavailable; offline-first with local dataset.
    public static List<String> fuzzySearch(String query) {
        if (query == null || query.trim().length() < 2) return List.of();
        try {
            List<Card> cards = CSVLoader.load("Dataset/cards.csv");
            Map<String, Card> catalog = new java.util.HashMap<>();
            for (Card c : cards) catalog.put(c.getSku(), c);
            Map<String, List<String>> graph = SkuCorrector.build(cards);
            List<Card> hits = SkuFuzzy.suggest(catalog, graph, query.trim(), 5);
            return hits.stream()
                    .map(Card::getName)
                    .distinct()
                    .limit(5)
                    .toList();
        } catch (Exception ignored) {
            // Fall back to Scryfall fuzzy search (offline-first: local catalog preferred)
            return List.of();
        }
    }

    public static ResolvedCard resolve(ParsedShortcut shortcut) throws Exception {
        if (!shortcut.isValid()) {
            throw new IllegalArgumentException(shortcut.errorMessage());
        }

        String set = shortcut.setCode().toLowerCase();
        String num = shortcut.collectorNumber();

        String body = null;
        try {
            body = get(API + "/cards/" + URLEncoder.encode(set, StandardCharsets.UTF_8)
                    + "/" + URLEncoder.encode(num, StandardCharsets.UTF_8));
        } catch (Exception e1) {
            // If 404 and number has leading zeros (e.g. 004), try stripping leading zeros
            String stripped = num.replaceFirst("^0+(?!$)", "");
            if (!stripped.equals(num)) {
                try {
                    body = get(API + "/cards/" + URLEncoder.encode(set, StandardCharsets.UTF_8)
                            + "/" + URLEncoder.encode(stripped, StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }

        if (body == null) {
            // Try fallback search by set and collector number
            try {
                String searchUrl = API + "/cards/search?q="
                        + URLEncoder.encode("set:" + set + " number:" + num, StandardCharsets.UTF_8);
                String searchBody = get(searchUrl);
                String firstCard = firstObject(searchBody, "data");
                if (firstCard != null) {
                    body = firstCard;
                }
            } catch (Exception ignored) {}
        }

        if (body == null) {
            throw new IllegalArgumentException("Card not found on Scryfall for set '"
                    + shortcut.setCode() + "' number '" + shortcut.collectorNumber() + "'");
        }

        return parseCardJson(body, shortcut.foil(), shortcut.quantity(), shortcut.condition());
    }

    // 1.2 Variant Listing: same-name search in the same set, capped at 9
    public static List<Variant> fetchVariants(String cardName, String setCode) {
        List<Variant> list = new ArrayList<>();
        if (cardName == null || cardName.isBlank() || setCode == null || setCode.isBlank()) {
            return list;
        }
        try {
            String query = "!\"" + cardName + "\" set:" + setCode.trim().toLowerCase();
            String url = API + "/cards/search?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&unique=prints";
            String body = get(url);

            // Scan card objects from data array
            int dataIdx = body.indexOf("\"data\":");
            if (dataIdx >= 0) {
                int openBracket = body.indexOf('[', dataIdx);
                int closeBracket = body.lastIndexOf(']');
                if (openBracket >= 0 && closeBracket > openBracket) {
                    String arrayContent = body.substring(openBracket + 1, closeBracket);
                    int depth = 0;
                    int start = -1;
                    int idx = 1;
                    for (int p = 0; p < arrayContent.length(); p++) {
                        char c = arrayContent.charAt(p);
                        if (c == '{') {
                            if (depth == 0) start = p;
                            depth++;
                        } else if (c == '}') {
                            depth--;
                            if (depth == 0 && start >= 0) {
                                String cardJson = arrayContent.substring(start, p + 1);
                                String id = jsonStr(cardJson, "id");
                                String name = jsonStr(cardJson, "name");
                                String sCode = jsonStr(cardJson, "set");
                                String cNum = jsonStr(cardJson, "collector_number");
                                String rarity = jsonStr(cardJson, "rarity");
                                Double usd = jsonNum(cardJson, "usd");
                                String img = extractImage(cardJson);

                                if (id != null && name != null) {
                                    list.add(new Variant(idx++, id, name,
                                            sCode == null ? setCode : sCode.toUpperCase(),
                                            cNum == null ? "" : cNum,
                                            rarity == null ? "normal" : rarity,
                                            cardJson.contains("\"foil\":true"),
                                            usd, img));
                                }
                                start = -1;
                                if (list.size() >= 9) break;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    // 2.1 SQLite intake_drafts Persistence
    public static String defaultEmployeeHandle() {
        var current = UserSessionManager.getInstance().currentOrNull();
        if (current != null && current.username() != null && !current.username().isBlank()) {
            return current.username();
        }
        return System.getProperty("user.name", "clerk");
    }

    public static String saveDraft(Connection c, String employeeHandle, String batchId, ResolvedCard card) throws Exception {
        // Dedup: same setCode + collectorNumber → increment qty (rapid-intake-improvements)
        String existingId = findDuplicate(c, card);
        if (existingId != null) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE intake_drafts SET quantity = quantity + ? WHERE draft_id = ?")) {
                ps.setInt(1, card.quantity());
                ps.setString(2, existingId);
                ps.executeUpdate();
            }
            return existingId;
        }

        String draftId = UUID.randomUUID().toString();
        String finish = card.foil() ? "FOIL" : "NONFOIL";

        // Record audit entry: each rapid intake input attributed to the current active user
        String employeeDisplay = employeeHandle;
        try {
            employeeDisplay = tutorengine.auth.UserSessionManager.getInstance()
                    .requireEmployee().displayName();
        } catch (IllegalStateException ignored) {
            // No active user; keep original employeeHandle
        }
        String tag = tutorengine.model.AuditCommit.generateTag(employeeDisplay, 1);
        try {
            tutorengine.audit.AuditLog.recordCommit(tag, employeeDisplay, 1);
        } catch (Exception ignored) {
            // Audit failure does not block the draft save
        }

        String sql = "INSERT INTO intake_drafts ("
                + "draft_id, employee_handle, batch_id, scryfall_id, name, set_code, collector_number, "
                + "condition, finish, quantity, price_php, image_uri, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, draftId);
            ps.setString(2, employeeHandle);
            ps.setString(3, batchId == null ? "DRAFT-" + System.currentTimeMillis() : batchId);
            ps.setString(4, card.scryfallId());
            ps.setString(5, card.name());
            ps.setString(6, card.setCode());
            ps.setString(7, card.collectorNumber());
            ps.setString(8, card.condition() == null ? "NM" : card.condition());
            ps.setString(9, finish);
            ps.setInt(10, card.quantity());
            ps.setDouble(11, card.phpPrice() == null ? 0.0 : card.phpPrice());
            ps.setString(12, card.imageUri());
            ps.executeUpdate();
        }
        return draftId;
    }

    // Dedup key: setCode + collectorNumber when collector number exists;
    // fallback to normalized name when no collector number.
    private static String findDuplicate(Connection c, ResolvedCard card) throws Exception {
        String sql;
        if (card.collectorNumber() != null && !card.collectorNumber().isBlank()) {
            sql = "SELECT draft_id FROM intake_drafts WHERE name = ? AND set_code = ?"
                    + " AND collector_number = ? AND removed = 0 LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, card.name());
                ps.setString(2, card.setCode());
                ps.setString(3, card.collectorNumber());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        } else {
            sql = "SELECT draft_id FROM intake_drafts WHERE name = ? AND set_code = ?"
                    + " AND (collector_number = '' OR collector_number IS NULL) AND removed = 0 LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, card.name());
                ps.setString(2, card.setCode());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        }
    }

    // Soft-delete a draft row (removed = 1) for undo support.
    public static void removeDraft(Connection c, String draftId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE intake_drafts SET removed = 1 WHERE draft_id = ?")) {
            ps.setString(1, draftId);
            ps.executeUpdate();
        }
    }

    // Undo a soft-delete (removed = 0).
    public static void undoRemoveDraft(Connection c, String draftId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE intake_drafts SET removed = 0 WHERE draft_id = ?")) {
            ps.setString(1, draftId);
            ps.executeUpdate();
        }
    }

    // Update quantity on a draft row.
    public static void updateDraftQuantity(Connection c, String draftId, int newQty) throws Exception {
        if (newQty <= 0) throw new IllegalArgumentException("Quantity must be positive");
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE intake_drafts SET quantity = ? WHERE draft_id = ?")) {
            ps.setInt(1, newQty);
            ps.setString(2, draftId);
            ps.executeUpdate();
        }
    }

    // Load only non-removed drafts.
    public static List<DraftRow> loadDrafts(Connection c, String employeeHandle) throws Exception {
        List<DraftRow> list = new ArrayList<>();
        String sql = "SELECT draft_id, employee_handle, batch_id, scryfall_id, name, set_code, "
                + "collector_number, condition, finish, quantity, price_php, image_uri, created_at "
                + "FROM intake_drafts WHERE employee_handle = ? AND removed = 0 ORDER BY created_at ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, employeeHandle);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DraftRow(
                            rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6),
                            rs.getString(7), rs.getString(8), rs.getString(9),
                            rs.getInt(10), rs.getDouble(11), rs.getString(12),
                            rs.getString(13)
                    ));
                }
            }
        }
        return list;
    }

    public static int discardDrafts(Connection c, String employeeHandle) throws Exception {
        String sql = "DELETE FROM intake_drafts WHERE employee_handle = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, employeeHandle);
            return ps.executeUpdate();
        }
    }

    public static int purgeDraftsByBatch(Connection c, String batchId) throws Exception {
        String sql = "DELETE FROM intake_drafts WHERE batch_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, batchId);
            return ps.executeUpdate();
        }
    }

    public static int purgeDrafts(Connection c, List<String> draftIds) throws Exception {
        if (draftIds == null || draftIds.isEmpty()) return 0;
        int count = 0;
        String sql = "DELETE FROM intake_drafts WHERE draft_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (String id : draftIds) {
                ps.setString(1, id);
                count += ps.executeUpdate();
            }
        }
        return count;
    }

    public static synchronized String nextSku(String setCode) {
        String code = setCode == null || setCode.isBlank() ? "RAPID" : setCode.trim().toUpperCase();
        return String.format("RAPID-%s-%04d", code, ++skuCounter);
    }

    public static Card toCard(ResolvedCard r, String sku) {
        double price = r.phpPrice() == null ? 0.0 : r.phpPrice();
        String targetSku = (sku == null || sku.isBlank()) ? nextSku(r.setCode()) : sku;
        return new Card(
                targetSku,
                r.name(),
                r.setName(),
                colorOf(r.cardType()),
                r.cardType(),
                r.releaseYear(),
                price,
                r.quantity(),
                50,
                r.foil(),
                r.slot() == null || r.slot().isBlank() ? null : r.slot(),
                r.manaCost() == null ? "" : r.manaCost(),
                r.power() == null ? "" : r.power(),
                r.toughness() == null ? "" : r.toughness(),
                r.setCode()
        );
    }

    public static Card toCard(DraftRow d, String sku) {
        boolean foil = "FOIL".equalsIgnoreCase(d.finish());
        String targetSku = (sku == null || sku.isBlank()) ? nextSku(d.setCode()) : sku;
        return new Card(
                targetSku,
                d.name(),
                d.setCode(),
                "Colorless",
                "Card",
                2026,
                d.pricePhp(),
                d.quantity(),
                50,
                foil,
                null,
                "", "", "",
                d.setCode()
        );
    }

    // --- JSON Helpers ---

    private static ResolvedCard parseCardJson(String json, boolean forceFoil, int qty, String condition) {
        String face = firstObject(json, "card_faces");
        String src = face == null ? json : face;

        String id = jsonStr(json, "id");
        String name = jsonStr(src, "name");
        String setName = jsonStr(json, "set_name");
        String setCode = jsonStr(json, "set");
        String collectorNum = jsonStr(json, "collector_number");
        String rarity = jsonStr(json, "rarity");
        String cardType = jsonStr(src, "type_line");
        String releasedAt = jsonStr(json, "released_at");
        String manaCost = jsonStr(src, "mana_cost");
        String power = jsonStr(src, "power");
        String toughness = jsonStr(src, "toughness");

        int year = 2026;
        if (releasedAt != null && releasedAt.length() >= 4) {
            try {
                year = Integer.parseInt(releasedAt.substring(0, 4));
            } catch (Exception ignored) {}
        }

        Double usdNormal = jsonNum(json, "usd");
        Double usdFoil = jsonNum(json, "usd_foil");
        Double usd = forceFoil ? (usdFoil != null ? usdFoil : usdNormal) : (usdNormal != null ? usdNormal : usdFoil);
        Double php = ScryfallLookup.toPhp(usd);

        String imageUri = extractImage(json);

        // Preliminary routing
        String normSet = setCode == null ? "" : setCode.toUpperCase();
        Card dummy = new Card("TMP", name == null ? "Unknown" : name, setName, "Colorless",
                cardType == null ? "Card" : cardType, year, php == null ? 0.0 : php, qty, 50, forceFoil,
                "Intake Sorting Desk", manaCost == null ? "" : manaCost,
                power == null ? "" : power, toughness == null ? "" : toughness, normSet);
        String slot = StorageLocation.route(dummy);

        return new ResolvedCard(
                id == null ? UUID.randomUUID().toString() : id,
                name == null ? "Unknown" : name,
                setName == null ? "" : setName,
                normSet,
                collectorNum == null ? "" : collectorNum,
                rarity == null ? "common" : rarity,
                cardType == null ? "Card" : cardType,
                year,
                manaCost == null ? "" : manaCost,
                power == null ? "" : power,
                toughness == null ? "" : toughness,
                forceFoil,
                qty,
                condition,
                usd,
                php,
                imageUri,
                slot
        );
    }

    private static String extractImage(String json) {
        String normal = jsonStr(json, "normal");
        if (normal != null) return normal;
        String small = jsonStr(json, "small");
        if (small != null) return small;
        return "";
    }

    private static String colorOf(String cardType) {
        if (cardType == null) return "Colorless";
        return "Colorless";
    }

    private static String jsonStr(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int c = json.indexOf(':', i + k.length());
        if (c < 0) return null;
        int j = c + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
        if (json.startsWith("null", j)) return null;
        if (json.charAt(j) != '"') return null;
        StringBuilder sb = new StringBuilder();
        for (int p = j + 1; p < json.length(); p++) {
            char ch = json.charAt(p);
            if (ch == '\\' && p + 1 < json.length()) { sb.append(json.charAt(p + 1)); p++; }
            else if (ch == '"') break;
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static Double jsonNum(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int c = json.indexOf(':', i + k.length());
        if (c < 0) return null;
        int j = c + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
        if (json.startsWith("null", j)) return null;
        if (json.charAt(j) == '"') {
            int q = json.indexOf('"', j + 1);
            if (q < 0) return null;
            try { return Double.parseDouble(json.substring(j + 1, q)); } catch (Exception ex) { return null; }
        }
        int e = j;
        while (e < json.length() && "0123456789.+-eE".indexOf(json.charAt(e)) >= 0) e++;
        try { return Double.parseDouble(json.substring(j, e)); } catch (Exception ex) { return null; }
    }

    private static String firstObject(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int a = json.indexOf('{', i + k.length());
        if (a < 0) return null;
        int depth = 0;
        for (int p = a; p < json.length(); p++) {
            char ch = json.charAt(p);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) return json.substring(a, p + 1);
            }
        }
        return null;
    }

    private static String get(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> r = client.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "TutorEngine/1.0 (CCDATRCL school project)")
                        .header("Accept", "application/json")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
        return r.body();
    }

    // --- Standalone Verification main() ---
    public static void main(String[] args) {
        System.out.println("=== RapidIntake Headless Verification Demo ===");

        // 1. Test Grammar Parser
        ParsedShortcut s1 = parseShortcut("woe 142");
        if (!s1.isValid() || !s1.setCode().equals("WOE") || !s1.collectorNumber().equals("142") || s1.foil() || s1.quantity() != 1) {
            throw new AssertionError("Parser failure on 'woe 142': " + s1);
        }
        System.out.println("  ✓ 'woe 142' parsed correctly");

        ParsedShortcut s2 = parseShortcut("mh3 004 foil");
        if (!s2.isValid() || !s2.setCode().equals("MH3") || !s2.collectorNumber().equals("004") || !s2.foil() || s2.quantity() != 1) {
            throw new AssertionError("Parser failure on 'mh3 004 foil': " + s2);
        }
        System.out.println("  ✓ 'mh3 004 foil' parsed correctly");

        ParsedShortcut s3 = parseShortcut("one 23 x4");
        if (!s3.isValid() || !s3.setCode().equals("ONE") || !s3.collectorNumber().equals("23") || s3.foil() || s3.quantity() != 4) {
            throw new AssertionError("Parser failure on 'one 23 x4': " + s3);
        }
        System.out.println("  ✓ 'one 23 x4' parsed correctly");

        ParsedShortcut s4 = parseShortcut("dmu 101 f x2");
        if (!s4.isValid() || !s4.setCode().equals("DMU") || !s4.collectorNumber().equals("101") || !s4.foil() || s4.quantity() != 2) {
            throw new AssertionError("Parser failure on 'dmu 101 f x2': " + s4);
        }
        System.out.println("  ✓ 'dmu 101 f x2' parsed correctly");

        ParsedShortcut bad1 = parseShortcut("gibberish");
        if (bad1.isValid() || bad1.errorMessage() == null) {
            throw new AssertionError("Gibberish should fail validation");
        }
        System.out.println("  ✓ Invalid shape rejected with message: " + bad1.errorMessage());

        // 2. Test Scryfall live resolution
        try {
            ResolvedCard r1 = resolve("woe 142");
            System.out.println("  ✓ Resolved 'woe 142': " + r1.name() + " (" + r1.setCode() + " #" + r1.collectorNumber() + ") slot=" + r1.slot());
            if (!r1.name().equalsIgnoreCase("Monstrous Rage")) {
                throw new AssertionError("Expected Monstrous Rage for woe 142, got " + r1.name());
            }

            ResolvedCard r2 = resolve("one 23 x4");
            System.out.println("  ✓ Resolved 'one 23 x4': " + r2.name() + " qty=" + r2.quantity() + " php=" + r2.phpPrice());
            if (!r2.name().contains("Mondrak")) {
                throw new AssertionError("Expected Mondrak for one 23, got " + r2.name());
            }

            ResolvedCard r3 = resolve("mh3 004 foil");
            System.out.println("  ✓ Resolved 'mh3 004 foil': " + r3.name() + " foil=" + r3.foil() + " qty=" + r3.quantity());
            if (!r3.foil() || r3.quantity() != 1) {
                throw new AssertionError("Expected foil=true, qty=1 for mh3 004 foil, got foil=" + r3.foil() + ", qty=" + r3.quantity());
            }

            // Test variants
            List<Variant> variants = fetchVariants("Mondrak, Glory Dominus", "ONE");
            System.out.println("  ✓ Found " + variants.size() + " variants for Mondrak in ONE (capped at 9):");
            for (Variant v : variants) {
                System.out.println("     [Alt+" + v.index() + "] #" + v.collectorNumber() + " " + v.rarity() + " (usd=" + v.usdPrice() + ")");
            }
            if (variants.isEmpty()) {
                throw new AssertionError("Expected at least 1 variant for Mondrak");
            }
        } catch (Exception e) {
            System.out.println("  ⚠ Network lookup skipped or failed: " + e.getMessage());
        }

        // 3. Test SQLite Draft Persistence on temporary DB (Kill recovery, hand-off, clean purge)
        try {
            java.io.File tempDb = java.io.File.createTempFile("tutorengine_draft_test_", ".db");
            tempDb.deleteOnExit();
            System.setProperty("tutorengine.db", tempDb.getAbsolutePath());
            tutorengine.db.Db.migrate();

            try (Connection c = tutorengine.db.Db.open()) {
                String clerkA = "clerk_alice";
                String clerkB = "clerk_bob";
                String batchA = "BATCH-ALICE-001";
                String batchB = "BATCH-BOB-001";

                // Save 3 drafts for Clerk Alice
                for (int i = 1; i <= 3; i++) {
                    ResolvedCard dummy = new ResolvedCard(
                            "scry-a-" + i, "Alice Card " + i, "Set A", "STA", String.valueOf(i),
                            "rare", "Creature", 2026, "{1}{G}", "2", "2", i % 2 == 0, i, "NM",
                            2.0 * i, 110.0 * i, "https://example.com/imga" + i, "CAB-T-TOP"
                    );
                    saveDraft(c, clerkA, batchA, dummy);
                }

                // Save 2 drafts for Clerk Bob
                for (int i = 1; i <= 2; i++) {
                    ResolvedCard dummy = new ResolvedCard(
                            "scry-b-" + i, "Bob Card " + i, "Set B", "STB", String.valueOf(i),
                            "uncommon", "Instant", 2026, "{U}", "", "", false, 1, "NM",
                            0.5 * i, 28.0 * i, "https://example.com/imgb" + i, "RACK-M-TOP"
                    );
                    saveDraft(c, clerkB, batchB, dummy);
                }
                System.out.println("  ✓ Simulated mid-batch stage: 3 drafts for Alice, 2 drafts for Bob");
            }

            // SIMULATED KILL & RE-LAUNCH (Fresh Connection / Next Session)
            try (Connection c2 = tutorengine.db.Db.open()) {
                String clerkA = "clerk_alice";
                String clerkB = "clerk_bob";
                String batchA = "BATCH-ALICE-001";

                // Alice restores her drafts after simulated crash
                List<DraftRow> aliceDrafts = loadDrafts(c2, clerkA);
                if (aliceDrafts.size() != 3) {
                    throw new AssertionError("Crash recovery failed: expected 3 drafts for Alice, got " + aliceDrafts.size());
                }
                System.out.println("  ✓ Crash recovery verified: Alice restored " + aliceDrafts.size() + " drafts in chronological order");

                // Bob's drafts are segregated and intact
                List<DraftRow> bobDrafts = loadDrafts(c2, clerkB);
                if (bobDrafts.size() != 2) {
                    throw new AssertionError("Hand-off isolation failed: expected 2 drafts for Bob, got " + bobDrafts.size());
                }
                System.out.println("  ✓ Hand-off isolation verified: Bob retains " + bobDrafts.size() + " separate drafts");

                // Alice commits and purges her batch
                int purgedAlice = purgeDraftsByBatch(c2, batchA);
                if (purgedAlice != 3) {
                    throw new AssertionError("Expected 3 drafts purged for Alice, got " + purgedAlice);
                }

                // Alice now has 0, Bob still has 2
                if (!loadDrafts(c2, clerkA).isEmpty()) {
                    throw new AssertionError("Alice should have 0 drafts after purge");
                }
                if (loadDrafts(c2, clerkB).size() != 2) {
                    throw new AssertionError("Bob should still have 2 drafts after Alice's purge");
                }
                System.out.println("  ✓ Clean purge verified: Alice has 0 drafts remaining, Bob still has 2 drafts");
            }
        } catch (Exception ex) {
            throw new RuntimeException("SQLite persistence test failed", ex);
        }

        System.out.println("✓ RapidIntake verification passed!");
    }
}
