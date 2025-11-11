(ns msc2.inference
  "Core inference helpers (subset of MSC2's Inference.c)."
  (:require [msc2.stamp :as stamp]
            [msc2.truth :as truth]))

(set! *warn-on-reflection* true)

(defn belief-induction
  "Derive a predictive implication from two belief events `earlier` and `later`.

  Mirrors `Inference_BeliefInduction`: project the earlier truth to the later
  occurrence time, run Truth_Induction + Truth_Eternalize, merge stamps, and
  compute the occurrence-time offset."
  [earlier later]
  (let [projected (truth/projection (:truth earlier)
                                    (:occurrence-time earlier)
                                    (:occurrence-time later))
        induced (-> (truth/induction (:truth later) projected)
                    truth/eternalize)
        {:keys [stamp creation-time]} (stamp/derive-stamp earlier later)]
    {:term [:implication :prediction (:term earlier) (:term later)]
     :consequent (:term later)
     :target-term (:term later)
     :truth induced
     :stamp stamp
     :occurrence-time-offset (- (:occurrence-time later)
                                (:occurrence-time earlier))
     :creation-time creation-time}))
