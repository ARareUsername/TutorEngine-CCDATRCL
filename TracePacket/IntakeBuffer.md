# Trace Packet — Queue + Stack (`IntakeBuffer`)
**Owner:** De Jesus, Aeon Miles J. — History & Intake
**Source:** `tutorengine/intake/IntakeBuffer.java` (mirrors the TRACE comment above `main()`)
**Spec:** S7 Stack/Queue, S11 stack-or-queue op · **Tests:** T101–T103 (queue/stack coverage)
**Run:** `mvn -q exec:java -Dexec.mainClass=tutorengine.intake.IntakeBuffer`

Real data (all three from `Dataset/cards.csv`):

| Tag | Card | SKU | CSV line |
|---|---|---|---|
| D | Bird | `MTG-TOTJ-7` | 33 |
| E | Temporal Manipulation | `MTG-SLD-1169` | 54 |
| F | Gorion, Wise Mentor | `MTG-CLB-276` | 59 |

## Step-by-step trace (queue state and undo-stack state after every operation)

| Step | Operation | queue (front → back) | undo (top → bottom) | pending | Returns |
|---|---|---|---|---|---|
| 0 | start | `[]` | `[]` | 0 | – |
| 1 | `stage(D)` — `offer` | `[D]` | `[]` | 1 | void |
| 2 | `stage(E)` | `[D, E]` | `[]` | 2 | void |
| 3 | `stage(F)` | `[D, E, F]` | `[]` | 3 | void |
| 4 | `processNext()` — `poll` | `[E, F]` | `[D]` | 2 | **D** ← FIFO |
| 5 | `processNext()` | `[F]` | `[E, D]` | 1 | **E** |
| 6 | `undo()` — `pop` | `[F]` | `[D]` | 1 | **E** ← LIFO |
| 7 | `processNext()` | `[]` | `[F, D]` | 0 | **F** |
| 8 | `processNext()` empty | `[]` | `[F, D]` | 0 | `null` (silent) |
| 9 | `undo()` | `[]` | `[D]` | 0 | **F** |
| 10 | `undo()` | `[]` | `[]` | 0 | **D** |
| 11 | `undo()` empty | `[]` | `[]` | 0 | `null` (silent) |

## Notes
- Queue out: **D, E, F** (first in, first out — step 4 `poll`). Undo lane out: **E, F, D**
  (last in, first out — step 6 `pop`).
- A misread card returns at step 6 while D is still buried under it, so it can be corrected and
  staged again without touching the waiting line.
- One `ArrayDeque` does both jobs O(1): `offer`/`poll` at one end = queue, `push`/`pop` at the
  same end = stack; no elements are ever shifted.
- Cross-class link: every `processNext()` also writes `INTAKE <SKU>` to the `TransactionLog`
  diary (steps 4, 5, 7).
