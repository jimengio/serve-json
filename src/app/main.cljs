
(ns app.main
  (:require [skir.core :as skir]
            ["fs" :as fs]
            ["path" :as path]
            [cirru-edn.core :refer [parse]]
            [clojure.string :as string]))

(defn load-config! [] (parse (fs/readFileSync "config.cirru" "utf8")))

(defonce *configs (atom (load-config!)))

(defn match-path [segments rule-path]
  (comment println "matching" segments rule-path)
  (if (empty? rule-path)
    {:matches? true, :rest segments}
    (cond
      (empty? segments) {:matches false, :rest segments, :rest-rule rule-path}
      (= (first segments) (first rule-path)) (recur (rest segments) (rest rule-path))
      (string/starts-with? (first rule-path) ":") (recur (rest segments) (rest rule-path))
      :else {:matches? false, :rest segments, :rest-rule rule-path})))

(defn split-path [x] (->> (string/split x "/") (filter (fn [x] (not (string/blank? x))))))

(defn find-match-rule [segments rules]
  (let [current-match (loop [xs rules]
                        (let [cursor (first xs)]
                          (comment println "compare" segments "to" cursor)
                          (if (empty? xs)
                            nil
                            (let [result (match-path segments (split-path (:path cursor)))]
                              (comment println "result" result)
                              (comment println "cursor" cursor)
                              (if (:matches? result)
                                (assoc result :rule cursor)
                                (recur (rest xs)))))))]
    (comment println "current rule" current-match)
    (if (nil? current-match)
      nil
      (let [matched-rule (:rule current-match)]
        (if (empty? (:rest current-match))
          matched-rule
          (recur (:rest current-match) (:next matched-rule)))))))

(defn handle-request! [req]
  (let [routes (:routes @*configs)
        segments (split-path (:url req))
        that-rule (find-match-rule segments routes)]
    (comment println "find rule" that-rule)
    (if (nil? that-rule)
      {:code 400,
       :message "OK",
       :headers {:Content-Type "application/json"},
       :body (str "No matching path for " (:url req))}
      (let [info (get that-rule (:method req))]
        (if (some? info)
          (case (:type info)
            :file
              (let [mock-path (path/join js/process.env.PWD (:file info))]
                (if (fs/existsSync mock-path)
                  {:code 200,
                   :message "OK",
                   :headers {:Content-Type "application/json"},
                   :body (fs/readFileSync mock-path "utf8")}
                  {:code 400,
                   :message "Unknown request",
                   :headers {:Content-Type "application/json"},
                   :body (str mock-path " not found")}))
            {:code 400,
             :message "Unknown request",
             :headers {:Content-Type "application/json"},
             :body (clj->js
                    {:code 400, :message "Unknown info", :rule that-rule, :info info})})
          {:code 400,
           :message "Unknown request",
           :headers {:Content-Type "application/json"},
           :body (clj->js {:code 400, :message "Unknown rule", :rule that-rule, :info info})})))))

(defn start-server! []
  (comment println @*configs)
  (skir/create-server! #(handle-request! %) {:port 8008}))

(defn main! [] (println "Started.") (start-server!))

(defn reload! []
  (.clear js/console)
  (reset! *configs (load-config!))
  (println "Reloaded configs."))