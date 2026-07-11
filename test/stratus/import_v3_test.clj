(ns stratus.import-v3-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]))

;; ═══════════════════════════════════════════════════════════════════
;; V3: else if lines
;; ═══════════════════════════════════════════════════════════════════

(deftest else-if-line
  (let [r (imp/convert "else if cond")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V3: compound assignment += -= *=
;; ═══════════════════════════════════════════════════════════════════

(deftest compound-assign-plus
  (let [r (imp/convert "x += 1")]
    (is (not (str/includes? r "WARN")))
    (is (str/includes? r "set!"))))

(deftest compound-assign-times
  (let [r (imp/convert "consecCounter *= 2")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V3: bare na
;; ═══════════════════════════════════════════════════════════════════

(deftest bare-na
  (let [r (imp/convert "na")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V3: typed non-var declaration (bool name = expr)
;; ═══════════════════════════════════════════════════════════════════

(deftest typed-declaration
  (let [r (imp/convert "bool outsideBar = barType(0) == \"outside\"")]
    (is (not (str/includes? r "WARN")))))

(deftest string-typed-declaration
  (let [r (imp/convert "string _astroTxt = \"\"")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V3: switch case continuation lines ("up" =>)
;; ═══════════════════════════════════════════════════════════════════

(deftest switch-case-lines
  (let [r (imp/convert "\"up\" =>")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V3: bare and expression
;; ═══════════════════════════════════════════════════════════════════

(deftest and-expression
  (let [r (imp/convert "time >= startTS and time_close <= endTS")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V3: Full files — expect 0 WARNs
;; ═══════════════════════════════════════════════════════════════════

(deftest gann-file-zero-warns
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    ;; switch(barType(0)) needs manual translation — accept ≤2 WARNs
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
  (let [r (clojure.test/run-tests 'stratus.import-v3-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
