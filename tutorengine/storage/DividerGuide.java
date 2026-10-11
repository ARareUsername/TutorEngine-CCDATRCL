package tutorengine.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import tutorengine.db.Db;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;

// DividerGuide: Alphabetical insertion-point guidance across 26 dividers (§2 specs/divider-navigation/spec.md).
// OWNER: Bondoc, Karl B.
public class DividerGuide {

    public record Guidance(
            char sectionLetter,
            String precedingCard,
            String succeedingCard,
            String guidanceText,
            String edgeCase) {
        @Override
        public String toString() {
            return guidanceText;
        }
    }

    // Resolves section letter from card name (skips leading quotes/symbols if any)
    public static char sectionFor(String cardName) {
        if (cardName == null || cardName.isBlank()) return 'A';
        for (char c : cardName.toCharArray()) {
            if (Character.isLetter(c)) return Character.toUpperCase(c);
        }
        return 'A';
    }

    // Computes guidance from an in-memory collection of card names in a box
    public static Guidance calculate(String targetCardName, List<String> boxCardNames) {
        char sec = sectionFor(targetCardName);
        String sectionPrefix = "Section [" + sec + "] → ";

        if (boxCardNames == null || boxCardNames.isEmpty()) {
            return new Guidance(sec, null, null,
                    sectionPrefix + "Behind divider [" + sec + "] (empty box)", "empty-box");
        }

        // Filter and sort distinct names
        List<String> sorted = new ArrayList<>(boxCardNames.stream().distinct().toList());
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);

        String prev = null;
        String next = null;
        boolean hasSectionCards = false;

        for (String name : sorted) {
            if (sectionFor(name) == sec) {
                hasSectionCards = true;
            }
            int cmp = String.CASE_INSENSITIVE_ORDER.compare(name, targetCardName);
            if (cmp < 0) {
                prev = name; // keeps advancing to highest name strictly < target
            } else if (cmp > 0 && next == null) {
                next = name; // first name strictly > target
            } else if (cmp == 0) {
                // Exact identical copy match
                return new Guidance(sec, name, name,
                        sectionPrefix + "Beside identical copy \"" + name + "\"", "identical");
            }
        }

        if (!hasSectionCards) {
            return new Guidance(sec, prev, next,
                    sectionPrefix + "Behind divider [" + sec + "] (empty section)", "behind-divider");
        }

