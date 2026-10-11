package tutorengine.model;

// SHARED RECORD — Card. Every member's code touches this class: do not rename
// fields without checking the CSV, the search, the sorts, and the report first.
//
// PURPOSE: One object per Magic single on the shelf: SKU code, name, set, color,
// type, year, price, stock count, demand 1-100, foil flag, and storage-box id.
// calculatePriority() scores listing urgency from price, demand, stock, and foil.
//
// HOW TO TEST: no main() here by design; Cards are exercised through the demos.
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.Main
//   Expected: a line like
//     Mana Drain [MTG-OTJ-055] | Outlaws of Thunder Junction | PHP 2600.00 x1 | ...
//   Bracketed code = SKU, PHP = price, trailing number = the priority score.
//   A wrong-looking priority anywhere implicates the formula below first.
public class Card implements Comparable<Card> {
    private String sku;
    private String name;
    private String setName;
    private String color;
    private String cardType;
    private int releaseYear;
    private double price;
    private int quantity;
    private int demandScore;
    private boolean isFoil;
    private String boxLocationId;
    private String manaCost;    // e.g. "{2}{G}"; "" for lands
    private String power;       // creatures only; "" otherwise
    private String toughness;   // creatures only; "" otherwise
    private String setCode;     // expansion code, e.g. "BLB" (maps to the set symbol)
    private String imageUrl;    // Scryfall thumbnail image URL
    private String collectorNumber; // e.g. "097", "373", "142a"; "" when unknown
    private int versionSeq;         // 1, 2, 3… within a printing group (name+set)
    private String versionLabel;    // "Standard", "Borderless", "Extended Art", "Promo"

    public Card(String sku, String name, String setName, String color, String cardType,
                int releaseYear, double price, int quantity, int demandScore, boolean isFoil, String boxLocationId) {
        this(sku, name, setName, color, cardType, releaseYear, price, quantity,
             demandScore, isFoil, boxLocationId, "", "", "", "", "");
    }

    public Card(String sku, String name, String setName, String color, String cardType,
                int releaseYear, double price, int quantity, int demandScore, boolean isFoil, String boxLocationId,
                String manaCost, String power, String toughness, String setCode) {
        this(sku, name, setName, color, cardType, releaseYear, price, quantity,
             demandScore, isFoil, boxLocationId, manaCost, power, toughness, setCode, "");
    }

    public Card(String sku, String name, String setName, String color, String cardType,
                int releaseYear, double price, int quantity, int demandScore, boolean isFoil, String boxLocationId,
                String manaCost, String power, String toughness, String setCode, String imageUrl) {
        this(sku, name, setName, color, cardType, releaseYear, price, quantity,
             demandScore, isFoil, boxLocationId, manaCost, power, toughness, setCode, imageUrl, "", 0, "");
    }

    public Card(String sku, String name, String setName, String color, String cardType,
                int releaseYear, double price, int quantity, int demandScore, boolean isFoil, String boxLocationId,
                String manaCost, String power, String toughness, String setCode, String imageUrl,
                String collectorNumber, int versionSeq, String versionLabel) {
        this.sku = sku;
        this.name = name;
        this.setName = setName;
        this.color = color;
        this.cardType = cardType;
        this.releaseYear = releaseYear;
        this.price = price;
        this.quantity = quantity;
        this.demandScore = demandScore;
        this.isFoil = isFoil;
        this.boxLocationId = boxLocationId;
        this.manaCost = manaCost;
        this.power = power;
        this.toughness = toughness;
        this.setCode = setCode;
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.collectorNumber = collectorNumber != null ? collectorNumber : "";
        this.versionSeq = versionSeq;
        this.versionLabel = versionLabel != null ? versionLabel : "";
    }

    public double calculatePriorityWithWeights(double priceWeight, double demandWeight, double qtyPenalty, double foilBonus) {
        return (price * priceWeight) + (demandScore * demandWeight) - (quantity * qtyPenalty) + (isFoil ? foilBonus : 0.0);
    }

    public double calculatePriority() {
        return calculatePriorityWithWeights(0.50, 0.35, 3.0, 10.0);
    }

    @Override
    public int compareTo(Card other) {
        return this.name.compareToIgnoreCase(other.name);
    }

    // Physical filing order: boxes are sorted by expansion (year, then set code),
    // then name, then collector number (version priority within a printing group).
    public static final java.util.Comparator<Card> BY_EXPANSION =
            java.util.Comparator.comparingInt(Card::getReleaseYear)
                    .thenComparing(Card::getSetCode)
                    .thenComparing(Card::getName, String::compareToIgnoreCase)
                    .thenComparing(Card::getCollectorNumber,
                            java.util.Comparator.comparingInt(c -> {
                                try { return Integer.parseInt(c.replaceAll("[^0-9].*$", "")); }
                                catch (Exception e) { return Integer.MAX_VALUE; }
                            }));

    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getSetName() { return setName; }
    public String getColor() { return color; }
    public String getCardType() { return cardType; }
    public int getReleaseYear() { return releaseYear; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public int getDemandScore() { return demandScore; }
    public boolean isFoil() { return isFoil; }
    public String getBoxLocationId() { return boxLocationId; }
    public String getManaCost() { return manaCost; }
    public String getPower() { return power; }
    public String getToughness() { return toughness; }
    public String getSetCode() { return setCode; }
    public String getCollectorNumber() { return collectorNumber; }
    public int getVersionSeq() { return versionSeq; }
    public String getVersionLabel() { return versionLabel; }
    public String getImageUrl() {
        if (imageUrl != null && !imageUrl.isEmpty()) return imageUrl;
        return "https://api.scryfall.com/cards/named?exact="
                + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8)
                + "&format=image&version=small"
                + (setCode != null && !setCode.isEmpty() ? "&set=" + setCode.toLowerCase() : "");
    }

    @Override
    public String toString() {
        return String.format("%s [%s] | %s | PHP %.2f x%d | Demand %d%s | -> %s | Priority %.2f",
                name, sku, setName, price, quantity, demandScore,
                isFoil ? " FOIL" : "", boxLocationId, calculatePriority());
    }
}
