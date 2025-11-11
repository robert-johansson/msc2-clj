(ns msc2.deduction
  "Belief and goal deduction helpers."
  (:require [msc2.event :as event]
            [msc2.truth :as truth]))

(set! *warn-on-reflection* true)

(defn belief->prediction
  "Given a belief event and implication, derive a predicted belief."
  [belief implication]
  (let [[_ _ _ consequent] (:term implication)
        truth' (truth/deduction (:truth belief) (:truth implication))
        occurrence (+ (:occurrence-time belief)
                      (:occurrence-time-offset implication))]
    {:type :belief
     :term consequent
     :truth truth'
     :occurrence-time occurrence
     :stamp (:stamp implication)
     :source :prediction}))

(defn goal->subgoal
  "Given a goal event on the consequent and implication, derive a subgoal to satisfy the antecedent."
  [goal implication]
  (let [[_ _ antecedent _] (:term implication)
        truth' (truth/deduction (:truth goal) (:truth implication))]
    {:type :goal
     :term antecedent
     :truth truth'
     :occurrence-time (:occurrence-time goal)
     :stamp (:stamp implication)
     :source :subgoal}))
