(ns electric-starter-app.bot.core.engine.flow.state
  (:require [clojure.tools.logging :as log]
            [electric-starter-app.bot.util.state :as state]
            [electric-starter-app.bot.impl.flows :refer [available-flows]]))

(defn init!
  "Initialize flows with schema validation."
  []
  (log/info "Initializing flows")
  (state/update-state! [:flows :registry] available-flows)
  (state/update-state! [:flows :contexts] {})
  (log/info "Flows initialized"))