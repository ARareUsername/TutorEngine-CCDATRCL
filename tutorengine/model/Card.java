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

    public Card(String sku, String name, String setName, String color, String cardType,
                int releaseYear, double price, int quantity, int demandScore, boolean isFoil, String boxLocationId) {
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
    }

    public double calculatePriority() {
        double foilBonus = isFoil ? 10.0 : 0.0;
        return (price * 0.50) + (demandScore * 0.35) - (quantity * 3.0) + foilBonus;
    }

    @Override
    public int compareTo(Card other) {
        return this.name.compareToIgnoreCase(other.name);
    }

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

    @Override
    public String toString() {
        return String.format("%s [%s] | %s | PHP %.2f x%d | Demand %d%s | -> %s | Priority %.2f",
                name, sku, setName, price, quantity, demandScore,
                isFoil ? " FOIL" : "", boxLocationId, calculatePriority());
    }
}
