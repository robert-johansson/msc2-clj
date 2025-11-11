(ns msc2.shell
  "Simple EDN-driven shell to exercise the MSC2 reducer during Milestone 1.

  Later milestones will replace the EDN stub with a full `.nal` interpreter,
  but exposing a CLI now keeps the project runnable end-to-end."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [msc2.core :as core]))

(defn parse-line
  "Interpret a user-provided line. Returns a command map consumed by
  `handle-command`."
  [line]
  (cond
    (nil? line) {:command :quit}
    (str/blank? line) {:command :noop}
    :else (let [trimmed (str/trim line)
                lowered (str/lower-case trimmed)]
            (cond
              (#{"quit" "exit"} lowered) {:command :quit}
              :else (try {:command :input
                          :value (edn/read-string trimmed)}
                         (catch Exception ex
                           {:command :error
                            :message (.getMessage ex)}))))))

(defn handle-command
  "Apply a parsed command to the running state. Returns a map containing at
  least `:state` and optionally `:reply` or `:quit?`."
  [state {:keys [command value message] :as cmd}]
  (case command
    :noop {:state state :reply "(noop)"}
    :quit {:state state :quit? true :reply "Bye."}
    :error {:state state
            :reply (format "Parse error: %s" (or message "unknown"))}
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
