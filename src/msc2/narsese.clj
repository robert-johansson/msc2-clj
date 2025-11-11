(ns msc2.narsese
  "Narsese term and sentence parser (subset needed for milestone 3)."
  (:require [clojure.string :as str]
            [instaparse.core :as insta]
            [msc2.term :as term]))

(set! *warn-on-reflection* true)

(def ^:private term-parser
  (insta/parser
   "<Term> = Implication | Sequence | Operation | Atom
    Implication = <'<'> Term Cop Term <'>'>
    Cop = '=/>' | '-->' | '<->'
    Sequence = <'('> (SeqPrefix | SeqInfix) <')'>
    SeqPrefix = <'&/'> Term Term
    SeqInfix = Term <'&/'> Term
    Operation = <'^'> IDENT
    Atom = BRACKET | IDENT
    BRACKET = <'['> IDENT <']'>
    IDENT = #'[A-Za-z0-9_]+'"
   :auto-whitespace :standard))

(def ^:private copula->kw
  {"=/>" :prediction
   "-->" :inheritance
   "<->" :similarity})

(defn- transform-term [tree]
  (insta/transform
   {:Term identity
    :Implication (fn [ante cop cons]
                   (term/implication (copula->kw cop) ante cons))
    :SeqPrefix (fn [a b] (term/seq-term a b))
    :SeqInfix (fn [a b] (term/seq-term a b))
    :Sequence (fn [& children] (first children))
    :Operation (fn [id] (term/op-term (str "^" id)))
    :Atom term/atom-term
    :BRACKET (fn [id] (term/atom-term (str "[" id "]")))
    :IDENT identity
    :Cop identity}
   tree))

(defn parse-term [s]
  (let [result (term-parser (str/trim s))]
    (if (insta/failure? result)
      (throw (ex-info "Unable to parse Narsese term" {:input s :failure result}))
      (let [value (transform-term result)]
        (if (and (sequential? value)
                 (= 1 (count value))
                 (sequential? (first value)))
          (first value)
          value)))))

(def default-truth {:frequency 1.0 :confidence 0.9})

(defn- parse-truth [s]
  (when-let [[_ f c] (re-find #"\{([\d.]+)\s+([\d.]+)\}" s)]
    {:frequency (Double/parseDouble f)
     :confidence (Double/parseDouble c)}))

(defn- parse-dt [s]
  (when-let [[_ dt rest] (re-matches #"(?i)dt=([0-9.]+)\s+(.*)" s)]
    {:dt (Double/parseDouble dt)
     :rest rest}))

(defn- punctuation->type [ch]
  (case ch
    \. :belief
    \! :goal
    \? :question
    nil))

(defn- extract-channel [s]
  (some #(when (and (seq %) (str/starts-with? % ":")) %)
        (remove str/blank? (str/split (str/trim s) #"\s+"))))

(defn- find-punctuation-index [s]
  (loop [idx 0]
    (when (< idx (count s))
      (if (#{\. \! \?} (nth s idx))
        idx
        (recur (inc idx))))))

(defn- parse-sentence-line [line]
  (let [{:keys [dt rest]} (or (parse-dt line) {:rest line})
        trimmed (str/trim rest)
        idx (find-punctuation-index trimmed)]
    (when (nil? idx)
      (throw (ex-info "Missing sentence punctuation" {:input line})))
    (let [punct (nth trimmed idx)
          term-str (subs trimmed 0 idx)
          trailing (subs trimmed (inc idx))
          truth (or (parse-truth trailing) default-truth)
          trailing* (str/replace trailing #"\{.*?\}" "")
          channel (extract-channel trailing*)]
      {:kind :sentence
       :type (punctuation->type punct)
       :term (parse-term term-str)
       :truth (when (not= :question (punctuation->type punct)) truth)
       :channel channel
       :dt dt})))

(defn- parse-command-line [line]
  (let [trim (str/trim line)]
    (cond
      (re-matches #"\*concepts" trim)
      {:kind :command :command :concepts}

      (re-matches #"\*setopname\s+\d+\s+\S+" trim)
      (let [[_ idx op] (re-matches #"\*setopname\s+(\d+)\s+(\S+)" trim)]
        {:kind :command
         :command :setopname
         :index (Integer/parseInt idx)
         :operation op})

      (re-matches #"\*motorbabbling=\d+(\.\d+)?" trim)
      (let [[_ v] (re-matches #"\*motorbabbling=([0-9.]+)" trim)]
        {:kind :command
         :command :motorbabbling
         :value (Double/parseDouble v)})

      :else {:kind :command
             :command :unknown
             :raw line})))

(defn parse-line [line]
  (let [trim (str/trim line)]
    (cond
      (str/blank? trim) nil
      (str/starts-with? trim "*") (parse-command-line trim)
      :else (parse-sentence-line trim))))

(defn sentence->string
  "Serialize a parsed sentence map back to Narsese."
  [{:keys [type term truth channel]}]
  (let [body (term/term->string term)
        punct (case type
                :belief "."
                :goal "!"
                :question "?"
                ".")
        truth-str (when (and truth (not= type :question))
                    (format "{%.6f %.6f}"
                            (:frequency truth)
                            (:confidence truth)))
        extras (->> [channel truth-str]
                    (remove str/blank?)
                    (str/join " "))]
    (str body punct (when (seq extras) (str " " extras)))))
