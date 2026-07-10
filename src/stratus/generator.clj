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
   :timeframe "timeframe.period"})

(def barstate-builtins
  {:bar-confirmed "barstate.isconfirmed", :bar-first "barstate.isfirst",
   :bar-last "barstate.islast"})

(def strategy-builtins
  {:position-size "strategy.position_size", :position-avg-price "strategy.position_avg_price",
   :open-trades "strategy.opentrades", :equity "strategy.equity",
   :net-profit "strategy.netprofit"})

(def resolve-source
  (fn [form]
    (cond (symbol? form)   (or (builtin-sources form) (strategy-builtins form) (name form))
          (keyword? form)  (or (builtin-sources form) (name form))
          (list? form)     (expr->pine form)
          :otherwise       (throw (ex-info "Unknown source" {:form form})))))

(defn parse-kwargs [args]
  (loop [pos [], kw {}, rem args]
    (if (empty? rem)
      {:positional pos, :keyword kw}
      (if (keyword? (first rem))
        (if (next rem)
          (recur pos (assoc kw (first rem) (second rem)) (drop 2 rem))
          (recur pos kw (rest rem)))
        (recur (conj pos (first rem)) kw (rest rem))))))

(defn parse-kwargs-until [args stop-kw]
  "Like parse-kwargs but stops processing at a specific keyword (e.g. :else)."
  (loop [pos [], kw {}, rem args]
    (if (or (empty? rem) (= (first rem) stop-kw))
      {:positional pos, :keyword kw, :remaining rem}
      (if (keyword? (first rem))
        (if (next rem)
          (recur pos (assoc kw (first rem) (second rem)) (drop 2 rem))
          (recur pos kw (rest rem)))
        (recur (conj pos (first rem)) kw (rest rem))))))

(def plot-style-map
  {:line "plot.style_line", :histogram "plot.style_histogram", :area "plot.style_area",
   :columns "plot.style_columns", :circles "plot.style_circles", :cross "plot.style_cross",
   :step-line "plot.style_stepline", :step-line-diamond "plot.style_stepline_diamond"})

(def lookup-tables
  (merge {:red "color.red", :green "color.green", :blue "color.blue",
          :yellow "color.yellow", :orange "color.orange", :purple "color.purple",
          :pink "color.pink", :gray "color.gray", :white "color.white",
          :black "color.black", :navy "color.navy", :teal "color.teal",
          :lime "color.lime", :maroon "color.maroon"}
         {:triangle-up "shape.triangleup", :triangle-down "shape.triangledown"}
         {:top "location.top", :bottom "location.bottom", :absolute "location.absolute"}
         {:solid "hline.style_solid", :dashed "hline.style_dashed", :dotted "hline.style_dotted"}
         plot-style-map))

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
      (map (fn [e] (let [k (name (key e))
                         v (val->pine (val e))]
                     (str (clojure.string/replace k #"-" "_") "=" v)))
           kwargs)))))

(defn plot-call [pine-fn form]
  (let [{:keys [positional keyword]} (parse-kwargs (drop 2 form))]
    (str pine-fn "(" (expr->pine (second form))
         (when (seq positional) (str ", " (str/join ", " (map val->pine positional))))
         (emit-kwargs keyword) ")")))

;; ─── Dispatch ────────────────────────────────────────────────────────

