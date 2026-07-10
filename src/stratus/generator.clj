(ns stratus.generator
  "AST → Pine Script v6.  Multimethod dispatches on form head symbol."
  (:require [clojure.string :as str]))

(declare expr->pine)

;; ─── Helpers ─────────────────────────────────────────────────────────

(def builtin-sources
  {:close "close", :high "high", :low "low", :open "open",
   :volume "volume", :hl2 "hl2", :hlc3 "hlc3", :ohlc4 "ohlc4"})

(def resolve-source
  (fn [form]
    (cond (symbol? form)   (or (builtin-sources form) (name form))
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

(def lookup-tables
  (merge {:red "color.red", :green "color.green", :blue "color.blue",
          :yellow "color.yellow", :orange "color.orange", :purple "color.purple",
          :pink "color.pink", :gray "color.gray", :white "color.white",
          :black "color.black", :navy "color.navy", :teal "color.teal",
          :lime "color.lime", :maroon "color.maroon"}
         {:triangle-up "shape.triangleup", :triangle-down "shape.triangledown"}
         {:top "location.top", :bottom "location.bottom"}
         {:solid "hline.style_solid", :dashed "hline.style_dashed"}))

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
      (map (fn [e] (str (name (key e)) "=" (val->pine (val e)))) kwargs)))))

(defn plot-call [pine-fn form]
  (let [{:keys [positional keyword]} (parse-kwargs (drop 2 form))
        pos-str (when (seq positional)
                  (str ", " (str/join ", " (map val->pine positional))))]
    (str pine-fn "(" (expr->pine (second form)) pos-str
         (emit-kwargs keyword) ")")))

;; ─── Dispatch ────────────────────────────────────────────────────────

(defmulti expr->pine
  (fn [form]
    (if (list? form)
      (let [k (keyword (first form))
            price-syms #{:close :high :low :open :volume :hl2 :hlc3 :ohlc4}]
        (if (and (price-syms k) (not (string? (second form)))) ::price-ref k))
      ::literal)))

(defmethod expr->pine ::price-ref [form]
  (str (name (first form)) (when (number? (second form)) (str "[" (second form) "]"))))

(defmethod expr->pine ::literal [form]
  (cond (string? form) (str "\"" form "\"")
        (number? form) (str form)
        (keyword? form) (name form)
        (symbol? form) (get builtin-sources (keyword (name form)) (name form))
        (list? form) (expr->pine form)
        (true? form) "true" (false? form) "false" (nil? form) "na"
        :otherwise (str form)))

;; ─── Strategy / Indicator Headers ───────────────────────────────────

(defmethod expr->pine :strategy [form]
  (str "strategy(\"" (second form) "\""
       (emit-kwargs (:keyword (parse-kwargs (drop 2 form)))) ")"))

(defmethod expr->pine :indicator [form]
  (str "indicator(\"" (second form) "\""
       (emit-kwargs (:keyword (parse-kwargs (drop 2 form)))) ")"))

(defmethod expr->pine :def [form]
  (str (name (second form)) " = " (expr->pine (nth form 2))))

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

;; ─── Conditions ─────────────────────────────────────────────────────

(defmethod expr->pine :crosses-above [form]
  (str "ta.cross(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2))
       ") and " (expr->pine (nth form 1)) " > " (expr->pine (nth form 2))))
(defmethod expr->pine :crosses-below [form]
  (str "ta.cross(" (expr->pine (nth form 1)) ", " (expr->pine (nth form 2))
       ") and " (expr->pine (nth form 1)) " < " (expr->pine (nth form 2))))
(defmethod expr->pine :rising  [form] (str "rising(" (expr->pine (second form)) ", 1)"))
(defmethod expr->pine :falling [form] (str "falling(" (expr->pine (second form)) ", 1)"))

;; ─── Logic ──────────────────────────────────────────────────────────

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

;; ─── Plotting & Alerts ──────────────────────────────────────────────

(defmethod expr->pine :plot           [form] (plot-call "plot" form))
(defmethod expr->pine :plotshape      [form] (plot-call "plotshape" form))
(defmethod expr->pine :hline          [form] (plot-call "hline" form))
(defmethod expr->pine :bgcolor        [form] (plot-call "bgcolor" form))
(defmethod expr->pine :barcolor       [form] (plot-call "barcolor" form))
(defmethod expr->pine :alertcondition [form] (plot-call "alertcondition" form))

;; ─── On-Bar Block ───────────────────────────────────────────────────

(defmethod expr->pine :on-bar [form]
  (str/join "\n\n"
    (map (fn [frm]
           (if (and (list? frm) (= :when (keyword (first frm))))
             (let [[_ c & a] frm]
               (str "if " (expr->pine c) "\n"
                    (str/join "\n" (map #(str "    " (expr->pine %)) a))))
             (expr->pine frm)))
         (rest form))))

;; ─── Public API ─────────────────────────────────────────────────────

(defn kw-first [coll kw] (= (keyword (first coll)) kw))

(defn emit-file [forms]
  (let [by-type (fn [kw] (filter #(kw-first % kw) forms))
        strat  (first (by-type :strategy)),  indic (first (by-type :indicator))
        defs   (by-type :def),               onbar (first (by-type :on-bar))
        plots  (by-type :plot),              hls   (by-type :hline)
        shps   (by-type :plotshape),         alts  (by-type :alertcondition)
        bg     (by-type :bgcolor),           bar   (by-type :barcolor)]
    (str/join "\n"
      (filter some?
        ["//@version=6" ""
         (when strat  (str (expr->pine strat) "\n"))
         (when indic  (str (expr->pine indic) "\n"))
         (when (seq defs) (str (str/join "\n" (map expr->pine defs)) "\n"))
         (when onbar  (str (expr->pine onbar) "\n"))
         (when (seq plots) (str (str/join "\n" (map expr->pine plots)) "\n"))
         (str/join "\n" (map expr->pine hls))
         (str/join "\n" (map expr->pine shps))
         (str/join "\n" (map expr->pine bg))
         (str/join "\n" (map expr->pine bar))
         (str/join "\n" (map expr->pine alts))]))))
