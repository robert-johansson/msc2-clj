Below is a developer‑level walkthrough of MSC2 (Minimal Sensorimotor Component 2) — the early, stripped‑down “OpenNARS for Applications” (ONA) that keeps only NAL‑6..8 (temporal & procedural) and the event‑driven control loop. Everything here is derived from the source you shared (OpenNARS-for-Applications-MSC2 2.zip) and organized so you can navigate the code quickly and understand how it all fits together.

⸻

1) Big picture — what MSC2 implements

MSC2 is the sensorimotor half of ONA: an event‑driven, bounded‑resource reasoner that (a) learns temporal and procedural implications from streams of events, (b) uses them to predict and subgoal, and (c) makes decisions (operation execution) when desire exceeds a threshold. The control loop and the component roles match the ONA architecture: sensory channels → FIFO sequencer → cycling events PQ → concept memory, with sensorimotor inference (induction, deduction, subgoaling, decision) and priority‑based forgetting. See the architecture diagram in the AGI‑2020 paper (the “Reasoning Node” with FIFO, Cycling Events Queue, Concept Memory, and separate Semantic/Sensorimotor blocks, page 4, Fig. 1).  ￼

MSC2 keeps the sensorimotor side only (NAL‑6..8), so there’s no high‑level declarative/semantic NAL‑1..6 inference in this branch; the emphasis is on forming and using temporal/procedural links and the event‑driven control (also described in the 2020 paper’s text around Fig. 1 and the operating cycle in Sec. 4).  ￼

⸻

2) Code map (what lives where)
	•	Public API / runner
	•	src/NAR.{h,c} – public functions: initialization, cycle stepping, add input belief/goal, add operations; parses Narsese sentences.
	•	src/Shell.{h,c} – interactive shell (stdin/stdout), commands (*stats, *concepts, parameter toggles), basic demo ops.
	•	src/main.c – starts shell or runs system tests (Pong, Robot, etc).
	•	Core data model
	•	src/Term.{h,c} – fixed‑size term representation (binary tree over a flat array), hashing, subterm override/extract, complexity.
	•	src/Narsese.{h,c} – parser/encoder: infix → canonical single‑char copulas → prefix tokens → binary tree (Term).
	•	src/Truth.{h,c} – NAL truth values (f, c), expectation, revision, deduction, induction, projection (temporal discount).
	•	src/Stamp.{h,c} – evidential bases for overlap checking.
	•	src/Event.{h,c} – event structure, construction, equality.
	•	src/Implication.h – implication structure (term, truth, occurrenceTimeOffset, provenance).
	•	Memory & attention
	•	src/Memory.{h,c} – bounded concept memory and cycling events queues; adding input/derived events; implication table management.
	•	src/Concept.h – the concept structure (belief & goal spikes, predicted belief, precondition implication tables, priority/use).
	•	src/Table.{h,c} – truth‑expectation‑ranked table of implications with in‑place revision.
	•	src/PriorityQueue.{h,c} – min‑max heap for bounded priority queues (concepts; cycling belief/goal events).
	•	src/FIFO.{h,c} – sequencer that incrementally caches recent sequences of events for induction.
	•	src/Usage.{h,c} – concept usefulness (recency & use count).
	•	src/InvertedAtomIndex.{h,c} – fast concept lookup by leading atoms (speeds unification search).
	•	Inference & decision
	•	src/Inference.{h,c} – event/procedural inference rules: induction → implication; deduction → predictions/subgoals; goal sequence decomposition; revision & choice.
	•	src/Decision.{h,c} – desire computation & Decision_BestCandidate; Anticipation (assumption of failure, negative evidence); Motor babbling.
	•	src/Cycle.{h,c} – the operating cycle: select events, process FIFO/induction, propagate subgoals/decisions, forgetting, requeue.
	•	Infrastructure
	•	src/HashTable.{h,c}, src/Stack.{h,c}, src/Globals.{h,c} – utilities, RNG, hashing, assertions.
	•	src/Config.h – all key constants (capacities, thresholds, projection decay, etc).

⸻

3) The data model in detail

