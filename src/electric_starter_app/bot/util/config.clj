(ns electric-starter-app.bot.util.config
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn get-config
  "Reads configuration from config.edn file.
   config-path - vector of keywords representing the path to desired config section
   Returns the entire config if no path is provided."
  ([]
   (get-config []))
  ([config-path]
   (log/debug "Loading configuration from config.edn with path" config-path)
   (try
     (let [config-file (io/resource "config.edn")
           _ (log/debug "Found config file at" config-file)
           full-config (-> config-file
                         slurp
                         edn/read-string)
           _ (log/debug "Successfully parsed config.edn")
           config-value (if (empty? config-path)
                          full-config
                          (get-in full-config config-path))]
       (if config-value
         (do
           (log/info "Configuration loaded successfully for path" config-path)
           (log/debug "Loaded config content:" config-value)
           config-value)
         (do
           (log/error "No configuration found at path" config-path)
           (throw (ex-info "Configuration path not found"
                    {:path config-path
                     :available-keys (keys full-config)})))))
     (catch Exception e
       (log/error e "Failed to load configuration")
       (throw e)))))