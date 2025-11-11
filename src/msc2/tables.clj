(ns msc2.tables
  "Implication table helpers mirroring MSC2's Table.c logic."
  (:require [msc2.truth :as truth]))

(set! *warn-on-reflection* true)

(def ^:const table-capacity 32)

(defn- expectation [imp]
  (truth/expectation (:truth imp)))

(defn add-implication
  "Insert or revise an implication in a bounded table."
  [table implication]
  (let [existing (some #(when (= (:term %) (:term implication)) %) table)
        table-without (if existing (remove #(= (:term %) (:term implication)) table) table)
        implication' (if existing
                       (assoc implication
                              :truth (truth/revision (:truth existing) (:truth implication)))
                       implication)
        sorted (->> (conj (vec table-without) implication')
                    (sort-by expectation >)
                    vec)]
    (if (> (count sorted) table-capacity)
      (subvec sorted 0 table-capacity)
      sorted)))
