(ns hyperlight.middleware-test
  (:require [clojure.test :refer [deftest is]]
            [hyperlight.middleware :as middleware]))

(deftest test-format-x-forwarded
  (let [base-request {:scheme :http :remote-addr "0.0.0.0"}]
    (is (= (middleware/format-x-forwarded base-request)
           (merge base-request {:headers {"x-forwarded-for" "0.0.0.0"
                                          "x-forwarded-port" "80"
                                          "x-forwarded-proto" "http"}}))))
  (let [base-request {:scheme :https :remote-addr "0.0.0.0"}]
    (is (= (middleware/format-x-forwarded base-request)
           (merge base-request {:headers {"x-forwarded-for" "0.0.0.0"
                                          "x-forwarded-port" "443"
                                          "x-forwarded-proto" "https"}}))))
  (let [base-request
        {:scheme :http
         :remote-addr "0.0.0.0"
         :headers {"x-forwarded-for" "1.2.3.4"
                   "x-forwarded-port" "443"
                   "x-forwarded-proto" "https"}}]
    (is (= (middleware/format-x-forwarded base-request)
           (merge base-request {:headers {"x-forwarded-for" "1.2.3.4, 0.0.0.0"
                                          "x-forwarded-port" "443, 80"
                                          "x-forwarded-proto" "https, http"}})))))
