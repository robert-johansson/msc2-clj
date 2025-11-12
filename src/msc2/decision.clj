(ns msc2.decision
  "Decision evaluation and motor babbling."
  (:require [msc2.memory :as memory]
            [msc2.term :as term]
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

(defn- recent-spike?
  [goal-event spike max-gap]
  (let [gap (double (max 0 (or max-gap 0)))
        goal-time (:occurrence-time goal-event)
        spike-time (:occurrence-time spike)]
    (if (and goal-time spike-time)
      (<= (Math/abs (double (- goal-time spike-time))) gap)
      true)))

(defn- applicable-precondition
  [concepts goal antecedent max-gap]
  (let [precondition (term/precondition-term antecedent)
        spike (when precondition
                (get-in concepts [precondition :belief-spike]))]
    (when (and spike (recent-spike? goal spike max-gap))
      {:precondition precondition
       :precondition-event spike})))

(defn candidates
  "Return candidate rules for the given goal."
  [concepts goal operations {:keys [max-induction-gap] :or {max-induction-gap 16} :as _config}]
  (let [valid-ops (when (seq operations)
                    (set (vals operations)))]
    (for [rule (memory/rules-for-consequent concepts (:term goal))
          :let [[_ _ antecedent _] (:term rule)
                op (operation-token antecedent)
                {:keys [precondition precondition-event]} (applicable-precondition concepts goal antecedent max-induction-gap)]
          :when (and op precondition precondition-event
                     (or (nil? valid-ops) (valid-ops op)))]
      {:operation op
       :rule rule
       :goal goal
       :precondition precondition
       :precondition-event precondition-event
       :desire (truth/expectation (:truth rule))})))

(defn- scale-desire [candidate goal]
  (let [goal-factor (truth/expectation (:truth goal))]
    (update candidate :desire #(* goal-factor %))))

(defn evaluate
  "Select a learned decision or fall back to motor babbling."
  [concepts goal operations {:keys [decision-threshold motor-babbling-prob babbling-ops] :as config}]
  (when goal
    (let [scored (map #(scale-desire % goal)
                      (candidates concepts goal operations config))
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
