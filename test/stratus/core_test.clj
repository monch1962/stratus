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
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.core-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
