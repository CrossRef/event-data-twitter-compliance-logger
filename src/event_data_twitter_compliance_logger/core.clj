(ns event-data-twitter-compliance-logger.core
  "Collect Twitter Compliance data"
  (:require [clojure.core.async :refer [go-loop thread go alts! buffer chan <!! >!! >! <! timeout alts!! close!]]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clj-time.coerce :as clj-time-coerce]
            [clj-time.core :as clj-time]
            [config.core :refer [env]]
            [event-data-common.status :as status]
            [clj-http.client :as client]
            [event-data-common.storage.s3 :as s3]
            [event-data-common.storage.store :as store]
            [signal.handler :as handler]
            [clojure.java.io :as io])
  (:import [org.apache.commons.codec.digest DigestUtils])
  (:gen-class))

; http://support.gnip.com/apis/compliance_firehose2.0/api_reference.html
(def num-partitions 8)
(def partitions (map str (range 1 (inc num-partitions))))

(def timeout-duration
  "Time to wait for a new line before timing out. This should be greater than the rate we expect to get events. 
   Two minutes should cover the worst case."
  120000)

(defn run
  [tweet-id-channel user-id-channel url]
  "Send parsed events to the chan and block.
   On exception, log and exit (allowing it to be restarted)"
  (try
    (let [response (client/get url
                    {:as :stream :basic-auth [(:gnip-username env) (:gnip-password env)]})
          stream (:body response)
          lines (line-seq (io/reader stream))]
        (loop [lines lines]
          (when lines
            (let [timeout-ch (timeout timeout-duration)
                  result-ch (thread (try [(or (first lines) :nil) (rest lines)] (catch java.io.IOException ex (do (log/error "Error getting line from PowerTrack:" (.getMessage ex)) nil))))
                  [[x xs] chosen-ch] (alts!! [timeout-ch result-ch])]

              ; timeout: x is nil, xs is nil
              ; null from server: x is :nil, xs is rest
              ; data from serer: x is data, xs is rest
              (cond
                ; nil from timeout
                (nil? x) (.close stream)
                ; empty string from API, ignore
                (clojure.string/blank? x) (recur xs)
                ; :nil, deliberately returned above
                (= :nil x) (recur xs)
                :default (let [parsed (json/read-str x :key-fn keyword)]
                           (when parsed
                             (when-let [tweet-id (-> parsed :delete :status :id)]
                               (>!! tweet-id-channel tweet-id))

                             (when-let [user-id (-> parsed :user_suspended :id)]
                               (>!! user-id-channel user-id))

                             (when-let [user-id (-> parsed :user_protect :id)]
                               (>!! user-id-channel user-id))

                             (when-let [user-id (-> parsed :user_suspend :id)]
                               (>!! user-id-channel user-id)))
                             (recur xs)))))))
    (catch Exception ex (do
      (log/info (.getMessage ex))
      (.printStackTrace ex)))))

(defn run-loop
  [tweet-id-channel user-id-channel url]
  (loop [timeout-delay 30000]
    (log/info "Starting / restarting." url)
    (run tweet-id-channel user-id-channel url)
    (log/info "Stopped" url)
    (log/info "Try again in" timeout-delay "ms" url)
    (Thread/sleep timeout-delay)
    (recur timeout-delay)))

(def tweet-partition-size 1000000) ; 1000000
(def user-partition-size 10000)

(def store (delay (s3/build (:s3-key env) (:s3-secret env) (:s3-region-name env) (:s3-bucket-name env))))

(def tweet-prefix "twitter/tweet-deletions/")
(def user-prefix "twitter/user-deletions/")

(defn terminate
  [tweet-id-channel user-id-channel thread-channels]
  (log/warn "Terminating!")
  (close! tweet-id-channel)
  (close! user-id-channel)
  (doseq [thread-channel thread-channels]
    (close! thread-channel))
  (log/info "Closed all channels."))

(defn -main
  [& args]
  (log/info "Starting...")
  (let [urls (map #(str (:compliance-url env) "?partition=" %) partitions)
        tweet-id-channel (chan (buffer 100000) (partition-all tweet-partition-size))
        user-id-channel (chan (buffer 100000) (partition-all user-partition-size))
        thread-channels (map #(thread 
                        (log/info "Start thread " %)
                        (run-loop tweet-id-channel user-id-channel %)) urls)
        
        done-saving-tweets (promise)
        done-saving-authors (promise)]
    
    ; Monitor the threads to see if any of them dies.
    (go
      (log/info "Waiting to see if any threads die.")
      (alts! thread-channels)
      (log/info "One thread died! Calling terminate")
      (terminate tweet-id-channel user-id-channel thread-channels))

    (.addShutdownHook
      (Runtime/getRuntime)
      (new Thread
        (fn []
          (log/info "Shutdown hook started")
          (terminate tweet-id-channel user-id-channel thread-channels)
          (deref done-saving-tweets)
          (log/info "Shutdown hook finished saving all tweets")
          (deref done-saving-authors)
          (log/info "Shutdown hook finished saving all authors")
          (log/info "Shutdown hook finished gracefully."))))

    ; Do the same thing for both channels.
    ; Loop over the input channels until they're closed. This will only happen when terminate is called.
    (log/info "Running on" (count thread-channels) "threads")
    (go-loop []
      ; If the channel was closed, send the signal and stop recurring.
      ; The partition-all transducer means that these come in chunks. Last time round, chunk will be shorter.
      (if-let [item (<! tweet-id-channel)]
        (let [storage-key (str tweet-prefix (str (clj-time/now)))]
          (log/info "Saving Tweet ID chunk size" (count item) "at" storage-key "to" (:s3-bucket-name env))
          (store/set-string @store storage-key (json/write-str item))
          (log/info "Saved Tweet ID chunk.")
          (recur))
        (deliver done-saving-tweets true)))

    (go-loop []
      (if-let [item (<! user-id-channel)]
        (let [storage-key (str user-prefix (str (clj-time/now)))]
          (log/info "Saving User ID chunk size" (count item) "at" storage-key "to" (:s3-bucket-name env))
          (store/set-string @store storage-key (json/write-str item))
          (log/info "Saved User ID chunk.")
          (recur))
        (deliver done-saving-authors true)))

    ; We only get here if the chans were closed, leaving the handlers to terminate.
    (deref done-saving-tweets)
    (log/info "Finished saving all tweets")
    (deref done-saving-authors)
    (log/info "Finished saving all authors")

    (log/info "Graceful exit.")))

  