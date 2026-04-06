(ns octatrack.infrastructure.project-folders
  "Infrastructure layer for changes to project folders"
  (:require [octatrack.infrastructure.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def other-project-file-prefixes ["arr" "markers" "project"])

(defn- is-other-project-filename? [filename]
  (some #(str/starts-with? filename %) other-project-file-prefixes))

(defn- copy-other-project-file [source-file target-folder-path]
  (let [filename (.getName source-file)]
    (when (is-other-project-filename? filename)
      (io/copy source-file (io/file target-folder-path filename))
      (io/copy source-file (io/file target-folder-path filename)))))

(defn copy-other-project-files
  "Copy all other project files to target project, keeping the same name"
  [source-folder-path target-folder-path]
  (doall (->> (utils/get-directory-contents-tree source-folder-path)
              (map #(copy-other-project-file % target-folder-path)))))

(defn copy-samples
  "Copy all subdirectories (sample folders) from source project to target project"
  [source-folder-path target-folder-path]
  (let [source-dir (io/file source-folder-path)]
    (doseq [item (.listFiles source-dir)
            :when (.isDirectory item)]
      (let [source-path (.toPath source-dir)]
        (doseq [f (file-seq item)
                :when (.isFile f)]
          (let [target-file (io/file target-folder-path (str (.relativize source-path (.toPath f))))]
            (.mkdirs (.getParentFile target-file))
            (io/copy f target-file)))))))

(defn validate-project-folder [project-folder-path]
  (let [folder (io/file project-folder-path)]
    (when-not (.exists folder)
      (throw (Exception. (str "Project folder doesn't exist: '" (.getAbsolutePath folder) "'"))))
    (when-not (> (count (.list folder)) 0)
      (throw (Exception. (str "Project folder is empty '" (.getAbsolutePath folder) "'"))))
    project-folder-path))