(defproject hyperlight "0.1.0"
  :description "A performance-focused HTTP reverse proxy"
  :license {:name "Eclipse Public License"}
  :dependencies [[aleph "0.4.4-alpha2"]
                 [org.clojure/clojure "1.9.0-alpha14"]]
  :jvm-opts ^:replace ["-server"
                       "-XX:+UseConcMarkSweepGC"
                       "-Xmx2g"
                       "-XX:+HeapDumpOnOutOfMemoryError"]
  :global-vars {*warn-on-reflection* true})
