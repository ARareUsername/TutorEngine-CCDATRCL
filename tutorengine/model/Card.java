package tutorengine.model;

// Hi! This is the heart of the app — one Card object is one Magic single on
// the shelf (its code, name, set, price, stock, demand, foil finish, and which
// box it lives in). Everybody's code touches Cards, so think twice before
// changing these fields: renames ripple into the CSV, the search, and sorting.
// There is no main() here on purpose — Cards get exercised through the demos.
// To check this file, run: mvn -q exec:java -Dexec.mainClass=tutorengine.Main
// and look for a line like:
//   Mana Drain [MTG-OTJ-055] | Outlaws of Thunder Junction | PHP 2600.00 x1 | ...
// The bracketed code is the SKU, PHP is the price, and the trailing number is
// calculatePriority(). If a priority looks wrong anywhere, this formula is the
// first suspect.
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
