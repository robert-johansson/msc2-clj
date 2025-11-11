# MSC2 Architecture

MSC2 (“Minimal Sensorimotor Component 2”) is the sensorimotor half of OpenNARS for Applications. It keeps the NAL‑6..8 inferential machinery (temporal sequence, procedural links, decision making) and drops the higher semantic layers. Everything revolves around an event-driven control loop that ingests sensorimotor events, mines temporal implications, and decides which operation to execute next.

## Top-Level Components

| Component | Key Files | Responsibilities |
|-----------|-----------|------------------|
| `NAR` API | `src/NAR.c`, `src/main.c`, `src/Shell.c` | Initializes global state, parses Narsese input, exposes the REPL/shell and batch interfaces. |
| Control Loop | `src/Cycle.c` | Pulls events from the cycling queues, updates FIFO memory, triggers inference, decision, and forgetting each tick. |
| Memory/Concepts | `src/Memory.c`, `src/Concept.h`, `src/Table.c`, `src/PriorityQueue.c`, `src/Usage.c`, `src/InvertedAtomIndex.c` | Stores concepts keyed by their term. Each concept carries belief spikes, goal spikes, implication tables, usage/priorities, and links into attention queues. |
| FIFO / Sequencer | `src/FIFO.c` | Maintains short event sequences per evidence channel; source for temporal induction. |
| Inference | `src/Inference.c`, `src/Decision.c` | Implements induction, deduction, revision, goal decomposition, and desire computation. `Decision.c` houses motor babbling and action selection. |
| Truth Logic | `src/Truth.c`, `src/Stamp.c` | Implements truth representation, expectation/projection math, and evidential overlap checking. |
| Data Model | `src/Term.c`, `src/Event.c`, `src/Implication.h`, `src/Narsese.c` | Defines the binary tree term format, event record, implication payload, and parser/serializer. |
| Utilities | `src/Globals.c`, `src/HashTable.c`, `src/Stack.c`, `src/Config.h` | RNG, assertions, object pools, global parameters, and lightweight containers. |

## Event Flow Overview

1. **Input:** `NAR_AddInputNarsese` parses a sentence into a `Term` + punctuation; `Memory_AddInputEvent` enqueues it as belief or goal.
2. **FIFO Update:** `Cycle_ProcessInputBeliefEvents` feeds the FIFO sequencer with recent events so temporal induction has context.
3. **Induction:** For each concept touched, `Inference_BeliefInduction` mines new implications and stores them in precondition tables (`Table_AddAndRevise`).
4. **Prediction/Subgoaling:** `Inference_BeliefDeduction` and `Inference_GoalDeduction` derive predictions or subgoals, adding them back as cycling events.
5. **Decision:** `Decision_BestCandidate` evaluates available operations using desire (expectation × goal truth). If nothing crosses the threshold, motor babbling picks an op at random.
6. **Execution Feedback:** When an operation is executed, MSC2 anticipates failure (assumption of failure) and revises the responsible implications once feedback arrives (positive or negative).
7. **Forgetting/Attention:** `Cycle_RelativeForgetting` and priority queues make sure bounded buffers drop low-utility concepts/events.

## Attention & Resources

- **Cycling Queues:** Two priority queues hold pending belief events and goal events; each element stores priority, occurrence time, and a pointer to the `Event`.
- **Concept Priority:** Concepts sit in another PQ ordered by `Concept.priority` and `Concept.usefulness`. Usage counters are updated via `Usage_use` so active concepts stay “hot”.
- **Table Limits:** Each concept owns per-operation implication tables (bounded by `TABLE_SIZE`); entries are revised/replaced in place.

## Procedural Knowledge Representation

A learned rule looks like `(<(A &/ ^op) =/> G)`, i.e., a temporal implication whose antecedent is a sequence of cues plus an operation, and whose consequent is the desired goal. Truth values capture the observed success rate (frequency) and confidence (amount of evidence). MSC2 keeps both concrete hypotheses (e.g., `<(<A1 --> [left]> &/ ^left) =/> G>`) and generalized ones (using variables such as `#1`) so that identity or substitution tasks can be covered.

## Interaction Points

- **Shell Commands:** `*reset`, `*stats`, `*concepts`, `*babblingops=`, `*motorbabbling=`, `*setopname n ^op`, etc., let developers inspect and tweak the runtime. `experiments/*.py` rely on these to orchestrate trials.
- **Compiled Binaries:** `build.sh` produces `NAR` (interactive shell). `libbuild.sh` can build shared/static libraries plus headers under `/usr/local/include/ona/`.

MSC2’s architecture intentionally mirrors the diagram from “OpenNARS for Applications: Architecture and Control”: FIFO (sequencer) → cycling queues → concept memory, wrapped by inference, decision, and attention. This modular structure is what we re-express in the Clojure port.***
