(ns stratus.p1p2-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

;; ═══════════════════════════════════════════════════════════════════
;; P1: cum / highestbars / lowestbars / sum / avg
;; ═══════════════════════════════════════════════════════════════════

(deftest p1-cum
  (is (= (gen/expr->pine '(cum (change close 1))) "math.cum(change(close, 1))")))

(deftest p1-highestbars
  (is (= (gen/expr->pine '(highestbars high 20)) "ta.highestbars(high, 20)"))
  (is (= (gen/expr->pine '(highestbars close 14)) "ta.highestbars(close, 14)")))

(deftest p1-lowestbars
  (is (= (gen/expr->pine '(lowestbars low 14)) "ta.lowestbars(low, 14)")))

(deftest p1-sum-avg
  (is (= (gen/expr->pine '(sum close 20)) "math.sum(close, 20)"))
  (is (= (gen/expr->pine '(avg close 20)) "math.avg(close, 20)")))

;; ═══════════════════════════════════════════════════════════════════
;; P1: fixnan / valuewhen
;; ═══════════════════════════════════════════════════════════════════

(deftest p1-fixnan-valuewhen
  (is (= (gen/expr->pine '(fixnan (rsi 14))) "fixnan(ta.rsi(close, 14))"))
  (is (= (gen/expr->pine '(valuewhen (crosses-above fast slow) close))
         "ta.valuewhen(ta.cross(fast, slow) and fast > slow, close)")))

;; ═══════════════════════════════════════════════════════════════════
;; P1: strategy.order / strategy.cancel
;; ═══════════════════════════════════════════════════════════════════

(deftest p1-order-cancel
  (is (= (gen/expr->pine '(order "LIMIT" :long 100.0 :limit 50.25))
         "strategy.order(\"LIMIT\", strategy.long, limit=50.25)")))

(deftest p1-cancel
  (is (= (gen/expr->pine '(cancel "LIMIT")) "strategy.cancel(\"LIMIT\")")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: Statistics — correlation / covariance / median / mode / percentile
;; ═══════════════════════════════════════════════════════════════════

(deftest p2-stats
  (is (= (gen/expr->pine '(correlation close high 20)) "ta.correlation(close, high, 20)"))
  (is (= (gen/expr->pine '(covariance close high 20)) "ta.covariance(close, high, 20)"))
  (is (= (gen/expr->pine '(median close 20)) "ta.median(close, 20)"))
  (is (= (gen/expr->pine '(mode close 20)) "ta.mode(close, 20)"))
  (is (= (gen/expr->pine '(percentile close 20 90)) "ta.percentile_nearest_rank(close, 20, 90)")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: barstate.* built-ins
;; ═══════════════════════════════════════════════════════════════════

(deftest p2-barstate
  (is (= (gen/expr->pine '(bar-confirmed)) "barstate.isconfirmed"))
  (is (= (gen/expr->pine '(bar-first)) "barstate.isfirst"))
  (is (= (gen/expr->pine '(bar-last)) "barstate.islast")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: Time / session built-ins
;; ═══════════════════════════════════════════════════════════════════

(deftest p2-time-builtins
  (is (= (gen/expr->pine '(time)) "time"))
  (is (= (gen/expr->pine '(dayofweek)) "dayofweek"))
  (is (= (gen/expr->pine '(month)) "month"))
  (is (= (gen/expr->pine '(hour)) "hour"))
  (is (= (gen/expr->pine '(bar-index)) "bar_index"))
  (is (= (gen/expr->pine '(ticker)) "syminfo.tickerid"))
  (is (= (gen/expr->pine '(timeframe)) "timeframe.period"))
  (is (= (gen/expr->pine '(in-session "0930-1600")) "session.isregular(\"0930-1600\")")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: for loop
;; ═══════════════════════════════════════════════════════════════════

(deftest p2-for-loop
  (is (= (gen/expr->pine '(for [i (range 1 5)] (long)))
         "for i = 1 to 5\n    strategy.entry(\"Long\", strategy.long)"))
  (is (str/includes? (gen/expr->pine '(for [i (range 1 3)] (set! sum (+ sum (close i)))))
                     "for i = 1 to 3")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: while loop
;; ═══════════════════════════════════════════════════════════════════

(deftest p2-while-loop
  (is (str/includes? (gen/expr->pine '(while (< i 10) (set! i (+ i 1))))
                     "while"))
  (is (str/includes? (gen/expr->pine '(while (< i 10) (set! i (+ i 1))))
                     "i := i + 1")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: switch statement
;; ═══════════════════════════════════════════════════════════════════

(deftest p2-switch
  (is (str/includes? (gen/expr->pine '(switch regime 0 (long "TREND") 1 (short "MR") :else (close "X")))
                     "switch"))
  (is (str/includes? (gen/expr->pine '(switch regime 0 (long "TREND") 1 (short "MR") :else (close "X")))
                     "strategy.entry")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: User-defined function (defn)
;; ═══════════════════════════════════════════════════════════════════

(deftest p2-defn
  (is (= (gen/expr->pine '(defn my-sma [src n] (sma src n)))
         "my_sma(src, n) =>\n    ta.sma(src, n)")))

(deftest p2-defn-multi-line
  (is (str/includes? (gen/expr->pine '(defn my-indicator [x y] (+ x y)))
                     "my_indicator(x, y) =>")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: library() header
;; ═══════════════════════════════════════════════════════════════════

(deftest p2-library
  (is (= (gen/expr->pine '(library "MyLib" :overlay true))
         "//@version=6\nlibrary(\"MyLib\", overlay=true)")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: Plot conditional colouring
;; ═══════════════════════════════════════════════════════════════════

(deftest p2-plot-conditional-color
  (is (str/includes? (gen/expr->pine '(plot val :color (if (> val 0) green red)))
                     "color="))
  (is (str/includes? (gen/expr->pine '(plot val :color (if (> val 0) green red)))
                     "if")))

;; ═══════════════════════════════════════════════════════════════════
;; Integration: full strategy with loops, state, and functions
;; ═══════════════════════════════════════════════════════════════════

(deftest p2-full-strategy
  (let [src "
(strategy \"Stats\" :default-qty 100)
(defn my-ma [src n] (sma src n))
(defvar sum-val 0.0)
(def fast (my-ma close 20))
(def slow (my-ma close 50))
(on-bar
  (if (crosses-above fast slow)
      (long \"E\")
      :else (close)))
(exit \"XL\" :from \"E\" :trail 50)
(plot fast)
(plot slow)
(plot (cum (change close 1)))"
        o (gen/emit-file (reader/parse src))]
    (is (str/includes? o "my_ma(src, n) =>"))
    (is (str/includes? o "ta.sma(src, n)"))
    (is (str/includes? o "var sum_val = 0.0"))
    (is (str/includes? o "strategy.exit"))
    (is (str/includes? o "math.cum"))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.p1p2-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
