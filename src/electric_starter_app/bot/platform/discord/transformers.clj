(ns electric-starter-app.bot.platform.discord.transformers
  (:require [electric-starter-app.bot.core.schema.definition.expression :as expressions])
  (:import [net.dv8tion.jda.api.entities User Member Role Message Guild]
           [net.dv8tion.jda.api.entities.channel Channel]
           [net.dv8tion.jda.api.interactions.commands OptionType]))

(defn user->map
  [^User user]
  (when user
    {:id (expressions/value-expr (.getId user))
     :name (expressions/value-expr (.getName user))
     :discriminator (expressions/value-expr (.getDiscriminator user))
     :bot? (expressions/value-expr (.isBot user))
     :avatar-url (expressions/value-expr (.getEffectiveAvatarUrl user))
     :global-name (expressions/value-expr (.getGlobalName user))}))

(defn role->map
  [^Role role]
  (when role
    {:id (expressions/value-expr (.getId role))
     :name (expressions/value-expr (.getName role))
     :color (expressions/value-expr (str (.getColor role)))
     :position (expressions/value-expr (.getPosition role))
     :managed? (expressions/value-expr (.isManaged role))
     :hoisted? (expressions/value-expr (.isHoisted role))}))

(defn member->map
  [^Member member]
  (when member
    (merge
      (user->map (.getUser member))
      {:nickname (.getNickname member)
       :joined-at (.getTimeJoined member)
       :roles (map role->map (.getRoles member))})))

(defn channel->map
  [^Channel channel]
  (when channel
    {:id (expressions/value-expr (.getId channel))
     :name (expressions/value-expr (.getName channel))
     :type (expressions/value-expr (str (.getType channel)))}))

(defn message->map
  [^Message message]
  (when message
    {:id (expressions/value-expr (.getId message))
     :content (expressions/value-expr (.getContentRaw message))
     :author (user->map (.getAuthor message))
     :channel (channel->map (.getChannel message))
     :created-at (expressions/value-expr (.getTimeCreated message))
     :attachments (expressions/value-expr (map #(hash-map :id (.getId %)
                                                  :url (.getUrl %)
                                                  :filename (.getFileName %))
                                            (.getAttachments message)))
     :reply-to (when-let [referenced (.getReferencedMessage message)]
                 {:message-id (expressions/value-expr (.getId referenced))
                  :author (user->map (.getAuthor referenced))
                  :content (expressions/value-expr (.getContentRaw referenced))})}))

(defn guild->map
  [^Guild guild]
  (when guild
    {:id (.getId guild)
     :name (.getName guild)
     :owner-id (some-> guild .getOwner .getId)  ;; Use some-> to safely handle null
     :member-count (.getMemberCount guild)}))

(defn keyword->option-type
  "Converts a keyword type to a JDA OptionType enum value"
  [type-keyword]
  (case type-keyword
    :string  OptionType/STRING
    :integer OptionType/INTEGER
    :boolean OptionType/BOOLEAN
    :user    OptionType/USER
    :channel OptionType/CHANNEL
    :role    OptionType/ROLE
    (throw (ex-info "Unsupported option type"
             {:type type-keyword
              :supported-types #{:string :integer :boolean :user :channel :role}}))))