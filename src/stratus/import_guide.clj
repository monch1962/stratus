(ns stratus.import-guide
  "Guided conversion walkthrough for Pine Script -> Stratus."
  (:require [clojure.string :as str]))

(def conversion-table
  "Common Pine Script patterns and their Stratus equivalents."
  [["Declaration" "//@version=6\nindicator(\"My Title\")" "(indicator \"My Title\")"]
   ["Strategy" "//@version=6\nstrategy(\"My Strat\", overlay=true)" "(strategy \"My Strat\" :overlay true)"]
   ["SMA" "ta.sma(src, 14)" "(sma src 14) or (sma 14)"]
   ["EMA" "ta.ema(src, 14)" "(ema src 14) or (ema 14)"]
   ["RSI" "ta.rsi(src, 14)" "(rsi src 14) or (rsi 14)"]
   ["MACD" "ta.macd(close, 12, 26, 9)" "(macd :fast 12 :slow 26 :signal 9)"]
   ["BB" "ta.bb(close, 20, 2)" "(bb 20 2)"]
   ["Stoch" "ta.stoch(close, high, low, 14)" "(stoch 14)"]
   ["ATR" "ta.atr(14)" "(atr 14)"]
   ["Plot" "plot(myVar, \"Title\", color.red, linewidth=2)" "(plot myVar \"Title\" :color red :linewidth 2)"]
   ["Hline" "hline(50)" "(hline 50)"]
   ["If" "if (cond)\n    doSomething" "(if cond (do-something))"]
   ["If/else" "if (cond)\n    a\nelse\n    b" "(if cond a :else b)"]
   ["For loop" "for i = 1 to 10\n    sum += i" "(for [i 1 10] (set! sum (+ sum i)))"]
   ["Entry long" "strategy.entry(\"id\", strategy.long)" "(long \"id\")"]
   ["Exit" "strategy.exit(\"id\", from_entry=\"e\")" "(exit \"id\" :from \"e\")"]
   ["Input int" "input.int(14, \"Period\")" "(input-int \"Period\" :def 14)"]
   ["Color RGB" "color.rgb(255, 0, 0)" "(rgb 255 0 0)"]
   ["Security" "request.security(sym, tf, expr)" "(security tf expr)"]
   ["Var" "var float x = 0" "(defvar x 0)"]])

(defn print-guide []
  (println "Pine Script -> Stratus Conversion Guide")
  (println "=======================================")
  (println)
  (println "1. Create a new .stratus file")
  (println "2. Replace the declaration line")
  (println "3. Convert each construct using the table below")
  (println "4. Run: ./stratus compile your.stratus")
  (println "5. Fix any validation warnings")
  (println "6. Paste the output into TradingView")
  (println)
  (println "Pattern Reference:")
  (println)
  (doseq [[category pine stratus] conversion-table]
    (println category ":")
    (println "  Pine:    " pine)
    (println "  Stratus: " stratus)
    (println))
  (println "---")
  (println "Clojure Features (not in Pine):")
  (println "  (let [a (sma 14)] (plot a))         -- local bindings")
  (println "  (let [[m s h] (macd)] ...)          -- destructuring")
  (println "  (-> x (sma 14) (ema 5))             -- thread-first")
  (println "  (some-> x (sma 14))                 -- nil-safe thread")
  (println "  (for [len [10 20]] (sma len))       -- comprehension")
  (println "  (defn f ([x] body) ([x y] body))    -- multi-arity")
  (println)
  (println "Commands:")
  (println "  ./stratus check file.stratus       Validate only")
  (println "  ./stratus compile file.stratus     Compile to Pine")
  (println "  ./stratus compile -w file           Watch & recompile")
  (println "  ./stratus simulate file [--data]    Backtest")
  (println "  ./stratus repl                      Interactive REPL")
  (println "  ./stratus list                      Show all constructs"))

(defn print-syntax-guide []
  (println "Quick Syntax Rules:")
  (println "  Function calls use prefix: (fn arg1 arg2)")
  (println "  Keywords start with :: :color red")
  (println "  Strings are double-quoted: \"Title\"")
  (println "  Semicolons start comments: ;; comment"))
