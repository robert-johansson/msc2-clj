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

(defn script-lines [filename]
  (:lines
   (reduce (fn [{:keys [state lines]} line]
             (let [trim (str/trim line)]
               (if (seq trim)
                 (let [cmd (shell/parse-line trim)
                       result (shell/handle-command state cmd)
                       new-lines (cond-> lines
                                    (:reply result) (into (str/split-lines (:reply result))))]
                   {:state (:state result)
                    :lines new-lines})
                 {:state state :lines lines})))
           {:state (core/initial-state) :lines []}
           (str/split-lines (slurp (str "external/msc2/tests/" filename))))))

(deftest simple-implication-script-populates-concepts
  (let [state (run-script "simple_implication2.nal")
        table (get-in state [:concepts [:atom "G"] :tables [:prediction [:atom "A"]]])
        confidence (get-in (first table) [:truth :confidence])]
    (is (seq table))
    (is (<= 0.20 confidence 0.30))))

(deftest simple-implication-transcript-matches
  (let [lines (script-lines "simple_implication.nal")]
    (is (= ["Input: A. :|: occurrenceTime=1 Priority=1.000000 Truth: frequency=1.000000, confidence=0.900000"
            "Input: B. :|: occurrenceTime=2 Priority=1.000000 Truth: frequency=1.000000, confidence=0.900000"
            "Derived: dt=1.000000 <A =/> B>. Priority=0.641115 Truth: frequency=1.000000, confidence=0.282230"
            "Input: <A =/> B>?"
            "Answer: <A =/> B>. creationTime=2 Truth: frequency=1.000000, confidence=0.282230"]
           lines))))

(deftest simple-implication4-produces-sequence-rule
  (let [lines (script-lines "simple_implication4.nal")]
    (is (some #(re-find #"<\(A &/ \^left\) =/> G>" %) lines))
    (is (some #(re-find #"\^left executed" %) lines))
    (is (not-any? #(re-find #"<A =/> A>" %) lines))))
