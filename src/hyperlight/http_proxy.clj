(ns hyperlight.http-proxy
  (:require [aleph.http :as http]
            [aleph.netty :as netty]
            [hyperlight.middleware :as middleware])
  (:import [io.netty.channel ChannelPipeline]
           [io.netty.handler.codec.http HttpContentDecompressor]
           [io.netty.handler.ssl SslContext]))

(def default-pool (promise))

(def secure-pools (atom {}))

(defn create-conn-pool
  "Creates a connection pool. Decompresses compressed response streams if
  `decompress-content?` is `true`. Defaults to `false`. Returns the connection
  pool."
  ([]
   (create-conn-pool false))
  ([decompress-content?]
   (http/connection-pool
     {:connection-options
      {:pipeline-transform
       (fn [^ChannelPipeline p]
         (when decompress-content?
           (let [decompressor (HttpContentDecompressor.)]
             (.addAfter p "http-client" "decompressor" decompressor))))
       :keep-alive? true
       :raw-stream? true}})))

(defn create-sni-conn-pool
  "Creates a connection pool for a given `server-name` with Server Name
  Indication provided. Decompresses compressed response streams if
  `decompress-content?` is `true`. Defaults to `false`. Returns the connection
  pool."
  ([server-name]
   (create-sni-conn-pool server-name false))
  ([server-name decompress-content?]
   (http/connection-pool
     {:connection-options
      {:pipeline-transform
       (fn [^ChannelPipeline p]
         (when (some #{"ssl-handler"} (.names p))
           (let [^SslContext insecure-ssl-context (netty/insecure-ssl-client-context)
                 sni-ssl-handler (.newHandler insecure-ssl-context
                                   (-> p .channel .alloc) server-name 443)]
             (.replace p "ssl-handler" "sni-ssl-handler" sni-ssl-handler)))
         (when decompress-content?
           (let [decompressor (HttpContentDecompressor.)]
             (.addAfter p "http-client" "decompressor" decompressor))))
       :keep-alive? true
       :raw-stream? true}})))

(defn- get-default-pool
  [decompress-content?]
  (if (realized? default-pool)
    @default-pool
    (do (deliver default-pool (create-conn-pool decompress-content?))
        @default-pool)))

(defn- get-secure-pool
  [sni-server-name decompress-content?]
  (or (get @secure-pools sni-server-name)
      (let [new-pool (create-sni-conn-pool sni-server-name decompress-content?)]
        (swap! secure-pools assoc sni-server-name new-pool)
        new-pool)))

(defn get-pool
  "Returns a connection pool configured according to `sni-server-name` and
  `decompress-content?`."
  [sni-server-name decompress-content?]
  (if sni-server-name
    (get-secure-pool sni-server-name decompress-content?)
    (get-default-pool decompress-content?)))

(defn proxy-request
  "Given a request map, makes an HTTP request via this map, and returns a
  deferred representing the response."
  [{:keys [scheme server-name sni-server-name uri url decompress-content?]
    :or {decompress-content? false}
    :as req}]
  (let [default-options
        {:pool (get-pool sni-server-name decompress-content?)
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
