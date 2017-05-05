# Hyperlight

An HTTP reverse proxy written with Aleph.

## Usage

Creating a proxy is simple with Hyperlight.

```clj
(require '[hyperlight.core :as h])

(def example-proxy
  (h/create-proxy-handler {:url "http://www.example.com/"}))

(h/start-proxy-server example-proxy)
```

By default the proxy server will listen on [localhost:3000](http://localhost:3000/).