3.1 Terms & Narsese
	•	Term (Term.h, Term.c): a fixed‑size array (COMPOUND_TERM_SIZE_MAX = 64) of atoms (Atom is unsigned short) encoding a binary tree. You address a subterm by tree index; Term_OverrideSubterm and Term_ExtractSubterm write/extract subtrees in O(1) per node. Terms are hashed and the hash is cached per term (Term_Hash).
	•	Narsese encoding (Narsese.c):
	•	Canonical single‑char copulas: e.g., : for inheritance (-->), = for similarity (<->), $ for predictive implication (=/> or ==>), + for temporal sequence (&/), ^ for ops.
	•	Parser normalizes: infix → canonical → prefix tokens → binary tree (buildBinaryTree), then variable normalization (Variable_Normalize).
	•	Narsese_Sequence(a,b) builds (&/, a, b) in term form.

These choices mirror ONA’s compositional term logic and Narsese handling, as outlined in the ONA papers (NAL compositionality & Narsese).  ￼

3.2 Truth, stamps, events, implications
	•	Truth (Truth.h, Truth.c):
	•	Represented by frequency f and confidence c; expectation E(f,c) = c*(f-0.5)+0.5 (code: Truth_Expectation). This summary scalar is used everywhere for ranking. The paper gives the same expectation definition (Sec. 3, Eqn., page 5).  ￼
	•	Revision merges evidences (confidence → evidence w via c = w/(w+1)).
	•	Deduction, Induction, Projection (the latter with TRUTH_PROJECTION_DECAY_INITIAL = 0.8) discounting by β^|Δt| — see page 6 for the temporal projection idea.  ￼
	•	Stamp (Stamp.h): fixed‑size evidential base; overlap check prevents double‑counting (independence requirement).
	•	Event (Event.h): term, type (belief/goal), truth, occurrenceTime, stamp, priority, flags (derived/predicted/spatioTemporal/processed), creationTime.
	•	Implication (Implication.h): term holds either (A ⇒ B) or ((A, op) ⇒ B) (encoded as $ copula); truth, occurrenceTimeOffset (learned average Δt), provenance (sourceConcept/id).
	•	Concept (Concept.h): a node indexed by its term, with:
	•	belief spike (most recent event), predicted belief, goal spike,
	•	precondition tables precondition_beliefs[op] (per‑op implication tables),
	•	priorities (priority) & usefulness (Usage).
	•	Tables & PQ:
	•	Table holds top‑N implications (ranked by expectation, TABLE_SIZE=20), supports in‑place revision (Table_AddAndRevise).
	•	Two bounded priority queues: concepts (ranked by usefulness) and cycling events (separate queues for belief and goal events, ranked by priority), both implemented as a min‑max heap.

⸻

4) Learning & inference pipeline

4.1 FIFO sequencer → Induction → Implications
	•	FIFO (FIFO.c) maintains a sliding window over recent events and caches compound sequences by bit‑states (state encodes which sequence length/position). When a new event arrives, it recombines it with cached subsequences and builds (&/, a, b) via Inference_BeliefIntersection.
	•	Induction (Inference_BeliefInduction):
	•	For a. followed later by b., derive implication <a =/> b>. (term with $), with truth induced from projected premises.
	•	It sets/updates occurrenceTimeOffset as the evidence‑weighted average Δt (so future predictions know when to expect B after A).
	•	If an op was executed between A and B, MSC2 will prefer procedural form ((A, op) ⇒ B) (see the mining in Cycle_ProcessInputBeliefEvents: it checks postcondition with all FIFO precondition sequences and captures optional operator arguments; then Memory_ProcessNewBeliefEvent indexes the implication in the postcondition concept’s precondition_beliefs[op] table).
	•	Revision of implications occurs inside the table (Table_AddAndRevise delegates to Inference_ImplicationRevision): confidences add up, and Δt is re‑averaged by evidence.

This is precisely the “learn causal (precondition, operation) ⇒ consequent hypotheses from few examples, with eligibility/projection handling temporal credit assignment” emphasized for ONA (Sec. 3 in the 2022 paper).  ￼

	•	Assumption of failure / anticipation (Decision_Anticipate): when an op is executed (and also for the “do‑nothing op”), the system proactively adds small negative evidence to all <A, op =/> B> links reaching a B that fails to appear; if B arrives as predicted, the subsequent positive evidence outweighs the negative. This implements the paper’s simple, deadline‑free anticipation mechanism (Sec. 3 “Collecting negative evidence…”, page 7).  ￼

