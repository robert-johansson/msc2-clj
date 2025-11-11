(ns msc2.truth-test
  (:require [clojure.test :refer [deftest is testing]]
            [msc2.truth :as truth]))

(defn approx=
  ([a b] (approx= a b 1.0e-9))
  ([a b eps]
   (<= (Math/abs (- (double a) (double b))) eps)))

(deftest weight-confidence-roundtrip
  (testing "weight->confidence and inverse conversions"
    (is (approx= 0.5 (truth/weight->confidence 1.0)))
    (is (approx= 1.0 (truth/confidence->weight 0.5)))
    (is (approx= 0.7 (-> 0.7 truth/confidence->weight truth/weight->confidence)))))

(deftest expectation-and-projection
  (let [tv {:frequency 1.0 :confidence 0.4}]
    (is (approx= 0.7 (truth/expectation tv)))
    (let [projected (truth/projection tv 0 3)]
      (is (= 1.0 (:frequency projected)))
      (is (approx= (* 0.4 (Math/pow truth/*projection-decay* 3))
                   (:confidence projected))))
    (testing "eternal events remain unchanged"
      (is (= tv (truth/projection tv truth/occurrence-eternal 42))))))

(deftest revision-matches-reference-formulas
  (let [prior {:frequency 1.0 :confidence 0.9}
        new {:frequency 0.0 :confidence 0.6}
        rev (truth/revision prior new)]
    (is (approx= (/ 6.0 7.0) (:frequency rev)))
    (is (approx= (/ 10.5 11.5) (:confidence rev) 1.0e-6))))

(deftest deduction-induction-and-structural
  (let [premise {:frequency 0.9 :confidence 0.8}
        rule {:frequency 0.75 :confidence 0.7}
        ded (truth/deduction premise rule)]
    (is (approx= (* 0.9 0.75) (:frequency ded)))
    (is (approx= (* 0.8 0.7 (* 0.9 0.75)) (:confidence ded))))
  (let [antecedent {:frequency 0.9 :confidence 0.8}
        consequent {:frequency 0.75 :confidence 0.7}
        ind (truth/induction antecedent consequent)]
    (is (= (:frequency antecedent) (:frequency ind)))
    (is (approx= (truth/weight->confidence (* (:frequency consequent)
                                              (:confidence consequent)
                                              (:confidence antecedent)))
                 (:confidence ind))))
  (testing "structural deduction uses RELIANCE"
    (let [tv {:frequency 0.8 :confidence 0.7}
          sd (truth/structural-deduction tv)]
      (is (approx= 0.8 (:frequency sd)))
      (is (approx= (* 0.7 truth/reliance 0.8) (:confidence sd))))))

(deftest induction-parity-with-simple-implication
  (testing "Reproduce C log values for <A =/> B> using projection + induction + eternalize"
    (let [a {:frequency 1.0 :confidence 0.9}
          b {:frequency 1.0 :confidence 0.9}
          projected-a (truth/projection a 1 2)
          conclusion (-> (truth/induction b projected-a)
                         truth/eternalize)]
      (is (= 1.0 (:frequency conclusion)))
      (is (approx= 0.28223 (:confidence conclusion) 1.0e-5)))))

(deftest equality-and-normalization
  (is (truth/equal? {:frequency 1.0 :confidence 0.5}
                    {:frequency 1.0 :confidence 0.5}))
  (is (not (truth/equal? {:frequency 1.0 :confidence 0.5}
                         {:frequency 0.99 :confidence 0.5}))))
