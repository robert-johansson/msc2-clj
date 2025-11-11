# MSC2 Operant Experiments

The reference experiments validate the MSC2 C implementation and serve as regression suites for the Clojure port. Scripts live under `external/msc2/experiments/`.

## Experiment 1 – Simple Discrimination
- **Phases:** Baseline ×3 blocks (no feedback), Training ×3 (feedback), Testing ×3 (no feedback).
- **Trials:** Two cues (A1 left/right) ×6 per block; 12 trials/block.
- **Target hypotheses:** `<(<A1 --> [left]> &/ ^left) =/> G>` and `<(<A1 --> [right]> &/ ^right) =/> G>`.
- **Outcome:** Accuracy jumps to 100% by Training block 2 and stays perfect in Testing; confidence climbs from ~0.2 to ~0.8 (see `exp1_truths.csv`).

## Experiment 2 – Changing Contingencies
- **Phases:** Baseline ×2, Training1 ×4 (A1 cues), Testing1 ×2, Training2 ×4 (switch to A2 cues), Testing2 ×2.
- **Behavior:** After the contingency flip, previously confident rules lose frequency (~0.65) while the new A2 rules rise from 0 to ~0.98, demonstrating revision under negative evidence (`exp2_truths.csv`).

## Experiment 3 – Conditional Discrimination
- **Phases:** Baseline ×3, Training ×6, Testing ×3.
- **Trials:** Sample A1/A2 + comparisons B1/B2; reward requires matching the operator to the comparison linked with the current sample.
- **Target hypotheses:** Four conditional rules combining sample + comparison + operation.
- **Result:** Accuracy exceeds 75% by Training block 6 and reaches 100% in Testing; confidence rises from ~0.13 to ~0.7 (`exp3_truths.csv`).

## Experiment 4 – Generalized Identity Matching
- **Phases:** Baseline ×3, Training ×6, Testing ×3, **Generalization ×3** (novel X1/X2 stimuli without prior feedback).
- **Hypotheses:** Four concrete identity rules plus two generalized schemas using `#1`.
- **Observation:** The generalized terms reach ~1.0 frequency / 0.87 confidence during training/testing, enabling perfect transfer to the X1/X2 trials (`exp4_truths.csv`).

### CSV Outputs
Each script emits:
- `expN_trials.csv`: per-trial phase/block, stimulus, executed op, reward flag.
- `expN_truths.csv`: block-level frequency/confidence snapshots for the monitored hypotheses.

Use these logs to verify behavioural parity between the C and Clojure implementations.***
