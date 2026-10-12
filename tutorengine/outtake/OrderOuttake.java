package tutorengine.outtake;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tutorengine.db.Db;
import tutorengine.intake.RapidIntake;
import tutorengine.intake.RapidIntake.ParsedShortcut;
import tutorengine.intake.RapidIntake.ResolvedCard;
import tutorengine.model.Card;
import tutorengine.model.StorageLocation;
import tutorengine.storage.StorageScaffolding;
import tutorengine.storage.StorageScaffolding.NeighborBreadcrumb;
import tutorengine.ui.CatalogView;

// OrderOuttake: Outtake / Checkout Order Fulfillment, CSV Order Import, Rapid Single-Card
// Shortcut Entry, Physical Location Resolution, Pick Path Optimization, and Inventory Checkout.
// Pure data methods are toolkit-free (headless testable via main()).
public class OrderOuttake {

    public static class OrderItem {
        private String sku;
        private String name;
        private String setCode;
        private String collectorNumber;
        private int quantity;
        private double price;
        private String boxId;
        private String shelfUnit;
        private String shelfTier;
        private int availableStock;
        private boolean sufficientStock;
        private boolean picked = false;
        private String pickedReceiptId = null;
        private List<String> checkedOutInstanceIds = new ArrayList<>();

        public OrderItem(String sku, String name, String setCode, String collectorNumber,
                         int quantity, double price, String boxId, String shelfUnit,
                         String shelfTier, int availableStock) {
            this.sku = sku != null ? sku : "";
            this.name = name != null ? name : "";
            this.setCode = setCode != null ? setCode.toUpperCase() : "";
            this.collectorNumber = collectorNumber != null ? collectorNumber : "";
            this.quantity = Math.max(1, quantity);
            this.price = Math.max(0.0, price);
            this.boxId = boxId != null ? boxId : "Unassigned";
            this.shelfUnit = shelfUnit != null ? shelfUnit : "Shelf Unit A";
            this.shelfTier = shelfTier != null ? shelfTier : "MID";
            this.availableStock = Math.max(0, availableStock);
            this.sufficientStock = this.availableStock >= this.quantity;
            this.picked = false;
            this.pickedReceiptId = null;
            this.checkedOutInstanceIds = new ArrayList<>();
        }

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSetCode() { return setCode; }
        public void setSetCode(String setCode) { this.setCode = setCode; }
        public String getCollectorNumber() { return collectorNumber; }
        public void setCollectorNumber(String collectorNumber) { this.collectorNumber = collectorNumber; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) {
            this.quantity = Math.max(1, quantity);
            this.sufficientStock = this.availableStock >= this.quantity;
        }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public String getBoxId() { return boxId; }
        public void setBoxId(String boxId) { this.boxId = boxId; }
        public String getShelfUnit() { return shelfUnit; }
        public void setShelfUnit(String shelfUnit) { this.shelfUnit = shelfUnit; }
        public String getShelfTier() { return shelfTier; }
        public void setShelfTier(String shelfTier) { this.shelfTier = shelfTier; }
        public int getAvailableStock() { return availableStock; }
        public void setAvailableStock(int availableStock) {
            this.availableStock = availableStock;
            this.sufficientStock = this.availableStock >= this.quantity;
        }
        public boolean isSufficientStock() { return sufficientStock; }

        public boolean isPicked() { return picked; }
        public void setPicked(boolean picked) { this.picked = picked; }
        public String getPickedReceiptId() { return pickedReceiptId; }
        public void setPickedReceiptId(String id) { this.pickedReceiptId = id; }
        public List<String> getCheckedOutInstanceIds() { return checkedOutInstanceIds; }
        public void setCheckedOutInstanceIds(List<String> ids) {
            this.checkedOutInstanceIds = (ids != null) ? new ArrayList<>(ids) : new ArrayList<>();
        }

        public double getTotalPrice() { return price * quantity; }

        public String getLocationDisplay() {
            return boxId + " (" + shelfUnit + " " + shelfTier + ")";
        }

        @Override
        public String toString() {
            return name + " [" + setCode + "] x" + quantity + " -> " + getLocationDisplay()
                    + " (Stock: " + availableStock + ", ₱" + String.format("%.2f", getTotalPrice()) + ")"
                    + (picked ? " [PICKED]" : "");
        }
    }

