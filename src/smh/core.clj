;; Next steps:
;;
;; 5. Find a way to enqueue new posts to not spam the user.
;;
;; 6. Solve the cache TODOs

(ns smh.core
  (:gen-class)
  (:require
   [clojure.core.async :as a]
   [clojure.string :as s]
   [clojure.tools.logging :as log]
   [smh.bot :as bot]
   [smh.crawler.myhome :as myhome]
   [smh.db :as db]
   [telegrambot-lib.core :as tbot]))

;; This will use the BOT_TOKEN envvar
(def mybot (tbot/create))

(def config
  {:chat-ping-interval 3000
   :site-update-interval 60000})

(def welcome-message "გაგიმარჯოს ძმ(დ)აო, რავა ხარ? ბინა გინდა? 🏠🙌")
(def please-subscribe-message "↙ აგერ ნახე ჩემი ბრძანებები ან პირდაპირ ️მომწერე \"/subscribe\" რომ გამოიწერო ავტომატური ბინების პოსტებიიი 🤪")
(def subscribed-message "აბა ჰერი ჰერი წავედით კარიკარ 🚪😋")
(def post-subscribe-message "როცა ახალი პოსტები დაიდება, მესიჯები ავტომატურად გამოიგზავნება 📰")
(def unsubscribed-message "აუუუ რატოოოო 🫤🥺")
(def post-unsubscribe-message "ავტომატური მესიჯები გაუქმებულია.")
(def non-command-reply-message "აუუუ 😣 ვერ გავიგეეე 😩 მენიუ 👈 გამოიყენე ან თავი გამანებე რააააა")
(def apartment-found-messages
  ["აუუუუ რა მაგარი რამე ვიპოვეეე 🤩"
   "მომე პეპსი ლაითი, აგერ ჩემი ბაითი ♠"
   "ეს მგონი დაგევასება 😏"
   "აიიი ახლა დაიდო ცხელ-ცხელიიიი 🔥"
   "სენსორებმა დაიჭირეს 🔬"])

(defonce update-id (atom nil))

(defn set-id!
  "Sets the update id to process next as the the passed in `id`."
  [id]
  (reset! update-id id))

(defn process-chat-messages
  [bot]
  ;; Handle incoming messages
  (log/info "checking for chat updates...")
  (let [updates (bot/poll-updates bot @update-id)
        messages (:result updates)]

    ;; Check all messages, if any, for commands/keywords.
    (doseq [msg messages]
      ;; maybe consider checking for :entities :type "bot_command"
      (let [message-text (get-in msg [:message :text])
            user-id (get-in msg [:message :from :id])]
        (cond (= message-text "/start")
              (do
                (bot/handle-start-message msg)
                (bot/send-messages mybot user-id [welcome-message please-subscribe-message]))
              (= message-text "/subscribe")
              (do
                (bot/handle-subscribe-command user-id)
                (db/update-last-message-sent! user-id) ;; idk if we actually need this here...
                (bot/send-messages mybot user-id [subscribed-message post-subscribe-message]))
              (= message-text "/unsubscribe")
              (do
                (bot/handle-unsubscribe-command user-id)
                (bot/send-messages mybot user-id [unsubscribed-message post-unsubscribe-message]))
              :else
              (bot/send-text-message mybot user-id non-command-reply-message)))
      ;; Increment the next update-id to process.
      (-> msg
          :update_id
          inc
          set-id!))))

(defn say-goodbye
  []
  (let [users (db/get-subscribed-users)]
    (doseq [user users]
      (bot/send-text-message mybot (:id user)
                             "ძმ(დ)აო! ეს ბოტი დავიწყებას მიეცი! აღარ უნდა ავტორს მეტი! გვბლოკავენ და!.. თან სახლიც იყიდა უკვე."))))

(comment
  (say-goodbye))

(defn send-fresh-listings
  []
  (log/info "Processing fresh listings...")
  (let [fresh-listings (db/get-fresh-listings)]
    (doseq [{user-id :id apartment-link :url picture :picture_url} fresh-listings]
      (let [message-text (str (rand-nth apartment-found-messages) " " apartment-link)]
        (if-not (s/blank? picture)
                (bot/send-picture mybot user-id picture message-text)
                (bot/send-text-message mybot user-id message-text)))
      (db/update-last-message-sent! user-id))))

;; TODO define macro to abstract go-loop

(defn app
  "Retrieve and process chat messages."
  [bot]
  (log/info "bot service started.")

  ;; Three separate services we want to have:
  ;; 1. Monitor chat messages
  ;; 2. Update listings db
  ;; 3. Notify user about new apartments
  ;;
  ;; Approx loop times:
  ;; 1. Every 3 sec.
  ;; 2. Every 2-3 min.
  ;; 3. Every 2-3 min. or whenever (2) pulls something new.

  (let [chat-loop (a/go-loop []
                    (a/<! (a/timeout (:chat-ping-interval config)))
                    (process-chat-messages bot)
                    (recur))
        site-update-loop (a/go-loop []
                           (a/<! (a/timeout (:site-update-interval config)))
                           (myhome/balls-to-the-walls)
                           (send-fresh-listings)
                           (recur))]
    (a/<!! chat-loop)
    (a/thread (a/<!! site-update-loop))))

(defn -main
  [& args]
  (db/init-users-db)
  (app mybot))