4.2 Deduction → Predictions and subgoals
	•	Belief deduction (Inference_BeliefDeduction): from A. and <A =/> B>. derive predicted B. (with occurrence time tA + Δt for non‑eternal events). The derived event is added via Cycle_DerivedEvent → Memory_AddEvent.
	•	Goal deduction / subgoaling:
	•	If we have a goal G! and an implication ((X, op) ⇒ G), then Decision_BestCandidate computes the op’s desire by deduction combining desire(G) and truth of the implication (two steps if you include precondition truth). This follows the rules shown in the 2022 paper (Sec. 3 “Decision Making”, equations for fded), and is exactly what Decision_BestCandidate computes before comparing to DECISION_THRESHOLD.  ￼
	•	If the best desire is below threshold, MSC2 derives subgoals from incoming links: it pushes X! for all ((X, op) ⇒ G) (plus “NOP subgoaling” from temporal A ⇒ G if enabled).
	•	Goal sequence decomposition (Cycle_GoalSequenceDecomposition): if a goal is itself a sequence (&/, a, b, c)!, it deduces the first not‑yet‑satisfied component subgoal (using unification & belief timestamps) so the planner “works backwards” on constraints.
	•	Decision execution (Decision_Execute): when above threshold, it executes the op (operations[op-1].action) and injects an operation feedback event (either a pure op atom ^op or (<args --> ^op>) if there are arguments) into the system so the FIFO can correlate actions with outcomes (critical for procedural induction and anticipation).
	•	Exploration (motor babbling): with probability MOTOR_BABBLING_CHANCE (default 0.2) MSC2 ignores the current suggestion and executes a random op (among those registered); as the confidence/expectation about good procedures rises, babbling is effectively suppressed (see also 2020 paper’s “Motor Babbling”).  ￼

⸻

5) The operating cycle (attention & control)

Look in Cycle_Perform for the top‑level loop:
	1.	Select a bounded number of events (BELIEF_EVENT_SELECTIONS, GOAL_EVENT_SELECTIONS) from the cycling queues (bounded PQs).
	2.	Process incoming belief events from the FIFO, generating sequences and forming/revising implications (Section 4.1).
	3.	Process incoming goal events, doing subgoaling and running Decision_BestCandidate; execute when desire ≥ threshold (Section 4.2).
	4.	Relative forgetting: decay concept priorities (CONCEPT_DURABILITY, default 0.9) and event priorities (EVENT_DURABILITY, default 0.9999), and on‑usage decay (EVENT_DURABILITY_ON_USAGE).
	5.	Re‑queue the selected items (with decayed priority).

This is the event‑driven attention policy described in the 2020 paper’s Operating Cycle (Sec. 4) — fixed‑time cycles, priority‑based selection, and forgetting for both events and concepts to respect AIKR (Assumption of Insufficient Knowledge & Resources).  ￼

Resource bounds (key constants from Config.h):
	•	Concept memory: bounded PQ (CONCEPTS_MAX, see code). Priority = usefulness from Usage_usefulness(useCount, recency).
	•	Cycling events: belief and goal queues (size CYCLING_BELIEF_EVENTS_MAX, CYCLING_GOAL_EVENTS_MAX).
	•	FIFO sequencer size FIFO_SIZE = 20; max sequence length MAX_SEQUENCE_LEN = 3.
	•	Implication table per (postcondition,op): TABLE_SIZE = 20.
	•	Max operations OPERATIONS_MAX = 10.
	•	Term size COMPOUND_TERM_SIZE_MAX = 64.
	•	Projection decay TRUTH_PROJECTION_DECAY_INITIAL = 0.8, evidential horizon TRUTH_EVIDENTAL_HORIZON_INITIAL = 1.0.
	•	Decision thresholds & anticipation: DECISION_THRESHOLD_INITIAL = 0.501, ANTICIPATION_THRESHOLD_INITIAL = 0.501, negative‑evidence confidence ANTICIPATION_CONFIDENCE_INITIAL = 0.01.

⸻

6) Variables, unification, and substitution
	•	Variable.{h,c} handles three variable kinds: independent $i, dependent #i, and query ?i. There’s unification (Variable_Unify/Unify2) that returns a substitution map; substitutions are applied to terms (Variable_ApplySubstitute).
	•	Normalization adjusts symbolic variables like ?what into ?1..9.
	•	Question answering in NAR_AddInputNarsese makes use of query variables to pick the best matching implication or belief spike (projected to “now” if asked in present/past/future), with best truth expectation—aligning with the real‑time Q/A example in the paper (Fig. 3, page 9).  ￼

⸻

