# MSC2 Clojure Port

This repository hosts a work‑in‑progress port of **MSC2** (Minimal Sensorimotor Component 2) from the original C implementation (_external/msc2_) to idiomatic Clojure. MSC2 represents the sensorimotor half of OpenNARS-for-Applications, focusing on NAL‑6..8 inference: temporal induction, procedural learning, decision making, and attention under bounded resources.

## Project Structure

- `src/msc2/`
  - `core.clj` – the pure reducer that models the MSC2 cycle (ingestion → FIFO → induction → deduction → decision → anticipation).
  - `truth.clj`, `stamp.clj` – NAL truth calculus and evidential stamp utilities.
  - `term.clj`, `narsese.clj` – EDN representations and parser/serializer for `.nal` scripts.
  - `event.clj`, `fifo.clj`, `memory.clj`, `tables.clj` – event normalization, FIFO sequencer, concept storage, and bounded implication tables.
  - `deduction.clj`, `decision.clj` – belief/goal deduction, decision evaluation, motor babbling, and anticipation.
  - `shell.clj` – REPL-friendly shell that reads EDN or `.nal` lines, similar to the original `NAR shell`.
- `test/msc2/` – unit and integration tests mirroring the C reference (`simple_implication.nal`, `identity_recall_right.nal`, etc.).
- `external/msc2/` – vendored C implementation (git submodule) used as the behavioral oracle.

## Development Workflow

1. Ensure the C reference builds and `.nal` scripts run inside `external/msc2`.
2. Work in small milestones (truth/stamps → parser → events/FIFO → concept tables → deduction/decision → …) mirroring `CLOJURE_PORT_PLAN.md`.
3. Use the C `.nal` files as regression fixtures: feed them through both implementations and compare outputs / state snapshots.
4. All new features should include unit tests (`clj -M:test`), and the shell should remain capable of running `.nal` scripts end-to-end.

## Current Status

- Truth calculus, stamps, EDN term representation, and Instaparse-based `.nal` parsing are in place.
- Event ingestion, FIFO sequencing, induction storage, deduction, decision/motor babbling, and anticipation are implemented with tests.
- Shell commands (`*setopname`, `*motorbabbling`, `*concepts`, predictive questions) have Clojure equivalents.
- Upcoming milestones (attention/forgetting, full shell parity, CI parity checks) are tracked in `CLOJURE_PORT_PLAN.md`.

For detailed architectural notes and future plans see:

- `docs/architecture.md`, `docs/data_model.md`, `docs/msc2-latex/main.tex`
- `CLOJURE_PORT.md` – idiomatic Clojure strategy
- `CLOJURE_PORT_PLAN.md` – milestone checklist
