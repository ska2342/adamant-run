(defproject adamant-run "1.0.0-SNAPSHOT"
  :description "A Clojure library to adamantly (or stubbornly) try to run a function "

  :url "https://github.com/ska2342/adamant-run"
  :license {:name "Eclipse Public License"}
  
  :dependencies     [[org.clojure/clojure "1.2.1"]]
  :dev-dependencies [[swank-clojure "1.4.0-SNAPSHOT"]]

  :aot [adamant-run.AdamantRetryException]
  )