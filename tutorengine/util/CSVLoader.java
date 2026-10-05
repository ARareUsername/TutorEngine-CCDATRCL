package tutorengine.util;

import java.util.ArrayList;
import java.util.List;
import tutorengine.model.Card;

// OWNER: Mate A (Data & Loader). No other member edits this file.
// TASK: parse scanner CSV exports into Card objects; own Dataset/cards.csv (50+ records).
// ACCEPT: CSVLoader.load("Dataset/cards.csv").size() >= 50; bad rows skipped, not crash.
// DEFENSE: why ArrayList staging O(1) index for sort/benchmark; generation rules documented.
// SPEC: PROJECT_SPECS.md S6 dataset, S11 load/generate, Template C.
public class CSVLoader {
    public static List<Card> load(String path) {
        System.out.println("TODO Mate A: parse " + path + " -> List<Card>");
        return new ArrayList<>();
    }

    public static void main(String[] args) {
        System.out.println("CSVLoader stub. OWNER: Mate A. Run: javac tutorengine/model/Card.java tutorengine/util/CSVLoader.java && java tutorengine.util.CSVLoader");
    }
}
