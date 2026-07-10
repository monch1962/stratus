(ns stratus.importer
  "Pine Script v6 → Stratus DSL converter."
  (:require [clojure.string :as str]))

(declare convert-expr to-pine-val to-kebab)

(def indicator-patterns
  [[#"ta\.sma\(([^,]+),?\s*([^)]*)\)"          "(sma $1$2)"]
   [#"ta\.ema\(([^,]+),?\s*([^)]*)\)"          "(ema $1$2)"]
   [#"ta\.rsi\(([^,]+),?\s*([^)]*)\)"          "(rsi $1$2)"]
   [#"ta\.macd\(([^,]+),\s*([^,]+),\s*([^,]+),\s*([^)]+)\)" "(macd :fast $2 :slow $3 :signal $4)"]
   [#"ta\.atr\(([^)]*)\)"                       "(atr $1)"]
   [#"ta\.adx\([^,]+,\s*[^,]+,\s*[^,]+,\s*([^)]+)\)"  "(adx $1)"]
   [#"ta\.stoch\([^,]+,\s*[^,]+,\s*[^,]+,\s*([^,]+),\s*([^,]+),\s*([^)]+)\)" "(stoch $1)"]
   [#"ta\.bb\([^,]+,\s*([^,]+),\s*([^)]+)\)"   "(bb $1 $2)"]
   [#"ta\.supertrend\(([^,]+),\s*([^)]+)\)"     "(supertrend $1 $2)"]
   [#"ta\.sar\(([^,]+),\s*([^)]+)\)"            "(sar $1 $2)"]
   [#"ta\.vwap\(([^)]*)\)"                      "(vwap $1)"]
   [#"ta\.wma\([^,]+,\s*([^)]+)\)"              "(wma $1)"]
   [#"ta\.hma\([^,]+,\s*([^)]+)\)"              "(hma $1)"]
   [#"ta\.vwma\([^,]+,\s*([^)]+)\)"             "(vwma $1)"]
   [#"ta\.alma\([^,]+,\s*([^,]+),\s*([^,]+),\s*([^)]+)\)" "(alma $1 $2 $3)"]
   [#"ta\.cci\([^,]+,\s*([^)]+)\)"              "(cci $1)"]
   [#"ta\.mfi\([^,]+,\s*([^)]+)\)"              "(mfi $1)"]
   [#"ta\.obv"                                   "(obv)"]
   [#"ta\.linreg\([^,]+,\s*([^)]+)\)"           "(linreg $1)"]
   [#"ta\.highest\(([^,]+),\s*([^)]+)\)"        "(highest $1 $2)"]
   [#"ta\.lowest\(([^,]+),\s*([^)]+)\)"         "(lowest $1 $2)"]
   [#"ta\.correlation\(([^,]+),\s*([^,]+),\s*([^)]+)\)" "(correlation $1 $2 $3)"]
   [#"ta\.covariance\(([^,]+),\s*([^,]+),\s*([^)]+)\)" "(covariance $1 $2 $3)"]
   [#"ta\.median\([^,]+,\s*([^)]+)\)"           "(median $1)"]
   [#"ta\.mode\([^,]+,\s*([^)]+)\)"             "(mode $1)"]
   [#"ta\.percentile_nearest_rank\([^,]+,\s*([^,]+),\s*([^)]+)\)" "(percentile $1 $2)"]
   [#"ta\.valuewhen\(([^,]+),\s*([^)]+)\)"      "(valuewhen $1 $2)"]])

(defn to-pine-val
  "Map a Pine Script value to Stratus DSL."
  [v]
  (or (when v (get {"color.red" "red", "color.green" "green", "color.blue" "blue",
                    "color.yellow" "yellow", "color.orange" "orange", "color.purple" "purple",
                    "color.pink" "pink", "color.gray" "gray", "color.white" "white",
                    "color.black" "black", "color.navy" "navy", "color.teal" "teal",
                    "color.lime" "lime", "color.maroon" "maroon"} v))
      (when v (get {"hline.style_dashed" "dashed", "hline.style_dotted" "dotted",
                    "hline.style_solid" "solid", "plot.style_histogram" "histogram",
                    "plot.style_area" "area", "plot.style_columns" "columns",
                    "plot.style_circles" "circles", "plot.style_cross" "cross",
                    "plot.style_stepline" "step-line", "shape.triangleup" "triangle-up",
                    "shape.triangledown" "triangle-down", "location.top" "top",
                    "location.bottom" "bottom", "location.absolute" "absolute"} v))
      (when (and v (str/starts-with? v "color.")) (subs v 6))
      v))

(defn to-kebab
  "Convert camelCase or snake_case to kebab-case."
  [s]
  (when s (-> s (str/replace #"([a-z])([A-Z])" "$1-$2") str/lower-case (str/replace #"_" "-"))))

(defn convert-strategy-header
  "Convert a strategy/indicator/library header line."
  [line]
  (when-let [[_ type] (re-find #"^(strategy|indicator|library)\(" (str/trim line))]
    (let [args (second (re-find #"\((.*)\)" (str/trim line)))
          kw-args (->> (re-seq #"(\w+)\s*=\s*([^,\)]+)" (or args ""))
                       (map (fn [[_ k v]] (str ":" (to-kebab k) " " (to-pine-val v))))
                       (str/join " "))
          name (or (second (re-find #"^\"([^\"]+)\"" (or args ""))) "")]
      (str "(" type " \"" name "\"" (when (seq kw-args) (str " " kw-args)) ")"))))

(defn convert-assignment
  "Convert a variable assignment line: name = expr"
  [line]
  (when-let [[_ varname expr] (re-find #"^\s*(\w+)\s*=\s*(.+)" line)]
    (let [varname (to-kebab varname)]
      (str "(def " varname " " (convert-expr expr) ")"))))

(defn convert-expr
  "Convert a Pine Script expression to Stratus DSL."
  [expr]
  (-> (str/trim expr)
      ;; Indicator patterns first (before ta. prefix is stripped)
      ((fn [s] (reduce (fn [acc [pat repl]] (str/replace acc pat repl)) s indicator-patterns)))
      (str/replace #"(\w+)\[(\d+)\]" "(#$1 $2)")
      ;; then fix the # prefix from the close[n] pattern
      (str/replace #"\(#(\w+)\s" "($1 ")
      (str/replace #"strategy\.position_size" "(position-size)")
      (str/replace #"strategy\.position_avg_price" "(position-avg-price)")
      (str/replace #"strategy\.opentrades" "(open-trades)")
      (str/replace #"strategy\.equity" "(equity)")
      (str/replace #"strategy\.netprofit" "(net-profit)")
      (str/replace #"\bna\(([^)]+)\)" "(na $1)")
      (str/replace #"\bnz\(([^)]+)\)" "(nz $1)")
      (str/replace #"\biff\(([^,]+),\s*([^,]+),\s*([^)]+)\)" "(iff $1 $2 $3)")
      (str/replace #"\bta\.cross\(([^,]+),\s*([^)]+)\)\s+and\s+\1\s*>\s*\2" "(crosses-above $1 $2)")
      (str/replace #"\bta\.cross\(([^,]+),\s*([^)]+)\)\s+and\s+\1\s*<\s*\2" "(crosses-below $1 $2)")
      (str/replace #"\brising\(([^,]+),\s*(\d+)\)" "(rising $1)")
      (str/replace #"\bfalling\(([^,]+),\s*(\d+)\)" "(falling $1)")
      (str/replace #"\bta\.(\w+)\(" "($1 ")
      (str/replace #"color\.new\(([^,]+),\s*(\d+)\)" "(color $1 $2)")
      (str/replace #"color\.rgb\(([^)]+)\)" "(rgb $1)")
      (str/replace #"color\.(\w+)" "$1")
      (str/replace #"\bmath\.(\w+)\(" "($1 ")
      (str/replace #"\bvar(ip)?\s+" "")))

(defn convert-line
  "Convert a single line of Pine Script to its Stratus equivalent."
  [line]
  (let [trimmed (str/trim line)]
    (cond
      (str/blank? trimmed) ""
      (str/starts-with? trimmed "//@version") ""
      (str/starts-with? trimmed "//") (str/replace trimmed #"^//" ";")
      (re-find #"^(strategy|indicator|library)\(" trimmed)
        (or (convert-strategy-header trimmed) (str "; " trimmed))
      (re-find #"^\s*var(ip)?\s+\w+\s*=" trimmed)
        (let [[_ ip varname expr] (re-find #"^\s*var(ip)?\s+(\w+)\s*=\s*(.+)" trimmed)]
          (str "(defvar" (or ip "") " " (to-kebab varname) " " (convert-expr expr) ")"))
      (re-find #"^\s*\w+\s*:=\s*" trimmed)
        (let [[_ varname expr] (re-find #"^\s*(\w+)\s*:=\s*(.+)" trimmed)]
          (str "(set! " (to-kebab varname) " " (convert-expr expr) ")"))
      (re-find #"strategy\.entry\(" trimmed)
        (str/replace trimmed
          #"strategy\.entry\(\"([^\"]+)\",\s*strategy\.(\w+)\)"
          (fn [[_ label dir]] (str "(" ({"long" "long", "short" "short"} dir) " \"" label "\")")))
      (re-find #"strategy\.close\(" trimmed)
        (str/replace trimmed #"strategy\.close\(\)" "(close)")
      (re-find #"strategy\.exit\(" trimmed)
        (str/replace trimmed #"strategy\.exit\(\"([^\"]+)\"(.*?)\)"
          (fn [[_ label rest]]
            (let [kw (->> (re-seq #"(\w+)=([^,\)]+)" rest)
                          (map (fn [[_ k v]] (str ":" (to-kebab k) " " (to-pine-val v))))
                          (str/join " "))]
              (str "(exit \"" label "\"" (when (seq kw) (str " " kw)) ")"))))
      (re-find #"strategy\.order\(" trimmed)
        (str/replace trimmed #"strategy\.order\(\"([^\"]+)\",\s*strategy\.(\w+)(.*?)\)"
          (fn [[_ label dir rest]]
            (let [kw (->> (re-seq #"(\w+)=([^,\)]+)" rest)
                          (map (fn [[_ k v]] (str ":" (to-kebab k) " " (to-pine-val v))))
                          (str/join " "))]
              (str "(order \"" label "\" " (case dir "long" ":long" "short" ":short") (when (seq kw) (str " " kw)) ")"))))
      (re-find #"strategy\.cancel\(" trimmed)
        (str/replace trimmed #"strategy\.cancel\(\"([^\"]+)\"\)" "(cancel \"$1\")")
      (re-find #"^\s*(plot|plotshape|hline|bgcolor|barcolor|fill|alertcondition)\(" trimmed)
        (let [[_ fname] (re-find #"^\s*(\w+)\(" trimmed)
              args (second (re-find #"\((.*)\)" trimmed))
              parts (map str/trim (str/split args #",(?![^()]*\))" 2))
              val (first parts)
              rest-args (nth parts 1 nil)]
          (str "(" fname " " val " " (convert-expr (or rest-args "")) ")"))
      (re-find #"^\s*export\s+" trimmed)
        (str "(export " (to-kebab (str/trim (subs trimmed 7))) ")")
      (re-find #"^\s*\w+\s*\(.*\)\s*=>" trimmed)
        (let [[_ name args] (re-find #"^\s*(\w+)\(([^)]*)\)\s*=>" trimmed)
              body (subs (str/trim trimmed) (str/index-of trimmed "=>") 2)]
          (str "(defn " (to-kebab name) " [" args "] " (convert-expr (str/trim body)) ")"))
      (re-find #"^\s*if\s+" trimmed)
        (let [cond (subs trimmed (+ 3 (str/index-of trimmed "if ")))]
          (str "(if " (convert-expr (str/trim cond)) " ...)"))
      (re-find #"^\s*\w+\s*=" trimmed)
        (or (convert-assignment trimmed) (str "; " trimmed))
      :else (str "; WARN: cannot translate '" trimmed "'"))))

(defn convert
  "Convert full Pine Script v6 source to Stratus DSL."
  [pine-source]
  (let [lines (str/split-lines pine-source)
        converted (for [line lines
                        :let [out (convert-line line)]
                        :when (not (str/blank? out))]
                    out)]
    (str (str/join "\n" converted) "\n")))
