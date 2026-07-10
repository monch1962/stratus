(ns stratus.generator
  "AST → Pine Script code generation.
   Takes parsed DSL forms and emits idiomatic Pine Script v6."
  (:require [stratus.constructs :as ct]
            [stratus.templates :as tpl]
            [clojure.string :as str]))

(declare expr->pine)

;; ─── Source Resolution ───────────────────────────────────────────────

(def builtin-sources
  {:close "close", :high "high", :low "low", :open "open",
   :volume "volume", :hl2 "hl2", :hlc3 "hlc3", :ohlc4 "ohlc4"})

(defn resolve-source [form]
  (cond (symbol? form)   (or (builtin-sources form) (name form))
        (keyword? form)  (or (builtin-sources form) (name form))
        (list? form)     (expr->pine form)
        :otherwise       (throw (ex-info "Unknown source" {:form form}))))

(defn parse-kwargs [args]
  (loop [pos [], kw {}, rem args]
    (if (empty? rem)
      {:positional pos, :keyword kw}
      (if (keyword? (first rem))
        (if (next rem)
          (recur pos (assoc kw (first rem) (second rem)) (drop 2 rem))
          (recur pos kw (rest rem)))
        (recur (conj pos (first rem)) kw (rest rem))))))

(def color-map
  {:red "color.red", :green "color.green", :blue "color.blue",
   :yellow "color.yellow", :orange "color.orange", :purple "color.purple",
   :pink "color.pink", :gray "color.gray", :white "color.white",
   :black "color.black", :navy "color.navy", :teal "color.teal",
   :lime "color.lime", :maroon "color.maroon"})

(def shape-style-map
  {:triangle-up "shape.triangleup", :triangle-down "shape.triangledown",
   :circle "shape.circle", :cross "shape.cross", :diamond "shape.diamond",
   :square "shape.square", :arrow-up "shape.arrowup", :arrow-down "shape.arrowdown"})

(def location-map
  {:top "location.top", :bottom "location.bottom",
   :abovebar "location.abovebar", :belowbar "location.belowbar"})

(def line-style-map
  {:solid "hline.style_solid", :dashed "hline.style_dashed", :dotted "hline.style_dotted"})

(defn val->pine [v]
  (cond (or (keyword? v) (symbol? v)) (let [s (name v)]
                                        (or (get color-map (keyword s))
                                            (get shape-style-map (keyword s))
                                            (get location-map (keyword s))
                                            (get line-style-map (keyword s))
                                            (str "color." s)))
        (string? v)  (str "\"" v "\"")
        (number? v)  (str v)
        (true? v)    "true"
        (false? v)   "false"
        (nil? v)     "na"
        :otherwise   (expr->pine v)))

(defn emit-kwarg-pairs [kwargs]
  (when (seq kwargs)
    (str ", " (str/join ", "
      (map (fn [entry]
             (let [k (key entry) v (val entry)]
               (str (name k) "=" (val->pine v))))
           kwargs)))))

;; ─── Multimethod Dispatch ───────────────────────────────────────────

(defmulti expr->pine
  (fn [form]
    (if (list? form)
      (let [k (keyword (first form))]
        (if (and (= k :close) (not (string? (second form)))) ::price-ref k))
      ::literal)))

;; Price reference: (close), (close 1), (high), (volume) etc
(defmethod expr->pine ::price-ref [form]
  (let [head (name (first form))
        idx (when (number? (second form)) (str "[" (second form) "]"))]
    (str head (or idx ""))))

(defmethod expr->pine ::literal [form]
  (cond (string? form) (str "\"" form "\"")
        (number? form) (str form)
        (keyword? form) (name form)
        (symbol? form) (get builtin-sources (keyword (name form)) (name form))
        (list? form) (expr->pine form)
        (true? form) "true"
        (false? form) "false"
        (nil? form) "na"
        :otherwise (str form)))

;; ─── Strategy / Indicator Headers ───────────────────────────────────

(defmethod expr->pine :strategy [form]
  (let [[_ name & rest] form
        {:keys [keyword]} (parse-kwargs rest)]
    (str "strategy(\"" name "\"" (emit-kwarg-pairs keyword) ")")))

(defmethod expr->pine :indicator [form]
  (let [[_ name & rest] form
        {:keys [keyword]} (parse-kwargs rest)]
    (str "indicator(\"" name "\"" (emit-kwarg-pairs keyword) ")")))

(defmethod expr->pine :def [form]
  (str (name (second form)) " = " (expr->pine (nth form 2))))

;; ─── Indicators ─────────────────────────────────────────────────────

(defn indicator-call [form fn-name default-src]
  (let [[_ & args] form
        {:keys [positional keyword]} (parse-kwargs args)
        first-pos (first positional)
        has-source (and first-pos (or (symbol? first-pos) (keyword? first-pos) (list? first-pos)))
        src (if has-source (resolve-source first-pos) default-src)
        remaining (if has-source (rest positional) positional)
        pos-args (map expr->pine remaining)]
    (str fn-name "(" src (when (seq pos-args) (str ", " (str/join ", " pos-args))) ")")))

(defmethod expr->pine :sma [form]  (indicator-call form "ta.sma" "close"))
(defmethod expr->pine :ema [form]  (indicator-call form "ta.ema" "close"))
(defmethod expr->pine :rsi [form]  (indicator-call form "ta.rsi" "close"))

