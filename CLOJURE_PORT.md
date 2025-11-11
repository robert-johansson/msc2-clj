# Porting MSC2 to Idiomatic Clojure

This note captures a practical strategy for reimplementing MSC2 (the C sensorimotor kernel) in idiomatic Clojure. The goal is behavioural parity with the C reference while embracing Clojure’s functional style, immutable data, and REPL-driven development.

## 1. Data Model as Pure Maps

- Represent terms, events, concepts, and implications as persistent maps/vectors:
  ```clojure
  {:term term-structure
   :truth {:frequency f :confidence c}
   :stamp stamp-data
   :type  :belief/:goal
   :occurrence-time t}
  ```
- Encode Narsese terms as EDN trees (`[:seq a b]`, `[:implication a b]`, etc.). Build parser/serializer helpers so `.nal` files feed directly into the Clojure REPL.
- Concepts become maps keyed by their term, holding spikes, implication tables, usage stats, etc.

## 2. Truth & Stamp Functions First

- Port the math from `Truth.c` and `Stamp.c` before anything else:
  - `truth/induction`, `truth/deduction`, `truth/revision`, `truth/projection`, `truth/expectation`
  - `stamp/make`, `stamp/overlap?`, `stamp/merge`
- Write unit tests that assert parity with the C formulas (use known `(f,c)` inputs and expected outputs).

## 3. State-Passing Pipeline (No `swap!` Required)

- Model the entire reasoner as a pure reducer:
  ```clojure
  (defn step [state input]
    (-> state
        (process-input input)
        update-fifo
        run-induction
        run-deduction
        run-goal-decomposition
        run-decision
        run-forgetting)))
  ```
- The REPL can keep a “current” state in an atom for convenience, but the core API remains `(step state input) -> state'`.
- For event streams, use `reductions`/`iterate` to build lazy sequences of successive states:
  ```clojure
  (def states (reductions step initial-state inputs))
  (def final-state (last states))
  ```
- In a real-time loop:
  ```clojure
  (loop [state initial-state
         events input-stream]
    (when-let [event (first events)]
      (let [state' (step state event)]
        (recur state' (rest events)))))
  ```
- When embedding in an app, thread the state explicitly through a loop (`loop/recur`) or a stream processor (core.async, manifold, etc.), avoiding implicit mutation.

## 4. FIFO, Priority Queues, Tables

- Use persistent data structures to emulate bounded queues:
  - FIFO sequences as vectors or ring buffers (e.g., `clojure.core/vec`, `conj`, `subvec` for trimming).
  - Priority queues via `clojure.data.priority-map` or custom sorted structures. Enforce capacity by dropping lowest-priority entries when pushing new ones.
  - Implication tables as sorted vectors keyed by expectation, trim exceeding capacity.
- Explicitly carry capacity limits in the state map so the O(1) per-cycle guarantee remains.

## 5. Recreate Cycle.c Logic Functionally

- Translate each block from `Cycle.c` into a composable Clojure function:
  - `process-input-beliefs`, `process-cycling-beliefs`, `process-cycling-goals`, `forgetting-pass`, etc.
  - Each function takes the current state map, performs deterministic transformations, and returns an updated state + any emitted outputs (`events`, `logs`).
- Use `reduce`/`transduce` to iterate over queues instead of manual loops.

## 6. Decision & Motor Babbling

- Keep decision logic pure: compute candidate desires, select the best by expectation, and return `{:state new-state :decision decision}`.
- Motor babbling draws from a RNG; encapsulate randomness via `rand` but keep it explicit (e.g., pass a random seed in tests).
- Anticipation/assumption-of-failure can be modelled by tagging decisions with pending outcomes stored in the state map.

## 7. `.nal` Scripts as Regression Tests

- Reuse the C `.nal` files (from `external/msc2/tests/`) to drive both implementations:
  - Write a Clojure shell that reads `.nal` lines, feeds them into the state pipeline, and prints outputs in the same format (`Input: …`, `Derived: …`, `^op`).
  - Compare logs from the C and Clojure versions to ensure parity.
- Automate this: run the same script through both shells in CI, diff the outputs.

## 8. Module Parity Checklist

Port modules roughly in this order, verifying each before moving on:
1. Truth + stamps (math parity)
2. Narsese term representation + parser
3. Event ingestion / FIFO sequencing
4. Induction + implication tables
5. Deduction + goal decomposition
6. Decision + anticipation/motor babbling
7. Attention/forgetting mechanics
8. Shell interface (commands like `*concepts`, `*stats`)

At each step, add `.nal`-based tests so regressions fail fast.

## 9. REPL-Driven Workflow

- Expose helper functions to inspect concepts, queues, and implication tables (similar to `*concepts`).
- Keep an interactive REPL session where you can `(swap! system-state run-cycle input)` and observe printed results.
- Document any intentional divergences from C (e.g., improved data structures or extra logging) so collaborators understand the differences.

## 10. Performance & Boundedness

- Even though Clojure structures are efficient, explicitly ensure:
  - Queues/tables never exceed configured size.
  - Each pipeline stage touches only bounded subsets (no unbounded `map`/`filter` over global memory).
  - Periodic `for`/`reduce` operations are capped by configuration (mirroring the C limits).

Following this plan yields a Clojure implementation that is idiomatic (pure data, explicit state passing) yet semantically identical to the C kernel. The `.nal` micros and experiment scripts provide the ground truth to verify behaviour at every stage.

## Tooling Recommendations

- **Testing:** [Kaocha](https://github.com/lambdaisland/kaocha) for orchestrating unit + integration tests; add aliases in `deps.edn` for `clojure -X:test` and `clojure -X:kaocha`.
- **Specs/Properties:** use `clojure.spec` and/or `test.check` to assert invariants (truth values stay in `[0,1]`, stamps never overlap after merging, etc.).
- **Priority queues:** [data.priority-map](https://github.com/clojure/data.priority-map) or [medley](https://github.com/weavejester/medley) for attention queues; wrap them to enforce capacity limits.
- **Linting:** `clj-kondo` (integrated into CI) to catch arity/namespace issues early.
- **Benchmarking:** `criterium` for profiling critical code paths (e.g., cycle step) to ensure bounded runtime.
- **REPL tooling:** use `rebel-readline`, Portal, or Reveal for interactive inspection; keep helper fns (`view-concepts`, `view-queues`) to replicate the `*concepts` experience.

With these tools in place, the port will stay testable, inspectable, and close to the C reference.***
