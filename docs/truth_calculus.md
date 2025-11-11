# Truth Calculus in MSC2

MSC2 uses the NARS truth representation: each statement is annotated with `frequency f ∈ [0,1]` (success ratio) and `confidence c ∈ [0,1]` (how much evidence we have). Expectation `E = f * (c + k)` (see `Truth_Expectation`) maps the pair into a decision-friendly scalar. The implementation lives in `src/Truth.c`.

## Core Operations

| Operation | Definition (refer to `Truth.c`) | Intuition |
|-----------|--------------------------------|-----------|
| **Projection** (`Truth_Projection`) | Applies temporal decay using `PROJECTION_DECAY` and the time delta. | Predictions made far in advance lose confidence. |
| **Revision** (`Truth_Revision`) | Combines two pieces of evidence with weights proportional to confidence / (1−confidence). | Merges positive + negative feedback, increasing confidence while adjusting frequency. |
| **Deduction** (`Truth_Deduction`) | `f'=f1 * f2`, `c' = f1*c1*c2` (simplified). | Chains evidence from condition and rule. |
| **Induction** (`Truth_Induction`) | Inverse of deduction: `f'=f2`, `c' = c1*c2*t_induction`. | Build a new implication when antecedent and consequent co-occur. |
| **Abduction / Exemplification** | Variants for symmetric reasoning; MSC2 mainly uses induction + deduction. |
| **Expectation** (`Truth_Expectation`) | Converts `(f,c)` to a scalar for decision thresholds. | Used heavily in `Decision_BestCandidate`. |

All functions obey the NARS axioms: confidence grows with evidence, frequency tends toward the empirical success ratio, and the operations are associative/commutative in the sense required by Non-Axiomatic Logic.

## Stamps & Evidential Overlap

Before combining truths, MSC2 checks whether the evidential bases overlap (`Stamp_CheckOverlap`). Stamps hold a fixed-size array of source IDs plus occurrence times. Overlap detection prevents double counting the same evidence in revision or induction.

## Anticipation & Negative Evidence

When MSC2 executes an operation, it immediately inserts an “assumption of failure” event (`Decision_Anticipate`). That injects negative evidence (frequency=0) until the environment confirms success with `G.`. This mechanism ensures confidence drops quickly when contingencies change (as seen in Experiment 2).

## Parameters & Constants

`src/Config.h` defines:
- `TRUTH_EPSILON`: default confidence for a single observation (≈0.01–0.2).
- `REVISION_WEIGHT`: controls how strongly new evidence overrides old.
- `PROJECTION_DECAY`, `EVENT_DURABILITY`: discount factors for temporal reasoning.
- `DECISION_THRESHOLD`, `MOTOR_BABBLING_CHANCE`: interplay between expectation and random exploration.

Tuning these parameters drives how fast hypotheses rise/fall, which is critical for matching the C reference during the Clojure port.***
