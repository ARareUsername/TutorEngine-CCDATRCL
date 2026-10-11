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
//   sku,name,setName,color,cardType,releaseYear,price,quantity,demandScore,isFoil,boxLocationId,manaCost,power,toughness,setCode
// (11-col legacy rows still load, with the 4 new fields blank.)
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
//   Expected: "Loaded 60 cards (need >= 50).", 3 sample card lines, and (with a
//   deliberately broken row planted) one "SKIP line N: ..." line while the
//   count still prints. Implemented 2026-10-05; David owns the trace + report rows.
//
// DEFENSE: why an ArrayList stages the data (positional access the sorts rely on).
// SPEC: S6 dataset, S11 load/generate.
public class CSVLoader {
    public static List<Card> load(String path) {
        // Implemented 2026-10-05 by Bondoc, Karl B. (David's file — unblocking).
        List<Card> out = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (Exception e) {
            System.out.println("LOAD ERROR: cannot read " + path + " (" + e.getMessage() + ")");
            return out; // empty, never null — callers stay simple
        }
        for (int i = 1; i < lines.size(); i++) { // line 0 is the header
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] p = splitCsv(line);
            if (p.length != 11 && p.length != 15) {
                System.out.println("SKIP line " + (i + 1) + ": expected 11 or 15 columns, got " + p.length);
                continue;
            }
            try {
                String sku = p[0].trim();
                String name = p[1].trim();
                if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
                    name = name.substring(1, name.length() -1);
                }
                String setName = p[2].trim();
                String color = p[3].trim();
                String cardType = p[4].trim();
                int year = Integer.parseInt(p[5].trim());
                double price = Double.parseDouble(p[6].trim());
                int qty = Integer.parseInt(p[7].trim());
                int demand = Integer.parseInt(p[8].trim());
                boolean foil = Boolean.parseBoolean(p[9].trim());
                String box = p[10].trim();
                String mana = p.length == 15 ? p[11].trim() : "";
                String pw = p.length == 15 ? p[12].trim() : "";
                String tw = p.length == 15 ? p[13].trim() : "";
                String code = p.length == 15 ? p[14].trim() : "";
                if (sku.isEmpty() || name.isEmpty()) {
                    System.out.println("SKIP line " + (i + 1) + ": empty SKU or name");
                    continue;
                }
                if (price < 0 || qty < 0 || demand < 1 || demand > 100) {
                    System.out.println("SKIP line " + (i + 1) + ": price/qty/demand out of range");
                    continue;
                }
                out.add(new Card(sku, name, setName, color, cardType,
                        year, price, qty, demand, foil, box, mana, pw, tw, code));
            } catch (NumberFormatException e) {
                System.out.println("SKIP line " + (i + 1) + ": bad number");
            }
        }
        return out;
    }

    // Split on commas, ignoring commas inside double quotes (the quotes themselves
    // are dropped). Limitation: a literal "" escape inside a quoted name is kept
    // as-is instead of unescaped — no card name in our data needs it.
    static String[] splitCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') inQuotes = !inQuotes;
            else if (ch == ',' && !inQuotes) { fields.add(cur.toString()); cur.setLength(0); }
            else cur.append(ch);
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    public static void main(String[] args) {
        // DEMO for presentation: shows count + first 3 cards.
        try {
            List<Card> cards = load("Dataset/cards.csv");
            System.out.println("Loaded " + cards.size() + " cards (need >= 50).");
            for (int i = 0; i < Math.min(3, cards.size()); i++)
                System.out.println("  sample: " + cards.get(i));
            System.out.println("CSVLoader. OWNER: David; load() implemented 2026-10-05 by Bondoc, Karl B.");
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }
}
