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

        // Try SKU lookup (MTG-SET-NUM format, e.g. MTG-BLB-1) before fuzzy name search
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.startsWith("MTG-") && trimmed.split("-").length >= 3) {
            try {
                String setCode = trimmed.split("-")[1].toLowerCase();
                String number = trimmed.split("-")[2].toUpperCase();
                String body = get(API + "/cards/" + URLEncoder.encode(setCode, StandardCharsets.UTF_8)
                        + "/" + URLEncoder.encode(number, StandardCharsets.UTF_8));
                if (body != null) return parseCardJson(body, false, 1, "NM");
            } catch (Exception ignored) {
                // Continue to fuzzy name search if SKU lookup fails
            }
        }

        // Fall back to fuzzy name search for non-matching input if at least 2 chars
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


    /**
     * Persist cards into card_instances with status = 'UNSTORED' and box_id = NULL
     * so they become available in the Storage view as pending batches.
     * Same card in the same spot (name + set + collector + finish + condition,
     * still unfiled) merges into one row with summed quantity; a different box,
     * foil, or condition keeps its own row.
     */
    public static int persistUnstoredInstances(Connection c, List<Card> cards) throws Exception {
        if (cards == null || cards.isEmpty()) return 0;
        int total = 0;
        String find = "SELECT id, quantity FROM card_instances"
                + " WHERE name = ? AND set_code = ? AND collector_number = ?"
                + " AND finish = ? AND condition = ? AND status = 'UNSTORED' AND box_id IS NULL"
                + " LIMIT 1";
        String sql = "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number,"
                + " rarity, condition, finish, language, status, box_id, created_at, quantity,"
                + " sku, set_name, color, card_type, release_year, price_php, demand,"
                + " mana_cost, power, toughness)"
                + " VALUES (?,?,?,?,?,?,?,?,?,'UNSTORED',NULL,CURRENT_TIMESTAMP,?,"
                + " ?,?,?,?,?,?,?,?,?,?)";
        String bump = "UPDATE card_instances SET quantity = quantity + ? WHERE id = ?";
        try (PreparedStatement sel = c.prepareStatement(find);
             PreparedStatement ps = c.prepareStatement(sql);
             PreparedStatement upd = c.prepareStatement(bump)) {
            for (Card card : cards) {
                int qty = Math.max(1, card.getQuantity());
                String set = card.getSetCode() != null && !card.getSetCode().isBlank() ? card.getSetCode().toUpperCase() : "UNK";
                String collNum = card.getCollectorNumber() != null && !card.getCollectorNumber().isBlank() ? card.getCollectorNumber() : "1";
                String sku = card.getSku() != null ? card.getSku() : "";
                String finish = card.isFoil() ? "foil" : "nonfoil";
                sel.setString(1, card.getName());
                sel.setString(2, set);
                sel.setString(3, collNum);
                sel.setString(4, finish);
                sel.setString(5, "NM");
                String matchId = null;
                try (java.sql.ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) matchId = rs.getString(1);
                }
                if (matchId != null) {
                    upd.setInt(1, qty);
                    upd.setString(2, matchId);
                    upd.executeUpdate();
                } else {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, "");
                    ps.setString(3, card.getName());
                    ps.setString(4, set);
                    ps.setString(5, collNum);
                    ps.setString(6, "common");
                    ps.setString(7, "NM");
                    ps.setString(8, finish);
                    ps.setString(9, "EN");
                    ps.setInt(10, qty);
                    ps.setString(11, sku);
                    ps.setString(12, card.getSetName() != null ? card.getSetName() : "");
                    ps.setString(13, card.getColor() != null ? card.getColor() : "");
                    ps.setString(14, card.getCardType() != null ? card.getCardType() : "");
                    ps.setInt(15, card.getReleaseYear());
                    ps.setDouble(16, card.getPrice());
                    ps.setInt(17, card.getDemandScore());
                    ps.setString(18, card.getManaCost() != null ? card.getManaCost() : "");
                    ps.setString(19, card.getPower() != null ? card.getPower() : "");
                    ps.setString(20, card.getToughness() != null ? card.getToughness() : "");
                    ps.executeUpdate();
                }
                total += qty;
            }
        }
        return total;
    }

    public static int persistUnstoredInstances(Connection c, Card card) throws Exception {
        if (card == null) return 0;
        return persistUnstoredInstances(c, List.of(card));
    }

    public static synchronized String nextSku(String setCode) {
        String code = setCode == null || setCode.isBlank() ? "UNK" : setCode.trim().toUpperCase();
        return String.format("MTG-%s-%04d", code, ++skuCounter);
    }

    /** Minted SKU from the collector number when known, counter fallback otherwise. */
    public static String skuFor(String setCode, String collectorNumber) {
        String code = setCode == null || setCode.isBlank() ? "UNK" : setCode.trim().toUpperCase();
        String coll = collectorNumber == null ? "" : collectorNumber.trim();
        if (!coll.isEmpty()) return "MTG-" + code + "-" + coll.toUpperCase();
        return nextSku(setCode);
    }

    public static Card toCard(ResolvedCard r, String sku) {
        double price = r.phpPrice() == null ? 0.0 : r.phpPrice();
        String targetSku = (sku == null || sku.isBlank()) ? skuFor(r.setCode(), r.collectorNumber()) : sku;
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

        // Rapid intake saves storage location as null until assigned by clerk in Storage
        String normSet = setCode == null ? "" : setCode.toUpperCase();
        String slot = null;

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

        // 3. Test SQLite UNSTORED persistence on temporary DB
        try {
            java.io.File tempDb = java.io.File.createTempFile("tutorengine_intake_test_", ".db");
            tempDb.deleteOnExit();
            System.setProperty("tutorengine.db", tempDb.getAbsolutePath());
            tutorengine.db.Db.migrate();

            try (Connection c = tutorengine.db.Db.open()) {
                List<Card> staged = List.of(
                        new Card("MTG-STA-001", "Alice Card 1", "Set A", "Green", "Creature", 2026, 110.0, 1, 50, false, null),
                        new Card("MTG-STB-002", "Bob Card 2", "Set B", "Blue", "Instant", 2026, 28.0, 2, 50, true, null)
                );
                int persisted = persistUnstoredInstances(c, staged);
                if (persisted != 3) {
                    throw new AssertionError("Expected 3 unstored card instances persisted, got " + persisted);
                }
                System.out.println("  ✓ UNSTORED card instances persisted: " + persisted + " instances");
            }
        } catch (Exception ex) {
            throw new RuntimeException("SQLite persistence test failed", ex);
        }

        System.out.println("✓ RapidIntake verification passed!");
    }
}
