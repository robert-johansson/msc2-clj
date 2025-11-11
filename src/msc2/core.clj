(ns msc2.core
  "Sensorimotor kernel scaffolding for the MSC2 Clojure port.

  Milestone 1 focuses on modelling the global state and single-cycle reducer.
  Later milestones thread concrete inference/decision functions into the same
  pipeline without changing the API exposed here."
  (:refer-clojure :exclude [step])
  (:require [msc2.decision :as dec]
            [msc2.deduction :as ded]
            [msc2.event :as event]
            [msc2.fifo :as fifo]
            [msc2.queue :as q]
            [msc2.inference :as inference]
            [msc2.memory :as memory]))

(def ^:const default-config
  "Minimal configuration derived from MSC2's Config.h defaults. The values are
  placeholders and mainly document the knobs that later milestones will honor."
  {:concept-capacity 1024
   :belief-queue-capacity 256
   :goal-queue-capacity 256
   :fifo-capacity 32
   :table-capacity 32
   :decision-threshold 0.5
   :motor-babbling-prob 0.25
   :event-durability 0.95
   :concept-durability 0.98
   :event-priority-threshold 0.05
   :concept-priority-threshold 0.02})

(def ^:private empty-queue
  "Persistent queue instance used for both belief and goal buffers."
  clojure.lang.PersistentQueue/EMPTY)

(defn initial-state
  "Return a fresh reasoning state map.

  `config` defaults to `default-config` but can be overridden in tests or
  experiments. The structure mirrors the major MSC2 subsystems so future code
  can fill in the actual data (concept tables, FIFO caches, etc.)."
  ([] (initial-state default-config))
  ([config]
   (let [queue-conf {:capacity (:belief-queue-capacity config)
                     :durability (:event-durability config)
                     :threshold (:event-priority-threshold config)}]
     {:config config
      :time 0
      :concepts {}
      :queues {:belief (q/empty-queue queue-conf)
               :goal (q/empty-queue (assoc queue-conf :capacity (:goal-queue-capacity config)))}
      :fifo (fifo/empty-buffer (:fifo-capacity config))
      :derived []
      :predictions []
      :subgoals []
      :decisions []
      :anticipations []
      :shell {:operations {}}
      :history []})))

(defn- classify-input
  "Decide which queue the incoming event should enter.

  Accepts maps with a `:type` key (`:belief` or `:goal`). Any other value is
  rejected so mistakes surface early in development."
  [{:keys [type] :as input}]
  (when input
    (case type
      :belief :belief
      :goal :goal
      (throw (ex-info "Input must declare :type either :belief or :goal."
                      {:input input})))))

(defn- record-deriveds [state derivations]
  (reduce (fn [s impl]
            (-> s
                (update :derived conj impl)
                (memory/record-derived impl)))
          state
          derivations))

(defn- decay-queues [state]
  (update state :queues
          (fn [queues]
            (reduce-kv (fn [m k queue]
                         (assoc m k (q/decay queue)))
                       {}
                       queues))))

(defn- decay-concepts [state]
  (let [factor (:concept-durability (:config state))
        threshold (:concept-priority-threshold (:config state))]
    (update state :concepts
            (fn [concepts]
              (reduce-kv
               (fn [m term concept]
                 (let [priority (* (or (:priority concept) 1.0) factor)
                       concept (assoc concept :priority priority)]
                   (if (and (< priority threshold)
                            (empty? (:derived concept))
                            (empty? (:tables concept))
                            (nil? (:belief-spike concept))
                            (nil? (:goal-spike concept)))
                     m
                     (assoc m term concept))))
               {}
               concepts)))))

(defn- resolve-anticipations [state event]
  (let [match? #(= (:term event) (:expected-term %))
        successes (filter match? (:anticipations state))
        remaining (remove match? (:anticipations state))]
    (-> (reduce (fn [s ant]
                  (memory/record-derived s (:rule ant)))
                (assoc state :anticipations (vec remaining))
                successes))))

