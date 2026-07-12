(ns stratus.generator
  "AST → Pine Script v6.  Multimethod dispatches on form head symbol."
  (:require [clojure.string :as str]))

(declare expr->pine)

;; ─── Helpers ─────────────────────────────────────────────────────────

(def builtin-sources
  {:close "close", :high "high", :low "low", :open "open",
   :volume "volume", :hl2 "hl2", :hlc3 "hlc3", :ohlc4 "ohlc4"})

(def time-builtins
  {:time "time", :dayofweek "dayofweek", :month "month",
   :hour "hour", :bar-index "bar_index", :ticker "syminfo.tickerid",
   :timeframe "timeframe.period",
   :year "year", :dayofmonth "dayofmonth", :minute "minute",
   :second "second", :weekofyear "weekofyear", :quarter "quarter"})

(def barstate-builtins
  {:bar-confirmed "barstate.isconfirmed", :bar-first "barstate.isfirst",
   :bar-last "barstate.islast",
   :bar-new "barstate.isnew", :bar-realtime "barstate.isrealtime",
   :bar-history "barstate.ishistory"})

(def syminfo-builtins
  {:mintick "syminfo.mintick", :pointvalue "syminfo.pointvalue",
   :sym-session "syminfo.session", :sym-description "syminfo.description",
   :sym-type "syminfo.type",
   :currency "syminfo.currency", :base-currency "syminfo.basecurrency",
   :price-scale "syminfo.pricescale", :min-move "syminfo.minmov",
   :sym-sector "syminfo.sector", :sym-industry "syminfo.industry"})

(def math-scalars
  [:log :log10 :exp :sqrt :abs :ceil :floor :round :pow :min :max :sign])

(def strategy-builtins-map
  {:position-size "strategy.position_size", :position-avg-price "strategy.position_avg_price",
   :open-trades "strategy.opentrades", :equity "strategy.equity",
   :net-profit "strategy.netprofit",
   :open-profit "strategy.openprofit", :win-trades "strategy.wintrades",
   :loss-trades "strategy.losstrades", :closed-trades "strategy.closedtrades",
   :gross-profit "strategy.grossprofit", :gross-loss "strategy.grossloss",
   :max-drawdown "strategy.maxdrawdown", :max-runup "strategy.maxrunup",
   :allow-entry-in "strategy.risk.allow_entry_in",
   :max-intraday-orders "strategy.risk.max_intraday_filled_orders"})

;; ─── Fundamental data requests ─────────────────────────────────────

(def fundamental-requests
  {:dividends "request.dividends(syminfo.tickerid)",
   :splits "request.splits(syminfo.tickerid)",
   :earnings "request.earnings(syminfo.tickerid)"})

;; ─── Source resolver ───────────────────────────────────────────────

(def resolve-source
  (fn [form]
    (cond (symbol? form)   (or (builtin-sources (keyword (name form)))
                               (get strategy-builtins-map (keyword (name form)))
                               (name form))
          (keyword? form)  (or (builtin-sources form) (name form))
          (list? form)     (expr->pine form)
          :otherwise       (throw (ex-info "Unknown source" {:form form})))))

;; ─── Arg parser ────────────────────────────────────────────────────

(defn parse-kwargs [args]
  (loop [pos [], kw {}, rem args]
    (if (empty? rem)
      {:positional pos, :keyword kw}
      (if (keyword? (first rem))
        (if (next rem)
          (recur pos (assoc kw (first rem) (second rem)) (drop 2 rem))
          (recur pos kw (rest rem)))
        (recur (conj pos (first rem)) kw (rest rem))))))

(def plot-style-map
  {:line "plot.style_line", :histogram "plot.style_histogram", :area "plot.style_area",
  :columns "plot.style_columns", :circles "plot.style_circles", :cross "plot.style_cross",
  :step-line "plot.style_stepline", :step-line-diamond "plot.style_stepline_diamond"})

  (def table-position-map
    {:top-right "table.position_top_right", :top-left "table.position_top_left",
     :top-center "table.position_top_center", :middle-right "table.position_middle_right",
     :middle-left "table.position_middle_left", :middle-center "table.position_middle_center",
     :bottom-left "table.position_bottom_left", :bottom-center "table.position_bottom_center",
     :bottom-right "table.position_bottom_right"})

(def lookup-tables
  (merge {:red "color.red", :green "color.green", :blue "color.blue",
          :yellow "color.yellow", :orange "color.orange", :purple "color.purple",
          :pink "color.pink", :gray "color.gray", :white "color.white",
          :black "color.black", :navy "color.navy", :teal "color.teal",
          :lime "color.lime", :maroon "color.maroon"}
         {:triangle-up "shape.triangleup", :triangle-down "shape.triangledown"}
         {:top "location.top", :bottom "location.bottom", :absolute "location.absolute"}
         {:solid "hline.style_solid", :dashed "hline.style_dashed", :dotted "hline.style_dotted"}
         plot-style-map
         table-position-map))

(defn val->pine [v]
  (cond (or (keyword? v) (symbol? v))
        (or (lookup-tables (keyword (name v))) (str "color." (name v)))
        (string? v)  (str "\"" v "\"")
        (number? v)  (str v)
        (true? v)    "true"
        (false? v)   "false"
        (nil? v)     "na"
        :otherwise   (expr->pine v)))

