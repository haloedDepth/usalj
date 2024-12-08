(ns electric-starter-app.bot-lifecycle
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.state :as state]
            [electric-starter-app.discord.adapter :as discord]
            [electric-starter-app.discord.flow-utils :as flows]
            [electric-starter-app.discord.database.core :as db]
            [electric-starter-app.discord.impl.triggers :refer [command-triggers]]
            [electric-starter-app.discord.schema.triggers :as schema.triggers])
  (:import [net.dv8tion.jda.api JDABuilder]
           [net.dv8tion.jda.api.entities Activity]
           [net.dv8tion.jda.api.requests GatewayIntent]
           [net.dv8tion.jda.api.interactions.commands.build Commands]
           [net.dv8tion.jda.api.interactions.commands OptionType]
           [net.dv8tion.jda.api.hooks ListenerAdapter]
           [net.dv8tion.jda.api.events.guild GuildReadyEvent]
           [net.dv8tion.jda.api.events.session ReadyEvent]))



;; NEW: Add function to register slash commands