7) Decisions & desire computation (what the code actually does)
	•	Decision_BestCandidate scans the postcondition concept’s precondition_beliefs[op] tables:
	•	It tries specific (unified with the exact goal) and general matches (unify with variables) and tracks the best expectation.
	•	Desire is the expectation computed by the deduction chain described in the paper (fded composition, Sec. 3), including the precondition truth if present. If best desire ≥ DECISION_THRESHOLD, it sets decision.execute=true.
	•	Decision_Suggest arbitrates motor babbling vs suggestion: if a babbled decision exists but the suggested desire is above MOTOR_BABBLING_SUPPRESSION_THRESHOLD, the suggestion wins.
	•	Decision_Anticipate runs every time an op is taken (including op=0 “do nothing”) to add negative evidence through implicit anticipation (assumption of failure).

This matches the ONA decision/subgoaling algorithm described in the 2022 paper (Sec. 3), including how eligibility/projection replaces explicit TD(λ): projection discounts evidence by time and revisions accumulate it (cf. page 6–7).  ￼

⸻

8) Memory, printing, and inspection
	•	Adding input:
	•	NAR_AddInput(term, type, truth, eternal, occurrenceTimeOffset) builds an Event then calls Memory_AddInputEvent, which shoves it into the FIFO and adds it to the appropriate cycling queue. A single NAR_Cycles(1) step runs afterwards.
	•	Adding operations:
	•	NAR_AddOperation(^op, Action) registers an op and its function pointer used by Decision_Execute.
	•	Debug/inspection:
	•	Shell commands *stats, *concepts, *inverted_atom_index, *motorbabbling=…, etc.
	•	When PRINT_INPUT / PRINT_DERIVATIONS are enabled, Memory_printAddedEvent and Memory_printAddedImplication show the knowledge stream with priorities, occurrence times, and whether it was input/derived/revised (also prints dt for learned Δt on implications).

⸻

9) Example flow (from raw events to a used procedure)

Scenario: the system sees A., later executes ^op, then sees B.
	1.	NAR_AddInputBelief(A) → FIFO stores, Cycle_ProcessInputBeliefEvents activates concept(A).
	2.	NAR_AddInputBelief(<(^op)> (operation feedback from Decision_Execute or babbling) → FIFO holds it.
	3.	NAR_AddInputBelief(B) triggers Induction in Cycle_ProcessInputBeliefEvents: mines ((A, ^op) ⇒ B) (and possibly (A ⇒ B)), with truth and Δt set. Memory_ProcessNewBeliefEvent inserts it into concept(B)’s precondition_beliefs[op] table (top‑N by expectation).
	4.	Later, a goal B! arrives → Decision_BestCandidate finds the best ((X, op) ⇒ B) where X matches current belief spikes; if desire ≥ threshold, it executes op; else it subgoals X!.
	5.	Each execution runs anticipation; if B shows up with correct timing, the link’s expectation rises; otherwise it is penalized.

This is the procedure learning & execution pattern highlighted in the ONA experiments (e.g., Pong, Robot, toothbrush setup), and the eligibility‑like temporal credit assignment is achieved by projection+revision instead of explicit TD(λ) (discussed in 2022, Sec. 3–4).  ￼

⸻

10) Attention & forgetting (how MSC2 stays real‑time)
	•	Event priorities decay continuously (EVENT_DURABILITY) and additionally when used (EVENT_DURABILITY_ON_USAGE). Selected items are pushed back with decayed priority.
	•	Concept priority is their computed usefulness (recency & use count), decayed by CONCEPT_DURABILITY. New concepts get a fair chance due to the normalization curve in Usage_usefulness.
	•	Conceptualization (Memory_Conceptualize) maintains bounded memory: on push into concepts’ PQ, the lowest‑usefulness concept is evicted; we also remove its hash mapping and inverted‑atom entries. This is the relative/absolute forgetting principle stressed in the ONA write‑up (Sec. 2 & 4, pages 2 & 6).  ￼

⸻