(defn emit-kwargs [kwargs]
  (when (seq kwargs)
    (str ", " (str/join ", "
      (map (fn [e] (let [raw-name (name (key e))
                         pine-name (case raw-name
                                    "min" "minval"
                                    "max" "maxval"
                                    raw-name)
                         v (val->pine (val e))]
                     (str (clojure.string/replace pine-name #"-" "_") "=" v)))
           kwargs)))))

(defn plot-call [pine-fn form]
  (let [{:keys [positional keyword]} (parse-kwargs (drop 2 form))]
    (str pine-fn "(" (expr->pine (second form))
         (when (seq positional) (str ", " (str/join ", " (map val->pine positional))))
         (emit-kwargs keyword) ")")))

;; ─── Dispatch ────────────────────────────────────────────────────────

(defmulti expr->pine
  (fn [form]
    (if (or (list? form) (seq? form))
      (let [k (keyword (first form))
            price-syms #{:close :high :low :open :volume :hl2 :hlc3 :ohlc4}
            strat-syms (set (keys strategy-builtins-map))
            fund-syms (set (keys fundamental-requests))]
        (cond (and (price-syms k) (not (string? (second form)))) ::price-ref
              (strat-syms k) ::strat-builtin
              (time-builtins k) ::time-ref
              (barstate-builtins k) ::barstate-ref
              (syminfo-builtins k) ::syminfo-ref
              (fund-syms k) ::fundamental
              :else k))
      ::literal)))

(defmethod expr->pine ::price-ref [form]
  (str (name (first form)) (when (number? (second form)) (str "[" (second form) "]"))))

(defmethod expr->pine ::strat-builtin [form]
  (let [k (keyword (first form))
        v (get strategy-builtins-map k)]
    (if (and v (seq (rest form)))
      (str v "(" (val->pine (second form)) ")")
      v)))

(defmethod expr->pine ::time-ref [form]
  (time-builtins (keyword (first form))))

(defmethod expr->pine ::barstate-ref [form]
  (barstate-builtins (keyword (first form))))

(defmethod expr->pine ::syminfo-ref [form]
  (syminfo-builtins (keyword (first form))))

(defmethod expr->pine ::fundamental [form]
  (fundamental-requests (keyword (first form))))

(defmethod expr->pine ::literal [form]
  (cond (string? form) (str "\"" form "\"")
        (number? form) (str form)
        (keyword? form) (name form)
        (symbol? form) (or (lookup-tables (keyword (str/replace (name form) #"\." "-")))
                           (builtin-sources (keyword (name form)))
                           (get strategy-builtins-map (keyword (name form)))
                           (time-builtins (keyword (name form)))
                           (barstate-builtins (keyword (name form)))
                           (syminfo-builtins (keyword (name form)))
                           (fundamental-requests (keyword (name form)))
                           (str/replace (name form) #"-" "_"))
        (list? form) (expr->pine form)
        (true? form) "true" (false? form) "false" (nil? form) "na"
        :otherwise (str form)))


;; ─── Simple method registry ─────────────────────────────────
;; Methods listed here follow the pattern:
;;   pine_fn(arg1, arg2, ...)
;; and do not need individual defmethods.
(def pine-simple-map
  {  ;; array (19)
     :array.avg "array.avg"
     :array.clear "array.clear"
     :array.concat "array.concat"
     :array.copy "array.copy"
     :array.includes "array.includes"
     :array.indexof "array.indexof"
     :array.insert "array.insert"
     :array.lastindexof "array.lastindexof"
     :array.max "array.max"
     :array.median "array.median"
     :array.min "array.min"
     :array.mode "array.mode"
     :array.range "array.range"
     :array.remove "array.remove"
     :array.shift "array.shift"
     :array.slice "array.slice"
     :array.stdev "array.stdev"
     :array.sum "array.sum"
     :array.unshift "array.unshift"
     ;; box (6)
     :box.get-bottom "box.get_bottom"
     :box.get-left "box.get_left"
     :box.get-right "box.get_right"
     :box.get-top "box.get_top"
     :box.set-extend "box.set_extend"
     :box.set-style "box.set_style"
     ;; chart (2)
     :chart.point.from-index "chart.point.from_index"
     :chart.point.now "chart.point.now"
     ;; color (4)
     :color.b "color.b"
     :color.g "color.g"
     :color.r "color.r"
     :color.t "color.t"
     ;; label (7)
     :label.get-text "label.get_text"
     :label.get-x "label.get_x"
     :label.get-y "label.get_y"
     :label.set-size "label.set_size"
     :label.set-style "label.set_style"
     :label.set-textalign "label.set_textalign"
     :label.set-textcolor "label.set_textcolor"
     ;; line (6)
     :line.get-price "line.get_price"
     :line.get-x1 "line.get_x1"
     :line.get-x2 "line.get_x2"
     :line.get-y1 "line.get_y1"
     :line.get-y2 "line.get_y2"
     :line.set-xloc "line.set_xloc"
     ;; map (7)
     :map.contains "map.contains"
     :map.delete "map.delete"
     :map.get "map.get"
     :map.keys "map.keys"
     :map.put "map.put"
     :map.size "map.size"
     :map.values "map.values"
     ;; math (14)
     :abs "math.abs"
     :array "array"
     :bool "bool"
     :ceil "math.ceil"
     :exp "math.exp"
     :float "float"
     :floor "math.floor"
     :int "int"
     :log "math.log"
     :log10 "math.log10"
     :series "series"
     :sign "math.sign"
     :sqrt "math.sqrt"
     :string "string"
     ;; matrix (15)
     :matrix.col "matrix.col"
     :matrix.columns "matrix.columns"
     :matrix.det "matrix.det"
     :matrix.fill "matrix.fill"
     :matrix.get "matrix.get"
     :matrix.inv "matrix.inv"
     :matrix.multiply "matrix.multiply"
     :matrix.pinv "matrix.pinv"
     :matrix.rank "matrix.rank"
     :matrix.row "matrix.row"
     :matrix.rows "matrix.rows"
     :matrix.set "matrix.set"
     :matrix.size "matrix.size"
     :matrix.sum "matrix.sum"
     :matrix.transpose "matrix.transpose"
     ;; order (5)
     :order.entry-condition "order.entry_condition"
     :order.entry-id "order.entry_id"
     :order.exit-condition "order.exit_condition"
     :order.filled "order.filled"
     :order.filled-condition "order.filled_condition"
     ;; polygon (7)
     :polygon.delete "polygon.delete"
     :polygon.get-bordercolor "polygon.get_bordercolor"
     :polygon.get-borderwidth "polygon.get_borderwidth"
     :polygon.get-fillcolor "polygon.get_fillcolor"
     :polygon.set-bordercolor "polygon.set_bordercolor"
     :polygon.set-borderwidth "polygon.set_borderwidth"
     :polygon.set-fillcolor "polygon.set_fillcolor"
     ;; str (11)
     :str.contains "str.contains"
     :str.endswith "str.endswith"
     :str.length "str.length"
     :str.lower "str.lower"
     :str.replace-all "str.replace_all"
     :str.split "str.split"
     :str.startswith "str.startswith"
     :str.substr "str.substr"
     :str.substring "str.substring"
     :str.tonumber "str.tonumber"
     :str.upper "str.upper"
     ;; table (11)
     :table.clear "table.clear"
     :table.delete "table.delete"
     :table.get-location "table.get_location"
     :table.get-size "table.get_size"
     :table.merge-cells "table.merge_cells"
     :table.set-bgcolor "table.set_bgcolor"
     :table.set-border-color "table.set_border_color"
     :table.set-border-width "table.set_border_width"
     :table.set-color "table.set_color"
     :table.set-position "table.set_position"
     :table.set-size "table.set_size"
     ;; ticker (7)
     :ticker.heikinashi "ticker.heikinashi"
     :ticker.kagi "ticker.kagi"
     :ticker.linebreak "ticker.linebreak"
     :ticker.new "ticker.new"
     :ticker.pnf "ticker.pnf"
     :ticker.range "ticker.range"
     :ticker.renko "ticker.renko"})

(defmethod expr->pine :default [form]
  (let [k (keyword (first form))
        fn-name (name (first form))
        args (rest form)]
    (if-let [pine-fn (get pine-simple-map k)]
      (str pine-fn "(" (str/join ", " (map expr->pine args)) ")")
      (str (str/replace fn-name #"-" "_")
           "(" (str/join ", " (map expr->pine args)) ")"))))

;; ─── Strategy / Indicator / Library Headers ─────────────────────────

(defmethod expr->pine :strategy [form]
  (str "strategy(\"" (second form) "\""
       (emit-kwargs (:keyword (parse-kwargs (drop 2 form)))) ")"))

(defmethod expr->pine :indicator [form]
  (str "indicator(\"" (second form) "\""
       (emit-kwargs (:keyword (parse-kwargs (drop 2 form)))) ")"))

;; ─── Variable Bindings ──────────────────────────────────────────────

(defmethod expr->pine :definline [form]
  (str "// definline " (name (second form)) " — should have been expanded"))

;; P0: comment — returns empty string
(defmethod expr->pine :comment [form] "")

(defmethod expr->pine :def [form]
  (str (str/replace (name (second form)) #"-" "_") " = " (expr->pine (nth form 2))))

(defmethod expr->pine :defvar [form]
  (str "var " (str/replace (name (second form)) #"-" "_") " = " (expr->pine (nth form 2))))

(defmethod expr->pine :defvarip [form]
  (str "varip " (str/replace (name (second form)) #"-" "_") " = " (expr->pine (nth form 2))))

(defmethod expr->pine :set! [form]
  (str (str/replace (name (second form)) #"-" "_") " := " (expr->pine (nth form 2))))

;; ─── Indicators ─────────────────────────────────────────────────────

(defn indicator-call [form fn-name src]
  (let [{:keys [positional]} (parse-kwargs (drop 1 form))
        f (first positional)
        has-src (and f (or (symbol? f) (keyword? f) (list? f)))
        src-val (if has-src (resolve-source f) src)
        args    (map expr->pine (if has-src (rest positional) positional))]
    (str fn-name "(" src-val (when (seq args) (str ", " (str/join ", " args))) ")")))

(defmethod expr->pine :sma  [form] (indicator-call form "ta.sma"  "close"))
(defmethod expr->pine :ema  [form] (indicator-call form "ta.ema"  "close"))
(defmethod expr->pine :rsi  [form] (indicator-call form "ta.rsi"  "close"))
(defmethod expr->pine :wma  [form] (indicator-call form "ta.wma"  "close"))
(defmethod expr->pine :vwma [form] (indicator-call form "ta.vwma" "close"))
(defmethod expr->pine :hma  [form] (indicator-call form "ta.hma"  "close"))

(defmethod expr->pine :macd [form]
  (let [{:keys [keyword]} (parse-kwargs (drop 1 form))
        f (or (:fast keyword) 12) s (or (:slow keyword) 26) g (or (:signal keyword) 9)]
    (str "ta.macd(close, " f ", " s ", " g ")")))
(defmethod expr->pine :adx  [form] (str "ta.adx(high, low, close, " (or (some-> (second form) str) "14") ")"))
(defmethod expr->pine :stoch [form] (str "ta.stoch(close, high, low, " (or (some-> (second form) str) "14") ")"))
(defmethod expr->pine :bb [form]
  (let [p (or (some-> (second form) str) "20")
        m (or (some->> (drop 2 form) first str) "2.0")]
    (str "ta.bb(close, " p ", " m ")")))
(defmethod expr->pine :atr  [form] (str "ta.atr(" (or (some-> (second form) str) "14") ")"))

;; P2: cross, tr, range, roc
(defmethod expr->pine :cross    [form]
  (str "ta.cross(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2)) ")"))
(defmethod expr->pine :tr       [form]
  (if (> (count form) 1) (str "ta.tr(" (expr->pine (second form)) ")") "ta.tr"))
(defmethod expr->pine :range    [form]
  (if (> (count form) 2) (str "ta.range(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2)) ")") "ta.range"))
(defmethod expr->pine :roc      [form] (indicator-call form "ta.roc" "close"))

;; P2: MA variants
(defmethod expr->pine :swma [form]
  (let [s (some-> (second form) name)] (str "ta.swma(" (or s "close") ")")))
(defmethod expr->pine :rma  [form] (indicator-call form "ta.rma" "close"))
(defmethod expr->pine :tema [form] (indicator-call form "ta.tema" "close"))
(defmethod expr->pine :dema [form] (indicator-call form "ta.dema" "close"))
(defmethod expr->pine :smma [form] (indicator-call form "ta.smma" "close"))

;; P1 indicators
(defmethod expr->pine :supertrend [form]
  (let [f (or (some-> (second form) str) "3")
        m (or (some-> (nth form 2) str) "10")]
    (str "ta.supertrend(" f ", " m ")")))
(defmethod expr->pine :sar  [form]
  (let [a (or (some-> (second form) str) "0.02")
        m (or (some-> (nth form 2) str) "0.2")]
    (str "ta.sar(" a ", " m ")")))
(defmethod expr->pine :vwap [form]
  (let [s (some-> (second form) name)]
    (str "ta.vwap(" (or s "hlc3") ")")))
(defmethod expr->pine :stdev [form] (indicator-call form "ta.stdev" "close"))
(defmethod expr->pine :cci  [form]
  (let [{:keys [positional]} (parse-kwargs (drop 1 form))
        src (if (seq positional) (resolve-source (first positional)) "close")
        per (or (some-> (second positional) str) (some-> (first positional) str) "20")]
    (str "ta.cci(" src ", " per ")")))
(defmethod expr->pine :mfi  [form]
  (let [s (or (some-> (second form) name) "hlc3")
        p (or (some-> (nth form 2) str) "14")]
    (str "ta.mfi(" s ", " p ")")))
(defmethod expr->pine :obv  [form] "ta.obv")
(defmethod expr->pine :linreg [form] (indicator-call form "ta.linreg" "close"))
;; P2: ta.kc (Keltner Channels)
(defmethod expr->pine :kc [form]
  (let [{:keys [positional]} (parse-kwargs (drop 1 form))
        f (first positional)
        has-src (and f (or (symbol? f) (keyword? f) (list? f)))
        src (if has-src (resolve-source f) "close")
        args (if has-src (rest positional) positional)
        p (or (some-> (first args) str) "20")
        m (or (some-> (second args) str) "2.0")]
    (str "ta.kc(" src ", " p ", " m ")")))
(defmethod expr->pine :alma [form]
  (let [p (or (some-> (second form) str) "10")
        o (or (some-> (nth form 2) str) "6")
        o2 (or (some-> (nth form 3) str) "0.85")]
    (str "ta.alma(close, " p ", " o ", " o2 ")")))

;; P0: highest / lowest
(defmethod expr->pine :highest [form]
  (let [s (resolve-source (second form))
        p (some-> (nth form 2) expr->pine)]
    (str "ta.highest(" s ", " (or p "20") ")")))
(defmethod expr->pine :lowest [form]
  (let [s (resolve-source (second form))
        p (some-> (nth form 2) expr->pine)]
    (str "ta.lowest(" s ", " (or p "20") ")")))

;; P1: cum / highestbars / lowestbars / sum / avg / fixnan / valuewhen
(defmethod expr->pine :cum       [form] (str "math.cum(" (expr->pine (second form)) ")"))
(defmethod expr->pine :highestbars [form]
  (str "ta.highestbars(" (or (some-> (second form) expr->pine) "high") ", " (or (some-> (nth form 2) expr->pine) "20") ")"))
(defmethod expr->pine :lowestbars [form]
  (str "ta.lowestbars(" (or (some-> (second form) expr->pine) "low") ", " (or (some-> (nth form 2) expr->pine) "20") ")"))
(defmethod expr->pine :sum  [form] (str "math.sum(" (expr->pine (second form)) ", " (or (some-> (nth form 2) expr->pine) "20") ")"))
(defmethod expr->pine :avg  [form] (str "math.avg(" (expr->pine (second form)) ", " (or (some-> (nth form 2) expr->pine) "20") ")"))
(defmethod expr->pine :fixnan [form] (str "fixnan(" (expr->pine (second form)) ")"))
(defmethod expr->pine :valuewhen [form]
  (str "ta.valuewhen(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2)) ")"))

;; P1: strategy.order / cancel
(defmethod expr->pine :order [form]
  (let [label (second form)
        dir (nth form 2)
        {:keys [keyword]} (parse-kwargs (drop 3 form))
        dir-str (case dir :long "strategy.long" :short "strategy.short" (name dir))]
    (str "strategy.order(\"" label "\", " dir-str (emit-kwargs keyword) ")")))
(defmethod expr->pine :cancel [form]
  (str "strategy.cancel(\"" (second form) "\")"))

;; P2: statistics
(defmethod expr->pine :correlation [form]
  (str "ta.correlation(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2)) ", " (or (some-> (nth form 3) str) "20") ")"))
(defmethod expr->pine :covariance [form]
  (str "ta.covariance(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2)) ", " (or (some-> (nth form 3) str) "20") ")"))
(defmethod expr->pine :median [form] (str "ta.median(" (expr->pine (second form)) ", " (or (some-> (nth form 2) str) "20") ")"))
(defmethod expr->pine :mode   [form] (str "ta.mode(" (expr->pine (second form)) ", " (or (some-> (nth form 2) str) "20") ")"))
(defmethod expr->pine :percentile [form]
  (str "ta.percentile_nearest_rank(" (expr->pine (second form)) ", " (or (some-> (nth form 2) str) "20") ", " (or (some-> (nth form 3) str) "50") ")"))

;; P2: in-session
(defmethod expr->pine :in-session [form]
  (str "session.isregular(" (val->pine (second form)) ")"))

;; ─── Conditions ─────────────────────────────────────────────────────

(defmethod expr->pine :crosses-above [form]
  (str "ta.cross(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2))
       ") and " (expr->pine (nth form 1)) " > " (expr->pine (nth form 2))))
(defmethod expr->pine :crosses-below [form]
  (str "ta.cross(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2))
       ") and " (expr->pine (nth form 1)) " < " (expr->pine (nth form 2))))
(defmethod expr->pine :rising  [form] (str "rising(" (expr->pine (second form)) ", 1)"))
(defmethod expr->pine :falling [form] (str "falling(" (expr->pine (second form)) ", 1)"))

;; P1: na / nz / iff / change / mom
(defmethod expr->pine :na  [form] (str "na(" (expr->pine (second form)) ")"))
(defmethod expr->pine :nz  [form]
  (if (> (count form) 2)
    (str "nz(" (expr->pine (second form)) ", " (expr->pine (nth form 2)) ")")
    (str "nz(" (expr->pine (second form)) ")")))
(defmethod expr->pine :iff [form]
  (str "iff(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2)) ", " (expr->pine (nth form 3)) ")"))
(defmethod expr->pine :change [form] (str "change(" (expr->pine (second form)) ", " (or (some-> (nth form 2) str) "1") ")"))
(defmethod expr->pine :mom [form] (str "mom(" (expr->pine (second form)) ", " (or (some-> (nth form 2) str) "10") ")"))

;; ─── Arithmetic (needed by set!, if/else, etc) ──────────────────────
(defmethod expr->pine :+  [form] (str (expr->pine (nth form 1)) " + " (expr->pine (nth form 2))))
(defmethod expr->pine :-  [form] (str (expr->pine (nth form 1)) " - " (expr->pine (nth form 2))))
(defmethod expr->pine :*  [form] (str (expr->pine (nth form 1)) " * " (expr->pine (nth form 2))))
(defmethod expr->pine :/  [form] (str (expr->pine (nth form 1)) " / " (expr->pine (nth form 2))))
(defmethod expr->pine :%  [form] (str (expr->pine (nth form 1)) " % " (expr->pine (nth form 2))))

;; ─── Do block (multi-action) ─────────────────────────────────────────
(defmethod expr->pine :do [form]
  (str/join "\n" (map #(str "    " (expr->pine %)) (rest form))))

(defmethod expr->pine :and [form] (str "(" (str/join " and " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :or  [form] (str "(" (str/join " or " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :not [form] (str "not " (expr->pine (second form))))
(defmethod expr->pine :>  [form] (str (expr->pine (nth form 1)) " > " (expr->pine (nth form 2))))
(defmethod expr->pine :<  [form] (str (expr->pine (nth form 1)) " < " (expr->pine (nth form 2))))
(defmethod expr->pine :>= [form] (str (expr->pine (nth form 1)) " >= " (expr->pine (nth form 2))))
(defmethod expr->pine :<= [form] (str (expr->pine (nth form 1)) " <= " (expr->pine (nth form 2))))
(defmethod expr->pine :=  [form] (str (expr->pine (nth form 1)) " == " (expr->pine (nth form 2))))

;; ─── Strategy Actions ───────────────────────────────────────────────

(defmethod expr->pine :long  [form] (str "strategy.entry(\"" (or (second form) "Long") "\", strategy.long)"))
(defmethod expr->pine :short [form] (str "strategy.entry(\"" (or (second form) "Short") "\", strategy.short)"))
(defmethod expr->pine :close [form]
  (if-let [l (second form)] (str "strategy.close(\"" l "\")") "strategy.close()"))

;; P1: close_all / reverse
(defmethod expr->pine :close-all [form] "strategy.close_all()")
(defmethod expr->pine :reverse [form]
  (if-let [l (second form)] (str "strategy.reverse(\"" l "\")") "strategy.reverse()"))

;; P0: strategy.exit()
(defmethod expr->pine :exit [form]
  (let [label (second form)
        {:keys [keyword]} (parse-kwargs (drop 2 form))
        kw-renamed (into {}
                     (map (fn [e]
                            (let [k (key e) v (val e)]
                              [(case k
                                 :trail :trail_points
                                 :trail-offset :trail_offset
                                 :from :from_entry
                                 :loss :loss
                                 :profit :profit
                                 :stop :stop
                                 :limit :limit
                                 k)
                               v]))
                          keyword))]
    (str "strategy.exit(\"" label "\"" (emit-kwargs kw-renamed) ")")))

;; ─── Plotting, Fills & Alerts ──────────────────────────────────────

(defmethod expr->pine :plot           [form] (plot-call "plot" form))
(defmethod expr->pine :plotshape      [form] (plot-call "plotshape" form))
(defmethod expr->pine :plotchar       [form] (plot-call "plotchar" form))
(defmethod expr->pine :plotarrow      [form] (plot-call "plotarrow" form))
(defmethod expr->pine :hline          [form] (plot-call "hline" form))
(defmethod expr->pine :bgcolor        [form] (plot-call "bgcolor" form))
(defmethod expr->pine :alertcondition [form] (plot-call "alertcondition" form))

;; P0: barcolor - unconditional when no condition expr
(defmethod expr->pine :barcolor [form]
  (let [{:keys [positional keyword]} (parse-kwargs (drop 1 form))]
    (if (seq positional)
      ;; Conditional: (barcolor cond :color red)
      (str "barcolor(" (expr->pine (first positional))
           (emit-kwargs (dissoc keyword :title)) ")")
      ;; Unconditional: (barcolor :color green)
      (str "barcolor(" (emit-kwargs (dissoc keyword :title :color))
           (when (:color keyword) (str (when (seq (dissoc keyword :title :color)) ", ")
                                       "color=" (val->pine (:color keyword)))) ")"))))

;; P0: fill
(defmethod expr->pine :fill [form]
  (let [p1 (expr->pine (second form))
        p2 (expr->pine (nth form 2))
        {:keys [keyword]} (parse-kwargs (drop 3 form))
        alpha (:alpha keyword)
        color-kw (:color keyword)
        color-str (if (and color-kw alpha)
                    (str "color.new(" (val->pine color-kw) ", " (val->pine alpha) ")")
                    (when color-kw (val->pine color-kw)))
        color-kwargs (if color-str (str (emit-kwargs (dissoc keyword :color :alpha))
                                         ", color=" color-str)
                        (emit-kwargs (dissoc keyword :color :alpha)))]
    (str "fill(" p1 ", " p2 color-kwargs ")")))

;; P1: color.new()
(defmethod expr->pine :color [form]
  (str "color.new(" (val->pine (second form)) ", " (or (some-> (nth form 2) str) "90") ")"))

;; P1: multiset tuple unpacking
(defmethod expr->pine :multiset [form]
  (let [[_ names expr] form
        name-str (str/join ", " (map name names))]
    (str "[" name-str "] = " (expr->pine expr))))

;; ─── P0: request.security() / security_lower_tf ──────────────────────

(defmethod expr->pine :security [form]
  (let [tf (second form)
        expr (nth form 2)]
    (str "request.security(syminfo.tickerid, \"" tf "\", " (expr->pine expr) ")")))
(defmethod expr->pine :security-lower-tf [form]
  (str "request.security_lower_tf(syminfo.tickerid, " (expr->pine (nth form 1)) ", " (expr->pine (nth form 2)) ")"))

;; ─── P0: if/else multi-branch ──────────────────────────────────────

(defmethod expr->pine :if [form]
  (let [branches (rest form)]
    (loop [rem branches, lines [], idx 0]
      (if (empty? rem)
        (str/join "\n" lines)
        (let [cond (first rem)
              action (second rem)]
          (if (= cond :else)
            ;; else clause — same indent as parent if
            (let [actions (if (and (list? action) (= (first action) 'do)) (rest action) [action])
                  action-lines (mapcat (fn [a]
                                        (map #(str "    " %)
                                             (str/split-lines (expr->pine a))))
                                      actions)]
              (recur (drop 2 rem) (into lines (cons "else" action-lines)) idx))
            ;; if or else-if clause
            (let [prefix (if (zero? idx) "if " "else if ")
                  actions (if (and (list? action) (= (first action) 'do)) (rest action) [action])
                  action-lines (mapcat (fn [a]
                                        (map #(str "    " %)
                                             (str/split-lines (expr->pine a))))
                                      actions)]
              (recur (drop 2 rem) (into lines (cons (str prefix (expr->pine cond)) action-lines)) (inc idx)))))))))
;; Math constants (zero-arg)
(defmethod expr->pine :pi  [form] "math.pi")
(defmethod expr->pine :tau [form] "math.tau")
(defmethod expr->pine :e   [form] "math.e")
;; round with optional precision
(defmethod expr->pine :round [form]
  (if (> (count form) 2)
    (str "math.round(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2)) ")")
    (str "math.round(" (expr->pine (second form)) ")")))
(defmethod expr->pine :pow [form]
  (str "math.pow(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2)) ")"))
(defmethod expr->pine :min [form]
  (str "math.min(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :max [form]
  (str "math.max(" (str/join ", " (map expr->pine (rest form))) ")"))

;; ─── P4: Input parameters ──────────────────────────────────────────
(defn input-call [form pine-type]
  (let [name-arg (second form)
        {:keys [keyword]} (parse-kwargs (drop 2 form))
        default (:def keyword)
        def-str (when default
                  (if (= pine-type "source")
                    (str (expr->pine default) ", ")
                    (str (val->pine default) ", ")))
        rest-str (emit-kwargs (dissoc keyword :def))]
    (str "input." pine-type "(" def-str "\"" name-arg "\"" rest-str ")")))

(defmethod expr->pine :input-int     [form] (input-call form "int"))
(defmethod expr->pine :input-float   [form] (input-call form "float"))
(defmethod expr->pine :input-bool    [form] (input-call form "bool"))
(defmethod expr->pine :input-string  [form] (input-call form "string"))
(defmethod expr->pine :input-color   [form] (input-call form "color"))
(defmethod expr->pine :input-source  [form] (input-call form "source"))
(defmethod expr->pine :input-symbol  [form] (input-call form "symbol"))
(defmethod expr->pine :input-timeframe [form] (input-call form "timeframe"))
(defmethod expr->pine :input-price    [form] (input-call form "price"))
(defmethod expr->pine :input-session  [form] (input-call form "session"))

;; ─── P4: Drawing objects ───────────────────────────────────────────
(defmethod expr->pine :line.new [form]
  (let [{:keys [positional keyword]} (parse-kwargs (drop 1 form))]
    (str "line.new(" (str/join ", " (map expr->pine positional)) (emit-kwargs keyword) ")")))
(defmethod expr->pine :line.delete [form]
  (str "line.delete(" (expr->pine (second form)) ")"))
(defmethod expr->pine :label.new [form]
  (let [{:keys [positional keyword]} (parse-kwargs (drop 1 form))]
    (str "label.new(" (str/join ", " (map expr->pine positional)) (emit-kwargs keyword) ")")))
(defmethod expr->pine :box.new [form]
  (let [{:keys [positional keyword]} (parse-kwargs (drop 1 form))]
    (str "box.new(" (str/join ", " (map expr->pine positional)) (emit-kwargs keyword) ")")))

;; P2: Drawing object setters and deleters
(defmethod expr->pine :line.set-color    [form] (str "line.set_color(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :line.set-width    [form] (str "line.set_width(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :line.set-extend   [form] (str "line.set_extend(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :line.set-style    [form] (str "line.set_style(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :label.set-color   [form] (str "label.set_color(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :label.set-text    [form] (str "label.set_text(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :label.set-x       [form] (str "label.set_x(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :label.set-y       [form] (str "label.set_y(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :label.delete      [form] (str "label.delete(" (expr->pine (second form)) ")"))
(defmethod expr->pine :box.set-color     [form] (str "box.set_color(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :box.set-border-color [form] (str "box.set_border_color(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :box.set-width     [form] (str "box.set_width(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :box.delete        [form] (str "box.delete(" (expr->pine (second form)) ")"))

;; ─── P4: color.rgb / from-gradient / tostring ──────────────────────
(defmethod expr->pine :rgb [form]
  (str "color.rgb(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :from-gradient [form]
  (str "color.from_gradient(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2))
       ", " (expr->pine (nth form 3)) ", " (val->pine (nth form 4))
       ", " (val->pine (nth form 5)) ")"))
(defmethod expr->pine :tostring [form]
  (str "str.tostring(" (str/join ", " (map expr->pine (rest form))) ")"))

;; ─── P4: library export ────────────────────────────────────────────
(defmethod expr->pine :export [form]
  (str "export " (str/replace (name (second form)) #"-" "_")))

;; P5: Array methods ─────────────────────────────────────────────
(defmethod expr->pine :array-int   [form] (str "array.new_int(" (or (some-> (second form) str) "10") ")"))
(defmethod expr->pine :array-float [form]
  (str "array.new_float(" (or (some-> (second form) str) "10")
       (if (> (count form) 2) (str ", " (val->pine (nth form 2))) "") ")"))
(defmethod expr->pine :array-bool   [form] (str "array.new_bool(" (or (some-> (second form) str) "10") ")"))
(defmethod expr->pine :array-string [form] (str "array.new_string(" (or (some-> (second form) str) "10") ")"))
(defmethod expr->pine :array-color  [form] (str "array.new_color(" (or (some-> (second form) str) "10") ")"))
(defmethod expr->pine :array-line   [form] (str "array.new_line(" (or (some-> (second form) str) "10") ")"))
(defmethod expr->pine :array-label  [form] (str "array.new_label(" (or (some-> (second form) str) "10") ")"))
(defmethod expr->pine :array-box    [form] (str "array.new_box(" (or (some-> (second form) str) "10") ")"))
(defmethod expr->pine :array-table  [form] (str "array.new_table(" (or (some-> (second form) str) "10") ")"))
(defmethod expr->pine :array.push      [form] (str "array.push(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :array.pop       [form] (str "array.pop(" (expr->pine (second form)) ")"))
(defmethod expr->pine :array.size      [form] (str "array.size(" (expr->pine (second form)) ")"))
(defmethod expr->pine :array.get       [form]
  (str "array.get(" (expr->pine (second form)) ", " (expr->pine (nth form 2)) ")"))
(defmethod expr->pine :array.set       [form]
  (str "array.set(" (expr->pine (second form)) ", " (expr->pine (nth form 2)) ", " (expr->pine (nth form 3)) ")"))
(defmethod expr->pine :array.sort      [form] (str "array.sort(" (expr->pine (second form)) ")"))

;; Backward compat aliases (pre-array.* names)
(defmethod expr->pine :push  [form] (str "array.push(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :pop   [form] (str "array.pop(" (expr->pine (second form)) ")"))
(defmethod expr->pine :size  [form] (str "array.size(" (expr->pine (second form)) ")"))
(defmethod expr->pine :get   [form]
  (str "array.get(" (expr->pine (second form)) ", " (expr->pine (nth form 2)) ")"))
(defmethod expr->pine :set   [form]
  (str "array.set(" (expr->pine (second form)) ", " (expr->pine (nth form 2)) ", " (expr->pine (nth form 3)) ")"))
(defmethod expr->pine :sort  [form] (str "array.sort(" (expr->pine (second form)) ")"))

;; P3: array.fill / array.reverse
(defmethod expr->pine :array.fill   [form] (str "array.fill(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :array.reverse [form] (str "array.reverse(" (expr->pine (second form)) ")"))
;; P4: timestamp
(defmethod expr->pine :timestamp [form]
  (str "timestamp(" (str/join ", " (map expr->pine (rest form))) ")"))

;; P4: request.financial / request.random
(defmethod expr->pine :financial [form]
  (let [args (map expr->pine (rest form))]
    (str "request.financial(syminfo.tickerid, " (str/join ", " args) ")")))
(defmethod expr->pine :random [form]
  (str "request.random(" (str/join ", " (map expr->pine (rest form))) ")"))
;; P4: polygon
(defmethod expr->pine :polygon.new [form]
  (let [{:keys [positional keyword]} (parse-kwargs (drop 1 form))]
    (str "polygon.new(" (str/join ", " (map expr->pine positional)) (emit-kwargs keyword) ")")))

;; P4: Matrix
(defmethod expr->pine :matrix.new [form]
  (str "matrix.new<float>(" (str/join ", " (map expr->pine (rest form))) ")"))
;; P4: Map
(defmethod expr->pine :map.new [form] "map.new<color, int>()")
;; P4: time_close / time_tradingday
(defmethod expr->pine :time.close [form]
  (str "time_close" (when (number? (second form)) (str "[" (second form) "]"))))
(defmethod expr->pine :time.tradingday [form] "time_tradingday")
;; ─── P5: Table basics ──────────────────────────────────────────────
(defmethod expr->pine :table.new [form]
  (let [{:keys [keyword]} (parse-kwargs (drop 1 form))]
    (str "table.new(" (emit-kwargs keyword) ")")))
(defmethod expr->pine :table-cell [form]
  (str "table.cell(" (str/join ", " (map expr->pine (rest form))) ")"))

;; ─── P2: library() header ──────────────────────────────────────────
(defmethod expr->pine :library [form]
  (let [{:keys [keyword]} (parse-kwargs (drop 2 form))]
    (str "//@version=6\nlibrary(\"" (second form) "\"" (emit-kwargs keyword) ")")))

;; ─── P2: User-defined functions (defn) ─────────────────────────────
(defmethod expr->pine :defn [form]
  (let [[_ fname & rest] form]
    ;; Detect multi-arity: rest starts with a form whose first element is a vector
    ;; e.g., (defn my-ma ([x] body1) ([x p] body2))
    (if (and (seq rest) (list? (first rest)) (vector? (ffirst rest)))
      ;; Multi-arity: merge all param lists, use na? for shorter arities
      (let [arities (map (fn [arity]
                           (let [[params & body] arity]
                             {:params (vec (map clojure.core/name params))
                              :body body}))
                         rest)
            all-params (vec (distinct (mapcat :params arities)))
            name-str (str/replace (name fname) #"-" "_")
            param-str (str/join ", " all-params)
            ;; For each arity, build body with na defaults for missing params
            defaulted-bodies (map (fn [{:keys [params body]}]
                                    (let [extra-params (drop (count params) all-params)
                                          na-checks (map (fn [p] (list 'na (symbol p))) extra-params)]
                                      (if (seq na-checks)
                                        (list 'if (first na-checks)
                                              (first body)
                                              (first body))
                                        (first body))))
                                  arities)]
        (str name-str "(" param-str ") =>\n    "
             (str/join "\n    " (map expr->pine defaulted-bodies))))
      ;; Single arity (original behavior)
      (let [[_ fname args & body] form
            name-str (str/replace (name fname) #"-" "_")
            arg-str (str/join ", " (map clojure.core/name args))]
        (str name-str "(" arg-str ") =>\n    " (str/join "\n    " (map expr->pine body)))))))

;; ─── P2: for loop ──────────────────────────────────────────────────
(defmethod expr->pine :for [form]
  (let [[_ bindings & body] form]
    (if (and (vector? bindings) (= 3 (count bindings)))
      ;; (for [i 1 10] body) — literal range in binding vector
      (let [[i start end] bindings]
        (str "for " (name i) " = " start " to " end "\n"
             (str/join "\n" (map #(str "    " (expr->pine %)) body))))
      ;; (for [i (range 1 10)] body) — range function form
      (let [[i range-form] bindings
            [_ start end] range-form]
        (str "for " (name i) " = " start " to " end "\n"
             (str/join "\n" (map #(str "    " (expr->pine %)) body)))))))

;; ─── P2: while loop ────────────────────────────────────────────────
(defmethod expr->pine :while [form]
  (let [[_ cond & body] form]
    (str "while " (expr->pine cond) "\n"
         (str/join "\n" (map #(str "    " (expr->pine %)) body)))))

;; ─── P2: switch statement ──────────────────────────────────────────
(defmethod expr->pine :switch [form]
  (let [[_ switch-expr & cases] form
        expr-str (expr->pine switch-expr)]
    (loop [rem cases, lines [], idx 0]
      (if (empty? rem)
        (str/join "\n" lines)
        (let [val (first rem) action (second rem)]
          (if (= val :else)
            (let [line (str "    =>\n        " (expr->pine action))]
              (recur (drop 2 rem) (conj lines line) (inc idx)))
            (let [keyword (if (zero? idx) (str "switch " expr-str) "")
                  line (str keyword "\n    " (expr->pine val) " =>\n        " (expr->pine action))]
              (recur (drop 2 rem) (conj lines line) (inc idx)))))))))

;; ─── On-Bar Block ──────────────────────────────────────────────────

(defmethod expr->pine :on-bar [form]
  (str/join "\n\n"
    (map (fn [frm]
           (let [k (keyword (first frm))]
             (cond (= k :when)
                   (let [[_ c & a] frm]
                     (str "if " (expr->pine c) "\n"
                          (str/join "\n" (map #(str "    " (expr->pine %)) a))))
                   (= k :if) (expr->pine frm)
                   :else (expr->pine frm))))
         (rest form))))

;; ─── Public API ─────────────────────────────────────────────────────

(defn emit-file [forms]
  (let [groups (group-by #(keyword (first %)) forms)
        section (fn [kw] (get groups kw))
        inputs (mapcat section [:input-int :input-float :input-bool :input-string
                                :input-color :input-source :input-symbol :input-timeframe
                                :input-price :input-session])
        exits (section :exit)]
    (str/join "\n"
      (filter some?
        ["//@version=6" ""
         (when-let [s (first (section :strategy))]  (str (expr->pine s) "\n"))
         (when-let [s (first (section :indicator))] (str (expr->pine s) "\n"))
         (when-let [s (first (section :library))] (str (expr->pine s) "\n"))
         ;; Input declarations
         (when (seq inputs) (str (str/join "\n" (map expr->pine inputs)) "\n"))
         ;; Variable bindings: defvars, then defs, then defns, then multiset
         (when-let [v (seq (section :defvar))]  (str (str/join "\n" (map expr->pine v)) "\n"))
         (when-let [v (seq (section :defvarip))] (str (str/join "\n" (map expr->pine v)) "\n"))
         (when-let [v (seq (section :defn))]    (str (str/join "\n\n" (map expr->pine v)) "\n"))
         (when-let [v (seq (section :def))]     (str (str/join "\n" (map expr->pine v)) "\n"))
         (when-let [v (seq (section :multiset))] (str (str/join "\n" (map expr->pine v)) "\n"))
         (when-let [v (seq (section :security))] (str (str/join "\n" (map expr->pine v)) "\n"))
         ;; Switch blocks
         (when-let [v (seq (section :switch))]  (str (str/join "\n\n" (map expr->pine v)) "\n"))
         ;; Set! assignments (inside on-bar before logic)
         (when-let [v (seq (section :set!))]    (str (str/join "\n" (map expr->pine v)) "\n"))
         (when-let [v (seq (section :export))]  (str (str/join "\n" (map expr->pine v)) "\n"))
         ;; On-bar logic
         (when-let [o (first (section :on-bar))] (str (expr->pine o) "\n"))
         ;; Exits
         (when (seq exits)   (str (str/join "\n" (map expr->pine exits)) "\n"))
         ;; Plots
         (when-let [p (seq (section :plot))] (str (str/join "\n" (map expr->pine p)) "\n"))
         (str/join "\n" (map expr->pine (section :hline)))
         (str/join "\n" (map expr->pine (section :plotshape)))
         (str/join "\n" (map expr->pine (section :fill)))
         (str/join "\n" (map expr->pine (section :bgcolor)))
         (str/join "\n" (map expr->pine (section :barcolor)))
         (str/join "\n" (map expr->pine (section :alertcondition)))
         (str/join "\n" (map expr->pine (section :table.new)))
         (str/join "\n" (map expr->pine (section :table-cell)))
         (str/join "\n" (map expr->pine (section :line.new)))
         (str/join "\n" (map expr->pine (section :label.new)))
         (str/join "\n" (map expr->pine (section :box.new)))
         ;; Inline function expansions (do blocks from definline)
         (str/join "\n" (map expr->pine (section :do)))]))))
;; ta.cmo / ta.wad
(defmethod expr->pine :cmo [form] (indicator-call form "ta.cmo" "close"))
(defmethod expr->pine :wad [form]
  (if (> (count form) 1)
    (str "ta.wad(" (str/join ", " (map expr->pine (rest form))) ")")
    "ta.wad"))
;; P3: ta.mama
(defmethod expr->pine :mama [form]
  (let [{:keys [positional]} (parse-kwargs (drop 1 form))
        f (first positional)
        has-src (and f (or (symbol? f) (keyword? f) (list? f)))
        src (if has-src (resolve-source f) "close")
        args (if has-src (rest positional) positional)
        fl (or (some-> (first args) str) "0.5")
        sl (or (some-> (second args) str) "0.05")]
    (str "ta.mama(" src ", " fl ", " sl ")")))
;; P4: ta.cog
(defmethod expr->pine :cog [form] (indicator-call form "ta.cog" "close"))
;; P4: ta.vwmacd
(defmethod expr->pine :vwmacd [form]
  (let [{:keys [positional]} (parse-kwargs (drop 1 form))
        f (first positional)
        has-src (and f (or (symbol? f) (keyword? f) (list? f)))
        src (if has-src (resolve-source f) "close")
        args (if has-src (rest positional) positional)]
    (str "ta.vwmacd(" src ", " (or (some-> (first args) str) "12") ", "
         (or (some-> (second args) str) "26") ", "
         (or (when (< 2 (count args)) (some-> (nth args 2) str)) "9") ")")))

;; ta.crossover / ta.crossunder (raw)
(defmethod expr->pine :crossover [form]
  (str "ta.crossover(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :crossunder [form]
  (str "ta.crossunder(" (str/join ", " (map expr->pine (rest form))) ")"))
;; ═══════════════════════════════════════════════════════════════════
;; Last missing features — P3 & P4
;; ═══════════════════════════════════════════════════════════════════

;; math.phi constant
(defmethod expr->pine :phi [form] "math.phi")

;; request.seed
(defmethod expr->pine :seed [form] (str "request.seed(" (str/join ", " (map expr->pine (rest form))) ")"))
;; Matrix type variants (explicit defmethods for SCI compat)
(defmethod expr->pine :matrix.float  [form] (str "matrix.new<float>("  (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :matrix.int    [form] (str "matrix.new<int>("    (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :matrix.bool   [form] (str "matrix.new<bool>("   (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :matrix.string [form] (str "matrix.new<string>(" (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :matrix.color  [form] (str "matrix.new<color>("  (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :matrix.line   [form] (str "matrix.new<line>("   (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :matrix.label  [form] (str "matrix.new<label>("  (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :matrix.box    [form] (str "matrix.new<box>("    (str/join ", " (map expr->pine (rest form))) ")"))
(defmethod expr->pine :matrix.table  [form] (str "matrix.new<table>("  (str/join ", " (map expr->pine (rest form))) ")"))

;; ticker.modify
(defmethod expr->pine :ticker.modify [form]
  (str "ticker.modify(" (str/join ", " (map expr->pine (rest form))) ")"))
