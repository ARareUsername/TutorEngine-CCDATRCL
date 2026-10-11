# TutorEngine — MTG Singles & Storage Decision System

A Java (Maven) desktop system designed for a local game store to scan barcodes, catalog card singles, manage online listing priority, auto-correct mistyped SKUs, and benchmark search and sorting algorithms.

* **Course:** CCDATRCL – Data Structures and Algorithms
* **Documentation & Specs:** See `PROJECT_SPECS.md` and `TutorEngine/TutorEngine.md`
* **Dataset:** `Dataset/cards.csv` (60 real Magic: The Gathering singles from Scryfall, 15 columns incl. manaCost/power/toughness/setCode; boxes filed in expansion order via `Card.BY_EXPANSION`)

---

## Quick Start (NetBeans)

Most team members will run and test the project directly in NetBeans:

1. **Open Project:** Go to **File → Open Project**, choose this repository's folder (marked with an `M` Maven icon), and click **Open Project**.
2. **Build:** Right-click the project name in the left sidebar and click **Build**. Ensure the console prints `BUILD SUCCESS`.
3. **Run the App:** Click the green **Run (▶)** button or right-click the project and select **Run** to launch the JavaFX UI (`tutorengine.ui.FxApp`, needs a display).
   * *To run a specific class (like CLI demos):* Right-click the Java file directly and choose **Run File** (or press `Shift + F6`).
   * *To debug:* Press `Ctrl + F5`.

### Terminal Alternative
If you prefer using the command line:
* Build: `mvn compile`
* Run JavaFX UI (needs a display): `mvn javafx:run`
* Run CLI demo: `mvn exec:java -Dexec.mainClass=tutorengine.Main`
* Headless view checks (no display needed): `mvn -q exec:java -Dexec.mainClass=tutorengine.ui.CatalogView` (also `Theme`, `PriorityView`, `GraphView`, `BenchmarkView`)

---

## Member Task Assignments

> **Rule:** Only work on files assigned to you. Check off `[x]` your tasks here in the same Pull Request where you deliver your code.

