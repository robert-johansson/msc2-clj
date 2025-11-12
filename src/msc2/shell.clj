(ns msc2.shell
  "Shell that accepts either EDN maps or a small subset of `.nal` sentences."
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [msc2.core :as core]
            [msc2.event :as evt]
            [msc2.memory :as memory]
            [msc2.narsese :as narsese]
            [msc2.term :as term]
            [msc2.truth :as truth]))

(defn- sentence->input [{:keys [type term truth dt channel]}]
  (case type
    :belief {:type :belief :term term :truth truth :dt dt :channel channel}
    :goal {:type :goal :term term :truth truth :dt dt :channel channel}
    :question nil
    nil))

(defn- punctuation [type]
  (case type
    :belief "."
    :goal "!"
    :question "?"
    "."))

(defn- truth->string [{:keys [frequency confidence]}]
  (format "Truth: frequency=%.6f, confidence=%.6f"
          (double (or frequency 0.0))
          (double (or confidence 0.0))))

(defn- term->fragment [term type channel]
  (let [body (try
               (term/term->string term)
               (catch Exception _
                 (str term)))]
    (str body
         (punctuation type)
       (when channel
         (str " " channel)))))

(defn- prepare-input-event [state input]
  (when input
    (let [time-context {:time (inc (:time state))}]
      (try
        (evt/prepare time-context input)
        (catch Exception _ nil)))))

(defn- parse-edn [trimmed]
  (try {:command :input
        :value (edn/read-string trimmed)}
       (catch Exception _ nil)))

(defn- format-input-line [{:keys [type term channel truth occurrence-time]}]
  (let [fragment (term->fragment term type channel)]
    (if (= :question type)
      (str "Input: " fragment)
      (str "Input: " fragment
           (when occurrence-time
             (format " occurrenceTime=%d" (long occurrence-time)))
           " Priority=1.000000 "
           (truth->string (or truth narsese/default-truth))))))

(defn- derived-priority [{:keys [priority truth]}]
  (double (or priority
              (when truth (truth/expectation truth))
              0.0)))

(defn- format-derived-line [{:keys [term truth occurrence-time-offset channel] :as derivation}]
  (let [dt (when occurrence-time-offset
             (format "dt=%.6f " (double occurrence-time-offset)))]
    (str "Derived: "
         (or dt "")
         (term->fragment term :belief channel)
         " Priority="
         (format "%.6f" (derived-priority derivation))
         " "
         (truth->string truth))))

(defn- new-derived-lines [old-state new-state]
  (let [old-count (count (:derived old-state))
        additions (drop old-count (:derived new-state))]
    (map format-derived-line additions)))

(defn- avg [xs]
  (when (seq xs)
    (/ (reduce + xs) (count xs))))

