(ns stratus.core
  (:require [stratus.reader :as reader]
            [stratus.inliner :as inliner]
            [stratus.expander :as expander]
            [stratus.generator :as gen]
            [stratus.import-guide :as guide]
            [stratus.validator :as validator]
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
          ast    (expander/expand-all ast)
          ast    (inliner/expand-all ast)
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
  (println "                          [-w|--watch]      Recompile on file save")
  (println "  check   <file.stratus>                    Validate without compiling")
  (println "  export  <symbol>            [--market <m>]     Export OHLCV to CSV")
  (println "                          [--interval <i>]      (default: D)")
  (println "                          [--format <fmt>]      csv or json (default: csv)")
  (println "                          [-o <file>]           Output file")
  (println "                          [--dry-run]           Print to stdout")
  (println "  import                           Show conversion guide")
  (println "  simulate <file.stratus> [--bars N] [--data file.csv]  Run simulation")
  (println "  repl                                Interactive DSL evaluator")
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
  (let [all-keys (set (concat (keys gen/pine-simple-map)
                              [:strategy :indicator :library :def :defvar :defvarip :set!
                               :defn :definline :comment
                               :sma :ema :rsi :wma :vwma :hma :macd :adx :stoch :bb :atr
                               :cross :tr :range :roc :swma :rma :tema :dema :smma
                               :supertrend :sar :vwap :stdev :cci :mfi :obv :linreg :kc :alma
                               :highest :lowest :cum :highestbars :lowestbars :sum :avg
                               :fixnan :valuewhen :correlation :covariance :median :mode
                               :percentile :in-session :cmo :wad :mama :cog :vwmacd
                               :crossover :crossunder
                               :crosses-above :crosses-below :rising :falling
                               :na :nz :iff :change :mom
                               :and :or :not :> :< :>= :<= := :+ :- :* :/ :%
                               :long :short :close :close-all :reverse :exit :order :cancel
                               :plot :plotshape :plotchar :plotarrow :hline :bgcolor
                               :barcolor :fill :alertcondition
                               :color :rgb :from-gradient :tostring
                               :input-int :input-float :input-bool :input-string
                               :input-color :input-source :input-symbol :input-timeframe
                               :input-price :input-session
                               :security :security-lower-tf :financial :random :seed
                               :timestamp :ticker.modify
                               :if :when :do :for :while :switch :on-bar
                               :line.new :line.delete :label.new :label.delete
                               :box.new :box.delete :polygon.new :polygon.delete
                               :table.new :table-cell :map.new :matrix.new
                               :array-int :array-float :array-bool :array-string
                               :array-color :array-line :array-label :array-box :array-table
                               :array.push :array.pop :array.size :array.get :array.set
                               :array.sort :array.fill :array.reverse
                               :push :pop :size :get :set :sort
                               :multiset :export :definline :defmacro :comment
                               :pi :tau :e :phi :round :pow :min :max
                               :time.close :time.tradingday
                               :chart.point.now :chart.point.from-index
                               :polygon.new :polygon.delete
                               :let :-> :->> :some-> :some->> :cond-> :as->]))
        categories [["Declarations" [:strategy :indicator :library :def :defvar :defvarip :set! :defn :definline :defmacro :export :comment]]
                    ["Price Built-ins" [:close :high :low :open :volume :hl2 :hlc3 :ohlc4
                                       :time :time.close :time.tradingday :bar-index
                                       :ticker :timeframe :year :month :dayofweek :dayofmonth
                                       :hour :minute :second :weekofyear :quarter
                                       :bar-confirmed :bar-first :bar-last :bar-new
                                       :bar-realtime :bar-history
                                       :mintick :pointvalue :sym-session :sym-description
                                       :sym-type :currency :base-currency :price-scale
                                       :min-move :sym-sector :sym-industry
                                       :dividends :splits :earnings
                                       :position-size :position-avg-price :open-trades
                                       :equity :net-profit :open-profit :win-trades
                                       :loss-trades :closed-trades :gross-profit :gross-loss
                                       :max-drawdown :max-runup :allow-entry-in
                                       :max-intraday-orders]]
                    ["Indicators" [:sma :ema :rsi :wma :vwma :hma :macd :adx :stoch :bb :atr
                                  :cross :tr :range :roc :swma :rma :tema :dema :smma
                                  :supertrend :sar :vwap :stdev :cci :mfi :obv :linreg
                                  :kc :alma :highest :lowest :cum :highestbars :lowestbars
                                  :sum :avg :fixnan :valuewhen :cmo :wad :mama :cog :vwmacd
                                  :correlation :covariance :median :mode :percentile
                                  :crossover :crossunder :in-session
                                  :highest :lowest :cum :highestbars :lowestbars]]
                    ["Conditions" [:crosses-above :crosses-below :rising :falling
                                  :na :nz :iff :change :mom]]
                    ["Logic / Math" [:and :or :not :> :< :>= :<= := :+ :- :* :/ :%
                                    :log :log10 :exp :sqrt :abs :ceil :floor :sign
                                    :round :pow :min :max :pi :tau :e :phi
                                    :timestamp :int :float :str.tonumber]]
                    ["Actions" [:long :short :close :close-all :reverse :exit :order :cancel]]
                    ["Plotting / Alerts" [:plot :plotshape :plotchar :plotarrow :hline
                                         :bgcolor :barcolor :fill :alertcondition
                                         :color :rgb :from-gradient :tostring]]
                    ["Inputs" [:input-int :input-float :input-bool :input-string
                              :input-color :input-source :input-symbol :input-timeframe
                              :input-price :input-session]]
                    ["Control Flow" [:if :when :do :for :while :switch :on-bar]]
                    ["Arrays" [:array-int :array-float :array-bool :array-string
                              :array-color :array-line :array-label :array-box :array-table
                              :array.push :array.pop :array.size :array.get :array.set
                              :array.sort :array.fill :array.reverse
                              :push :pop :size :get :set :sort
                              :array.insert :array.remove :array.clear :array.concat
                              :array.slice :array.copy :array.shift :array.unshift
                              :array.includes :array.indexof :array.lastindexof
                              :array.min :array.max :array.avg :array.median
                              :array.sum :array.stdev :array.mode :array.range]]
                    ["Tables" [:table.new :table-cell :table.clear :table.delete
                              :table.merge-cells :table.set-position :table.set-size
                              :table.set-color :table.set-bgcolor :table.set-border-color
                              :table.set-border-width :table.get-location :table.get-size]]
                    ["Strings" [:str.contains :str.length :str.split :str.lower :str.upper
                               :str.replace-all :str.substring :str.substr :str.startswith
                               :str.endswith :tostring :str.tonumber]]
                    ["Drawing" [:line.new :line.delete :line.set-color :line.set-width
                               :line.set-extend :line.set-style :line.set-xloc
                               :line.get-x1 :line.get-x2 :line.get-y1 :line.get-y2 :line.get-price
                               :label.new :label.delete :label.set-color :label.set-text
                               :label.set-x :label.set-y :label.set-style
                               :label.set-textcolor :label.set-textalign :label.set-size
                               :label.get-x :label.get-y :label.get-text
                               :box.new :box.delete :box.set-color :box.set-border-color
                               :box.set-width :box.set-extend :box.set-style
                               :box.get-left :box.get-top :box.get-right :box.get-bottom
                               :polygon.new :polygon.delete :polygon.set-fillcolor
                               :polygon.set-bordercolor :polygon.set-borderwidth
                               :polygon.get-fillcolor :polygon.get-bordercolor :polygon.get-borderwidth]]
                    ["Matrix / Map" [:matrix.new :matrix.float :matrix.int :matrix.bool
                                    :matrix.string :matrix.color :matrix.line
                                    :matrix.label :matrix.box :matrix.table
                                    :matrix.rows :matrix.columns :matrix.size :matrix.get
                                    :matrix.set :matrix.row :matrix.col :matrix.sum
                                    :matrix.transpose :matrix.multiply :matrix.inv
                                    :matrix.fill :matrix.det :matrix.rank :matrix.pinv
                                    :map.new :map.put :map.get :map.delete :map.contains
                                    :map.keys :map.values :map.size]]
                    ["Ticker / Security" [:ticker.heikinashi :ticker.renko :ticker.linebreak
                                        :ticker.kagi :ticker.pnf :ticker.range :ticker.new
                                        :ticker.modify
                                        :security :security-lower-tf :financial :random :seed
                                        :chart.point.now :chart.point.from-index]]
                    ["Colors" [:color.r :color.g :color.b :color.t]]
                    ["Order" [:order.entry-condition :order.exit-condition
                             :order.filled-condition :order.filled :order.entry-id]]
                    ["Type Predicates" [:series :array :string :int :float :bool]]
                    ["Misc" [:multiset :comment :definline :let :-> :->> :some-> :some->> :cond-> :as->]]]]
    (println (str "Available constructs (" (count all-keys) " total):\n"))
    (doseq [[cat-label cat-keys] categories]
      (let [present (sort (filter all-keys cat-keys))]
        (when (seq present)
          (println "  " cat-label ":")
          (doseq [k present]
            (println (str "    " (name k))))
          (println))))))

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
        data-idx  (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["--data"])
        source    (slurp in-path)
        forms     (reader/parse source)
        body      (filter #(not (and (list? %) (#{:strategy :indicator :library} (first %)))) forms)
        strategy-do (cons 'do body)
        orders    (atom [])
        bars      (if (and data-idx (< (inc data-idx) (count args-vec)))
                    (sim/load-csv (slurp (nth args-vec (inc data-idx))))
                    (make-sim-bars n-bars))
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
                    (let [args-vec (vec rest-args)
                          watch? (or (some #{"-w" "--watch"} args-vec))]
                      (if watch?
                        (let [out-idx (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["-o" "--output"])
                              out-path (when (and out-idx (< (inc out-idx) (count args-vec))) (nth args-vec (inc out-idx)))
                              clip? (some #{"-c" "--clip"} args-vec)]
                          (println "Watching" in-path "(recompile on save)...")
                          (watch-file in-path out-path clip?))
                        (compile-strategy in-path args-vec)))
                    (println "Usage: bb -m stratus.core compile <file.stratus> [-o file.pine] [-c] [-w|--watch]")))

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

      "check" (let [in-path (first rest-args)]
                (if in-path
                  (try
                    (let [source (slurp in-path)
                          forms (reader/parse source)]
                      (validator/report forms source))
                    (catch java.io.FileNotFoundException e
                      (println "✕ File not found:" in-path)))
                  (println "Usage: bb -m stratus.core check <file.stratus>")))

      "import" (guide/print-guide)

      "simulate" (let [in-path (first rest-args)]
                   (if in-path
                     (run-simulation in-path (vec rest-args))
                     (println "Usage: bb -m stratus.core simulate <file.stratus> [--bars N] [--data file.csv]")))

      "repl" (println "Stratus REPL — type (.q) to quit")
              (loop []
                (print "stratus> ")
                (flush)
                (let [line (read-line)]
                  (when (and line (not= line "(.q)"))
                    (try
                      (let [forms (reader/parse line)
                            expanded (inliner/expand-all forms)
                            pine (gen/emit-file expanded)]
                        (println (str/trim pine)))
                      (catch Exception e
                        (println "✕" (.getMessage e))))
                    (recur))))

      (usage))))
