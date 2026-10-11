package tutorengine.priority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import tutorengine.model.Card;

// OWNER: Dimazana — Catalog & Priority. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: Answers "which card should the associate handle next?". Every card
// carries a priority score and this queue always hands out the highest-scoring
// card first.
//
// SCORE FORMULA (keep it, and justify it in report section S4 — it must combine
// at least two attributes):
//   (price x 0.50) + (demandScore x 0.35) - (quantity x 3.0) + (foil ? 10 : 0)
// Expensive, in-demand cards rank up; overstocked bulk sinks; foil adds a flat
// bonus for curl protection.
//
// HOW TO IMPLEMENT:
//   1. Keep offer(), peek(), and poll() as written. peek() looks without removing;
//      poll() removes and returns the top card.
//   2. Extend main() with 5 real cards: one obvious star (Mana Drain, price 2600,
//      demand 98, quantity 1) and one obvious bulk card (Llanowar Elves, price 25,
//      quantity 48). Print name + score at every step (call calculatePriority()).
//   3. Script: peek() must name Mana Drain; poll() removes it; the next peek()
//      must name the runner-up.
//   4. Then offer an even hotter card LAST and show peek() switching to it at once.
//      A newcomer jumping the line is the proof the queue truly prioritizes.
//   5. Rehearse reweighting: change one formula weight (e.g. double the foil bonus),
//      rebuild the queue by re-offering the cards, and show the new order — the
//      instructor may request exactly this live.
//   6. Trace packet (1 page): 3 arrivals bubbling upward step by step, then 1
//      removal sinking downward.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.priority.PriorityDesk
//   Expected: peek names Mana Drain first; poll removes it; next peek is the
//   runner-up; the late hot card jumps the line; every printed score matches a
//   hand calculation of the formula.
//
// DEFENSE: walk one card bubbling upward on paper; adding/removing costs
// logarithmic steps, peeking is instant.
// SPEC: S7 heap, S8 priority calc, S11 heap add/remove, Template H trace, test T10.
public class PriorityDesk {

    //PriorityQueue is being used as a heap.
    //The card with the highest priority score is placed first.
    private final PriorityQueue<Card> heap;

    public PriorityDesk() {
        this.heap = new PriorityQueue<>(
                Comparator.comparingDouble(Card::calculatePriority).reversed()
        );
    }

    // Constructor allowing custom comparator for live reweighting rehearsals
    public PriorityDesk(Comparator<Card> comparator) {
        this.heap = new PriorityQueue<>(comparator);
    }

    public void offer(Card c) {
        heap.offer(c);
    }

    public Card peek() {
        return heap.peek();
    }

    public Card poll() {
        return heap.poll();
    }

    public int size() {
        return heap.size();
    }

    // Prints the label, card name, and priority score.
    private static void printCard(String label, Card card) {
        if (card == null) {
            System.out.println(label + ": none");
            return;
        }

        System.out.printf("%s: %s | Score = %.2f%n",
                label,
                card.getName(),
                card.calculatePriority());
    }
 
    //Reweighted priority calculation for Step 5 rehearsal.
    //Doubles foil bonus from 10 to 20.
    private static double calculateReweightedPriority(Card card, double foilBonus) {
        return (card.getPrice() * 0.50)
                + (card.getDemandScore() * 0.35)
                - (card.getQuantity() * 3.0)
                + (card.isFoil() ? foilBonus : 0.0);
    }

    //Helper to print contents without altering the original queue.
    public static void printDeskContents(String label, PriorityDesk desk) {
        System.out.println(label);
        PriorityQueue<Card> copy = new PriorityQueue<>(desk.heap);
        while (!copy.isEmpty()) {
            Card card = copy.poll();
            System.out.printf("  - %-25s | Score = %.2f%n", card.getName(), card.calculatePriority());
        }
    }

