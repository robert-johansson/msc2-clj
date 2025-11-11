# Using `.nal` Scripts to Probe MSC2

As we reverse-engineer the MSC2 C implementation and mirror it in Clojure, small `.nal` files are invaluable. Each script exercises a single behaviour (induction, recall, decision, concept memory) and can be replayed via the MSC2 shell. This document outlines the strategy and the tools we use.

## Running Scripts

From `external/msc2`:

```bash
./build.sh                # ensure NAR is rebuilt
./NAR shell < tests/foo.nal
```

The shell prints every `Input`, `Derived`, and `^op` execution, plus any diagnostics (e.g., `decision expectation=` lines).

## Why `.nal` files help

- **Deterministic probes:** Each file resets the system, sets operator names, and feeds a known sequence. Outputs are stable, so divergences in the Clojure port are easy to spot.
- **Focused coverage:** We can isolate specific mechanisms (e.g., motor babbling, goal deduction, concept listing) without running the full Pong harness.
- **Portable regression tests:** The same `.nal` content can drive the future Clojure shell, giving us side-by-side comparisons.

## Inspecting State with `*` Commands

Within a `.nal` script you can issue shell commands:

- `*reset` – clear all concepts/events.
- `*volume=100` – turn on verbose derivation printing.
- `*motorbabbling=0.9` – control exploration rate.
- `*setopname 1 ^left` – bind operation slots to names.
- `*concepts` – dump every concept and its stored implications (`tests/memory.nal` shows the format).
- `*stats` – show queue sizes, priorities, etc.

These commands are the quickest way to peek into MSC2’s internal memory without attaching a debugger.

## Sample Scripts (`external/msc2/tests/`)

- `simple_implication.nal`: asserts `A.` then `B.`, asks `<A =/> B>?` so we can see the induced rule.
- `identity_recall_right.nal`: motor babbles `^right` once on `A1/B1` cues, then replays the cues to verify recall without feedback.
- `pong_ball_right.nal`: mimics a Pong observation (`ball_right.`), reinforces `^right`, and checks the predictive decision later.
- `memory.nal`: inputs a few atomic beliefs then runs `*concepts` to inspect the concept table.

Feel free to add more `.nal` files as you explore new behaviours (goal chains, forgetting, anticipation). Keep them small and focused; our goal is to build a library of “micros” that make MSC2’s black box transparent and double as regression tests once the Clojure port is ready.***