    public static class OuttakeBoxBatch {
        private final String boxId;
        private final String shelfUnit;
        private final String shelfTier;
        private final List<OrderItem> items = new ArrayList<>();

        public OuttakeBoxBatch(String boxId, String shelfUnit, String shelfTier) {
            this.boxId = boxId != null ? boxId : "Unassigned";
            this.shelfUnit = shelfUnit != null ? shelfUnit : "Shelf Unit A";
            this.shelfTier = shelfTier != null ? shelfTier : "MID";
        }

        public String getBoxId() { return boxId; }
        public String getShelfUnit() { return shelfUnit; }
        public String getShelfTier() { return shelfTier; }
        public List<OrderItem> getItems() { return items; }

        public int getTotalCardCount() {
            int count = 0;
            for (OrderItem item : items) count += item.getQuantity();
            return count;
        }

        public int getPickedCardCount() {
            int count = 0;
            for (OrderItem item : items) {
                if (item.isPicked()) count += item.getQuantity();
            }
            return count;
        }

        public int getRemainingCardCount() {
            return getTotalCardCount() - getPickedCardCount();
        }

        public boolean isFullyPicked() {
            if (items.isEmpty()) return false;
            for (OrderItem item : items) {
                if (!item.isPicked()) return false;
            }
            return true;
        }

        public String getCoordinatesDisplay() {
            return shelfUnit + " • " + shelfTier;
        }

        public String getStatusDisplay() {
            int picked = getPickedCardCount();
            int total = getTotalCardCount();
            if (total == 0) return "Empty";
            if (picked == total) return "✓ Picked (" + total + "/" + total + ")";
            if (picked == 0) return "Pending (" + total + " cards)";
            return "Partial (" + picked + "/" + total + ")";
        }

        @Override
        public String toString() {
            return boxId + " (" + getCoordinatesDisplay() + ") - " + getStatusDisplay();
        }
    }

    public static class OrderSummary {
        private final int totalLines;
        private final int totalCards;
        private final double totalAmount;
        private final int distinctBoxes;
        private final boolean allInStock;

        public OrderSummary(int totalLines, int totalCards, double totalAmount, int distinctBoxes, boolean allInStock) {
            this.totalLines = totalLines;
            this.totalCards = totalCards;
            this.totalAmount = totalAmount;
            this.distinctBoxes = distinctBoxes;
            this.allInStock = allInStock;
        }

        public int getTotalLines() { return totalLines; }
        public int getTotalCards() { return totalCards; }
        public double getTotalAmount() { return totalAmount; }
        public int getDistinctBoxes() { return distinctBoxes; }
        public boolean isAllInStock() { return allInStock; }
    }

    public static class CheckoutResult {
        private final boolean success;
        private final String receiptId;
        private final int linesCheckedOut;
        private final int totalCardsCheckedOut;
        private final double totalAmount;
        private final String message;

        public CheckoutResult(boolean success, String receiptId, int linesCheckedOut,
                              int totalCardsCheckedOut, double totalAmount, String message) {
            this.success = success;
            this.receiptId = receiptId;
            this.linesCheckedOut = linesCheckedOut;
            this.totalCardsCheckedOut = totalCardsCheckedOut;
            this.totalAmount = totalAmount;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getReceiptId() { return receiptId; }
        public int getLinesCheckedOut() { return linesCheckedOut; }
        public int getTotalCardsCheckedOut() { return totalCardsCheckedOut; }
        public double getTotalAmount() { return totalAmount; }
        public String getMessage() { return message; }
    }

    public static class LocationInfo {
        public final String boxId;
        public final String shelfUnit;
        public final String shelfTier;

        public LocationInfo(String boxId, String shelfUnit, String shelfTier) {
            this.boxId = boxId != null ? boxId : "Unassigned";
            this.shelfUnit = shelfUnit != null ? shelfUnit : "Shelf Unit A";
            this.shelfTier = shelfTier != null ? shelfTier : "MID";
        }
    }

