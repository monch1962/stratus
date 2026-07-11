(ns stratus.import-fix-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]))

;; ═══════════════════════════════════════════════════════════════════
;; P0 Crash fixes — these must not throw
;; ═══════════════════════════════════════════════════════════════════

(deftest function-def-with-arrow
  (is (str/includes? (imp/convert "isUpSwing() => array.get(swingY, IDX_CURRENT) > array.get(swingY, IDX_PREV)")
                     "defn is-up-swing"))
  (is (str/includes? (imp/convert "myFun(x, y) => x + y") "defn my-fun")))

(deftest if-conversion-no-crash
  (let [r (imp/convert "if (high[offset] > high[offset+1] and low[offset] < low[offset+1])\n    \"outside\"")]
    (is (str/includes? r "if"))))

(deftest full-gann-file-imports-without-crash
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)]
    (is (str/includes? result "indicator"))
    (is (str/includes? result "def"))
    (is (not (str/blank? result)))))

;; ═══════════════════════════════════════════════════════════════════
;; P1: Input conversion
;; ═══════════════════════════════════════════════════════════════════

(deftest convert-input-bool
  (is (str/includes? (imp/convert "showGannSwings = input.bool(true, 'Show swings')") "input-bool")))

(deftest convert-input-color
  (is (str/includes? (imp/convert "colorUp = input.color(color.teal, 'Up')") "input-color")))

(deftest convert-input-int
  (is (str/includes? (imp/convert "widthGann = input.int(2, 'Width', minval=1)") "input-int")))

(deftest convert-input-with-keyword-args
  (let [r (imp/convert "showGannSwings = input.bool(true, 'Show swings', group='Gann', inline='Line1')")]
    (is (str/includes? r "input-bool"))
    (is (str/includes? r ":group"))
    (is (str/includes? r ":inline"))))

;; ═══════════════════════════════════════════════════════════════════
;; P1: color.new with no space after comma
;; ═══════════════════════════════════════════════════════════════════

(deftest color-new-no-space
  (is (not (str/includes? (imp/convert "color.new(color.teal,30)") "WARN"))))

;; ═══════════════════════════════════════════════════════════════════
;; P2: request.security
;; ═══════════════════════════════════════════════════════════════════

(deftest convert-request-security
  (let [r (imp/convert "pyramid = request.security(syminfo.tickerid, \"1W\", ta.atr(8))")]
    (is (str/includes? r "security"))))

;; ═══════════════════════════════════════════════════════════════════
;; P2: line.new / line.delete / array operations
;; ═══════════════════════════════════════════════════════════════════

(deftest convert-line-new
  (is (str/includes? (imp/convert "line.new(x1, y1, x2, y2, color=color.blue)")
                     "(line.new x1 y1 x2 y2")))

(deftest convert-line-delete
  (is (str/includes? (imp/convert "line.delete(swingLines[1])") "(line.delete")))

(deftest convert-array-push
  (is (str/includes? (imp/convert "array.push(swingY, close)") "push")))

(deftest convert-array-get
  (is (str/includes? (imp/convert "array.get(swingY, IDX_CURRENT)") "(get swingY IDX_CURRENT)")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: barstate / syminfo
;; ═══════════════════════════════════════════════════════════════════

(deftest convert-barstate
  (let [r1 (imp/convert "barstate.islast")
        r2 (imp/convert "barstate.isconfirmed")]
    (is (str/includes? r1 "bar-last"))
    (is (str/includes? r2 "bar-confirmed"))))

(deftest convert-syminfo
  (let [r1 (imp/convert "syminfo.mintick")
        r2 (imp/convert "syminfo.tickerid")]
    (is (str/includes? r1 "mintick"))
    (is (str/includes? r2 "tickerid"))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.import-fix-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
