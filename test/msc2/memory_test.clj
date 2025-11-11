(ns msc2.memory-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.memory :as memory]))

(deftest record-spike-updates-concept
  (let [state {:concepts {}}
        event {:type :belief :term [:atom "A"]}
        state' (memory/record-spike state event)]
    (is (= event (get-in state' [:concepts [:atom "A"] :belief-spike])))))

(deftest record-derived-appends
  (let [state {:concepts {}}
        impl {:term [:implication :prediction [:atom "A"] [:atom "B"]]
              :target-term [:atom "B"]}]
    (is (= [impl]
           (get-in (memory/record-derived state impl) [:concepts [:atom "B"] :derived])))
    (is (= [impl]
           (get-in (memory/record-derived state impl) [:concepts [:atom "B"] :tables [:prediction [:atom "A"]]])))))

(deftest concepts-summary-empty-and-populated
  (is (= "No concepts yet." (memory/concepts-summary {})))
  (is (re-find #"belief=true"
               (memory/concepts-summary {[:atom "A"] {:belief-spike {:term [:atom "A"]}
                                                      :goal-spike nil
                                                      :derived []}}))))

(deftest rule-lookups
  (let [imp {:term [:implication :prediction [:atom "A"] [:atom "B"]]
             :target-term [:atom "B"]}
        state (memory/record-derived {:concepts {}} imp)]
    (is (= [imp] (memory/rules-for-antecedent (:concepts state) [:atom "A"])))
    (is (seq (memory/rules-for-consequent (:concepts state) [:atom "B"])))))