    // Resolves physical storage location (boxId, shelfUnit, shelfTier) from database / catalog
    public static LocationInfo resolvePhysicalLocation(String cardName, String setCode) {
        String boxId = null;
        String unit = null;
        String tier = null;

        try {
            Db.migrate();
            try (Connection c = Db.open()) {
                // 1. Check card_instances with STORED status
                if (cardName != null && !cardName.isBlank()) {
                    String query = (setCode != null && !setCode.isBlank())
                            ? "SELECT box_id FROM card_instances WHERE name = ? AND set_code = ? AND status = 'STORED' AND box_id IS NOT NULL LIMIT 1"
                            : "SELECT box_id FROM card_instances WHERE name = ? AND status = 'STORED' AND box_id IS NOT NULL LIMIT 1";
                    try (PreparedStatement ps = c.prepareStatement(query)) {
                        ps.setString(1, cardName.trim());
                        if (setCode != null && !setCode.isBlank()) ps.setString(2, setCode.trim().toUpperCase());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) boxId = rs.getString(1);
                        }
                    }
                }

                // 2. Fall back to CatalogView in-memory cards
                if (boxId == null && CatalogView.activeCards != null) {
                    for (Card card : CatalogView.activeCards) {
                        if (card.getName().equalsIgnoreCase(cardName.trim())) {
                            if (setCode == null || setCode.isBlank() || card.getSetCode().equalsIgnoreCase(setCode.trim())) {
                                if (card.getBoxLocationId() != null && !card.getBoxLocationId().isBlank()
                                        && !card.getBoxLocationId().equalsIgnoreCase("Unassigned")) {
                                    boxId = card.getBoxLocationId();
                                    break;
                                }
                            }
                        }
                    }
                }

                // 3. Fall back to standard set-box convention
                if (boxId == null || boxId.isBlank()) {
                    if (setCode != null && !setCode.isBlank()) {
                        boxId = setCode.trim().toUpperCase() + "-1";
                    } else {
                        boxId = "Unassigned";
                    }
                }

                // 4. Query box_containers for physical shelf coordinates
                if (!boxId.equalsIgnoreCase("Unassigned")) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT shelf_unit, shelf_tier FROM box_containers WHERE box_id = ?")) {
                        ps.setString(1, boxId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                unit = rs.getString("shelf_unit");
                                tier = rs.getString("shelf_tier");
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (unit == null || unit.isBlank()) {
            unit = (Math.abs(boxId.hashCode() % 2) == 0) ? "Shelf Unit A" : "Shelf Unit B";
        }
        if (tier == null || tier.isBlank()) {
            tier = "MID";
        }

        return new LocationInfo(boxId, unit, tier);
    }

    // Returns available stored stock for card
    public static int checkAvailableStock(String cardName, String setCode) {
        int count = 0;
        try {
            Db.migrate();
            try (Connection c = Db.open()) {
                String query = (setCode != null && !setCode.isBlank())
                        ? "SELECT COUNT(*) FROM card_instances WHERE name = ? AND set_code = ? AND status = 'STORED'"
                        : "SELECT COUNT(*) FROM card_instances WHERE name = ? AND status = 'STORED'";
                try (PreparedStatement ps = c.prepareStatement(query)) {
                    ps.setString(1, cardName.trim());
                    if (setCode != null && !setCode.isBlank()) ps.setString(2, setCode.trim().toUpperCase());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) count = rs.getInt(1);
                    }
                }
            }
        } catch (Exception ignored) {}

        if (count == 0 && CatalogView.activeCatalog != null) {
            for (Card card : CatalogView.activeCatalog.values()) {
                if (card.getName().equalsIgnoreCase(cardName.trim())) {
                    if (setCode == null || setCode.isBlank() || card.getSetCode().equalsIgnoreCase(setCode.trim())) {
                        count += card.getQuantity();
                    }
                }
            }
        }
        return count;
    }

    // Resolves default card price from catalog or defaults to 50.0
    public static double resolveCardPrice(String cardName, String setCode) {
        if (CatalogView.activeCatalog != null) {
            for (Card card : CatalogView.activeCatalog.values()) {
                if (card.getName().equalsIgnoreCase(cardName.trim())) {
                    if (setCode == null || setCode.isBlank() || card.getSetCode().equalsIgnoreCase(setCode.trim())) {
                        return card.getPrice();
                    }
                }
            }
        }
        return 50.0;
    }

