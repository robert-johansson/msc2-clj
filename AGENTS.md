# Repository Guidelines

## Project Structure & Module Organization
Keep the root lean: `deps.edn`, this guide, and the MSC2 reference. Place production code under `src/msc2` (e.g., `src/msc2/core.clj` for the kernel loop, `src/msc2/engine/truth.clj` for the calculus) and mirror the C components one namespace at a time. Tests live in `test/msc2`, mirroring the namespaces. Vendor the upstream C reference as `external/msc2` (git submodule pinned to the MSC2 branch) and store experiment artifacts or CSV baselines in `resources/experiments`. Keep lightweight harness scripts in `scripts/micro_*` so each file maps to a MSC2 regression.

## Build, Test, and Development Commands
- Run `clojure_inspect_project` for the "Clojure Project Info" snapshot before starting work on a new task.
- `clj -M:repl` — interactive REPL bound to `src/msc2` for probing kernels and harnesses.
- `clj -M:test` — runs every `clojure.test` suite, including micro parity checks.
- `clj -M:run :scenario baseline` — entry point for experiment drivers; pass MSC2-style scenario keywords.
- `(cd external/msc2 && ./build.sh && ./MSC --run-all-tests)` — rebuild and re-run the C baseline before comparing CSVs.

## Coding Style & Naming Conventions
Follow idiomatic Clojure: two-space indentation, `kebab-case` namespaces/functions, and `snake_case` keys when mirroring MSC2 structs. Keep pure functions data-first, threading domain maps via `->`/`->>`. Use `cljfmt` (via `clj -M:fmt`) before commits, and prefer docstrings describing the MSC2 concept being ported.

## Testing Guidelines
Use `clojure.test` (or Kaocha via an optional `:test` alias) and keep one test namespace per production namespace. Encode micro harnesses as data fixtures (`resources/experiments/micro/*.edn`) so they can feed both the REPL and automated suites. Name tests after the MSC2 concept under test (e.g., `truth_revision_increases_expectation`). Aim for deterministic seeds and compare generated CSVs against the golden files in `resources/experiments/baseline`, flagging deviations >0.01.

## Commit & Pull Request Guidelines
There is no established history yet, so adopt Conventional Commits (`feat(engine): add FIFO ingestion`) to seed a readable log. Reference relevant MSC2 C files in the body when porting logic. PRs should include: summary of the scenario exercised, CI/test output (even if run locally via `clj -M:test`), links to comparison CSVs, and any tuning knobs touched (`decision-th`, `motor-babble`).

## Agent-Specific Tips
Run the upstream MSC2 binaries first to refresh expected traces before changing the Clojure port. Instrument early: dumping `:implications` or decision traces from the REPL usually surfaces divergence faster than guessing. When behavior drifts, bisect with the micro scripts before touching the full experiments; most regressions originate in truth calculus or stamping.
