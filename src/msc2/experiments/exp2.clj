(ns msc2.experiments.exp2
  "Clojure replication of MSC2 Experiment 2 (changing contingencies)."
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
    :expected {:a1 "^left" :a2 "^right"}}
   {:name "A1-right"
    :stimuli ["<A1 --> [right]>. :|:" "<A2 --> [left]>. :|:"]
    :expected {:a1 "^right" :a2 "^left"}}])

(def target-term-strings
  ["<(<A1 --> [left]> &/ ^left) =/> G>"
   "<(<A1 --> [right]> &/ ^right) =/> G>"
   "<(<A2 --> [left]> &/ ^left) =/> G>"
   "<(<A2 --> [right]> &/ ^right) =/> G>"])

(defn- parse-target [s]
  (let [term (:term (narsese/parse-line (str s ". :|:")))
        [_ _ antecedent consequent] term]
    {:label s
     :antecedent antecedent
     :consequent consequent}))

(def target-terms (mapv parse-target target-term-strings))

(def default-phases
  [{:name "Baseline" :blocks 2 :feedback? false :expect-key :a1}
   {:name "Training1" :blocks 4 :feedback? true :expect-key :a1}
   {:name "Testing1" :blocks 2 :feedback? false :expect-key :a1}
   {:name "Training2" :blocks 7 :feedback? true :expect-key :a2}
   {:name "Testing2" :blocks 2 :feedback? false :expect-key :a2}])

(def setup-commands
  ["*reset"
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
    (if entry
      (:truth entry)
      {:frequency 0.0 :confidence 0.0})))

(defn- run-trial [state {:keys [stimuli expected] :as trial} trial-index feedback? expect-key]
  (let [state (reduce (fn [st line] (:state (exec-line st line))) state stimuli)
        {:keys [state decision]} (exec-line state "G! :|:")
        expected-op (get expected expect-key)
        op (or decision "")
        correct? (= op expected-op)
        state (if feedback?
                (let [feedback-line (if correct? "G. :|:" "G. :|: {0.0 0.9}")]
                  (:state (exec-line state feedback-line)))
                state)
        state (:state (exec-line state "100"))]
    {:state state
     :row {:phase nil
           :block nil
           :trial-index trial-index
           :stimulus (:name trial)
           :expectation (name expect-key)
           :expected-op expected-op
           :executed op
           :correct (if correct? 1 0)}
     :correct? correct?}))

(defn- run-block [{:keys [state rng phase block-index feedback? expect-key verbose?]}]
  (let [order (shuffle-block rng)
        start-index (* block-index trials-per-block)
        {:keys [state rows correct]}
        (loop [state state
               idx 0
               remaining order
               rows []
               correct 0]
          (if (empty? remaining)
            {:state state :rows rows :correct correct}
            (let [{:keys [state row correct?]}
                  (run-trial state (first remaining) (+ start-index idx) feedback? expect-key)
                  row (assoc row
                             :phase (str/lower-case phase)
                             :block (inc block-index))]
              (recur state
                     (inc idx)
                     (rest remaining)
                     (conj rows row)
                     (+ correct (if correct? 1 0))))))
        accuracy (double (/ correct trials-per-block))
        truth-rows (mapv (fn [target]
                           (let [{:keys [frequency confidence]} (concept-truth state target)]
                             {:phase (str/lower-case phase)
                              :block (inc block-index)
                              :term (:label target)
                              :frequency frequency
                              :confidence confidence}))
                         target-terms)]
    (when verbose?
      (println (format "%s Block %d: %d/12 correct (%.0f%%)"
                       phase (inc block-index) correct (* 100 accuracy))))
    {:state state
     :rows rows
     :truths truth-rows
     :accuracy accuracy}))

(defn- run-phase [{:keys [state rng phase block-index blocks feedback? expect-key verbose?]}]
  (loop [state state
         idx (long block-index)
         remaining-blocks (long blocks)
         trial-rows []
         truth-rows []]
    (if (pos? remaining-blocks)
      (let [{:keys [state rows truths]}
            (run-block {:state state
                        :rng rng
                        :phase phase
                        :block-index idx
                        :feedback? feedback?
                        :expect-key expect-key
                        :verbose? verbose?})]
        (recur state
               (inc idx)
               (dec remaining-blocks)
               (into trial-rows rows)
               (into truth-rows truths)))
      {:state state
       :next-index idx
       :rows trial-rows
       :truths truth-rows})))

(defn run-exp2!
  ([] (run-exp2! {}))
  ([{:keys [phases seed output-dir verbose?]
     :or {phases default-phases
          seed 42
          output-dir "experiments"
          verbose? true}}]
   (let [rng (Random. (long seed))
         initial-state (reduce (fn [st line] (:state (exec-line st line)))
                               (core/initial-state)
                               setup-commands)]
     (loop [state initial-state
            remaining phases
            block-index (long 0)
            trial-rows []
            truth-rows []]
       (if-let [{:keys [name blocks feedback? expect-key]} (first remaining)]
         (let [{:keys [state next-index rows truths]}
               (run-phase {:state state
                           :rng rng
                           :phase name
                           :block-index block-index
                           :blocks blocks
                           :feedback? feedback?
                           :expect-key expect-key
                           :verbose? verbose?})]
           (recur state
                  (rest remaining)
                  (long next-index)
                  (into trial-rows rows)
                  (into truth-rows truths)))
         (let [out-dir (doto (io/file output-dir) (.mkdirs))
               trials-path (io/file out-dir "exp2_trials.csv")
               truths-path (io/file out-dir "exp2_truths.csv")]
           (with-open [w (io/writer trials-path)]
             (.write w "phase,block,trial_index,stimulus,expectation,expected_op,executed,correct\n")
             (doseq [row trial-rows]
               (.write w (format "%s,%d,%d,%s,%s,%s,%s,%d\n"
                                 (:phase row) (:block row) (:trial-index row)
                                 (:stimulus row) (:expectation row) (:expected-op row)
                                 (:executed row) (:correct row)))))
           (with-open [w (io/writer truths-path)]
             (.write w "phase,block,term,frequency,confidence\n")
             (doseq [row truth-rows]
               (.write w (format "%s,%d,%s,%.6f,%.6f\n"
                                 (:phase row) (:block row) (:term row)
                                 (:frequency row) (:confidence row)))))
           (when verbose?
             (println "\nHypothesis strength by block:")
             (doseq [row truth-rows]
               (println (format "  %s block %d: %s freq=%.2f conf=%.2f"
                                (:phase row) (:block row) (:term row)
                                (:frequency row) (:confidence row))))
             (println (format "\nTrial log written to %s" (.getPath trials-path)))
             (println (format "Hypothesis log written to %s" (.getPath truths-path))))
           {:trials trial-rows
            :truths truth-rows
            :output-dir out-dir}))))))

(defn run!
  ([] (run-exp2! {}))
  ([opts]
   (run-exp2! opts)))

(defn -main [& _]
  (run-exp2!)
  (shutdown-agents))
