(ns electric-starter-app.bot.platform.discord.lifecycle.bot
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.util.state :as state]
            [electric-starter-app.bot.storage.database :as db] 
            [electric-starter-app.bot.core.engine.flow.state :as flow.state]
            [electric-starter-app.bot.platform.discord.adapter :as discord]
            [electric-starter-app.bot.impl.triggers :refer [command-triggers]])
  (:import [net.dv8tion.jda.api JDABuilder]
           [net.dv8tion.jda.api.entities Activity]
           [net.dv8tion.jda.api.requests GatewayIntent]
           [net.dv8tion.jda.api.interactions.commands.build Commands]
           [net.dv8tion.jda.api.hooks ListenerAdapter]
           [net.dv8tion.jda.api.events.guild GuildReadyEvent]
           [net.dv8tion.jda.api.events.session ReadyEvent]))

(defn register-commands-when-ready!
  "Registers commands after bot is fully ready"
  [jda]
  (let [guild-ready-listener
        (proxy [ListenerAdapter] []
          (onGuildReady [^GuildReadyEvent event]
            (try
              (let [guild (.getGuild event)]
                (log/info "Guild became ready - registering commands"
                  {:guild-name (.getName guild)
                   :guild-id (.getId guild)
                   :member-count (.getMemberCount guild)})

                (let [commands (.updateCommands guild)]
                  (doseq [command command-triggers]
                    (log/debug "Registering command for guild"
                      {:command (:command-name command)
                       :guild-name (.getName guild)})

                    (let [builder (Commands/slash
                                    (:command-name command)
                                    (:description command))]
                      (.addCommands commands (into-array [builder]))))

                  (.queue commands
                    (reify java.util.function.Consumer
                      (accept [_ success]
                        (log/info "Successfully registered commands for guild"
                          {:guild-name (.getName guild)
                           :command-count (count success)})))
                    (reify java.util.function.Consumer
                      (accept [_ error]
                        (log/error error "Failed to register commands"
                          {:guild-name (.getName guild)}))))))
              (catch Exception e
                (log/error e "Critical error in guild ready handler"
                  {:event-type (-> event .getClass .getSimpleName)})))))

        ready-listener
        (proxy [ListenerAdapter] []
          (onReady [^ReadyEvent _]
            (try
              (log/info "Bot fully ready"
                {:connected-guilds (count (.getGuilds jda))
                 :guild-names (mapv #(.getName %) (.getGuilds jda))})
              (catch Exception e
                (log/error e "Error in ready handler")))))]

    (.addEventListener jda (into-array [guild-ready-listener ready-listener]))
    (log/info "Registered event listeners for command handling"
      {:listeners ["GuildReadyListener" "ReadyListener"]})))

(defn start-bot
  ([bot-name]
   (log/debug "Attempting to start bot" bot-name "using config token")
   (if-let [token (get-in @state/state [:bots :botnames bot-name :token])]
     (do
       (log/debug "Token found for bot" bot-name)
       (start-bot token bot-name))
     (do
       (log/error "No token found for bot:" bot-name)
       (throw (ex-info "No token found" {:bot bot-name})))))
  ([token bot-name]
   (try
     (log/info "Starting bot" bot-name)
     (log/debug "Initializing bot with token starting with" (subs token 0 10) "...")
     (state/update-state! [:bots :botnames bot-name :status] "connecting")

     ;; Initialize database
     (log/info "Initializing database")
     (db/init-db!)
     (log/info "Database initialized successfully")

     ;; Initialize flows
     (flow.state/init!)
     (log/debug "Configuring JDA builder with intents and activity")
     (let [discord-adapter (discord/create-adapter bot-name)
           jda (-> (JDABuilder/createDefault token)
                 (.enableIntents [GatewayIntent/GUILD_MESSAGES
                                  GatewayIntent/MESSAGE_CONTENT
                                  GatewayIntent/GUILD_MEMBERS
                                  GatewayIntent/GUILD_MESSAGE_REACTIONS])
                 (.setActivity (Activity/playing "Electric Clojure"))
                 (.addEventListeners (into-array [discord-adapter]))
                 .build)]

       (log/debug "JDA builder configured successfully")

       ;; Register the ready-check listener instead of immediate registration
       (register-commands-when-ready! jda)

       (state/update-state! [:bots :botnames bot-name :jda] jda)
       (state/update-state! [:bots :botnames bot-name :status] "online")
       (log/info "Bot" bot-name "started successfully")
       jda)
     (catch Exception e
       (log/error e "Failed to start bot" bot-name)
       (state/update-state! [:bots :botnames bot-name :status] "error")
       (throw (ex-info "Bot startup failed"
                {:bot bot-name :error (.getMessage e)}
                e))))))

(defn stop-bot [bot-name]
  (log/info "Attempting to stop bot" bot-name)
  (if-let [jda (get-in @state/state [:bots :botnames bot-name :jda])]
    (try
      (log/debug "Found JDA instance for bot" bot-name)
      (state/update-state! [:bots :botnames bot-name :status] "disconnecting")
      (log/debug "Shutting down JDA instance")
      (.shutdown jda)
      (state/update-state! [:bots :botnames bot-name :jda] nil)
      (state/update-state! [:bots :botnames bot-name :status] "offline")
      (log/info "Bot" bot-name "stopped successfully")
      (catch Exception e
        (log/error e "Error while stopping bot" bot-name)
        (throw (ex-info "Bot shutdown failed"
                 {:bot bot-name :error (.getMessage e)}
                 e))))
    (do
      (log/warn "No running JDA instance found for bot" bot-name)
      (state/update-state! [:bots :botnames bot-name :status] "offline"))))