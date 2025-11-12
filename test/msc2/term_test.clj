(ns msc2.term-test
  (:require [clojure.test :refer [deftest is]]
            [msc2.term :as term]))

(deftest precondition-term-removes-operation-tail
  (let [antecedent (term/seq-term
                    (term/seq-term
                     (term/atom-term "A1")
                     (term/atom-term "B1"))
                    (term/op-term "^right"))
        precondition (term/precondition-term antecedent)]
    (is (= [:seq [:atom "A1"] [:atom "B1"]] precondition))))

(deftest precondition-term-collapses-single-entry
  (let [antecedent (term/seq-term
                    (term/atom-term "A1")
                    (term/op-term "^left"))
        precondition (term/precondition-term antecedent)]
    (is (= [:atom "A1"] precondition))))

(deftest precondition-term-returns-nil-when-only-operation
  (is (nil? (term/precondition-term (term/op-term "^right")))))
