package tutorengine.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tutorengine.model.Card;

// Hi David! This file is yours. Please don't let anyone else edit it, and
// please don't edit anyone else's — if you need a change somewhere else,
// leave a comment on that person's pull request instead.
//
// What this does: our card list lives in Dataset/cards.csv (also yours), and
// this class reads that file and turns every row into a Card object. Almost
// everything else in the app — search, sorting, benchmarks — eats the list this
// produces, so if this breaks, everything breaks. Handle it with care.
//
// Your steps:
//   1. Open Dataset/cards.csv. Keep the header row exactly as it is:
//      sku,name,setName,color,cardType,releaseYear,price,quantity,demandScore,isFoil,boxLocationId
//   2. Make sure it holds at least 50 real Magic singles (we have 60 from
//      Scryfall already — verify, don't rebuild). Example row:
//      MTG-OTJ-056,Lightning Helix,Outlaws of Thunder Junction,Multi,Instant,2024,120.00,6,70,false,Bulk Box B
//   3. Fill in load() below: read every line, skip the header and blank lines,
//      split on commas (names containing commas are wrapped in quotes), convert
//      the numbers, and reject nonsense (negative price or quantity, demand
//      outside 1-100). A broken row should print "SKIP line N: <why>" and be
//      skipped — one bad row must never crash the whole load.
//   4. Fill in main() so the demo loads the file and prints the count plus
//      3 sample cards.
//   5. For the report, describe every CSV column (Template C) and where the
//      data came from (spec section S2).
//
// How to check your work:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.util.CSVLoader
//   Right now you'll see a TODO line and "Loaded 0 cards" — that's normal, the
//   loader is still a stub. When you're done you should see "Loaded 60 cards",
//   3 sample card lines, and (if you plant a broken row to test) a SKIP message
//   with the count still printing fine.
//
// For the defense, be ready to explain why the cards are staged in an ArrayList:
// instant lookup by position, which is exactly what the sorting and benchmark
// code relies on. (Spec: S6 dataset, S11 load/generate.)
public class CSVLoader {
    public static List<Card> load(String path) {
        // TODO David: replace stub below with steps 3a-3e.
        System.out.println("TODO David: parse " + path + " -> List<Card>");
        return new ArrayList<>();
    }

    public static void main(String[] args) {
        // DEMO for presentation: shows count + first 3 cards.
        // TODO David: call load("Dataset/cards.csv") here and print size + 3 samples.
        try {
            List<Card> cards = load("Dataset/cards.csv");
            System.out.println("Loaded " + cards.size() + " cards (need >= 50).");
            System.out.println("OWNER: David.");
            Path p = Path.of("Dataset/cards.csv");
            if (Files.exists(p)) System.out.println("CSV found at Dataset/cards.csv");
            else System.out.println("MISSING Dataset/cards.csv - create it first.");
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }
}
