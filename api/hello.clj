(ns hello)

(defn handler [_event]
  (format "Hello from babashka %s!" (System/getProperty "babashka.version")))