# Exp3 Right-Hand Contingency Investigation

## 1. What We Observed
- Four target rules (`<((A? &/ B? left/right) &/ ^left/^right) =/> G>`) behave as expected for left cues, but `<((A1 &/ B1 right) &/ ^right) =/> G>` stays at `freq=0, conf=0` from baseline through testing (`experiments/exp3_truths.csv`).
- `A1-B1-right` trials recorded in `experiments/exp3_trials.csv` show 54 occurrences: the only `^right` executions happened in Baseline (4 total), while every Training/Training2/Testing trial logs `executed,^left` (49 instances) or blank (5 baseline rows). In other words, once feedback starts, the agent **never executes `^right`** when the `A1/B1 right` cue is present.
- All positive `^right` actions during Training/Testing occur on `A2-B2-right` trials (44 correct executions). These reinforce the `A2` rule, but leave the `A1/B1 right` rule without positive evidence.
- Decision logs showed a rule with antecedent `B1-right → B2-left` firing repeatedly. After adding that implication to `target-terms`, the CSV still shows `(freq=0, conf=0)` for it—confirming that, despite the decision trace, the rule never actually makes it into concept memory with non-zero truth.
- Punishers (`G. :|: {0.0 0.9}`) are applied only when an implication exists. In Exp3 the problematic rule never forms, so there is nothing to punish or demote for that context.

## 2. Root Cause Hypothesis
1. **Motor babbling never explores `^right` in the `A1-B1-right` context once feedback is on.** During Baseline the babbling branch does fire and we see four correct `^right` actions, but Baseline provides no feedback, so these successes don’t enter concept memory.
2. **Because the first few Training trials happen to execute `^left`, the left rule crosses the decision threshold immediately.** The default threshold was 0.5 (now 0.501). Once a rule exceeds it, `msc2.decision/evaluate` always picks that rule, bypassing motor babbling entirely.
3. **No positive evidence ever enters the system for `<((A1 &/ B1 right) &/ ^right) =/> G>`,** so this rule never exists. The shorter `B1-right → ?` sequence also never receives positive reinforcement for the same reason.
4. **Punishers can only demote rules that exist.** Since neither the short nor the long `^right` rule for `A1/B1` is ever stored, negative feedback on those trials doesn’t affect any `^right` implication.
5. **Meanwhile the `A2-B2-right` rule does see positive evidence,** because those trials actually execute `^right`. Hence we observe 44 correct `^right` executions, reinforcing only the `A2` contingency and leaving the `A1` one empty.

## 3. Supporting Evidence
- `experiments/exp3_trials.csv`: all `A1-B1-right` rows after Baseline show `executed,^left`.
- `experiments/exp3_truths.csv`: the short rule `<((<B1 --> [right]> &/ <B2 --> [left]>) &/ ^right) =/> G>` now logs per block, and every entry remains `(0.0, 0.0)`.
- Running `external/msc2/tests/simple_implication6.nal` through both C and Clojure ports confirms that punishers *do* demote all matching implications when they exist; the difference in Exp3 is that the relevant implication never forms.

## 4. Potential Fixes / Next Steps
1. **Force exploration for `A1-B1-right` during early Training.** Options:
   - Temporarily lower/increase the decision threshold (or add an ε-greedy exploration clause) so motor babbling keeps firing even when the first rule appears.
   - Script the first few training trials to execute `^right` explicitly, ensuring at least one positive `A1/B1` sample makes it into memory.
2. **Adjust stimuli ordering** so `B1-right` remains the most recent comparison when `G!` is sent (currently we show `B2-left` right before the goal, which biases the learned antecedent toward the distractor).
3. **Extend `target-terms` logging permanently** (already done) to include this cue-only sequence so we can keep tracking its truth values.
4. **Investigate decision preconditions**: although `term/precondition-term` now preserves sequences, the decision logs still suggest we’re matching only on the comparison cue. Double-check that sequence spikes are recorded with enough occurrences so the proper antecedent is actually available.
5. **Compare with the C harness** by running the same `.nal` scripts through both shells and diffing the concept tables (`scripts/micro_exp3_diff.sh` currently diff derived lines only; we may need to extend it to log truth tables).

## 5. Open Questions
- Why do `A1-B1-right` trials always show `^left` after Baseline? Is the belief spike for the correct sequence missing when decisions are made (i.e., the FIFO always sees `B2-left` as the most recent cue)?
- Should we be pushing sequence spikes into concept memory earlier (before `G!`), so the correct precondition is available when the decision runs?
- Would logging the belief-spike timestamps show that the wrong precondition is repeatedly fresher than the right one (because of stimulus ordering)?

## 6. Summary
The controller isn’t "ignoring punishers"—it never learns the `A1-B1-right` rule at all. All reinforcement for `^right` comes from the `A2-B2-right` stimulus, so the only `^right` rule we store is the one for that context. Fixing this requires ensuring at least one positive `A1-B1-right` → `^right` example occurs during Training (before the left rule shuts off exploration) and/or adjusting the stimulus sequence so the correct antecedent is actually present when `G!` is processed.
