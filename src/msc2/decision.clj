(ns msc2.decision
  "Decision evaluation and motor babbling."
  (:require [clojure.walk :as walk]
            [msc2.memory :as memory]
            [msc2.truth :as truth]))

(set! *warn-on-reflection* true)

(defn- operation-token [term]
  (some #(when (and (vector? %) (= :op (first %))) (second %))
        (tree-seq vector? seq term)))

(defn- sorted-ops [operations babbling-ops]
  (let [ordered (->> operations
                     (sort-by key)
                     (map second)
                     (remove nil?))]
    (vec (if (and babbling-ops (pos? babbling-ops))
           (take babbling-ops ordered)
           ordered))))

(defn candidates
  "Return candidate rules for the given goal."
  [concepts goal operations]
  (let [valid-ops (when (seq operations)
                    (set (vals operations)))]
    (for [rule (memory/rules-for-consequent concepts (:term goal))
          :let [[_ _ antecedent _] (:term rule)
                op (operation-token antecedent)]
          :when (and op (or (nil? valid-ops) (valid-ops op)))]
      {:operation op
       :rule rule
       :goal goal
       :desire (truth/expectation (:truth rule))})))

(defn- scale-desire [candidate goal]
  (let [goal-factor (truth/expectation (:truth goal))]
    (update candidate :desire #(* goal-factor %))))

(defn evaluate
  "Select a learned decision or fall back to motor babbling."
  [concepts goal operations {:keys [decision-threshold motor-babbling-prob babbling-ops]}]
  (when goal
    (let [scored (map #(scale-desire % goal)
                      (candidates concepts goal operations))
          best (first (sort-by :desire > scored))
          op-pool (sorted-ops operations babbling-ops)]
      (cond
        (and best (>= (:desire best) decision-threshold))
        (assoc best :source :learned)

        (and (seq op-pool)
             (> motor-babbling-prob 0.0)
             (< (rand) motor-babbling-prob))
        (let [op (rand-nth op-pool)]
          {:operation op
           :goal goal
           :source :babble
           :desire 0.0})

        :else nil))))
