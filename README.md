# TutorEngine — MTG Singles & Storage Decision System

Java (Maven) app for a local game store: intake barcode scans, catalog singles,
prioritize web listings, correct mistyped SKUs, and benchmark sort/search choices.
Course: CCDATRCL – Data Structures and Algorithms.

Spec: `PROJECT_SPECS.md` (repo parent) + `TutorEngine/TutorEngine.md` (design + §7 assignments + §8 GUI roadmap).
Dataset: `Dataset/cards.csv` — 60 real singles from Scryfall Default Cards 2026-10-04
(converted 2026-10-05 by Bondoc, Karl B.; quantity/box assigned locally, demand from `edhrec_rank`).

## Quick start (NetBeans — everyone uses this)

1. **Open:** File → Open Project → select this folder (it shows a Maven `M` badge) → Open Project.
2. **Build:** right-click project → Build (hammer icon). Must say BUILD SUCCESS.
3. **Run UI:** right-click project → Run / green ▶ (runs `tutorengine.TutorEngineUI`).
   Switch target: right-click project → Properties → Run → Main Class → Browse…
   (`tutorengine.Main` for the CLI demo, `tutorengine.util.CSVLoader` etc. for your own demo).
4. **Run one file:** right-click the file → Run File (Shift+F6). Debug: Ctrl+F5.

Terminal fallback: `mvn compile`, UI: `mvn exec:java`, CLI: `mvn exec:java -Dexec.mainClass=tutorengine.Main`.

## NetBeans Git workflow (buttons, no terminal)

1. **First time — Clone:** Team → Git → Clone… → paste `https://github.com/ARareUsername/TutorEngine-CCDATRCL.git` → Next → Finish. Then File → Open Project on the cloned folder.
2. **New task — Branch:** Team → Branch → Create Branch… → name `<lastname>/my-task` (e.g. `david/csv-loader`) → Create & Checkout.
3. **Work:** edit ONLY your files (see task list above). Right-click project → Build after every change.
4. **Commit:** Team → Commit… → write message `feat(scope): what you did` (never `update`) → tick ONLY your files → Commit.
5. **Push:** Team → Push… → Next → Finish.
6. **Pull Request:** NetBeans has no PR button — open the repo in a browser, click the yellow **Compare & pull request** banner → **Create pull request**, paste your `TutorEngine.md §7` checklist.
7. **Update this README:** tick your `[ ]` → `[x]` in the same PR that finishes the task.

## Tasks by member (update your boxes in the same PR that finishes the work)

### Bondoc, Karl B. — Search / UI / Dataset
- [x] SKU search (`HashMap`) + List Catalog (`TutorEngineUI.java`)
- [x] Scryfall bulk → 60-row `Dataset/cards.csv`
- [x] Maven `pom.xml` (build verified)
- [ ] Hash **collision demo** in `Main.java` (init `new HashMap<>(7)`, 2 colliding SKUs, print chain)
- [ ] Blank-SKU / invalid-input guard in search
- [ ] `tutorengine/ui/CardImageCache.java` + responsive GUI w/ image double-check (see `TutorEngine.md §8`)
- [ ] Final integration of all members' classes into UI + `Main`

### David, Abraham John D. — Data & Loader (`util/CSVLoader.java`, `Dataset/cards.csv`)
- [ ] `load()` parses CSV, skips bad rows with `SKIP line N` message, never crashes
- [ ] `main()` demo prints count >= 50 + 3 samples
- [ ] 1-page list trace for `TracePacket/` + Template C field table (report S2)

### De Jesus, Aeon Miles J. — History & Intake (`model/TransactionLog.java`, `intake/IntakeBuffer.java`)
- [ ] `printAll()` numbered traversal + empty-log safe
- [ ] `main()` demos: 3-entry log + undo; FIFO stage/process + LIFO undo with real cards
- [ ] 1-page traces (list + queue/stack) + tests T01–T03

### Dimazana, Amiel Benedict R. — Catalog / Priority / Sort (`catalog/CatalogIndex.java`, `priority/PriorityDesk.java`, `algorithms/CardSorter.java`)
- [ ] 5-card catalog demo: put/get-hit/get-miss/inorder + leaf & root delete
- [ ] 5-card heap demo: poll order + new-top jump; practice comparator reweight
- [ ] Manual Insertion + Selection sorts with comparison/movement counts, verified vs `List.sort`
- [ ] Traces (BST, heap, one sort pass) + tests T04/T09/T10; add-record validation (price/qty/demand guards)

### Huypungco, Matthew James M. — Graph & Benchmark (`model/StorageLocation.java`, `algorithms/StoreNavigator.java`, `algorithms/SkuCorrector.java`, `util/BenchmarkSuite.java`)
- [ ] 10-node/15-edge store map; `bfsPath()` min-hop + `dfs()` audit; missing-node safe
- [ ] `SkuCorrector`: 1-char-diff graph + `bfsSuggest()` depth≤2 demo on mistyped SKU
- [ ] `BenchmarkSuite`: `nanoTime` sort-vs-sort + search-vs-search at 100/500/1000/5000 → `BenchmarkResults/bench.txt`
- [ ] Traces (BFS, DFS) + tests T07/T08; benchmark interpretation (why small-n diverges)

## How to contribute — terminal

```bash
git clone https://github.com/ARareUsername/TutorEngine-CCDATRCL.git
cd TutorEngine-CCDATRCL
git checkout -b <lastname>/my-task      # e.g. david/csv-loader
# edit ONLY your files (see table above)
mvn compile                              # must pass
git add <your files>
git commit -m "feat(scope): what you did"  # never "update"/"fix"
git push -u origin <lastname>/my-task
```
Then open a Pull Request (see GUI steps 4–5 below) and fill the PR checklist from `TutorEngine.md §7`.

## How to contribute — GitHub website buttons

1. Open https://github.com/ARareUsername/TutorEngine-CCDATRCL → click the file you own.
2. Pencil icon (Edit) → make changes → green **Commit changes…** button.
3. Choose **Create a new branch** (name it `<lastname>/my-task`) → **Propose changes**.
4. Yellow banner **Compare & pull request** → **Create pull request**.
5. In the description paste your PR checklist (`TutorEngine.md §7`: demo passes, tests, trace page, Template I row).
6. Small edits only in the browser — for new files use **Add file → Create new file**; for real work prefer the terminal steps above.

## Documentation rules (graded — read before pushing)

1. **One owner per file.** File headers name the owner; never edit another member's file (comment on their PR instead).
2. **Every class runs standalone** via its `main()` demo: `mvn exec:java -Dexec.mainClass=<your.Class>`.
3. **Commits are evidence** (need ≥15 total, all members, across days): `feat(catalog): TreeMap delete-root + inorder demo`, not `update`.
4. **Each task ships 4 things:** code + test IDs + 1-page trace in `TracePacket/` + your Template I row (files, commit links, defense Qs).
5. **Report ties to code:** every Big-O row and benchmark number must name the real class/method it measures.
