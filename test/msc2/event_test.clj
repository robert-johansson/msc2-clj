(ns msc2.event-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.event :as event]))

(deftest prepare-normalizes-missing-fields
  (let [state {:time 5}
        prepared (event/prepare state {:type :belief
                                       :term [:atom "A"]
                                       :truth {:frequency 1.2 :confidence 0.5}})]
    (is (= 5 (:occurrence-time prepared)))
    (is (= 5 (:creation-time prepared)))
    (is (= 5 (:queued-at prepared)))
    (is (= 1.0 (get-in prepared [:truth :frequency])))
    (is (= [] (get-in prepared [:stamp :evidence])))))
