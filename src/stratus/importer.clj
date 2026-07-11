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

(def strategy-builtins-map
  {"strategy\\.position_size"     "(position-size)"
   "strategy\\.position_avg_price" "(position-avg-price)"
   "strategy\\.opentrades"        "(open-trades)"
   "strategy\\.equity"            "(equity)"
   "strategy\\.netprofit"         "(net-profit)"
   "strategy\\.openprofit"        "(open-profit)"
   "strategy\\.wintrades"         "(win-trades)"
   "strategy\\.losstrades"        "(loss-trades)"
   "strategy\\.closedtrades"      "(closed-trades)"
   "strategy\\.grossprofit"       "(gross-profit)"
   "strategy\\.grossloss"         "(gross-loss)"
   "strategy\\.maxdrawdown"       "(max-drawdown)"
   "strategy\\.maxrunup"          "(max-runup)"})

(defn apply-until-stable
  "Apply str/replace until the string stops changing (max N iterations)."
  [s pattern replacement & [max-n]]
  (let [max-n (or max-n 20)]
    (loop [prev "" current s n 0]
      (if (or (= prev current) (>= n max-n))
        current
        (recur current (str/replace current pattern replacement) (inc n))))))

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
  (let [s (str/trim expr)]
    (-> s
      ((fn [s] (reduce (fn [acc [pat repl]] (str/replace acc pat repl)) s indicator-patterns)))
      (#(str/replace % #"(\w+)\[(\d+)\]" "(#$1 $2)"))
      (#(str/replace % #"\(#(\w+)\s" "($1 "))
      (#(reduce (fn [acc [pat repl]] (str/replace acc (re-pattern pat) repl)) % strategy-builtins-map))
      (#(str/replace % #"\bna\(([^)]+)\)" "(na $1)"))
      (#(str/replace % #"\bnz\(([^)]+)\)" "(nz $1)"))
      (#(str/replace % #"\biff\(([^,]+),\s*([^,]+),\s*([^)]+)\)" "(iff $1 $2 $3)"))
      (#(str/replace % #"\bta\.cross\(([^,]+),\s*([^)]+)\)\s+and\s+\1\s*>\s*\2" "(crosses-above $1 $2)"))
      (#(str/replace % #"\bta\.cross\(([^,]+),\s*([^)]+)\)\s+and\s+\1\s*<\s*\2" "(crosses-below $1 $2)"))
      (#(str/replace % #"\brising\(([^,]+),\s*(\d+)\)" "(rising $1)"))
      (#(str/replace % #"\bfalling\(([^,]+),\s*(\d+)\)" "(falling $1)"))
      (#(str/replace % #"\bta\.(\w+)\(" "($1 "))
      (#(str/replace % #"color\.new\(\s*([^,]+)\s*,\s*(\d+)\s*\)" "(color $1 $2)"))
      (#(str/replace % #"color\.rgb\(([^)]+)\)" "(rgb $1)"))
      (#(str/replace % #"color\.(\w+)" "$1"))
      (#(str/replace % #"\bmath\.(\w+)\(" "($1 "))
      (#(str/replace % #"\bvar(ip)?\s+" ""))
      (#(str/replace % #"(?<![(\w])\bnot\s+" "(not "))
      (#(str/replace % #"\b([a-z]\w+(?:\.\w+)*)\(([^)]*)\)"
         (fn [[_ fname args]]
           (let [args-trimmed (str/trim args)]
             (str "(" (to-kebab fname) (if (seq args-trimmed) (str " " args-trimmed) "") ")")))))
      (#(str/replace % #"(?<!\w)\(([^)]*(?:\s+[+*\/%-]\s+)[^)]*)\)"
         (fn [match]
           (let [inner (nth match 1)]
             (convert-expr inner)))))
      (#(str/replace % #"([^\s(]+)\s*\*\s*([^\s(]+)" "(* $1 $2)"))
      (#(str/replace % #"([^\s(]+)\s+/\s+([^\s(]+)" "(/ $1 $2)"))
      (#(str/replace % #"([^\s(]+)\s*%\s*([^\s(]+)" "(% $1 $2)"))
      (#(str/replace % #"([^\s(]+)\s*\+\s*([^\s(]+)" "(+ $1 $2)"))
      (#(str/replace % #"([^\s(]+)\s+-\s+([^\s(]+)" "(- $1 $2)"))
      (#(str/replace % #"#([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})"
         (fn [[_ r g b a]]
           (str "(rgb " (Integer/parseInt r 16) " " (Integer/parseInt g 16) " "
                (Integer/parseInt b 16) " " (Integer/parseInt a 16) ")"))))
      (#(str/replace % #"#([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})\b"
         (fn [[_ r g b]]
           (str "(rgb " (Integer/parseInt r 16) " " (Integer/parseInt g 16) " "
                (Integer/parseInt b 16) " 100)"))))
      (#(apply-until-stable % #"(.+?)\s*==\s*(.+)" "(= $1 $2)"))
      (#(apply-until-stable % #"(.+?)\s*!=\s*(.+)" "(not (= $1 $2))"))
      (#(apply-until-stable % #"(.+?)\s+and\s+(.+)" "(and $1 $2)"))
      (#(apply-until-stable % #"(.+?)\s+or\s+(.+)" "(or $1 $2)"))
      (#(str/replace % #"([^!=<>+\-*/%\s]+(?:\s*[><=!]+\s*[^,?:\n]+)?)\s*\?\s*([^:\n,]+)\s*:\s*([^,;\n]+)" "(iff $1 $2 $3)"))
      (#(str/replace % #"\s*//.*" ""))
      (#(identity %)))))

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
    (str "(" fname " " (convert-expr (str/trim val))
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
      (str "(table." (to-kebab fn-name) " " (str/join " " (map str/trim (str/split args #",\s*"))) ")"))))

;; ─── Line dispatch table ─────────────────────────────────────────

(def line-handlers
  "Vector of [predicate-regex handler-fn] for convert-line dispatch.
   Uses re-pattern with escaped strings for SCI compatibility."
  (let [rp re-pattern]
    [[(rp "^strategy|indicator|library\\(")       (fn [t line] (or (convert-strategy-header t) (str "; " t)))]
     [(rp "^input\\.\\w+\\(")                     (fn [t line] (convert-input t))]
     [(rp "^var(ip)?\\s+\\w+\\s+\\w+\\s*=")       (fn [t line]
        (let [[_ ip _type varname expr] (re-find #"^\s*var(ip)?\s+(\w+)\s+(\w+)\s*=\s*(.+)" t)
              expr-cleaned (convert-expr (or expr ""))]
          (str "(defvar" (or ip "") " " (to-kebab varname) " " expr-cleaned ")")))]
     [(rp "^var(ip)?\\s+\\w+\\s*=")               (fn [t line]
        (let [[_ ip varname expr] (re-find #"^\s*var(ip)?\s+(\w+)\s*=\s*(.+)" t)]
          (str "(defvar" (or ip "") " " (to-kebab varname) " " (convert-expr expr) ")")))]
     [(rp "^\\s*\\w+\\s*:=\\s*")                  (fn [t line]
        (let [[_ varname expr] (re-find #"^\s*(\w+)\s*:=\s*(.+)" t)]
          (str "(set! " (to-kebab varname) " " (convert-expr expr) ")")))]
     [(rp "^\\s*\\w+\\s*[+\\-*/]=\\s*")           (fn [t line]
        (let [[_ varname op expr] (re-find #"^\s*(\w+)\s*([+\-*/])=\s*(.+)" t)
              op-name (case op "+" "+" "-" "-" "*" "*" "/" "/")]
          (str "(set! " (to-kebab varname) " (" op-name " " (to-kebab varname) " " (convert-expr expr) ")")))]
     [(rp "^\\s*switch\\s+")                      (fn [t line] (let [[_ val] (re-find #"^\s*switch\s+(.+)" t)]
                                                                 (str "; WARN: switch(" (str/trim val) ") -- manual translation needed")))]
     [(rp "^\\s*for\\s+")                         (fn [t line] (let [[_ var coll] (re-find #"^\s*for\s+(\w+)\s+in\s+(.+)" t)]
                                                                 (str "(for [" var " " (str/trim coll) "] ...)")))]
     [(rp "^\\s*break\\s*$")                      (fn [t line] "(break)")]
     [(rp "^\\s*else\\s+if\\s+")                  (fn [t line] (let [cond (str/trim (subs t (+ 8 (str/index-of t "else if "))))]
                                                                 (str "else if " (convert-expr cond))))]
     [(rp "^\\s*else\\s*$")                       (fn [t line] "else")]
     [(rp "strategy\\.entry\\(")                  (fn [t line] (str/replace t #"strategy\.entry\(\"([^\"]+)\",\s*strategy\.(\w+)\)"
                                                                             (fn [[_ label dir]] (str "(" ({"long" "long", "short" "short"} dir) " \"" label "\")"))))]
     [(rp "strategy\\.close\\(")                  (fn [t line] (str/replace t #"strategy\.close\(\"([^\"]+)\"\)" "(close \"$1\")"))]
     [(rp "strategy\\.exit\\(")                   (fn [t line] (convert-exit t))]
     [(rp "strategy\\.order\\(")                  (fn [t line] (convert-order t))]
     [(rp "strategy\\.cancel\\(")                 (fn [t line] (str/replace t #"strategy\.cancel\(\"([^\"]+)\"\)" "(cancel \"$1\")"))]
     [(rp "^\\s*\\w+\\s*=\\s*input\\.\\w+\\(")    (fn [t line] (let [[_ varname expr] (re-find #"^\s*(\w+)\s*=\s*(input\..+)" t)]
                                                                 (str "(def " (to-kebab varname) " " (convert-input expr) ")")))]
     [(rp "^(plotshape|plotchar|plotarrow|plot|hline|bgcolor|barcolor|fill|alertcondition)\\(") (fn [t line] (convert-plot t))]
     [(rp "^array\\.\\w+\\(")                     (fn [t line] (convert-array t))]
     [(rp "^(line|label|box|polygon)\\.\\w+\\(")  (fn [t line] (convert-drawing t))]
     [(rp "^table\\.\\w+\\(")                     (fn [t line] (convert-table t))]
     [(rp "^request\\.security\\(")               (fn [t line] (str/replace t #"request\.security\([^,]+,\s*\"([^\"]+)\",\s*(.+)\)"
                                                                             (fn [[_ tf expr]] (str "(security \"" tf "\" " (convert-expr expr) ")"))))]
     [(rp "^barstate\\.\\w+")                     (fn [t line] (if-let [kw (get barstate-map t)] (str "(" kw ")") (str "; " t)))]
     [(rp "^syminfo\\.\\w+")                      (fn [t line] (let [[_ name] (re-find #"^syminfo\.(\w+)" t)]
                                                                 (str "(" (to-kebab name) ")")))]
     [(rp "^str\\.tostring\\(")                   (fn [t line] (str/replace t #"str\.tostring\((.+)\)" "(tostring $1)"))]
     [(rp "^\\s*export\\s+")                      (fn [t line] (str "(export " (to-kebab (str/trim (subs t 7))) ")"))]
     [(rp "^\\s*\\w+\\s*\\(.*\\)\\s*=>")          (fn [t line]
        (let [[_ fname args] (re-find #"^\s*(\w+)\(([^)]*)\)\s*=>" t)
              body-start (+ 2 (str/index-of t "=>"))
              body-raw (subs t (min body-start (count t)))]
          (str "(defn " (to-kebab fname) " [" args "] " (convert-expr (str/trim body-raw)) ")")))]
     [(rp "^\\s*if\\s+")                          (fn [t line] (let [cond (str/trim (subs t (+ 3 (str/index-of t "if "))))]
                                                                 (str "(if " (convert-expr cond) ")")))]
     [(rp "^\\s*\\w+\\s*=")                       (fn [t line] (or (convert-assignment t) (str "; " t)))]
     [(rp "^\\s*(bool|string|int|float|line|label|color)\\s+\\w+\\s*=") (fn [t line]
        (let [[_ _type varname expr] (re-find #"^\s*(\w+)\s+(\w+)\s*=\s*(.+)" t)]
          (str "(def " (to-kebab varname) " " (convert-expr expr) ")")))]
     [(rp "^\\s*na\\s*$")                         (fn [t line] "na")]
     [(rp "^\\s*\".*\"\\s*=>\\s*$")               (fn [t line] (str "; " t))]
     [(rp "\\s+and\\s+")                          (fn [t line] (let [[_ left right] (re-find #"^\s*(.+?)\s+and\s+(.+)\s*$" t)]
                                                                 (if (and left right)
                                                                   (str "; (and " (convert-expr (str/trim left)) " " (convert-expr (str/trim right)) ")")
                                                                   (str "; WARN: cannot translate '" t "'"))))]
     [(rp "^\\s*\\w+\\s*$")                       (fn [t line] (str "; " (convert-expr t)))]
     [(rp "^\\s*\".*\"\\s*$")                     (fn [t line] (str "; " (convert-expr t)))]
     [(rp "^\\w+")                                (fn [t line] (let [converted (convert-expr t)]
                                                                 (if (not= converted t)
                                                                   (str "; " converted)
                                                                   (if (re-find #"^\w+\(.*\)$" t)
                                                                     (str "; " (convert-expr t))
                                                                     (str "; WARN: cannot translate '" t "'")))))]]))
(defn convert-line
  "Convert a single line of Pine Script to its Stratus equivalent."
  [line]
  (let [trimmed (str/trim line)]
    (cond
      (str/blank? trimmed) ""
      (str/starts-with? trimmed "//@version") ""
      (str/starts-with? trimmed "//") (str/replace trimmed #"^//" ";")
      ;; Multi-line continuation — check RAW line before trim
      (re-find #"^\s+(?:timestam|color\.|\w+\()" line) (str "; " trimmed)
      :else
      (some (fn [[pred handler]]
              (when (re-find pred trimmed) (handler trimmed line)))
            line-handlers))))

(defn convert
  "Convert full Pine Script v6 source to Stratus DSL."
  [pine-source]
  (let [lines (str/split-lines pine-source)
        converted (for [line lines
                        :let [out (convert-line line)]
                        :when (not (str/blank? out))]
                    out)
        result (str/join "\n" converted)]
    ;; Post-processing: single-quoted strings → double-quoted (EDN compat)
    (str (str/replace result #"'([^']*)'" "\"$1\"") "\n")))
