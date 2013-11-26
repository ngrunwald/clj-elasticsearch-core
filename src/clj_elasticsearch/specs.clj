(ns clj-elasticsearch.specs
  (:require [clojure.string :as str]))

(defprotocol PCallAPI
  (make-api-call [this method-name options] "Use the given ES Client to make an API call"))

(defn make-api-call*
  [impl client nam options]
  (if-let [f (impl nam)]
    (f client options)
    (let [error-message (format "No implementation found for method %s in client %s" nam client)]
      (throw (ex-info error-message {:name nam :client client})))))

(defmulti make-client "Makes an ES client from the given type and spec"
  (fn [type _] type))

(defmulti make-listener "Builds a listener for the given implementation"
  (fn [type _] (cond
                (#{:node :transport :native} type) :native
                (= type :rest) :rest)))

(defn parse-json-key
  [^String k]
  (-> k
      (str/replace #"^_" "")
      (.replace \_ \-)
      (keyword)))

(def global-specs
  {;; client
   "org.elasticsearch.action.index.IndexRequest"
   {:symb 'index-doc :impl :client :constructor [:index] :required [:source :type]
    :rest-uri [:index :type :id] :rest-method :put/post}
   "org.elasticsearch.action.search.SearchRequest"
   {:symb 'search :impl :client :constructor [] :required []
    :aliases {:index :indices :type :types}
    :rest-uri [:indices :types "_search"] :rest-method :get
    :rest-default {:indices "_all"}}
   "org.elasticsearch.action.get.GetRequest"
   {:symb 'get-doc :impl :client :constructor [:index] :required [:id]
    :rest-uri [:index :type :id] :rest-method :get
    :rest-default {}}
   "org.elasticsearch.action.count.CountRequest"
   {:symb 'count-docs :impl :client :constructor [:indices] :required []
    :aliases {:index :indices :type :types}
    :rest-uri [:indices :type "_count"] :rest-method :get
    :rest-default {:indices "_all"}}
   "org.elasticsearch.action.delete.DeleteRequest"
   {:symb 'delete-doc :impl :client :constructor [:index :type :id] :required []
    :rest-uri [:index :type :id] :rest-method :delete}
   "org.elasticsearch.action.deletebyquery.DeleteByQueryRequest"
   {:symb 'delete-by-query :impl :client :constructor [] :required [:query]
    :aliases {:index :indices :type :types}
    :rest-uri [:indices :types "_query"] :rest-method :delete
    :rest-default {:index "_all"}}
   "org.elasticsearch.action.mlt.MoreLikeThisRequest"
   {:symb 'more-like-this :impl :client :constructor [:index] :required [:id :type]
    :rest-uri [:index :type "_mlt"] :rest-method :get
    :rest-default {:index "_all"}}
   "org.elasticsearch.action.percolate.PercolateRequest"
   {:symb 'percolate :impl :client :constructor [:index :type] :required [:source]
    :rest-uri [:index :type "_percolate"] :rest-method :get}
   "org.elasticsearch.action.search.SearchScrollRequest"
   {:symb 'scroll :impl :client :constructor [:scroll-id] :required []
    :rest-uri [:index :type "_search" "scroll"] :rest-method :get}
   ;; for es > 0.20
   "org.elasticsearch.action.update.UpdateRequest"
   {:symb 'update-doc :impl :client :constructor [:index :type :id] :required []
    :rest-uri [:index :type :id "_update"] :rest-method :post}

   ;; indices
   "org.elasticsearch.action.admin.indices.optimize.OptimizeRequest"
   {:symb 'optimize-index :impl :indices :constructor [] :required []
    :rest-uri [:index "_optimize"] :rest-method :post}
   "org.elasticsearch.action.admin.indices.analyze.AnalyzeRequest"
   {:symb 'analyze-request :impl :indices :constructor [:index :text] :required []
    :rest-uri [:index "_analyze"] :rest-method :get :rest-default {:index nil}}
   "org.elasticsearch.action.admin.indices.cache.clear.ClearIndicesCacheRequest"
   {:symb 'clear-index-cache :impl :indices :constructor [:indices] :required []
    :aliases {:index :indices}
    :rest-uri [:indices "_cache" "clear"] :rest-method :post :rest-default {:indices "_all"}}
   "org.elasticsearch.action.admin.indices.close.CloseIndexRequest"
   {:symb 'close-index :impl :indices :constructor [:index] :required []
    :rest-uri [:index "_close"] :rest-method :post}
   "org.elasticsearch.action.admin.indices.create.CreateIndexRequest"
   {:symb 'create-index :impl :indices :constructor [:index] :required []
    :rest-uri [:index] :rest-method :put}
   "org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest"
   {:symb 'delete-index :impl :indices :constructor [:indices] :required []
    :aliases {:index :indices}
    :rest-uri [:indices] :rest-method :delete :rest-default {:indices "_all"}}
   "org.elasticsearch.action.admin.indices.mapping.delete.DeleteMappingRequest"
   {:symb 'delete-mapping :impl :indices :constructor [:indices] :required []
    :aliases {:index :indices}
    :rest-uri [:indices :type] :rest-method :delete}
   "org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest"
   {:symb 'delete-template :impl :indices :constructor [:name] :required []
    :rest-uri ["_template" :name] :rest-method :delete}
   ;; for es < 0.20
   "org.elasticsearch.action.admin.indices.exists.IndicesExistsRequest"
   {:symb 'exists-index :impl :indices :constructor [:indices] :required []
    :aliases {:index :indices}}
   ;; for es > 0.20
   "org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest"
   {:symb 'exists-index :impl :indices :constructor [:indices] :required []
    :aliases {:index :indices}
    :rest-uri [:indices] :rest-method :head
    :on-success (fn [_] {:exists? true})
    :on-failure (fn [{:keys [status] :as resp}]
                  (if (= status 404)
                    {:exists? false} resp))}
   "org.elasticsearch.action.admin.indices.flush.FlushRequest"
   {:symb 'flush-index :impl :indices :constructor [:indices] :required []
    :aliases {:index :indices}
    :rest-uri [:indices "_flush"] :rest-method :post}
   "org.elasticsearch.action.admin.indices.gateway.snapshot.GatewaySnapshotRequest"
   {:symb 'gateway-snapshot :impl :indices :constructor [:indices] :required []
    :aliases {:index :indices}
    :rest-uri [:indices "_snapshot"] :rest-method :post :rest-default {:indices "_all"}}
   "org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest"
   {:symb 'put-mapping :impl :indices :constructor [:indices] :required []
    :aliases {:index :indices}
    :rest-uri [:indices :type "_mapping"] :rest-method :put :rest-default {:indices "_all"}}
   "org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest"
   {:symb 'put-template :impl :indices :constructor [:name] :required []
    :rest-uri ["_template" :name] :rest-method :put}
   "org.elasticsearch.action.admin.indices.refresh.RefreshRequest"
   {:symb 'refresh-index :impl :indices :constructor [:indices] :required []
    :aliases {:index :indices}
    :rest-uri [:indices "_refresh"] :rest-method :post :rest-default {:indices "_all"}}
   "org.elasticsearch.action.admin.indices.segments.IndicesSegmentsRequest"
   {:symb 'index-segments :impl :indices :constructor [] :required []
    :aliases {:index :indices}
    :rest-uri [:indices "_segments"] :rest-method :get :rest-default {:indices "_all"}}
   "org.elasticsearch.action.admin.indices.stats.IndicesStatsRequest"
   {:symb 'index-stats :impl :indices :constructor [] :required []
    :aliases {:index :indices :type :types}
    :rest-uri [:indices "_stats"] :rest-method :get :rest-default {:indices "_all"}}
   "org.elasticsearch.action.admin.indices.status.IndicesStatusRequest"
   {:symb 'index-status :impl :indices :constructor [] :required []
    :aliases {:index :indices}
    :rest-uri [:indices "_status"] :rest-method :get :rest-default {:indices "_all"}}
   "org.elasticsearch.action.admin.indices.settings.UpdateSettingsRequest"
   {:symb 'update-index-settings :impl :indices :constructor [:indices] :required []
    :aliases {:index :indices}
    :rest-uri [:indices "_settings"] :rest-method :put :rest-default {:indices nil}}

   ;; cluster
   "org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest"
   {:symb 'cluster-health :impl :cluster :constructor [:indices] :required []
    :aliases {:index :indices}
    :rest-uri ["_cluster" "health" :indices] :rest-method :get :rest-default {:indices nil}}
   "org.elasticsearch.action.admin.cluster.state.ClusterStateRequest"
   {:symb 'cluster-state :impl :cluster :constructor [] :required []
    :rest-uri ["_cluster" "state"] :rest-method :get}
   "org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest"
   {:symb 'node-info :impl :cluster :constructor [] :required []
    :rest-uri ["_cluster" "nodes" :nodes-ids] :rest-method :get :rest-default {:node-ids nil}}
   "org.elasticsearch.action.admin.cluster.node.restart.NodesRestartRequest"
   {:symb 'node-restart :impl :cluster :constructor [:nodes-ids] :required []}
   "org.elasticsearch.action.admin.cluster.node.shutdown.NodesShutdownRequest"
   {:symb 'node-shutdown :impl :cluster :constructor [:nodes-ids] :required []
    :rest-uri ["_cluster" "nodes" :nodes-ids "shutdown"] :rest-method :post
    :rest-default {:nodes-ids nil}}
   "org.elasticsearch.action.admin.cluster.node.stats.NodesStatsRequest"
   {:symb 'nodes-stats :impl :cluster :constructor [:nodes-ids] :required []
    :rest-uri ["_cluster" "nodes" :nodes-ids "stats"] :rest-method :get
    :rest-default {:nodes-ids nil}}
   "org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest"
   {:symb 'update-cluster-settings :impl :cluster :constructor [] :required []
    :rest-uri ["_cluster" "settings"] :rest-method :put}})
