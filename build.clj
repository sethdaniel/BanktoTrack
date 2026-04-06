(ns build
  (:require [clojure.tools.build.api :as b]))

(defn get-sem-version [] (-> "deps.edn"
                             slurp
                             read-string
                             :version))

(def uber-main    'octatrack.application.console)
(def uber-ui-main 'octatrack.application.ui)
(def lib 'bankdiddler)
(def version (format "%s.%s" (get-sem-version) (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def uber-file (format "target/uberjar/%s.jar" (name lib)))

(defn clean [_] (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))


(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main uber-main})
  ;; output version
  (println version))

(def uber-ui-file (format "target/uberjar/%s-ui.jar" (name lib)))

(defn uber-ui [_]
  (let [ui-basis (b/create-basis {:project "deps.edn" :aliases [:ui]})]
    (clean nil)
    (b/copy-dir {:src-dirs ["src" "resources"]
                 :target-dir class-dir})
    (b/compile-clj {:basis     ui-basis
                    :src-dirs  ["src"]
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-ui-file
             :basis     ui-basis
             :main      uber-ui-main})
    (println version)))

(defn package [_]
  ;; Converts icon.png -> icon.icns via iconset (macOS only), then builds .dmg
  (uber-ui nil)
  (b/process {:command-args ["mkdir" "-p" "target/BankDiddler.iconset"]})
  (doseq [[size name] [[16 "icon_16x16"] [32 "icon_16x16@2x"]
                       [32 "icon_32x32"] [64 "icon_32x32@2x"]
                       [128 "icon_128x128"] [256 "icon_128x128@2x"]
                       [256 "icon_256x256"] [512 "icon_256x256@2x"]
                       [512 "icon_512x512"] [1024 "icon_512x512@2x"]]]
    (b/process {:command-args ["sips" "-z" (str size) (str size)
                               "resources/icon.png"
                               "--out" (str "target/BankDiddler.iconset/" name ".png")]}))
  (b/process {:command-args ["iconutil" "-c" "icns"
                             "target/BankDiddler.iconset"
                             "--output" "target/icon.icns"]})
  (b/process {:command-args ["jpackage"
                             "--input"       "target/uberjar"
                             "--main-jar"    "bankdiddler-ui.jar"
                             "--name"        "BankDiddler"
                             "--app-version" (get-sem-version)
                             "--icon"        "target/icon.icns"
                             "--type"        "dmg"
                             "--dest"        "target/dist"]})
  (println "Built: target/dist/BankDiddler.dmg"))