    // Creates an OrderItem resolving all physical location & stock details
    public static OrderItem createOrderItem(String cardName, String setCode, String collectorNumber, int quantity, double priceOverride) {
        String cleanName = cardName != null ? cardName.trim() : "";
        String cleanSet = setCode != null ? setCode.trim().toUpperCase() : "";
        LocationInfo loc = resolvePhysicalLocation(cleanName, cleanSet);
        int stock = checkAvailableStock(cleanName, cleanSet);
        double price = priceOverride > 0 ? priceOverride : resolveCardPrice(cleanName, cleanSet);
        String sku = cleanSet.isEmpty() ? "ORD-" + Math.abs(cleanName.hashCode() % 10000) : "ORD-" + cleanSet + "-" + Math.abs(cleanName.hashCode() % 10000);
        return new OrderItem(sku, cleanName, cleanSet, collectorNumber, quantity, price, loc.boxId, loc.shelfUnit, loc.shelfTier, stock);
    }

    // Parses single card shortcut (e.g. "blb 1 x2", "mh3 4 foil", "Black Cat x3")
    public static OrderItem parseSingleCardInput(String input) {
        if (input == null || input.isBlank()) return null;
        String raw = input.trim();

        // 1. Try RapidIntake shortcut parser
        ParsedShortcut ps = RapidIntake.parseShortcut(raw);
        if (ps != null && ps.isValid()) {
            try {
                ResolvedCard rc = RapidIntake.resolve(ps);
                if (rc != null) {
                    double price = (rc.phpPrice() != null && rc.phpPrice() > 0) ? rc.phpPrice() : -1.0;
                    return createOrderItem(rc.name(), rc.setCode(), rc.collectorNumber(), ps.quantity(), price);
                }
            } catch (Exception ignored) {}
        }

        // 2. Check name with quantity modifier (e.g. "Card Name x4" or "Card Name, 4")
        String name = raw;
        int qty = 1;
        if (raw.matches("(?i).*\\s+x\\d+$")) {
            int xIdx = raw.toLowerCase().lastIndexOf(" x");
            name = raw.substring(0, xIdx).trim();
            try {
                qty = Integer.parseInt(raw.substring(xIdx + 2).trim());
            } catch (Exception ignored) {}
        }

        // Find set from catalog if exists
        String setCode = "";
        if (CatalogView.activeCards != null) {
            for (Card c : CatalogView.activeCards) {
                if (c.getName().equalsIgnoreCase(name)) {
                    setCode = c.getSetCode();
                    break;
                }
            }
        }

        return createOrderItem(name, setCode, "", qty, -1.0);
    }

    // Parses CSV order file or content
    public static List<OrderItem> importOrderCsv(String csvContent) throws Exception {
        List<OrderItem> items = new ArrayList<>();
        if (csvContent == null || csvContent.isBlank()) return items;

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return items;

            char delimiter = headerLine.contains("\t") ? '\t' : (headerLine.contains(";") ? ';' : ',');
            List<String> headers = parseCsvLine(headerLine, delimiter);
            Map<String, Integer> colMap = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                colMap.put(headers.get(i).trim().toLowerCase().replaceAll("[_\\-\\s]", ""), i);
            }

            // Detect column indices
            int nameIdx = findCol(colMap, "name", "cardname", "card", "title");
            int setIdx = findCol(colMap, "set", "setcode", "edition");
            int qtyIdx = findCol(colMap, "qty", "quantity", "count", "amount");
            int numIdx = findCol(colMap, "number", "num", "collectornumber", "collector", "sku");
            int priceIdx = findCol(colMap, "price", "unitprice", "rate", "cost");

