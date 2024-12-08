(ns electric-starter-app.bot.impl.triggers
  (:require [electric-starter-app.bot.platform.discord.schema.definition.trigger :as schema.triggers]))

(def command-triggers
  [(schema.triggers/create-trigger
     {:name :register
      :command-name "register"
      :description "Register yourself as a member"
      :flow-id :register})

   (schema.triggers/create-trigger
     {:name :collect
      :command-name "collect"
      :description "Start collecting responses from users"
      :flow-id :collect-responses})])

(def trigger-registry
  (into {}
    (map (juxt :command-name identity)
      command-triggers)))