11) Parameters worth knowing (from Config.h)
	•	Event selection per cycle: BELIEF_EVENT_SELECTIONS=1, GOAL_EVENT_SELECTIONS=1.
	•	Durability: EVENT_DURABILITY=0.9999, CONCEPT_DURABILITY=0.9.
	•	Induction timing: TRUTH_PROJECTION_DECAY_INITIAL=0.8 (β), TRUTH_EVIDENTAL_HORIZON_INITIAL=1.0.
	•	Thresholds: DECISION_THRESHOLD_INITIAL=0.501, ANTICIPATION_THRESHOLD_INITIAL=0.501, ANTICIPATION_CONFIDENCE_INITIAL=0.01.
	•	Exploration: MOTOR_BABBLING_CHANCE_INITIAL=0.2 and suppression threshold 0.55.
	•	Sizes: FIFO_SIZE=20, TABLE_SIZE=20, MAX_SEQUENCE_LEN=3, OPERATIONS_MAX=10, COMPOUND_TERM_SIZE_MAX=64.

⸻

12) Differences vs. full ONA & limitations
	•	No declarative (NAL‑1..6): MSC2 focuses on NAL‑6..8; the “Semantic Inference” block in the 2020 diagram is absent. The code paths labeled “NAL module” are intentionally minimized; the induction/deduction logic is all sensorimotor.  ￼
	•	Local temporal mining: FIFO windowing & sequence length (MAX_SEQUENCE_LEN=3) bound temporal credit assignment and pattern span.
	•	Bounded tables: each postcondition/op keeps only top‑TABLE_SIZE implications by expectation (keeps everything O(1) per cycle).
	•	Question answering is limited to spikes/implications; there’s no full semantic network.

Despite those limits, this component already reproduces the event‑driven control and procedure learning that gave ONA its strong results on Pong/Robot/Space‑Invaders (and the generalization behavior discussed in the papers).  ￼  ￼

⸻

13) How to extend or hack it
	•	Add ops: define Action functions and register with NAR_AddOperation(Narsese_AtomicTerm("^myop"), my_fn). The shell includes a few built‑ins (up, down, say, pick, drop, go, activate, deactivate).
	•	Tune behavior: thresholds (DECISION_THRESHOLD, ANTICIPATION_*), projection decay, event/concept durability, table sizes, and MAX_SEQUENCE_LEN.
	•	Instrumentation: use *stats and PRINT_INPUT / PRINT_DERIVATIONS to watch streams; *inverted_atom_index checks fast lookup structure.
	•	Perception adapters: feed symbolic event streams (the repo also includes Python tooling / ROS channels under misc/).

⸻

14) A quick “call chain” mental model

Input sentence → Term → Event → FIFO → Cycle
NAR_AddInputNarsese → Narsese_Sentence → NAR_AddInput → Memory_AddInputEvent → (queued; 1 step) → Cycle_Perform → Cycle_ProcessInputBeliefEvents (sequences & induction) → Memory_ProcessNewBeliefEvent (implication tables) → Cycle_ProcessInputGoalEvents (subgoals/decisions) → Decision_Execute (and Decision_Anticipate) → back to Memory_AddEvent (operation feedback & predictions) → … loop.

⸻

Pointers into the code for the crucial mechanisms
	•	Building implications: Cycle_ProcessInputBeliefEvents (mining), Inference_BeliefInduction, Table_AddAndRevise.
	•	Using implications: Inference_BeliefDeduction (predictions), Decision_BestCandidate (desire calc).
	•	Subgoaling: Cycle_ProcessInputGoalEvents, Cycle_GoalSequenceDecomposition, Inference_GoalDeductions.
	•	Anticipation (neg. evidence): Decision_Anticipate.
	•	Attention & forgetting: Cycle_RelativeForgetting, Memory_addCyclingEvent, Usage_usefulness, PriorityQueue_*.
	•	Parsing & terms: Narsese_*, Term_*.
	•	Truth math: Truth_* (expectation, projection, revision).

⸻

15) How this aligns with the papers
	•	The architecture and control (event‑driven, FIFO, cycling PQs, concept memory, forgetting) directly reflect the 2020 ONA paper, Fig. 1 and Sec. 2–4.  ￼
	•	The truth model, expectation, projection, temporal induction, subgoaling/decision formulas, and the assumption‑of‑failure “negative evidence immediately, over‑voted by positive if success” are consistent with the 2022 paper’s Sec. 3 (and its comparison to TD(λ)/eligibility via projection+revision).  ￼

⸻

If you want, I can next:
	•	walk through any specific file or function line‑by‑line,
	•	trace a concrete run (e.g., how it learns ((ballLeft, moveRight) ⇒ ballCenter) in Pong),
	•	or propose a change (e.g., larger MAX_SEQUENCE_LEN, different projection β) and explain the expected effects.
