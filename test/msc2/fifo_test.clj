(ns msc2.fifo-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.fifo :as fifo]))

(deftest enqueue-respects-capacity-and-pairs
  (let [{:keys [fifo pairs]} (fifo/enqueue (fifo/empty-buffer 2) {:occurrence-time 1})
        {:keys [fifo pairs]} (fifo/enqueue fifo {:occurrence-time 2})
        {:keys [fifo pairs]} (fifo/enqueue fifo {:occurrence-time 3})]
    (is (= [{:occurrence-time 2}
            {:occurrence-time 3}]
           (fifo/events fifo)))
    (is (= [[{:occurrence-time 2} {:occurrence-time 3}]]
           pairs))))
