(ns msc2.fifo-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.fifo :as fifo]))

(deftest enqueue-respects-capacity
  (let [buf (-> (fifo/empty-buffer 2)
                (fifo/enqueue {:occurrence-time 1})
                (fifo/enqueue {:occurrence-time 2})
                (fifo/enqueue {:occurrence-time 3}))]
    (is (= [{:occurrence-time 2}
            {:occurrence-time 3}]
           (:events buf)))))

(deftest pairs-only-return-ordered
  (let [buf {:events [{:occurrence-time 1 :id :a}
                      {:occurrence-time 3 :id :b}
                      {:occurrence-time 2 :id :c}]}]
    (is (= [[{:occurrence-time 1 :id :a}
             {:occurrence-time 3 :id :b}]
            [{:occurrence-time 1 :id :a}
             {:occurrence-time 2 :id :c}]]
           (fifo/pairs buf)))))
