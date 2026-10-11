package tutorengine.ui;

import java.awt.Image;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import tutorengine.model.Card;
import tutorengine.util.CSVLoader;

// OWNER: Bondoc, Karl B. — card-picture double-check. Do not edit files owned
// by others; use PR comments instead.
//
// PURPOSE: The associate verifies the physical card against its picture.
// Thumbnails come from Scryfall (`cards/named`, small ~146x204) on first
// search, then live in git-ignored ImageCache/<SKU>.jpg forever after:
// no connection needed on repeat views. Any failure (offline, unknown card)
// returns null and the caller shows a placeholder — the app never crashes.
//
// STORAGE MATH (TutorEngine.md section 8): 60 small thumbs ~= 1-3 MB; cap the
// cache at ~50 MB with delete-oldest eviction so it never grows unbounded.
// Scryfall rate limit respected: cache first, ~100 ms minimum between fetches.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.ui.CardImageCache
//   Expected: "HIT ImageCache/<SKU>.jpg (<bytes> B)" on repeat runs, or
//   "MISS (offline?) ..." with no exception when there is no connection.
//   Either way the demo exits 0 — offline is a supported state, not a failure.
public final class CardImageCache {
    static final Path DIR = Path.of("ImageCache");
    static final long MAX_BYTES = 50L * 1024 * 1024;
    static final long MIN_GAP_MS = 100;
    private static long lastFetchMs = 0;

    private CardImageCache() {}

    public static Path pathFor(String sku) {
        return pathFor(sku, false);
    }

    // Large variant (Scryfall normal) lives beside the small as <SKU>-large.jpg.
    public static Path pathFor(String sku, boolean large) {
        return pathFor(sku, large ? Variant.NORMAL : Variant.SMALL);
    }

    public enum Variant {
        SMALL("small", ""), NORMAL("normal", "-normal"), LARGE("large", "-large");
        final String api;
        final String suffix;
        Variant(String api, String suffix) { this.api = api; this.suffix = suffix; }
    }

    public static Path pathFor(String sku, Variant v) {
        return DIR.resolve(sku.replaceAll("[^A-Za-z0-9-]", "_") + v.suffix + ".jpg");
    }

    // Cached thumbnail scaled to `width` px (aspect kept); null = unavailable.
    public static synchronized ImageIcon get(String sku, String cardName, int width) {
        byte[] bytes = fetchBytes(sku, cardName, false);
        return bytes == null ? null : readBytes(bytes, width);
    }

    // JavaFX twin of get(): same cache files, JavaFX Image out. Null-safe;
    // image decode needs no running toolkit, only rendering does.
    public static synchronized javafx.scene.image.Image getFx(String sku, String cardName, int width) {
        return getFx(sku, cardName, width, false);
    }

    // Large=true pulls the Scryfall normal variant for big previews.
    public static synchronized javafx.scene.image.Image getFx(String sku, String cardName, int width, boolean large) {
        return getFx(sku, cardName, width, large ? Variant.NORMAL : Variant.SMALL);
    }

    public static synchronized javafx.scene.image.Image getFx(
            String sku, String cardName, int width, Variant v) {
        return getFx(sku, cardName, width, v, "");
    }

