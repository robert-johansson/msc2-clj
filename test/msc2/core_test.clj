(ns msc2.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc2.core :as core]))

(defn- queue? [q]
  (instance? clojure.lang.PersistentQueue q))

(deftest initial-state-structure
  (let [state (core/initial-state)]
    (is (= core/default-config (:config state)))
    (is (= 0 (:time state)))
    (is (queue? (get-in state [:queues :belief])))
    (is (queue? (get-in state [:queues :goal])))
    (is (vector? (:history state)))))

(deftest step-increments-time-and-logs
  (let [state (core/initial-state)
        ticked (core/step state)]
    (is (= 1 (:time ticked)))
    (is (= {:time 1 :stage :cycle/complete :input :tick}
           (last (:history ticked))))))

(deftest step-enqueues-beliefs
  (let [state (core/initial-state)
        event {:type :belief
               :term [:inherit 'A 'B]
               :truth {:frequency 1.0 :confidence 0.9}}
        ticked (core/step state event)
        queued (peek (get-in ticked [:queues :belief]))]
    (testing "queue time is recorded"
      (is (= 1 (:queued-at queued))))
    (testing "original payload preserved"
      (is (= (:term event) (:term queued)))
      (is (= (:truth event) (:truth queued)))
      (is (= 1 (:occurrence-time queued)))
      (is (= [] (get-in queued [:stamp :evidence]))))))
