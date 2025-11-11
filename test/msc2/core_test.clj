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
    (is (= [] (:derived state)))
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

(deftest belief-induction-occurs-when-ordered-events-arrive
  (let [state (core/initial-state)
        a {:type :belief
           :term [:a]
           :truth {:frequency 1.0 :confidence 0.9}}
        b {:type :belief
           :term [:b]
           :truth {:frequency 1.0 :confidence 0.9}}
        state' (-> (core/step state a)
                   (core/step (assoc b :occurrence-time 2)))]
    (is (= 1 (count (:derived state'))))
    (let [impl (first (:derived state'))]
      (is (= [:implication :prediction [:a] [:b]] (:term impl)))
      (is (= 1 (:occurrence-time-offset impl)))
      (is (< (Math/abs (- 0.28223 (get-in impl [:truth :confidence]))) 1.0e-5)))))

(deftest concept-spikes-track-latest-events
  (let [state (core/initial-state)
        state' (core/step state {:type :belief
                                 :term [:atom "A"]
                                 :truth {:frequency 1.0 :confidence 0.9}})
        state'' (core/step state' {:type :goal
                                   :term [:atom "G"]
                                   :truth {:frequency 1.0 :confidence 0.9}})]
    (is (some? (get-in state'' [:concepts [:atom "A"] :belief-spike])))
    (is (some? (get-in state'' [:concepts [:atom "G"] :goal-spike])))))
