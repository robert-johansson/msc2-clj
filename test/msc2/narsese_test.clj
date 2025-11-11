(ns msc2.narsese-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [msc2.narsese :as narsese]))

(deftest parse-simple-term
  (is (= [:implication :prediction [:atom "A"] [:atom "B"]]
         (narsese/parse-term "<A =/> B>"))))

(deftest parse-sentence-line
  (let [sentence (narsese/parse-line "A. :|:")]
    (is (= :sentence (:kind sentence)))
    (is (= :belief (:type sentence)))
    (is (= [:atom "A"] (:term sentence)))
    (is (= {:frequency 1.0 :confidence 0.9} (:truth sentence)))))

(deftest parse-command-line
  (is (= {:kind :command :command :concepts}
         (narsese/parse-line "*concepts")))
  (is (= {:kind :command :command :setopname :index 1 :operation "^left"}
         (narsese/parse-line "*setopname 1 ^left"))))

(deftest parse-sequence-implication
  (let [line "<(B &/ ^right) =/> G>. :|:"
        sentence (narsese/parse-line line)]
    (is (= :sentence (:kind sentence)))
    (is (= [:implication :prediction
            [:seq [:atom "B"] [:op "^right"]]
            [:atom "G"]]
           (:term sentence)))))

(deftest sentence-roundtrip
  (let [line "A. :|:"
        parsed (narsese/parse-line line)
        serialized (narsese/sentence->string parsed)
        reparsed (narsese/parse-line serialized)]
    (is (= [:atom "A"] (:term reparsed)))
    (is (= ":|:" (:channel reparsed)))))

(defn- read-lines [filename]
  (-> (str "external/msc2/tests/" filename)
      slurp
      str/split-lines))

(deftest parse-script-lines
  (doseq [file ["simple_implication2.nal" "simple_implication3.nal"]
          line (read-lines file)
          :let [trim (str/trim line)]
          :when (seq trim)]
    (let [parsed (narsese/parse-line trim)]
      (is parsed (str "Failed to parse line: " trim))
      (cond
        (str/starts-with? trim "*") (is (= :command (:kind parsed)))
        (str/ends-with? trim "?") (is (= :question (:type parsed)))
        :else (is (= :sentence (:kind parsed)))))))
