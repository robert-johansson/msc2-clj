(ns msc2.queue-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.queue :as q]))

(def conf {:capacity 2 :durability 0.5 :threshold 0.2})

(deftest enqueue-trims-and-decays
  (let [queue (-> (q/empty-queue conf)
                  (q/enqueue {:term :a :priority 1.0})
                  (q/enqueue {:term :b :priority 0.8})
                  (q/enqueue {:term :c :priority 0.9}))
        events (q/all-events queue)]
    (is (= [:a :c] (map :term events)))
    (let [decayed (q/decay queue)]
      (is (> (count (q/all-events decayed)) 0)))))
