(ns echo-pretty.index
  (:require [clojure.pprint :as pp]))

(defn handler [event]
  (with-out-str (pp/pprint event)))