(defn- expire-anticipations [state]
  (let [time (:time state)
        {:keys [expired keep]} (group-by #(<= (:deadline %) time) (:anticipations state))
        expired (get expired true)
        keep (get keep false)]
    (-> (reduce (fn [s ant]
                  (memory/record-derived s (assoc (:rule ant)
                                                  :truth {:frequency 0.0 :confidence 0.2})))
                (assoc state :anticipations (vec keep))
                (or expired [])))))

(defn- add-anticipation [state goal decision]
  (update state :anticipations conj {:expected-term (:term goal)
                                     :deadline (+ (:time state)
                                                  (:occurrence-time-offset (:rule decision)))
                                     :rule (:rule decision)}))

(defn- current-goal [state]
  (or (first (:subgoals state))
      (first (q/all-events (get-in state [:queues :goal])))))

(defn- drop-current-subgoal [state goal]
  (if (and (seq (:subgoals state))
           (= goal (first (:subgoals state))))
    (update state :subgoals subvec 1)
    (update-in state [:queues :goal :events]
               (fn [events]
                 (vec (remove #(= (:term %) (:term goal)) events))))))

(defn- process-decisions [state]
  (let [goal (current-goal state)
        decision (dec/evaluate (:concepts state)
                               goal
                               (get-in state [:shell :operations])
                               (:config state))]
    (if (nil? decision)
      state
      (-> state
          (update :decisions conj decision)
          (drop-current-subgoal goal)
          (add-anticipation goal decision)
          (update :history conj {:time (:time state)
                                 :stage :decision/execute
                                 :input (:operation decision)})))))

(defn- produce-predictions [state belief]
  (let [rules (memory/rules-for-antecedent (:concepts state) (:term belief))
        predictions (map #(ded/belief->prediction belief %) rules)]
    (if (seq predictions)
      (-> state
          (update :predictions into predictions)
          (update :history conj {:time (:time state)
                                 :stage :deduction/prediction
                                 :input (map :term predictions)}))
      state)))

(defn- produce-subgoals [state goal]
  (let [rules (memory/rules-for-consequent (:concepts state) (:term goal))
        subgoals (map #(ded/goal->subgoal goal %) rules)]
    (if (seq subgoals)
      (-> state
          (update :subgoals into subgoals)
          (update :history conj {:time (:time state)
                                 :stage :deduction/subgoal
                                 :input (map :term subgoals)}))
      state)))

(defn- queue-belief [state event]
  (let [{:keys [fifo pairs]} (fifo/enqueue (:fifo state) event)
        derivations (map #(apply inference/belief-induction %) pairs)]
    (-> state
        (assoc :fifo fifo)
        (update-in [:queues :belief] q/enqueue event)
        (memory/record-spike event)
        (update :history conj {:time (:time state)
                               :stage :fifo/enqueue
                               :input (:term event)})
        (record-deriveds derivations)
        (produce-predictions event)
        (cond-> (seq derivations)
          (update :history conj {:time (:time state)
                                 :stage :induction/derived
                                 :input (map :term derivations)})))))

(defn- enqueue-input
  "Place the input event into the appropriate queue, tagging it with the cycle
  time so later stages can compute deltas."
  [state input]
  (if-let [queue-key (classify-input input)]
    (let [event (event/prepare state input)]
      (if (= queue-key :belief)
        (queue-belief state event)
        (-> state
            (update-in [:queues :goal] q/enqueue event)
            (memory/record-spike event)
            (produce-subgoals event))))
    state))

(defn- log-cycle
  "Append a lightweight log entry. This is intentionally small so it can stay
  enabled in tests; heavy tracing belongs in later instrumentation layers."
  [state stage & [input]]
  (update state :history
          conj {:time (:time state)
                :stage stage
                :input (or input :tick)}))

(defn- decay-state [state]
  (-> state
      decay-queues
      decay-concepts))

(defn step
  "Advance the reasoning state by a single cycle.

  If `input` is provided it will be queued as a belief/goal event before the
  placeholder pipeline runs. Future milestones will thread truth calculus,
  FIFO induction, attention, and decision logic through this same reducer.

  Returns the updated state map."
  ([state] (step state nil))

  ([state input]
   (let [state' (-> state
                    (update :time inc)
                    expire-anticipations
                    decay-state
                    (log-cycle :cycle/start input))
         state'' (cond-> state'
                   input (enqueue-input input))
         state''' (process-decisions state'')]
     (log-cycle state''' :cycle/complete))))
