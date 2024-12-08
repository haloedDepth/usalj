(ns electric-starter-app.bot.platform.discord.tracking.guild
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.platform.discord.schema.definition.interaction :as schema.interactions]
            [electric-starter-app.bot.util.state :as state]))

(def guild-events
  (->> schema.interactions/interaction-schemas
    keys
    (filter #(#{:guild-join :guild-leave :guild-ready :guild-unavailable
                :guild-available :channel-create :channel-delete} %))
    set))

(defmulti handle-guild-event
  "Multimethod to handle different types of guild events"
  :interaction-type)

(defmethod handle-guild-event :guild-join
  [{:keys [bot-name guild-id guild-data]}]
  (state/update-state! [:bots :botnames bot-name :guilds guild-id] guild-data))

(defmethod handle-guild-event :guild-leave
  [{:keys [bot-name guild-id]}]
  (state/update-state! [:bots :botnames bot-name :guilds guild-id] nil))

(defmethod handle-guild-event :guild-ready
  [{:keys [bot-name guild-id guild-data]}]
  (state/update-state! [:bots :botnames bot-name :guilds guild-id] guild-data))

(defmethod handle-guild-event :guild-unavailable
  [{:keys [bot-name guild-id]}]
  (state/update-state! [:bots :botnames bot-name :guilds guild-id :available?] false))

(defmethod handle-guild-event :guild-available
  [{:keys [bot-name guild-id guild-data]}]
  (state/update-state! [:bots :botnames bot-name :guilds guild-id] guild-data))

(defmethod handle-guild-event :channel-create
  [{:keys [bot-name guild-id channel-id channel-data]}]
  (state/update-state! [:bots :botnames bot-name :guilds guild-id :channels channel-id] channel-data))

(defmethod handle-guild-event :channel-delete
  [{:keys [bot-name guild-id channel-id]}]
  (state/update-state! [:bots :botnames bot-name :guilds guild-id :channels channel-id] nil))

(defmethod handle-guild-event :default [_] nil)

(defn guild-update
  "Update guild and channel state based on interaction type.
   Only processes events defined in the schema as guild events."
  [interaction]
  (when (guild-events (:interaction-type interaction))
    (log/debug "Updating guild state for bot" (:bot-name interaction) "interaction:" interaction)
    (handle-guild-event interaction)))