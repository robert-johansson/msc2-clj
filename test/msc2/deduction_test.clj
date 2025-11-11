(ns msc2.deduction-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.deduction :as ded]))

(def belief {:type :belief
             :term [:a]
             :truth {:frequency 1.0 :confidence 0.9}
             :occurrence-time 1})

(def implication {:term [:implication :prediction [:a] [:b]]
                  :truth {:frequency 1.0 :confidence 0.3}
                  :occurrence-time-offset 2
                  :stamp {:evidence [1 2]}})

(deftest belief-produces-prediction
  (let [pred (ded/belief->prediction belief implication)]
    (is (= [:b] (:term pred)))
    (is (= 3 (:occurrence-time pred)))
    (is (= :prediction (:source pred)))))

(deftest goal-produces-subgoal
  (let [goal {:type :goal
              :term [:b]
              :truth {:frequency 1.0 :confidence 0.9}
              :occurrence-time 5}
        subgoal (ded/goal->subgoal goal implication)]
    (is (= [:a] (:term subgoal)))
    (is (= 5 (:occurrence-time subgoal)))
    (is (= :subgoal (:source subgoal)))))
