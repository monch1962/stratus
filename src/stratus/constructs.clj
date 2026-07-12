(ns stratus.constructs
  "Dynamic construct definitions derived from generator and validator.
   Always stays in sync since it queries the authoritative sources."
  (:require [stratus.generator :as gen]
            [stratus.validator :as val]
            [clojure.string :as str]))

(defn- derive-name
  "Given a keyword like :input-int or :str.contains, return a human-readable name."
  [k]
  (let [s (name k)]
    (str/replace (str/replace s #"\." " ") #"-" " ")))

(defn- derive-category
  "Guess a category from the keyword name."
  [k]
  (let [s (name k)]
    (cond
      (str/starts-with? s "input-") :input
      (str/starts-with? s "str.") :string
      (str/starts-with? s "math.") :math
      (str/starts-with? s "array.") :array
      (str/starts-with? s "table.") :table
      (str/starts-with? s "line.") :line
      (str/starts-with? s "label.") :label
      (str/starts-with? s "box.") :box
      (str/starts-with? s "polygon.") :polygon
      (str/starts-with? s "matrix.") :matrix
      (str/starts-with? s "map.") :map
      (str/starts-with? s "ticker.") :ticker
      (str/starts-with? s "syminfo.") :builtin
      (str/starts-with? s "barstate.") :builtin
      (str/starts-with? s "chart.") :chart
      (str/starts-with? s "strategy.") :strategy
      (str/starts-with? s "color.") :color
      (#{:sma :ema :rsi :wma :hma :vwma :macd :adx :stoch :bb :atr
         :tr :range :roc :swma :rma :tema :dema :smma :supertrend
         :sar :vwap :stdev :cci :mfi :obv :linreg :kc :alma :mama
         :cog :vwmacd :dmi :wpr :apo :sarext :cvd :efi :vpvr
         :correlation :covariance :median :mode :percentile
         :cross :crossover :crossunder :valuewhen
         :highest :lowest :cum :highestbars :lowestbars :sum :avg
         :fixnan :cmo :wad :mom :change :rising :falling} k) :indicator
      (#{:long :short :close :close-all :reverse :exit :order :cancel
         :position-size :position-avg-price
         :open-trades :equity :net-profit :open-profit
         :win-trades :loss-trades :closed-trades
         :gross-profit :gross-loss :max-drawdown :max-runup} k) :action
      (#{:plot :plotshape :plotchar :plotarrow :hline :bgcolor
         :barcolor :fill :alertcondition} k) :plot
      (#{:if :when :for :while :switch :and :or :not
         :> :< :>= :<= :== :!= :=} k) :logic
      (#{:def :defn :defvar :defvarip :definline :defmacro :export
         :strategy :indicator :library :comment :set!} k) :declaration
      (#{:let :-> :->> :some-> :some->> :cond-> :as-> :for :cond
         :multiset} k) :expansion
      :else :builtin)))

(defn- derive-doc
  "Generate a basic doc string from the keyword."
  [k]
  (str "DSL construct: " (derive-name k)))

(def constructs
  "List of construct maps derived from generator + validator sources.
   Each map has :name, :category, :doc — same shape as the old static list."
  (vec (for [k (sort val/known-constructs)]
         {:name k
          :category (derive-category k)
          :doc (derive-doc k)})))

(defn lookup
  "Find a construct by keyword."
  [k]
  (first (filter #(= (:name %) k) constructs)))
