(ns stratus.import-v6-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

;; ═══════════════════════════════════════════════════════════════════
;; V6: Single-quoted strings → double-quoted
;; ═══════════════════════════════════════════════════════════════════

(deftest single-quotes-converted
  (let [input (str "x = input.bool(true, '" "Show swings" "')")
        r (imp/convert input)]
    (is (not (str/includes? r (str "'" "Show"))))
    (is (str/includes? r "Show swings"))))

;; ═══════════════════════════════════════════════════════════════════
;; V6: Output quality checks (no invalid tokens)
;; ═══════════════════════════════════════════════════════════════════

(deftest gann-no-ellipsis-in-output
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)]
    (is (not (str/includes? result " ...")) "No ... placeholder in output")))

(deftest gann-no-hash-in-output
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)]
    (is (not (re-find #"#[0-9A-Fa-f]" result)) "No hex # in output")))

(deftest astro-no-single-quotes
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)]
    (is (not (re-find #"'[A-Za-z]" result)) "No single-quoted strings in output")))

;; ═══════════════════════════════════════════════════════════════════
;; V6: WARN counts remain low
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
  (let [r (clojure.test/run-tests 'stratus.import-v6-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
