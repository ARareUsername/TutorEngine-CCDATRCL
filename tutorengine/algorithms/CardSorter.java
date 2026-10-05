package tutorengine.algorithms;

import java.util.ArrayList;
import java.util.List;
import tutorengine.model.Card;

// OWNER: Mate C (Catalog & Priority). No other member edits this file.
// TASK: 2 manual sorts on project objects (Insertion + Selection by name) + comparison counts.
// ACCEPT: both sort 100/500/1000/5000 lists identically to List.sort; report comparisons/movements.
// DEFENSE: best/avg/worst per sort + why small-input timings diverge from theory.
// SPEC: S8 sorting (2 of Bubble/Selection/Insertion), S9 sort-vs-sort, Template F.
public class CardSorter {
    public static long comparisons, movements;

    public static void insertionSort(List<Card> a) {
        System.out.println("TODO Mate C: manual insertion sort by name");
    }

    public static void selectionSort(List<Card> a) {
        System.out.println("TODO Mate C: manual selection sort by name");
    }

    public static void main(String[] args) {
        System.out.println("CardSorter stub. OWNER: Mate C. comparisons=" + comparisons);
        new ArrayList<Card>().sort(null);
    }
}
