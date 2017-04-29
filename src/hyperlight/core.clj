(ns hyperlight.core
  (:require [aleph.http :as http]
            [manifold.deferred :as d]))

(def default-pool
  (http/connection-pool
    {:connections-per-host 256
     :total-connections 2048
     :connection-options
     {:raw-stream? true :keep-alive? true :insecure? true}}))

(defn format-set-cookies
  "Formats set-cookie headers."
  [{headers :headers :as rsp}]
  (if-let [set-cookie (http/get-all headers "set-cookie")]
    (assoc-in rsp [:headers "set-cookie"] (into [] set-cookie))
    rsp))

(defn wrap-format-set-cookies
  "Wraps formatting of set-cookie headers."
  [handler]
  (fn [req]
    (d/let-flow [rsp (handler req)]
      (format-set-cookies rsp))))

(defn proxy-req
  "Proxies a request."
  [{headers :headers :as req} req-options]
  (let [default-req-options
        {:pool default-pool
         :throw-exceptions? false
         :follow-redirects? false}]
    (d/chain
      (http/request
        (merge
          req
          default-req-options
          req-options)))))

(defn create-proxy-handler
  "Creates a proxy handler."
  [scheme port server-name]
  (-> (fn [req]
        (proxy-req
          req
          {:scheme scheme
           :port port
           :server-name server-name}))
      wrap-format-set-cookies))

(defn start-proxy-server
  "Starts a proxy server."
  ([handler]
   (start-proxy-server
     handler
     {:port 3000 :raw-stream? true :executor :none}))
  ([handler options]
   (http/start-server handler options)))
