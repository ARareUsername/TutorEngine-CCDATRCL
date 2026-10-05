# TutorEngine — MTG Singles & Storage Decision System

A Java (Maven) desktop system designed for a local game store to scan barcodes, catalog card singles, manage online listing priority, auto-correct mistyped SKUs, and benchmark search and sorting algorithms.

* **Course:** CCDATRCL – Data Structures and Algorithms
* **Documentation & Specs:** See `PROJECT_SPECS.md` and `TutorEngine/TutorEngine.md`
* **Dataset:** `Dataset/cards.csv` (60 real Magic: The Gathering singles from Scryfall)

---

## Quick Start (NetBeans)

Most team members will run and test the project directly in NetBeans:

1. **Open Project:** Go to **File → Open Project**, choose this repository's folder (marked with an `M` Maven icon), and click **Open Project**.
2. **Build:** Right-click the project name in the left sidebar and click **Build**. Ensure the console prints `BUILD SUCCESS`.
3. **Run the App:** Click the green **Run (▶)** button or right-click the project and select **Run** to launch the GUI (`tutorengine.TutorEngineUI`).
   * *To run a specific class (like CLI demos):* Right-click the Java file directly and choose **Run File** (or press `Shift + F6`).
   * *To debug:* Press `Ctrl + F5`.

### Terminal Alternative
If you prefer using the command line:
* Build: `mvn compile`
* Run GUI: `mvn exec:java`
* Run CLI demo: `mvn exec:java -Dexec.mainClass=tutorengine.Main`

---

## Member Task Assignments

> **Rule:** Only work on files assigned to you. Check off `[x]` your tasks here in the same Pull Request where you deliver your code.

### Bondoc, Karl B. — Search, UI & Dataset
* [x] SKU search (`HashMap`) + List Catalog view (`TutorEngineUI.java`)
* [x] Convert Scryfall bulk data into 60-row `Dataset/cards.csv`
* [x] Set up and verify Maven `pom.xml` build configuration
* [ ] Create hash collision demo in `Main.java` (using `new HashMap<>(7)`, two colliding SKUs, and printed bucket chain)
* [ ] Add input guards for empty or invalid SKUs in search
* [ ] Implement `tutorengine/ui/CardImageCache.java` with responsive GUI preview (see `TutorEngine.md §8`)
* [ ] Complete final system integration of all member modules into GUI and `Main`

### David, Abraham John D. — Data & Loader
*Target Files: `util/CSVLoader.java`, `Dataset/cards.csv`*
* [ ] Implement `load()` to read CSV data cleanly and skip corrupt rows with a `SKIP line N` console warning without crashing
* [ ] Add a `main()` demo showing at least 50 loaded cards and printing 3 sample entries
* [ ] Provide a 1-page list trace diagram for `TracePacket/` and complete the Template C field mapping table

### De Jesus, Aeon Miles J. — Transaction History & Intake
*Target Files: `model/TransactionLog.java`, `intake/IntakeBuffer.java`*
* [ ] Implement `printAll()` with numbered log traversal and empty-log safety checks
* [ ] Create `main()` runnable demos:
  * 3-entry transaction history log with undo capability
  * FIFO staging buffer and LIFO undo operations using sample cards
* [ ] Prepare 1-page trace packet (linked list, queue, and stack) and pass test cases T01–T03

### Dimazana, Amiel Benedict R. — Catalog, Priority & Sorting
*Target Files: `catalog/CatalogIndex.java`, `priority/PriorityDesk.java`, `algorithms/CardSorter.java`*
* [ ] 5-card binary search tree demo: `put`, search hits/misses, in-order traversal, and deletion of leaf and root nodes
* [ ] 5-card priority heap demo: polling order, new-top priority promotion, and custom comparator reweighting
* [ ] Implement manual Insertion Sort and Selection Sort tracking comparison and swap counts; verify correctness against `List.sort`
* [ ] Prepare traces (BST, Min/Max Heap, single sort pass), complete validation guards (price, quantity, demand), and pass tests T04, T09, and T10

