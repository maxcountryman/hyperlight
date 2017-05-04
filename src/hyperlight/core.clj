(ns hyperlight.core
  (:require [aleph.http :as http]
            [manifold.deferred :as d]))

(def default-pool
  (http/connection-pool
    {:connections-per-host 256
     :total-connections 2048
     :connection-options
     {:keep-alive? true :insecure? true}}))

(defn format-set-cookies
  "Formats set-cookie headers."
  [{headers :headers :as rsp}]
  (if-let [set-cookie (http/get-all headers "set-cookie")]
    (assoc-in rsp [:headers "set-cookie"] (into [] set-cookie))
    rsp))

(defn- get-x-forwarded-for
  [req]
  (if-let [forwarded-for (get-in req [:headers "x-forwarded-for"])]
    (str forwarded-for "," (:remote-addr req))
    (:remote-addr req)))

(defn format-x-forwarded
  "Formats x-forwarded headers."
  [req]
  (assoc-in req [:headers "x-forwarded-for"] (get-x-forwarded-for req)))

(defn wrap-format-set-cookies
  "Wraps formatting of set-cookie headers."
  [handler]
  (fn [req]
    (d/let-flow [rsp (handler req)]
      (format-set-cookies rsp))))

(defn wrap-x-forwarded
  "Wraps formatting of x-forwarded headers."
  [handler]
  (fn [req]
    (handler (format-x-forwarded req))))

(defn proxy-req
  "Proxies a request."
  [{:keys [body headers query-string request-method uri]
    :as req}
   {:keys [port scheme server-name url]
    :as req-options}]
  (let [default-req-options
        {:pool default-pool
         :throw-exceptions? false
         :follow-redirects? false}
        uri-options
        (if url
          {:url (str url uri)}
          {:port port :scheme scheme :server-name server-name :uri uri})]
    (d/chain'
      (http/request
        (merge
          {:body body
           :headers headers
           :query-string query-string
           :request-method request-method}
          default-req-options
          req-options
          uri-options)))))

(defn create-proxy-handler
  "Creates a proxy handler."
  [{:keys [format-set-cookies? format-x-forwarded?]
    :or {format-set-cookies? true format-x-forwarded? true}
    :as req-options}]
  (as-> #(proxy-req % req-options) handler
    (if format-set-cookies?
      (wrap-format-set-cookies handler)
      handler)
    (if format-x-forwarded?
      (wrap-x-forwarded handler)
      handler)))

(defn start-proxy-server
  "Starts a proxy server."
  ([handler]
   (start-proxy-server
     handler
     {:port 3000 :executor :none}))
  ([handler options]
   (http/start-server handler options)))
