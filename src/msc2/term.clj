(ns msc2.term
  "Narsese term representation and helpers.

  Terms are modeled as persistent vectors whose first element identifies the
  operator:
  - `[:atom \"A\"]`
  - `[:op \"^left\"]`
  - `[:seq term1 term2 ...]`
  - `[:implication copula antecedent consequent]`

  Copulas are keywords such as `:prediction` (for `=/>`) or `:inheritance`
  (for `-->`).")

(defn- ->token [x]
  (cond
    (keyword? x) (name x)
    (symbol? x) (name x)
    :else (str x)))

(defn atom-term
  "Wrap a raw token (string/symbol/keyword) as an atom term."
  [token]
  [:atom (->token token)])

(defn op-term
  "Represent an operation symbol (e.g., `^left`)."
  [token]
  [:op (->token token)])

(defn seq-term
  "Build a temporal sequence term (the `&/` copula)."
  [& terms]
  (into [:seq] terms))

(defn implication
  "Build an implication term with the given copula keyword."
  [copula antecedent consequent]
  [:implication copula antecedent consequent])

(defn inheritance
  [subject predicate]
  [:inheritance subject predicate])

(defn compound?
  [term]
  (and (vector? term)
       (not= :atom (first term))
       (not= :op (first term))))

(defn atom?
  [term]
  (= :atom (first term)))

(defn op?
  [term]
  (= :op (first term)))

(defn sequence?
  [term]
  (= :seq (first term)))

(defn precondition-term
  "Return the left-most non-sequence term inside an antecedent.

  Used by decision making to determine which recent percept must be active for
  a prediction rule to fire."
  [term]
  (cond
    (nil? term) nil
    (sequence? term) (precondition-term (second term))
    :else term))

(defn term->string
  "Serialize a term back to a Narsese string (subset, used for round-trips)."
  [term]
  (let [[op & args] term]
    (case op
      :atom (first args)
      :op (first args)
      :seq (format "(%s &/ %s)"
                   (term->string (first args))
                   (term->string (second args)))
      :implication (let [[cop ante cons] args
                         cop-str (case cop
                                   :prediction "=/>"
                                   :inheritance "-->"
                                   :similarity "<->"
                                   "=/>")]
                     (format "<%s %s %s>"
                             (term->string ante)
                             cop-str
                             (term->string cons)))
      :inheritance (let [[subject predicate] args]
                     (format "<%s --> %s>"
                             (term->string subject)
                             (term->string predicate)))
      (throw (ex-info "Unsupported term for serialization" {:term term})))))
