(ns stratus.import-v2-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]))

;; ═══════════════════════════════════════════════════════════════════
;; V2: Ternary ? : → iff
;; ═══════════════════════════════════════════════════════════════════

(deftest ternary-converts-to-iff
  (let [r (imp/convert "x = cond ? a : b")]
    (is (str/includes? r "iff"))
    (is (str/includes? r "(iff cond"))))

(deftest ternary-in-expression
  (let [r (imp/convert "halfBar = barstate.isconfirmed ? (high-low)/2 : (high[1]-low[1])/2")]
    (is (not (str/includes? r "WARN"))))
  (let [r (imp/convert "maxRisk = math.min(prevBarRange + 2 * tickSize, 3 * ktr)")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V2: var type declarations
;; ═══════════════════════════════════════════════════════════════════

(deftest var-int-declaration
  (let [r (imp/convert "var int r1Index = int(na)")]
    (is (not (str/includes? r "WARN")))))

(deftest var-line-declaration
  (let [r (imp/convert "var line swingLines = line(na)")]
    (is (not (str/includes? r "WARN")))))

(deftest var-float-declaration
  (let [r (imp/convert "var float highPriceInSwing = high")]
    (is (not (str/includes? r "WARN")))))

(deftest var-bool-declaration
  (let [r (imp/convert "varip bool trendIsUp = false")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V2: for loop
;; ═══════════════════════════════════════════════════════════════════

(deftest for-loop-conversion
  (let [r (imp/convert "for ts in eventData\n    x = 1")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V2: break
;; ═══════════════════════════════════════════════════════════════════

(deftest break-conversion
  (let [r (imp/convert "break")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V2: else on its own line
;; ═══════════════════════════════════════════════════════════════════

(deftest else-line
  (let [r (imp/convert "if cond\n    x = 1\nelse\n    x = 2")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V2: array.from with multi-line args (continuation lines)
;; ═══════════════════════════════════════════════════════════════════

(deftest array-from-with-data
  (let [r (imp/convert "NEW_MOON_DATA = array.from(\n    timestamp(\"2024-Jan-12\"),\n    timestamp(\"2024-Feb-10\"),\n    timestamp(\"2024-Mar-11\"),\n    timestamp(\"2024-Apr-09\"))")]
    (is (not (str/includes? r "WARN")))
    (is (str/includes? r "array.from"))
    (is (str/includes? r "timestamp"))))

;; ═══════════════════════════════════════════════════════════════════
;; V2: Standalone function call
;; ═══════════════════════════════════════════════════════════════════

(deftest standalone-function-call
  (let [r (imp/convert "showRule1Entry()")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V2: Inline // comments
;; ═══════════════════════════════════════════════════════════════════

(deftest inline-comment
  (let [r (imp/convert "x = 1  // this is a comment")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; V2: Full files import with reduced WARNs
;; ═══════════════════════════════════════════════════════════════════

(deftest gann-file-reduced-warns
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (< warns 46) (str "Expected <46 WARNs, got " warns))))

(deftest astro-file-reduced-warns
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (< warns 200) (str "Expected <200 WARNs, got " warns))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.import-v2-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
