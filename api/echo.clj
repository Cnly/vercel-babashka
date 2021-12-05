(ns echo)

(defn handler [event]
  {:jsonify true
   :body {:event-passed-to-handler event}})
