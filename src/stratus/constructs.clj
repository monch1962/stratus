(ns stratus.constructs
  "Definition of all supported DSL constructs.
   Each construct has:
   - :name       — keyword matching the DSL symbol
   - :category   — :indicator, :condition, :action, :plot, :logic, :decl, :builtin
   - :args       — vector of [name type default?] for documentation
   - :arity      — :fixed | :variadic
   - :returns    — description of return type
   - :pine-fn    — Pine Script function name or generator fn name
   - :doc        — human-readable description")

(def constructs
  "All supported DSL constructs in priority order."
  [
   ;; ─── Declarations ─────────────────────────────────────────────────
   {:name :strategy, :category :decl, :arity :block,
    :doc "Define a trading strategy with name and parameters"}
   {:name :indicator, :category :decl, :arity :block,
    :doc "Define an indicator with name and parameters"}
   {:name :library, :category :decl, :arity :block,
    :doc "Define a reusable library"}
   {:name :def, :category :decl, :arity :fixed,
    :doc "Bind a name to an expression"}

   ;; ─── Price Built-ins ──────────────────────────────────────────────
   {:name :close, :category :builtin, :arity :fixed, :returns :series,
    :pine-fn "close", :doc "Close price series"}
   {:name :high, :category :builtin, :arity :fixed, :returns :series,
    :pine-fn "high", :doc "High price series"}
   {:name :low, :category :builtin, :arity :fixed, :returns :series,
    :pine-fn "low", :doc "Low price series"}
   {:name :open, :category :builtin, :arity :fixed, :returns :series,
    :pine-fn "open", :doc "Open price series"}
   {:name :volume, :category :builtin, :arity :fixed, :returns :series,
    :pine-fn "volume", :doc "Volume series"}
   {:name :hl2, :category :builtin, :arity :fixed, :returns :series,
    :pine-fn "hl2", :doc "(high + low) / 2"}
   {:name :hlc3, :category :builtin, :arity :fixed, :returns :series,
    :pine-fn "hlc3", :doc "(high + low + close) / 3"}
   {:name :ohlc4, :category :builtin, :arity :fixed, :returns :series,
    :pine-fn "ohlc4", :doc "(open + high + low + close) / 4"}

   ;; ─── Indicators ───────────────────────────────────────────────────
   {:name :sma, :category :indicator, :arity :fixed,
    :args [[{:name :period, :type :int}]],
    :pine-fn "ta.sma", :returns :series,
    :default-source :close,
    :doc "Simple Moving Average: sma(14) → ta.sma(close, 14)"}
   {:name :ema, :category :indicator, :arity :fixed,
    :args [[{:name :period, :type :int}]],
    :pine-fn "ta.ema", :returns :series,
    :default-source :close,
    :doc "Exponential Moving Average: ema(14) → ta.ema(close, 14)"}
   {:name :rsi, :category :indicator, :arity :fixed,
    :args [[{:name :period, :type :int}]],
    :pine-fn "ta.rsi", :returns :series,
    :default-source :close,
    :doc "Relative Strength Index: rsi(14) → ta.rsi(close, 14)"}
   {:name :macd, :category :indicator, :arity :fixed,
    :args [[{:name :fast, :type :int, :default 12}]
           [{:name :slow, :type :int, :default 26}]
           [{:name :signal, :type :int, :default 9}]],
    :pine-fn "ta.macd", :returns :tuple,
    :default-source :close,
    :doc "MACD: macd → let [m,s,h] = ta.macd(close, 12, 26, 9)"}
   {:name :adx, :category :indicator, :arity :fixed,
    :args [[{:name :period, :type :int, :default 14}]],
    :pine-fn "ta.adx", :returns :series,
    :default-source :close,
    :doc "Average Directional Index: adx(14) → ta.adx(high, low, close, 14)"}
   {:name :stoch, :category :indicator, :arity :fixed,
    :args [[{:name :k-period, :type :int, :default 14}]
           [{:name :k-smooth, :type :int, :default 3}]
           [{:name :d-smooth, :type :int, :default 3}]],
    :pine-fn "ta.stoch", :returns :tuple,
    :doc "Stochastic Oscillator: stoch → ta.stoch(close, high, low, 14)"}
   {:name :bb, :category :indicator, :arity :fixed,
    :args [[{:name :period, :type :int, :default 20}]
           [{:name :mult, :type :float, :default 2.0}]],
    :pine-fn "ta.bb", :returns :tuple,
    :default-source :close,
    :doc "Bollinger Bands: bb(20, 2) → [middle, upper, lower]"}
   {:name :atr, :category :indicator, :arity :fixed,
    :args [[{:name :period, :type :int, :default 14}]],
    :pine-fn "ta.atr", :returns :series,
    :doc "Average True Range: atr(14) → ta.atr(14)"}

   ;; ─── Conditions ───────────────────────────────────────────────────
   {:name :crosses-above, :category :condition, :arity :fixed,
    :pine-fn "ta.cross", :returns :bool,
    :doc "True when series a crosses above series b on current bar"}
   {:name :crosses-below, :category :condition, :arity :fixed,
    :pine-fn "ta.cross", :returns :bool,
    :doc "True when series a crosses below series b on current bar"}
   {:name :rising, :category :condition, :arity :fixed,
    :pine-fn "rising", :returns :bool,
    :doc "True when value is rising over lookback period"}
   {:name :falling, :category :condition, :arity :fixed,
    :pine-fn "falling", :returns :bool,
    :doc "True when value is falling over lookback period"}

   ;; ─── Logic ────────────────────────────────────────────────────────
   {:name :and, :category :logic, :arity :variadic,
    :doc "Logical AND of all conditions"}
   {:name :or, :category :logic, :arity :variadic,
    :doc "Logical OR of all conditions"}
   {:name :not, :category :logic, :arity :fixed,
    :doc "Logical NOT of condition"}
   {:name :when, :category :logic, :arity :fixed,
    :doc "Conditional: (when condition action1 action2 ...)"}

   ;; ─── Comparisons ──────────────────────────────────────────────────
   {:name :>, :category :logic, :arity :variadic, :pine-fn ">",
    :doc "Greater than"}
   {:name :<, :category :logic, :arity :variadic, :pine-fn "<",
    :doc "Less than"}
   {:name :>=, :category :logic, :arity :variadic, :pine-fn ">=",
    :doc "Greater than or equal"}
   {:name :<=, :category :logic, :arity :variadic, :pine-fn "<=",
    :doc "Less than or equal"}
   {:name :=, :category :logic, :arity :variadic, :pine-fn "==",
    :doc "Equals"}

   ;; ─── Strategy Actions ─────────────────────────────────────────────
   {:name :long, :category :action, :arity :fixed,
    :doc "Enter long position with optional label"}
   {:name :short, :category :action, :arity :fixed,
    :doc "Enter short position with optional label"}
   {:name :close, :category :action, :arity :fixed,
    :doc "Close position with optional label"}

   ;; ─── Plotting ─────────────────────────────────────────────────────
   {:name :plot, :category :plot, :arity :fixed,
    :pine-fn "plot", :doc "Plot a line on the chart"}
   {:name :plotshape, :category :plot, :arity :fixed,
    :pine-fn "plotshape", :doc "Plot a shape marker on the chart"}
   {:name :hline, :category :plot, :arity :fixed,
    :pine-fn "hline", :doc "Draw a horizontal line"}
   {:name :bgcolor, :category :plot, :arity :fixed,
    :pine-fn "bgcolor", :doc "Set background color conditionally"}
   {:name :barcolor, :category :plot, :arity :fixed,
    :pine-fn "barcolor", :doc "Set bar color conditionally"}

   ;; ─── Alerts ───────────────────────────────────────────────────────
   {:name :alertcondition, :category :alert, :arity :fixed,
    :pine-fn "alertcondition", :doc "Define an alert condition"}])

;; ─── Lookup ──────────────────────────────────────────────────────────

(def construct-index
  "Map from keyword to construct definition."
  (into {} (map (juxt :name identity) constructs)))

(defn lookup
  "Look up a construct by symbol or keyword.
   Returns the construct map, or nil if not found."
  [sym]
  (get construct-index (keyword sym)))

(defn indicator?
  "True if the construct is an indicator (takes price source)."
  [construct]
  (= :indicator (:category construct)))

(defn has-default-source?
  "True if the construct accepts an implicit close source."
  [construct]
  (boolean (:default-source construct)))