(defmulti expr->pine
  (fn [form]
    (if (list? form)
      (let [k (keyword (first form))
            price-syms #{:close :high :low :open :volume :hl2 :hlc3 :ohlc4}
            strat-syms #{:position-size :position-avg-price :open-trades :equity :net-profit}]
        (cond (and (price-syms k) (not (string? (second form)))) ::price-ref
              (strat-syms k) ::strat-builtin
              (time-builtins k) ::time-ref
              (barstate-builtins k) ::barstate-ref
              :else k))
      ::literal)))

(defmethod expr->pine ::price-ref [form]
  (str (name (first form)) (when (number? (second form)) (str "[" (second form) "]"))))

(defmethod expr->pine ::strat-builtin [form]
  (strategy-builtins (keyword (first form))))

(defmethod expr->pine ::time-ref [form]
  (time-builtins (keyword (first form))))

(defmethod expr->pine ::barstate-ref [form]
  (barstate-builtins (keyword (first form))))

(defmethod expr->pine ::literal [form]
  (cond (string? form) (str "\"" form "\"")
        (number? form) (str form)
        (keyword? form) (name form)
        (symbol? form) (or (builtin-sources (keyword (name form)))
                           (strategy-builtins (keyword (name form)))
                           (time-builtins (keyword (name form)))
                           (barstate-builtins (keyword (name form)))
                           (str/replace (name form) #"-" "_"))
        (list? form) (expr->pine form)
        (true? form) "true" (false? form) "false" (nil? form) "na"
        :otherwise (str form)))

(defmethod expr->pine :default [form]
  (let [fn-name (name (first form))
        args (rest form)]
    (str (str/replace fn-name #"-" "_") "(" (str/join ", " (map expr->pine args)) ")")))

;; ─── Strategy / Indicator / Library Headers ─────────────────────────

(defmethod expr->pine :strategy [form]
  (str "strategy(\"" (second form) "\""
       (emit-kwargs (:keyword (parse-kwargs (drop 2 form)))) ")"))

(defmethod expr->pine :indicator [form]
  (str "indicator(\"" (second form) "\""
       (emit-kwargs (:keyword (parse-kwargs (drop 2 form)))) ")"))

;; ─── Variable Bindings ──────────────────────────────────────────────

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
(defmethod expr->pine :alma [form]
  (let [p (or (some-> (second form) str) "10")
        o (or (some-> (nth form 2) str) "6")
        o2 (or (some-> (nth form 3) str) "0.85")]
    (str "ta.alma(close, " p ", " o ", " o2 ")")))

;; P0: highest / lowest
(defmethod expr->pine :highest [form]
  (let [s (resolve-source (second form))
        p (or (some-> (nth form 2) str) "20")]
    (str "ta.highest(" s ", " p ")")))
(defmethod expr->pine :lowest [form]
  (let [s (resolve-source (second form))
        p (or (some-> (nth form 2) str) "20")]
    (str "ta.lowest(" s ", " p ")")))

;; P1: cum / highestbars / lowestbars / sum / avg / fixnan / valuewhen
(defmethod expr->pine :cum       [form] (str "math.cum(" (expr->pine (second form)) ")"))
(defmethod expr->pine :highestbars [form]
  (str "ta.highestbars(" (expr->pine (second form)) ", " (or (some-> (nth form 2) str) "20") ")"))
(defmethod expr->pine :lowestbars [form]
  (str "ta.lowestbars(" (expr->pine (second form)) ", " (or (some-> (nth form 2) str) "20") ")"))
(defmethod expr->pine :sum  [form] (str "math.sum(" (expr->pine (second form)) ", " (or (some-> (nth form 2) str) "20") ")"))
(defmethod expr->pine :avg  [form] (str "math.avg(" (expr->pine (second form)) ", " (or (some-> (nth form 2) str) "20") ")"))
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
                                 :from :from
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

;; ─── P0: request.security() ─────────────────────────────────────────

(defmethod expr->pine :security [form]
  (let [tf (second form)
        expr (nth form 2)]
    (str "request.security(syminfo.tickerid, \"" tf "\", " (expr->pine expr) ")")))

;; ─── P0: if/else multi-branch ──────────────────────────────────────

(defmethod expr->pine :if [form]
  (let [branches (rest form)]
    (loop [rem branches, lines [], depth 0]
      (if (empty? rem)
        (str/join "\n" lines)
        (let [cond (first rem)
              action (second rem)]
          (if (= cond :else)
            ;; else clause: one or more actions
            (let [actions (if (and (list? action) (= (first action) :do)) (rest action) [action])
                  indent (apply str (repeat depth "    "))
                  action-lines (map #(str indent "    " (expr->pine %)) actions)]
              (recur (drop 2 rem) (into lines (cons (str indent "else") action-lines)) depth))
            ;; if or else-if clause
            (let [prefix (if (zero? depth) "if " "else if ")
                  actions (if (and (list? action) (= (first action) :do)) (rest action) [action])
                  indent (apply str (repeat depth "    "))
                  action-lines (map #(str indent "    " (expr->pine %)) actions)]
              (recur (drop 2 rem) (into lines (cons (str prefix (expr->pine (first rem))) action-lines)) (inc depth)))))))))

;; ─── On-Bar Block (extended with if/else support) ──────────────────

;; ─── P2: library() header ──────────────────────────────────────────
(defmethod expr->pine :library [form]
  (let [{:keys [keyword]} (parse-kwargs (drop 2 form))]
    (str "//@version=6\nlibrary(\"" (second form) "\"" (emit-kwargs keyword) ")")))

;; ─── P2: User-defined functions (defn) ─────────────────────────────
(defmethod expr->pine :defn [form]
  (let [[_ fname args & body] form
        name-str (str/replace (name fname) #"-" "_")
        arg-str (str/join ", " (map clojure.core/name args))]
    (str name-str "(" arg-str ") =>\n    " (str/join "\n    " (map expr->pine body)))))

;; ─── P2: for loop ──────────────────────────────────────────────────
(defmethod expr->pine :for [form]
  (let [[_ [i [_ start end]] & body] form]
    (str "for " (name i) " = " start " to " end "\n"
         (str/join "\n" (map #(str "    " (expr->pine %)) body)))))

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

(defn kw-first [coll kw] (= (keyword (first coll)) kw))

(defn emit-file [forms]
  (let [by-type  (fn [kw] (filter #(kw-first % kw) forms))
        defs     (by-type :def)
        defns    (by-type :defn)
        defvars  (by-type :defvar)
        defvips  (by-type :defvarip)
        sets     (by-type :set!)
        multis   (by-type :multiset)
        secs     (by-type :security)
        exits    (by-type :exit)]
    (str/join "\n"
      (filter some?
        ["//@version=6" ""
         (when-let [s (first (by-type :strategy))]  (str (expr->pine s) "\n"))
         (when-let [s (first (by-type :indicator))] (str (expr->pine s) "\n"))
         (when-let [s (first (by-type :library))] (str (expr->pine s) "\n"))
         ;; Variable bindings: defvars, then defs, then defns, then multiset
         (when (seq defvars) (str (str/join "\n" (map expr->pine defvars)) "\n"))
         (when (seq defvips) (str (str/join "\n" (map expr->pine defvips)) "\n"))
         (when (seq defns)   (str (str/join "\n\n" (map expr->pine defns)) "\n"))
         (when (seq defs)    (str (str/join "\n" (map expr->pine defs)) "\n"))
         (when (seq multis)  (str (str/join "\n" (map expr->pine multis)) "\n"))
         (when (seq secs)    (str (str/join "\n" (map expr->pine secs)) "\n"))
         ;; Set! assignments (inside on-bar before logic)
         (when (seq sets)    (str (str/join "\n" (map expr->pine sets)) "\n"))
         ;; On-bar logic
         (when-let [o (first (by-type :on-bar))] (str (expr->pine o) "\n"))
         ;; Exits
         (when (seq exits)   (str (str/join "\n" (map expr->pine exits)) "\n"))
         ;; Plots
         (when-let [p (seq (by-type :plot))]           (str (str/join "\n" (map expr->pine p)) "\n"))
         (str/join "\n" (map expr->pine (by-type :hline)))
         (str/join "\n" (map expr->pine (by-type :plotshape)))
         (str/join "\n" (map expr->pine (by-type :fill)))
         (str/join "\n" (map expr->pine (by-type :bgcolor)))
         (str/join "\n" (map expr->pine (by-type :barcolor)))
         (str/join "\n" (map expr->pine (by-type :alertcondition)))]))))
