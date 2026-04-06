(ns octatrack.application.ui
  "JavaFX UI via cljfx"
  (:require [cljfx.api :as fx]
            [octatrack.application.api :as api]
            [octatrack.infrastructure.logger :as log]
            [clojure.string :as str])
  (:import [javafx.stage DirectoryChooser]
           [javafx.application Platform]
           [javafx.scene.image Image])
  (:gen-class))

;; ── State ──────────────────────────────────────────────────────────────────────

(def *state
  (atom {:source-path   ""
         :target-path   ""
         :bank-mapping  (into (sorted-map) (map #(vector % "") (range 1 17)))
         :copy-samples? true
         :log-lines     []
         :running?      false}))

;; ── Helpers ────────────────────────────────────────────────────────────────────

(defn- bank-pairs-from-mapping [bank-mapping]
  (->> bank-mapping
       (filter (fn [[_ v]] (not (str/blank? v))))
       (map (fn [[t s]] (str t "=" s)))
       vec))

(defn- append-log! [msg]
  (Platform/runLater #(swap! *state update :log-lines conj msg)))

;; ── Event Handlers ─────────────────────────────────────────────────────────────

(defmulti handle-event :event/type)

(defmethod handle-event :default [_] nil)

(defmethod handle-event :source-changed [{:keys [fx/event]}]
  (swap! *state (fn [s]
                  (cond-> (assoc s :source-path event)
                    (str/blank? (:target-path s))
                    (assoc :target-path (str event "_new"))))))

(defmethod handle-event :target-changed [{:keys [fx/event]}]
  (swap! *state assoc :target-path event))

(defmethod handle-event :bank-changed [{:keys [bank-number fx/event]}]
  (swap! *state assoc-in [:bank-mapping bank-number] event))

(defmethod handle-event :browse-source [{:keys [fx/event]}]
  (let [window  (.. event getSource getScene getWindow)
        chooser (doto (DirectoryChooser.) (.setTitle "Select Source Project Folder"))
        dir     (.showDialog chooser window)]
    (when dir
      (let [path (.getAbsolutePath dir)]
        (swap! *state (fn [s]
                        (cond-> (assoc s :source-path path)
                          (str/blank? (:target-path s))
                          (assoc :target-path (str path "_new")))))))))

(defmethod handle-event :browse-target [{:keys [fx/event]}]
  (let [window  (.. event getSource getScene getWindow)
        chooser (doto (DirectoryChooser.) (.setTitle "Select Target Project Folder"))
        dir     (.showDialog chooser window)]
    (when dir
      (swap! *state assoc :target-path (.getAbsolutePath dir)))))

(defmethod handle-event :copy-banks [_]
  (let [{:keys [source-path target-path bank-mapping copy-samples?]} @*state
        pairs (bank-pairs-from-mapping bank-mapping)]
    (if (str/blank? source-path)
      (swap! *state update :log-lines conj "==> (!) Error: Source folder is required")
      (do
        (swap! *state assoc :running? true :log-lines [])
        (future
          (try
            (log/set-ui-log-fn! append-log!)
            (api/copy-project-command source-path target-path pairs :copy-samples? copy-samples?)
            (catch Exception e
              (append-log! (str "==> (!) Error: " (ex-message e))))
            (finally
              (log/set-ui-log-fn! nil)
              (Platform/runLater #(swap! *state assoc :running? false)))))))))

(defmethod handle-event :toggle-copy-samples [{:keys [fx/event]}]
  (swap! *state assoc :copy-samples? event))

(defmethod handle-event :clear [_]
  (swap! *state assoc
         :source-path   ""
         :target-path   ""
         :bank-mapping  (into (sorted-map) (map #(vector % "") (range 1 17)))
         :copy-samples? true
         :log-lines     []
         :running?      false))

;; ── Design Tokens ──────────────────────────────────────────────────────────────

(def ^:private BG      "#141414")
(def ^:private SURFACE "#1e1e1e")
(def ^:private CARD    "#252525")
(def ^:private BORDER  "#3a3a3a")
(def ^:private ACCENT  "#e85d04")
(def ^:private TEXT    "#e8e8e8")
(def ^:private MUTED   "#777777")
(def ^:private MONO    "\"Courier New\", Courier, monospace")

;; ── Components ─────────────────────────────────────────────────────────────────

(defn- bank-cell [{:keys [bank-number value]}]
  {:fx/type    :v-box
   :alignment  :center
   :spacing    4
   :pref-width  68
   :pref-height 68
   :style (str "-fx-background-color:" CARD ";"
               "-fx-background-radius:4;"
               "-fx-border-color:" BORDER ";"
               "-fx-border-radius:4;"
               "-fx-border-width:1;"
               "-fx-padding:6;")
   :children
   [{:fx/type :label
     :text    (format "%02d" bank-number)
     :style   (str "-fx-text-fill:" ACCENT ";"
                   "-fx-font-size:11;"
                   "-fx-font-weight:bold;"
                   "-fx-font-family:" MONO ";")}
    {:fx/type          :text-field
     :text             value
     :pref-width       48
     :max-width        48
     :prompt-text      "—"
     :style            (str "-fx-background-color:" SURFACE ";"
                            "-fx-text-fill:" TEXT ";"
                            "-fx-prompt-text-fill:" MUTED ";"
                            "-fx-font-family:" MONO ";"
                            "-fx-font-size:14;"
                            "-fx-alignment:center;"
                            "-fx-border-color:" BORDER ";"
                            "-fx-border-radius:2;"
                            "-fx-border-width:1;")
     :on-text-changed  {:event/type :bank-changed :bank-number bank-number}}]})

(defn- bank-grid [{:keys [bank-mapping]}]
  {:fx/type :grid-pane
   :hgap    6
   :vgap    6
   :children (for [i (range 16)
                   :let [n (inc i)]]
               (assoc (bank-cell {:bank-number n :value (get bank-mapping n "")})
                      :grid-pane/column (mod i 4)
                      :grid-pane/row    (quot i 4)))})

(defn- folder-row [{:keys [label value on-change on-browse]}]
  {:fx/type   :h-box
   :alignment :center-left
   :spacing   8
   :children
   [{:fx/type    :label
     :pref-width 64
     :text       label
     :style      (str "-fx-text-fill:" MUTED ";"
                      "-fx-font-family:" MONO ";"
                      "-fx-font-size:11;")}
    {:fx/type         :text-field
     :h-box/hgrow     :always
     :text            value
     :prompt-text     "Select a folder…"
     :style           (str "-fx-background-color:" SURFACE ";"
                           "-fx-text-fill:" TEXT ";"
                           "-fx-prompt-text-fill:" MUTED ";"
                           "-fx-font-family:" MONO ";"
                           "-fx-font-size:12;"
                           "-fx-border-color:" BORDER ";"
                           "-fx-border-radius:2;"
                           "-fx-border-width:1;"
                           "-fx-padding:4 6 4 6;")
     :on-text-changed on-change}
    {:fx/type   :button
     :text      "BROWSE"
     :style     (str "-fx-background-color:transparent;"
                     "-fx-text-fill:" ACCENT ";"
                     "-fx-border-color:" ACCENT ";"
                     "-fx-border-width:1;"
                     "-fx-border-radius:2;"
                     "-fx-font-family:" MONO ";"
                     "-fx-font-size:11;"
                     "-fx-cursor:hand;"
                     "-fx-padding:5 10 5 10;")
     :on-action on-browse}]})

(defn root [{:keys [source-path target-path bank-mapping copy-samples? log-lines running?]}]
  {:fx/type  :stage
   :showing  true
   :title    "BankDiddler"
   :icons    [(Image. (str (clojure.java.io/resource "icon.png")))]
   :min-width  400
   :min-height 560
   :scene
   {:fx/type :scene
    :root
    {:fx/type  :v-box
     :spacing  12
     :padding  {:top 20 :bottom 20 :left 20 :right 20}
     :pref-width  420
     :pref-height 720
     :style    (str "-fx-background-color:" BG ";")
     :children
     [;; ── Header
      {:fx/type :label
       :text    "BANKDIDDLER"
       :style   (str "-fx-text-fill:" ACCENT ";"
                     "-fx-font-size:17;"
                     "-fx-font-weight:bold;"
                     "-fx-font-family:" MONO ";")}
      {:fx/type :label
       :text    "octatrack bank routing tool"
       :style   (str "-fx-text-fill:" MUTED ";"
                     "-fx-font-size:11;"
                     "-fx-font-family:" MONO ";")}
      {:fx/type :separator
       :style   (str "-fx-background-color:" BORDER ";")}

      ;; ── Folder inputs
      (folder-row {:label     "SOURCE"
                   :value     source-path
                   :on-change {:event/type :source-changed}
                   :on-browse {:event/type :browse-source}})
      (folder-row {:label     "TARGET"
                   :value     target-path
                   :on-change {:event/type :target-changed}
                   :on-browse {:event/type :browse-target}})

      ;; ── Bank grid
      {:fx/type :label
       :text    "BANK MAPPING  ·  TARGET = SOURCE  ·  0 = EMPTY"
       :style   (str "-fx-text-fill:" MUTED ";"
                     "-fx-font-size:10;"
                     "-fx-font-family:" MONO ";")}
      (bank-grid {:bank-mapping bank-mapping})

      ;; ── Copy samples toggle
      {:fx/type            :check-box
       :text               "Copy samples from project folder"
       :selected           copy-samples?
       :style              (str "-fx-text-fill:" TEXT ";"
                                "-fx-font-family:" MONO ";"
                                "-fx-font-size:11;")
       :on-selected-changed {:event/type :toggle-copy-samples}}

      ;; ── Action buttons
      {:fx/type   :h-box
       :spacing   8
       :alignment :center-right
       :children
       [{:fx/type   :button
         :text      "CLEAR"
         :disable   running?
         :style     (str "-fx-background-color:transparent;"
                         "-fx-text-fill:" MUTED ";"
                         "-fx-border-color:" BORDER ";"
                         "-fx-border-width:1;"
                         "-fx-border-radius:2;"
                         "-fx-font-family:" MONO ";"
                         "-fx-font-size:11;"
                         "-fx-cursor:hand;"
                         "-fx-padding:6 12 6 12;")
         :on-action {:event/type :clear}}
        {:fx/type   :button
         :text      (if running? "COPYING…" "COPY BANKS")
         :disable   running?
         :style     (str "-fx-background-color:" ACCENT ";"
                         "-fx-text-fill:#ffffff;"
                         "-fx-font-weight:bold;"
                         "-fx-font-family:" MONO ";"
                         "-fx-font-size:12;"
                         "-fx-border-radius:2;"
                         "-fx-background-radius:2;"
                         "-fx-cursor:hand;"
                         "-fx-padding:6 16 6 16;")
         :on-action {:event/type :copy-banks}}]}

      ;; ── Log output
      {:fx/type    :text-area
       :v-box/vgrow :always
       :pref-height 160
       :editable   false
       :wrap-text  true
       :text       (str/join "\n" log-lines)
       :style      (str "-fx-control-inner-background:" SURFACE ";"
                        "-fx-text-fill:" TEXT ";"
                        "-fx-font-family:" MONO ";"
                        "-fx-font-size:11;"
                        "-fx-border-color:" BORDER ";"
                        "-fx-border-width:1;")}]}}})

;; ── Entry Point ────────────────────────────────────────────────────────────────

(def ^:private renderer
  (fx/create-renderer
   :middleware (fx/wrap-map-desc root)
   :opts {:fx.opt/map-event-handler handle-event}))

(defn -main [& _]
  (Platform/setImplicitExit true)
  (fx/mount-renderer *state renderer))
