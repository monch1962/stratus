(ns stratus.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.constructs :as ct]))

;; ═══════════════════════════════════════════════════════════════════
;; Reader Tests
;; ═══════════════════════════════════════════════════════════════════

(deftest reader-parses-simple-form
  (is (= (reader/parse "(sma 14)") '((sma 14)))))

(deftest reader-parses-multiple-forms
  (let [r (reader/parse "(def fast (sma 50))\n(def slow (sma 200))")]
    (is (= 2 (count r)))
    (is (= 'def (ffirst r)))
    (is (= 'def (ffirst (rest r))))))

(deftest reader-removes-line-comments
  (is (= (reader/parse ";; comment\n(sma 14)") '((sma 14)))))

(deftest reader-removes-inline-comments
  (is (= (reader/parse "(sma 14) ;; trailing") '((sma 14)))))

(deftest reader-handles-keywords
  (is (= (reader/parse "(plot val :color blue :linewidth 2)")
         '((plot val :color blue :linewidth 2)))))

(deftest reader-handles-strings
  (is (= (reader/parse "(strategy \"Golden Cross\")")
         '((strategy "Golden Cross")))))

(deftest reader-handles-mixed-with-strategy-block
  (let [r (reader/parse "(strategy \"T\" :q 100)\n(def x (sma 14))\n(on-bar\n  (when (crosses-above x x) (long \"E\")))")]
    (is (= 3 (count r)))
    (is (= :q (-> r first (nth 2))))))

(deftest reader-handles-empty-input
  (is (= (reader/parse "") '())))

;; ═══════════════════════════════════════════════════════════════════
;; Construct Definitions
;; ═══════════════════════════════════════════════════════════════════

(deftest constructs-are-complete
  (doseq [c ct/constructs]
    (is (:name c) (str "Missing :name: " (pr-str c)))
    (is (:category c) (str (or (:name c) "?") " missing :category"))
    (is (:doc c) (str (or (:name c) "?") " missing :doc"))))

(deftest constructs-contain-all-planned
  (let [names (set (map :name ct/constructs))]
    (doseq [req [:strategy :indicator :def :sma :ema :rsi :macd :adx
                 :stoch :bb :atr :crosses-above :crosses-below
                 :rising :falling :and :or :not :> :< :>= :<= :=
                 :long :short :close :plot :plotshape :hline
                 :bgcolor :barcolor :alertcondition
                 :close :high :low :open :volume]]
      (is (names req) (str "Missing construct: " req)))))

;; ═══════════════════════════════════════════════════════════════════
;; Generator — Indicators
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-sma
  (is (= (gen/expr->pine '(sma 14)) "ta.sma(close, 14)"))
  (is (= (gen/expr->pine '(sma 10)) "ta.sma(close, 10)")))

(deftest gen-ema
  (is (= (gen/expr->pine '(ema 20)) "ta.ema(close, 20)")))

(deftest gen-rsi
  (is (= (gen/expr->pine '(rsi 14)) "ta.rsi(close, 14)")))

(deftest gen-macd
  (is (= (gen/expr->pine '(macd)) "ta.macd(close, 12, 26, 9)"))
  (is (= (gen/expr->pine '(macd :fast 8 :slow 21 :signal 5))
         "ta.macd(close, 8, 21, 5)")))

(deftest gen-adx
  (is (= (gen/expr->pine '(adx 14)) "ta.adx(high, low, close, 14)"))
  (is (= (gen/expr->pine '(adx 7)) "ta.adx(high, low, close, 7)")))

(deftest gen-stoch
  (is (= (gen/expr->pine '(stoch 14)) "ta.stoch(close, high, low, 14)")))

(deftest gen-bb
  (is (= (gen/expr->pine '(bb 20)) "ta.bb(close, 20, 2.0)"))
  (is (= (gen/expr->pine '(bb 20 2.5)) "ta.bb(close, 20, 2.5)")))

(deftest gen-atr
  (is (= (gen/expr->pine '(atr 14)) "ta.atr(14)"))
  (is (= (gen/expr->pine '(atr 7)) "ta.atr(7)")))

;; ═══════════════════════════════════════════════════════════════════
;; Generator — Conditions & Logic
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-crosses-above
  (is (str/includes? (gen/expr->pine '(crosses-above (sma 50) (sma 200))) "ta.cross("))
  (is (str/includes? (gen/expr->pine '(crosses-above (sma 50) (sma 200))) " > ")))

(deftest gen-crosses-below
  (is (str/includes? (gen/expr->pine '(crosses-below (sma 50) (sma 200))) "ta.cross("))
  (is (str/includes? (gen/expr->pine '(crosses-below (sma 50) (sma 200))) " < ")))

(deftest gen-rising-falling
  (is (= (gen/expr->pine '(rising close)) "rising(close, 1)"))
  (is (= (gen/expr->pine '(falling (rsi 14))) "falling(ta.rsi(close, 14), 1)")))

(deftest gen-logic
  (is (= (gen/expr->pine '(and a b)) "(a and b)"))
  (is (= (gen/expr->pine '(or a b c)) "(a or b or c)"))
  (is (= (gen/expr->pine '(not x)) "not x")))

(deftest gen-comparisons
  (is (= (gen/expr->pine '(> a b)) "a > b"))
  (is (= (gen/expr->pine '(< a b)) "a < b"))
  (is (= (gen/expr->pine '(>= a b)) "a >= b"))
  (is (= (gen/expr->pine '(<= a b)) "a <= b"))
  (is (= (gen/expr->pine '(= a b)) "a == b")))

;; ═══════════════════════════════════════════════════════════════════
;; Generator — Strategy Actions
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-long
  (is (= (gen/expr->pine '(long "E")) "strategy.entry(\"E\", strategy.long)"))
  (is (= (gen/expr->pine '(long)) "strategy.entry(\"Long\", strategy.long)")))

(deftest gen-short
  (is (= (gen/expr->pine '(short "S")) "strategy.entry(\"S\", strategy.short)"))
  (is (= (gen/expr->pine '(short)) "strategy.entry(\"Short\", strategy.short)")))

(deftest gen-close
  (is (= (gen/expr->pine '(close "X")) "strategy.close(\"X\")"))
  (is (= (gen/expr->pine '(close)) "close")))  ;; bare (close) = price ref

;; ═══════════════════════════════════════════════════════════════════
;; Generator — Plotting & Alerts
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-plot
  (is (= (gen/expr->pine '(plot (sma 14) "Fast" :color blue :linewidth 2))
         "plot(ta.sma(close, 14), \"Fast\", color=color.blue, linewidth=2)")))

(deftest gen-plot-with-defaults
  (is (= (gen/expr->pine '(plot x)) "plot(x)"))
  (is (= (gen/expr->pine '(plot y "Title")) "plot(y, \"Title\")")))

(deftest gen-plotshape
  (let [o (gen/expr->pine '(plotshape cond "D" :location bottom :color green :style triangle-up))]
    (is (str/includes? o "location=location.bottom"))
    (is (str/includes? o "color=color.green"))
    (is (str/includes? o "style=shape.triangleup"))))

(deftest gen-hline
  (let [o (gen/expr->pine '(hline 70 "OB" :color red :linestyle dashed))]
    (is (str/includes? o "hline(70"))
    (is (str/includes? o "color=color.red"))
    (is (str/includes? o "linestyle=hline.style_dashed"))))

(deftest gen-bgcolor
  (is (= (gen/expr->pine '(bgcolor cond :color blue))
         "bgcolor(cond, color=color.blue)")))

(deftest gen-barcolor
  (is (= (gen/expr->pine '(barcolor cond :color red))
         "barcolor(cond, color=color.red)")))

(deftest gen-alertcondition
  (is (= (gen/expr->pine '(alertcondition cond "Alert"))
         "alertcondition(cond, \"Alert\")")))

;; ═══════════════════════════════════════════════════════════════════
;; Generator — Price References
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-close-price
  (is (= (gen/expr->pine '(close)) "close"))
  (is (= (gen/expr->pine '(close 1)) "close[1]"))
  (is (= (gen/expr->pine '(close 5)) "close[5]")))

(deftest gen-price-builtins
  (is (= (gen/expr->pine '(high)) "high"))
  (is (= (gen/expr->pine '(low)) "low"))
  (is (= (gen/expr->pine '(open)) "open"))
  (is (= (gen/expr->pine '(volume)) "volume"))
  (is (= (gen/expr->pine '(hl2)) "hl2"))
  (is (= (gen/expr->pine '(hlc3)) "hlc3"))
  (is (= (gen/expr->pine '(ohlc4)) "ohlc4")))

;; ═══════════════════════════════════════════════════════════════════
;; Generator — Strategy Header
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-strategy-header
  (is (= (gen/expr->pine '(strategy "T" :default-qty 100 :pyramiding 1))
         "strategy(\"T\", default_qty=100, pyramiding=1)")))

(deftest gen-strategy-header-minimal
  (is (= (gen/expr->pine '(strategy "T"))
         "strategy(\"T\")")))

(deftest gen-indicator-header
  (is (= (gen/expr->pine '(indicator "RSI" :overlay false :precision 2))
         "indicator(\"RSI\", overlay=false, precision=2)")))

;; ═══════════════════════════════════════════════════════════════════
;; Generator — Def & On-Bar
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-def
  (is (= (gen/expr->pine '(def fast (sma 50))) "fast = ta.sma(close, 50)"))
  (is (= (gen/expr->pine '(def r (rsi 14))) "r = ta.rsi(close, 14)")))

(deftest gen-on-bar-with-when
  (let [o (gen/expr->pine '(on-bar
                            (when (crosses-above fast slow)
                              (long "E"))
                            (when (crosses-below fast slow)
                              (close "X"))))]
    (is (str/includes? o "if "))
    (is (str/includes? o "ta.cross("))
    (is (str/includes? o "strategy.entry"))
    (is (str/includes? o "strategy.close"))))

;; ═══════════════════════════════════════════════════════════════════
;; Generator — Full Strategy Compilation
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-full-strategy
  (let [src "
(strategy \"Test\" :default-qty 100)
(def fast (sma 50))
(def slow (sma 200))
(on-bar
  (when (crosses-above fast slow) (long \"E\")))
(plot fast \"F\" :color blue)"
        o (gen/emit-file (reader/parse src))]
    (is (str/includes? o "//@version=6"))
    (is (str/includes? o "strategy(\"Test\""))
    (is (str/includes? o "ta.sma(close, 50)"))
    (is (str/includes? o "ta.cross("))
    (is (str/includes? o "strategy.entry"))
    (is (str/includes? o "plot("))))

(deftest gen-full-indicator
  (let [src "
(indicator \"RSI\" :overlay false :precision 2)
(def r (rsi 14))
(plot r \"RSI\" :color purple :linewidth 2)
(hline 70 \"OB\" :color red :linestyle dashed)"
        o (gen/emit-file (reader/parse src))]
    (is (str/includes? o "//@version=6"))
    (is (str/includes? o "indicator(\"RSI\""))
    (is (str/includes? o "ta.rsi(close, 14)"))
    (is (str/includes? o "plot("))
    (is (str/includes? o "hline(70"))))

(deftest gen-golden-cross-example
  (let [o (gen/emit-file (reader/parse (slurp "examples/golden-cross.stratus")))]
    (is (str/includes? o "//@version=6"))
    (is (str/includes? o "ta.sma(close, 50)"))
    (is (str/includes? o "ta.cross("))
    (is (str/includes? o "strategy.entry"))
    (is (str/includes? o "color=color.blue"))
    (is (str/includes? o "color=color.red"))
    (is (str/includes? o "linewidth=2"))))

(deftest gen-rsi-divergence-example
  (let [o (gen/emit-file (reader/parse (slurp "examples/rsi-divergence.stratus")))]
    (is (str/includes? o "ta.rsi(close, 14)"))
    (is (str/includes? o "close[1]"))
    (is (str/includes? o "location=location.bottom"))
    (is (str/includes? o "style=shape.triangleup"))
    (is (str/includes? o "linestyle=hline.style_dashed"))))

(deftest gen-adaptive-regime-example
  (let [o (gen/emit-file (reader/parse (slurp "examples/adaptive-regime.stratus")))]
    (is (str/includes? o "ta.adx(high, low, close, 14)"))
    (is (str/includes? o "strategy.entry(\"TREND\""))
    (is (str/includes? o "strategy.entry(\"MR\""))
    (is (str/includes? o "rising("))
    (is (str/includes? o "falling("))))

;; ═══════════════════════════════════════════════════════════════════
;; Generator — Edge Cases
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-empty-forms
  (is (str/includes? (gen/emit-file []) "//@version=6")))

(deftest gen-def-with-complex-expr
  (is (= (gen/expr->pine '(def v (> (sma 50) (sma 200))))
         "v = ta.sma(close, 50) > ta.sma(close, 200)")))

(deftest gen-nested-logic
  (is (= (gen/expr->pine '(and (or a b) (not c)))
         "((a or b) and not c)")))

(deftest gen-keyword-color-mapping
  (is (str/includes? (gen/expr->pine '(plot x :color blue)) "color=color.blue"))
  (is (str/includes? (gen/expr->pine '(plot x :color red)) "color=color.red"))
  (is (str/includes? (gen/expr->pine '(plot x :color purple)) "color=color.purple")))

(deftest gen-bool-literal-handling
  (is (= (gen/expr->pine true) "true"))
  (is (= (gen/expr->pine false) "false"))
  (is (= (gen/expr->pine nil) "na")))

;; ═══════════════════════════════════════════════════════════════════
;; Property-Based: Random Indicator Periods
;; ═══════════════════════════════════════════════════════════════════

(deftest property-random-sma-periods
  (doseq [p (take 30 (repeatedly #(+ 2 (rand-int 198))))]
    (let [o (gen/expr->pine (list 'sma p))]
      (is (re-find #"^ta\.sma\(close, \d+\)$" o)
          (str "Failed sma period " p)))))

(deftest property-random-ema-periods
  (doseq [p (take 20 (repeatedly #(+ 2 (rand-int 198))))]
    (let [o (gen/expr->pine (list 'ema p))]
      (is (re-find #"^ta\.ema\(close, \d+\)$" o)
          (str "Failed ema period " p)))))

(deftest property-random-rsi-periods
  (doseq [p (take 20 (repeatedly #(+ 2 (rand-int 98))))]
    (let [o (gen/expr->pine (list 'rsi p))]
      (is (re-find #"^ta\.rsi\(close, \d+\)$" o)
          (str "Failed rsi period " p)))))

(deftest property-random-atr-periods
  (doseq [p (take 20 (repeatedly #(+ 2 (rand-int 98))))]
    (let [o (gen/expr->pine (list 'atr p))]
      (is (re-find #"^ta\.atr\(\d+\)$" o)
          (str "Failed atr period " p)))))

;; ═══════════════════════════════════════════════════════════════════
;; Property-Based: Strategy + Plot Variations
;; ═══════════════════════════════════════════════════════════════════

(deftest property-various-colors-compile
  (doseq [c [:red :green :blue :purple :orange :gray :white :black]]
    (let [o (gen/emit-file
              (reader/parse
                (str "(indicator \"T\" :overlay true)\n"
                     "(plot close :color " (name c) ")")))]
      (is (str/includes? o (str "color=color." (name c)))
          (str "Failed for color " c)))))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: Additional Indicator Tests
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-wma
  (is (= (gen/expr->pine '(wma 14)) "ta.wma(close, 14)"))
  (is (= (gen/expr->pine '(wma high 14)) "ta.wma(high, 14)")))

(deftest gen-hma
  (is (= (gen/expr->pine '(hma 20)) "ta.hma(close, 20)")))

(deftest gen-vwma
  (is (= (gen/expr->pine '(vwma 14)) "ta.vwma(close, 14)")))

(deftest gen-alma
  (is (= (gen/expr->pine '(alma 10 6 0.85)) "ta.alma(close, 10, 6, 0.85)")))

(deftest gen-supertrend
  (is (= (gen/expr->pine '(supertrend 3 10)) "ta.supertrend(3, 10)")))

(deftest gen-sar
  (is (= (gen/expr->pine '(sar 0.02 0.2)) "ta.sar(0.02, 0.2)")))

(deftest gen-stdev
  (is (= (gen/expr->pine '(stdev 20)) "ta.stdev(close, 20)")))

(deftest gen-mfi
  (is (= (gen/expr->pine '(mfi hlc3 14)) "ta.mfi(hlc3, 14)"))
  (is (= (gen/expr->pine '(mfi close 14)) "ta.mfi(close, 14)")))

(deftest gen-cci
  (is (= (gen/expr->pine '(cci close 20)) "ta.cci(close, 20)"))
  (is (= (gen/expr->pine '(cci high 14)) "ta.cci(high, 14)")))

(deftest gen-obv
  (is (= (gen/expr->pine '(obv)) "ta.obv")))

(deftest gen-linreg
  (is (= (gen/expr->pine '(linreg 20)) "ta.linreg(close, 20)")))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: Strategy Exit, Order, Cancel
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-strategy-exit
  (is (= (gen/expr->pine '(exit "X" :from "E" :loss 100 :profit 200))
         "strategy.exit(\"X\", from_entry=\"E\", loss=100, profit=200)"))
  (is (= (gen/expr->pine '(exit "T" :from "E" :trail 50 :trail-offset 10))
         "strategy.exit(\"T\", from_entry=\"E\", trail_points=50, trail_offset=10)")))

(deftest gen-strategy-order
  ;; Order uses :direction + keyword args; qty is not extracted from positional
  (is (= (gen/expr->pine '(order "O" :long :limit 50.25))
         "strategy.order(\"O\", strategy.long, limit=50.25)")))

(deftest gen-strategy-cancel
  (is (= (gen/expr->pine '(cancel "O")) "strategy.cancel(\"O\")")))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: Barcolor Unconditional
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-barcolor-unconditional
  (let [o (gen/expr->pine '(barcolor :color green))]
    (is (str/includes? o "barcolor("))
    (is (str/includes? o "color.green"))))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: Fill
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-fill
  (let [o (gen/expr->pine '(fill p1 p2 :color blue :alpha 90))]
    (is (str/includes? o "fill"))
    (is (str/includes? o "color.new"))))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: Defvar, Defvarip, Set!
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-defvar
  (is (= (gen/expr->pine '(defvar count 0)) "var count = 0"))
  (is (= (gen/expr->pine '(defvar total 0.0)) "var total = 0.0")))

(deftest gen-defvarip
  (is (= (gen/expr->pine '(defvarip high-val 0.0)) "varip high_val = 0.0")))

(deftest gen-set!
  (is (= (gen/expr->pine '(set! count (+ count 1))) "count := count + 1"))
  (is (= (gen/expr->pine '(set! total (- total 1))) "total := total - 1"))
  (is (= (gen/expr->pine '(set! running-total (+ running-total close)))
         "running_total := running_total + close")))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: If/Else Multi-Branch
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-if-else-simple
  (let [o (gen/expr->pine '(if (> x 0) (long "E") :else (close)))]
    (is (str/includes? o "if"))
    (is (str/includes? o "else"))))

(deftest gen-if-elseif-else
  (let [o (gen/expr->pine '(if (> x 70) (short) (< x 30) (long) :else (close)))]
    (is (str/includes? o "if"))
    (is (str/includes? o "else if"))
    (is (str/includes? o "else"))))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: For, While, Switch
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-for-loop
  (let [o (gen/expr->pine '(for [i (range 1 5)] (set! sum (+ sum (close i)))))]
    (is (str/includes? o "for"))
    (is (str/includes? o "="))
    (is (str/includes? o "to"))))

(deftest gen-while-loop
  (let [o (gen/expr->pine '(while (< i 10) (set! i (+ i 1))))]
    (is (str/includes? o "while"))
    (is (str/includes? o "i :="))))

(deftest gen-switch
  (let [o (gen/expr->pine '(switch regime 0 (long "TREND") 1 (short "MR") :else (close "X")))]
    (is (str/includes? o "switch"))
    (is (str/includes? o "=>"))))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: Highest, Lowest, Highestbars, Lowestbars
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-highest-lowest
  (is (= (gen/expr->pine '(highest high 20)) "ta.highest(high, 20)"))
  (is (= (gen/expr->pine '(highest close 10)) "ta.highest(close, 10)"))
  (is (= (gen/expr->pine '(lowest low 20)) "ta.lowest(low, 20)"))
  (is (= (gen/expr->pine '(highestbars high 20)) "ta.highestbars(high, 20)"))
  (is (= (gen/expr->pine '(lowestbars low 14)) "ta.lowestbars(low, 14)")))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: Symbol references in indicator period args
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-indicator-with-symbol-period
  ;; Symbols in period position are converted to snake_case
  (is (= (gen/expr->pine '(sma close my-period)) "ta.sma(close, my_period)"))
  (is (= (gen/expr->pine '(highest high my-period)) "ta.highest(high, my_period)")))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: Math scalar functions
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-math-scalars
  (is (= (gen/expr->pine '(log close)) "math.log(close)"))
  (is (= (gen/expr->pine '(exp 2)) "math.exp(2)"))
  (is (= (gen/expr->pine '(sqrt x)) "math.sqrt(x)"))
  (is (= (gen/expr->pine '(abs -5)) "math.abs(-5)"))
  (is (= (gen/expr->pine '(ceil 3.2)) "math.ceil(3.2)"))
  (is (= (gen/expr->pine '(floor 3.8)) "math.floor(3.8)"))
  (is (= (gen/expr->pine '(pow 2 3)) "math.pow(2, 3)"))
  (is (= (gen/expr->pine '(min a b)) "math.min(a, b)"))
  (is (= (gen/expr->pine '(max a b)) "math.max(a, b)"))
  (is (= (gen/expr->pine '(sign -5)) "math.sign(-5)")))

;; ═══════════════════════════════════════════════════════════════════
;; Property-Based: Random period tests for additional indicators
;; ═══════════════════════════════════════════════════════════════════

(deftest property-random-wma-periods
  (doseq [p (take 20 (repeatedly #(+ 2 (rand-int 98))))]
    (let [o (gen/expr->pine (list 'wma p))]
      (is (re-find #"^ta\.wma\(close, \d+\)$" o)
          (str "Failed wma period " p)))))

(deftest property-random-hma-periods
  (doseq [p (take 20 (repeatedly #(+ 2 (rand-int 98))))]
    (let [o (gen/expr->pine (list 'hma p))]
      (is (re-find #"^ta\.hma\(close, \d+\)$" o)
          (str "Failed hma period " p)))))

(deftest property-random-stdev-periods
  (doseq [p (take 20 (repeatedly #(+ 2 (rand-int 98))))]
    (let [o (gen/expr->pine (list 'stdev p))]
      (is (re-find #"^ta\.stdev\(close, \d+\)$" o)
          (str "Failed stdev period " p)))))

(deftest property-random-atr-variants
  (doseq [p (take 20 (repeatedly #(+ 2 (rand-int 98))))]
    (let [o (gen/expr->pine (list 'atr p))]
      (is (re-find #"^ta\.atr\(\d+\)$" o)
          (str "Failed atr period " p)))))

;; ═══════════════════════════════════════════════════════════════════
;; Coverage: emit-file integration with all construct types
;; ═══════════════════════════════════════════════════════════════════

(deftest gen-emit-file-with-defvar-on-bar
  (let [o (gen/emit-file (reader/parse "
(strategy \"T\" :default-qty 100)
(defvar count 0)
(def fast (sma 50))
(on-bar
  (when (> close fast) (long \"E\"))
  (set! count (+ count 1)))
(exit \"X\" :from \"E\" :loss 100)
(plot fast \"F\")"))]
    (is (str/includes? o "var count"))
    (is (str/includes? o "strategy.exit"))
    (is (str/includes? o "count :="))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.core-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