    // setCode constrains the lookup to our printing: Scryfall resolves bare
    // token names like "Wolf" to a different card ("Human // Wolf") without it.
    public static synchronized javafx.scene.image.Image getFx(
            String sku, String cardName, int width, Variant v, String setCode) {
        try {
            byte[] bytes = fetchBytes(sku, cardName, v, setCode);
            if (bytes == null) return null;
            return new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes),
                    Math.max(1, width), 0, true, true);
        } catch (Exception e) {
            if (Boolean.getBoolean("tutorengine.debug")) e.printStackTrace();
            return null;
        }
    }

    // Raw cached-or-fetched JPEG bytes; null when offline/unknown. Headless-safe.
    static synchronized byte[] fetchBytes(String sku, String cardName, boolean large) {
        return fetchBytes(sku, cardName, large ? Variant.NORMAL : Variant.SMALL);
    }

    // One-time migration: early -large.jpg files hold normal bytes; move them
    // aside so -large.jpg always means the large variant. Idempotent, safe.
    public static synchronized void migrateCache() {
        try {
            Files.createDirectories(DIR);
            try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(DIR, "*-large.jpg")) {
                for (Path p : ds) {
                    String name = p.getFileName().toString();
                    Path normal = DIR.resolve(name.substring(0, name.length() - "-large.jpg".length()) + "-normal.jpg");
                    if (!Files.exists(normal)) Files.move(p, normal);
                }
            }
        } catch (Exception e) {
            if (Boolean.getBoolean("tutorengine.debug")) e.printStackTrace();
        }
    }

    private static boolean migrated = false;

    static synchronized byte[] fetchBytes(String sku, String cardName, Variant v) {
        return fetchBytes(sku, cardName, v, "");
    }

    static synchronized byte[] fetchBytes(String sku, String cardName, Variant v, String setCode) {
        try {
            if (!migrated) { migrated = true; migrateCache(); }
            Files.createDirectories(DIR);
            Path p = pathFor(sku, v);
            if (Files.exists(p)) return Files.readAllBytes(p);
            fetch(sku, cardName, p, v, setCode);
            evictIfNeeded();
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (Exception e) {
            if (Boolean.getBoolean("tutorengine.debug")) e.printStackTrace();
            return null;
        }
    }

    private static ImageIcon readBytes(byte[] bytes, int width) {
        try {
            var img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img == null) return null;
            int w = Math.max(1, width);
            int h = Math.max(1, img.getHeight() * w / Math.max(1, img.getWidth()));
            return new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH));
        } catch (Exception e) {
            return null;
        }
    }

    private static void fetch(String sku, String cardName, Path dest, boolean large) throws Exception {
        fetch(sku, cardName, dest, large ? Variant.NORMAL : Variant.SMALL);
    }

    private static HttpResponse<byte[]> send(HttpClient client, String url) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "TutorEngine/1.0 (CCDATRCL school project)")
                        .header("Accept", "image/jpeg")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static void fetch(String sku, String cardName, Path dest, Variant v) throws Exception {
        fetch(sku, cardName, dest, v, "");
    }

    private static void fetch(String sku, String cardName, Path dest, Variant v, String setCode) throws Exception {
        long wait = MIN_GAP_MS - (System.currentTimeMillis() - lastFetchMs);
        if (wait > 0) Thread.sleep(wait);
        lastFetchMs = System.currentTimeMillis();
        // Double-faced names ("A // B") 404 on exact: query the front face.
        String query = cardName.contains("//") ? cardName.split("//")[0].trim() : cardName;
        String set = setCode == null ? "" : setCode.trim().toLowerCase();
        String base = "https://api.scryfall.com/cards/named?exact="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&format=image&version=" + v.api;
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<byte[]> r = send(client, base + (set.isEmpty() ? "" : "&set=" + set));
        // Set-constrained miss (custom printings): fall back to name-only.
        if (r.statusCode() == 404 && !set.isEmpty()) r = send(client, base);
        if (Boolean.getBoolean("tutorengine.debug"))
            System.err.println("scryfall status=" + r.statusCode() + " bytes=" + r.body().length
                    + " body=" + new String(r.body(), StandardCharsets.UTF_8));
        if (r.statusCode() == 200 && r.body().length > 0) Files.write(dest, r.body());
    }

    static void evictIfNeeded() throws Exception {
        List<Path> jpgs = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(DIR, "*.jpg")) {
            for (Path p : ds) jpgs.add(p);
        }
        long total = 0;
        for (Path p : jpgs) total += Files.size(p);
        if (total <= MAX_BYTES) return;
        jpgs.sort(Comparator.comparingLong(p -> {
            try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0; }
        }));
        for (Path p : jpgs) {
            total -= Files.size(p);
            Files.deleteIfExists(p);
            if (total <= MAX_BYTES) break;
        }
    }

    public static void main(String[] args) {
        List<Card> cards = CSVLoader.load("Dataset/cards.csv");
        if (cards.isEmpty()) { System.out.println("no dataset rows; nothing to preview."); return; }
        Card c = cards.get(0);
        ImageIcon icon = get(c.getSku(), c.getName(), 146);
        Path p = pathFor(c.getSku());
        if (icon != null && Files.exists(p)) {
            try { System.out.println("HIT " + p + " (" + Files.size(p) + " B)"); }
            catch (Exception e) { System.out.println("HIT " + p); }
        } else {
            System.out.println("MISS (offline?) for " + c.getSku() + " — placeholder path, app keeps working.");
        }
        byte[] raw = fetchBytes(c.getSku(), c.getName(), false);
        System.out.println("fetchBytes=" + (raw == null ? "null" : raw.length + " B"));
        try {
            javafx.scene.image.Image fx = getFx(c.getSku(), c.getName(), 146);
            System.out.println(fx == null ? "getFx=null (offline or headless toolkit)"
                    : "getFx=" + (int) fx.getWidth() + "x" + (int) fx.getHeight());
        } catch (Exception e) {
            System.out.println("getFx needs a running toolkit (visual check on display)");
        }
        System.out.println("CardImageCache OK.");
    }
}
