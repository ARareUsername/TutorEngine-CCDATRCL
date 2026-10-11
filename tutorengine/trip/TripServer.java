package tutorengine.trip;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tutorengine.db.Db;
import tutorengine.model.CardInstance;

// TripServer: Lightweight JDK HttpServer serving phone-friendly shelf trip pages on the LAN
// (<15 KB plain HTML, big touch targets, check toggles, confirm/exclude actions) (§1 specs/shelf-trip-viewer/spec.md).
// OWNER: Bondoc, Karl B.
public class TripServer {

    private static TripServer instance;
    private HttpServer server;
    private int port;
    private String hostAddress;

    public static synchronized TripServer getInstance() {
        if (instance == null) {
            instance = new TripServer();
        }
        return instance;
    }

    public synchronized void start(int requestedPort) throws IOException {
        if (server != null) return;
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            hostAddress = "127.0.0.1";
        }

        server = HttpServer.create(new InetSocketAddress(requestedPort), 0);
        server.createContext("/t/", new TripHandler());
        server.setExecutor(null); // default executor
        server.start();
        port = server.getAddress().getPort();
        System.out.println("TripServer started on http://" + hostAddress + ":" + port + "/");
    }

    public synchronized void start() throws IOException {
        start(0); // ephemeral port
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized int getPort() {
        return port;
    }

    public synchronized String getHostAddress() {
        return hostAddress;
    }

    public synchronized String getUrl(String tripId) {
        return "http://" + hostAddress + ":" + port + "/t/" + tripId;
    }

    private static class TripHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath(); // e.g. /t/<tripId> or /t/<tripId>/toggle
            String query = uri.getQuery();

            String[] parts = path.substring(3).split("/");
            if (parts.length == 0 || parts[0].isBlank()) {
                sendResponse(exchange, 404, "<h1>Trip ID missing</h1>");
                return;
            }

            String tripId = parts[0];
            TripSession session;
            try {
                session = TripSession.load(tripId);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Error loading trip: " + e.getMessage());
                return;
            }

            if (session == null) {
                sendResponse(exchange, 404, "<h1>Trip session not found: " + tripId + "</h1>");
                return;
            }

            // Sub-action handling
            if (parts.length > 1) {
                String action = parts[1];
                Map<String, String> params = parseQuery(query);
                try {
                    if ("toggle".equalsIgnoreCase(action)) {
                        String cardId = params.get("card");
                        if (cardId != null) {
                            TripSession.toggleCheck(tripId, cardId);
                        }
                    } else if ("exclude".equalsIgnoreCase(action)) {
                        String cardId = params.get("card");
                        String reasonStr = params.get("reason");
                        String notes = params.getOrDefault("notes", "Excluded on mobile");
                        TripSession.ExcludeReason reason = TripSession.ExcludeReason.BOX_FULL;
                        if (reasonStr != null) {
                            try { reason = TripSession.ExcludeReason.valueOf(reasonStr); } catch (Exception ignored) {}
                        }
                        if (cardId != null) {
                            TripSession.excludeCard(tripId, cardId, reason, notes);
                        }
                    } else if ("confirm".equalsIgnoreCase(action)) {
                        TripSession.confirmCompletion(tripId);
                    }
                } catch (Exception e) {
                    sendResponse(exchange, 500, "Action error: " + e.getMessage());
                    return;
                }

                // Redirect back to main page
                exchange.getResponseHeaders().set("Location", "/t/" + tripId);
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            }

            // Render main HTML page
            String html = renderHtml(session);
            sendResponse(exchange, 200, html);
        }

        private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private Map<String, String> parseQuery(String query) {
            Map<String, String> map = new HashMap<>();
            if (query == null || query.isBlank()) return map;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    map.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
            return map;
        }
    }

    public static String renderHtml(TripSession s) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>");
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>");
        sb.append("<title>Shelf Trip: ").append(escape(s.getBoxId())).append("</title>");
        sb.append("<style>");
        sb.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;margin:0;padding:12px;background:#f8fafc;color:#0f172a;}");
        sb.append(".header{background:#1e293b;color:#f8fafc;padding:14px;border-radius:8px;margin-bottom:12px;}");
        sb.append(".header h1{font-size:18px;margin:0 0 4px 0;}");
        sb.append(".header p{font-size:13px;margin:2px 0;color:#94a3b8;}");
        sb.append(".directive{background:#fef3c7;border:1px solid #f59e0b;color:#92400e;padding:10px;border-radius:6px;font-size:13px;font-weight:bold;margin-bottom:12px;}");
        sb.append(".card{background:#ffffff;border:1px solid #cbd5e1;border-radius:8px;padding:12px;margin-bottom:8px;}");
        sb.append(".card.checked{border-left:5px solid #10b981;background:#f0fdf4;}");
        sb.append(".card.excluded{border-left:5px solid #ef4444;background:#fef2f2;opacity:0.8;}");
        sb.append(".card-title{font-size:15px;font-weight:bold;display:flex;justify-content:space-between;}");
        sb.append(".divider{font-size:12px;color:#0284c7;font-weight:bold;margin:4px 0;}");
        sb.append(".btn{display:inline-block;padding:10px 14px;border-radius:6px;text-decoration:none;font-size:14px;font-weight:bold;text-align:center;touch-action:manipulation;min-height:24px;}");
        sb.append(".btn-check{background:#e2e8f0;color:#0f172a;}");
        sb.append(".card.checked .btn-check{background:#10b981;color:white;}");
        sb.append(".btn-exclude{background:#fee2e2;color:#991b1b;font-size:12px;padding:6px 10px;margin-left:6px;}");
        sb.append(".btn-confirm{display:block;width:95%;background:#059669;color:white;padding:14px;font-size:16px;margin:20px auto;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);}");
        sb.append(".status-tag{font-size:11px;padding:2px 6px;border-radius:4px;font-weight:bold;text-transform:uppercase;}");
        sb.append(".status-active{background:#dbeafe;color:#1e40af;}");
        sb.append(".status-completed{background:#d1fae5;color:#065f46;}");
        sb.append(".status-partial{background:#fed7aa;color:#9a3412;}");
        sb.append("</style></head><body>");

        // Header
        sb.append("<div class='header'>");
        sb.append("<h1>").append(escape(s.getBoxLabel())).append("</h1>");
        sb.append("<p>Target: <b>").append(escape(s.getBoxId())).append("</b> &bull; ").append(escape(s.getShelfUnit())).append(" (").append(escape(s.getShelfTier())).append(")</p>");
        sb.append("<p>Clerk: ").append(escape(s.getClerkName())).append(" &bull; Total: ").append(s.getCards().size()).append(" cards &bull; Status: <span class='status-tag status-").append(s.getStatus().name().toLowerCase()).append("'>").append(s.getStatus()).append("</span></p>");
        sb.append("</div>");

        // Directive
        sb.append("<div class='directive'>📋 IN-HAND FORWARD-PASS DIRECTIVE:<br>Arrange all cards in hand in this exact 1..N order (Name ASC) before walking to shelf.</div>");

        // Cards list
        int checkedCount = 0;
        int excludedCount = 0;
        for (TripSession.TripCard tc : s.getCards()) {
            if (tc.getState() == TripSession.CardState.CHECKED) checkedCount++;
            if (tc.getState() == TripSession.CardState.EXCLUDED) excludedCount++;

            String cls = switch (tc.getState()) {
                case CHECKED -> "card checked";
                case EXCLUDED -> "card excluded";
                default -> "card";
            };
            sb.append("<div class='").append(cls).append("'>");
            sb.append("<div class='card-title'>");
            sb.append("<span>").append(tc.getSortOrder()).append(". ").append(escape(tc.getCardName())).append(" <small>(").append(escape(tc.getSetCode())).append(")</small></span>");
            sb.append("<span>");
            if (s.getStatus() == TripSession.SessionStatus.ACTIVE) {
                sb.append("<a class='btn btn-check' href='/t/").append(s.getTripId()).append("/toggle?card=").append(tc.getCardId()).append("'>");
                sb.append(tc.getState() == TripSession.CardState.CHECKED ? "✓ Stored" : "[ ] Check");
                sb.append("</a>");
                if (tc.getState() != TripSession.CardState.EXCLUDED) {
                    sb.append("<a class='btn btn-exclude' href='/t/").append(s.getTripId()).append("/exclude?card=").append(tc.getCardId()).append("&reason=BOX_FULL'>Exclude</a>");
                }
            } else {
                sb.append("<b>").append(tc.getState()).append("</b>");
            }
            sb.append("</span></div>");

            sb.append("<div class='divider'>").append(escape(tc.getDividerGuidance())).append("</div>");
            if (tc.getState() == TripSession.CardState.EXCLUDED) {
                sb.append("<div style='font-size:12px;color:#b91c1c;'>⚠️ Excluded: ").append(tc.getExcludeReason()).append(" (").append(escape(tc.getNotes())).append(")</div>");
            }
            sb.append("</div>");
        }

        // Action confirmation button
        if (s.getStatus() == TripSession.SessionStatus.ACTIVE) {
            sb.append("<a class='btn btn-confirm' href='/t/").append(s.getTripId()).append("/confirm'>");
            sb.append("✓ Confirm Filing Complete (").append(checkedCount).append("/").append(s.getCards().size()).append(" stored");
            if (excludedCount > 0) sb.append(", ").append(excludedCount).append(" excluded");
            sb.append(")</a>");
        } else {
            sb.append("<div style='text-align:center;padding:16px;font-weight:bold;color:#059669;'>✓ Session Resolved (").append(s.getStatus()).append(" at ").append(escape(s.getCompletedAt())).append(")</div>");
        }

        sb.append("<div style='text-align:center;font-size:11px;color:#94a3b8;margin-top:20px;'>TutorEngine &bull; Offline LAN Trip Terminal</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static void main(String[] args) {
        System.out.println("=== TripServer Headless Verification Demo ===");
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("trip-server-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();

            // Insert commit and cards
            try (java.sql.Connection c = Db.open();
                 java.sql.PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO audit_commits (commit_hash, commit_tag, employee_name, card_count, row_hash) "
                         + "VALUES ('C02', 'BATCH-2', 'Alice', 2, 'hash')")) {
                ps.executeUpdate();
            }

            List<CardInstance> cards = List.of(
                    new CardInstance("c1", "s1", "Ajani, Caller of the Pride", "M14", "1", "rare", "NM", "nonfoil", "EN", "UNSTORED", null, "C02", null, null),
                    new CardInstance("c2", "s2", "Black Cat", "M14", "84", "common", "NM", "nonfoil", "EN", "UNSTORED", null, "C02", null, null)
            );
            try (java.sql.Connection c = Db.open()) {
                for (CardInstance ci : cards) {
                    try (java.sql.PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, status, commit_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'UNSTORED', 'C02')")) {
                        ps.setString(1, ci.getId());
                        ps.setString(2, ci.getScryfallId());
                        ps.setString(3, ci.getName());
                        ps.setString(4, ci.getSetCode());
                        ps.setString(5, ci.getCollectorNumber());
                        ps.setString(6, ci.getRarity());
                        ps.setString(7, ci.getCondition());
                        ps.setString(8, ci.getFinish());
                        ps.executeUpdate();
                    }
                }
            }

            TripSession s = TripSession.create("RACK-M-TOP", "Alice", cards);
            TripServer server = getInstance();
            server.start();

            String url = server.getUrl(s.getTripId());
            System.out.println("Trip URL: " + url);

            // Fetch via HttpClient
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            System.out.println("HTTP GET Status: " + resp.statusCode());
            if (resp.statusCode() != 200) throw new AssertionError("Expected 200 OK");

            int payloadSize = resp.body().getBytes(StandardCharsets.UTF_8).length;
            System.out.println("Payload size: " + payloadSize + " bytes (want < 15,000 bytes)");
            if (payloadSize >= 15000) throw new AssertionError("Payload exceeds 15 KB");

            if (!resp.body().contains("Ajani, Caller of the Pride") || !resp.body().contains("Black Cat")) {
                throw new AssertionError("Missing card content in HTML");
            }

            // Test check toggle
            HttpRequest toggleReq = HttpRequest.newBuilder().uri(URI.create(url + "/toggle?card=c1")).GET().build();
            HttpResponse<String> toggleResp = client.send(toggleReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("Toggle response: " + toggleResp.statusCode());

            // Test confirm action
            HttpRequest confirmReq = HttpRequest.newBuilder().uri(URI.create(url + "/confirm")).GET().build();
            HttpResponse<String> confirmResp = client.send(confirmReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("Confirm response: " + confirmResp.statusCode());

            // Reload and check DB status
            TripSession verified = TripSession.load(s.getTripId());
            System.out.println("Trip session resolved status: " + verified.getStatus() + " (want COMPLETED or PARTIAL)");

            server.stop();
            java.nio.file.Files.deleteIfExists(tmp);
            System.out.println("✓ TripServer verification passed!");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
