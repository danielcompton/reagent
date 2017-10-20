(ns sitetools.server
  "Used to pre-render HTML files."
  (:require [clojure.string :as string]
            [goog.events :as evt]
            [reagent.core :as r]
            [reagent.dom.server :as server]
            [reagent.debug :refer-macros [dbg log dev?]]
            [reagent.interop :as i :refer-macros [$ $!]]
            [sitetools.core :as tools]))


;;; Static site generation

(defn base [page]
  (let [depth (->> page tools/to-relative (re-seq #"/") count)]
    (->> "../" (repeat depth) (apply str))))

(defn danger [t s]
  [t {:dangerouslySetInnerHTML {:__html s}}])

(defn html-template [{:keys [title body-html timestamp page-conf
                             js-file css-file main-div]}]
  (let [main (str js-file timestamp)]
    (server/render-to-static-markup
     [:html
      [:head
       [:meta {:charset 'utf-8}]
       [:meta {:name 'viewport
               :content "width=device-width, initial-scale=1.0"}]
       [:base {:href (-> page-conf :page-path base)}]
       [:link {:href (str css-file timestamp) :rel 'stylesheet}]
       [:title title]]
      [:body
       [:div {:id main-div} (danger :div body-html)]
       (danger :script (str "var pageConfig = "
                            (-> page-conf clj->js js/JSON.stringify)))
       [:script {:src main :type "text/javascript"}]]])))

(defn gen-page [page-path conf]
  (tools/emit [:set-page page-path])
  (let [conf (merge conf @tools/config)
        b (:body conf)
        bhtml (server/render-to-string b)]
    (str "<!doctype html>\n"
         (html-template (assoc conf
                               :page-conf {:page-path page-path}
                               :body-html bhtml)))))

(defn fs [] (js/require "fs"))
(defn path [] (js/require "path"))

(defn mkdirs [f]
  (doseq [d (reductions #(str %1 "/" %2)
                        (-> ($ (path) normalize f)
                            (string/split #"/")))]
    (when-not ($ (fs) existsSync d)
      ($ (fs) mkdirSync d))))

(defn write-file [f content]
  (log "Write" f)
  (mkdirs ($ (path) dirname f))
  ($ (fs) writeFileSync f content))

(defn path-join [& paths]
  (apply ($ (path) :join) paths))

(defn write-resources [dir {:keys [css-file css-infiles]}]
  (write-file (path-join dir css-file)
              (->> css-infiles
                   (map #($ (fs) readFileSync %))
                   (string/join "\n"))))
