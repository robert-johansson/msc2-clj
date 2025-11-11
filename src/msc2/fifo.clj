(ns msc2.fifo
  "FIFO sequencer for recent events (mirrors MSC2's FIFO.c).")

(set! *warn-on-reflection* true)

(def ^:const default-capacity 32)

(defn empty-buffer
  ([] (empty-buffer default-capacity))
  ([capacity]
   {:capacity capacity
    :events []}))

(defn enqueue
  [{:keys [capacity events] :as fifo} event]
  (let [events' (conj events event)
        overflow (max 0 (- (count events') capacity))]
    (assoc fifo :events (if (pos? overflow)
                          (subvec events' overflow)
                          events'))))

(defn pairs
  "Return all ordered pairs (older -> newer) that respect occurrence-time ordering."
  [{:keys [events]}]
  (for [i (range (count events))
        j (range (inc i) (count events))
        :let [older (events i)
              newer (events j)]
        :when (< (:occurrence-time older) (:occurrence-time newer))]
    [older newer]))
