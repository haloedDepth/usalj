(ns electric-starter-app.bot.impl.flows
  (:require [electric-starter-app.bot.core.schema.definition.expression :refer [path-expr value-expr map-expr str-expr]]))

(def register
  {:name :register
   :description "Registers a new member in the system"
   :context-schema {:channel-id {:source :interaction
                                 :path [:channel-id]}
                    :bot-name {:source :interaction
                               :path [:bot-name]}
                    :connection {:source :bot-state
                                 :path [:bots :botnames :bot-name :jda]}
                    :member-id {:source :interaction
                                :path [:member :id]}}
   :steps
   [{:action :db-operation
     :inputs {:operation (value-expr :profile-create)
              :member-id (path-expr [:member-id])}
     :outputs {:success :db-success
               :message :db-message
               :error :db-error}}
    {:action :send-message
     :inputs {:connection (path-expr [:connection])
              :channel-id (path-expr [:channel-id])
              :bot-name (path-expr [:bot-name])
              :content (map-expr
                         {:success (path-expr [:action-outputs :db-success])
                          :message (path-expr [:action-outputs :db-message])
                          :error (path-expr [:action-outputs :db-error])})}
     :outputs {:message-id :response-message-id}}
    {:action :end-conversation
     :inputs {}
     :outputs {:success :conversation-ended}}]})

(def collect-responses
  {:name :collect-responses
   :description "Collects and acknowledges responses to a specific message"
   :context-schema {:channel-id {:source :interaction
                                 :path [:channel-id]}
                    :bot-name {:source :interaction
                               :path [:bot-name]}
                    :connection {:source :bot-state
                                 :path [:bots :botnames :bot-name :jda]}
                    :author-id {:source :interaction
                                :path [:member :id]}}
   :steps
   [;; Step 1: Send initial prompt message
    {:action :send-message
     :inputs {:connection (path-expr [:connection])
              :channel-id (path-expr [:channel-id])
              :bot-name (path-expr [:bot-name])
              :content "Please reply to this message! I will acknowledge each reply."}
     :outputs {:message-id :prompt-message-id}}

    ;; Step 2: Wait for replies - using queue-based wait state
    {:wait {:interaction-type :message-received
            :store-as :collected-replies
            :queue? true
            :input-filters {:channel-id (path-expr [:channel-id])
                            :message-data {:reply-to {:message-id (path-expr [:action-outputs :prompt-message-id])}}}}}

    ;; Step 3: Acknowledge each reply - modified to use simpler string building
    {:action :send-message
     :inputs {:connection (path-expr [:connection])
              :channel-id (path-expr [:channel-id])
              :bot-name (path-expr [:bot-name])
              :content (str-expr
                         {:message (path-expr [:waiting-state :collected-replies :interaction :message-data :content])
                          :separator " | ID: "
                          :id (path-expr [:waiting-state :collected-replies :interaction :message-data :id])})}
     :outputs {:message-id :acknowledgment-message-id}}]})

(def available-flows
  {:register register
   :collect-responses collect-responses})