package tutorengine.trip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import tutorengine.db.Db;
import tutorengine.model.CardInstance;
import tutorengine.model.StorageLocation;
import tutorengine.storage.DividerGuide;

// TripSession: Shelf filing trip batch model, forward-pass presorting, SQLite persistence,
// per-card state management, and plain-text slip generation (§1-§2 specs/shelf-trip-viewer/spec.md,
// §1 specs/partial-batch-resolution/spec.md).
// OWNER: Bondoc, Karl B.
public class TripSession {

    public enum CardState {
        PENDING,
        CHECKED,
        EXCLUDED
    }

    public enum ExcludeReason {
        BOX_FULL("Box is at maximum physical capacity"),
        NOT_FOUND("Card not found in clerk hand / batch"),
        DAMAGED("Card condition damaged during transit"),
        MISPLACED("Card belongs to different set/box");

        private final String description;
        ExcludeReason(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    public enum SessionStatus {
        ACTIVE,
        COMPLETED,
        PARTIAL
    }

    public static class TripCard {
        private final String cardId;
        private final String cardName;
        private final String setCode;
        private final int sortOrder;
        private final String dividerGuidance;
        private CardState state;
        private ExcludeReason excludeReason;
        private String notes;

        public TripCard(String cardId, String cardName, String setCode, int sortOrder,
                        String dividerGuidance, CardState state, ExcludeReason excludeReason, String notes) {
            this.cardId = cardId;
            this.cardName = cardName;
            this.setCode = setCode;
            this.sortOrder = sortOrder;
            this.dividerGuidance = dividerGuidance;
            this.state = state;
            this.excludeReason = excludeReason;
            this.notes = notes;
        }

        public String getCardId() { return cardId; }
        public String getCardName() { return cardName; }
        public String getSetCode() { return setCode; }
        public int getSortOrder() { return sortOrder; }
        public String getDividerGuidance() { return dividerGuidance; }
        public CardState getState() { return state; }
        public void setState(CardState state) { this.state = state; }
        public ExcludeReason getExcludeReason() { return excludeReason; }
        public void setExcludeReason(ExcludeReason reason) { this.excludeReason = reason; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    private final String tripId;
    private final String boxId;
    private final String boxLabel;
    private final String shelfUnit;
    private final String shelfTier;
    private final String clerkName;
    private final List<TripCard> cards = new ArrayList<>();
    private SessionStatus status;
    private final String createdAt;
    private String completedAt;

    public TripSession(String tripId, String boxId, String boxLabel, String shelfUnit,
                       String shelfTier, String clerkName, SessionStatus status,
                       String createdAt, String completedAt) {
        this.tripId = tripId;
        this.boxId = boxId;
        this.boxLabel = boxLabel;
        this.shelfUnit = shelfUnit;
        this.shelfTier = shelfTier;
        this.clerkName = clerkName;
        this.status = status;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public String getTripId() { return tripId; }
    public String getBoxId() { return boxId; }
    public String getBoxLabel() { return boxLabel; }
    public String getShelfUnit() { return shelfUnit; }
    public String getShelfTier() { return shelfTier; }
    public String getClerkName() { return clerkName; }
    public List<TripCard> getCards() { return cards; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public String getCompletedAt() { return completedAt; }

    // Creates and persists a new trip session from a collection of unstored cards
    public static TripSession create(String boxId, String clerkName, List<CardInstance> unstoredCards) throws Exception {
        String tripId = UUID.randomUUID().toString();
        String label = boxId;
        String unit = "White Cabinet";
        String tier = "LOW";

        for (StorageLocation s : StorageLocation.slots()) {
            if (s.getId().equalsIgnoreCase(boxId)) {
                label = s.getLabel();
                unit = s.getZone().equals("Rack") ? "Metal Wire Rack" : "White Cabinet";
                tier = s.getShelf();
                break;
            }
        }

        List<CardInstance> sorted = new ArrayList<>(unstoredCards);
        sorted.sort(Comparator.comparing(CardInstance::getName, String.CASE_INSENSITIVE_ORDER));

        String now = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
        TripSession session = new TripSession(tripId, boxId, label, unit, tier, clerkName, SessionStatus.ACTIVE, now, null);

        int order = 1;
        for (CardInstance ci : sorted) {
            DividerGuide.Guidance g = DividerGuide.calculateFromDb(boxId, ci.getName());
            TripCard tc = new TripCard(ci.getId(), ci.getName(), ci.getSetCode(), order++,
                    g.guidanceText(), CardState.PENDING, null, "");
            session.getCards().add(tc);
        }

        // Persist to DB
        try (Connection c = Db.open()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO trip_sessions (trip_id, box_id, clerk_name, total_cards, status, created_at) "
                    + "VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)")) {
                ps.setString(1, tripId);
                ps.setString(2, boxId);
                ps.setString(3, clerkName);
                ps.setInt(4, session.getCards().size());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO trip_cards (trip_id, card_id, card_name, set_code, sort_order, state, exclude_reason, notes) "
                    + "VALUES (?, ?, ?, ?, ?, 'PENDING', NULL, '')")) {
                for (TripCard tc : session.getCards()) {
                    ps.setString(1, tripId);
                    ps.setString(2, tc.getCardId());
                    ps.setString(3, tc.getCardName());
                    ps.setString(4, tc.getSetCode());
                    ps.setInt(5, tc.getSortOrder());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        return session;
    }

    // Loads a trip session by trip ID from DB
    public static TripSession load(String tripId) throws Exception {
        TripSession session = null;
        try (Connection c = Db.open()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT trip_id, box_id, clerk_name, status, created_at, completed_at FROM trip_sessions WHERE trip_id = ?")) {
                ps.setString(1, tripId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String boxId = rs.getString(2);
                        String label = boxId;
                        String unit = "White Cabinet";
                        String tier = "LOW";
                        for (StorageLocation s : StorageLocation.slots()) {
                            if (s.getId().equalsIgnoreCase(boxId)) {
                                label = s.getLabel();
                                unit = s.getZone().equals("Rack") ? "Metal Wire Rack" : "White Cabinet";
                                tier = s.getShelf();
                                break;
                            }
                        }
                        session = new TripSession(rs.getString(1), boxId, label, unit, tier,
                                rs.getString(3), SessionStatus.valueOf(rs.getString(4)),
                                rs.getString(5), rs.getString(6));
                    }
                }
            }

            if (session != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT card_id, card_name, set_code, sort_order, state, exclude_reason, notes "
                        + "FROM trip_cards WHERE trip_id = ? ORDER BY sort_order ASC")) {
                    ps.setString(1, tripId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String cardId = rs.getString(1);
                            String name = rs.getString(2);
                            String set = rs.getString(3);
                            int order = rs.getInt(4);
                            CardState state = CardState.valueOf(rs.getString(5));
                            String reasonStr = rs.getString(6);
                            ExcludeReason reason = reasonStr != null ? ExcludeReason.valueOf(reasonStr) : null;
                            String notes = rs.getString(7);
                            DividerGuide.Guidance g = DividerGuide.calculateFromDb(session.getBoxId(), name);
                            session.getCards().add(new TripCard(cardId, name, set, order, g.guidanceText(), state, reason, notes));
                        }
                    }
                }
            }
        }
        return session;
    }

