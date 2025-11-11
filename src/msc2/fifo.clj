(ns msc2.fifo
  "FIFO sequencer for recent events (mirrors MSC2's FIFO.c).")

(set! *warn-on-reflection* true)

(def ^:const default-capacity 32)

(defn empty-buffer
  ([] (empty-buffer default-capacity))
  ([capacity]
   {:capacity capacity
    :events []}))

(defn- trim-events [events capacity]
  (if (>= (count events) capacity)
    (subvec events 1)
    events))

(defn enqueue
  "Insert `event` into the buffer and return {:fifo updated :pairs new-pairs}."
  [{:keys [capacity events] :as fifo} event]
  (let [base (trim-events events capacity)
        pairs (->> base
                   (filter #(< (:occurrence-time %) (:occurrence-time event)))
                   (map #(vector % event))
                   vec)
        updated (conj base event)]
    {:fifo (assoc fifo :events updated)
     :pairs pairs}))

(defn events
  [fifo]
  (:events fifo))