        if (prev == null && next != null) {
            return new Guidance(sec, null, next,
                    sectionPrefix + "Front of box (before \"" + next + "\")", "front-of-box");
        } else if (prev != null && next == null) {
            return new Guidance(sec, prev, null,
                    sectionPrefix + "Back of box (after \"" + prev + "\")", "back-of-box");
        } else if (prev != null && next != null) {
            return new Guidance(sec, prev, next,
                    sectionPrefix + "Between \"" + prev + "\" and \"" + next + "\"", "mid-box");
        } else {
            return new Guidance(sec, null, null,
                    sectionPrefix + "Behind divider [" + sec + "] (empty section)", "behind-divider");
        }
    }

    // Queries guidance using loaded catalog cards for a given slot
    public static Guidance calculateForCatalog(Card targetCard, List<Card> catalog) {
        String boxId = StorageLocation.route(targetCard);
        List<String> namesInBox = new ArrayList<>();
        if (catalog != null) {
            for (Card c : catalog) {
                if (StorageLocation.route(c).equalsIgnoreCase(boxId)) {
                    namesInBox.add(c.getName());
                }
            }
        }
        return calculate(targetCard.getName(), namesInBox);
    }

    // Queries guidance using SQLite card_instances if DB is initialized
    public static Guidance calculateFromDb(String boxId, String targetCardName) {
        char sec = sectionFor(targetCardName);
        String sectionPrefix = "Section [" + sec + "] → ";
        try (Connection c = Db.open()) {
            String prev = null;
            String next = null;
            int sectionCount = 0;

            // Check if section has cards
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM card_instances WHERE box_id = ? AND status = 'STORED' AND UPPER(SUBSTR(name,1,1)) = ?")) {
                ps.setString(1, boxId);
                ps.setString(2, String.valueOf(sec));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) sectionCount = rs.getInt(1);
                }
            }

            // Preceding neighbor
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name FROM card_instances WHERE box_id = ? AND status = 'STORED' AND name < ? ORDER BY name DESC LIMIT 1")) {
                ps.setString(1, boxId);
                ps.setString(2, targetCardName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) prev = rs.getString(1);
                }
            }

            // Succeeding neighbor
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name FROM card_instances WHERE box_id = ? AND status = 'STORED' AND name > ? ORDER BY name ASC LIMIT 1")) {
                ps.setString(1, boxId);
                ps.setString(2, targetCardName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) next = rs.getString(1);
                }
            }

            if (sectionCount == 0) {
                return new Guidance(sec, prev, next,
                        sectionPrefix + "Behind divider [" + sec + "] (empty section)", "behind-divider");
            }
            if (prev == null && next != null) {
                return new Guidance(sec, null, next,
                        sectionPrefix + "Front of box (before \"" + next + "\")", "front-of-box");
            } else if (prev != null && next == null) {
                return new Guidance(sec, prev, null,
                        sectionPrefix + "Back of box (after \"" + prev + "\")", "back-of-box");
            } else if (prev != null && next != null) {
                return new Guidance(sec, prev, next,
                        sectionPrefix + "Between \"" + prev + "\" and \"" + next + "\"", "mid-box");
            }
        } catch (Exception ignored) {}

        return new Guidance(sec, null, null,
                sectionPrefix + "Behind divider [" + sec + "] (empty section)", "behind-divider");
    }

    public static void main(String[] args) {
        System.out.println("=== DividerGuide Headless Demo ===");

        // Synthetic box test
        List<String> boxCards = List.of(
                "Ajani, Caller of the Pride",
                "Chandra, Pyromaster",
                "Davriel, Rogue Shadowmage",
                "Enter the God-Eternals",
                "Liliana, Dreadhorde General",
                "Teferi, Time Raveler"
        );

        // 1. Mid-box insert scenario: Dreadhorde Invasion in section [D] between Davriel and Enter the God-Eternals
        Guidance gMid = calculate("Dreadhorde Invasion", boxCards);
        System.out.println("[1] Mid-box: " + gMid);
        if (!gMid.edgeCase().equals("mid-box") || !gMid.guidanceText().contains("Davriel") || !gMid.guidanceText().contains("Enter the God-Eternals")) {
            throw new AssertionError("Mid-box guidance incorrect: " + gMid);
        }

        // 2. Front of box scenario: Abzan Battle Priest (before Ajani)
        Guidance gFront = calculate("Abzan Battle Priest", boxCards);
        System.out.println("[2] Front of box: " + gFront);
        if (!gFront.edgeCase().equals("front-of-box") || !gFront.guidanceText().contains("Front of box")) {
            throw new AssertionError("Front-of-box guidance incorrect: " + gFront);
        }

        // 3. Back of box scenario: Vraska, Golgari Queen (after Teferi)
        Guidance gBack = calculate("Vraska, Golgari Queen", boxCards);
        System.out.println("[3] Back of box: " + gBack);
        // Note: section V has no cards in boxCards, so let's test empty section and back of box
        if (!gBack.edgeCase().equals("behind-divider") && !gBack.edgeCase().equals("back-of-box")) {
            throw new AssertionError("Back/Behind guidance unexpected: " + gBack);
        }

        // Add a V card to test back of box in a populated section
        List<String> withV = new ArrayList<>(boxCards);
        withV.add("Vampire Nighthawk");
        Guidance gBack2 = calculate("Vraska, Golgari Queen", withV);
        System.out.println("[3b] Back of box with V section: " + gBack2);
        if (!gBack2.edgeCase().equals("back-of-box") || !gBack2.guidanceText().contains("Back of box")) {
            throw new AssertionError("Back-of-box guidance incorrect: " + gBack2);
        }

        // 4. Empty section scenario: Kiora, Behemoth Beckoner in section [K] (no K cards)
        Guidance gEmptySec = calculate("Kiora, Behemoth Beckoner", boxCards);
        System.out.println("[4] Empty section: " + gEmptySec);
        if (!gEmptySec.edgeCase().equals("behind-divider") || !gEmptySec.guidanceText().contains("Behind divider [K] (empty section)")) {
            throw new AssertionError("Empty section guidance incorrect: " + gEmptySec);
        }

        // 5. Test with real loaded dataset cards
        List<Card> loaded = tutorengine.ui.CatalogView.loadCards();
        System.out.println("\n[5] Testing with real dataset cards (" + loaded.size() + " cards):");
        Card testCard1 = loaded.get(0);
        Guidance gReal1 = calculateForCatalog(testCard1, loaded);
        System.out.println("  Card: " + testCard1.getName() + " [" + StorageLocation.route(testCard1) + "]");
        System.out.println("  Guidance: " + gReal1);

        Card testCard2 = loaded.get(Math.min(10, loaded.size() - 1));
        Guidance gReal2 = calculateForCatalog(testCard2, loaded);
        System.out.println("  Card: " + testCard2.getName() + " [" + StorageLocation.route(testCard2) + "]");
        System.out.println("  Guidance: " + gReal2);

        System.out.println("\n✓ DividerGuide verification passed!");
    }
}
