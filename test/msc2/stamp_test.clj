(ns msc2.stamp-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.stamp :as stamp]))

(deftest normalize-drops-free-markers
  (let [raw {:evidence [1 2 0 3 nil]}
        normalized (stamp/normalize raw)]
    (is (= [1 2 3] (:evidence normalized)))
    (is (stamp/equal? normalized (stamp/normalize [1 2 3])))))

(deftest make-zips-like-c-reference
  (let [a (stamp/normalize [1 2])
        b (stamp/normalize [2 3 4])
        merged (stamp/make a b)]
    (is (= [1 2 2 3 4] (:evidence merged)))))

(deftest overlap-detection
  (let [a (stamp/normalize [1 2])
        b (stamp/normalize [2 3])
        c (stamp/normalize [4 5])]
    (is (stamp/overlap? a b))
    (is (not (stamp/overlap? a c)))))

(deftest derive-merges-stamps-and-creation-time
  (let [derived (stamp/derive-stamp {:stamp (stamp/normalize [1 2])
                                     :creation-time 5}
                                    {:stamp (stamp/normalize [2 3 4])
                                     :creation-time 7})]
    (is (= 7 (:creation-time derived)))
    (is (= [1 2 2 3 4] (get-in derived [:stamp :evidence])))))