    public static void main(String[] args) {

        // TODO Dimazana: 5-card script from steps 1-3. Print name + priority each step.
        System.out.println("PriorityDesk. OWNER: Dimazana. (implement 5-card demo)");
        System.out.println("========================================");
        System.out.println("        PRIORITY DESK DEMONSTRATION");
        System.out.println("========================================");

        // Define 5 real cards
        Card manaDrain = new Card(
                "CUSTOM-001", "Mana Drain", "Commander Masters", "Blue", "Instant",
                2023, 2600.00, 1, 98, false, "High Value Box"
        );

        Card llanowarElves = new Card(
                "CUSTOM-002", "Llanowar Elves", "Dominaria United", "Green", "Creature",
                2022, 25.00, 48, 70, false, "Bulk Box C (Green/Colorless)"
        );

        Card mysticalTutor = new Card(
                "MTG-DMR-421", "Mystical Tutor", "Dominaria Remastered", "Blue", "Instant",
                2023, 16.56, 42, 100, true, "Bulk Box A (White/Blue)"
        );

        Card birdsOfParadise = new Card(
                "MTG-SLD-176", "Birds of Paradise", "Secret Lair Drop", "Green", "Creature",
                2021, 13.69, 17, 100, true, "Bulk Box C (Green/Colorless)"
        );

        Card narset = new Card(
                "MTG-SLD-1141", "Narset, Parter of Veils", "Secret Lair Drop", "Blue", "Planeswalker",
                2022, 19.03, 9, 98, true, "Bulk Box A (White/Blue)"
        );

        // Late hotter card
        Card blackLotus = new Card(
                "CUSTOM-003", "Black Lotus", "Collector Demo", "Colorless", "Artifact",
                1993, 5000.00, 1, 100, true, "High Value Box"
        );

        List<Card> cards = new ArrayList<>();
        cards.add(manaDrain);
        cards.add(llanowarElves);
        cards.add(mysticalTutor);
        cards.add(birdsOfParadise);
        cards.add(narset);

        // STEP 1: Add five cards
        System.out.println("\nSTEP 1: Offer initial 5 cards");
        PriorityDesk desk = new PriorityDesk();

        for (Card card : cards) {
            desk.offer(card);
            printCard("Added", card);
        }

        System.out.println("\nCalculated Priority Scores:");
        for (Card card : cards) {
            printCard("Card", card);
        }

        // STEP 2: Peek top card (Mana Drain)
        System.out.println("\nSTEP 2: Peek top card");
        printCard("Peek", desk.peek());

        // STEP 3: Poll removes Mana Drain, check runner-up
        System.out.println("\nSTEP 3: Poll top card");
        Card removed = desk.poll();
        printCard("Removed", removed);
        printCard("Next peek (Runner-up)", desk.peek());

        // STEP 4: Add late hot card (Black Lotus)
        System.out.println("\nSTEP 4: Add late hot card (Line-Jumping Test)");
        desk.offer(blackLotus);
        printCard("Late card added", blackLotus);
        printCard("New peek (Hot newcomer)", desk.peek());

        // STEP 5: Rehearse Reweighting (Foil Bonus changed from 10 to 20)
        System.out.println("\nSTEP 5: Reweight formula (Foil Bonus: 10 -> 20)");

        Comparator<Card> reweightedComp = Comparator.comparingDouble(
                (Card c) -> calculateReweightedPriority(c, 20.0)
        ).reversed();

        PriorityDesk reweightedDesk = new PriorityDesk(reweightedComp);

        for (Card c : cards) {
            reweightedDesk.offer(c);
        }
        reweightedDesk.offer(blackLotus);

        System.out.println("Queue Order after Reweighting:");
        while (reweightedDesk.size() > 0) {
            Card c = reweightedDesk.poll();
            System.out.printf("  - %-25s | New Score = %.2f%n",
                    c.getName(),
                    calculateReweightedPriority(c, 20.0));
        }

        System.out.println("\n========================================");
        System.out.println("              DEMO COMPLETE");
        System.out.println("========================================");
    }
}
