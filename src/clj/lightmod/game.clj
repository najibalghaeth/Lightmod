(ns lightmod.game
  (:require [clojure.java.io :as io]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.util.response :refer [redirect not-found]]
            [ring.util.request :refer [body-string]]
            [nightcode.state :refer [runtime-state]]
            [cljs.build.api :refer [build]]
            [nightcode.utils :as u]))

(def redirects {"/" "/index.html"
                "/main.js" "/.out/main.js"})

(def ending-redirects {"/p5.js" "/.out/js/p5.js"
                       "/p5.tiledmap.js" "/.out/js/p5.tiledmap.js"})

(defn handler [request]
  (let [uri (:uri request)]
    (or (when-let [new-uri (redirects uri)]
          (redirect new-uri))
        (when-let [new-uri (some (fn [[k v]]
                                   (when (.endsWith uri k) v))
                             ending-redirects)]
          (when (not= uri new-uri)
            (redirect new-uri)))
        (let [uri (if (.startsWith uri "/") (subs uri 1) uri)
              file (io/file (:project-dir @runtime-state) uri)]
          (when (.exists file)
            {:status 200
             :body (slurp file)}))
        (not-found ""))))

(defn start-web-server! []
  (-> handler
      (wrap-content-type)
      (run-jetty {:port 0 :join? false})
      .getConnectors
      (aget 0)
      .getLocalPort))

(defn init-game! [scene]
  (let [dir (:project-dir @runtime-state)]
    (build dir
      {:output-to (.getCanonicalPath (io/file dir ".out" "main.js"))
       :output-dir (.getCanonicalPath (io/file dir ".out"))
       :main (str (.getName (io/file dir)) ".core")
       :asset-path ".out"
       ; apparently :foreign-libs requires the paths to be relative
       :foreign-libs [{:file (u/get-relative-path
                               (.getCanonicalPath (io/file "."))
                               (.getCanonicalPath (io/file dir ".out" "js" "p5.js")))
                       :provides ["p5.core"]}
                      {:file (u/get-relative-path
                               (.getCanonicalPath (io/file "."))
                               (.getCanonicalPath (io/file dir ".out" "js" "p5.tiledmap.js")))
                       :provides ["p5.tiled-map"]
                       :requires ["p5.core"]}]}))
  (let [port (start-web-server!)
        game (.lookup scene "#game")
        engine (.getEngine game)]
    (.load engine (str "http://localhost:" port "/"))))

