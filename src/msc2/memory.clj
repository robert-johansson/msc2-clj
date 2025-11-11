(ns msc2.memory
  "Concept storage helpers (spikes + derived implications)."
  (:require [clojure.string :as str]
            [msc2.tables :as tables]))

(set! *warn-on-reflection* true)

(def ^:const max-derived 32)

(defn- ensure-concept [concepts term]
  (if (contains? concepts term)
    concepts
    (assoc concepts term {:belief-spike nil
                          :goal-spike nil
                          :derived []
                          :tables {}})))

(defn record-spike [state event]
  (let [term (:term event)
        key (case (:type event)
              :belief :belief-spike
              :goal :goal-spike)]
    (update state :concepts
            (fn [concepts]
              (let [concepts (ensure-concept concepts term)]
                (assoc-in concepts [term key] event))))))

(defn- clamp-derived [entries]
  (if (> (count entries) max-derived)
    (vec (take-last max-derived entries))
    entries))

(defn- table-key [implication]
  (let [[_ cop antecedent _] (:term implication)]
    (case cop
      :prediction [:prediction antecedent]
      nil)))

(defn- update-table [tables implication]
  (if-let [key (table-key implication)]
    (update tables key #(tables/add-implication (or % []) implication))
    tables))

(defn record-derived [state implication]
  (let [target (or (:target-term implication)
                   (:consequent implication)
                   (:term implication))]
    (update state :concepts
            (fn [concepts]
              (let [concepts (ensure-concept concepts target)]
                (-> concepts
                    (update-in [target :derived]
                               (fn [entries]
                                 (-> entries
                                     (or [])
                                     (conj implication)
                                     clamp-derived)))
                    (update-in [target :tables]
                               #(update-table % implication))))))))

(defn concepts-summary [concepts]
  (if (empty? concepts)
    "No concepts yet."
    (str/join
     "\n"
     (for [[term {:keys [belief-spike goal-spike derived]}] concepts]
       (format "%s belief=%s goal=%s derived=%d"
               term
               (boolean belief-spike)
               (boolean goal-spike)
               (count derived))))))
