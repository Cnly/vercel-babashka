(ns redirect)

(defn handler [_event]
  {:status 302
   :headers {:location "https://example.com"}})
