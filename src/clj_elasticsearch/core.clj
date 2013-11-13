(ns clj-elasticsearch.core
  (:require [clj-elasticsearch.specs :as utils]))

(def ^{:dynamic true} *client*)

(defmacro with-client
  "uses an existing client in the body, does not close it afterward"
  [client & body]
  `(binding [*client* ~client]
     (do ~@body)))

(defn- def-requests
  [specs]
  (doseq [[class-name {:keys [symb impl constructor required] :as spec}] specs
          :let [kw-name (-> symb (name) (keyword))]]
    (let [req-fn (fn [client options] (utils/make-api-call client kw-name options))
          symb-name (vary-meta symb merge {::spec spec})]
      (intern 'clj-elasticsearch.core symb-name req-fn))))

(def-requests utils/global-specs)
