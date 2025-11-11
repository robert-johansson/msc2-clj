(ns msc2.tables-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.tables :as tables]
            [msc2.util :as util]))

(defn implication [term confidence]
  {:term term
   :truth {:frequency 1.0 :confidence confidence}})

(deftest add-implication-revises-and-sorts
  (let [table (tables/add-implication [] (implication [:implication :prediction [:a] [:b]] 0.2))
        table (tables/add-implication table (implication [:implication :prediction [:a] [:b]] 0.4))]
    (is (= 1 (count table)))
    (is (util/approx= (get-in (first table) [:truth :confidence]) 0.4 0.2))))
