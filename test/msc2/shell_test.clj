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
    (is (re-find #"cycle=1" reply))))

(deftest handle-command-rejects-bad-input
  (let [state (core/initial-state)
        response (shell/handle-command state {:command :input :value {}})]
    (is (= 0 (:time (:state response))))
    (is (re-find #"Input rejected" (:reply response)))))

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
    (is (re-find #"Motor babbling" reply))))

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
