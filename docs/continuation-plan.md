# Continuation Plan

This note distills the next steps for the MSC2 Clojure port after reviewing
`HANDOFF.md`, `CLOJURE_PORT.md`, `CLOJURE_PORT_PLAN.md`, and
`docs/msc2-latex/main.tex`. It focuses on closing the parity gap for the
conditional-discrimination experiment (Exp 3) while keeping the broader
milestone sequence intact.

## 1. Context Recap

- **Architecture.** MSC2 enforces the AIKR constraint via a bounded cycle
  (`docs/msc2-latex/main.tex`) composed of FIFO sequencing, inference, decision,
  and forgetting passes. The Clojure reducer (`src/msc2/core.clj`) already hosts
  the shell of this loop.
- **Port roadmap.** `CLOJURE_PORT.md` and `CLOJURE_PORT_PLAN.md` prescribe the
  module order: truth/stamps → Narsese → FIFO/events → induction/deduction →
  decision/attention → shell tooling.
- **Current state.** Experiments 1 and 2 match the C reference; Experiment 3
  stalls because the FIFO builder only emits two-step sequences, preventing
  `<((sample &/ comparison) &/ ^op) =/> G>` implications (`HANDOFF.md`).

## 2. Reference Mapping

| Clojure focus | C source of truth | Notes |
| --- | --- | --- |
| `src/msc2/fifo.clj` | `external/msc2/src/FIFO.c` | Bitmask-based cached sequences up to `MAX_SEQUENCE_LEN`. |
| `src/msc2/core.clj` (`sequence-*`) | `external/msc2/src/Cycle.c` (`Cycle_ProcessInputBeliefEvents`) | Filters out sequences containing ops before the postcondition window. |
| `src/msc2/inference.clj` | `external/msc2/src/Inference.c` | `Inference_BeliefIntersection` and `Inference_BeliefInduction` drive sequence events + implications. |
| `src/msc2/experiments/exp3.clj` | `external/msc2/experiments/` | Harness structure matches the Python driver; accuracy plateaus until longer sequences exist. |

## 3. Execution Steps

1. **Document current reducer state.** Capture the data-flow of `src/msc2/core.clj`
   (queues, FIFO cache, anticipation) so upcoming edits have a baseline.
2. **Rebuild the sequence cache.**
   - Mirror the bitmask iteration from `FIFO_Add` to generate all left-nested
     sequences up to `max-sequence-length`.
   - Track occurrence-time offsets and terminate when an intermediate element is
     an operation (C’s operation guard in `Cycle_ProcessInputBeliefEvents`).
   - Add property tests that feed synthetic FIFO streams and compare with the C
     implementation for length-3 cases.
3. **Extend induction and memory recording.**
   - Ensure `sequence-derivations` threads the deeper sequences into
     `inference/belief-induction` with correct `:occurrence-time-offset`.
   - Update `memory/record-derived` (and concept tables) so nested antecedents
     land in the right prediction slots.
4. **Regression harnesses.**
   - Build `.nal` fixtures under `experiments/micro/` that emulate Exp 3 trials.
   - Script a diff runner that feeds each fixture through both shells
     (`clj -M -m msc2.shell` vs. `./external/msc2/NAR shell`).
5. **Experiment 3 verification.**
   - Rerun `clj -M -m msc2.experiments.exp3` once long sequences fire.
   - Compare `exp3_trials.csv`/`exp3_truths.csv` with the C reference outputs,
     flagging deviations >0.01 as suggested in `HANDOFF.md`.
6. **Automation & docs.**
   - Wire the new micro harness into `clj -M:test`.
   - Update `HANDOFF.md` (or a follow-up log) with parity results and any
     intentional divergences.

## 4. Validation Strategy

- **Unit coverage.** Tests for truth, stamps, FIFO cache, and sequence builders
  stay under `test/msc2`. Add property checks for occurrence-time offsets and
  stamp merges.
- **Parity checks.** Every `.nal` fixture should run through both the C and
  Clojure shells with diffing scripts; regressions halt the pipeline before
  full experiments.
- **Experiment baselines.** After each calculus change, regenerate the CSV
  baselines in `experiments/` so reviewers can eyeball trends without rerunning
  the harness.

Following this plan tackles the missing sequence depth for Exp 3 while keeping
the rest of the MSC2 milestones aligned with the existing documentation.
