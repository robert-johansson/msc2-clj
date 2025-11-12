(ns msc2.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc2.core :as core]
            [msc2.fifo :as fifo]
            [msc2.queue :as q]
            [msc2.term :as term]))

(defn- queue? [q]
  (map? q))

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
        queued (first (q/all-events (get-in ticked [:queues :belief])))]
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
      (is (= [:b] (:consequent impl)))
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

(deftest predictions-and-subgoals-are-produced
  (let [state (-> (core/initial-state)
                  (core/step {:type :belief
                              :term [:a]
                              :truth {:frequency 1.0 :confidence 0.9}})
                  (core/step {:type :belief
                              :term [:b]
                              :occurrence-time 2
                              :truth {:frequency 1.0 :confidence 0.9}})
                  (core/step {:type :belief
                              :term [:a]
                              :occurrence-time 3
                              :truth {:frequency 1.0 :confidence 0.9}}))]
    (is (seq (:predictions state))))
  (let [state (-> (core/initial-state)
                  (core/step {:type :belief
                              :term [:a]
                              :truth {:frequency 1.0 :confidence 0.9}})
                  (core/step {:type :belief
                              :term [:b]
                              :occurrence-time 2
                              :truth {:frequency 1.0 :confidence 0.9}})
                  (core/step {:type :goal
                              :term [:b]
                              :truth {:frequency 1.0 :confidence 0.9}}))]
    (is (seq (:subgoals state)))))

(defn- belief-event
  [term occ-time]
  {:type :belief
   :term term
   :truth {:frequency 1.0 :confidence 0.9}
   :stamp {:evidence [occ-time]}
   :occurrence-time occ-time
   :creation-time occ-time})

(defn- enqueue-all
  [events]
  (reduce (fn [buf event]
            (:fifo (fifo/enqueue buf event)))
          (fifo/empty-buffer)
          events))

(deftest sequence-derivations-include-gapped-triples-ending-with-ops
  (let [sample (belief-event (term/atom-term "sample") 1)
        noise (belief-event (term/atom-term "noise") 2)
        comparison (belief-event (term/atom-term "comparison") 3)
        op (belief-event (term/op-term "^left") 4)
        goal (belief-event (term/atom-term "G") 5)
        fifo (enqueue-all [sample noise comparison op goal])
        {:keys [implications]} (@#'msc2.core/sequence-derivations fifo goal 16)
        antecedents (set (map #(get-in % [:term 2]) implications))
        target (term/seq-term
                (term/seq-term (:term sample) (:term comparison))
                (:term op))]
    (is (contains? antecedents target))
    (is (seq implications))))

(deftest sequence-derivations-reject-internal-operations
  (let [old-op (belief-event (term/op-term "^prep") 1)
        sample (belief-event (term/atom-term "sample") 2)
        comparison (belief-event (term/atom-term "comparison") 3)
        op (belief-event (term/op-term "^left") 4)
        goal (belief-event (term/atom-term "G") 5)
        fifo (enqueue-all [old-op sample comparison op goal])
        seq-impls (@#'msc2.core/sequence-derivations fifo goal 16)
        antecedents (set (map #(get-in % [:term 2]) seq-impls))
        forbidden (term/seq-term
                   (term/seq-term (:term old-op) (:term sample))
                   (:term op))]
    (is (not (contains? antecedents forbidden)))
    (is (seq seq-impls))))

(deftest sequence-spikes-are-recorded
  (let [state (-> (core/initial-state)
                  (core/step {:type :belief
                              :term (term/atom-term "A1")
                              :truth {:frequency 1.0 :confidence 0.9}})
                  (core/step {:type :belief
                              :term (term/atom-term "B1")
                              :truth {:frequency 1.0 :confidence 0.9}})
                  (core/step {:type :belief
                              :term (term/op-term "^op")
                              :truth {:frequency 1.0 :confidence 0.9}}))
        seq-term [:seq [:atom "A1"] [:atom "B1"]]
        spike (get-in state [:concepts seq-term :belief-spike])]
    (is (some? spike))
    (is (= seq-term (:term spike)))))
