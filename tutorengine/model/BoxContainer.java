package tutorengine.model;

// Entity model for box_containers table (tutorengine_implementation_specification.md §2.3)
public class BoxContainer {
    private final String boxId;
    private final String displayLabel;
    private final String shelfUnit;
    private final String shelfTier;
    private final int columnIndex;
    private final int stackPosition;
    private int maxCapacity;
    private int currentCardCount;
    private String sortPolicy;

    public BoxContainer(String boxId, String displayLabel, String shelfUnit, String shelfTier,
                        int columnIndex, int stackPosition, int maxCapacity,
                        int currentCardCount, String sortPolicy) {
        this.boxId = boxId;
        this.displayLabel = displayLabel;
        this.shelfUnit = shelfUnit;
        this.shelfTier = shelfTier;
        this.columnIndex = columnIndex;
        this.stackPosition = stackPosition;
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : 1000;
        this.currentCardCount = currentCardCount;
        this.sortPolicy = sortPolicy != null ? sortPolicy : "NAME_ASC";
    }

    public String getBoxId() { return boxId; }
    public String getDisplayLabel() { return displayLabel; }
    public String getShelfUnit() { return shelfUnit; }
    public String getShelfTier() { return shelfTier; }
    public int getColumnIndex() { return columnIndex; }
    public int getStackPosition() { return stackPosition; }
    public int getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }
    public int getCurrentCardCount() { return currentCardCount; }
    public void setCurrentCardCount(int currentCardCount) { this.currentCardCount = currentCardCount; }
    public String getSortPolicy() { return sortPolicy; }
    public void setSortPolicy(String sortPolicy) { this.sortPolicy = sortPolicy; }

    public double fillPercentage() {
        if (maxCapacity <= 0) return 0.0;
        return (double) currentCardCount / maxCapacity * 100.0;
    }

    public boolean isFull() {
        return currentCardCount >= maxCapacity;
    }

    @Override
    public String toString() {
        return displayLabel + " [" + boxId + "] (" + currentCardCount + "/" + maxCapacity + " cards, " + shelfUnit + " " + shelfTier + ")";
    }

    public static void main(String[] args) {
        BoxContainer bc = new BoxContainer("FRA-1", "White cabinet top: T cluster", "White Cabinet", "TOP", 0, 0, 1000, 240, "NAME_ASC");
        System.out.println("BoxContainer: " + bc + " fill=" + String.format("%.1f%%", bc.fillPercentage()));
        System.out.println("BoxContainer OK.");
    }
}
