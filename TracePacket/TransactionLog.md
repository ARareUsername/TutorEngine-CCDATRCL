# Trace Packet — LinkedList (`TransactionLog`)
**Owner:** De Jesus, Aeon Miles J. — History & Intake
**Source:** `tutorengine/model/TransactionLog.java` (mirrors the TRACE comment above `main()`)
**Spec:** S7 Linked List, S11 list traversal · **Tests:** T01–T03
**Run:** `mvn -q exec:java -Dexec.mainClass=tutorengine.model.TransactionLog`

Real data (all three SKUs exist in `Dataset/cards.csv`):

| Tag | Entry | CSV evidence |
|---|---|---|
| E1 | `INTAKE MTG-TSP-157` | line 3 — Fury Sliver |
| E2 | `DELETE MTG-UGIN-146` | line 12 — Wildcall |
| E3 | `INTAKE MTG-WOC-129` | line 35 — Paradise Druid |

## Step-by-step trace

| Step | Operation | entries (head → tail) | head | tail | Returns / output |
|---|---|---|---|---|---|
| 0 | start | `[]` | – | – | – |
| 1 | `log(E1)` — `addLast` | `[E1]` | 1 | 1 | void |
| 2 | `log(E2)` — `addLast` | `[E1, E2]` | 1 | 2 | void |
| 3 | `log(E3)` — `addLast` | `[E1, E2, E3]` | 1 | 3 | void |
| 4 | `printAll()` — for-each traversal | `[E1, E2, E3]` | 1 | 3 | `1. E1` / `2. E2` / `3. E3` |
| 5 | `undoLast()` — `removeLast` | `[E1, E2]` | 1 | 2 | **`E3`** |
| 6 | `undoLast()` on emptied log | `[]` | – | – | `null` — safe, never throws |

## Notes
- Step 5 relinked the **tail** only — E1 and E2 never moved.
- `addLast`/`removeLast` are O(1) on a doubly linked list. An ArrayList removal at the head
  shifts every remaining element (O(n)) and its growth/shrink copies arrays; the diary only
  touches its two ends, so LinkedList costs the same no matter how long the log grows.
- The demo then calls `all().clear()` and `undoLast()` again to show the empty case printing
  `log is empty, nothing to undo` (message lives in `main()`, not in the method).
- Undo deletes the newest record only — it does not reverse what the entry described.
