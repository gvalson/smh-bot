(ns smh.crawler.myhome
  (:require
   [clojure.tools.logging :as log]
   [clj-http.client :as client]
   [clojure.string :as str]
   [hickory.core :as hcore]
   [hickory.select :as s]
   [smh.db :as db]
   [java-time.api :as jt]))

;; Potentially get this from the API
;; POST https://www.myhome.ge/ka/search/getSubLocs
;; form param
;; osm_id=1996871
;; Use "childs" to extract sub-ubnebi
(def tbilisi-regions
  {:gldani "687578743"
   :didube "687611312"
   :vake "687586034"
   :isani "688350922"
   :krtsanisi "689701920"
   :mtatsminda "689678147"
   :nadzaladevi "688137211"
   :saburtalo "687602533"
   :samgori "688330506"
   :chughureti "687618311"
   :shemogareni "-1"})

(def default-tbilisi-regions
  (str/join "." [(:didube tbilisi-regions)
                 (:vake tbilisi-regions)
                 (:saburtalo tbilisi-regions)
                 (:chughureti tbilisi-regions)]))

(def default-query-params
  {"Keyword" "თბილისი"
   "AdTypeID" 1
   "PrTypeID" 1
   "regions" default-tbilisi-regions
   "fullregions" default-tbilisi-regions
   ;; "districts" -- apparently don't need to specify if you want ALL.
   "cities" "1996871"
   "GID" "1996871"
   "FCurrencyID" 1
   "FPriceTo" 120000})

(defn get-search-page
  []
  ;; TODO add caching!
  (-> (client/get "https://myhome.ge/ka/s/iyideba-bina-Tbilisi"
                  {:query-params default-query-params})
      :body
      hcore/parse
      hcore/as-hickory))

(defn parse-myhome-date
  "Parses a date string such as '7 მაისი 20:32' and returns a useable jt object."
  [date-string]
  ;; (jt/local-date-time (DateTimeFormatter/ofPattern "d M hh:mm" (Locale. "ka")) date-string) does not work in java 8 ig
  (let [monthless-vector (-> date-string
                             (str/replace "იანვარი" "1")
                             (str/replace "თებერვალი" "2")
                             (str/replace "მარტი" "3")
                             (str/replace "აპრილი" "4")
                             (str/replace "მაისი" "5")
                             (str/replace "ივნისი" "6")
                             (str/replace "ივლისი" "7")
                             (str/replace "აგვისტო" "8")
                             (str/replace "სექტემბერი" "9")
                             (str/replace "ოქტომბერი" "10")
                             (str/replace "ნოემბერი" "11")
                             (str/replace "დეკემბერი" "12")
                             (str/split #" "))
        date-string (str/join " " [(get monthless-vector 0)
                                   (get monthless-vector 1)
                                   (str (jt/as (jt/local-date) :year))
                                   (get monthless-vector 2)])]
    (jt/local-date-time "d M yyyy HH:mm" date-string)))

(comment (parse-myhome-date "15 მაისი 13:59"))

(defn extract-all-page-listings
  [search-page-hmap]
  (->> (s/select (s/and (s/class "statement-card") (s/attr "data-product-id")) search-page-hmap)
       (map (fn [listing]
              (let [id (get-in listing [:attrs :data-product-id])
                    price (->
                           (s/select (s/class "item-price-usd") listing)
                           first
                           :content
                           first)
                    area (->
                          (s/select (s/class "item-size") listing)
                          first
                          :content
                          first
                          str/trim)
                    post-date (->
                               (s/select (s/class "statement-date") listing)
                               first
                               :content
                               first
                               parse-myhome-date)
                    url (->
                         (s/select (s/tag :a) listing)
                         first
                         :attrs
                         :href)
                    picture-url (get-in listing [:attrs :data-thumb])]
                {:id id
                 :price price
                 :area area
                 :post-date post-date
                 :url url
                 :picture-url picture-url})))))

(defn balls-to-the-walls []
  (log/info "Extracting new information from myhome.ge...")
  (let [listings (extract-all-page-listings (get-search-page))]
    (doseq [listing listings]
      (db/add-listing! listing))
    (log/info "Added new listings.")))
