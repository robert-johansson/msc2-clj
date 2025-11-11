(ns msc2.core
  "Sensorimotor kernel scaffolding for the MSC2 Clojure port.

  Milestone 1 focuses on modelling the global state and single-cycle reducer.
  Later milestones thread concrete inference/decision functions into the same
  pipeline without changing the API exposed here."
  (:refer-clojure :exclude [step])
  (:require [msc2.fifo :as fifo]
            [msc2.inference :as inference]
            [msc2.stamp :as stamp]
            [msc2.truth :as truth]))

(def ^:const default-config
  "Minimal configuration derived from MSC2's Config.h defaults. The values are
  placeholders and mainly document the knobs that later milestones will honor."
  {:concept-capacity 1024
   :belief-queue-capacity 256
   :goal-queue-capacity 256
   :fifo-capacity 32
   :table-capacity 32
   :decision-threshold 0.5
   :motor-babbling-prob 0.25})

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
   {:config config
    :time 0
    :concepts {}
    :queues {:belief empty-queue
             :goal empty-queue}
    :fifo (fifo/empty-buffer (:fifo-capacity config))
    :derived []
    :shell {:operations {}}
    :history []}))

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

(defn- prepare-input
  [state input]
  (-> input
      (update :truth truth/normalize)
      (update :stamp stamp/normalize)
      (update :occurrence-time #(or % (:time state)))
      (assoc :queued-at (:time state)
             :creation-time (:time state))))

(defn- derive-with-new-event
  [state event]
  (let [existing (get-in state [:fifo :events])]
    (->> existing
         (filter #(< (:occurrence-time %) (:occurrence-time event)))
         (map #(inference/belief-induction % event)))))

(defn- queue-belief [state event]
  (let [derivations (derive-with-new-event state event)]
    (-> state
        (update :derived into derivations)
        (update :fifo fifo/enqueue event)
        (update-in [:queues :belief] conj event)
        (update :history conj {:time (:time state)
                               :stage :fifo/enqueue
                               :input (:term event)})
        (cond-> (seq derivations)
          (update :history conj {:time (:time state)
                                 :stage :induction/derived
                                 :input (map :term derivations)})))))

(defn- enqueue-input
  "Place the input event into the appropriate queue, tagging it with the cycle
  time so later stages can compute deltas."
  [state input]
  (if-let [queue-key (classify-input input)]
    (let [event (prepare-input state input)]
      (if (= queue-key :belief)
        (queue-belief state event)
        (update-in state [:queues queue-key] conj event)))
    state))

(defn- log-cycle
  "Append a lightweight log entry. This is intentionally small so it can stay
  enabled in tests; heavy tracing belongs in later instrumentation layers."
  [state stage & [input]]
  (update state :history
          conj {:time (:time state)
                :stage stage
                :input (or input :tick)}))

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
                    (log-cycle :cycle/start input))
         state'' (cond-> state'
                   input (enqueue-input input))]
     (log-cycle state'' :cycle/complete))))
