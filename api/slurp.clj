(ns slurp)

(defn handler [_event]
  (let [file (System/getenv "ENTRYPOINT")]
    (str ";; The content of " file " is:\n\n"
         (slurp file))))
