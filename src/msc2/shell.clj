(ns msc2.shell
  "Shell that accepts either EDN maps or a small subset of `.nal` sentences."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [msc2.core :as core]
            [msc2.narsese :as narsese]))

(defn- sentence->input [{:keys [type term truth dt channel]}]
  (case type
    :belief {:type :belief :term term :truth truth :dt dt :channel channel}
    :goal {:type :goal :term term :truth truth :dt dt :channel channel}
    :question nil
    nil))

(defn- parse-edn [trimmed]
  (try {:command :input
        :value (edn/read-string trimmed)}
       (catch Exception _ nil)))

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
    :setopname (let [state' (assoc-in state [:shell :operations index] operation)]
                 {:state state'
                  :reply (format "Set op %d to %s" index operation)})
    :motorbabbling (let [state' (assoc-in state [:config :motor-babbling-prob] value)]
                     {:state state'
                      :reply (format "Motor babbling set to %.2f" value)})
    :concepts {:state state :reply "Concept dump not implemented yet."}
    :unknown {:state state :reply (format "Unknown command: %s" raw)}
    {:state state :reply (format "Unhandled command: %s" command)}))

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
    :question {:state state
               :reply (format "Question handling not implemented for %s" (:term value))}
    :narsese-command (apply-narsese-command state value)
    :input (try
             (let [state' (core/step state value)
                   summary (if value (:type value) :tick)]
               {:state state'
                :reply (format "cycle=%d queued=%s" (:time state') summary)})
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
