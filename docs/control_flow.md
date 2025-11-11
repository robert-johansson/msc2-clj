# Control Flow

This note walks through a single MSC2 cycle, highlighting the functions in `src/Cycle.c`, `src/Memory.c`, `src/Inference.c`, and `src/Decision.c`.

## 1. Input Ingestion
1. `NAR_AddInputNarsese` (shell or API) parses Narsese into `(Term, punctuation)`.
2. `NAR_AddInput` dispatches to `Memory_AddInputEvent`, which:
   - Wraps the term into an `Event` (belief or goal based on punctuation).
   - Pushes it into the belief or goal cycling queue.
   - Initializes stamp/truth per `Narsese_PunctuationToTruth`.

## 2. Cycle Tick (`Cycle_Perform`)

### a. Process Input Beliefs
- `Cycle_ProcessInputBeliefEvents` dequeues fresh beliefs, sends them through:
  - `FIFO_Add` (sequence buffer) → yields pairings for induction.
  - `Memory_ProcessNewBeliefEvent` updates the owning concept, triggers `Inference_BeliefInduction` and `Table_AddAndRevise`.

### b. Process Cyclers
- `Cycle_ProcessRetrievedBeliefEvents` pulls scheduled predictions/goals from priority queues.
- `Cycle_GoalSequenceDecomposition` breaks complex goals into subgoals.
- `Memory_addCyclingEvent` requeues derived events with decayed priority.

### c. Inference & Decision
- `Inference_BeliefDeduction` predicts future beliefs; results re-enter the cycling queue.
- `Inference_GoalDeduction` attempts to satisfy goals using existing implications.
- `Decision_BestCandidate` aggregates candidate operations:
  - Computes desire = expectation(goal) × confidence(rule).
  - Applies `DECISION_THRESHOLD`; if unmet, `MotorBabble` may trigger random ops.
- `Decision_Execute` enqueues the chosen operation, creates anticipation records (assumption of failure), and logs the pending execution in concept memory.

### d. Feedback Handling
- When environment sends `G.` (success) or `G. {0.0 0.9}` (failure), `Decision_Anticipate`/`Decision_AnticipationApply` revise the responsible implications. Negative feedback immediately lowers frequency; positive feedback raises it.

### e. Forgetting & Attention
- `Cycle_RelativeForgetting` gradually lowers concept priority and trims old events.
- `Usage_decay` keeps “sleepy” concepts from dominating the PQs.

## 3. Output & Logging
- The shell prints `Input:`, `Derived:`, `^op` (executions), and `Performed n inference steps` per tick.
- Commands such as `*concepts`, `*stats`, `*babblingops=` are handled in `Shell.c` and allow introspection of PQs and truth tables.

This loop repeats every time `Cycle_Perform` is called (either automatically from the shell or manually via the API). The Clojure port mirrors this flow to guarantee behavioural parity with MSC2.***
