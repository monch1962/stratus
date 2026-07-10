(ns stratus.core
  (:require [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.constructs :as constructs]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:gen-class))

;; ─── CLI Entry Point ─────────────────────────────────────────────────

(defn usage
  "Print usage information."
  []
  (println "Stratus v0.1 — LISP-syntax Strategy DSL for Pine Script")
  (println)
  (println "Usage:")
  (println "  bb stratus compile <file.stratus>     Compile DSL to Pine Script")
  (println "  bb stratus repl                        Start interactive REPL")
  (println "  bb stratus test <file.stratus>        Run strategy against sample data")
  (println "  bb stratus list                        List available constructs")
  (println)
  (println "Examples:")
  (println "  bb stratus compile examples/golden-cross.stratus")
  (println "  bb stratus compile examples/golden-cross.stratus -o output.pine"))

(defn compile-strategy
  "Compile a .stratus file to Pine Script."
  [in-path out-path]
  (let [source     (slurp in-path)
        ast        (reader/parse source)
        pine-code  (gen/emit-file ast)]
    (if out-path
      (do (spit out-path pine-code)
          (println "✓ Compiled" in-path "→" out-path))
      (println pine-code))))

(defn list-constructs
  "List all available DSL constructs."
  []
  (println "Available constructs:\n")
  (println "  Declarations:")
  (println "    strategy    Strategy definition")
  (println "    indicator   Indicator definition")
  (println "    def         Variable binding")
  (println)
  (println "  Indicators (price source defaults to close):")
  (println "    sma         Simple Moving Average         sma(close, 14)")
  (println "    ema         Exponential Moving Average    ema(close, 14)")
  (println "    rsi         Relative Strength Index       rsi(close, 14)")
  (println "    macd        MACD                         macd(close)")
  (println "    adx         Average Directional Index     adx(14)")
  (println "    stoch       Stochastic                    stoch(14, 3, 3)")
  (println "    bb          Bollinger Bands               bb(close, 20, 2)")
  (println "    atr         Average True Range            atr(14)")
  (println)
  (println "  Conditions:")
  (println "    crosses-above  Series crosses above")
  (println "    crosses-below  Series crosses below")
  (println "    rising         Value is rising")
  (println "    falling        Value is falling")
  (println)
  (println "  Logic:")
  (println "    and/or/not   Logical operators")
  (println "    when         Conditional execution")
  (println)
  (println "  Actions:")
  (println "    long         Enter long position")
  (println "    short        Enter short position")
  (println "    close        Close position")
  (println)
  (println "  Plotting:")
  (println "    plot         Line plot")
  (println "    plotshape    Shape marker")
  (println "    hline        Horizontal line")
  (println "    bgcolor      Background color")
  (println "    barcolor     Bar color")
  (println)
  (println "  Alerts:")
  (println "    alertcondition  Define alert")
  (println)
  (println "  Built-ins: close, high, low, open, volume, hl2, hlc3, ohlc4"))

(defn -main
  "CLI entry point."
  [& args]
  (case (first args)
    "compile" (let [in   (second args)
                    out  (some #(when (#{"-o" "--output"} %)
                                  (nth args (inc (.indexOf (vec args) %))))
                               ["-o" "--output"])]
                (if in
                  (compile-strategy in out)
                  (println "Usage: bb stratus compile <file.stratus> [-o output.pine]")))
    "repl"    (println "REPL mode not yet implemented")
    "test"    (println "Test mode not yet implemented")
    "list"    (list-constructs)
    (usage)))
