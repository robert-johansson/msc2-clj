(ns msc2.shell
  "Shell that accepts either EDN maps or a small subset of `.nal` sentences."
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
        avg-priority (double (or (avg (keep :priority concepts)) 0.0))
        belief-count (count (get-in state [:queues :belief :events]))
        goal-count (count (get-in state [:queues :goal :events]))]
    (str/join
     "\n"
     [(str "Statistics")
      "----------"
      (format "currentTime:\t\t\t%d" (:time state))
      (format "total concepts:\t\t\t%d" concept-count)
      (format "current average concept priority:\t%.6f" avg-priority)
      (format "current belief events:\t\t%d" belief-count)
      (format "current goal events:\t\t%d" goal-count)])))

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
                 config (:config state)
                 fresh (-> (core/initial-state config)
                           (assoc-in [:shell :operations] ops))]
             {:state fresh
              :reply "State reset."})
    :stats {:state state
            :reply (stats-summary state)}
    :setopname (let [state' (assoc-in state [:shell :operations index] operation)]
                 {:state state'
                  :reply (format "Set op %d to %s" index operation)})
    :motorbabbling (let [state' (assoc-in state [:config :motor-babbling-prob] value)]
                     {:state state'
                      :reply (format "Motor babbling set to %.2f" value)})
    :concepts {:state state
               :reply (memory/concepts-summary (:concepts state))}
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
    (map (fn [{:keys [operation source desire]}]
           (format "%s executed desire=%.3f source=%s"
                   (or operation "^op")
                   (double (or desire 0.0))
                   (name (or source :unknown))))
         new-decisions)))

(defn handle-command
  "Apply a parsed command to the running state. Returns a map containing at
  least `:state` and optionally `:reply` or `:quit?`."
  [state {:keys [command value message] :as cmd}]
  (case command
    :noop {:state state :reply "(noop)"}
    :quit {:state state :quit? true :reply "Bye."}
    :info {:state state :reply (or message "(info)")}
    :error {:state state
            :reply (format "Parse error: %s" (or message "unknown"))}
    :question (let [{state' :state reply :reply} (answer-question state value)
                    lines (cond-> [(format-input-line value)]
                            reply (conj reply))]
                {:state state'
                 :reply (str/join "\n" lines)})
    :narsese-command (apply-narsese-command state value)
    :input (try
             (let [prepared (when value (prepare-input-event state value))
                   state' (core/step state value)
                   input-line (when prepared
                                (format-input-line prepared))
                   derived-lines (new-derived-lines state state')
                   decision-lines (decision-lines state state')
                   lines (cond-> []
                            input-line (conj input-line)
                            (seq derived-lines) (into derived-lines)
                            (seq decision-lines) (into decision-lines))]
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