    // Toggles card check state between PENDING and CHECKED
    public static boolean toggleCheck(String tripId, String cardId) throws Exception {
        try (Connection c = Db.open()) {
            String currentState = "PENDING";
            try (PreparedStatement ps = c.prepareStatement("SELECT state FROM trip_cards WHERE trip_id = ? AND card_id = ?")) {
                ps.setString(1, tripId);
                ps.setString(2, cardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) currentState = rs.getString(1);
                }
            }

            String nextState = currentState.equals("CHECKED") ? "PENDING" : "CHECKED";
            try (PreparedStatement ps = c.prepareStatement("UPDATE trip_cards SET state = ? WHERE trip_id = ? AND card_id = ?")) {
                ps.setString(1, nextState);
                ps.setString(2, tripId);
                ps.setString(3, cardId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    // Excludes a card with reason and notes, logging an exception and flagging card instance
    public static boolean excludeCard(String tripId, String cardId, ExcludeReason reason, String notes) throws Exception {
        String boxId = null;
        String clerk = "Clerk";
        try (Connection c = Db.open()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT box_id, clerk_name FROM trip_sessions WHERE trip_id = ?")) {
                ps.setString(1, tripId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        boxId = rs.getString(1);
                        clerk = rs.getString(2);
                    }
                }
            }

            // Update trip_cards
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE trip_cards SET state = 'EXCLUDED', exclude_reason = ?, notes = ? WHERE trip_id = ? AND card_id = ?")) {
                ps.setString(1, reason.name());
                ps.setString(2, notes != null ? notes : "");
                ps.setString(3, tripId);
                ps.setString(4, cardId);
                ps.executeUpdate();
            }

            // Record into inventory_exceptions
            if (boxId != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO inventory_exceptions (exception_id, card_instance_id, reported_by, target_box_id, reason_code, notes, resolved, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP)")) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, cardId);
                    ps.setString(3, clerk);
                    ps.setString(4, boxId);
                    ps.setString(5, reason.name());
                    ps.setString(6, notes != null ? notes : "");
                    ps.executeUpdate();
                }
            }

            // Flag card in card_instances to block identical reroute
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE card_instances SET status = 'UNSTORED_FLAGGED' WHERE id = ?")) {
                ps.setString(1, cardId);
                ps.executeUpdate();
            }
        }
        return true;
    }

    // Confirms completion of session: marks checked cards STORED, resolves status
    public static int confirmCompletion(String tripId) throws Exception {
        TripSession session = load(tripId);
        if (session == null) return 0;

        int storedCount = 0;
        int excludedCount = 0;

        try (Connection c = Db.open()) {
            for (TripCard tc : session.getCards()) {
                if (tc.getState() == CardState.CHECKED) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE card_instances SET status = 'STORED', box_id = ?, stored_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                        ps.setString(1, session.getBoxId());
                        ps.setString(2, tc.getCardId());
                        if (ps.executeUpdate() > 0) storedCount++;
                    }
                } else if (tc.getState() == CardState.EXCLUDED) {
                    excludedCount++;
                }
            }

            // Update box container card count
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE box_containers SET current_card_count = "
                    + "(SELECT COUNT(*) FROM card_instances WHERE box_id = ? AND status = 'STORED') WHERE box_id = ?")) {
                ps.setString(1, session.getBoxId());
                ps.setString(2, session.getBoxId());
                ps.executeUpdate();
            }

            // Update trip_sessions status
            SessionStatus nextStatus = (excludedCount > 0) ? SessionStatus.PARTIAL : SessionStatus.COMPLETED;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE trip_sessions SET status = ?, completed_at = CURRENT_TIMESTAMP WHERE trip_id = ?")) {
                ps.setString(1, nextStatus.name());
                ps.setString(2, tripId);
                ps.executeUpdate();
            }
        }
        return storedCount;
    }

    // Renders an 80mm-friendly plain text routing slip
    public static String renderPlainSlip(TripSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("      TUTORENGINE SHELF TRIP SLIP       \n");
        sb.append("========================================\n");
        sb.append(String.format("Trip ID:    %s\n", session.getTripId()));
        sb.append(String.format("Clerk:      %s\n", session.getClerkName()));
        sb.append(String.format("Created:    %s\n", session.getCreatedAt()));
        sb.append(String.format("Target Box: %s [%s]\n", session.getBoxLabel(), session.getBoxId()));
        sb.append(String.format("Shelf:      %s (%s)\n", session.getShelfUnit(), session.getShelfTier()));
        sb.append(String.format("Batch Size: %d cards\n", session.getCards().size()));
        sb.append("----------------------------------------\n");
        sb.append("PRE-SORT DIRECTIVE:\n");
        sb.append("Arrange cards 1..N in exact top-to-bottom\n");
        sb.append("order before walking to the shelf.\n");
        sb.append("----------------------------------------\n");

        for (TripCard tc : session.getCards()) {
            String checkMark = switch (tc.getState()) {
                case CHECKED -> "[X]";
                case EXCLUDED -> "[!]";
                default -> "[ ]";
            };
            sb.append(String.format("%s %d. %s [%s]\n", checkMark, tc.getSortOrder(), tc.getCardName(), tc.getSetCode()));
            sb.append(String.format("    %s\n", tc.getDividerGuidance()));
            if (tc.getState() == CardState.EXCLUDED) {
                sb.append(String.format("    EXCLUDED: %s (%s)\n", tc.getExcludeReason(), tc.getNotes()));
            }
        }

        sb.append("========================================\n");
        sb.append("[ ] Stored Complete     [ ] Partial Issue\n\n");
        sb.append("Clerk Signature: _______________________\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("=== TripSession Headless Verification Demo ===");
        try {
            Path tmp = Files.createTempFile("trip-session-test-", ".db");
            System.setProperty("tutorengine.db", tmp.toString());
            Db.migrate();

            // Insert dummy audit commit
            try (Connection c = Db.open();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO audit_commits (commit_hash, commit_tag, employee_name, card_count, row_hash) "
                         + "VALUES ('C01', 'BATCH-1', 'Karl', 6, 'hash')")) {
                ps.executeUpdate();
            }

            // Create 6 card instances
            List<CardInstance> cards = new ArrayList<>();
            String[] names = {
                "Liliana, Dreadhorde General",
                "Ajani, Caller of the Pride",
                "Teferi, Time Raveler",
                "Chandra, Pyromaster",
                "Black Cat",
                "Dark Confidant"
            };

            for (int i = 0; i < names.length; i++) {
                String id = "ci-" + (i + 1);
                try (Connection c = Db.open();
                     PreparedStatement ps = c.prepareStatement(
                             "INSERT INTO card_instances (id, scryfall_id, name, set_code, collector_number, rarity, condition, finish, status, box_id, commit_id) "
                             + "VALUES (?, ?, ?, 'M14', ?, 'rare', 'NM', 'nonfoil', 'UNSTORED', NULL, 'C01')")) {
                    ps.setString(1, id);
                    ps.setString(2, "scry-" + i);
                    ps.setString(3, names[i]);
                    ps.setString(4, String.valueOf(i + 1));
                    ps.executeUpdate();
                }
                cards.add(new CardInstance(id, "scry-" + i, names[i], "M14", String.valueOf(i + 1), "rare", "NM", "nonfoil", "EN", "UNSTORED", null, "C01", null, null));
            }

            // 1. Create trip session
            TripSession session = create("RACK-M-TOP", "KarlBondoc", cards);
            System.out.println("Created trip session: " + session.getTripId());
            if (session.getCards().size() != 6) throw new AssertionError("Expected 6 cards");

            // Verify presort Name ASC order
            System.out.println("Forward-pass pre-sort order:");
            for (TripCard tc : session.getCards()) {
                System.out.println("  " + tc.getSortOrder() + ". " + tc.getCardName() + " -> " + tc.getDividerGuidance());
            }
            if (!session.getCards().get(0).getCardName().equals("Ajani, Caller of the Pride")) {
                throw new AssertionError("Presort failed: first should be Ajani");
            }

            // 2. Toggle check on first 5 cards
            for (int i = 0; i < 5; i++) {
                toggleCheck(session.getTripId(), session.getCards().get(i).getCardId());
            }

            // 3. Exclude the 6th card (Teferi) as BOX_FULL with note
            String excludedCardId = session.getCards().get(5).getCardId();
            excludeCard(session.getTripId(), excludedCardId, ExcludeReason.BOX_FULL, "Box slot too tight, cannot fit safely");

            // 4. Confirm completion
            int storedCount = confirmCompletion(session.getTripId());
            System.out.println("Confirmed completion: " + storedCount + " stored (want 5)");
            if (storedCount != 5) throw new AssertionError("Expected 5 stored, got " + storedCount);

            // Reload session
            TripSession reloaded = load(session.getTripId());
            System.out.println("Reloaded session status: " + reloaded.getStatus() + " (want PARTIAL)");
            if (reloaded.getStatus() != SessionStatus.PARTIAL) throw new AssertionError("Expected PARTIAL status");

            // Verify SQLite state of excluded card
            try (Connection c = Db.open();
                 PreparedStatement ps = c.prepareStatement("SELECT status FROM card_instances WHERE id = ?")) {
                ps.setString(1, excludedCardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String st = rs.getString(1);
                        System.out.println("Excluded card status in DB: " + st + " (want UNSTORED_FLAGGED)");
                        if (!st.equals("UNSTORED_FLAGGED")) throw new AssertionError("Expected UNSTORED_FLAGGED");
                    }
                }
            }

            // Verify inventory_exceptions table
            try (Connection c = Db.open();
                 PreparedStatement ps = c.prepareStatement("SELECT reason_code, notes FROM inventory_exceptions WHERE card_instance_id = ?")) {
                ps.setString(1, excludedCardId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Exception recorded: " + rs.getString(1) + " - " + rs.getString(2));
                    } else {
                        throw new AssertionError("Exception not recorded in DB");
                    }
                }
            }

            // Verify that flagged card blocks rerouting (clusterUnstored returns 0)
            List<tutorengine.storage.StorageScaffolding.StorageBatch> unstoredBatches = tutorengine.storage.StorageScaffolding.clusterUnstored();
            System.out.println("Unstored batches after partial trip: " + unstoredBatches.size() + " (want 0 - flagged card blocked from rerouting)");
            if (!unstoredBatches.isEmpty()) throw new AssertionError("Expected 0 unstored batches (flagged card should not route)");

            // 5. Test plain text slip rendering
            String slip = renderPlainSlip(reloaded);
            System.out.println("\nGenerated Slip Preview:\n" + slip);
            if (!slip.contains("TUTORENGINE SHELF TRIP SLIP") || !slip.contains("EXCLUDED: BOX_FULL")) {
                throw new AssertionError("Slip rendering missing required sections");
            }

            Files.deleteIfExists(tmp);
            System.out.println("✓ TripSession verification passed!");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