### Huypungco, Matthew James M. — Graph Navigation & Benchmarks
*Target Files: `model/StorageLocation.java`, `algorithms/StoreNavigator.java`, `algorithms/SkuCorrector.java`, `util/BenchmarkSuite.java`*
* [ ] Model a 10-node / 15-edge store map; implement shortest-path BFS traversal and full DFS audit with missing-node handling
* [ ] Implement `SkuCorrector`: build a 1-character difference graph and run `bfsSuggest()` (depth $\le$ 2) to correct mistyped SKU inputs
* [ ] Build `BenchmarkSuite` with `System.nanoTime()` comparing sorting algorithms and search algorithms across sizes 100, 500, 1000, and 5000; output data to `BenchmarkResults/bench.txt`
* [ ] Prepare BFS/DFS trace diagrams, provide analysis on small-sample timing divergence, and pass tests T07 and T08

---

## How to Contribute

Follow one of the two workflows below. Always keep each branch dedicated to your own task.

### Option A: Using NetBeans (Recommended)

1. **Clone the Repo:**
   * In NetBeans, click **Team → Git → Clone…**
   * Paste Repository URL: `https://github.com/ARareUsername/TutorEngine-CCDATRCL.git`
   * Click **Next**, complete the wizard, and open the project.
2. **Create Your Feature Branch:**
   * Go to **Team → Branch → Create Branch…**
   * Name your branch using the format `<lastname>/<feature-name>` (e.g., `david/csv-loader`).
   * Select **Create & Checkout**.
3. **Make Changes & Test:**
   * Edit only your assigned files.
   * Verify your work by right-clicking the project and selecting **Build** to make sure there are no errors.
4. **Commit:**
   * Go to **Team → Commit…**
   * Check the boxes *only* for the files you modified.
   * Write a clear commit message using conventional format: `feat(scope): short description of work` (avoid vague messages like `update` or `fix`).
   * Click **Commit**.
5. **Push:**
   * Go to **Team → Push…**, click **Next**, and click **Finish**.
6. **Open a Pull Request:**
   * Open the repository on GitHub in your browser.
   * Click the yellow **Compare & pull request** button.
   * Fill out the PR template checklist (from `TutorEngine.md §7`) and submit.

---

### Option B: Using Git via Terminal

```bash
# 1. Clone the project and enter the folder
git clone https://github.com/ARareUsername/TutorEngine-CCDATRCL.git
cd TutorEngine-CCDATRCL

# 2. Create and switch to your feature branch
git checkout -b <lastname>/<feature-name>

# 3. Work on your files, then verify compilation
mvn compile

# 4. Stage and commit your assigned files
git add <your-changed-files>
git commit -m "feat(scope): concise description of changes"

# 5. Push your branch to GitHub
git push -u origin <lastname>/<feature-name>
```

*After pushing, go to GitHub in your browser and click **Compare & pull request**.*

---

## Contribution & Grading Guidelines

To ensure full credit during project defense and grading, adhere strictly to these rules:

* **Strict File Ownership:** Do not edit another member's assigned file. If you notice an issue, review or leave a comment on their pull request.
* **Independent Executability:** Every class must contain a functional `public static void main(String[] args)` method demonstrating its data structure or algorithm in isolation.
* **Meaningful Git History:** We need at least 15 verified, well-spaced commits across the team. Use clear semantic prefixes (e.g., `feat:`, `test:`, `docs:`).
* **Deliverable Checklist:** Every completed task must include:
  1. Working source code.
  2. Passing test cases.
  3. A 1-page visual memory/execution trace saved under `TracePacket/`.
  4. Your filled-in row in **Template I** (documenting modified files, commit links, and defense questions).
* **Code-Backed Documentation:** All Big-O complexity tables and benchmark values cited in your final report must link directly to the specific class and method implementing them.
