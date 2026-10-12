package tutorengine.search;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tutorengine.model.Card;
import tutorengine.util.CSVLoader;

// OWNER: Bondoc, Karl B. — live card intake. Hand-rolled JSON scan (two flat
// shapes); a parsing library for two calls is unjustifiable.
//
// PURPOSE: Add cards that are NOT in the catalog. Autocomplete finds the real
// game card by fragment, exact-detail maps it to Card fields with a PHP price,
// and the dialog (CatalogView) verifies the picture before confirming.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.search.ScryfallLookup
//   Expected: "shah" suggests Shahrazad; exact maps set/type/mana/USD 475.00;
//   a null-USD card leaves price blank; unreachable host returns empty + message.
public final class ScryfallLookup {
    static final String API = "https://api.scryfall.com";
    private static final int CACHE_CAP = 20;
    private static final Map<String, List<String>> recentQueries = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<String>> e) {
            return size() > CACHE_CAP;
        }
    };
    private static int skuSeq = 0;

    private ScryfallLookup() {}

    // Mapped draft of an exact record; usdPrice null when Scryfall has none.
    public record Draft(String name, String setName, String setCode, String color,
                        String cardType, int releaseYear, Double usdPrice,
                        String manaCost, String power, String toughness) {}

    public static List<String> autocomplete(String query) {
        return autocomplete(API, query);
    }

    static List<String> autocomplete(String base, String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return List.of();
        synchronized (recentQueries) {
            if (recentQueries.containsKey(q.toLowerCase())) return recentQueries.get(q.toLowerCase());
        }
        List<String> out = new ArrayList<>();
        try {
            String body = get(base + "/cards/autocomplete?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
            for (String s : strArray(body, "data")) out.add(s);
        } catch (Exception e) {
            debug("autocomplete miss: " + e.getMessage());
        }
        synchronized (recentQueries) {
            recentQueries.put(q.toLowerCase(), List.copyOf(out));
        }
        return out;
    }

    public static Draft fetchDetail(String exactName) {
        return fetchDetail(API, exactName);
    }

    static Draft fetchDetail(String base, String exactName) {
        String q = exactName == null ? "" : exactName.trim();
        if (q.isEmpty()) return null;
        try {
            String body = get(base + "/cards/named?exact="
                    + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&format=json");
            // Double-faced cards: front face carries name/mana/type/stats.
            String face = firstObject(body, "card_faces");
            String src = face == null ? body : face;
            String setCode = str(body, "set");
            return new Draft(
                    str(src, "name"), str(body, "set_name"),
                    setCode == null ? "" : setCode.toUpperCase(),
                    colorOf(strArray(body, "colors")),
                    str(src, "type_line"), yearOf(str(body, "released_at")),
                    num(body, "usd"), str(src, "mana_cost"),
                    str(src, "power"), str(src, "toughness"));
        } catch (Exception e) {
            debug("detail miss: " + e.getMessage());
            return null;
        }
    }

    // Draft -> session Card (PHP price, blank stays blank for the form to fill).
    public static Card toCard(Draft d, String sku, int qty, String slot, Double phpPrice) {
        double price = phpPrice == null ? 0.0 : phpPrice;
        return new Card(sku, d.name(), d.setName(), d.color(), d.cardType(),
                d.releaseYear(), price, qty, 50, false,
                slot == null || slot.isBlank() ? "Intake Sorting Desk" : slot.toUpperCase(),
                d.manaCost() == null ? "" : d.manaCost(),
                d.power() == null ? "" : d.power(),
                d.toughness() == null ? "" : d.toughness(),
                d.setCode() == null ? "" : d.setCode());
    }

    public static Double toPhp(Double usd) {
        if (usd == null) return null;
        return Math.round(usd * CSVLoader.USD_TO_PHP * 100.0) / 100.0;
    }

    public static synchronized String nextSku(String setCode) {
        String code = setCode == null || setCode.isBlank() ? "UNK" : setCode.trim().toUpperCase();
        return String.format("MTG-%s-%03d", code, ++skuSeq);
    }

    static String colorOf(List<String> colors) {
        if (colors == null || colors.isEmpty()) return "Colorless";
        if (colors.size() == 1) return switch (colors.get(0)) {
            case "W" -> "White"; case "U" -> "Blue"; case "B" -> "Black";
            case "R" -> "Red"; case "G" -> "Green"; default -> "Colorless";
        };
        return "Multi";
    }

    static int yearOf(String releasedAt) {
        try {
            return Integer.parseInt(releasedAt.substring(0, 4));
        } catch (Exception e) {
            return 2026;
        }
    }

    // --- minimal JSON scan (flat string/number/array shapes only) ---

    public static String str(String json, String key) {
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

    static Double num(String json, String key) {
        // nested one level (prices.usd): search raw "\"usd\":" anywhere.
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int c = json.indexOf(':', i + k.length());
        if (c < 0) return null;
        int j = c + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
        if (json.startsWith("null", j)) return null;
        // Scryfall quotes some numbers ("usd": "475.00"): accept both forms.
        if (json.charAt(j) == '"') {
            int q = json.indexOf('"', j + 1);
            if (q < 0) return null;
            try {
                return Double.parseDouble(json.substring(j + 1, q));
            } catch (Exception ex) {
                return null;
            }
        }
        int e = j;
        while (e < json.length() && "0123456789.+-eE".indexOf(json.charAt(e)) >= 0) e++;
        try {
            return Double.parseDouble(json.substring(j, e));
        } catch (Exception ex) {
            return null;
        }
    }

    static List<String> strArray(String json, String key) {
        List<String> out = new ArrayList<>();
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return out;
        int c = json.indexOf(':', i + k.length());
        if (c < 0) return out;
        int a = json.indexOf('[', c);
        int z = json.indexOf(']', a);
        if (a < 0 || z < 0) return out;
        for (String part : json.substring(a + 1, z).split(",")) {
            part = part.trim();
            if (part.startsWith("\"") && part.endsWith("\"") && part.length() >= 2)
                out.add(part.substring(1, part.length() - 1));
            else if (!part.isEmpty() && !part.equals("null")) out.add(part);
        }
        return out;
    }

    // First {...} inside "key": [...] (card_faces front face), balanced braces.
    static String firstObject(String json, String key) {
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

    public static String get(String url) throws Exception {
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
        if (r.statusCode() != 200) throw new IllegalStateException("status " + r.statusCode());
        return r.body();
    }

    static void debug(String msg) {
        if (Boolean.getBoolean("tutorengine.debug")) System.err.println(msg);
    }

    public static void main(String[] args) {
        System.out.println("autocomplete 'shah' -> " + autocomplete("shah"));
        Draft d = fetchDetail("Shahrazad");
        System.out.println("detail: " + (d == null ? "MISS" : d));
        System.out.println("php: " + (d == null ? "n/a" : toPhp(d.usdPrice())));
        Card c = d == null ? null : toCard(d, nextSku(d.setCode()), 4, "", toPhp(d.usdPrice()));
        System.out.println("card: " + c);
        System.out.println("null-USD draft -> php=" + toPhp(null) + " (blank for the form)");
        System.out.println("offline -> " + autocomplete("http://127.0.0.1:9", "zzzq") + " (empty + message)");
        System.out.println("ScryfallLookup OK.");
    }
}
