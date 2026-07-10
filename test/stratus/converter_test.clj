(ns stratus.converter-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]))

(deftest convert-strategy-header
  (is (str/includes? (imp/convert "strategy(\"Test\", overlay=true)")
                     "(strategy \"Test\""))
  (is (str/includes? (imp/convert "strategy(\"Test\", overlay=true)")
                     ":overlay true"))
  (is (str/includes? (imp/convert "indicator(\"RSI\", precision=2)")
                     "(indicator \"RSI\"")))

(deftest convert-var-bindings
  (let [r (imp/convert "fast = ta.sma(close, 14)")]
    (is (str/includes? r "(def fast"))
    (is (str/includes? r "(sma")))
  (let [r (imp/convert "rsiVal = ta.rsi(close, 14)")]
    (is (str/includes? r "rsi-val"))))

(deftest convert-persistent-state
  (is (str/includes? (imp/convert "var count = 0") "(defvar count"))
  (is (str/includes? (imp/convert "varip highVal = 0.0") "(defvarip"))
  (is (str/includes? (imp/convert "count := count + 1") "(set! count")))

(deftest convert-price-refs
  (let [r (imp/convert "x = close")]
    (is (str/includes? r "close")))
  (let [r (imp/convert "x = close[1]")]
    (is (str/includes? r "(close 1)"))))

(deftest convert-strategy-actions
  (is (str/includes? (imp/convert "strategy.entry(\"E\", strategy.long)")
                     "(long \"E\")"))
  (is (str/includes? (imp/convert "strategy.entry(\"E\", strategy.short)")
                     "(short \"E\")"))
  (is (str/includes? (imp/convert "strategy.exit(\"X\", from=\"ENTER\", loss=100, profit=200)")
                     "(exit \"X\"")))

(deftest convert-plotting
  (let [r (imp/convert "plot(fast, \"Fast MA\", color=color.blue, linewidth=2)")]
    (is (str/includes? r "(plot fast"))
    (is (str/includes? r "color=blue"))))

(deftest convert-full-strategy
  (let [pine "
//@version=6
strategy(\"Golden Cross\", overlay=true)
fast = ta.sma(close, 50)
slow = ta.sma(close, 200)
if ta.cross(fast, slow) and fast > slow
    strategy.entry(\"GO\", strategy.long)
if ta.cross(fast, slow) and fast < slow
    strategy.close(\"GO\")
plot(fast, \"Fast\", color=color.blue, linewidth=2)
plot(slow, \"Slow\", color=color.red, linewidth=2)"
        result (imp/convert pine)]
  (is (str/includes? result "strategy"))
  (is (str/includes? result "(def fast"))
  (is (str/includes? result "crosses-above"))
  (is (str/includes? result "(long \"GO\")"))
  (is (str/includes? result "(plot fast"))))

(deftest convert-indicator-macd
  (let [r (imp/convert "macdLine = ta.macd(close, 12, 26, 9)")]
    (is (str/includes? r "macd"))))

(deftest convert-with-unknown-flagged
  (let [result (imp/convert "someObscureFunction(x, y)")]
    (is (str/includes? result "WARN"))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.converter-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