### Bondoc, Karl B. — Search, UI & Dataset (JavaFX-only UI; Swing `TutorEngineUI.java` retired)
* [x] SKU search (`HashMap`) + List Catalog view (JavaFX `CatalogView`; Swing predecessor retired)
* [x] Convert Scryfall bulk data into 60-row `Dataset/cards.csv`
* [x] Set up and verify Maven `pom.xml` build configuration
* [x] Implement `CSVLoader.load()` with SKIP handling + wire 60-row dataset into `Main` and UI (fallback-safe; David keeps trace + report rows)
* [x] Create hash collision demo in `Main.java` (using `new HashMap<>(7)`, two colliding SKUs, and printed bucket chain) (PR #12: MTG-OTJ-001/009 share bucket 1, chain printed, both `get()` fine)
* [x] Add input guards for empty or invalid SKUs in search (PR #12: `checkSku` in `Main` + JavaFX search boxes)
* [x] Implement `tutorengine/ui/CardImageCache.java` with responsive GUI preview (see `TutorEngine.md §8) (PR #12: cache-first, 100 ms gap, 50 MB eviction; verified HIT 12829 B + offline placeholder)
* [x] Complete final system integration of all member modules into GUI and `Main` (PR #12: `Main` [5] smoke lines; JavaFX views per module + Store Map + fill)
* [x] JavaFX + AtlantaFX UI shell (`javafx-atlantafx-ui`: FxApp, Theme, views — implemented 2026-10-08, now the only UI)
* [x] Fuzzy card search (`fuzzy-card-search`: field matchers + fan-out + UI fallback in `SkuCorrector`) (PR #12: new `tutorengine/search/SkuFuzzy.java`, calls `bfsSuggest` without editing it; JavaFX shows closest + field)
* [x] Cabinet store map (`cabinet-store-map`: hierarchy model + canonical 18-node graph + fill display) (model + graph on master; PR #12 adds fill display to `Main` + `GraphView`)
* [x] Session add/delete records in the JavaFX UI with guards (ui-demo-coverage: duplicate/unknown messages, in-memory only)
* [x] §11 demo surfaces in the JavaFX UI — list traversal, stack/queue, BST, heap, both sorts, collision (ui-demo-coverage: `DemoTexts` builders + Demos tab)
* [x] Benchmark + complexity display (ui-demo-coverage: `BenchmarkView` reads `bench.txt` with placeholder fallback + Template E table)
* [x] Clerk workflows (clerk-workflow-fixes: card picture on hits, Locate-to-shelf + clickable suggestions, qty/slot add form, delete confirm + undo, Demos behind `-Dtutorengine.dev`)

### David, Abraham John D. — Data & Loader
*Target Files: `util/CSVLoader.java`, `Dataset/cards.csv`*
* [x] Implement `load()` to read CSV data cleanly and skip corrupt rows with a `SKIP line N` console warning without crashing (merged PR #1; master verified 2026-10-11: loads 60 cards)
* [x] Add a `main()` demo showing at least 50 loaded cards and printing 3 sample entries (master verified 2026-10-11: `Loaded 60 cards` + 3 samples)
* [ ] Provide a 1-page list trace diagram for `TracePacket/` and complete the Template C field mapping table

### De Jesus, Aeon Miles J. — Transaction History & Intake
*Target Files: `model/TransactionLog.java`, `intake/IntakeBuffer.java`*
* [x] Implement `printAll()` with numbered log traversal and empty-log safety checks (merged PR #4; master verified)
* [x] Create `main()` runnable demos (merged PR #6 + #10, both compile+run verified 2026-10-11):
  * 3-entry transaction history log with undo capability (`TransactionLog`: real SKUs MTG-TSP-157 / MTG-UGIN-146 / MTG-WOC-129, PR #10)
  * FIFO staging buffer and LIFO undo operations using sample cards (`IntakeBuffer`: Bird / Temporal Manipulation / Gorion, `processNext` + `TransactionLog` wiring, PR #6)
* [ ] Prepare 1-page trace packet (linked list, queue, and stack) and pass test cases T01–T03 (trace exists as in-file comments in PR #6/#10; still needs `TracePacket/` files)

### Dimazana, Amiel Benedict R. — Catalog, Priority & Sorting
*Target Files: `catalog/CatalogIndex.java`, `priority/PriorityDesk.java`, `algorithms/CardSorter.java`*
* [x] 5-card binary search tree demo: `put`, search hits/misses, in-order traversal, and deletion of leaf and root nodes (merged PR #7, compile+run verified 2026-10-11; adds `firstKey()` for min-key deletion)
* [x] 5-card priority heap demo: polling order, new-top priority promotion, and custom comparator reweighting (merged PR #8, compile+run verified 2026-10-11: peek Mana Drain → poll → runner-up Narset → late Black Lotus jumps line → foil 10→20 reweight)
* [x] Implement manual Insertion Sort and Selection Sort tracking comparison and swap counts; verify correctness against `List.sort` (merged PR #9, compile+run verified 2026-10-11: both match `List.sort`, counters print)
* [ ] Prepare traces (BST, Min/Max Heap, single sort pass), complete validation guards (price, quantity, demand), and pass tests T04, T09, and T10

### Huypungco, Matthew James M. — Graph Navigation & Benchmarks
*Target Files: `model/StorageLocation.java`, `algorithms/StoreNavigator.java`, `algorithms/SkuCorrector.java`, `util/BenchmarkSuite.java`*
* [x] Model a 10-node / 15-edge store map; implement shortest-path BFS traversal and full DFS audit with missing-node handling (on master via `6313d11`: 18-node / 21-edge map, BFS 3-hop + 18/18 DFS + `[]` missing-node case all run-verified 2026-10-11; PR #11 adds only a date comment here)
* [x] Implement `SkuCorrector`: build a 1-character difference graph and run `bfsSuggest()` (depth $\le$ 2) to correct mistyped SKU inputs (merged PR #11, compile+run verified 2026-10-11: O-for-0 typo → 6 suggestions, exact → `[]`, `ZZZ` → `[]`)
* [x] Build `BenchmarkSuite` with `System.nanoTime()` comparing sorting algorithms and search algorithms across sizes 100, 500, 1000, and 5000; output data to `BenchmarkResults/bench.txt` (merged PR #11, run-verified 2026-10-11: 16 race rows + 2 NOTE lines, file written)
* [x] Prepare BFS/DFS trace diagrams, provide analysis on small-sample timing divergence, and pass tests T07 and T08 (merged PR #11 ships `TracePacket/StoreNavigator-BFS-DFS.txt`, `SkuCorrector-BFS.txt`, `BenchmarkSuite.txt` with divergence notes)

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
* **Meaningful Git History:** We need at least 15 verified, well-spaced commits across the team. Use clear semantic prefixes (e.g., `feat:`, `test:`, `docs:`). Evidence: [`GitHistory.md`](GitHistory.md) (regenerate after each merge).
* **Deliverable Checklist:** Every completed task must include:
  1. Working source code.
  2. Passing test cases.
  3. A 1-page visual memory/execution trace saved under `TracePacket/`.
  4. Your filled-in row in **Template I** (documenting modified files, commit links, and defense questions).
* **Code-Backed Documentation:** All Big-O complexity tables and benchmark values cited in your final report must link directly to the specific class and method implementing them.
