  # MSC2 Clojure Port – Project Brief                                                                                
                                                                                                                     
  ## Overview                                                                                                        
  MSC2 is the latest C implementation of the Minimal Sensorimotor Core (the “MSC” branch inside `OpenNARS-for-       
  Applications`). The goal is to create a faithful Clojure port of MSC2, similar in spirit to the existing `msc-clj` 
  repo but targeting the new kernel and experiment harnesses. This document captures the scope, dependencies, and    
  expectations for the Codex agent (or any contributor) working on the port.                                         
                                                                                                                     
  ---                                                                                                                
                                                                                                                     
  ## Source of Truth                                                                                                 
  - **C reference**: `https://github.com/opennars/OpenNARS-for-Applications` (branch `MSC2`)                         
  - Plan to add it as a Git submodule (e.g., `external/msc2`), pinned to the commit you test against. This ensures   
  the port always tracks a known C baseline.                                                                         
                                                                                                                     
  ## Primary Deliverables                                                                                            
  1. **Clojure MSC2 kernel**                                                                                         
     - Re‑implement the core engine (event ingestion, truth functions, induction, decision loop) in idiomatic        
  Clojure.                                                                                                           
     - Mirror MSC2’s data structures closely enough that experiment parity is achievable.                            
  2. **Experiment harnesses**                                                                                        
     - Rewrite the MSC2 experiment suites (the new regression tests, motor-babbling harnesses, CSV exporters, etc.)  
  so that they produce comparable traces/metrics.                                                                    
  3. **Micro regression scripts**                                                                                    
     - Similar to the `micro_*` scripts in `msc-clj`, add tiny harnesses that inject hand-crafted sequences to       
  sanity-check individual rules. These are invaluable when diagnosing timing/learning issues.                        
                                                                                                                    
                                                                                                                      ## Dependencies & Tooling                                                                                          
  - Clojure CLI / deps.edn.                                                                                          
  - Git submodule pointing to the MSC2 branch.                                                                       
  - (Optional) Plotting / CSV comparison scripts if you want visual diffing vs. MSC2 output.                         

  ---

  ## Workflow Guidelines
  1. **Start from MSC2 C**: Build and run the C project first (`./build.sh`, then `./MSC --run-all-tests`).
  Familiarize yourself with MSC2’s experiment outputs and logging formats.
  2. **Port in layers**:
     - Truth / evidence calculus.
     - Event pipeline (FIFO, stamping, procedural flags).
     - Induction + decision logic.
     - Experiment driver(s) – baseline, training, testing phases.
  3. **Use micro harnesses early**: Before wiring a full experiment, write micro scripts that feed the exact
  sequences you expect to learn (e.g., `A → B → ^op → G`). Confirm each implication strengthens with the Clojure
  kernel in isolation.
  4. **Parities to check**:
     - Experiment CSV structure (columns, block/trial rows).
     - Block accuracies (matching the MSC2 plots).
     - Decision trace behaviour (what rules fire, when motor babbling kicks in).
  5. **Instrument aggressively**: When something diverges, dump the implication table (`:implications`) and compute
  expectation/weight values. In MSC1 we learned the hard way that a simpler rule can crowd out the one you’re trying
  to train.

  ---

  ## Known Pitfalls (from MSC1 work)
  - **Procedural flag misuse**: Only flag the cue(s) you intend as part of the antecedent. Marking every cue as
  `:procedural? true` creates spurious simple rules that dominate decisions.
  - **Decision threshold vs. motor babbling**: MSC defaults to `decision-th = 0.501` and `motor-babble = 0.2`. If
  simpler rules exceed the threshold, the agent never babbles. Be ready to tune these per experiment (or temporarily
  raise the threshold) while you align the port.
  - **Goal injection timing**: When `goal/decide` returns an operation immediately after a goal injection, you need
  to consume that effect before the next decision cycle, or you’ll think the agent “ignored” the rule.
  - **Balanced exposure**: If you need a balanced experiment (all four hypotheses get evidence), make sure the
  schedule actually cycles through every sample/operation combination. Randomized side placement alone isn’t enough
  if the harness always injects both cues before the operation.

  ---

  ## Suggested Roadmap
  1. **Bootstrap repo**: `deps.edn`, stub `src/msc2` namespace, and add the MSC2 C submodule.
  2. **Truth & Evidence**: Port MSC2’s truth calculus (`Truth.c`) first; add unit tests mirroring the C functions
  (induction, revision, expectation).
  3. **Engine skeleton**: FIFO, stamping, event ingestion, and motor babbling loop.
  4. **Experiment micro tests**:
     - Single sequence (A+B+^op+G).
     - Alternating pair.
     - All four combinations. 
  5. **Full Experiment harnesses**:
     - Port MSC2’s regression tests (`tests_regression.c` on MSC2 branch).
     - Export CSVs and compare vs. the C outputs.
  6. **Diagnostics**:
     - Add CLI scripts to print implication tables, decision traces, and truth summaries.
     - Keep the micro scripts checked in for future debugging.

  ---

  ## Success Criteria
  - **Feature parity**: For each MSC2 experiment, the Clojure version produces CSVs/plots that qualitatively match
  the C run (same learning curves, final accuracies).
  - **Micro tests pass**: Each of the four target sequences can be learned when injected explicitly.
  - **Deterministic runs**: With the same random seed, the harness should be reproducible enough that regressions
  are obvious.
  - **Documentation**: README (or this doc) updated with build/run instructions, MSC2 submodule usage, and notes
  about any intentional departures from the C implementation.

  ---

  ## Open Questions / To Consider
  - Do we want to keep MSC1 (`msc-clj`) as a separate repo (recommended) or fold it into a mono‑repo with MSC2?
  - Should we add visualization scripts (Python/Matplotlib, or Clojure + Vega) to compare the C and Clojure CSVs
  automatically?
  - How deeply will we integrate with OpenNARS proper? (e.g., long term: share truth functions, inference code).

  ---

  ## Final Notes
  - Commit often, especially after getting a new experiment to match C.
  - Keep micro harnesses around; their overhead is tiny compared to the time they save during debugging.
  - Don’t be shy about instrumenting the engine—printing out the `:implications` map or the decision trace is much
  faster than guessing which rule won.
  - When in doubt, re-run the C harness and diff its CSV against the Clojure output; a quick `python compare.py` goes
  a long way.

  Good luck with the MSC2 port! If you discover anything quirky about the MSC2 branch (new truth functions, different
  goal propagation, etc.), append those notes here so the next contributor doesn’t start from scratch.

