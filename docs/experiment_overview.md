# Experiments 1–3 Overview

This summary distills the three operant-conditioning experiments from `docs/2405.19498.pdf`, highlighting task goals, procedure, and key findings. Each experiment uses OpenNARS for Applications (ONA) restricted to sensorimotor reasoning (`SEMANTIC_INFERENCE_NAL_LEVEL=0`). At the start of every run the agent receives:

- `*babblingops=2`, `*motorbabbling=0.9` to enable two operators (`^left`, `^right`) with 90 % babbling chance.
- `*setopname 1 ^left`, `*setopname 2 ^right`, and `*volume=100`.
- Stimulus encoding: `<A1 --> [sample]>. :|:`, `<B1 --> [left]>. :|:`, `<B2 --> [right]>. :|:`, followed by `G! :|:` to trigger an action; feedback is `G. :|:` for reinforcement or `G. :|: {0.0 0.9}` for punishment, with 100 idle cycles between trials.

## Experiment 1 – Simple Discrimination
- **Goal:** Show that ONA can acquire a stable mapping between stimuli and motor responses via reinforcement.
- **Design:** Three phases of three blocks (12 randomized trials per block). During baseline no feedback is provided; training delivers reinforcing/punishing `G.` based on whether the executed op matches the sample’s side; testing repeats baseline conditions to check retention without feedback.
- **Outcome:** Accuracy jumped from ≤ 50 % during baseline to 100 % by the second training block and stayed perfect throughout testing. Confidence for the learned rules `<(<A1 --> [left]> &/ ^left) =/> G>` and `<(<A1 --> [right]> &/ ^right) =/> G>` rose from 0.56 to 0.82, indicating strong belief in the learned contingencies.

## Experiment 2 – Changing Contingencies
- **Goal:** Probe whether the agent can unlearn and relearn when reward contingencies are reversed mid-task.
- **Design:** Five phases (baseline × 2 blocks, Training1 × 4, Testing1 × 2, Training2 × 4 with reversed contingencies, Testing2 × 2). Training1 rewards `^left` when A1 is on the left and `^right` when A1 is on the right; Training2 swaps the mapping so the agent must respond to A2’s position instead.
- **Outcome:** The system again reached 100 % accuracy during Training1 and Testing1. After reversal, accuracy dipped but climbed to 75 % by the end of Training2 and 91.7 % in Testing2. Revision dynamics captured the shift: the frequency of the old hypothesis `<(<A1 --> [left]> &/ ^left) =/> G>` fell from ~1.0 to 0.74 once negative evidence arrived, while the new hypothesis `<(<A2 --> [right]> &/ ^right) =/> G>` gained support. This demonstrates real-time adaptation using negative reinforcement and hypothesis revision.

## Experiment 3 – Conditional Discrimination
- **Goal:** Test whether ONA can handle conditional (sample-dependent) discriminations where cues must be combined before acting.
- **Design:** Baseline (3 blocks), Training (6 blocks with feedback), Testing (3 blocks without feedback). Each trial specifies a “sample” (e.g., A1 vs. A2) and positions for B1/B2; reinforcement depends on executing the operation that matches the left/right position associated with the current sample (e.g., execute `^left` when A1 is the sample and B1 is on the left).
- **Outcome:** Baseline accuracy hovered around chance, but during the extended training the agent learned to associate conditional cues, and testing maintained high accuracy without feedback. The learned hypotheses combine sample, comparison, and executed operator (e.g., `<((<A2 --> [sample]> &/ <B2 --> [right]>) &/ ^right) =/> G>`), showing that ONA can build multi-step temporal implications needed for conditional discriminations.

## Takeaways
- ONA’s sensorimotor reasoning loop can express classic operant conditioning phenomena: acquisition, extinction/reversal, and conditional discriminations.
- Motor babbling jump-starts exploration, while reinforcement (`G.`) and punishment (`G. {0.0 0.9}`) drive hypothesis confidence and frequency updates.
- Experiment 2 highlights the importance of revision to integrate conflicting evidence, and Experiment 3 confirms that ONA can bind sample/context cues before making decisions—critical for more complex AGI tasks.
