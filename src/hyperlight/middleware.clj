(ns hyperlight.middleware
  (:require [aleph.http :as http]
            [manifold.deferred :as d]))

(defn- get-x-forwarded-for
  [req]
  (if-let [forwarded-for (get-in req [:headers "x-forwarded-for"])]
    (str forwarded-for "," (:remote-addr req))
    (:remote-addr req)))

(defn format-set-cookies
  "Formats set-cookie headers."
  [{headers :headers :as rsp}]
  (if-let [set-cookie (http/get-all headers "set-cookie")]
    (assoc-in rsp [:headers "set-cookie"] (into [] set-cookie))
    rsp))

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
