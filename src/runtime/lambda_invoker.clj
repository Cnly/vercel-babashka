(ns lambda-invoker
  "Integration with the Vercel Serverless Functions Runtime API, which is
  very similar to the AWS Lambda Runtime API (https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html)"
  (:require
   [cheshire.core :as json]
   [org.httpkit.client :as http]
   [clojure.string :as s])
  (:import [java.util Base64]
           [java.net URL URLDecoder]))

(defn- get-lambda-invocation-event
  "Retrieves an invocation event from the Lambda runtime API as an HTTP response.
  The HTTP response body is a JSON document containing the event data from the function trigger.
  The HTTP resopnse headers contain additional information about the invocation."
  [lambda-api-endpoint]
  @(http/request
    {:method   :get
     :url      (str "http://" lambda-api-endpoint "/2018-06-01/runtime/invocation/next")
     :timeout  2147483647
     :as :text}))

(defn- send-invocation-response [lambda-api-endpoint req-id response-data]
  @(http/request
    {:method  :post
     :url     (str "http://" lambda-api-endpoint "/2018-06-01/runtime/invocation/" req-id "/response")
     :body    response-data
     :headers {"content-type" "application/json"}}))

(defn- send-invocation-error [lambda-api-endpoint req-id err-msg]
  @(http/request
    {:method  :post
     :url     (str "http://" lambda-api-endpoint "/2018-06-01/runtime/invocation/" req-id "/error")
     :body    (json/encode {:errorMessage err-msg})
     :headers {"content-type" "application/json"}}))

(defn- qpair-str->qpair [qp-str]
  (map #(.decode URLDecoder % "UTF-8") (s/split qp-str #"=" 2)))

#_(qpair-str->qpair "%61=2")
;; => ("a" "2")

(defn query-str->queries-map [qstr]
  (some-> qstr
          (s/split #"&")
          (->>
           (map qpair-str->qpair)
           (map (fn [[k v]] [(keyword k) v]))
           (into {}))))

(defn- path->queries-map [path]
  (some-> (str "http://dummy" path) URL. .getQuery query-str->queries-map))

#_(path->queries-map "/hi?a=1&%62=%63d")
  ;; => {:a "1", :b "cd"}
#_(path->queries-map "/hi")
;; => nil

(defn b64decode
  ([input]
   (b64decode input true))
  ([input to-str]
   (cond-> input
     :always (->> .getBytes (.decode (Base64/getDecoder)))
     to-str String.)))

(defn- cook-http-event [event]
  (let [{{:keys [content-type]} :headers, :keys [encoding path]} event]  ;; NB: body and encoding may change
    (as-> event event
      (if (not= encoding "base64") event (if (s/starts-with? (str content-type) "multipart/form-data")
                                           (assoc event :decoded-body-bytes (b64decode (:body event) false))
                                           (assoc event
                                                  :body (b64decode (:body event))
                                                  :encoding "base64-decoded")))
      (assoc event :params (path->queries-map path)
             :form-params (when (= content-type "application/x-www-form-urlencoded")
                            (query-str->queries-map (:body event)))))))

(defn- event-data->response [handler-fn event-data]
  ;; JSON-decoded event-data can look like {:Action Invoke, :body "{\"body\":\"...\", ...
  (if-let [event (-> event-data (json/decode true) :body)]
    (let [resp (-> event (json/decode true) cook-http-event handler-fn (#(if (map? %) % {:body %})))
          {:keys [status headers body jsonify]} resp]
      (json/encode
       {:statusCode (or status 200)
        :headers    (update headers :content-type #(or % (cond
                                                           jsonify "application/json"
                                                           (bytes? body) "application/octet-stream"
                                                           :else "text/plain")))
        :body       (cond->> body
                      (bytes? body) (.encodeToString (Base64/getEncoder))
                      :finally ((if jsonify json/encode str)))
        ;; In AWS Lambda they seem to use isBase64Encoded instead of encoding
        :encoding   (when (and (bytes? body) (not jsonify)) "base64")}))
    (throw (RuntimeException. (str "The invocation event has no body: " event-data)))))

(defn- get-handler-fn []
  (if-let [entry-ns-name (System/getenv "ENTRY_NS")]
    (let [entry-ns-sym (symbol entry-ns-name)]
      (try
        (require entry-ns-sym)
        (or (ns-resolve entry-ns-sym 'handler)
            (throw (RuntimeException. (str "No handler found defined in namespace " (ns-name entry-ns-sym)))))
        (catch Exception e
          (throw (RuntimeException. (str "Failed to get handler function in namespace `" entry-ns-name "`: " e))))))
    (throw (RuntimeException. "Environment variable ENTRY_NS seems to be unset."))))

(defn -main []
  (let [lambda-api-endpoint (System/getenv "AWS_LAMBDA_RUNTIME_API")
        handler-fn (get-handler-fn)]
    (loop [event-resp (get-lambda-invocation-event lambda-api-endpoint)]
      (let [req-id (-> event-resp :headers :lambda-runtime-aws-request-id)]
        (when (or (:error event-resp)
                  (not= 200 (:status event-resp)))
          (let [err-msg "Error retrieving Lambda invocation event"]
            (send-invocation-error lambda-api-endpoint req-id err-msg)
            (throw (RuntimeException. (str err-msg ": " (pr-str (event-resp)))))))
        (try
          (->> (:body event-resp)
               (event-data->response handler-fn)
               (send-invocation-response lambda-api-endpoint req-id))
          (catch Exception e
            (println "Error handling event:" e)
            (println "Original event response:" event-resp)
            (send-invocation-error lambda-api-endpoint req-id (str e)))))
      (recur (get-lambda-invocation-event lambda-api-endpoint)))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
