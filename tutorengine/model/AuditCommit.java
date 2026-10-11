package tutorengine.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Entity model for audit_commits table (tutorengine_implementation_specification.md §2.2)
public class AuditCommit {
    private static final DateTimeFormatter TAG_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final String commitHash;
    private final String commitTag;
    private final String employeeName;
    private final int cardCount;
    private final String timestamp;
    private final String prevHash;
    private final String rowHash;

    public AuditCommit(String commitHash, String commitTag, String employeeName,
                       int cardCount, String timestamp, String prevHash, String rowHash) {
        this.commitHash = commitHash;
        this.commitTag = commitTag;
        this.employeeName = employeeName;
        this.cardCount = cardCount;
        this.timestamp = timestamp;
        this.prevHash = prevHash != null ? prevHash : "";
        this.rowHash = rowHash != null ? rowHash : "";
    }

    public static String generateTag(String employeeName, int cardCount) {
        String safeName = (employeeName == null || employeeName.isBlank()) ? "Staff" : employeeName.trim().replaceAll("\\s+", "_");
        return "BATCH-" + LocalDateTime.now().format(TAG_FMT) + "-" + safeName + "-" + cardCount + "cards";
    }

    public String getCommitHash() { return commitHash; }
    public String getCommitTag() { return commitTag; }
    public String getEmployeeName() { return employeeName; }
    public int getCardCount() { return cardCount; }
    public String getTimestamp() { return timestamp; }
    public String getPrevHash() { return prevHash; }
    public String getRowHash() { return rowHash; }

    @Override
    public String toString() {
        return commitTag + " (" + cardCount + " cards by " + employeeName + ") [" + commitHash + "]";
    }

    public static void main(String[] args) {
        String tag = generateTag("Karl", 50);
        System.out.println("Generated Tag: " + tag);
        AuditCommit ac = new AuditCommit("hash123", tag, "Karl", 50, "2026-10-11 20:30:00", "", "hash123");
        System.out.println("AuditCommit: " + ac);
        System.out.println("AuditCommit OK.");
    }
}
