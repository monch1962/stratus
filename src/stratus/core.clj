(ns stratus.core
  (:require [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.constructs :as constructs]
            [stratus.importer :as imp]
            [stratus.simulator :as sim]
            [stratus.exporter :as exp]
            [clojure.string :as str])
  (:gen-class))

(declare safe-compile run-simulation)

;; ─── Cross-platform clipboard ───────────────────────────────────────

(defn- copy-to-clipboard
  "Copy text to system clipboard via platform CLI tool."
  [text]
  (try
    (let [cmd (let [os (System/getProperty "os.name")]
                (cond (.contains os "Mac")     ["pbcopy"]
                      (.contains os "Linux")   (if (zero? (.waitFor (.exec (Runtime/getRuntime) (into-array String ["which" "wl-copy"]))))
                                                 ["wl-copy"] ["xclip" "-selection" "clipboard"])
                      :else nil))]
      (if cmd
        (let [proc (.exec (Runtime/getRuntime) (into-array String cmd))]
          (with-open [w (.getOutputStream proc)]
            (.write w (.getBytes text))
            (.flush w))
          (.waitFor proc) (zero? (.exitValue proc)))
        false))
    (catch Exception _ false)))

;; ─── File watcher ───────────────────────────────────────────────────

(defn- watch-file
  "Watch a .stratus file for changes and recompile on save."
  [in-path out-path clip?]
  (let [file (java.io.File. in-path)]
    (println "👀 Watching" in-path "for changes...")
    (println "   Press Ctrl+C to stop")
    (loop [mtime (.lastModified file)]
      (Thread/sleep 800)
      (let [new-mtime (.lastModified file)]
        (if (> new-mtime mtime)
          (let [pine-code (do (Thread/sleep 100)
                              (safe-compile in-path))
                ts (java.time.LocalTime/now)]
            (when pine-code
              (if out-path
                (do (spit out-path pine-code)
                    (println (str "⏱ " ts) "Compiled" in-path "→" out-path))
                (println (str "⏱ " ts)))
              (when clip?
                (if (copy-to-clipboard pine-code)
                  (println "📋 Copied to clipboard")
                  (println "⚠️  Clipboard unavailable"))))
            (recur new-mtime))
          (recur mtime))))))

;; ─── Compilation with friendly errors ──────────────────────────────

