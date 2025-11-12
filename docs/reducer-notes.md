# Reducer & FIFO Notes

Snapshot of the current Clojure reducer after wiring the bitmask-style sequence
builder described in `docs/continuation-plan.md` Step 2.

## State Map Layout

- `:config` — knobs from `msc2.core/default-config` (queue capacities, decay
  factors, induction gap).
- `:queues` — belief and goal priority queues created via `msc2.queue/empty-queue`
  (`src/msc2/core.clj:52-71`). Events enter through `enqueue-input`, decay via
  `decay-queues`, and feed into decision logic later in the cycle.
- `:fifo` — sliding buffer built by `msc2.fifo/empty-buffer`. `fifo/enqueue`
  returns both the updated buffer and raw induction pairs (`src/msc2/fifo.clj`).
- `:derived` / `:predictions` — log of derived implications and generated belief
  predictions (populated inside `record-deriveds` and `produce-predictions`).
- `:anticipations` — pending consequences produced in decision handling
  (resolved in `resolve-anticipations`, expired in `expire-anticipations`).

## FIFO & Sequence Pipeline

1. Incoming belief events are appended to the FIFO via `fifo/enqueue`. The helper
   returns `{ :fifo updated :pairs valid-pairs }`, where each pair preserves the
   original ordering constraint (`earlier` occurrence time < `later`).
2. `sequence-derivations` converts the FIFO history into antecedent candidates:
   - Pulls `prior` events from the FIFO (excluding the newest element) and
     reverses them so index `0` corresponds to the most recent belief.
   - `sequence-candidates` iterates over the C-style bitmask states (those with
     the least-significant bit set) to pick offset lists, mirroring how
     `FIFO_Add` caches left-nested `(&/, …)` chains in the C reference.
   - Each pattern yields a nested sequence only when every component is a belief
     event and all but the tail are non-operations, matching the guard in
     `Cycle_ProcessInputBeliefEvents`.
   - Candidates whose earliest occurrence falls outside `:max-induction-gap`
     relative to the current event are discarded.
3. For each candidate within the gap, `inference/belief-induction` (wrapping
   `Truth_Projection + Truth_Induction` and stamp merging) emits a predictive
   implication. `record-deriveds` stores the implication in concept memory and
   appends it to `:derived`.

## Current Guarantees & Follow-ups

- Bitmask offsets reproduce the combinations produced by `FIFO_Add` (including
  gaps between events), so length-3 preconditions such as
  `<((sample &/ comparison) &/ ^op) …>` now appear.
- Operations are only allowed as the newest component of a sequence, matching
  the C guard that skips sequences with internal `^op`.
- Combined sequence events inherit the earliest occurrence time (the first
  offset in the pattern), keeping `dt` calculations consistent with existing
  induction logic.
- Remaining follow-up items live in `docs/continuation-plan.md` (regression
  harnesses, Exp 3 re-run, and parity logging).
