(ns hyperlight.http-proxy
  (:require [aleph.http :as http]
            [aleph.netty :as netty]
            [hyperlight.middleware :as middleware]
            [manifold.deferred :as d])
  (:import [io.netty.channel ChannelPipeline]
           [io.netty.handler.ssl SslContext]))

(def pools (atom {}))

(def default-pool
  (http/connection-pool
    {:connection-options {:keep-alive? true}}))

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
      :keep-alive? true}}))

(defn proxy-req
  "Proxies a request."
  [{:keys [body headers query-string request-method uri]
    :as req}
   {:keys [sni-server-name port scheme server-name url]
    :as req-options}]
  (let [pool (if sni-server-name
               (or (get @pools sni-server-name)
                   (let [new-pool (create-sni-conn-pool sni-server-name)]
                     (swap! pools assoc
                       sni-server-name
                       new-pool)
                     new-pool))
               default-pool)
        default-req-options
        {:pool pool
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

(defn create-handler
  "Creates a proxy handler."
  [{:keys [format-set-cookies? format-x-forwarded?]
    :or {format-set-cookies? true format-x-forwarded? true}
    :as req-options}]
  (as-> #(proxy-req % req-options) handler
    (if format-set-cookies?
      (middleware/wrap-format-set-cookies handler)
      handler)
    (if format-x-forwarded?
      (middleware/wrap-x-forwarded handler)
      handler)))

(defn start-server
  "Starts an HTTP server using the provided Ring `handler`. Returns a server
  object."
  [handler {:or {executor :none}
            :as options}]
  (http/start-server handler options))