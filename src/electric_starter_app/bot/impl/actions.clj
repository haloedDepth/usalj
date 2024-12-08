(ns electric-starter-app.bot.impl.actions
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.storage.database :as db]))

;; Action handler implementations
(defn send-message-handler
  "Handles sending a message to a Discord channel.
   Returns a map containing success status and optional error details."
  [{:keys [connection channel-id content]} ctx]
  (log/debug "Attempting to send message"
    {:channel-id channel-id
     :content content})
  (if-let [channel (.getTextChannelById connection channel-id)]
    (try
      ;; Handle different content types (string, map with success/message)
      (let [final-content (cond
                            (and (map? content)
                              (contains? content :success)
                              (contains? content :message))
                            (:message content)

                            (map? content)
                            (str content)

                            :else
                            content)
            future-message (-> channel
                             (.sendMessage final-content)
                             .submit)]
        {:message-id (.getId (.get future-message))
         :success true
         :error nil})
      (catch Exception e
        (log/error e "Failed to send message"
          {:channel-id channel-id
           :content content})
        {:success false
         :message-id nil
         :error {:type :message-failed
                 :details (.getMessage e)}}))
    (do
      (log/error "Could not find channel" {:channel-id channel-id})
      {:success false
       :message-id nil
       :error {:type :channel-not-found
               :details {:channel-id channel-id}}})))

(defn end-conversation-handler
  "Simple handler that marks a conversation as complete.
   Always returns success as this is a terminal operation."
  [_ ctx]
  (log/debug "Ending conversation")
  {:success true})

;; Database operation handlers
(defmulti handle-db-operation
  "Multimethod for handling different types of database operations"
  (fn [operation-type & _] operation-type))

(defmethod handle-db-operation :profile-create
  [_ {:keys [member-id]} _]
  (log/debug "Creating member profile" {:member-id member-id})
  (let [result (db/create-profile! member-id)]
    (if (:error result)
      {:success false
       :message (get-in result [:error :message])
       :error (:error result)}
      {:success true
       :message "Profile created successfully"})))

(defmethod handle-db-operation :default
  [operation-type & _]
  (log/error "Unsupported database operation" {:type operation-type})
  {:success false
   :error {:type :unsupported-operation
           :message (str "Unsupported database operation: " operation-type)}})

(defn db-operation-handler
  "Main handler for database operations.
   Routes operations to appropriate handlers based on operation type."
  [{:keys [operation member-id]} ctx]
  (handle-db-operation operation {:member-id member-id} ctx))

;; Action definitions
(def send-message-action
  {:name :send-message
   :description "Sends a message to a Discord channel"
   :spec {:inputs {:connection {:type :jda-client
                                :required true
                                :doc "JDA client instance"}
                   :channel-id {:type :string
                                :required true
                                :doc "Discord channel ID"}
                   :bot-name {:type :string
                              :required true
                              :doc "Name of bot sending message"}
                   :content {:type :any
                             :required true
                             :doc "Message content - string or content map"}}
          :outputs {:message-id {:type :string
                                 :required false
                                 :doc "ID of sent message"}
                    :success {:type :boolean
                              :required true
                              :doc "Whether message was sent successfully"}
                    :error {:type :map
                            :required false
                            :doc "Error information if message failed"}}}
   :handler send-message-handler})

(def end-conversation-action
  {:name :end-conversation
   :description "Ends the current conversation"
   :spec {:inputs {}
          :outputs {:success {:type :boolean
                              :required true
                              :doc "Whether conversation ended successfully"}}}
   :handler end-conversation-handler})

(def db-operation-action
  {:name :db-operation
   :description "Performs database operations"
   :acceptable-errors #{:profile-exists}  ; This error should continue flow
   :spec {:inputs {:operation {:type :keyword
                               :required true
                               :doc "Type of database operation to perform"}
                   :member-id {:type :string
                               :required true
                               :doc "Discord member ID to operate on"}}
          :outputs {:success {:type :boolean
                              :required true
                              :doc "Whether the operation was successful"}
                    :message {:type :string
                              :required true
                              :doc "Operation result message"}
                    :error {:type :map
                            :required false
                            :doc "Error information if operation failed"}}}
   :handler db-operation-handler})

;; Action registry
;; Simply register actions without validation - validation will happen elsewhere
(def registered-actions
  (try
    (let [actions [send-message-action
                   end-conversation-action
                   db-operation-action]]
      (into {} (map (juxt :name identity) actions)))
    (catch Exception e
      (log/error e "Failed to register actions")
      (throw e))))