(ns msc2.inference-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.inference :as inference]))

(deftest belief-induction-matches-simple-implication
  (let [earlier {:term [:a]
                 :truth {:frequency 1.0 :confidence 0.9}
                 :occurrence-time 1
                 :stamp {:evidence [1]}
                 :creation-time 1}
        later {:term [:b]
               :truth {:frequency 1.0 :confidence 0.9}
               :occurrence-time 2
               :stamp {:evidence [2]}
               :creation-time 2}
        impl (inference/belief-induction earlier later)]
    (is (= 1 (:occurrence-time-offset impl)))
    (is (= [:implication :prediction [:a] [:b]] (:term impl)))
    (is (= [:b] (:consequent impl)))
    (is (= [1 2] (get-in impl [:stamp :evidence])))
    (is (= 2 (:creation-time impl)))
    (is (= 1.0 (get-in impl [:truth :frequency])))
    (is (< (Math/abs (- 0.28223 (get-in impl [:truth :confidence]))) 1.0e-5))))
