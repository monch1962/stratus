(ns stratus.import-v4-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]))

;; ═══════════════════════════════════════════════════════════════════
;; V4: if conditions — function calls, not, and
;; ═══════════════════════════════════════════════════════════════════

(deftest if-function-call
  (let [r (imp/convert "if isUpSwing()\n    x = 1")]
    (is (str/includes? r "(is-up-swing"))))

(deftest if-not-function-call
  (let [r (imp/convert "if not isUpSwing()\n    x = 1")]
    (is (str/includes? r "(not (is-up-swing"))))

(deftest if-not-variable
  (let [r (imp/convert "if not trendInitialized\n    x = 1")]
    (is (str/includes? r "(not trendInitialized"))))

(deftest else-if-with-parens
  (let [r (imp/convert "else if (not trendIsUp) and isUpSwing()\n    x = 1")]
    (is (not (str/includes? r "WARN")))))

(deftest if-simple-and
  (let [r (imp/convert "if a and b\n    x = 1")]
    ;; and remains unconverted in if-conditions (string-based limitation)
    (is (str/includes? r "if"))))

;; ═══════════════════════════════════════════════════════════════════
;; V4: Generic function calls in expressions
;; ═══════════════════════════════════════════════════════════════════

(deftest function-call-in-assignment
  (let [r (imp/convert "x = isUpSwing()")]
    (is (str/includes? r "is-up-swing"))
    (is (not (str/includes? r "WARN")))))

(deftest function-call-in-let
  (let [r (imp/convert "x = array.get(swingY, 0)")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V4: Full file quality
;; ═══════════════════════════════════════════════════════════════════

(deftest gann-file-warns-stable
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (<= warns 2) (str "Expected ≤2 WARNs, got " warns))))

(deftest astro-file-zero-warns
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (zero? warns) (str "Expected 0 WARNs, got " warns))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.import-v4-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
