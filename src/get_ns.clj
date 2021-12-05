;; #! /usr/bin/env bb

;; This script outputs the namespace declared in the file specified as a
;; command line argument.

(ns get-ns
  (:require [rewrite-clj.zip :as z]))

(let [zloc (z/of-file (first *command-line-args*))]
  (or (-> zloc
          (z/find #(and (= (z/tag %) :list)
                        (= (first (z/sexpr %)) 'ns)))
          z/sexpr
          second)
      (throw (Exception. (str "Can't determine namespace of file " (first *command-line-args*))))))