(ns stratus.p0p1-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

;; ═══════════════════════════════════════════════════════════════════
;; P0: strategy.exit()
;; ═══════════════════════════════════════════════════════════════════

(deftest p0-exit-basic
  (is (= (gen/expr->pine '(exit "XL" :from "E" :loss 100 :profit 200))
         "strategy.exit(\"XL\", from_entry=\"E\", loss=100, profit=200)")))

(deftest p0-exit-with-trail
  (is (= (gen/expr->pine '(exit "XL" :from "E" :trail 50 :trail-offset 10))
         "strategy.exit(\"XL\", from_entry=\"E\", trail_points=50, trail_offset=10)")))

(deftest p0-exit-minimal
  (is (= (gen/expr->pine '(exit "XL" :from "E"))
         "strategy.exit(\"XL\", from_entry=\"E\")")))

;; ═══════════════════════════════════════════════════════════════════
;; P0: if/else multi-branch
;; ═══════════════════════════════════════════════════════════════════

(deftest p0-if-else-simple
  (is (str/includes? (gen/expr->pine '(if (crosses-above fast slow) (long "E") :else (short "X")))
                     "if ta.cross(fast, slow) and fast > slow"))
  (is (str/includes? (gen/expr->pine '(if (crosses-above fast slow) (long "E") :else (short "X")))
                     "strategy.entry(\"E\""))
  (is (str/includes? (gen/expr->pine '(if (crosses-above fast slow) (long "E") :else (short "X")))
                     "else"))
  (is (str/includes? (gen/expr->pine '(if (crosses-above fast slow) (long "E") :else (short "X")))
                     "strategy.entry(\"X\"")))

(deftest p0-if-elseif-else
  (let [o (gen/expr->pine '(if (> a b) (long) (< a b) (short) :else (close "X")))]
    (is (str/includes? o "if a > b"))
    (is (str/includes? o "else if a < b"))
    (is (str/includes? o "else"))
    (is (str/includes? o "strategy.close"))))

;; ═══════════════════════════════════════════════════════════════════
;; P0: var/varip persistent state
;; ═══════════════════════════════════════════════════════════════════

(deftest p0-defvar
  (is (= (gen/expr->pine '(defvar count 0)) "var count = 0"))
  (is (= (gen/expr->pine '(defvarip high-val 0.0)) "varip high_val = 0.0")))

(deftest p0-set!
  (is (= (gen/expr->pine '(set! count (+ count 1))) "count := count + 1"))
  (is (= (gen/expr->pine '(set! running-total (+ running-total close))) "running_total := running_total + close")))

;; ═══════════════════════════════════════════════════════════════════
;; P0: highest() / lowest()
;; ═══════════════════════════════════════════════════════════════════

(deftest p0-highest
  (is (= (gen/expr->pine '(highest high 20)) "ta.highest(high, 20)"))
  (is (= (gen/expr->pine '(highest close 14)) "ta.highest(close, 14)")))

(deftest p0-lowest
  (is (= (gen/expr->pine '(lowest low 20)) "ta.lowest(low, 20)"))
  (is (= (gen/expr->pine '(lowest close 10)) "ta.lowest(close, 10)")))

;; ═══════════════════════════════════════════════════════════════════
;; P0: fill()
;; ═══════════════════════════════════════════════════════════════════

(deftest p0-fill
  (is (= (gen/expr->pine '(fill p1 p2 :color blue :alpha 90))
         "fill(p1, p2, color=color.new(color.blue, 90))"))
  (is (= (gen/expr->pine '(fill p1 p2))
         "fill(p1, p2)")))

;; ═══════════════════════════════════════════════════════════════════
;; P0: request.security() multi-timeframe
;; ═══════════════════════════════════════════════════════════════════

(deftest p0-security
  (is (= (gen/expr->pine '(security "60" (sma 20)))
         "request.security(syminfo.tickerid, \"60\", ta.sma(close, 20))"))
  (is (= (gen/expr->pine '(security "D" (high)))
         "request.security(syminfo.tickerid, \"D\", high)")))

;; ═══════════════════════════════════════════════════════════════════
;; P1: na() / nz() / iff()
;; ═══════════════════════════════════════════════════════════════════

(deftest p1-na-nz-iff
  (is (= (gen/expr->pine '(na x)) "na(x)"))
  (is (= (gen/expr->pine '(nz x 0)) "nz(x, 0)"))
  (is (= (gen/expr->pine '(nz x)) "nz(x)"))
  (is (= (gen/expr->pine '(iff (> a b) a b)) "iff(a > b, a, b)")))

;; ═══════════════════════════════════════════════════════════════════
;; P1: change() / mom()
;; ═══════════════════════════════════════════════════════════════════

(deftest p1-change-mom
  (is (= (gen/expr->pine '(change close 1)) "change(close, 1)"))
  (is (= (gen/expr->pine '(mom close 10)) "mom(close, 10)")))

;; ═══════════════════════════════════════════════════════════════════
;; P1: Additional indicators
;; ═══════════════════════════════════════════════════════════════════

(deftest p1-inds
  (is (= (gen/expr->pine '(supertrend 3 10)) "ta.supertrend(3, 10)"))
  (is (= (gen/expr->pine '(sar 0.02 0.2)) "ta.sar(0.02, 0.2)"))
  (is (= (gen/expr->pine '(vwap)) "ta.vwap(hlc3)"))
  (is (= (gen/expr->pine '(vwap hl2)) "ta.vwap(hl2)"))
  (is (= (gen/expr->pine '(stdev close 20)) "ta.stdev(close, 20)"))
  (is (= (gen/expr->pine '(wma 14)) "ta.wma(close, 14)"))
  (is (= (gen/expr->pine '(vwma 20)) "ta.vwma(close, 20)"))
  (is (= (gen/expr->pine '(hma 20)) "ta.hma(close, 20)"))
  (is (= (gen/expr->pine '(alma 10 6 0.85)) "ta.alma(close, 10, 6, 0.85)"))
  (is (= (gen/expr->pine '(cci close 20)) "ta.cci(close, 20)"))
  (is (= (gen/expr->pine '(mfi hlc3 14)) "ta.mfi(hlc3, 14)"))
  (is (= (gen/expr->pine '(obv)) "ta.obv"))
  (is (= (gen/expr->pine '(linreg close 14 0)) "ta.linreg(close, 14, 0)")))

;; ═══════════════════════════════════════════════════════════════════
;; P1: Plot styles
;; ═══════════════════════════════════════════════════════════════════

(deftest p1-plot-styles
  (is (str/includes? (gen/expr->pine '(plot val :style :histogram)) "plot.style_histogram"))
  (is (str/includes? (gen/expr->pine '(plot val :style :area)) "plot.style_area"))
  (is (str/includes? (gen/expr->pine '(plot val :style :columns)) "plot.style_columns"))
  (is (str/includes? (gen/expr->pine '(plot val :style :circles)) "plot.style_circles"))
  (is (str/includes? (gen/expr->pine '(plot val :style :cross)) "plot.style_cross"))
  (is (str/includes? (gen/expr->pine '(plot val :style :step-line)) "plot.style_stepline"))
  (is (str/includes? (gen/expr->pine '(plot val :style :step-line-diamond)) "plot.style_stepline_diamond")))

;; ═══════════════════════════════════════════════════════════════════
;; P1: Tuple unpacking (multiset)
;; ═══════════════════════════════════════════════════════════════════

(deftest p1-multiset
  (is (= (gen/expr->pine '(multiset [m s h] (macd)))
         "[m, s, h] = ta.macd(close, 12, 26, 9)"))
  (is (= (gen/expr->pine '(multiset [mid upper lower] (bb 20 2.5)))
         "[mid, upper, lower] = ta.bb(close, 20, 2.5)"))
  (is (str/includes? (gen/expr->pine '(multiset [k d] (stoch 14 3 3)))
                     "ta.stoch(close, high, low, 14")))

;; ═══════════════════════════════════════════════════════════════════
;; P1: Strategy position info
;; ═══════════════════════════════════════════════════════════════════

(deftest p1-strategy-builtins
  (is (= (gen/expr->pine '(position-size)) "strategy.position_size"))
  (is (= (gen/expr->pine '(position-avg-price)) "strategy.position_avg_price"))
  (is (= (gen/expr->pine '(open-trades)) "strategy.opentrades"))
  (is (= (gen/expr->pine '(equity)) "strategy.equity"))
  (is (= (gen/expr->pine '(net-profit)) "strategy.netprofit")))

;; ═══════════════════════════════════════════════════════════════════
;; P1: barcolor unconditional + color.new
;; ═══════════════════════════════════════════════════════════════════

(deftest p1-barcolor-uncond
  (is (str/includes? (gen/expr->pine '(barcolor :color green)) "color.green")))

(deftest p1-color-new
  (is (= (gen/expr->pine '(color :red 90)) "color.new(color.red, 90)"))
  (is (= (gen/expr->pine '(color blue 50)) "color.new(color.blue, 50)")))

;; ═══════════════════════════════════════════════════════════════════
;; P0+P1: Integration — full strategy with exit
;; ═══════════════════════════════════════════════════════════════════

(deftest p0p1-full-strategy
  (let [src "
(strategy \"Trailing\" :default-qty 100)
(defvar count 0)
(def fast (sma 20))
(def slow (sma 50))
(on-bar
  (if (crosses-above fast slow)
      (do (set! count (+ count 1)) (long \"E\"))
      :else (close)))
(exit \"XL\" :from \"E\" :trail 50 :trail-offset 10)
(plot fast)
(plot slow)"
        o (gen/emit-file (reader/parse src))]
    (is (str/includes? o "var count = 0"))
    (is (str/includes? o "count := count + 1"))
    (is (str/includes? o "strategy.exit"))
    (is (str/includes? o "trail_points=50"))
    (is (str/includes? o "trail_offset=10"))
    (is (str/includes? o "else"))
    (is (str/includes? o "if "))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.p0p1-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
