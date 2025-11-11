(ns msc2.truth
  "MSC2 truth calculus (projection, induction, deduction, revision, expectation).")

(set! *warn-on-reflection* true)

(def ^:const occurrence-eternal
  "Matches OCCURRENCE_ETERNAL in the MSC2 C sources."
  -1)

(def ^:const reliance
  "Confidence assigned to structural truths (Config.h RELIANCE)."
  0.9)

(def ^:const max-confidence
  "Hard cap imposed by the C implementation (MAX_CONFIDENCE)."
  0.99)

(def ^:dynamic *evidential-horizon*
  "TRUTH_EVIDENTAL_HORIZON (adjustable for experiments)."
  1.0)

(def ^:dynamic *projection-decay*
  "TRUTH_PROJECTION_DECAY (adjustable for experiments)."
  0.8)

(def ^:const structural-truth
  "Truth pair used in structural deduction steps."
  {:frequency 1.0
   :confidence reliance})

(def ^:const zero-truth
  {:frequency 0.0
   :confidence 0.0})

(defn clamp
  ([x low high]
   (-> x (max low) (min high))))

(defn normalize
  "Return a canonical truth map (doubles, bounded to valid ranges)."
  [truth]
  (let [{:keys [frequency confidence]} (or truth zero-truth)]
    {:frequency (-> frequency (or 0.0) double (clamp 0.0 1.0))
     :confidence (-> confidence (or 0.0) double (clamp 0.0 max-confidence))}))

(defn weight->confidence
  "Convert evidential weight to confidence (Truth_w2c)."
  [w]
  (cond
    (neg? w) 0.0
    (Double/isInfinite w) max-confidence
    :else (let [den (+ w *evidential-horizon*)]
            (if (zero? den) 0.0 (/ w den)))))

(defn confidence->weight
  "Convert confidence back to evidential weight (Truth_c2w)."
  [c]
  (cond
    (<= c 0.0) 0.0
    (>= c 1.0) Double/POSITIVE_INFINITY
    :else (/ (* *evidential-horizon* c)
             (- 1.0 c))))

(defn expectation
  "Truth expectation scalar (Truth_Expectation)."
  [truth]
  (let [{:keys [frequency confidence]} (normalize truth)]
    (+ (* confidence (- frequency 0.5)) 0.5)))

(defn revision
  "Combine two truths by evidential weight (Truth_Revision)."
  [v1 v2]
  (let [{f1 :frequency c1 :confidence} (normalize v1)
        {f2 :frequency c2 :confidence} (normalize v2)
        w1 (confidence->weight c1)
        w2 (confidence->weight c2)
        w (+ w1 w2)
        freq (if (zero? w) 0.0 (/ (+ (* w1 f1) (* w2 f2)) w))
        c* (weight->confidence w)]
    {:frequency (min 1.0 freq)
     :confidence (clamp (max c* c1 c2) 0.0 max-confidence)}))

(defn deduction
  "Forward inference truth function (Truth_Deduction)."
  [premise rule]
  (let [{f1 :frequency c1 :confidence} (normalize premise)
        {f2 :frequency c2 :confidence} (normalize rule)
        f (* f1 f2)]
    {:frequency f
     :confidence (* c1 c2 f)}))

(defn abduction
  "Inverse inference truth function (Truth_Abduction)."
  [conclusion rule]
  (let [{f1 :frequency c1 :confidence} (normalize conclusion)
        {f2 :frequency c2 :confidence} (normalize rule)]
    {:frequency f2
     :confidence (weight->confidence (* f1 c1 c2))}))

(defn induction
  "Temporal induction truth function (Truth_Induction)."
  [antecedent consequent]
  (abduction consequent antecedent))

(defn intersection
  "Return the logical intersection truth (Truth_Intersection)."
  [v1 v2]
  (let [{f1 :frequency c1 :confidence} (normalize v1)
        {f2 :frequency c2 :confidence} (normalize v2)]
    {:frequency (* f1 f2)
     :confidence (* c1 c2)}))

(defn eternalize
  "Convert a time-bounded truth into an eternal one (Truth_Eternalize)."
  [truth]
  (let [{:keys [frequency confidence]} (normalize truth)]
    {:frequency frequency
     :confidence (weight->confidence confidence)}))

(defn projection
  "Project a truth from `original-time` onto `target-time` (Truth_Projection)."
  [truth original-time target-time]
  (let [{:keys [frequency confidence]} (normalize truth)]
    (if (= original-time occurrence-eternal)
      {:frequency frequency
       :confidence confidence}
      (let [delta (Math/abs (double (- (long target-time)
                                       (long original-time))))
            decay (Math/pow *projection-decay* delta)]
        {:frequency frequency
         :confidence (* confidence decay)}))))

(defn structural-deduction
  "Deduction variant that composes a truth with STRUCTURAL_TRUTH."
  [truth]
  (deduction truth structural-truth))

(defn equal?
  "Exact equality check matching Truth_Equal."
  [v1 v2]
  (let [{f1 :frequency c1 :confidence} (normalize v1)
        {f2 :frequency c2 :confidence} (normalize v2)]
    (and (= f1 f2) (= c1 c2))))
