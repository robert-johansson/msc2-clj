# MSC2 Clojure Port – Handoff (Next up: Milestone 9)

## Project Overview

This repo is an idiomatic Clojure port of **MSC2** (Minimal Sensorimotor Component 2), the sensorimotor kernel of OpenNARS-for-Applications focused on NAL‑6..8 inference. The goal is behavioral parity with the C reference in `external/msc2/` while adopting immutable data structures and REPL-driven development.

### Key Namespaces

- `msc2.core` – pure reducer that implements the MSC2 cycle (event ingestion, FIFO, induction, deduction, decision, anticipation, attention decay).
- `msc2.truth`, `msc2.stamp` – truth calculus, evidential overlap.
- `msc2.term`, `msc2.narsese` – EDN term representation and Instaparse-based `.nal` parser/serializer.
- `msc2.event`, `msc2.fifo`, `msc2.queue` – event normalization, FIFO sequencer, bounded priority queues.
- `msc2.memory`, `msc2.tables` – concept storage, bounded implication tables, usage/priority tracking.
- `msc2.deduction`, `msc2.decision` – belief/goal deduction, decision evaluation, motor babbling, anticipations.
- `msc2.shell` – REPL shell that accepts EDN or `.nal` input, runs the reducer, and prints summaries (currently minimal).
- Tests under `test/msc2/` mirror each namespace plus integration suites for `.nal` scripts.

## Completed Milestones (0–8)

1. **Tooling & Repo Hygiene** – deps.edn, Kaocha, clj-kondo, vendored C ref.
2. **Truth & Stamp Parity** – `msc2.truth`, `msc2.stamp` with parity tests.
3. **Narsese Terms & Parser** – EDN term schema + Instaparse parser/serializer + shell ingestion.
4. **Event & FIFO Layer** – event normalization, FIFO sequencer, ingestion pipeline.
5. **Induction & Concept Tables** – bounded per-antecedent tables, question answering from stored rules.
6. **Deduction & Goal Handling** – predictions/subgoals, decision scoring/motor babbling/anticipation.
7. **Decision/Motor Babbling/Anticipation** – integrated with shell logs and state tracking.
8. **Attention & Forgetting** – bounded priority queues, event/concept decay, concept eviction.

All unit and integration tests (`clj -M:test`) pass. Scripts like `simple_implication*.nal` run through the parser + reducer, populate concept tables, and produce decisions internally. The shell currently prints only cycle summaries and minimal decision lines.

## Next Steps – Milestone 9 (Shell & `.nal` Runner)

Milestone 9 is about shell parity and `.nal` runner usability:

1. **Shell Output Parity**
   - Emit lines matching `./NAR shell`: `Input:`, `Derived:`, `Answer:`, `^op executed with args`.
   - Track and print `Derived:` entries when induction fires, predictions/subgoals, and decisions/motor babbling events.

2. **Command Coverage**
   - Implement remaining commands: `*reset`, `*stats`, `*volume`, `*allowFork`, etc., mirroring the C shell behavior.
   - Hook `*stats` to the new queue/priority data (concept counts, avg priorities, queue sizes).

3. **CLI Runner**
   - Wire `deps.edn` with `:run` alias (or similar) so `clj -M -m msc2.shell < file.nal` acts like the C shell, including exit codes and interactive prompts.

4. **Regression Scripts**
   - Create tests/scripts that run key `.nal` files via the Clojure shell and compare the output to the C version (even if just comparing counts/events initially).

5. **Documentation**
   - Update README/HANDOFF once shell parity is achieved; document how to run `.nal` scripts through the new runner.

Once Milestone 9 is complete, Milestone 10 focuses on automated parity verification (CI jobs comparing C vs. Clojure logs) and any remaining polish. Stretch goals include live inspection tools, metrics, and experiment harnesses.
