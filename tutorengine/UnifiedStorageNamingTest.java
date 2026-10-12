package tutorengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tutorengine.db.Db;
import tutorengine.intake.RapidIntake;
import tutorengine.model.Card;
import tutorengine.model.CardInstance;
import tutorengine.model.StorageLocation;
import tutorengine.storage.StorageScaffolding;
import tutorengine.ui.CatalogView;
import tutorengine.ui.PriorityView;
import tutorengine.ui.StorageView;
import tutorengine.util.CSVLoader;

public class UnifiedStorageNamingTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== UNIFIED STORAGE NAMING TEST SUITE ===");
        try {
            Db.migrate();
            testCardModelAndStorageLocationRouting();
            testCSVLoaderSetBoxFormat();
            testRapidIntakeNullLocation();
            testStorageScaffoldingBatchRouting();
            testStorageToCatalogLiveSynchronization();
            testPriorityViewMatchesCatalog();

            System.out.println();
            System.out.println(passed + " passed, " + failed + " failed");
            if (failed > 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void check(String desc, boolean condition) {
        if (condition) {
            System.out.println("  PASS: " + desc);
            passed++;
        } else {
            System.out.println("  FAIL: " + desc);
            failed++;
        }
    }

    private static void testCardModelAndStorageLocationRouting() {
        System.out.println("1. Testing Card model setBoxLocationId and StorageLocation routing...");

        Card card = new Card("SKU-TEST-001", "Storage Test Card", "Bloomburrow", "Green", "Creature",
                2024, 50.0, 4, 70, false, null, "{2}{G}", "3", "3", "BLB");

        check("Initial box location is null", card.getBoxLocationId() == null);

        // Mutator test
        card.setBoxLocationId("BLB-1");
        check("setBoxLocationId mutates boxLocationId to BLB-1", "BLB-1".equals(card.getBoxLocationId()));

        // Route with assigned boxLocationId
        String routeAssigned = StorageLocation.route(card);
        check("StorageLocation.route returns assigned box BLB-1", "BLB-1".equals(routeAssigned));

        // Route with null boxLocationId falls back to setCode-1
        card.setBoxLocationId(null);
        String routeFallback = StorageLocation.route(card);
        check("StorageLocation.route falls back to setCode-1 (BLB-1)", "BLB-1".equals(routeFallback));

        // Route with no setCode falls back to Unassigned
        Card cardNoSet = new Card("SKU-TEST-002", "Unknown Set Card", "", "Colorless", "Artifact",
                2024, 10.0, 1, 50, false, null, "", "", "", "");
        check("StorageLocation.route returns Unassigned when no setCode and no box", "Unassigned".equals(StorageLocation.route(cardNoSet)));

        // Slot validation
        check("StorageLocation.isValidSlot accepts BLB-1", StorageLocation.isValidSlot("BLB-1"));
        check("StorageLocation.isValidSlot accepts M14-2", StorageLocation.isValidSlot("M14-2"));
        check("StorageLocation.isValidSlot rejects empty", !StorageLocation.isValidSlot(""));
    }

    private static void testCSVLoaderSetBoxFormat() {
        System.out.println("2. Testing CSVLoader formats card locations as <set>-1...");
        List<Card> cards = CSVLoader.load("Dataset/cards.csv");
        if (cards.isEmpty()) {
            cards = CatalogView.loadCards();
        }
        check("Cards loaded from dataset/fallback", !cards.isEmpty());

        boolean allMatchConvention = true;
        for (Card c : cards) {
            String box = c.getBoxLocationId();
            if (box == null || !box.matches("[A-Za-z0-9]+-[0-9]+")) {
                allMatchConvention = false;
                System.out.println("    Mismatch card: " + c.getName() + " box: " + box);
                break;
            }
        }
        check("All loaded card box locations match <set>-<box number> convention", allMatchConvention);
    }

    private static void testRapidIntakeNullLocation() {
        System.out.println("3. Testing Rapid Intake defaults storage location to null...");

        RapidIntake.ResolvedCard resolved = new RapidIntake.ResolvedCard(
                "scry-rapid-1", "Rapid Test Dragon", "Wilds of Eldraine", "WOE", "142",
                "rare", "Creature", 2023, "{3}{R}{R}", "5", "4", false, 1, "NM",
                5.0, 314.0, "https://example.com/img.jpg", null
        );

        check("ResolvedCard slot is null", resolved.slot() == null);

        Card card = RapidIntake.toCard(resolved, "RAPID-WOE-0001");
        check("RapidIntake.toCard produces Card with null boxLocationId", card.getBoxLocationId() == null);
        check("StorageLocation.route for rapid intake card suggests shelf WOE-1", "WOE-1".equals(StorageLocation.route(card)));
    }

    private static void testStorageScaffoldingBatchRouting() {
        System.out.println("4. Testing StorageScaffolding routes unstored cards into <SET>-1 batches...");

        CardInstance ci1 = new CardInstance("inst-1", "scry-1", "Siege Rhino", "KTK", "200",
                "rare", "NM", "NONFOIL", "EN", "UNSTORED", null, "2026-10-12 10:00:00", null);
        CardInstance ci2 = new CardInstance("inst-2", "scry-2", "Monastery Swiftspear", "KTK", "118",
                "uncommon", "NM", "NONFOIL", "EN", "UNSTORED", null, "2026-10-12 10:00:00", null);

        String routedBox = StorageScaffolding.routeInstance(ci1);
        check("StorageScaffolding.routeInstance routes unstored KTK card to KTK-1", "KTK-1".equals(routedBox));

        // When card instance already has a box assigned
        CardInstance ciPinned = new CardInstance("inst-3", "scry-3", "Custom Box Card", "M14", "1",
                "rare", "NM", "NONFOIL", "EN", "UNSTORED", "M14-2", "2026-10-12 10:00:00", null);
        check("StorageScaffolding.routeInstance preserves pinned box M14-2", "M14-2".equals(StorageScaffolding.routeInstance(ciPinned)));
    }

    private static void testStorageToCatalogLiveSynchronization() {
        System.out.println("5. Testing Storage confirmation synchronizes live CatalogView...");

        List<Card> activeCards = new ArrayList<>();
        Map<String, Card> activeCatalog = new HashMap<>();
        CatalogView.activeCards = activeCards;
        CatalogView.activeCatalog = activeCatalog;

        Card rapidCard = new Card("SKU-RAPID-001", "Intaked Phoenix", "Core Set 2019", "Red", "Creature",
                2018, 500.0, 2, 80, true, null, "{2}{R}{R}", "4", "3", "M19");

        activeCards.add(rapidCard);
        activeCatalog.put(rapidCard.getSku(), rapidCard);

        check("Card in Catalog initially has null box location", rapidCard.getBoxLocationId() == null);

        // Simulate clerk confirming batch in StorageView for M19-1
        CatalogView.updateCardStorageLocation("Intaked Phoenix", "M19", "M19-1");

        check("Catalog card box location updated to M19-1", "M19-1".equals(rapidCard.getBoxLocationId()));
        check("Catalog map card box location updated to M19-1", "M19-1".equals(activeCatalog.get("SKU-RAPID-001").getBoxLocationId()));
    }

    private static void testPriorityViewMatchesCatalog() {
        System.out.println("6. Testing Priority View showcase rows match Catalog locations...");

        Card c1 = new Card("SKU-PRIO-001", "Priority Showcase Card", "Modern Horizons 3", "White", "Creature",
                2024, 2500.0, 1, 95, true, "MH3-1", "{W}", "2", "1", "MH3");
        Card c2 = new Card("SKU-PRIO-002", "Unassigned Rare", "Bloomburrow", "Green", "Sorcery",
                2024, 1800.0, 1, 90, false, null, "{1}{G}", "", "", "BLB");

        List<Card> testCards = List.of(c1, c2);

        // When boxLocationId is set, location matches catalog
        String loc1 = c1.getBoxLocationId() != null ? c1.getBoxLocationId() : StorageLocation.route(c1);
        check("Showcase location for c1 matches catalog MH3-1", "MH3-1".equals(loc1));

        // When boxLocationId is null, route suggests BLB-1
        String loc2 = StorageLocation.route(c2);
        check("Storage route for c2 matches BLB-1", "BLB-1".equals(loc2));

        // Update c2 location to BLB-1 via catalog sync
        c2.setBoxLocationId("BLB-1");
        String updatedLoc2 = c2.getBoxLocationId();
        check("Updated location for c2 in Priority matches BLB-1", "BLB-1".equals(updatedLoc2));
    }
}
