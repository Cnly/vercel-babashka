(ns get-image.index
  (:require [babashka.curl :as curl]))

(defn handler [_event]
  {:headers {:content-type :image/png}
   :body (:body (curl/get "https://github.com/babashka/babashka/raw/master/logo/icon.png"
                          {:as :bytes}))})
