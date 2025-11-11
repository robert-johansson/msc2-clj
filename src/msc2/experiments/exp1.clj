(ns msc2.experiments.exp1
  "Clojure replication of MSC2 Experiment 1 (simple discrimination)."
  (:refer-clojure :exclude [run!])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [msc2.core :as core]
            [msc2.narsese :as narsese]
            [msc2.shell :as shell])
  (:import [java.util ArrayList Collections Random]))

(set! *warn-on-reflection* true)

(def trials-per-block 12)

(def trials
  [{:name "A1-left"
    :stimuli ["<A1 --> [left]>. :|:" "<A2 --> [right]>. :|:"]
    :expected "^left"}
   {:name "A1-right"
    :stimuli ["<A1 --> [right]>. :|:" "<A2 --> [left]>. :|:"]
    :expected "^right"}])

(def target-term-strings
  ["<(<A1 --> [left]> &/ ^left) =/> G>"
   "<(<A1 --> [right]> &/ ^right) =/> G>"])

(defn- parse-term [s]
  (:term (narsese/parse-line (str s ". :|:"))))

(def target-terms
  (mapv (fn [label]
          (let [term (parse-term label)
                [_ _ antecedent consequent] term]
            {:label label
             :term term
             :antecedent antecedent
             :consequent consequent}))
        target-term-strings))

(def default-phases
  [{:name "Baseline" :blocks 3 :feedback? false}
   {:name "Training" :blocks 3 :feedback? true}
   {:name "Testing" :blocks 3 :feedback? false}])

(def setup-commands ["*reset"
                     "*babblingops=2"
                     "*motorbabbling=0.9"
                     "*setopname 1 ^left"
                     "*setopname 2 ^right"
                     "*volume=100"])

(defn- shuffle-block [^Random rng]
  (let [^java.util.Collection base (vec (mapcat (constantly trials)
                                                (range (/ trials-per-block (count trials)))))
        ^ArrayList arr (ArrayList. base)]
    (Collections/shuffle arr rng)
    (vec arr)))

(defn- exec-line [state line]
  (when (str/blank? line)
    (throw (ex-info "Blank experiment line" {:line line})))
  (let [command (or (shell/parse-line line)
                    (throw (ex-info "Unable to parse line" {:line line})))
        old-count (count (:decisions state))
        {:keys [state]} (shell/handle-command state command)
        new-decisions (drop old-count (:decisions state))]
    {:state state
     :decision (some-> new-decisions last :operation)}))

(defn- concept-truth [state {:keys [antecedent consequent]}]
  (let [entry (first (get-in state [:concepts consequent :tables [:prediction antecedent]]))]
    (if entry (:truth entry) {:frequency 0.0 :confidence 0.0})))

(defn- run-trial [state {:keys [name stimuli expected]} trial-index feedback?]
  (let [state (reduce (fn [st line] (:state (exec-line st line))) state stimuli)
        {:keys [state decision]} (exec-line state "G! :|:")
        op (or decision "")
        correct? (= op expected)
        state (if feedback?
                (let [feedback-line (if correct? "G. :|:" "G. :|: {0.0 0.9}")]
                  (:state (exec-line state feedback-line)))
                state)
        state (:state (exec-line state "100"))]
    {:state state
     :row {:phase nil
           :block nil
           :trial-index trial-index
           :stimulus name
           :expected expected
           :executed op
           :correct (if correct? 1 0)}
     :correct? correct?}))

(defn- run-block [{:keys [state rng phase block-index feedback? verbose?]}]
  (let [order (shuffle-block rng)
        offset (* block-index trials-per-block)
        {:keys [state rows correct]}
        (loop [state state
               idx 0
               remaining order
               rows []
               correct 0]
          (if (empty? remaining)
            {:state state :rows rows :correct correct}
            (let [{:keys [state row correct?]} (run-trial state (first remaining) (+ offset idx) feedback?)
                  row (assoc row
                             :phase (str/lower-case phase)
                             :block (inc block-index))]
              (recur state
                     (inc idx)
                     (rest remaining)
                     (conj rows row)
                     (+ correct (if correct? 1 0))))))
        accuracy (double (/ correct trials-per-block))
        truth-rows (for [target target-terms
                         :let [{:keys [frequency confidence]} (concept-truth state target)]]
                     {:phase (str/lower-case phase)
                      :block (inc block-index)
                      :term (:label target)
                      :frequency frequency
                      :confidence confidence})]
    (when verbose?
      (println (format "%s Block %d: %d/12 correct (%.0f%%)"
                       phase (inc block-index) correct (* 100 accuracy))))
    {:state state
     :rows rows
     :truths truth-rows
     :accuracy accuracy}))

(defn run!
  ([]
   (run! {}))
  ([{:keys [phases seed output-dir verbose?]
     :or {phases default-phases
          seed 42
          output-dir "experiments"
          verbose? true}}]
   (let [rng (Random. (long seed))
         state (reduce (fn [st line] (:state (exec-line st line)))
                       (core/initial-state)
                       setup-commands)]
     (loop [state state
            remaining phases
            trials []
            truths []]
       (if-let [{:keys [name blocks feedback?]} (first remaining)]
        (let [{:keys [state phase-trials phase-truths]}
              (loop [state state
                     block 0
                     rows-acc []
                     truths-acc []]
                (if (< block blocks)
                  (let [{:keys [state rows truths]}
                        (run-block {:state state
                                    :rng rng
                                    :phase name
                                    :block-index block
                                    :feedback? feedback?
                                    :verbose? verbose?})]
                    (recur state
                           (inc block)
                           (into rows-acc rows)
                           (into truths-acc truths)))
                  {:state state
                   :phase-trials rows-acc
                   :phase-truths truths-acc}))]
          (recur state
                 (rest remaining)
                 (into trials phase-trials)
                 (into truths phase-truths)))
         (let [out-dir (doto (io/file output-dir) (.mkdirs))
               trials-path (io/file out-dir "exp1_trials.csv")
               truths-path (io/file out-dir "exp1_truths.csv")]
           (with-open [w (io/writer trials-path)]
             (.write w "phase,block,trial_index,stimulus,expected,executed,correct\n")
             (doseq [row trials]
               (.write w (format "%s,%d,%d,%s,%s,%s,%d\n"
                                 (:phase row) (:block row) (:trial-index row)
                                 (:stimulus row) (:expected row) (:executed row) (:correct row)))))
           (with-open [w (io/writer truths-path)]
             (.write w "phase,block,term,frequency,confidence\n")
             (doseq [row truths]
               (.write w (format "%s,%d,%s,%.6f,%.6f\n"
                                 (:phase row) (:block row) (:term row)
                                 (:frequency row) (:confidence row)))))
           (when verbose?
             (println "\nHypothesis strength by block:")
             (doseq [row truths]
               (println (format "  %s block %d: %s freq=%.2f conf=%.2f"
                                (:phase row) (:block row) (:term row)
                                (:frequency row) (:confidence row))))
             (println (format "\nTrial log written to %s" (.getPath trials-path)))
             (println (format "Hypothesis log written to %s" (.getPath truths-path))))
           {:trials trials
            :truths truths
            :output-dir out-dir}))))))

(defn -main [& _]
  (run!)
  (shutdown-agents))