            if (nameIdx == -1) {
                // If no header matches, assume col 0 is name, col 1 is set, col 2 is qty
                nameIdx = 0;
                setIdx = headers.size() > 1 ? 1 : -1;
                qtyIdx = headers.size() > 2 ? 2 : -1;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> parts = parseCsvLine(line, delimiter);
                if (nameIdx >= parts.size()) continue;

                String name = parts.get(nameIdx).trim().replaceAll("^\"|\"$", "");
                if (name.isEmpty() || name.equalsIgnoreCase("name") || name.equalsIgnoreCase("card")) continue;

                String setCode = (setIdx >= 0 && setIdx < parts.size()) ? parts.get(setIdx).trim().replaceAll("^\"|\"$", "") : "";
                String num = (numIdx >= 0 && numIdx < parts.size()) ? parts.get(numIdx).trim().replaceAll("^\"|\"$", "") : "";
                int qty = 1;
                if (qtyIdx >= 0 && qtyIdx < parts.size()) {
                    try {
                        qty = Integer.parseInt(parts.get(qtyIdx).trim().replaceAll("[^0-9]", ""));
                    } catch (Exception ignored) {}
                }
                double price = -1.0;
                if (priceIdx >= 0 && priceIdx < parts.size()) {
                    try {
                        price = Double.parseDouble(parts.get(priceIdx).trim().replaceAll("[^0-9.]", ""));
                    } catch (Exception ignored) {}
                }

                items.add(createOrderItem(name, setCode, num, qty, price));
            }
        }
        return items;
    }

    // RFC 4180-compliant CSV line parser handling quoted cells containing commas/delimiters
    public static List<String> parseCsvLine(String line, char delimiter) {
        List<String> tokens = new ArrayList<>();
        if (line == null) return tokens;
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++; // skip escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }

    private static int findCol(Map<String, Integer> colMap, String... candidates) {
        for (String c : candidates) {
            String norm = c.toLowerCase().replaceAll("[_\\-\\s]", "");
            if (colMap.containsKey(norm)) return colMap.get(norm);
        }
        return -1;
    }

    public static List<OrderItem> importOrderCsvFile(File file) throws Exception {
        if (file == null || !file.exists()) return new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return importOrderCsv(sb.toString());
    }

    // Calculates Breadcrumb for in-box depth navigation during picking
    public static NeighborBreadcrumb calculateBreadcrumb(String boxId, String targetCardName) {
        try {
            return StorageScaffolding.calculateBreadcrumb(boxId, targetCardName);
        } catch (Exception e) {
            return new NeighborBreadcrumb(null, targetCardName, null, 50.0, "Box: " + boxId + " (Position ~50%)");
        }
    }

    // Groups & sorts order items into an optimized physical forward-pass pick path
    public static List<OrderItem> sortPickPath(List<OrderItem> items) {
        List<OrderItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator
                .comparing((OrderItem a) -> a.getShelfUnit() != null ? a.getShelfUnit() : "")
                .thenComparing((OrderItem a) -> a.getBoxId() != null ? a.getBoxId() : "")
                .thenComparing((OrderItem a) -> a.getName() != null ? a.getName() : ""));
        return sorted;
    }

    // Groups active order items into box batches sorted by pick path
    public static List<OuttakeBoxBatch> groupOrderIntoBatches(List<OrderItem> items) {
        Map<String, OuttakeBoxBatch> batchMap = new LinkedHashMap<>();
        if (items != null) {
            for (OrderItem item : items) {
                String bId = (item.getBoxId() != null && !item.getBoxId().isBlank()) ? item.getBoxId() : "Unassigned";
                OuttakeBoxBatch batch = batchMap.computeIfAbsent(bId, k ->
                        new OuttakeBoxBatch(k, item.getShelfUnit(), item.getShelfTier()));
                batch.getItems().add(item);
            }
        }
        List<OuttakeBoxBatch> batches = new ArrayList<>(batchMap.values());
        batches.sort(Comparator
                .comparing((OuttakeBoxBatch b) -> b.getShelfUnit() != null ? b.getShelfUnit() : "")
                .thenComparing((OuttakeBoxBatch b) -> b.getBoxId() != null ? b.getBoxId() : ""));
        return batches;
    }

    // Confirms a single card item picked, updating SQLite to CHECKED_OUT and Catalog stock live
    public static List<String> confirmCardPick(OrderItem item) throws Exception {
        if (item == null || item.isPicked()) return List.of();
        Db.migrate();
        int needed = item.getQuantity();
        List<String> matchedIds = new ArrayList<>();
        String boxId = item.getBoxId();

        try (Connection c = Db.open()) {
            c.setAutoCommit(false);
            try {
                String query = (item.getSetCode() != null && !item.getSetCode().isBlank())
                        ? "SELECT id, box_id FROM card_instances WHERE name = ? AND set_code = ? AND status = 'STORED' LIMIT ?"
                        : "SELECT id, box_id FROM card_instances WHERE name = ? AND status = 'STORED' LIMIT ?";
                try (PreparedStatement ps = c.prepareStatement(query)) {
                    ps.setString(1, item.getName());
                    if (item.getSetCode() != null && !item.getSetCode().isBlank()) {
                        ps.setString(2, item.getSetCode());
                        ps.setInt(3, needed);
                    } else {
                        ps.setInt(2, needed);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            matchedIds.add(rs.getString("id"));
                            if (boxId == null || boxId.isBlank() || boxId.equals("Unassigned")) {
                                boxId = rs.getString("box_id");
                            }
                        }
                    }
                }

                if (!matchedIds.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE card_instances SET status = 'CHECKED_OUT', stored_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                        for (String id : matchedIds) {
                            ps.setString(1, id);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                if (boxId != null && !boxId.isBlank() && !boxId.equals("Unassigned")) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE box_containers SET current_card_count = "
                            + "(SELECT COUNT(*) FROM card_instances WHERE box_id = ? AND status = 'STORED') WHERE box_id = ?")) {
                        ps.setString(1, boxId);
                        ps.setString(2, boxId);
                        ps.executeUpdate();
                    }
                }

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }

        // Live catalog sync: decrement quantity
        try {
            if (CatalogView.activeCards != null) {
                for (Card card : CatalogView.activeCards) {
                    if (card.getName().equalsIgnoreCase(item.getName())) {
                        if (item.getSetCode().isEmpty() || card.getSetCode().equalsIgnoreCase(item.getSetCode())) {
                            CatalogView.adjustCardQuantity(card, -needed);
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        item.setPicked(true);
        item.setCheckedOutInstanceIds(matchedIds);
        return matchedIds;
    }

    // Reverts a card item pick, restoring SQLite to STORED and Catalog stock live
    public static void revertCardPick(OrderItem item, List<String> instanceIds) throws Exception {
        if (item == null || !item.isPicked()) return;
        Db.migrate();
        int qty = item.getQuantity();
        String boxId = item.getBoxId();

        try (Connection c = Db.open()) {
            c.setAutoCommit(false);
            try {
                if (instanceIds != null && !instanceIds.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE card_instances SET status = 'STORED', stored_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                        for (String id : instanceIds) {
                            ps.setString(1, id);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                } else {
                    String query = (item.getSetCode() != null && !item.getSetCode().isBlank())
                            ? "UPDATE card_instances SET status = 'STORED', stored_at = CURRENT_TIMESTAMP WHERE id IN (SELECT id FROM card_instances WHERE name = ? AND set_code = ? AND status = 'CHECKED_OUT' LIMIT ?)"
                            : "UPDATE card_instances SET status = 'STORED', stored_at = CURRENT_TIMESTAMP WHERE id IN (SELECT id FROM card_instances WHERE name = ? AND status = 'CHECKED_OUT' LIMIT ?)";
                    try (PreparedStatement ps = c.prepareStatement(query)) {
                        ps.setString(1, item.getName());
                        if (item.getSetCode() != null && !item.getSetCode().isBlank()) {
                            ps.setString(2, item.getSetCode());
                            ps.setInt(3, qty);
                        } else {
                            ps.setInt(2, qty);
                        }
                        ps.executeUpdate();
                    }
                }

                if (boxId != null && !boxId.isBlank() && !boxId.equals("Unassigned")) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE box_containers SET current_card_count = "
                            + "(SELECT COUNT(*) FROM card_instances WHERE box_id = ? AND status = 'STORED') WHERE box_id = ?")) {
                        ps.setString(1, boxId);
                        ps.setString(2, boxId);
                        ps.executeUpdate();
                    }
                }

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }

        // Live catalog sync: re-increment quantity
        try {
            if (CatalogView.activeCards != null) {
                for (Card card : CatalogView.activeCards) {
                    if (card.getName().equalsIgnoreCase(item.getName())) {
                        if (item.getSetCode().isEmpty() || card.getSetCode().equalsIgnoreCase(item.getSetCode())) {
                            CatalogView.adjustCardQuantity(card, qty);
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        item.setPicked(false);
        item.setCheckedOutInstanceIds(null);
    }

    // Confirms all unpicked cards in a box batch atomically
    public static Map<OrderItem, List<String>> confirmBoxBatchPick(OuttakeBoxBatch batch) throws Exception {
        Map<OrderItem, List<String>> affectedMap = new LinkedHashMap<>();
        if (batch == null || batch.getItems().isEmpty()) return affectedMap;
        for (OrderItem item : batch.getItems()) {
            if (!item.isPicked()) {
                List<String> ids = confirmCardPick(item);
                affectedMap.put(item, ids);
            }
        }
        return affectedMap;
    }

    // Reverts an entire box batch pick
    public static void revertBoxBatchPick(OuttakeBoxBatch batch, Map<OrderItem, List<String>> affectedMap) throws Exception {
        if (batch == null || affectedMap == null) return;
        for (Map.Entry<OrderItem, List<String>> entry : affectedMap.entrySet()) {
            revertCardPick(entry.getKey(), entry.getValue());
        }
    }

    // Reversible command model for Outtake actions
    public static class OuttakeAction {
        public enum Type { PICK_CARD, PICK_BATCH, ADD_ITEM, REMOVE_ITEM, MODIFY_QTY }

        public final Type type;
        public final OrderItem item;
        public final OuttakeBoxBatch batch;
        public final List<String> instanceIds;
        public final Map<OrderItem, List<String>> batchInstanceMap;
        public final int oldQty;
        public final int newQty;
        public final int itemIndex;

        public OuttakeAction(Type type, OrderItem item, List<String> instanceIds) {
            this.type = type;
            this.item = item;
            this.batch = null;
            this.instanceIds = instanceIds;
            this.batchInstanceMap = null;
            this.oldQty = item != null ? item.getQuantity() : 0;
            this.newQty = this.oldQty;
            this.itemIndex = -1;
        }

        public OuttakeAction(Type type, OuttakeBoxBatch batch, Map<OrderItem, List<String>> batchInstanceMap) {
            this.type = type;
            this.item = null;
            this.batch = batch;
            this.instanceIds = null;
            this.batchInstanceMap = batchInstanceMap;
            this.oldQty = 0;
            this.newQty = 0;
            this.itemIndex = -1;
        }

        public OuttakeAction(Type type, OrderItem item, int itemIndex, int oldQty, int newQty) {
            this.type = type;
            this.item = item;
            this.batch = null;
            this.instanceIds = null;
            this.batchInstanceMap = null;
            this.oldQty = oldQty;
            this.newQty = newQty;
            this.itemIndex = itemIndex;
        }
    }

    // History manager for Outtake undo and redo
    public static class OuttakeHistory {
        private final Deque<OuttakeAction> undoStack = new ArrayDeque<>();
        private final Deque<OuttakeAction> redoStack = new ArrayDeque<>();

        public void push(OuttakeAction action) {
            if (action != null) {
                undoStack.push(action);
                redoStack.clear();
            }
        }

        public boolean canUndo() { return !undoStack.isEmpty(); }
        public boolean canRedo() { return !redoStack.isEmpty(); }

        public OuttakeAction undo(List<OrderItem> activeOrderList) throws Exception {
            if (undoStack.isEmpty()) return null;
            OuttakeAction action = undoStack.pop();
            switch (action.type) {
                case PICK_CARD -> revertCardPick(action.item, action.instanceIds);
                case PICK_BATCH -> revertBoxBatchPick(action.batch, action.batchInstanceMap);
                case ADD_ITEM -> {
                    if (activeOrderList != null && action.item != null) activeOrderList.remove(action.item);
                }
                case REMOVE_ITEM -> {
                    if (activeOrderList != null && action.item != null) {
                        if (action.itemIndex >= 0 && action.itemIndex <= activeOrderList.size()) {
                            activeOrderList.add(action.itemIndex, action.item);
                        } else {
                            activeOrderList.add(action.item);
                        }
                    }
                }
                case MODIFY_QTY -> {
                    if (action.item != null) action.item.setQuantity(action.oldQty);
                }
            }
            redoStack.push(action);
            return action;
        }

        public OuttakeAction redo(List<OrderItem> activeOrderList) throws Exception {
            if (redoStack.isEmpty()) return null;
            OuttakeAction action = redoStack.pop();
            switch (action.type) {
                case PICK_CARD -> confirmCardPick(action.item);
                case PICK_BATCH -> confirmBoxBatchPick(action.batch);
                case ADD_ITEM -> {
                    if (activeOrderList != null && action.item != null && !activeOrderList.contains(action.item)) {
                        activeOrderList.add(action.item);
                    }
                }
                case REMOVE_ITEM -> {
                    if (activeOrderList != null && action.item != null) activeOrderList.remove(action.item);
                }
                case MODIFY_QTY -> {
                    if (action.item != null) action.item.setQuantity(action.newQty);
                }
            }
            undoStack.push(action);
            return action;
        }

        public void clear() {
            undoStack.clear();
            redoStack.clear();
        }
    }

    // Computes order summary statistics
    public static OrderSummary computeSummary(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return new OrderSummary(0, 0, 0.0, 0, true);
        }
        int totalCards = 0;
        double totalAmount = 0.0;
        Map<String, Boolean> distinctBoxes = new LinkedHashMap<>();
        boolean allInStock = true;

        for (OrderItem item : items) {
            totalCards += item.getQuantity();
            totalAmount += item.getTotalPrice();
            if (item.getBoxId() != null && !item.getBoxId().isBlank()) {
                distinctBoxes.put(item.getBoxId(), true);
            }
            if (!item.isSufficientStock()) {
                allInStock = false;
            }
        }
        return new OrderSummary(items.size(), totalCards, totalAmount, distinctBoxes.size(), allInStock);
    }

    // Executes atomic order checkout, updating SQLite card instances to CHECKED_OUT and Catalog stock
    public static CheckoutResult checkoutOrder(List<OrderItem> items) throws Exception {
        if (items == null || items.isEmpty()) {
            return new CheckoutResult(false, "", 0, 0, 0.0, "Order is empty.");
        }

        Db.migrate();
        String receiptId = "REC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        int totalCardsCheckedOut = 0;
        int linesCheckedOut = 0;
        double totalAmount = 0.0;

        try (Connection c = Db.open()) {
            c.setAutoCommit(false);
            try {
                for (OrderItem item : items) {
                    if (item.isPicked()) {
                        totalCardsCheckedOut += item.getQuantity();
                        linesCheckedOut++;
                        totalAmount += item.getTotalPrice();
                        continue;
                    }

                    int needed = item.getQuantity();
                    List<String> matchedIds = new ArrayList<>();
                    String boxId = item.getBoxId();

                    // Find stored card instances in SQLite
                    String query = (item.getSetCode() != null && !item.getSetCode().isBlank())
                            ? "SELECT id, box_id FROM card_instances WHERE name = ? AND set_code = ? AND status = 'STORED' LIMIT ?"
                            : "SELECT id, box_id FROM card_instances WHERE name = ? AND status = 'STORED' LIMIT ?";
                    try (PreparedStatement ps = c.prepareStatement(query)) {
                        ps.setString(1, item.getName());
                        if (item.getSetCode() != null && !item.getSetCode().isBlank()) {
                            ps.setString(2, item.getSetCode());
                            ps.setInt(3, needed);
                        } else {
                            ps.setInt(2, needed);
                        }
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                matchedIds.add(rs.getString("id"));
                                if (boxId == null || boxId.isBlank() || boxId.equals("Unassigned")) {
                                    boxId = rs.getString("box_id");
                                }
                            }
                        }
                    }

                    // Transition matched instances to CHECKED_OUT
                    if (!matchedIds.isEmpty()) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE card_instances SET status = 'CHECKED_OUT', stored_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                            for (String id : matchedIds) {
                                ps.setString(1, id);
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                    }

                    // Update box container card count if box known
                    if (boxId != null && !boxId.isBlank() && !boxId.equals("Unassigned")) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE box_containers SET current_card_count = "
                                + "(SELECT COUNT(*) FROM card_instances WHERE box_id = ? AND status = 'STORED') WHERE box_id = ?")) {
                            ps.setString(1, boxId);
                            ps.setString(2, boxId);
                            ps.executeUpdate();
                        }
                    }

                    totalCardsCheckedOut += needed;
                    linesCheckedOut++;
                    totalAmount += item.getTotalPrice();

                    // Live catalog sync: decrement quantity
                    try {
                        if (CatalogView.activeCards != null) {
                            for (Card card : CatalogView.activeCards) {
                                if (card.getName().equalsIgnoreCase(item.getName())) {
                                    if (item.getSetCode().isEmpty() || card.getSetCode().equalsIgnoreCase(item.getSetCode())) {
                                        CatalogView.adjustCardQuantity(card, -needed);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }

        return new CheckoutResult(true, receiptId, linesCheckedOut, totalCardsCheckedOut, totalAmount,
                "✓ Order checked out successfully! Receipt #" + receiptId + " (" + totalCardsCheckedOut + " cards, ₱"
                + String.format("%.2f", totalAmount) + ")");
    }
}
