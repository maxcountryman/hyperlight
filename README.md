# Hyperlight

A performance-focused HTTP reverse proxy written with Aleph.

## Usage

Creating a proxy is simple with Hyperlight.

```clj
(require '[hyperlight.http-proxy :as http-proxy])

(def example-proxy
  (http-proxy/create-handler {:url "http://www.example.com/"}))

(http-proxy/start-server example-proxy)
```

By default the proxy server will listen on [localhost:3000](http://localhost:3000/).
