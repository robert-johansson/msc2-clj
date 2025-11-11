# MSC2 Documentation Index

This directory stores reference material related to the MSC2 C implementation and its Clojure port. Suggested structure:

1. **architecture.md** – high-level overview of MSC2’s control loop, module boundaries (`Cycle`, `Memory`, `Inference`, etc.), and how sensorimotor reasoning is staged per cycle.
2. **truth_calculus.md** – detailed explanation of `Truth.c`, including expectation, projection, revision, induction/deduction math, and how frequency/confidence evolve.
3. **data_model.md** – documentation of core structures (`Term`, `Event`, `Implication`, `Concept`, `Stamp`, `FIFO`) and their key fields.
4. **control_flow.md** – annotated walk-through of `Cycle.c`, `Decision.c`, and `Memory.c` showing how input events move through FIFO, concept lookup, implication tables, and decision making.
5. **experiments.md** – summary of the operant-conditioning experiments (1–4) plus any new scenarios, linking to the scripts under `external/msc2/experiments/`.
6. **build_and_tooling.md** – notes on `build.sh`, platform quirks (e.g., SSE flags on arm64), and common developer commands for MSC2.

The existing PDF references (`2405.19498.pdf`, `Hammer 2022.pdf`) back the theoretical context; link to them from the markdown files where relevant.
