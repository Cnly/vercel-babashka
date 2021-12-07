(ns queries)

(defn handler [event]
  (str "You have given "
       (if-let [queries (not-empty (:params event))]
         (str "these queries: " queries)
         "no queries.")))