(defn- stats-summary [state]
  (let [concepts (vals (:concepts state))
        concept-count (count concepts)
        config (:config state)
        concept-capacity (double (max 1 (:concept-capacity config 1)))
        goal-capacity (double (max 1 (:goal-queue-capacity config 1)))
        avg-priority (/ (double (reduce + (map #(double (or (:priority %) 0.0)) concepts)))
                        concept-capacity)
        usage-usefulness (fn [{:keys [use-count last-used]}]
                           (let [use-count (double (or use-count 0.0))
                                 last-used (double (or last-used (:time state)))
                                 recency (max 0.0 (- (double (:time state)) last-used))
                                 usefulness (/ use-count (inc recency))]
                             (if (zero? usefulness)
                               0.0
                               (/ usefulness (inc usefulness)))))
        avg-usefulness (/ (double (reduce + (map usage-usefulness concepts)))
                          concept-capacity)
        goal-events (get-in state [:queues :goal :events])
        goal-count (count goal-events)
        avg-goal-priority (/ (double (reduce + (map #(double (or (:priority %) 0.0)) goal-events)))
                             goal-capacity)
        concept-hash (let [keys (keys (:concepts state))
                           buckets (max 1 (:concept-capacity config 1))]
                       (if (seq keys)
                         (->> keys
                              (map #(mod (hash %) buckets))
                              frequencies
                              vals
                              (apply max))
                         0))
        atom-hash (let [extract-atoms (fn extract [term]
                                        (cond
                                          (term/atom? term) [term]
                                          (term/op? term) [term]
                                          (vector? term) (mapcat extract (rest term))
                                          (sequential? term) (mapcat extract term)
                                          :else []))
                          atoms (mapcat #(extract-atoms (:term %)) concepts)
                          buckets (max 1 (* 2 (:concept-capacity config 1)))]
                      (if (seq atoms)
                        (->> atoms
                             (map #(mod (hash %) buckets))
                             frequencies
                             vals
                             (apply max))
                        0))]
    (str/join
     "\n"
     [(str "Statistics")
      "----------"
      "countConceptsMatchedTotal:\t0"
      "countConceptsMatchedMax:\t0"
      "countConceptsMatchedAverage:\t0"
      (format "currentTime:\t\t\t%d" (:time state))
      (format "total concepts:\t\t\t%d" concept-count)
      (format "current average concept priority:\t%.6f" avg-priority)
      (format "current average concept usefulness:\t%.6f" avg-usefulness)
      (format "curring goal events cnt:\t\t%d" goal-count)
      (format "current average goal event priority:\t%.6f" avg-goal-priority)
      (format "Maximum chain length in concept hashtable: %d" concept-hash)
      (format "Maximum chain length in atoms hashtable: %d" atom-hash)])))

(defn- queue-lines [events label]
  (if (seq events)
    (str "//*" label "\n"
         (str/join
          "\n"
          (for [e events]
            (format "%s: {\"priority\": %.6f, \"time\": %d } %s"
                    (term->fragment (:term e) (:type e) (:channel e))
                    (double (or (:priority e) 0.0))
                    (long (or (:occurrence-time e) 0))
                    (truth->string (:truth e)))))
         "\n//*done")
    (str "//*" label "\n//*done")))

(defn- trace-event->fragment [event]
  (when event
    (format "%s@%d"
            (term->fragment (:term event) (:type event) (:channel event))
            (long (or (:occurrence-time event) 0)))))

(defn- trace-spike->fragment [[term {:keys [belief-spike]}]]
  (when belief-spike
    (format "%s@%d"
            (term->fragment term :belief (:channel belief-spike))
            (long (or (:occurrence-time belief-spike) 0)))))

(defn- trace-lines [state]
  (when (get-in state [:shell :trace?])
    (let [fifo-lines (->> (get-in state [:fifo :events])
                          (take-last 5)
                          (map trace-event->fragment)
                          (remove nil?))
          spike-lines (->> (:concepts state)
                           (map trace-spike->fragment)
                           (remove nil?)
                           (take-last 5))]
      (-> []
          (cond-> (seq fifo-lines)
            (conj (str "trace fifo: " (str/join " | " fifo-lines))))
          (cond-> (seq spike-lines)
            (conj (str "trace spikes: " (str/join " | " spike-lines))))))))

(defn- sentence->command [sentence]
  (case (:type sentence)
    :question {:command :question :value sentence}
    (if-let [event (sentence->input sentence)]
      {:command :input :value event}
      {:command :error :message "Unsupported sentence"})))

(defn- parse-narsese-line [line]
  (try
    (when-let [parsed (narsese/parse-line line)]
      (case (:kind parsed)
        :sentence (sentence->command parsed)
        :command {:command :narsese-command
                  :value parsed}))
    (catch Exception ex
      {:command :error
       :message (.getMessage ex)})))

(defn- apply-narsese-command [state {:keys [command index operation value raw]}]
  (case command
    :reset (let [ops (get-in state [:shell :operations])
                 trace? (get-in state [:shell :trace?])
                 config (:config state)
                 fresh (-> (core/initial-state config)
                           (assoc-in [:shell :operations] ops)
                           (assoc-in [:shell :trace?] (boolean trace?)))]
             {:state fresh
              :reply "State reset."})
    :stats {:state state
            :reply (stats-summary state)}
    :volume (let [print? (>= (long value) 100)
                  state' (assoc-in state [:shell :print-derived?] print?)]
              {:state state'
               :reply (format "Volume set to %d" (long value))})
    :babblingops (let [state' (assoc-in state [:config :babbling-ops] value)]
                   {:state state'
                    :reply (format "Babbling ops limited to %d" value)})
    :motorbabbling-toggle (let [default (get-in state [:shell :motor-babbling-default]
                                               (get-in state [:config :motor-babbling-prob] 0.25))
                                new-prob (if value default 0.0)
                                state' (assoc-in state [:config :motor-babbling-prob] new-prob)]
                            {:state state'
                             :reply (if value "Motor babbling enabled" "Motor babbling disabled")})
    :setopname (let [state' (assoc-in state [:shell :operations index] operation)]
                 {:state state'
                  :reply (format "Set op %d to %s" index operation)})
    :motorbabbling (let [state' (-> state
                                    (assoc-in [:config :motor-babbling-prob] value)
                                    (assoc-in [:shell :motor-babbling-default] value))]
                     {:state state'
                      :reply (format "Motor babbling set to %.2f" value)})
    :trace (let [state' (assoc-in state [:shell :trace?] (boolean value))]
             {:state state'
              :reply (if (get-in state' [:shell :trace?])
                       "Trace logging enabled"
                       "Trace logging disabled")})
    :concepts {:state state
               :reply (memory/concepts-summary (:concepts state))}
    :cycling-belief {:state state
                     :reply (queue-lines (get-in state [:queues :belief :events])
                                         "cycling_belief_events")}
    :cycling-goal {:state state
                   :reply (queue-lines (get-in state [:queues :goal :events])
                                       "cycling_goal_events")}
    :unknown {:state state :reply (format "Unknown command: %s" raw)}
    {:state state :reply (format "Unhandled command: %s" command)}))

(defn- format-answer-line [entry]
  (let [creation (long (or (:creation-time entry) 0))]
    (str "Answer: "
         (term->fragment (:term entry) :belief nil)
         " creationTime="
         creation
         " "
         (truth->string (:truth entry)))))

(defn- answer-question [state {:keys [term]}]
  (let [[_ cop antecedent consequent] term
        concept (get-in state [:concepts consequent])]
    (if (and (= :prediction cop) concept)
      (if-let [entry (first (get-in concept [:tables [:prediction antecedent]]))]
        {:state state
         :reply (format-answer-line entry)}
        {:state state :reply "Answer: None."})
      {:state state :reply "Answer: None."})))

(defn parse-line
  "Interpret a user-provided line."
  [line]
  (cond
    (nil? line) {:command :quit}
    (str/blank? line) {:command :noop}
    :else (let [trimmed (str/trim line)
                lowered (str/lower-case trimmed)]
            (cond
              (#{"quit" "exit"} lowered) {:command :quit}
              (re-matches #"[0-9]+" trimmed)
              {:command :cycles
               :value (Long/parseLong trimmed)}
              :else (let [first-char (first trimmed)
                          edn-first? (and first-char (#{\{ \[ \( \" \: \#} first-char))
                          primary (if edn-first?
                                    (parse-edn trimmed)
                                    (parse-narsese-line trimmed))
                          fallback (if edn-first?
                                     (parse-narsese-line trimmed)
                                     (parse-edn trimmed))]
                      (or primary
                          fallback
                          {:command :error
                           :message "Unrecognized input"}))))))

(defn- decision-lines [old-state new-state]
  (let [old-count (count (:decisions old-state))
        new-decisions (drop old-count (:decisions new-state))]
    (mapcat
     (fn [{:keys [operation source desire rule]}]
       (if rule
         (let [[_ _ antecedent _] (:term rule)
               precondition (term/precondition-term antecedent)
               antecedent-event (when precondition
                                  (get-in new-state [:concepts precondition :belief-spike]))
               precondition-term (when precondition
                                   (term->fragment precondition :belief (:channel antecedent-event)))
               precondition-truth (or (:truth antecedent-event) narsese/default-truth)
               precondition-time (long (or (:occurrence-time antecedent-event)
                                           (:time new-state) 0))
               rule-truth (:truth rule)
               dt (double (or (:occurrence-time-offset rule) 0.0))]
           [(format "decision expectation=%.6f implication: %s Truth: frequency=%.6f confidence=%.6f dt=%.6f precondition: %s Truth: frequency=%.6f confidence=%.6f occurrenceTime=%d"
                    (double (or desire 0.0))
                    (term->fragment (:term rule) :belief nil)
                    (get rule-truth :frequency 0.0)
                    (get rule-truth :confidence 0.0)
                    dt
                    (or precondition-term "None")
                    (get precondition-truth :frequency 0.0)
                    (get precondition-truth :confidence 0.0)
                    precondition-time)
            (format "%s executed with args "
                    (or operation "^op"))])
         [(format "decision motor-babble desire=%.6f source=%s"
                  (double (or desire 0.0))
                  (name (or source :babble)))
          (format "%s executed with args "
                  (or operation "^op"))]))
     new-decisions)))

(defn- advance-cycles [state n]
  (loop [state state
         remaining n]
    (if (pos? remaining)
      (recur (core/step state) (dec remaining))
      state)))

(defn handle-command
  "Apply a parsed command to the running state. Returns a map containing at
  least `:state` and optionally `:reply` or `:quit?`."
  [state {:keys [command value message] :as cmd}]
  (case command
    :noop {:state state}
    :quit {:state state :quit? true :reply (str (stats-summary state) "\nBye.")}
    :info {:state state :reply (or message "(info)")}
    :error {:state state
            :reply (format "Parse error: %s" (or message "unknown"))}
    :question (let [{state' :state reply :reply} (answer-question state value)
                    lines (cond-> [(format-input-line value)]
                            reply (conj reply))]
                {:state state'
                 :reply (str/join "\n" lines)})
    :narsese-command (apply-narsese-command state value)
    :cycles (let [n (max 0 (long value))
                  state' (advance-cycles state n)]
              {:state state'
               :reply (format "performing %d inference steps:\ndone with %d additional inference steps."
                              n n)})
    :input (try
             (let [prepared (when value (prepare-input-event state value))
                   state' (core/step state value)
                   input-line (when prepared
                                (format-input-line prepared))
                   print-derived? (get-in state' [:shell :print-derived?] true)
                   derived-lines (when print-derived?
                                   (seq (new-derived-lines state state')))
                   decision-lines (decision-lines state state')
                   trace-output (trace-lines state')
                   lines (cond-> []
                            input-line (conj input-line)
                            derived-lines (into derived-lines)
                            (seq decision-lines) (into decision-lines)
                            (seq trace-output) (into trace-output))]
               {:state state'
                :reply (if (seq lines)
                         (str/join "\n" lines)
                         (format "cycle=%d" (:time state')))})
             (catch Exception ex
               {:state state
                :reply (format "Input rejected: %s" (.getMessage ex))}))
    {:state state :reply (format "Unknown command: %s" cmd)}))

(defn repl-loop
  "Run a minimal blocking REPL that reads EDN maps until the user quits."
  ([] (repl-loop (core/initial-state)))
  ([state]
   (println "MSC2 shell (placeholder). Enter EDN maps or 'quit' to exit.")
   (loop [state state]
     (print "msc2> ")
     (flush)
     (let [line (read-line)
           {:keys [state reply quit?]} (handle-command state (parse-line line))]
       (when reply
         (println reply))
       (if quit?
         state
         (recur state))))))

(defn -main
  "Entry point used by `clj -M -m msc2.shell`."
  [& _]
  (repl-loop)
  (shutdown-agents))
