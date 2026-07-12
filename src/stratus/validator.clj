(ns stratus.validator
  "Pre-compile validation of .stratus files.
   Checks construct names, argument counts, and basic type ranges
   before the generator runs. Catches errors early rather than
   producing bad Pine Script."
  (:require [clojure.string :as str]
            [stratus.generator :as gen]))

;; ─── Known construct set ──────────────────────────────────────────

(def known-constructs
  "Set of all known Stratus construct keywords.
   Derived from generator's pine-simple-map + explicit defmethods."
  (let [simple (set (keys gen/pine-simple-map))
        ;; Multi-arity dispatch values (::price-ref etc.) aren't user-facing
        explicit #{:strategy :indicator :library :def :defvar :defvarip :set!
                   :defn :definline :defmacro :comment :export :multiset
                   :if :when :do :for :while :switch :on-bar
                   :let :-> :->> :some-> :some->> :cond-> :as-> :cond
                   :sma :ema :rsi :wma :vwma :hma :macd :adx :stoch :bb :atr
                   :cross :tr :range :roc :swma :rma :tema :dema :smma
                   :supertrend :sar :vwap :stdev :cci :mfi :obv :linreg :kc :alma
                   :highest :lowest :cum :highestbars :lowestbars :sum :avg
                   :fixnan :valuewhen :cmo :wad :mama :cog :vwmacd
                   :crossover :crossunder
                   :correlation :covariance :median :mode :percentile :in-session
                   :crosses-above :crosses-below :rising :falling
                   :na :nz :iff :change :mom
                   :and :or :not :> :< :>= :<= := :+ :- :* :/ :%
                   :long :short :close :close-all :reverse :exit :order :cancel
                   :plot :plotshape :plotchar :plotarrow :hline :bgcolor
                   :barcolor :fill :alertcondition
                   :color :rgb :from-gradient :tostring
                   :security :security-lower-tf :financial :random :seed
                   :timestamp :ticker.modify
                   :line.new :line.delete :label.new :label.delete
                   :box.new :box.delete :polygon.new :polygon.delete
                   :table.new :table-cell :map.new :matrix.new
                   :array-int :array-float :array-bool :array-string
                   :array-color :array-line :array-label :array-box :array-table
                   :array.push :array.pop :array.size :array.get :array.set
                   :array.sort :array.fill :array.reverse
                   :push :pop :size :get :set :sort
                   :pi :tau :e :phi :round :pow :min :max
                   :time.close :time.tradingday
                   :chart.point.now :chart.point.from-index
                   :input-int :input-float :input-bool :input-string
                   :input-color :input-source :input-symbol :input-timeframe
                   :input-price :input-session
                   :high :low :open :volume :hl2 :hlc3 :ohlc4}]
    (into simple explicit)))

;; ─── Arg count hints ──────────────────────────────────────────────

(def arity-hints
  "Map of construct keyword -> {:min N, :max N} for argument count hints.
   Includes only constructs where the arg count is strictly bounded."
  {:sma {:min 1, :max 3}
   :ema {:min 1, :max 3}
   :rsi {:min 1, :max 3}
   :wma {:min 1, :max 3}
   :vwma {:min 1, :max 3}
   :hma {:min 1, :max 3}
   :atr {:min 0, :max 1}
   :stdev {:min 0, :max 3}
   :linreg {:min 0, :max 3}
   :cmo {:min 0, :max 3}
   :mom {:min 0, :max 3}
   :change {:min 0, :max 3}
   :roc {:min 0, :max 3}
   :cum {:min 0, :max 2}
   :highest {:min 1, :max 3}
   :lowest {:min 1, :max 3}
   :sum {:min 1, :max 3}
   :avg {:min 1, :max 3}
   :fixnan {:min 1, :max 2}
   :abs {:min 1, :max 2}
   :ceil {:min 1, :max 2}
   :floor {:min 1, :max 2}
   :sqrt {:min 1, :max 2}
   :log {:min 1, :max 2}
   :log10 {:min 1, :max 2}
   :exp {:min 1, :max 2}
   :sign {:min 1, :max 2}
   :round {:min 1, :max 3}
   :pow {:min 2, :max 3}
   :min {:min 2, :max 10}
   :max {:min 2, :max 10}})

;; ─── Type-ranged constructs ───────────────────────────────────────

(def range-checks
  "Constructs where certain numeric args should be in a sensible range."
  {:sma [{:idx 1 :name "period" :min 1 :max 500}]
   :ema [{:idx 1 :name "period" :min 1 :max 500}]
   :rsi [{:idx 1 :name "period" :min 1 :max 500}]
   :atr [{:idx 0 :name "period" :min 1 :max 500}]
   :bb [{:idx 0 :name "period" :min 2 :max 200} {:idx 1 :name "mult" :min 0.5 :max 10}]
   :stoch [{:idx 0 :name "period" :min 1 :max 100}]
   :adx [{:idx 0 :name "period" :min 1 :max 100}]})

;; ─── Validation ──────────────────────────────────────────────────

(defn- validate-form
  "Validate a single parsed form. Returns a seq of warning/error strings."
  [form]
  (if (and (list? form) (symbol? (first form)))
    (let [k (keyword (first form))
          fn-name (name (first form))
          arg-count (count (rest form))]
      (concat
        ;; Check 1: Is the construct known?
        (when (not (contains? known-constructs k))
          [(str "WARN: Unknown construct '" fn-name "' — check spelling or run `stratus list`")])
        ;; Check 2: Does the construct look like a user variable/def? (skip known builtins)
        ;; Check 3: Arg count bounds
        (when-let [hint (get arity-hints k)]
          (cond
            (< arg-count (:min hint))
            [(str "WARN: '" fn-name "' needs at least " (:min hint) " arguments, got " arg-count)]
            (> arg-count (:max hint))
            [(str "WARN: '" fn-name "' expected at most " (:max hint) " arguments, got " arg-count)]
            :else []))
        ;; Check 4: Numeric arg ranges
        (when-let [checks (get range-checks k)]
          (keep (fn [{:keys [idx name min max]}]
                  (let [arg (nth (rest form) idx nil)]
                    (when (and (number? arg) (number? min) (number? max))
                      (cond
                        (< arg min) (str "WARN: '" fn-name "' " name "=" arg " is below minimum " min)
                        (> arg max) (str "WARN: '" fn-name "' " name "=" arg " exceeds maximum " max)
                        :else nil))))
                checks))))
    []))

(defn validate
  "Validate a list of parsed forms. Returns a vector of warning/error strings."
  [forms]
  (vec (mapcat validate-form forms)))

;; ─── Friendly output ──────────────────────────────────────────────

(defn report
  "Run validation and print a friendly report. Returns true if no issues."
  [forms]
  (let [issues (validate forms)]
    (if (empty? issues)
      (do (println "✓ No issues found")
          true)
      (do (println "Validation found" (count issues) "issue(s):")
          (doseq [i issues]
            (println "  " i))
          false))))
