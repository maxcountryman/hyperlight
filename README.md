# Hyperlight

A performance-focused HTTP reverse proxy written with [Aleph](http://aleph.io).

## Usage

Creating a proxy is simple with Hyperlight.

```clj
(require '[hyperlight.http-proxy :as http-proxy])

(def example-proxy
  (http-proxy/create-handler
    {:url "http://www.example.com/"}))

(http-proxy/start-server example-proxy {:port 3000})
```

The proxy server will now be listening on [localhost:3000](http://localhost:3000).
