(defproject hyperlight "0.2.0"
  :description "A performance-focused HTTP reverse proxy"
  :license {:name "Eclipse Public License"}
  :dependencies [[aleph "0.4.4-alpha4"]
                 [org.clojure/clojure "1.9.0-alpha14"]]
  :jvm-opts ^:replace ["-server"
                       "-XX:+UseConcMarkSweepGC"
                       "-Xmx2g"
                       "-XX:+HeapDumpOnOutOfMemoryError"]
  :profiles {:dev {:dependencies [[criterium "0.4.4"]]}}
  :test-selectors {:default   (complement :benchmark)
                   :benchmark :benchmark
                   :all       (constantly true)}
  :global-vars {*warn-on-reflection* true})
