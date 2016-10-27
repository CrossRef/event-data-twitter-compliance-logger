(ns event-data-twitter-compliance-logger.core
    (:require [config.core :refer [env]]
              [clojure.tools.logging :as log]
              [clojure.core.async :refer [chan <! >!! go-loop]])
    (:import [org.crossref.eventdata.twitter ComplianceStreamSaver])
    (:import [com.amazonaws.services.s3 AmazonS3 AmazonS3Client]
             [com.amazonaws.auth BasicAWSCredentials]
             [com.amazonaws.services.s3.model GetObjectRequest PutObjectRequest ObjectMetadata]
             [com.amazonaws AmazonServiceException AmazonClientException])
  (:gen-class))

(def upload-files-chan (chan))

(defn upload-compliance-file
  "Upload a file. Delete on completion."
  [file]
  ; It's possible that the signal was twice, and the file's already been uploaded.
  (when (and file (.exists file))
    (let [keyname (str "twitter/compliance/" (.getName file))
          aws-client (new AmazonS3Client (new BasicAWSCredentials (:s3-access-key-id env) (:s3-secret-access-key env)))
          request (new PutObjectRequest (:storage-bucket env) keyname file)]
      (log/info "Upload Compliance file" file "to" keyname)
      (.putObject aws-client request))
      (.delete file)
      (log/info "Finished upload Compliance file" file)))

(defn run-upload []
  (go-loop [file (<! upload-files-chan)]
    (try
      (upload-compliance-file file)
    (catch Exception e
      (log/fatal "Error uploading file: " (.toString e))))))

(defn ingest-compliance
  "Consume all compliance streams, save and upload to S3."
  []
  (let [dir-path (:temp-dir env)
        dir (new java.io.File dir-path)]
    (when-not (.exists (new java.io.File dir-path))
      (when-not (.mkdirs dir)
        (log/fatal "Failed to create directory" dir-path)))

    ; The stream comes in eight partitions.
    (let [t1 (new Thread (fn [] (.main (new ComplianceStreamSaver) (str (:gnip-compliance-url env) "?partition=1") (:gnip-username env) (:gnip-password env) #(>!! upload-files-chan %) dir-path "-1" )))
          t2 (new Thread (fn [] (.main (new ComplianceStreamSaver) (str (:gnip-compliance-url env) "?partition=2") (:gnip-username env) (:gnip-password env) #(>!! upload-files-chan %) dir-path "-2" )))
          t3 (new Thread (fn [] (.main (new ComplianceStreamSaver) (str (:gnip-compliance-url env) "?partition=3") (:gnip-username env) (:gnip-password env) #(>!! upload-files-chan %) dir-path "-3" )))
          t4 (new Thread (fn [] (.main (new ComplianceStreamSaver) (str (:gnip-compliance-url env) "?partition=4") (:gnip-username env) (:gnip-password env) #(>!! upload-files-chan %) dir-path "-4" )))
          t5 (new Thread (fn [] (.main (new ComplianceStreamSaver) (str (:gnip-compliance-url env) "?partition=5") (:gnip-username env) (:gnip-password env) #(>!! upload-files-chan %) dir-path "-5" )))
          t6 (new Thread (fn [] (.main (new ComplianceStreamSaver) (str (:gnip-compliance-url env) "?partition=6") (:gnip-username env) (:gnip-password env) #(>!! upload-files-chan %) dir-path "-6" )))
          t7 (new Thread (fn [] (.main (new ComplianceStreamSaver) (str (:gnip-compliance-url env) "?partition=7") (:gnip-username env) (:gnip-password env) #(>!! upload-files-chan %) dir-path "-7" )))
          t8 (new Thread (fn [] (.main (new ComplianceStreamSaver) (str (:gnip-compliance-url env) "?partition=8") (:gnip-username env) (:gnip-password env) #(>!! upload-files-chan %) dir-path "-8" )))
          ts [t1 t2 t3 t4 t5 t6 t7 t8]]
      
      (doseq [t ts]
        (.start t))

      (doseq [t ts]
        (.join t)))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (log/info "Start ingesting compliance stream")
  ; Start uploading in the background.
  (run-upload)

  ; This should block forever.
  (ingest-compliance)

  (log/fatal "Stopped ingesting compliance stream!"))
