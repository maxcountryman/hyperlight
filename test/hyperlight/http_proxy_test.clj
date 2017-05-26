(ns hyperlight.http-proxy-test
  (:require [aleph.http :as http]
            [aleph.netty :as netty]
            [byte-streams :as bs]
            [clojure.test :refer [deftest is]]
            [hyperlight.http-proxy :as http-proxy]))

(def server-port 8081)

(def proxy-port 3001)

(def proxy-url (str "http://localhost:" server-port))

(defn hello-world-handler
  [req]
  {:status 200
   :body "Hello, world!"})

(defn proxy-handler
  [req]
  (http-proxy/proxy-req (assoc req :url proxy-url)))

(defmacro with-server
  [server & body]
  `(let [server# ~server]
     (try
       ~@body
       (finally
         (.close ^java.io.Closeable server#)
         (netty/wait-for-close server#)))))

(defmacro with-handler
  [handler & body]
  `(with-server (http/start-server ~handler {:port server-port})
     ~@body))

(defmacro with-proxy-handler
  [handler & body]
  `(with-server (http-proxy/start-server ~handler {:port proxy-port})
     ~@body))

(deftest test-proxy-response
  (with-handler hello-world-handler
    (with-proxy-handler proxy-handler
      (let [rsp @(http/get (str "http://localhost:" proxy-port))]
        (is (= "Hello, world!" (bs/to-string (:body rsp))))))))
