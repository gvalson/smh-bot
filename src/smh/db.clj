(ns smh.db
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as log]
   [java-time.api :as jt]))

(def db
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname "db/database.db"})

(defn init-users-db
  "create db and table"
  []
  (try
    (jdbc/db-do-commands db
                         (jdbc/create-table-ddl :users
                                                [[:id :int :primary :key]
                                                 [:first_name :text]
                                                 [:last_name :text]
                                                 [:username :text]
                                                 [:subscribed :integer]
                                                 [:last_message_sent :text]]
                                                {:conditional? true})) ;; TODO: add indexing
    (catch Exception e
      (println (.getMessage e)))))

(defn init-listings-db
  "Create the listings table if it doesn't exist. This will be used to store
  already seen listings on all sites."
  []
  (try
    (jdbc/db-do-commands db
                         (jdbc/create-table-ddl :listings
                                                [[:id :int :primary :key]
                                                 [:price :real] ;; assumed to be USD
                                                 [:area :real]
                                                 [:post_date :text]
                                                 [:url :text]
                                                 [:picture_url :text]]
                                                {:conditional? true}))
    (catch Exception e
      (log/error e))))

(defn add-new-user!
  "Add new user to the database."
  [id first-name last-name username]
  (jdbc/insert! db :users {:id id
                           :first_name first-name
                           :last_name last-name
                           :username username
                           :subscribed 0
                           :last_message_sent nil}))

(defn revoke-subscription!
  "Revoke subscription for a user"
  [user-id]
  (jdbc/update! db :users {:subscribed 0} ["id = ?" user-id]))

(defn subscribe-user!
  "Subscribe the user"
  [user-id]
  (jdbc/update! db :users {:subscribed 1} ["id = ?" user-id]))

(defn get-user
  "Returns the user object if user with the id USER-ID has already been added to the database or nil otherwise."
  [user-id]
  (seq (jdbc/query db ["SELECT * FROM users WHERE id = ?" user-id])))

(defn get-subscribed-users
  "Returns all users that are currently subscribed to the notifications"
  []
  (jdbc/query db ["SELECT * FROM users WHERE subscribed = ?" 1]))

(defn get-listing
  "Get a listing by its ID"
  [id]
  (jdbc/query db ["SELECT * FROM listings WHERE id = ?" id]))

(defn add-listing!
  "Add a listing to the database"
  ([id price area post-date url picture-url]
   ;; (jdbc/insert! db :listings {:id id
   ;;                             :price price
   ;;                             :area area
   ;;                             :post_date post-date
   ;;                             :url url})
   (jdbc/execute! db
                  ["INSERT OR IGNORE INTO listings (id, price, area, post_date, url, picture_url) VALUES(?, ?, ?, ?, ?, ?)"
                   id
                   price
                   area
                   post-date
                   url
                   picture-url]))
  ([{id :id price :price area :area post-date :post-date url :url picture-url :picture-url}]
   (add-listing! id price area post-date url picture-url)))

(defn get-listings-later-than
  "Returns all listings that have been posted later than DATE"
  [date]
  (jdbc/query db ["SELECT * from listings WHERE post_date > ?" (jt/format :iso-date-time date)]))

(defn update-last-message-sent!
  ([id]
   (update-last-message-sent! id (jt/local-date-time)))
  ([id date]
   (jdbc/update! db :users {:last_message_sent (jt/format :iso-date-time date)} ["id = ?" id])))

(defn get-fresh-listings
  []
  (jdbc/query db
              ["SELECT * FROM users AS a INNER JOIN listings AS b ON a.subscribed = 1 AND b.post_date > a.last_message_sent"]))

;; For parsing dates just do:
;; (jt/local-date-time "2023-05-15T22:44:58.839")
