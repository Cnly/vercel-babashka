#! /usr/bin/env bb

;; This script is meant for testing an unpublished runtime.
;; If you want to use custom runtimes locally outside its own project, you need
;; to have a way to make `npm install <runtime-name>` work, which is invoked by
;; `vercel dev`.
;; This script does that by replacing the installation of the runtime package
;; with an `npm link --save <runtime-name>` command.
;; Note that you need to `npm link` under your custom runtime directory first.

;; To use this script, run `/path/to/this_script.clj vercel dev`.

(ns fix-vercel-npm-install
  (:require [clojure.string :as s]
            [babashka.fs :as fs]
            [babashka.process :as p]))

(def script-dir (str (fs/normalize (fs/parent *file*))))
(def npm-exec (str (fs/normalize (s/trim (:out (p/sh '[which npm]))))))
(def npm-dir (str (fs/parent npm-exec)))

(defn exec [opts cmd-and-args]
  (println "Executing:" (s/join \  cmd-and-args))
  (p/sh cmd-and-args (merge {:in :inherit
                             :out :inherit
                             :err :inherit} opts)))

(defn handle-exec-from-vercel []
  (let [real-npm (System/getenv "real-npm")
        runtime-package-name (System/getenv "runtime-package-name")]
    (when (= (first *command-line-args*) "install")
      (exec {} [real-npm 'link '--save runtime-package-name]))
    (->> *command-line-args*
         (filter #(not (s/starts-with? % (str runtime-package-name \@))))
         (cons real-npm)
         (exec {}))))

(defn handle-exec-from-user []
  (let [runtime-package-name (->> (:out (p/sh '[npm run env] {:dir script-dir}))
                                  s/split-lines
                                  (map #(s/split % #"=" 2))
                                  (filter #(= (first %) "npm_package_name"))
                                  (map second)
                                  first)]
    (when-not runtime-package-name
      (throw (Exception. "Couldn't get npm package name of custom runtime")))
    (println "Using npm from:" npm-exec)
    (println "Runtime package name:" runtime-package-name)
    (exec {:extra-env
           {:PATH (str script-dir \: (System/getenv "PATH"))
            :real-npm npm-exec
            :runtime-package-name runtime-package-name}}
          *command-line-args*)))

(when (= *file* (System/getProperty "babashka.file"))
  (if (= script-dir npm-dir)
    (handle-exec-from-vercel)
    (handle-exec-from-user))
  nil)
