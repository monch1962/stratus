(ns stratus.p1p2-fixes-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.constructs :as ct]))

;; ═══════════════════════════════════════════════════════════════════
;; P1: If/else indentation
;; ═══════════════════════════════════════════════════════════════════

(deftest if-else-basic-indentation
  (let [o (gen/expr->pine '(if (> x 0) (long "E") :else (close)))]
    (is (re-find #"(?m)^if x > 0$" o))
    (is (re-find #"(?m)^    strategy\.entry" o))
    (is (re-find #"(?m)^else$" o))
    (is (re-find #"(?m)^    close$" o))))

(deftest if-elseif-else-indentation
  (let [o (gen/expr->pine '(if (> x 70) (short "S") (< x 30) (long "B") :else (close)))]
    (is (re-find #"(?m)^if x > 70$" o))
    (is (re-find #"(?m)^else if x < 30$" o))
    (is (re-find #"(?m)^else$" o))))

(deftest nested-if-indentation
  (let [o (gen/expr->pine '(if (= swing-period 1)
                             (do (if (> high (high 1))
                                   (set! swing-high high)))
                             :else (do
                                     (if (= high (highest high swing-period))
                                       (set! swing-high (highest high swing-period))))))]
    ;; do adds 4-space prefix; if generator starts relative to do
    (is (re-find #"(?m)^if swing_period == 1$" o))
    (is (re-find #"(?m)^    if high > high\[1\]$" o))
    (is (re-find #"(?m)^        swing_high := high$" o))
    (is (re-find #"(?m)^else$" o))
    (is (re-find #"(?m)^    if high == ta\.highest" o))))

;; ═══════════════════════════════════════════════════════════════════
;; P1: strategy.exit() from_entry keyword
;; ═══════════════════════════════════════════════════════════════════

(deftest exit-uses-from-entry
  (is (= (gen/expr->pine '(exit "X" :from "E" :loss 100))
         "strategy.exit(\"X\", from_entry=\"E\", loss=100)"))
  (is (= (gen/expr->pine '(exit "T" :from "E" :trail 50 :trail-offset 10))
         "strategy.exit(\"T\", from_entry=\"E\", trail_points=50, trail_offset=10)")))

(deftest exit-without-from-unchanged
  (is (= (gen/expr->pine '(exit "X" :loss 100 :profit 200))
         "strategy.exit(\"X\", loss=100, profit=200)")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: ta.cross raw
;; ═══════════════════════════════════════════════════════════════════

(deftest cross-generates-ta-cross
  (is (= (gen/expr->pine '(cross a b)) "ta.cross(a, b)"))
  (is (= (gen/expr->pine '(cross fast slow)) "ta.cross(fast, slow)")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: ta.tr and ta.range
;; ═══════════════════════════════════════════════════════════════════

(deftest tr-generates
  (is (= (gen/expr->pine '(tr)) "ta.tr"))
  (is (= (gen/expr->pine '(tr true)) "ta.tr(true)")))

(deftest range-generates
  (is (= (gen/expr->pine '(range)) "ta.range"))
  (is (= (gen/expr->pine '(range high low)) "ta.range(high, low)")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: ta.roc
;; ═══════════════════════════════════════════════════════════════════

(deftest roc-generates
  (is (= (gen/expr->pine '(roc 14)) "ta.roc(close, 14)"))
  (is (= (gen/expr->pine '(roc close 14)) "ta.roc(close, 14)"))
  (is (= (gen/expr->pine '(roc high 10)) "ta.roc(high, 10)")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: MA variants
;; ═══════════════════════════════════════════════════════════════════

(deftest swma-generates
  (is (= (gen/expr->pine '(swma)) "ta.swma(close)"))
  (is (= (gen/expr->pine '(swma high)) "ta.swma(high)")))

(deftest rma-generates
  (is (= (gen/expr->pine '(rma 14)) "ta.rma(close, 14)"))
  (is (= (gen/expr->pine '(rma close 14)) "ta.rma(close, 14)")))

(deftest tema-generates
  (is (= (gen/expr->pine '(tema 20)) "ta.tema(close, 20)"))
  (is (= (gen/expr->pine '(tema close 20)) "ta.tema(close, 20)")))

(deftest dema-generates
  (is (= (gen/expr->pine '(dema 14)) "ta.dema(close, 14)")))

(deftest smma-generates
  (is (= (gen/expr->pine '(smma 14)) "ta.smma(close, 14)")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: Strategy close_all and reverse
;; ═══════════════════════════════════════════════════════════════════

(deftest close-all-generates
  (is (= (gen/expr->pine '(close-all)) "strategy.close_all()")))

(deftest reverse-generates
  (is (= (gen/expr->pine '(reverse)) "strategy.reverse()"))
  (is (= (gen/expr->pine '(reverse "R")) "strategy.reverse(\"R\")")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: Drawing object setters and deleters
;; ═══════════════════════════════════════════════════════════════════

(deftest line-set-generates
  (is (= (gen/expr->pine '(line.set-color l color.red)) "line.set_color(l, color.red)"))
  (is (= (gen/expr->pine '(line.set-width l 2)) "line.set_width(l, 2)"))
  (is (= (gen/expr->pine '(line.set-extend l extend.none)) "line.set_extend(l, extend.none)")))

(deftest label-set-generates
  (is (= (gen/expr->pine '(label.set-color l color.blue)) "label.set_color(l, color.blue)"))
  (is (= (gen/expr->pine '(label.set-text l "Hello")) "label.set_text(l, \"Hello\")"))
  (is (= (gen/expr->pine '(label.set-x l 100)) "label.set_x(l, 100)")))

(deftest box-set-generates
  (is (= (gen/expr->pine '(box.set-color b color.red)) "box.set_color(b, color.red)"))
  (is (= (gen/expr->pine '(box.set-border-color b color.blue)) "box.set_border_color(b, color.blue)"))
  (is (= (gen/expr->pine '(box.set-width b 2)) "box.set_width(b, 2)")))

(deftest label-delete-generates
  (is (= (gen/expr->pine '(label.delete l)) "label.delete(l)")))

(deftest box-delete-generates
  (is (= (gen/expr->pine '(box.delete b)) "box.delete(b)")))

;; ═══════════════════════════════════════════════════════════════════
;; Integration: existing exit tests still pass
;; ═══════════════════════════════════════════════════════════════════

(deftest exit-existing-compat
  (is (= (gen/expr->pine '(exit "X" :from "E" :loss 100 :profit 200))
         "strategy.exit(\"X\", from_entry=\"E\", loss=100, profit=200)")))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.p1p2-fixes-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
