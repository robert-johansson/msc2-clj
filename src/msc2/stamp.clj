(ns msc2.stamp
  "Evidence stamp helpers mirroring MSC2's Stamp.c implementation.")

(set! *warn-on-reflection* true)

(def ^:const stamp-size 10)
(def ^:const stamp-free 0)

(def ^:const empty-stamp
  "Canonical empty stamp."
  {:evidence []})

(defn normalize
  "Return a stamp with at most `stamp-size` non-zero evidential ids."
  [stamp]
  (let [entries (cond
                  (nil? stamp) nil
                  (map? stamp) (:evidence stamp)
                  (sequential? stamp) stamp
                  (number? stamp) [stamp]
                  :else (throw (ex-info "Unsupported stamp representation"
                                        {:stamp stamp})))]
    {:evidence (->> entries
                    (keep #(when (some? %) (long %)))
                    (remove zero?)
                    (take stamp-size)
                    (into []))}))

(defn empty-stamp?
  [stamp]
  (clojure.core/empty? (:evidence (normalize stamp))))

(defn base
  "Raw evidential vector accessor."
  [stamp]
  (:evidence (normalize stamp)))

(defn make
  "Zip two stamps together just like Stamp_make."
  [stamp-a stamp-b]
  (let [a (base stamp-a)
        b (base stamp-b)]
    {:evidence
     (loop [a a
            b b
            acc []]
       (cond
         (>= (count acc) stamp-size) acc
         (and (empty? a) (empty? b)) acc
         :else (let [[acc a] (if (seq a)
                               [(conj acc (first a)) (rest a)]
                               [acc a])
                     [acc b] (if (and (< (count acc) stamp-size) (seq b))
                               [(conj acc (first b)) (rest b)]
                               [acc b])]
                 (recur a b acc))))}))

(defn overlap?
  "Return true when two stamps share evidential ids (Stamp_checkOverlap)."
  [stamp-a stamp-b]
  (let [set-b (set (base stamp-b))]
    (boolean (some set-b (base stamp-a)))))

(defn equal?
  "Convenience equality predicate for tests."
  [stamp-a stamp-b]
  (= (base stamp-a) (base stamp-b)))
