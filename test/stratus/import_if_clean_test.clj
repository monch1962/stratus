(ns stratus.import-if-clean-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

;; ═══════════════════════════════════════════════════════════════════
;; If: no :_ placeholder
;; ═══════════════════════════════════════════════════════════════════

(deftest no-placeholder-in-simple-if
  (let [r (imp/convert "if x > 0\n    y = 1")]
    (is (not (str/includes? r ":_")) "No :_ placeholder")
    (is (not (str/includes? r "...")) "No ellipsis placeholder")))

(deftest no-placeholder-in-if-and
  (let [r (imp/convert "if a and b\n    x = 1")]
    (is (not (str/includes? r ":_")) "No :_ placeholder")))

(deftest no-placeholder-in-nested-if
  (let [r (imp/convert "if x > 0\n    if y > 0\n        z = 1")]
    (is (not (str/includes? r ":_")) "No :_ placeholder")))

;; ═══════════════════════════════════════════════════════════════════
;; If: output format is valid Stratus
;; ═══════════════════════════════════════════════════════════════════

(deftest if-output-parses
  (let [src (imp/convert "if x == 0\n    y = 1")]
    (try
      (reader/parse src)
      (is true "Parsed successfully")
      (catch Exception e
        (is false (str "Parse failed: " (.getMessage e)))))))

(deftest if-and-output-parses
  (let [src (imp/convert "if a and b\n    x = 1")]
    (try
      (reader/parse src)
      (is true "Parsed successfully")
      (catch Exception e
        (is false (str "Parse failed: " (.getMessage e)))))))

;; ═══════════════════════════════════════════════════════════════════
;; If: body lines follow the if condition
;; ═══════════════════════════════════════════════════════════════════

(deftest if-body-follows
  (let [r (imp/convert "if x > 0\n    y = 1")]
    (is (str/includes? r "(if"))
    (is (str/includes? r "(def y 1"))
    ;; The if should be IMMEDIATELY followed by the body
    (is (re-find #"\(if .+\)\n\(def y 1\)" r))))

;; ═══════════════════════════════════════════════════════════════════
;; WARN counts unchanged
;; ═══════════════════════════════════════════════════════════════════

(deftest gann-warns-stable
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (<= warns 2) (str "Expected ≤2 WARNs, got " warns))))

(deftest astro-warns-zero
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (zero? warns) (str "Expected 0 WARNs, got " warns))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.import-if-clean-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
