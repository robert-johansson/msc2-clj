(ns msc2.queue
  "Bounded priority queues for belief/goal events.")

(set! *warn-on-reflection* true)

(defn empty-queue
  [{:keys [capacity durability threshold]}]
  {:capacity capacity
   :durability durability
   :threshold threshold
   :events []})

(defn- trim [events capacity]
  (if (> (count events) capacity)
    (subvec (vec events) 0 capacity)
    (vec events)))

(defn enqueue
  [queue event]
  (let [event (assoc event :priority (or (:priority event) 1.0))]
    (update queue :events
            (fn [events]
              (let [updated (conj (vec events) event)
                    sorted (vec (sort-by :priority > updated))]
                (trim sorted (:capacity queue)))))))

(defn decay
  [queue]
  (update queue :events
          (fn [events]
            (->> events
                 (map #(update % :priority (fn [p] (* p (:durability queue)))))
                 (filter #(>= (:priority %) (:threshold queue)))
                 vec))))

(defn all-events [queue]
  (:events queue))
