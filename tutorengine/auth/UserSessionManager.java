package tutorengine.auth;

import java.time.Instant;

// OWNER: Bondoc, Karl B. — session state. Thin over Employees; no UI here.
//
// PURPOSE: Exactly one authenticated employee at a time. Commits and drafts
// call requireEmployee() so unattributed work fails loudly at the boundary.
// Switching closes the old session row, opens a new one, and runs the
// registered switch hook (buffer save — wired by the drafts change later).
public final class UserSessionManager {
    private static final UserSessionManager INSTANCE = new UserSessionManager();

    private Employees.Employee current;
    private String sessionId;
    private Instant sessionStart;
    private Runnable switchHook;

    private UserSessionManager() {}

    public static UserSessionManager getInstance() { return INSTANCE; }

    public boolean isAuthenticated() { return current != null; }

    public Employees.Employee requireEmployee() {
        if (current == null) throw new IllegalStateException("No active employee logged in!");
        return current;
    }

    public Employees.Employee currentOrNull() { return current; }

    public String sessionId() { return sessionId; }

    public Instant sessionStart() { return sessionStart; }

    // Commit tag for the audit trail: BATCH-yyyyMMdd-HHmmss-<Name>-<N>cards.
    // Later changes (rapid intake, audit commits) call this at commit time.
    public static String commitTag(String displayName, int count) {
        String stamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(java.time.LocalDateTime.now());
        String who = displayName == null ? "unknown"
                : displayName.trim().replaceAll("\\s+", "_").replaceAll("^[^A-Za-z0-9]+|[^A-Za-z0-9]+$", "");
        if (who.isEmpty()) who = "unknown";
        return "BATCH-" + stamp + "-" + who + "-" + count + "cards";
    }

    public void setSwitchHook(Runnable hook) { this.switchHook = hook; }

    public boolean login(String username, String pin) throws Exception {
        Employees.Employee e = Employees.verifyPin(username, pin);
        if (e == null) return false;
        start(e);
        return true;
    }

    public boolean loginBadge(String barcode) throws Exception {
        Employees.Employee e = Employees.findByBadge(barcode);
        if (e == null) return false;
        start(e);
        return true;
    }

    public void logout() throws Exception {
        if (sessionId != null) Employees.closeSession(sessionId);
        current = null;
        sessionId = null;
        sessionStart = null;
        if (switchHook != null) switchHook.run();
    }

    // Switch = logout + login as one visible hand-off (buffer saved by the hook).
    public boolean switchTo(String username, String pin) throws Exception {
        logout();
        return login(username, pin);
    }

    private void start(Employees.Employee e) throws Exception {
        current = e;
        sessionStart = Instant.now();
        sessionId = Employees.openSession(e.id());
    }

    public static void main(String[] args) {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("tutorengine-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            tutorengine.db.Db.migrate();
            Employees.register("karl_m", "Karl M.", "1234", "CLERK", null, null);
            Employees.register("sarah_t", "Sarah T.", "5678", "MANAGER", null, null);
            UserSessionManager m = getInstance();
            System.out.println("login A=" + m.login("karl_m", "1234")
                    + " who=" + m.requireEmployee().displayName());
            try {
                m.logout();
                m.requireEmployee();
                System.out.println("require-after-logout=NO-THROW (bug)");
            } catch (IllegalStateException expected) {
                System.out.println("require-after-logout throws (want true)");
            }
            final boolean[] hookRan = {false};
            m.setSwitchHook(() -> hookRan[0] = true);
            System.out.println("switch B=" + m.switchTo("sarah_t", "5678")
                    + " who=" + m.requireEmployee().displayName()
                    + " hook=" + hookRan[0]);
            System.out.println("bad login=" + !m.login("karl_m", "0000")
                    + " still=" + m.requireEmployee().username());
            m.logout();
            System.out.println("UserSessionManager OK.");
            System.out.println("tag=" + commitTag("Karl M.", 42)
                    + " (want BATCH-<stamp>-Karl_M-42cards)");
            java.nio.file.Files.deleteIfExists(tmp);
            m.setSwitchHook(null);
        } catch (Exception e) {
            System.out.println("SESSION ERROR: " + e.getMessage());
        }
    }
}
