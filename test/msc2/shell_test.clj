(ns msc2.shell-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.core :as core]
            [msc2.shell :as shell]))

(deftest parse-line-handles-core-commands
  (is (= {:command :quit} (shell/parse-line "quit")))
  (is (= {:command :noop} (shell/parse-line "   \t")))
  (is (= {:command :input :value {:type :belief}}
         (shell/parse-line "{:type :belief}"))))

(deftest parse-line-narsese-sentences
  (let [{:keys [command value]} (shell/parse-line "A. :|:")]
    (is (= :input command))
    (is (= :belief (:type value)))
    (is (= ":|:" (:channel value))))
  (let [{:keys [command value]} (shell/parse-line "<A =/> B>?")]
    (is (= :question command))
    (is (= [:implication :prediction [:atom "A"] [:atom "B"]]
           (:term value)))))

(deftest handle-command-updates-state
  (let [state (core/initial-state)
        {:keys [state reply]} (shell/handle-command state {:command :input
                                                           :value {:type :belief
                                                                   :term :a
                                                                   :truth {:frequency 1.0
                                                                           :confidence 0.9}}})]
    (is (= 1 (:time state)))
    (is (re-find #"Input:" reply))
    (is (re-find #"occurrenceTime=1" reply))))

(deftest handle-command-rejects-bad-input
  (let [state (core/initial-state)
        response (shell/handle-command state {:command :input :value {}})]
    (is (= 0 (:time (:state response))))
    (is (re-find #"Input rejected" (:reply response)))))

(deftest numeric-input-advances-cycles
  (let [state (core/initial-state)
        {:keys [state reply]} (shell/handle-command state {:command :cycles :value 3})]
    (is (= 3 (:time state)))
    (is (re-find #"performing 3" reply))))

(deftest narsese-commands-update-state
  (let [state (core/initial-state)
        {:keys [state reply]} (shell/handle-command state {:command :narsese-command
                                                           :value {:command :setopname
                                                                   :index 1
                                                                   :operation "^left"}})]
    (is (= "^left" (get-in state [:shell :operations 1])))
    (is (re-find #"Set op" reply)))
  (let [state (core/initial-state)
        {:keys [state reply]} (shell/handle-command state {:command :narsese-command
                                                           :value {:command :motorbabbling
                                                                   :value 0.9}})]
    (is (= 0.9 (get-in state [:config :motor-babbling-prob])))
    (is (re-find #"Motor babbling" reply)))
  (let [state (assoc (core/initial-state) :time 5)
        {:keys [state reply]} (shell/handle-command state {:command :narsese-command
                                                           :value {:command :stats}})]
    (is (= 5 (:time state)))
    (is (re-find #"Statistics" reply)))
  (let [state (assoc (core/initial-state) :time 42)
        {:keys [state reply]} (shell/handle-command state {:command :narsese-command
                                                           :value {:command :reset}})]
    (is (= 0 (:time state)))
    (is (re-find #"State reset" reply)))
  (let [{:keys [state reply]} (shell/handle-command (core/initial-state)
                                                   {:command :narsese-command
                                                    :value {:command :volume
                                                            :value 0}})]
    (is (false? (get-in state [:shell :print-derived?])))
    (is (re-find #"Volume set" reply)))
  (let [{:keys [state reply]} (shell/handle-command (core/initial-state)
                                                   {:command :narsese-command
                                                    :value {:command :babblingops
                                                            :value 1}})]
    (is (= 1 (get-in state [:config :babbling-ops])))
    (is (re-find #"Babbling ops" reply)))
  (let [{:keys [state reply]} (shell/handle-command (core/initial-state)
                                                   {:command :narsese-command
                                                    :value {:command :motorbabbling-toggle
                                                            :value false}})]
    (is (zero? (get-in state [:config :motor-babbling-prob])))
    (is (re-find #"disabled" reply)))
  (let [state (-> (core/initial-state)
                  (core/step {:type :belief
                              :term [:atom "A"]
                              :truth {:frequency 1.0 :confidence 0.9}}))
        {:keys [reply]} (shell/handle-command state {:command :narsese-command
                                                     :value {:command :cycling-belief}})]
    (is (re-find #"cycling_belief_events" reply)))
  (let [state (-> (core/initial-state)
                  (core/step {:type :goal
                              :term [:atom "G"]
                              :truth {:frequency 1.0 :confidence 0.9}}))
        {:keys [reply]} (shell/handle-command state {:command :narsese-command
                                                     :value {:command :cycling-goal}})]
    (is (re-find #"cycling_goal_events" reply))))

(deftest volume-command-suppresses-derived-lines
  (let [{state :state} (shell/handle-command (core/initial-state)
                                             {:command :narsese-command
                                              :value {:command :volume
                                                      :value 0}})
        {:keys [state]} (shell/handle-command state {:command :input
                                                     :value {:type :belief
                                                             :term [:atom "A"]
                                                             :truth {:frequency 1.0
                                                                     :confidence 0.9}}})
        {:keys [reply]} (shell/handle-command state {:command :input
                                                     :value {:type :belief
                                                             :term [:atom "B"]
                                                             :occurrence-time 2
                                                             :truth {:frequency 1.0
                                                                     :confidence 0.9}}})]
    (is (not (re-find #"Derived" reply)))))

(deftest concepts-command-shows-summary
  (let [state (-> (core/initial-state)
                  (core/step {:type :belief
                              :term [:atom "A"]
                              :truth {:frequency 1.0 :confidence 0.9}}))
        {:keys [reply]} (shell/handle-command state {:command :narsese-command
                                                     :value {:command :concepts}})]
    (is (re-find #"priority=" reply))))

(deftest questions-use-concept-tables
  (let [state (-> (core/initial-state)
                  (core/step {:type :belief
                              :term [:a]
                              :truth {:frequency 1.0 :confidence 0.9}})
                  (core/step {:type :belief
                              :term [:b]
                              :occurrence-time 2
                              :truth {:frequency 1.0 :confidence 0.9}}))
        {:keys [reply]} (shell/handle-command state {:command :question
                                                     :value {:term [:implication :prediction [:a] [:b]]}})]
    (is (re-find #"Answer:" reply))))
