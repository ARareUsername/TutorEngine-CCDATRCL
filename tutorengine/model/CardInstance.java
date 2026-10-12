package tutorengine.model;

import java.util.UUID;

// Entity model for card_instances table (tutorengine_implementation_specification.md §2.1)
public class CardInstance {
    private final String id;
    private final String scryfallId;
    private final String name;
    private final String setCode;
    private final String collectorNumber;
    private final String rarity;
    private String condition;
    private String finish;
    private String language;
    private String status;
    private String boxId;
    private final String createdAt;
    private String storedAt;

    public CardInstance(String id, String scryfallId, String name, String setCode,
                        String collectorNumber, String rarity, String condition,
                        String finish, String language, String status,
                        String boxId, String createdAt, String storedAt) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.scryfallId = scryfallId != null ? scryfallId : "";
        this.name = name;
        this.setCode = setCode;
        this.collectorNumber = collectorNumber;
        this.rarity = rarity != null ? rarity : "common";
        this.condition = condition != null ? condition : "NM";
        this.finish = finish != null ? finish : "nonfoil";
        this.language = language != null ? language : "EN";
        this.status = status != null ? status : "UNSTORED";
        this.boxId = boxId;
        this.createdAt = createdAt;
        this.storedAt = storedAt;
    }

    public static CardInstance createUnstored(String scryfallId, String name, String setCode,
                                              String collectorNumber, String rarity, String condition,
                                              String finish) {
        return new CardInstance(UUID.randomUUID().toString(), scryfallId, name, setCode,
                collectorNumber, rarity, condition, finish, "EN", "UNSTORED",
                null, null, null);
    }

    public String getId() { return id; }
    public String getScryfallId() { return scryfallId; }
    public String getName() { return name; }
    public String getSetCode() { return setCode; }
    public String getCollectorNumber() { return collectorNumber; }
    public String getRarity() { return rarity; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getFinish() { return finish; }
    public void setFinish(String finish) { this.finish = finish; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBoxId() { return boxId; }
    public void setBoxId(String boxId) { this.boxId = boxId; }
    public String getCreatedAt() { return createdAt; }
    public String getStoredAt() { return storedAt; }
    public void setStoredAt(String storedAt) { this.storedAt = storedAt; }

    @Override
    public String toString() {
        return name + " (" + setCode + " #" + collectorNumber + ") [" + condition + "/" + finish + "] - " + status + (boxId != null ? " @" + boxId : "");
    }

    public static void main(String[] args) {
        CardInstance ci = CardInstance.createUnstored("scry-123", "Sol Ring", "CMM", "100", "uncommon", "NM", "foil");
        System.out.println("CardInstance: " + ci);
        System.out.println("CardInstance OK.");
    }
}
