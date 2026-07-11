(ns stratus.import-math-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]
            [stratus.reader :as reader]))

;; ═══════════════════════════════════════════════════════════════════
;; Math: binary operators → Polish notation
;; ═══════════════════════════════════════════════════════════════════

(deftest simple-add
  (let [r (imp/convert "x = 4 + 3")]
    (is (not (str/includes? r "WARN")))
    (is (re-find #"\(\+ 4 3\)" r))))

(deftest simple-subtract
  (let [r (imp/convert "x = a - b")]
    (is (re-find #"\(- a b\)" r))))

(deftest simple-multiply
  (let [r (imp/convert "x = a * b")]
    (is (re-find #"\(\* a b\)" r))))

(deftest simple-divide
  (let [r (imp/convert "x = a / b")]
    (is (re-find #"\(/ a b\)" r))))

;; ═══════════════════════════════════════════════════════════════════
;; Math: operator precedence (* before +)
;; ═══════════════════════════════════════════════════════════════════

(deftest precedence-mul-before-add
  (let [r (imp/convert "x = a + b * c")]
    ;; * converts first; + stays infix (left operand has spaces)
    (is (str/includes? r "(* b c"))))

(deftest precedence-mul-before-sub
  (let [r (imp/convert "x = a - b * c")]
    (is (str/includes? r "(* b c"))))

(deftest precedence-div-after-sub
  (let [r (imp/convert "x = (high + low) / 2")]
    ;; paren group converts (high + low) first
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; Math: chained same-precedence (first pair only)
;; ═══════════════════════════════════════════════════════════════════

(deftest chained-add
  (let [r (imp/convert "x = a + b + c")]
    ;; single pass converts first pair
    (is (str/includes? r "(+ a b"))))

(deftest chained-mul
  (let [r (imp/convert "x = a * b * c")]
    (is (str/includes? r "(* a b"))))

;; ═══════════════════════════════════════════════════════════════════
;; Math: with function calls
;; ═══════════════════════════════════════════════════════════════════

(deftest math-with-abs
  (let [r (imp/convert "x = math.abs(high - low) * 2")]
    (is (not (str/includes? r "WARN")))))

(deftest push-swing-expr
  (let [r (imp/convert "x = close - math.abs(high - low)*1")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; Math: in if conditions
;; ═══════════════════════════════════════════════════════════════════

(deftest math-in-if-cond
  (let [r (imp/convert "if x > 0 and y < 0 + z\n    w = 1")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; Math: with comparisons
;; ═══════════════════════════════════════════════════════════════════

(deftest comparison-after-math
  (let [r (imp/convert "x = a + b > 0")]
    (is (not (str/includes? r "WARN")))))

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
  (let [r (clojure.test/run-tests 'stratus.import-math-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
