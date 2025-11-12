(ns msc2.decision-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.decision :as decision]
            [msc2.memory :as memory]))

(def goal {:type :goal
           :term [:g]
           :truth {:frequency 1.0 :confidence 0.9}
           :occurrence-time 12})

(def rule {:term [:implication :prediction
                  [:seq [:atom "B"] [:op "^right"]] [:g]]
           :truth {:frequency 1.0 :confidence 0.5}
           :occurrence-time-offset 1
           :target-term [:g]})

(defn- record-spike [concepts term time]
  (get (memory/record-spike {:concepts concepts}
                            {:type :belief
                             :term term
                             :truth {:frequency 1.0 :confidence 0.9}
                             :occurrence-time time
                             :creation-time time})
       :concepts))

(deftest evaluate-selects-learned-rule
  (let [concepts (-> {:concepts {}}
                     (memory/record-derived rule)
                     :concepts
                     (record-spike [:atom "B"] 10))
        decision (decision/evaluate concepts goal {1 "^right"}
                                    {:decision-threshold 0.1
                                     :motor-babbling-prob 0.0})]
    (is (= "^right" (:operation decision)))
    (is (= :learned (:source decision)))))

(deftest evaluate-requires-precondition-spike
  (let [concepts (get (memory/record-derived {:concepts {}} rule) :concepts)]
    (is (nil?
         (decision/evaluate concepts goal {1 "^right"}
                             {:decision-threshold 0.1
                              :motor-babbling-prob 0.0})))))

(deftest evaluate-demands-sequence-precondition
  (let [seq-term [:seq [:atom "S"] [:atom "C"]]
        seq-rule {:term [:implication :prediction
                         [:seq seq-term [:op "^right"]] [:g]]
                  :truth {:frequency 1.0 :confidence 0.9}
                  :occurrence-time-offset 2
                  :target-term [:g]}
        base-concepts (-> {:concepts {}}
                          (memory/record-derived seq-rule)
                          :concepts)
        ;; Only sample spike present -> rule should not fire
        sample-concepts (record-spike base-concepts [:atom "S"] 5)]
    (is (nil? (decision/evaluate sample-concepts goal {1 "^right"}
                                 {:decision-threshold 0.1
                                  :motor-babbling-prob 0.0})))
    ;; Full sequence spike present -> rule can fire
    (let [seq-concepts (record-spike base-concepts seq-term 6)
          decision (decision/evaluate seq-concepts goal {1 "^right"}
                                      {:decision-threshold 0.1
                                       :motor-babbling-prob 0.0})]
      (is (= "^right" (:operation decision)))
      (is (= :learned (:source decision))))))
