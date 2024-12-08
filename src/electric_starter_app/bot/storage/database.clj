(ns electric-starter-app.bot.storage.database
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [clojure.tools.logging :as log]))

(def db-spec
  {:dbtype "sqlite"
   :dbname "discord_bot.db"})

(defonce datasource (atom nil))

(defn init-db!
  "Initialize database connection and create necessary tables"
  []
  (log/info "Initializing database connection")
  (try
    (let [ds (jdbc/get-datasource db-spec)]
      (reset! datasource ds)

      ;; Members table - stores Discord user IDs
      (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS members (
                         discord_id TEXT PRIMARY KEY)"])

      ;; Questions table - stores questions
      (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS questions (
                         question_id INTEGER PRIMARY KEY AUTOINCREMENT,
                         question_text TEXT NOT NULL)"])

      ;; QuestionResponses table - stores responses to questions
      (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS question_responses (
                         response_id INTEGER PRIMARY KEY AUTOINCREMENT,
                         question_id INTEGER NOT NULL,
                         discord_id TEXT NOT NULL,
                         response_text TEXT NOT NULL,
                         FOREIGN KEY (question_id) REFERENCES questions(question_id),
                         FOREIGN KEY (discord_id) REFERENCES members(discord_id))"])

      ;; Discussions table - stores discussion threads about responses
      (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS discussions (
                         message_id INTEGER PRIMARY KEY AUTOINCREMENT,
                         response_id INTEGER NOT NULL,
                         parent_id INTEGER NOT NULL DEFAULT 0,
                         discord_id TEXT NOT NULL,
                         content TEXT NOT NULL,
                         FOREIGN KEY (response_id) REFERENCES question_responses(response_id),
                         FOREIGN KEY (discord_id) REFERENCES members(discord_id))"])

      ;; Achievements table - stores user achievements
      (jdbc/execute! ds ["CREATE TABLE IF NOT EXISTS achievements (
                         discord_id TEXT NOT NULL,
                         achievement_name TEXT NOT NULL,
                         PRIMARY KEY (discord_id, achievement_name),
                         FOREIGN KEY (discord_id) REFERENCES members(discord_id))"])

      (log/info "Database initialized successfully")
      true)
    (catch Exception e
      (log/error e "Failed to initialize database")
      (throw (ex-info "Database initialization failed"
               {:error (.getMessage e)})))))

(defn profile-exists?
  "Check if a member profile exists"
  [member-id]
  (when @datasource
    (try
      (let [result (sql/query @datasource
                     ["SELECT discord_id FROM members WHERE discord_id = ?" member-id])]
        (boolean (seq result)))
      (catch Exception e
        (log/error e "Error checking profile existence" {:member-id member-id})
        (throw (ex-info "Failed to check profile existence"
                 {:member-id member-id
                  :error (.getMessage e)}))))))

(defn create-profile!
  "Create a new member profile"
  [member-id]
  (when @datasource
    (try
      (if (profile-exists? member-id)
        {:success false
         :error {:type :profile-exists
                 :message "Profile already exists"}}
        (do
          (sql/insert! @datasource :members {:discord_id member-id})
          {:success true}))
      (catch Exception e
        (log/error e "Error creating profile" {:member-id member-id})
        {:success false
         :error {:type :database-error
                 :message (.getMessage e)}}))))