(ns electric-starter-app.bot.platform.discord.adapter
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.core.engine.interaction.processing :as interactions])
  (:import [net.dv8tion.jda.api.hooks ListenerAdapter]
           [net.dv8tion.jda.api.events.interaction.command SlashCommandInteractionEvent]))

(defn create-adapter [bot-name]
  (proxy [ListenerAdapter] []
    ;; Handle all non-slash command events
    (onGenericEvent [event]
      (when-not (instance? SlashCommandInteractionEvent event)
        (try
          (when-let [transformed (interactions/transform-event event bot-name)]
            (interactions/process-interaction transformed))
          (catch Exception e
            (log/error e "Error processing interaction"
              {:event-type (-> event .getClass .getSimpleName)
               :error (.getMessage e)})))))

    ;; Enhanced slash command handling
    (onSlashCommandInteraction [^SlashCommandInteractionEvent event]
      (try
        (log/info "Received slash command interaction"
          {:command (.getName event)
           :user (-> event .getUser .getName)
           :guild (-> event .getGuild .getName)
           :channel (-> event .getChannel .getName)
           :options (when (.getOptions event)
                      (into {} (map #(vector (.getName %) (.getAsString %))
                                 (.getOptions event))))})

        ;; Acknowledge the command immediately
        (-> event
          (.reply "Command processed successfully")
          (.setEphemeral true)
          (.queue
            (reify java.util.function.Consumer
              (accept [this success]
                (log/debug "Successfully acknowledged slash command"
                  {:command (.getName event)})))
            (reify java.util.function.Consumer
              (accept [this error]
                (log/error error "Failed to acknowledge slash command"
                  {:command (.getName event)})))))

        ;; Process the command through the flow system
        (when-let [transformed (interactions/transform-event event bot-name)]
          (log/debug "Successfully transformed slash command event"
            {:command (.getName event)
             :interaction-type (:interaction-type transformed)
             :guild-id (:guild-id transformed)})

          (interactions/process-interaction transformed))

        (catch Exception e
          (log/error e "Error processing slash command"
            {:command (.getName event)
             :error-type (-> e .getClass .getSimpleName)
             :error-message (.getMessage e)
             :stack-trace (with-out-str
                            (clojure.stacktrace/print-stack-trace e))}))))))