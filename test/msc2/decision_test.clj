(ns msc2.decision-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.decision :as decision]
            [msc2.memory :as memory]))

(def goal {:type :goal
           :term [:g]
           :truth {:frequency 1.0 :confidence 0.9}})

(def rule {:term [:implication :prediction
                  [:seq [:atom "B"] [:op "^right"]] [:g]]
           :truth {:frequency 1.0 :confidence 0.5}
           :occurrence-time-offset 1
           :target-term [:g]})

(deftest evaluate-selects-learned-rule
  (let [concepts (get (memory/record-derived {:concepts {}} rule) :concepts)
        decision (decision/evaluate concepts goal {1 "^right"}
                                    {:decision-threshold 0.1
                                     :motor-babbling-prob 0.0})]
    (is (= "^right" (:operation decision)))
    (is (= :learned (:source decision)))))
