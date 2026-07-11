(ns stratus.import-v5-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]))

;; ═══════════════════════════════════════════════════════════════════
;; V5: Hex colors #XXXXXX → (rgb ...)
;; ═══════════════════════════════════════════════════════════════════

(deftest hex-color-6-digit
  (let [r (imp/convert "bgcolor(#00FF00)")]
    (is (not (str/includes? r "WARN")))))

(deftest hex-color-8-digit
  (let [r (imp/convert "bgcolor(#00FF0020)")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V5: Ternary ? : in plot args
;; ═══════════════════════════════════════════════════════════════════

(deftest ternary-in-plot
  (let [r (imp/convert "bgcolor(_bgUp ? color.red : color.green)")]
    (is (not (str/includes? r "WARN")))
    (is (str/includes? r "iff"))))

;; ═══════════════════════════════════════════════════════════════════
;; V5: WARN counts remain low
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
  (let [r (clojure.test/run-tests 'stratus.import-v5-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
