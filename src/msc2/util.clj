(ns msc2.util)

(defn approx=
  ([a b]
   (approx= a b 1.0e-6))
  ([a b eps]
   (<= (Math/abs (- (double a) (double b))) eps)))
