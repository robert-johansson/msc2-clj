(ns msc2.experiments.exp1-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [msc2.experiments.exp1 :as exp1]))

(deftest exp1-mini-run
  (let [out-dir "target/exp1-test"]
    (when (.exists (io/file out-dir))
      (doseq [f (.listFiles (io/file out-dir))]
        (.delete f)))
    (.mkdirs (io/file out-dir))
    (let [result (exp1/run! {:phases [{:name "Baseline" :blocks 1 :feedback? false}
                                      {:name "Training" :blocks 1 :feedback? true}
                                      {:name "Testing" :blocks 1 :feedback? false}]
                             :output-dir out-dir
                             :seed 0
                             :verbose? false})
          trials-file (io/file out-dir "exp1_trials.csv")
          truth-file (io/file out-dir "exp1_truths.csv")]
      (is (.exists trials-file))
      (is (.exists truth-file))
      (is (> (count (str/split-lines (slurp trials-file))) 1))
      (is (> (count (str/split-lines (slurp truth-file))) 1))
      (is (= (* 3 exp1/trials-per-block) (count (:trials result)))))))
