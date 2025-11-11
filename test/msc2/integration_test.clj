(ns msc2.integration-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [msc2.core :as core]
            [msc2.shell :as shell]))

(defn run-script [filename]
  (reduce (fn [state line]
            (let [trim (str/trim line)]
              (if (seq trim)
                (let [cmd (shell/parse-line trim)
                      result (shell/handle-command state cmd)]
                  (or (:state result) state))
                state)))
          (core/initial-state)
          (str/split-lines (slurp (str "external/msc2/tests/" filename)))))

(deftest simple-implication-script-populates-concepts
  (let [state (run-script "simple_implication2.nal")
        table (get-in state [:concepts [:atom "G"] :tables [:prediction [:atom "A"]]])]
    (is (seq table))))
