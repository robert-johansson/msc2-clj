# Handoff – MSC2 Clojure Port

## Current Status
- Experiments 1 & 2 remain stable and match the C reference.
- Experiment 3 now logs both the four long contingencies and the short cue-only rule `<((<B1 --> [right]> &/ <B2 --> [left]>) &/ ^right) =/> G>` (see `docs/exp3-right-contingency.md` for full details).
- Sequence spikes are recorded in concept memory, so decision preconditions can match the entire `(sample &/ comparison)` chain.
- The decision threshold is aligned with MSC2 C (`:decision-threshold 0.501` in `src/msc2/core.clj`).

## Key Findings
1. **A1-B1-right never learns:** In the current run (seed 42) the system never executes `^right` during training/testing when the `A1/B1-right` cue is presented, so `<((A1 &/ B1 right) &/ ^right) =/> G>` remains at zero expectation.
2. **Short rule also absent:** The cue-only rule `<((<B1 --> [right]> &/ <B2 --> [left]>) &/ ^right) =/> G>` never receives positive evidence either (its CSV entries stay `(0.0, 0.0)`), so punishers have nothing to demote.
3. **All positive `^right` actions come from `A2-B2-right` trials**, reinforcing the `A2` contingency but not the `A1` one.
4. The markdown analysis in `docs/exp3-right-contingency.md` summarises the investigation, proposed fixes, and open questions.

## Open Questions / Next Steps
1. **Force exploration for `A1-B1-right`:** ensure at least a handful of training trials execute `^right` in that context (either by scripting the action, lowering the threshold temporarily, or adding an ε-greedy branch).
2. **Stimulus ordering:** verify whether showing `B2-left` right before `G!` is causing the wrong sequence to be freshest; consider swapping the order or inserting delays.
3. **Parity check:** use `scripts/micro_exp3_diff.sh` (already exists) to diff concept tables in addition to derived lines so we can see when short rules appear/disappear relative to the C baseline.
4. **Decision logging:** optionally extend the shell to print the actual precondition term (now that sequences are recorded) whenever a learned decision fires; this will help confirm which rule is active per trial.

## Files / Artifacts to Know About
- `docs/exp3-right-contingency.md` – investigation summary + potential fixes.
- `experiments/exp3_trials.csv`, `experiments/exp3_truths.csv` – latest run output (with the extra short rule column).
- `scripts/micro_exp3_diff.sh` – compares derived lines between C and Clojure shells for micro fixtures.
- `external/msc2/tests/simple_implication6.nal` – reproducible C case demonstrating punish behavior (mirrored successfully in Clojure).

Feel free to reach out if you need the full command logs or REPL snippets used above. Good luck on the next session!***
