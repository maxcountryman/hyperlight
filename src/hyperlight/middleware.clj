(ns hyperlight.middleware
  (:require [aleph.http :as http]
            [clojure.string :as string]
            [manifold.deferred :as d]))

(defn- join-headers [& headers]
  (string/join ", " (remove nil? headers)))

(defn- get-x-forwarded-for
  [{{:strs [x-forwarded-for]} :headers remote-addr :remote-addr}]
  (join-headers x-forwarded-for remote-addr))

(defn- get-x-forwarded-port
  [{{:strs [x-forwarded-port]} :headers scheme :scheme}]
  (let [secure? (= scheme :https)
        forwarding-port (if secure? "443" "80")]
    (join-headers x-forwarded-port forwarding-port)))

(defn- get-x-forwarded-proto
  [{{:strs [x-forwarded-proto]} :headers scheme :scheme}]
  (let [secure? (= scheme :https)
        forwarding-proto (if secure? "https" "http")]
    (join-headers x-forwarded-proto forwarding-proto)))

(defn format-set-cookies
  "Formats set-cookie headers."
  [{headers :headers :as rsp}]
  (if-let [set-cookie (http/get-all headers "set-cookie")]
    (assoc-in rsp [:headers "set-cookie"] (into [] set-cookie))
    rsp))

(defn format-x-forwarded
  "Formats x-forwarded headers."
  [req]
  (->
    req
    (assoc-in [:headers "x-forwarded-for"] (get-x-forwarded-for req))
    (assoc-in [:headers "x-forwarded-port"] (get-x-forwarded-port req))
    (assoc-in [:headers "x-forwarded-proto"] (get-x-forwarded-proto req))))

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
