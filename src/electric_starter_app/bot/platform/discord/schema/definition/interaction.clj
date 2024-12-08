(ns electric-starter-app.bot.platform.discord.schema.definition.interaction
  (:require [electric-starter-app.bot.platform.discord.transformers :as dt])
  (:import [net.dv8tion.jda.api.events.guild GuildJoinEvent GuildLeaveEvent
            GuildReadyEvent GuildUnavailableEvent GuildAvailableEvent]
           [net.dv8tion.jda.api.events.channel ChannelCreateEvent
            ChannelDeleteEvent]
           [net.dv8tion.jda.api.events.message MessageReceivedEvent
            MessageUpdateEvent MessageDeleteEvent MessageBulkDeleteEvent]
           [net.dv8tion.jda.api.events.message.react MessageReactionAddEvent
            MessageReactionRemoveEvent]
           [net.dv8tion.jda.api.events.interaction.command SlashCommandInteractionEvent]))

(def interaction-schemas
  {;; Guild Events
   :guild-join
   {:event-class GuildJoinEvent
    :fields {:interaction-type {:value :guild-join :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :guild-data {:type :map :required true :transform #(dt/guild->map (.getGuild %))}}}

   :guild-leave
   {:event-class GuildLeaveEvent
    :fields {:interaction-type {:value :guild-leave :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}}}

   :guild-ready
   {:event-class GuildReadyEvent
    :fields {:interaction-type {:value :guild-ready :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :guild-data {:type :map :required true :transform #(dt/guild->map (.getGuild %))}}}

   :guild-unavailable
   {:event-class GuildUnavailableEvent
    :fields {:interaction-type {:value :guild-unavailable :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}}}

   :guild-available
   {:event-class GuildAvailableEvent
    :fields {:interaction-type {:value :guild-available :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :guild-data {:type :map :required true :transform #(dt/guild->map (.getGuild %))}}}

   ;; Channel Events
   :channel-create
   {:event-class ChannelCreateEvent
    :fields {:interaction-type {:value :channel-create :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :channel-id {:type :string :required true :transform #(.getId (.getChannel %))}
             :channel-data {:type :map :required true :transform #(dt/channel->map (.getChannel %))}}}

   :channel-delete
   {:event-class ChannelDeleteEvent
    :fields {:interaction-type {:value :channel-delete :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :channel-id {:type :string :required true :transform #(.getId (.getChannel %))}}}

   ;; Message Events
   :message-received
   {:event-class MessageReceivedEvent
    :fields {:interaction-type {:value :message-received :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :channel-id {:type :string :required true :transform #(.getId (.getChannel %))}
             :message-data {:type :map :required true :transform #(dt/message->map (.getMessage %))}}}

   :message-update
   {:event-class MessageUpdateEvent
    :fields {:interaction-type {:value :message-update :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :channel-id {:type :string :required true :transform #(.getId (.getChannel %))}
             :message-data {:type :map :required true :transform #(dt/message->map (.getMessage %))}}}

   :message-delete
   {:event-class MessageDeleteEvent
    :fields {:interaction-type {:value :message-delete :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :channel-id {:type :string :required true :transform #(.getId (.getChannel %))}
             :message-id {:type :string :required true :transform #(.getMessageId %)}}}

   :message-bulk-delete
   {:event-class MessageBulkDeleteEvent
    :fields {:interaction-type {:value :message-bulk-delete :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :channel-id {:type :string :required true :transform #(.getId (.getChannel %))}
             :message-ids {:type :vector :required true :transform #(vec (.getMessageIds %))}}}

;; Reaction Events
   :reaction-add
   {:event-class MessageReactionAddEvent
    :fields {:interaction-type {:value :reaction-add :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :channel-id {:type :string :required true :transform #(.getId (.getChannel %))}
             :message-id {:type :string :required true :transform #(.getMessageId %)}
             :user-id {:type :string :required true :transform #(.getUserId %)}
             :message-author-id {:type :string :required true :transform #(.getMessageAuthorId %)}
             :reaction {:type :map :required true
                        :transform #(hash-map :emoji-name (.getName (.getEmoji (.getReaction %)))
                                      :formatted (.getFormatted (.getEmoji (.getReaction %))))}}}

   :reaction-remove
   {:event-class MessageReactionRemoveEvent
    :fields {:interaction-type {:value :reaction-remove :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :channel-id {:type :string :required true :transform #(.getId (.getChannel %))}
             :message-id {:type :string :required true :transform #(.getMessageId %)}
             :user-id {:type :string :required true :transform #(.getUserId %)}
             :reaction {:type :map :required true
                        :transform #(hash-map :emoji-name (.getName (.getEmoji (.getReaction %)))
                                      :formatted (.getFormatted (.getEmoji (.getReaction %))))}}}

   ;; Slash Command Events
   :slash-command
   {:event-class SlashCommandInteractionEvent
    :fields {:interaction-type {:value :slash-command :required true}
             :bot-name {:type :string :required true}
             :guild-id {:type :string :required true :transform #(.getId (.getGuild %))}
             :channel-id {:type :string :required true :transform #(.getId (.getChannel %))}
             :command-name {:type :string :required true :transform #(.getName %)}
             :options {:type :map :required false
                       :transform (fn [event]
                                    (when-let [options (.getOptions event)]
                                      (reduce (fn [m opt] (assoc m (keyword (.getName opt)) (.getAsString opt))) {} options)))}
             :member {:type :map :required false :transform #(when-let [member (.getMember %)] (dt/member->map member))}
             :deferred? {:type :boolean :required true :transform #(.isAcknowledged %)}
             :interaction-id {:type :string :required true :transform #(.getId %)}}}})

(defn get-schema-for-event
  "Returns the schema definition for a given JDA event class.
   Returns nil if no schema is found."
  [event]
  (->> interaction-schemas
    (filter (fn [[_ schema]] (instance? (:event-class schema) event)))
    first
    second))