(ns smh.bot
  (:require
   [clojure.tools.logging :as log]
   [smh.db :as db]
   [telegrambot-lib.core :as tbot] ;; [clojure-tools-logging :as log]
))

;; Code lifted and modified from
;; https://github.com/wdhowe/telegrambot-lib/wiki/Getting-and-Sending-Messages
(def config
  {:timeout 10}) ;the bot api timeout is in seconds

(defn poll-updates
  "Long poll for recent chat messages from Telegram."
  ([bot]
   (poll-updates bot nil))
  ([bot offset]
   (let [resp (tbot/get-updates bot {:offset offset
                                     :timeout (:timeout config)})]
     (if (contains? resp :error)
       (log/error "tbot/get-updates error:" (:error resp))
       resp))))

(defn send-text-message
  "Sends a plaintext message MESSAGE to USER-ID on telegram"
  [bot user-id message]
  (tbot/send-message bot user-id message))

(defn send-messages
  "Sends multiple messages via BOT to user with USER-ID with optional delay between them"
  [bot user-id messages & [delay]]
  (let [f (future
            (doseq [message messages]
              (send-text-message bot user-id message)
              (when delay
                (Thread/sleep delay))))]
    @f))

(defn send-picture
  [bot user-id picture & [caption]]
  (tbot/send-photo bot user-id picture {:caption (or caption "")}))

(defn handle-start-message
  "Handle the incoming '/start' message."
  [msg]
  (let [user-details (get-in msg [:message :from])
        {id :id
         first-name :first_name
         last-name :last_name
         username :username} user-details]
    (when-not (db/get-user id)
      (db/add-new-user! id first-name last-name username)
      (log/info (str "Successfully added new user with id " id)))))

(defn handle-subscribe-command
  "Subscribes the user"
  [id]
  (log/info "Subscribe command received from user with ID " id)
  (db/subscribe-user! id))

(defn handle-unsubscribe-command
  "Unsubscribes the user"
  [id]
  (log/info "Unsubscribe command received from user with ID " id)
  (db/revoke-subscription! id))
