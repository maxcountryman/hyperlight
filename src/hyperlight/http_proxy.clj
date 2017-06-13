(ns hyperlight.http-proxy
  (:require [aleph.http :as http]
            [aleph.netty :as netty]
            [hyperlight.middleware :as middleware])
  (:import [io.netty.channel ChannelPipeline]
           [io.netty.handler.ssl SslContext]))

(def pools (atom {}))

(def default-pool
  (http/connection-pool
    {:connection-options
     {:keep-alive? true :raw-stream? true}}))

(defn create-sni-conn-pool
  "Creates a connection pool for a given `server-name` with Server Name
  Indication provided. Returns the connection pool."
  [server-name]
  (http/connection-pool
    {:connection-options
     {:pipeline-transform
      (fn [^ChannelPipeline p]
        (when (some #{"ssl-handler"} (.names p))
          (let [^SslContext insecure-ssl-context (netty/insecure-ssl-client-context)
                sni-ssl-handler (.newHandler insecure-ssl-context
                                  (-> p .channel .alloc) server-name 443)]
            (.replace p "ssl-handler" "sni-ssl-handler" sni-ssl-handler))))
      :keep-alive? true
      :raw-stream? true}}))

(defn proxy-request
  "Given a request map, makes an HTTP request via this map, and returns a
  deferred representing the response."
  [{:keys [scheme server-name sni-server-name uri url] :as req}]
  (let [default-options
        {:pool (if sni-server-name
                 (or (get @pools sni-server-name)
                     (let [new-pool (create-sni-conn-pool sni-server-name)]
                       (swap! pools assoc sni-server-name new-pool)
                       new-pool))
                 default-pool)
         :throw-exceptions? false
         :follow-redirects? false}
        url-options
        (if url
          {:url (str url uri)}
          {:scheme scheme :server-name server-name :uri uri})
        merged-req (merge default-options req url-options)]
    (http/request merged-req)))

(defn create-handler
  "Given a map of request options which are merged with the received request
  map, returns a proxy handler. Extends the request map such that
  `:sni-server-name` may be provided to indicate the server-name sent with
  secure requests. Returns the handler wrapped in convenience middleware."
  [proxy-req]
  (letfn [(handler [req]
            (let [merge-req-map
                  (fn [& vs]
                    (if (every? map? vs)
                      (apply merge vs)
                      (last vs)))
                  merged-req (merge-with merge-req-map req proxy-req)]
              (proxy-request merged-req)))]
    (-> handler
      middleware/wrap-format-set-cookies
      middleware/wrap-x-forwarded)))

(defn start-server
  "Starts an HTTP server using the provided Ring `handler`. Returns a server
  object."
  [handler options]
  (let [default-options {:executor :none :raw-stream? true}]
    (http/start-server handler (merge default-options options))))
