(ns msc2.shell-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.core :as core]
            [msc2.shell :as shell]))

(deftest parse-line-handles-core-commands
  (is (= {:command :quit} (shell/parse-line "quit")))
  (is (= {:command :noop} (shell/parse-line "   \t")))
  (is (= {:command :input :value {:type :belief}}
         (shell/parse-line "{:type :belief}"))))

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
