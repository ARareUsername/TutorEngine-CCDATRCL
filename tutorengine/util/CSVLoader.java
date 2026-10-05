package tutorengine.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tutorengine.model.Card;

// OWNER: David — Data & Loader. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: Read Dataset/cards.csv and convert each row into a Card object.
// Every other feature (search, sorting, benchmarks) consumes the list built here,
// so a crash in this file breaks the whole app. The CSV header must stay exactly:
//   sku,name,setName,color,cardType,releaseYear,price,quantity,demandScore,isFoil,boxLocationId
//
// HOW TO IMPLEMENT:
//   1. In load(path): read all lines with Files.readAllLines(Path.of(path)).
//      Line 0 is the header — start the loop at line 1 and skip blank lines.
//   2. Split each line on commas with split(",", -1) so trailing empty fields survive.
//      Card names containing commas are wrapped in double quotes in the file, so
//      strip one surrounding pair of quotes from the name field when present.
//   3. Convert and validate every field before building the Card:
//        double price = Double.parseDouble(p[6].trim());
//        int qty = Integer.parseInt(p[7].trim());
//        int demand = Integer.parseInt(p[8].trim());
//        boolean foil = Boolean.parseBoolean(p[9].trim());
//      Reject the row when price < 0, quantity < 0, or demand is outside 1-100.
//   4. A rejected or unparsable row prints "SKIP line N: <reason>" where N is the
//      real file line number (loop index + 1), then the loop continues. Wrap the
//      per-row parsing in try/catch (NumberFormatException) so one malformed
//      number is skipped, never thrown — a single bad row must never abort the load.
//   5. A good row becomes new Card(sku, name, setName, color, cardType, releaseYear,
//      price, qty, demand, foil, boxId) and is appended to the result list.
//   6. In main(): call load("Dataset/cards.csv"), print the list size, then print
//      the first 3 cards via toString(). Keep one deliberately broken row in the
//      CSV (or a copy) to demonstrate the SKIP path in the demo.
//   7. Report: describe every CSV column (Template C) and the data origin
//      (Scryfall Default Cards 2026-10-04, converted 2026-10-05) in section S2.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.util.CSVLoader
//   Expected: "Loaded 60 cards (need >= 50).", 3 sample card lines, and one
//   "SKIP line N: ..." line for the planted bad row. Until load() is implemented
//   the stub prints a TODO line and "Loaded 0 cards".
//
// DEFENSE: why an ArrayList stages the data (positional access the sorts rely on).
// SPEC: S6 dataset, S11 load/generate.
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