(defn safe-compile
  "Compile a .stratus file, returning Pine code or printing a friendly error."
  [in-path]
  (try
    (let [source (slurp in-path)
          ast    (reader/parse source)
          pine   (gen/emit-file ast)]
      pine)
    (catch java.io.FileNotFoundException e
      (println "✕ File not found:" in-path)
      (println "  Check the path and try again.")
      nil)
    (catch Exception e
      (let [msg (.getMessage e)]
        (cond (str/includes? msg "No method")
              (println "✕ Unknown construct:" (second (re-find #":(\w+)" msg))
                       "\n  Check spelling or run `stratus list` for available constructs.")
              (str/includes? msg "EOF while reading")
              (println "✕ Unmatched parenthesis in" in-path
                       "\n  Every ( must have a matching ). Check your brackets.")
              (str/includes? msg "IndexOutOfBounds")
              (println "✕ Wrong number of arguments in" in-path
                       "\n  A construct is missing required arguments.")
              :else
              (println "✕ Compilation error:" msg))))))

;; ─── Scaffold generator ────────────────────────────────────────────

(def scaffolds
  {:strategy
   (str "(strategy \"MyStrategy\" :default-qty 100)\n\n"
        "(def fast (sma 50))\n(def slow (sma 200))\n\n"
        "(on-bar\n"
        "  (when (crosses-above fast slow) (long \"ENTER\"))\n"
        "  (when (crosses-below fast slow) (close \"EXIT\")))\n\n"
        "(plot fast \"Fast MA\" :color blue :linewidth 2)\n"
        "(plot slow \"Slow MA\" :color red :linewidth 2)\n")

   :indicator
   (str "(indicator \"MyIndicator\" :overlay false :precision 2)\n\n"
        "(input-int \"Period\" :def 14)\n"
        "(input-source \"Source\" :def close)\n\n"
        "(def val (rsi 14))\n\n"
        "(plot val \"RSI\" :color blue :linewidth 2)\n"
        "(hline 70 \"Overbought\" :color red :linestyle dashed)\n"
        "(hline 30 \"Oversold\" :color green :linestyle dashed)\n")

   :library
   (str "(library \"MyLibrary\" :overlay true)\n\n"
        "(defn my-sma [src n] (sma src n))\n"
        "(export my-sma)\n\n"
        "(defn my-ema [src n] (ema src n))\n"
        "(export my-ema)\n")})

(defn scaffold
  "Generate a scaffold .stratus file."
  [type-str name]
  (let [type (keyword type-str)]
    (if-let [template (get scaffolds type)]
      (let [fname (str (or name (str "my-" type-str)) ".stratus")
            content (str/replace template "MyS" (or name "MyS"))]
        (spit fname content)
        (println "✓ Created" fname)
        (println "  Compile: bb -m stratus.core compile" fname "--clip"))
      (println "✕ Unknown type:" type-str
               "\n  Available: strategy, indicator, library"))))

;; ─── CLI ───────────────────────────────────────────────────────────

(defn usage
  []
  (println "Stratus — LISP-syntax Strategy DSL for Pine Script")
  (println)
  (println "Usage:")
  (println "  compile <file.stratus>  [-o <file.pine>]  Compile to Pine Script")
  (println "                          [-c|--clip]       Copy to clipboard")
  (println "  export  <symbol>            [--market <m>]     Export OHLCV to CSV")
  (println "                          [--interval <i>]      (default: D)")
  (println "                          [--format <fmt>]      csv or json (default: csv)")
  (println "                          [-o <file>]           Output file")
  (println "                          [--dry-run]           Print to stdout")
  (println "  import  <file.pine>     [-o <file.stratus>]  Convert Pine to Stratus")
  (println "  simulate <file.stratus> [--bars N]        Simulate strategy")
  (println "  watch   <file.stratus>  [-o <file.pine>]  Watch for changes")
  (println "                          [-c|--clip]       Auto-copy on save")
  (println "  new     <type> [name]                    Generate scaffold")
  (println "  list                                      List constructs")
  (println)
  (println "Types: strategy, indicator, library")
  (println)
  (println "Quick start:")
  (println "  ./stratus import my-strategy.pine -o my.stratus    Convert existing")
  (println "  ./stratus compile my.stratus --clip               Compile to clipboard")
  (println "  ./stratus simulate my.stratus                     Backtest strategy")
  (println "  ./stratus export AAPL --interval W --format csv   Export weekly data")
  (println "  ./stratus new strategy \"Breakout\"                 New from scratch"))

(defn compile-strategy
  [in-path args]
  (let [args-vec  (vec args)
        out-idx   (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["-o" "--output"])
        out-path  (when (and out-idx (< (inc out-idx) (count args-vec))) (nth args-vec (inc out-idx)))
        clip?     (some #{"-c" "--clip"} args-vec)
        pine-code (safe-compile in-path)]
    (when pine-code
      (if out-path
        (do (spit out-path pine-code)
            (println "✓ Compiled" in-path "→" out-path))
        (println pine-code))
      (when clip?
        (if (copy-to-clipboard pine-code)
          (println "📋 Copied to clipboard (Ctrl+V into TradingView)")
          (println "⚠️  Clipboard copy unavailable. Install xclip (Linux) or use macOS."))))))

(defn list-constructs
  []
  (println "Available constructs (" (count constructs/constructs) "total):\n")
  (doseq [[cat-label cat-sym] [["Declarations" :decl] ["Price Sources" :builtin]
                                ["Indicators" :indicator] ["Conditions" :condition]
                                ["Logic / Comparison" :logic] ["Actions" :action]
                                ["Plotting / Alerts" :plot] ["Inputs" :input]
                                ["Math & Stats" :stat] ["Control Flow" :control]
                                ["Drawing & Tables" :drawing]]]
    (let [cat-constructs (filter #(= (:category %) cat-sym) constructs/constructs)]
      (when (seq cat-constructs)
        (println "  " cat-label ":")
        (doseq [c cat-constructs]
          (println (str "    " (name (:name c)) "  " (or (:summary c) (:doc c) ""))))
        (println)))))

;; ─── Import ────────────────────────────────────────────────────────

(defn import-strategy
  "Convert a Pine Script file to Stratus DSL."
  [in-path args]
  (let [args-vec  (vec args)
        out-idx   (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["-o" "--output"])
        out-path  (or (and out-idx (< (inc out-idx) (count args-vec)) (nth args-vec (inc out-idx)))
                      (str/replace in-path #"\.pine$" ".stratus"))
        source    (slurp in-path)
        dsl       (imp/convert source)]
    (spit out-path dsl)
    (println "✓ Converted" in-path "→" out-path)))

;; ─── Simulate ──────────────────────────────────────────────────────

(defn make-sim-bars
  "Generate synthetic bars for simulation."
  [n & {:keys [trend volatility start]
        :or {trend 0.002, volatility 0.01, start 100.0}}]
  (let [rng (java.util.Random. 42)]
    (reductions
      (fn [prev _]
        (let [ret (+ trend (* volatility (.nextGaussian rng)))
              prev-close (:close prev)
              c (max 0.5 (* prev-close (Math/exp ret)))
              h (max c (* c (+ 1 (* volatility (.nextGaussian rng) 0.3))))
              l (min c (* c (- 1 (* volatility (.nextGaussian rng) 0.3))))
              o (+ l (* (- h l) (.nextFloat rng)))]
          {:open o, :high h, :low l, :close c, :volume (int (+ 1000 (* 500 (.nextGaussian rng))))}))
      {:open start, :high start, :low start, :close start, :volume 1000}
      (range (dec n)))))

(defn run-simulation
  "Run a .stratus file through the simulator."
  [in-path args]
  (let [args-vec  (vec args)
        n-idx     (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["--bars"])
        n-bars    (if (and n-idx (< (inc n-idx) (count args-vec)))
                    (Integer/parseInt (nth args-vec (inc n-idx))) 300)
        source    (slurp in-path)
        forms     (reader/parse source)
        ;; Extract do-like from the parsed forms (skip strategy header)
        body      (filter #(not (and (list? %) (#{:strategy :indicator :library} (first %)))) forms)
        strategy-do (cons 'do body)
        orders    (atom [])
        bars      (make-sim-bars n-bars)
        result    (sim/simulate bars strategy-do
                    :on-bar (fn [state form]
                              (case (first form)
                                long  (swap! orders conj {:bar (:bar-count state), :action :buy})
                                short (swap! orders conj {:bar (:bar-count state), :action :sell})
                                close (swap! orders conj {:bar (:bar-count state), :action :sell})
                                nil))
                    :on-result (fn [state] state))]
    (println "┌──────────────────────────────────────┐")
    (println "│ Simulation Results                    │")
    (println "├──────────────────────────────────────┤")
    (println (str "│  File:         " (max 0 (- 36 (count in-path))) (apply str (repeat (- 37 (count in-path)) " ")) "│"))
    (println (str "│  Bars:         " n-bars (apply str (repeat (- 36 (count (str n-bars))) " ")) "│"))
    (println (str "│  Trades:       " (:total-trades result) (apply str (repeat (- 35 (count (str (:total-trades result)))) " ")) "│"))
    (println (str "│  Net P&L:      " (format "%.2f" (double (:net-profit result))) (apply str (repeat (- 35 (count (format "%.2f" (double (:net-profit result))))) " ")) "│"))
    (println "└──────────────────────────────────────┘")))

;; ─── Export ────────────────────────────────────────────────────────

(defn export-data
  "Export OHLCV data for a symbol from TradingView."
  [symbol args]
  (let [args-vec (vec args)
        mkt-idx  (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["--market"])
        int-idx  (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["--interval"])
        fmt-idx  (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["--format"])
        out-idx  (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["-o" "--output"])
        dry-run? (some #{"--dry-run"} args-vec)
        market   (if (and mkt-idx (< (inc mkt-idx) (count args-vec))) (nth args-vec (inc mkt-idx)))
        interval (if (and int-idx (< (inc int-idx) (count args-vec))) (nth args-vec (inc int-idx)))
        format   (or (and fmt-idx (< (inc fmt-idx) (count args-vec)) (nth args-vec (inc fmt-idx))) "csv")
        out-path (when (and out-idx (< (inc out-idx) (count args-vec))) (nth args-vec (inc out-idx)))
        url      (exp/build-url symbol
                   :market (or market "america")
                   :interval (or interval "D"))
        bars     (exp/fetch-ohlcv url)]
    (if bars
      (let [output ((if (= format "json") exp/format-json exp/format-csv) bars)]
        (if (or dry-run? (nil? out-path))
          (println output)
          (do (spit out-path output)
              (println "✓ Exported" (count bars) "bars for" symbol "→" out-path))))
      (println "✕ No data returned for" symbol
               "\n  Check the symbol and market. Try: --market crypto --exchange BINANCE"))))

(defn -main
  [& args]
  (let [cmd (first args)
        rest-args (rest args)]
    (case cmd
      "compile" (let [in-path (first rest-args)]
                  (if in-path
                    (compile-strategy in-path (vec rest-args))
                    (println "Usage: bb -m stratus.core compile <file.stratus> [-o file.pine] [-c]")))

      "export" (let [sym (first rest-args)]
                 (if sym
                   (export-data sym (rest rest-args))
                   (println "Usage: bb -m stratus.core export <symbol> [--market m] [--interval i] [--format csv|json] [-o file] [--dry-run]")))

      "watch" (let [in-path (first rest-args)]
                (if in-path
                  (let [out-idx   (some #(let [i (.indexOf (vec rest-args) %)] (when (>= i 0) i)) ["-o" "--output"])
                        out-path  (when (and out-idx (< (inc out-idx) (count rest-args))) (nth rest-args (inc out-idx)))
                        clip?     (some #{"-c" "--clip"} (vec rest-args))]
                    (watch-file in-path out-path clip?))
                  (println "Usage: bb -m stratus.core watch <file.stratus> [-o file.pine] [-c]")))

      "new" (let [type-str (first rest-args)
                  name     (second rest-args)]
              (if type-str
                (scaffold type-str name)
                (println "Usage: bb -m stratus.core new <type> [name]\n  Types: strategy, indicator, library")))

      "list" (list-constructs)

      "import" (let [in-path (first rest-args)]
                 (if in-path
                   (import-strategy in-path (vec rest-args))
                   (println "Usage: bb -m stratus.core import <file.pine> [-o file.stratus]")))

      "simulate" (let [in-path (first rest-args)]
                   (if in-path
                     (run-simulation in-path (vec rest-args))
                     (println "Usage: bb -m stratus.core simulate <file.stratus> [--bars N]")))

      (usage))))
