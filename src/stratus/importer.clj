(ns stratus.importer
  "Pine Script v6 → Stratus DSL converter."
  (:require [clojure.string :as str]))

(declare convert-expr to-pine-val to-kebab convert-input convert-exit
         convert-order convert-plot convert-array convert-drawing convert-table)

(def barstate-map
  {"barstate.isconfirmed" "bar-confirmed", "barstate.isfirst" "bar-first",
   "barstate.islast" "bar-last", "barstate.isnew" "bar-new",
   "barstate.isrealtime" "bar-realtime", "barstate.ishistory" "bar-history"})

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
   [#"ta\.valuewhen\(([^,]+),\s*([^)]+)\)"      "(valuewhen $1 $2)"]
   [#"ta\.kc\([^,]+,\s*([^,]+),\s*([^)]+)\)"   "(kc $1 $2)"]
   [#"ta\.rma\([^,]+,\s*([^)]+)\)"              "(rma $1)"]
   [#"ta\.tema\([^,]+,\s*([^)]+)\)"             "(tema $1)"]
   [#"ta\.dema\([^,]+,\s*([^)]+)\)"             "(dema $1)"]
   [#"ta\.swma\(([^)]*)\)"                      "(swma $1)"]
   [#"ta\.smma\([^,]+,\s*([^)]+)\)"             "(smma $1)"]
   [#"ta\.tr"                                   "(tr)"]
   [#"ta\.roc\([^,]+,\s*([^)]+)\)"              "(roc $1)"]
   [#"ta\.cmo\([^,]+,\s*([^)]+)\)"              "(cmo $1)"]
   [#"ta\.wad\(([^)]*)\)"                       "(wad $1)"]
   [#"ta\.cog\([^,]+,\s*([^)]+)\)"              "(cog $1)"]
   [#"ta\.mama\(([^,]+),\s*([^,]+),\s*([^)]+)\)" "(mama $1 $2 $3)"]
   [#"ta\.vwmacd\(([^,]+),\s*([^,]+),\s*([^,]+),\s*([^)]+)\)" "(vwmacd $1 $2 $3 $4)"]
   [#"ta\.cross\(([^,]+),\s*([^)]+)\)"          "(cross $1 $2)"]
   [#"ta\.crossover\(([^,]+),\s*([^)]+)\)"      "(crossover $1 $2)"]
   [#"ta\.crossunder\(([^,]+),\s*([^)]+)\)"     "(crossunder $1 $2)"]
   [#"ta\.fixnan\(([^)]+)\)"                    "(fixnan $1)"]
   [#"ta\.stdev\([^,]+,\s*([^)]+)\)"            "(stdev $1)"]])

(defn to-pine-val
  "Map a Pine Script value to Stratus DSL."
  [v]
  (or (when v (get {"color.red" "red", "color.green" "green", "color.blue" "blue",
                    "color.yellow" "yellow", "color.orange" "orange", "color.purple" "purple",
                    "color.pink" "pink", "color.gray" "gray", "color.white" "white",
                    "color.black" "black", "color.navy" "navy", "color.teal" "teal",
                    "color.lime" "lime", "color.maroon" "maroon", "color.fuchsia" "fuchsia"} v))
      (when v (get {"hline.style_dashed" "dashed", "hline.style_dotted" "dotted",
                    "hline.style_solid" "solid", "plot.style_histogram" "histogram",
                    "plot.style_area" "area", "plot.style_columns" "columns",
                    "plot.style_circles" "circles", "plot.style_cross" "cross",
                    "plot.style_stepline" "step-line", "shape.triangleup" "triangle-up",
                    "shape.triangledown" "triangle-down", "location.top" "top",
                    "location.bottom" "bottom", "location.absolute" "absolute",
                    "location.abovebar" "abovebar", "location.belowbar" "belowbar",
                    "shape.flag" "flag"} v))
      (when (and v (str/starts-with? v "color.")) (subs v 6))
      (when (and v (str/starts-with? v "syminfo.")) (to-kebab (subs v 8)))
      v))

(defn to-kebab
  "Convert camelCase or snake_case to kebab-case."
  [s]
  (when s (-> s (str/replace #"([a-z])([A-Z])" "$1-$2") str/lower-case (str/replace #"_" "-"))))

(defn parse-kw-args
  "Extract keyword=value pairs from a string."
  [s]
  (when s
    (->> (re-seq #"(\w+)\s*=\s*([^,\s\)]+(?:\s*[^,\)]+)?)" s)
         (map (fn [[_ k v]]
                (let [kebab (to-kebab k)
                      vval  (to-pine-val (str/trim v))]
                  (str ":" kebab " " vval)))))))

(defn convert-strategy-header
  "Convert a strategy/indicator/library header line."
  [line]
  (when-let [[_ type] (re-find #"^(strategy|indicator|library)\(" (str/trim line))]
    (let [args (second (re-find #"\((.*)\)" (str/trim line)))
          kw-args (str/join " " (parse-kw-args args))
          name (or (second (re-find #"^\"([^\"]+)\"" (or args ""))) "")]
      (str "(" type " \"" name "\"" (when (seq kw-args) (str " " kw-args)) ")"))))

(defn convert-assignment
  "Convert a variable assignment line: name = expr"
  [line]
  (when-let [[_ varname expr] (re-find #"^\s*(\w+)\s*=\s*(.+)" line)]
    (str "(def " (to-kebab varname) " " (convert-expr expr) ")")))

(defn convert-expr
  "Convert a Pine Script expression to Stratus DSL."
  [expr]
  (let [s (str/trim expr)
        s (reduce (fn [acc [pat repl]] (str/replace acc pat repl)) s indicator-patterns)
        s (str/replace s #"(\w+)\[(\d+)\]" "(#$1 $2)")
        s (str/replace s #"\(#(\w+)\s" "($1 ")
        s (str/replace s #"strategy\.position_size" "(position-size)")
        s (str/replace s #"strategy\.position_avg_price" "(position-avg-price)")
        s (str/replace s #"strategy\.opentrades" "(open-trades)")
        s (str/replace s #"strategy\.equity" "(equity)")
        s (str/replace s #"strategy\.netprofit" "(net-profit)")
        s (str/replace s #"strategy\.openprofit" "(open-profit)")
        s (str/replace s #"strategy\.wintrades" "(win-trades)")
        s (str/replace s #"strategy\.losstrades" "(loss-trades)")
        s (str/replace s #"strategy\.closedtrades" "(closed-trades)")
        s (str/replace s #"strategy\.grossprofit" "(gross-profit)")
        s (str/replace s #"strategy\.grossloss" "(gross-loss)")
        s (str/replace s #"strategy\.maxdrawdown" "(max-drawdown)")
        s (str/replace s #"strategy\.maxrunup" "(max-runup)")
        s (str/replace s #"\bna\(([^)]+)\)" "(na $1)")
        s (str/replace s #"\bnz\(([^)]+)\)" "(nz $1)")
        s (str/replace s #"\biff\(([^,]+),\s*([^,]+),\s*([^)]+)\)" "(iff $1 $2 $3)")
        s (str/replace s #"\bta\.cross\(([^,]+),\s*([^)]+)\)\s+and\s+\1\s*>\s*\2" "(crosses-above $1 $2)")
        s (str/replace s #"\bta\.cross\(([^,]+),\s*([^)]+)\)\s+and\s+\1\s*<\s*\2" "(crosses-below $1 $2)")
        s (str/replace s #"\brising\(([^,]+),\s*(\d+)\)" "(rising $1)")
        s (str/replace s #"\bfalling\(([^,]+),\s*(\d+)\)" "(falling $1)")
        s (str/replace s #"\bta\.(\w+)\(" "($1 ")
        s (str/replace s #"color\.new\(\s*([^,]+)\s*,\s*(\d+)\s*\)" "(color $1 $2)")
        s (str/replace s #"color\.rgb\(([^)]+)\)" "(rgb $1)")
        s (str/replace s #"color\.(\w+)" "$1")
        s (str/replace s #"\bmath\.(\w+)\(" "($1 ")
        s (str/replace s #"\bvar(ip)?\s+" "")]
    s))

(defn convert-input [trimmed]
  (let [[_ itype] (re-find #"^input\.(\w+)\(" trimmed)
        args (second (re-find #"\((.*)\)" trimmed))
        parts (str/split args #",\s*(?=(?:[^']*'[^']*')*[^']*$)")
        positionals (take 2 parts)
        kw-str (str/join ", " (drop 2 parts))
        kw-args (when (seq kw-str) (parse-kw-args kw-str))
        ds-type (str/replace itype #"-" "")
        pos-str (str/join " :def " (map str/trim positionals))]
    (str "(input-" ds-type (when (seq pos-str) (str " " pos-str))
         (when (seq kw-args) (str " " (str/join " " kw-args))) ")")))

(defn convert-exit [trimmed]
  (str/replace trimmed #"strategy\.exit\(\"([^\"]+)\"(.*?)\)"
    (fn [[_ label rest]]
      (str "(exit \"" label "\"" (when (seq (str/trim rest)) (str " " (str/join " " (parse-kw-args rest)))) ")"))))

(defn convert-order [trimmed]
  (str/replace trimmed #"strategy\.order\(\"([^\"]+)\",\s*strategy\.(\w+)(.*?)\)"
    (fn [[_ label dir rest]]
      (let [kw (str/join " " (parse-kw-args rest))]
        (str "(order \"" label "\" " (case dir "long" ":long" "short" ":short")
             (when (seq kw) (str " " kw)) ")")))))

(defn convert-plot [trimmed]
  (let [[_ fname] (re-find #"^(\w+)\(" trimmed)
        args (second (re-find #"\((.*)\)" trimmed))
        parts (str/split args #",(?![^()]*\))(?![^']*'[^']*')")
        val (first parts)
        rest-args (str/join ", " (drop 1 parts))]
    (str "(" fname " " (str/trim val)
         (when (seq rest-args)
           (str " " (str/replace rest-args
                     #"(\w+)\s*=\s*(.+?)(?:,\s*|$)"
                     (fn [[_ k v]] (str ":" (to-kebab k) " " (to-pine-val (str/trim v)) " ")))))
         ")")))

(defn convert-array [trimmed]
  (str/replace trimmed #"^array\.(\w+)\((.+)\)"
    (fn [[_ fn-name args]]
      (str "(" (to-kebab fn-name) " " (str/join " " (map str/trim (str/split args #",\s*"))) ")"))))

(defn convert-drawing [trimmed]
  (str/replace trimmed #"^(line|label|box|polygon)\.(\w+)\((.+)\)"
    (fn [[_ obj fn-name args]]
      (str "(" obj "." (to-kebab fn-name) " " (str/join " " (map str/trim (str/split args #",\s*"))) ")"))))

(defn convert-table [trimmed]
  (str/replace trimmed #"^table\.(\w+)\((.+)\)"
    (fn [[_ fn-name args]]
      (str "table-" (to-kebab fn-name) " " (str/join " " (map str/trim (str/split args #",\s*"))) ")"))))

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
      (re-find #"^input\.(\w+)\(" trimmed)
        (convert-input trimmed)
      (re-find #"^var(ip)?\s+\w+\s*=" trimmed)
        (let [[_ ip varname expr] (re-find #"^\s*var(ip)?\s+(\w+)\s*=\s*(.+)" trimmed)]
          (str "(defvar" (or ip "") " " (to-kebab varname) " " (convert-expr expr) ")"))
      (re-find #"^\s*\w+\s*:=\s*" trimmed)
        (let [[_ varname expr] (re-find #"^\s*(\w+)\s*:=\s*(.+)" trimmed)]
          (str "(set! " (to-kebab varname) " " (convert-expr expr) ")"))
      (re-find #"^\s*switch\s+" trimmed)
        (let [[_ val] (re-find #"^\s*switch\s+(.+)" trimmed)]
          (str "; WARN: switch(" (str/trim val) ") — manual translation needed"))
      (re-find #"strategy\.entry\(" trimmed)
        (str/replace trimmed
          #"strategy\.entry\(\"([^\"]+)\",\s*strategy\.(\w+)\)"
          (fn [[_ label dir]] (str "(" ({"long" "long", "short" "short"} dir) " \"" label "\")")))
      (re-find #"strategy\.close\(" trimmed)
        (str/replace trimmed #"strategy\.close\(\"([^\"]+)\"\)" "(close \"$1\")")
      (re-find #"strategy\.exit\(" trimmed)
        (convert-exit trimmed)
      (re-find #"strategy\.order\(" trimmed)
        (convert-order trimmed)
      (re-find #"strategy\.cancel\(" trimmed)
        (str/replace trimmed #"strategy\.cancel\(\"([^\"]+)\"\)" "(cancel \"$1\")")
      ;; input.* in assignment — catch BEFORE plain =
      (re-find #"^\s*\w+\s*=\s*input\.\w+\(" trimmed)
        (let [[_ varname expr] (re-find #"^\s*(\w+)\s*=\s*(input\..+)" trimmed)]
          (str "(def " (to-kebab varname) " " (convert-input expr) ")"))
      (re-find #"^(plotshape|plotchar|plotarrow|plot|hline|bgcolor|barcolor|fill|alertcondition)\(" trimmed)
        (convert-plot trimmed)
      (re-find #"^array\.\w+\(" trimmed)
        (convert-array trimmed)
      (re-find #"^(line|label|box|polygon)\.\w+\(" trimmed)
        (convert-drawing trimmed)
      (re-find #"^table\.\w+\(" trimmed)
        (convert-table trimmed)
      (re-find #"^request\.security\(" trimmed)
        (str/replace trimmed #"request\.security\([^,]+,\s*\"([^\"]+)\",\s*(.+)\)"
          (fn [[_ tf expr]] (str "(security \"" tf "\" " (convert-expr expr) ")")))
      (re-find #"^barstate\.\w+" trimmed)
        (let [kw (get barstate-map trimmed)]
          (if kw (str "(" kw ")") (str "; " trimmed)))
      (re-find #"^syminfo\.\w+" trimmed)
        (let [[_ name] (re-find #"^syminfo\.(\w+)" trimmed)]
          (str "(" (to-kebab name) ")"))
      (re-find #"^str\.tostring\(" trimmed)
        (str/replace trimmed #"str\.tostring\((.+)\)" "(tostring $1)")
      (re-find #"^\s*export\s+" trimmed)
        (str "(export " (to-kebab (str/trim (subs trimmed 7))) ")")
      (re-find #"^\s*\w+\s*\(.*\)\s*=>" trimmed)
        (let [[_ fname args] (re-find #"^\s*(\w+)\(([^)]*)\)\s*=>" trimmed)
              body-start (+ 2 (str/index-of trimmed "=>"))
              body-raw (subs trimmed (min body-start (count trimmed)))
              body (convert-expr (str/trim body-raw))]
          (str "(defn " (to-kebab fname) " [" args "] " body ")"))
      (re-find #"^\s*if\s+" trimmed)
        (let [cond (str/trim (subs trimmed (+ 3 (str/index-of trimmed "if "))))]
          (str "(if " (convert-expr cond) " ...)"))
      (re-find #"^\s*\w+\s*=" trimmed)
        (or (convert-assignment trimmed) (str "; " trimmed))
      ;; Catch-all: try convert-expr on any remaining expression
      (re-find #"^\w+" trimmed)
        (let [converted (convert-expr trimmed)]
          (if (not= converted trimmed)
            (str "; " converted)
            (str "; WARN: cannot translate '" trimmed "'")))
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
