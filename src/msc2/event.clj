(ns msc2.event
  "Event helpers: normalization, stamping, projection."
  (:require [msc2.stamp :as stamp]
            [msc2.truth :as truth]))

(set! *warn-on-reflection* true)

(defn prepare
  "Normalize an event map relative to the current state.

  Ensures truth/stamp are canonical, assigns occurrence/creation times, and
  carries optional channel/dt annotations forward."
  [{:keys [time]} {:keys [type] :as event}]
  (when-not (#{:belief :goal} type)
    (throw (ex-info "Event must declare :type (:belief or :goal)" {:event event})))
  (let [normalized-truth (truth/normalize (:truth event))
        normalized-stamp (stamp/normalize (:stamp event))
        occurrence (or (:occurrence-time event) time)]
    (-> event
        (assoc :truth normalized-truth
               :stamp normalized-stamp
               :occurrence-time occurrence
               :creation-time time
               :queued-at time
               :priority 1.0))))

(defn project-to-time
  "Project an event's truth to a target occurrence time."
  [event target-time]
  (let [projected (truth/projection (:truth event)
                                    (:occurrence-time event)
                                    target-time)]
    (-> event
        (assoc :truth projected
               :occurrence-time target-time))))
