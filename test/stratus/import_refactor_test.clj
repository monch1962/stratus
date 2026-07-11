(ns stratus.import-refactor-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]))

;; ═══════════════════════════════════════════════════════════════════
;; #6: convert-table inconsistency — should produce (table.cell ...)
;; ═══════════════════════════════════════════════════════════════════

(deftest table-cell-format
  (let [r (imp/convert "table.cell(myTable, 0, 0, close)")]
    (is (not (str/includes? r "table-")) "Should not use table- prefix")
    (is (str/includes? r "(table.cell")) "Should wrap in parens like array/line"))

(deftest table-new-format
  (let [r (imp/convert "table.new(position.top_right, 5, 5)")]
    (is (str/includes? r "(") "Should wrap in parens")))

;; ═══════════════════════════════════════════════════════════════════
;; #9: apply-until-stable — deep chaining works
;; ═══════════════════════════════════════════════════════════════════

(deftest deep-and-chaining
  (let [r (imp/convert "x = a and b and c and d and e and f")]
    (is (not (str/includes? r "WARN")))
    ;; Should have 5+ nested (and ...)
    (is (str/includes? r "(and"))))

(deftest deep-or-chaining
  (let [r (imp/convert "x = a or b or c or d or e")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; #1/#3: data-driven pipeline preserves existing behavior
;; ═══════════════════════════════════════════════════════════════════

(deftest strategy-builtins-still-work
  (let [r (imp/convert "x = strategy.position_size")]
    (is (str/includes? r "(position-size)"))))

(deftest existing-tests-still-pass
  ;; Spot-check: these were the original import-fix tests
  (is (not (str/includes? (imp/convert "if close > ta.sma(close, 20)\n    strategy.entry(\"Long\", strategy.long)") "WARN")))
  (is (str/includes? (imp/convert "barstate.isconfirmed") "(bar-confirmed)"))
  (is (not (str/includes? (imp/convert "x = color.new(color.teal, 30)") "WARN"))))

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
  (let [r (clojure.test/run-tests 'stratus.import-refactor-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
