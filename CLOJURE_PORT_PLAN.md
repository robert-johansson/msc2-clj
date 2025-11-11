# MSC2 → Clojure Port Plan

This document proposes a concrete, step-by-step plan for porting MSC2 from C to idiomatic Clojure. It assumes you are working in this repository (with `external/msc2/` holding the C sources, scripts, and experiments) and have the new `deps.edn` tooling in place.

## Milestone 0: Tooling & Repo Hygiene
1. Ensure `clj`, Kaocha, `clj-kondo`, and nREPL/mcp aliases work (run `clojure -M:nrepl` and `clojure -X:test` once).
2. Verify the C version builds and `.nal` scripts run (`./build.sh`, `./NAR shell < tests/simple_implication.nal`).
3. Familiarize yourself with docs:
   - `docs/architecture.md`, `docs/data_model.md`, `docs/truth_calculus.md`, etc.
   - `docs/msc2-latex/main.tex` for deeper context.

## Milestone 1: Clojure Project Skeleton
1. Create `src/msc2_clj/core.clj` (or similar) with a basic namespace and REPL entry.
2. Add placeholder namespaces:
   - `msc2_clj.truth`, `msc2_clj.stamp`, `msc2_clj.term`, `msc2_clj.event`, `msc2_clj.memory`, `msc2_clj.cycle`, `msc2_clj.shell`.
3. Set up Kaocha config (optional) and a `test` folder mirroring namespaces.

## Milestone 2: Truth & Stamp Parity
1. Implement truth operations in `msc2_clj.truth`:
   - `projection`, `induction`, `deduction`, `revision`, `expectation`.
2. Implement stamps in `msc2_clj.stamp`:
   - `make`, `merge`, `overlap?`, `equal?`.
3. Write unit tests comparing results vs. known C outputs (from `Truth.c`). Use `test.check` for ranges (e.g., frequencies stay in `[0,1]`).

## Milestone 3: Narsese Terms & Parser
1. Decide on EDN representation for terms (e.g., `[:inherit a b]`, `[:seq a b]`, `[:implication a b]`).
2. Port the minimal subset of `Narsese.c`:
   - String parser for `.nal` lines.
   - Serializer for printing results.
3. Provide helper functions to build sequences, implications, operations.

## Milestone 4: Event & FIFO Layer
1. Define the `Event` structure as a map.
2. Implement FIFO sequencer as a pure structure (vector + max length).
3. Implement functions to ingest new events:
   - projecting truth to current time (`EventUpdate`).
   - linking events to concepts (stub for now).
4. Tests: feed synthetic event pairs and ensure FIFO outputs match the C logic (use `.nal` micro scripts).

## Milestone 5: Induction & Memory Tables
1. Implement belief induction (`<a =/> b>`) from FIFO outputs.
2. Model concept memory:
   - Map keyed by term → concept data (`{:belief-spike … :goal-spike … :tables {:op-left […]}}`).
   - Bounded tables (drop lowest expectation when full).
3. Port revision logic for implications (weighting by confidence).
4. Tests: run `tests/simple_implication.nal` through the Clojure pipeline and assert the same implication is stored.

## Milestone 6: Deduction & Goal Handling
1. Implement belief deduction (predicting `b` from `a + <a =/> b>`).
2. Implement goal deduction / sequence decomposition.
3. Manage cycling queues (priority maps) for beliefs/goals. Keep capacity limits.
4. Tests: `identity_recall_right.nal` should produce the same `decision expectation=…` line.

## Milestone 7: Decision, Motor Babbling, Anticipation
1. Port `Decision_BestCandidate`: compute desire from concept tables, choose best op above threshold.
2. Implement motor babbling (random op with probability p) using explicit RNG.
3. Implement anticipation (assumption-of-failure) hooks: tag pending consequences, revise truth when feedback arrives.
4. Tests: `pong_ball_right.nal` should match C output (first trial babbles, second trial picks learned op).

## Milestone 8: Attention & Forgetting
1. Port priority decay (`event-durability`, `concept-durability`) and eviction policies.
2. Ensure all queues and tables are bounded and drop lowest-priority entries.
3. Provide inspection helpers akin to `*concepts` and `*stats` using pure data snapshots.
4. Tests: run experiments (Exp 1–4) and compare CSV logs with the C version.

## Milestone 9: Shell & `.nal` Runner
1. Build a `.nal` interpreter:
   - Parse commands (`*reset`, `*concepts`, etc.).
   - Feed sentences/events into the state pipeline.
   - Print outputs in the C shell format.
2. Scripts for experiments:
   - Run `external/msc2/experiments/*.py` (or reimplement in Clojure) using the new shell.
3. Provide CLI entry points via `deps.edn` aliases (e.g., `:main-opts ["-m" "msc2-clj.shell"]`).

## Milestone 10: Parity Verification & CI
1. Add automation to compare C vs. Clojure logs for each `.nal` and experiment:
   - run `./NAR shell < file.nal > c.log`
   - run `clj -M -m msc2-clj.shell < file.nal > clj.log`
   - diff outputs in CI.
2. Add Kaocha suites for all unit + integration tests.
3. Add `clj-kondo` checks, `criterium` benchmarks if needed.

## Stretch Goals
- Provide visualizations (Portal/Reveal) to inspect concept tables live.
- Add instrumentation for tracing (per-cycle metrics).
- Port the Pong harness to Clojure and connect it to the Clojure MSC2 core for an end-to-end demo.

Each milestone should produce a PR/commit series with tests. Use the `.nal` files and CSV outputs as your ground truth; when in doubt, run the C version and compare traces. This keeps the port honest and makes regressions obvious.