(defmethod expr->pine :macd [form]
  (let [[_ & args] form {:keys [keyword]} (parse-kwargs args)
        fast (or (:fast keyword) 12) slow (or (:slow keyword) 26) sig (or (:signal keyword) 9)]
    (str "ta.macd(close, " fast ", " slow ", " sig ")")))

(defmethod expr->pine :adx [form]
  (str "ta.adx(high, low, close, " (or (some-> (second form) str) "14") ")"))

(defmethod expr->pine :stoch [form]
  (str "ta.stoch(close, high, low, " (or (some-> (second form) str) "14") ")"))

(defmethod expr->pine :bb [form]
  (let [period (or (some-> (second form) str) "20")
        mult   (or (some-> (nth form 2 nil) str) "2.0")]
    (str "ta.bb(close, " period ", " mult ")")))

(defmethod expr->pine :atr [form]
  (str "ta.atr(" (or (some-> (second form) str) "14") ")"))

;; ─── Conditions ─────────────────────────────────────────────────────

(defmethod expr->pine :crosses-above [form]
  (let [[_ a b] form]
    (str "ta.cross(" (expr->pine a) ", " (expr->pine b) ") and " (expr->pine a) " > " (expr->pine b))))

(defmethod expr->pine :crosses-below [form]
  (let [[_ a b] form]
    (str "ta.cross(" (expr->pine a) ", " (expr->pine b) ") and " (expr->pine a) " < " (expr->pine b))))

(defmethod expr->pine :rising [form]  (str "rising(" (expr->pine (second form)) ", 1)"))
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

(defmethod expr->pine :long [form]
  (str "strategy.entry(\"" (or (second form) "Long") "\", strategy.long)"))

(defmethod expr->pine :short [form]
  (str "strategy.entry(\"" (or (second form) "Short") "\", strategy.short)"))

(defmethod expr->pine :close [form]
  (if-let [label (second form)] (str "strategy.close(\"" label "\")") "strategy.close()"))

;; ─── Plotting & Alerts ──────────────────────────────────────────────

(defmethod expr->pine :plot [form]
  (str "plot(" (expr->pine (second form))
       (emit-kwarg-pairs (:keyword (parse-kwargs (drop 2 form)))) ")"))

(defmethod expr->pine :plotshape [form]
  (str "plotshape(" (expr->pine (second form))
       (emit-kwarg-pairs (:keyword (parse-kwargs (drop 2 form)))) ")"))

(defmethod expr->pine :hline [form]
  (str "hline(" (expr->pine (second form))
       (emit-kwarg-pairs (:keyword (parse-kwargs (drop 2 form)))) ")"))

(defmethod expr->pine :bgcolor [form]
  (str "bgcolor(" (expr->pine (second form))
       (emit-kwarg-pairs (:keyword (parse-kwargs (drop 2 form)))) ")"))

(defmethod expr->pine :barcolor [form]
  (str "barcolor(" (expr->pine (second form))
       (emit-kwarg-pairs (:keyword (parse-kwargs (drop 2 form)))) ")"))

(defmethod expr->pine :alertcondition [form]
  (str "alertcondition(" (expr->pine (second form))
       (emit-kwarg-pairs (:keyword (parse-kwargs (drop 2 form)))) ")"))

;; ─── On-Bar Block ───────────────────────────────────────────────────

(defmethod expr->pine :on-bar [form]
  (str/join "\n\n"
    (map (fn [frm]
           (if (and (list? frm) (= (keyword (first frm)) :when))
             (let [[_ cond & actions] frm]
               (str "if " (expr->pine cond) "\n"
                    (str/join "\n" (map #(str "    " (expr->pine %)) actions))))
             (expr->pine frm)))
         (rest form))))

;; ─── Public API ─────────────────────────────────────────────────────

(defn kw-first [coll kw]
  (= (keyword (first coll)) kw))

(defn emit-file [forms]
  (let [strat   (first (filter #(kw-first % :strategy) forms))
        indic   (first (filter #(kw-first % :indicator) forms))
        defs    (filter #(kw-first % :def) forms)
        onbar   (first (filter #(kw-first % :on-bar) forms))
        plots   (filter #(kw-first % :plot) forms)
        hlines  (filter #(kw-first % :hline) forms)
        shapes  (filter #(kw-first % :plotshape) forms)
        alerts  (filter #(kw-first % :alertcondition) forms)
        bgcs    (filter #(kw-first % :bgcolor) forms)
        barcs   (filter #(kw-first % :barcolor) forms)]
    (str/join "\n"
      (filter some?
        ["//@version=6" ""
         (when strat (str (expr->pine strat) "\n"))
         (when indic (str (expr->pine indic) "\n"))
         (when (seq defs) (str (str/join "\n" (map expr->pine defs)) "\n"))
         (when onbar (str (expr->pine onbar) "\n"))
         (when (seq plots) (str (str/join "\n" (map expr->pine plots)) "\n"))
         (str/join "\n" (map expr->pine hlines))
         (str/join "\n" (map expr->pine shapes))
         (str/join "\n" (map expr->pine bgcs))
         (str/join "\n" (map expr->pine barcs))
         (str/join "\n" (map expr->pine alerts))]))))
