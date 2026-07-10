(ns stratus.reader
  (:require [clojure.edn :as edn]
            [clojure.string :as str])
  (:import [java.io PushbackReader StringReader]))

;; ─── Parsing ─────────────────────────────────────────────────────────

(defn- remove-line-comments
  "Remove ; line comments from source before EDN parsing."
  [source]
  (str/replace source #";[^\n]*" ""))

(defn- read-all-forms
  "Read all top-level S-expressions from source string.
   Returns a vector of forms."
  [source]
  (let [clean   (remove-line-comments source)
        reader  (PushbackReader. (StringReader. clean))
        forms   (atom [])]
    (try
      (loop []
        (let [form (edn/read {:eof ::eof} reader)]
          (when-not (= ::eof form)
            (swap! forms conj form)
            (recur))))
      (catch Exception e
        (throw (ex-info (str "Parse error: " (.getMessage e))
                        {:source source, :error e}))))
    @forms))

(defn parse
  "Parse a .stratus source string into a vector of top-level forms.
   Each form is raw Clojure data (lists, symbols, keywords, strings, numbers).

   Example:
     (strategy \"Golden Cross\"
       (def fast (sma 14))
       (plot fast \"Fast\" :color blue))
   → [(strategy \"Golden Cross\" (def fast (sma 14)) (plot fast \"Fast\" :color blue))]"
  [source]
  (read-all-forms source))
