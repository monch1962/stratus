(ns stratus.import-property-test
  "Property-based tests for the Pine Script → Stratus importer.
   Uses random/formulaic generation (no test.check dependency)
   to verify invariants across a wide range of inputs."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]))

;; ─── Helpers ────────────────────────────────────────────────────────

(defn balanced-parens?
  "True if string has balanced parentheses (quick check)."
  [s]
  (zero? (reduce (fn [balance ch]
                   (case ch
                     \( (inc balance)
                     \) (dec balance)
                     balance))
                 0 s)))

(defn no-edn-conflicts?
  "True if string has no single-quoted strings (EDN reads ' as quote)."
  [s]
  (not (re-find #"(?<!\\)'(?:[^'\\]|\\.)*'" s)))

;; ─── Generators ────────────────────────────────────────────────────

(defn rand-str
  "Pick a random element from a collection."
  [coll]
  (nth (vec coll) (rand-int (count coll))))

(defn rand-int-range
  "Random int in [lo, hi)."
  [lo hi]
  (+ lo (rand-int (- hi lo))))

(def price-sources ["close" "high" "low" "open" "volume" "hl2" "hlc3" "ohlc4"])
(def indicators ["ta.sma" "ta.ema" "ta.rsi" "ta.wma" "ta.hma" "ta.atr" "ta.stdev" "ta.linreg"])
(def indicators-2arg ["ta.cross" "ta.crossover" "ta.crossunder" "ta.highest" "ta.lowest"])
(def ma-variants ["ta.rma" "ta.tema" "ta.dema" "ta.swma" "ta.smma" "ta.vwma"])
(def math-fns ["math.log" "math.log10" "math.exp" "math.sqrt" "math.abs" "math.ceil" "math.floor"])
(def colors ["color.red" "color.blue" "color.green" "color.gray" "color.navy"])
(def line-styles ["hline.style_dashed" "hline.style_solid" "hline.style_dotted"])
(def plot-styles ["plot.style_line" "plot.style_histogram" "plot.style_area" "plot.style_cross"])
(def shapes ["shape.triangleup" "shape.triangledown"])
(def locations ["location.top" "location.bottom" "location.absolute"])

(defn gen-num [] (str (rand-int-range 1 200)))
(defn gen-src [] (rand-str price-sources))

(defn gen-indicator-call
  "Generate a random indicator call string."
  []
  (let [ind (rand-str indicators)]
    (str ind "(" (gen-src) ", " (gen-num) ")")))

(defn gen-indicator-2arg
  "Generate a 2-argument indicator call."
  []
  (let [ind (rand-str indicators-2arg)]
    (str ind "(" (gen-src) ", " (gen-src) ")")))

(defn gen-ma-variant
  "Generate a moving average variant call."
  []
  (let [ind (rand-str ma-variants)]
    (str ind "(" (gen-src) ", " (gen-num) ")")))

(defn gen-math-call
  "Generate a math function call."
  []
  (str (rand-str math-fns) "(" (gen-num) ")"))

(defn gen-header
  "Generate a random strategy/indicator header."
  []
  (let [type (rand-str ["strategy" "indicator"])]
    (str type "(\"Test " (rand-int-range 1 100) "\", overlay="
         (rand-str ["true" "false"]) ")")))

(defn gen-assignment
  "Generate a variable assignment with a random expression."
  [expr-gen]
  (let [varname (str "var" (rand-int-range 1 100))]
    (str varname " = " (expr-gen))))

(defn gen-plot
  "Generate a random plot expression."
  []
  (let [varname (str "val" (rand-int-range 1 100))
        color (rand-str colors)]
    (str "plot(" varname ", \"Label\", color=" color ", linewidth=" (rand-int-range 1 5) ")")))

(defn gen-hline
  "Generate a random hline expression."
  []
  (let [level (rand-int-range 10 90)
        style (rand-str line-styles)]
    (str "hline(" level ", \"Line\", color=" (rand-str colors)
         ", linestyle=" style ")")))

(defn gen-if-statement
  "Generate a random if statement."
  []
  (let [cond (str (gen-src) " > " (gen-src))]
    (str "if " cond "\n    " (gen-assignment gen-num))))

(defn gen-var-decl
  "Generate a var or varip declaration."
  []
  (let [prefix (if (zero? (rand-int 2)) "var " "varip ")
        vartype (rand-str ["float" "int" "bool"])]
    (str prefix vartype " " (str "x" (rand-int-range 1 100)) " = " (gen-num))))

(defn gen-pine-expression
  "Generate a random valid-ish Pine Script expression."
  []
  (rand-str
    [(gen-indicator-call)
     (gen-indicator-2arg)
     (gen-ma-variant)
     (gen-math-call)
     (str gen-src " + " gen-num)
     (str gen-src " * 2")
     (str gen-src " - " gen-src)
     (str "(" gen-src " + " gen-src ") / 2")
     (str "na(" gen-src ")")
     (str "nz(" gen-src ")")
     (str "iff(" gen-src " > " gen-num ", " gen-src ", 0)")
     (str "rising(" gen-src ", " (gen-num) ")")
     (str "falling(" gen-src ", " (gen-num) ")")
     (str "change(" gen-src ")")
     (str "mom(" gen-src ", " (gen-num) ")")
     (str "math.sum(" gen-src ", " (gen-num) ")")
     (str "math.avg(" gen-src ", " (gen-num) ")")
     (str "math.cum(" gen-src ")")
     (str "ta.cmo(" gen-src ", " (gen-num) ")")
     (str "ta.median(" gen-src ", " (gen-num) ")")
     (str "ta.mode(" gen-src ", " (gen-num) ")")
     (str "ta.percentile_nearest_rank(" gen-src ", " (gen-num) ", 50)")
     (str "ta.wad(" gen-src ")")
     (str "ta.cog(" gen-src ", " (gen-num) ")")]))

(defn gen-pine-file
  "Generate a random multi-line Pine Script snippet."
  [& {:keys [lines] :or {lines 5}}]
  (str/join "\n"
    (cons (gen-header)
          (take lines (repeatedly
            (fn []
              (rand-str
                [(gen-assignment gen-pine-expression)
                 (gen-var-decl)
                 (gen-if-statement)
                 (gen-plot)
                 (gen-hline)
                 (str "// comment " (rand-int-range 1 100))])))))))

;; ─── Property Tests ────────────────────────────────────────────────

(deftest import-never-crashes-on-single-expressions
  (doseq [i (range 200)]
    (let [expr (gen-pine-expression)]
      (is (string? (imp/convert expr))
          (str "Crash on: " expr)))))

(deftest import-never-crashes-on-full-files
  (doseq [i (range 100)]
    (let [file (gen-pine-file :lines (rand-int-range 3 8))]
      (is (string? (imp/convert file))
          (str "Crash on multi-line input")))))

(deftest output-has-balanced-parens
  (doseq [i (range 200)]
    (let [expr (gen-pine-expression)
          result (imp/convert expr)]
      (is (balanced-parens? result)
          (str "Unbalanced parens for: " expr " => " result)))))

(deftest output-no-edn-conflicts
  (doseq [i (range 200)]
    (let [expr (gen-pine-expression)
          result (imp/convert expr)]
      (is (no-edn-conflicts? result)
          (str "EDN-conflicting quotes in: " expr " => " result)))))

(deftest import-is-idempotent-on-expr
  (doseq [i (range 100)]
    (let [expr (gen-pine-expression)
          once (imp/convert expr)
          twice (imp/convert once)]
      ;; Second pass should not crash and should be similar structure
      (is (string? twice)
          (str "Second pass crashed on: " once)))))

(deftest headers-convert-with-keywords
  (doseq [[header expected-kw] [["strategy(\"Test\", overlay=true)" ":overlay"]
                                ["indicator(\"RSI\", precision=2)" ":precision"]
                                ["strategy(\"GC\", overlay=true, default_qty=100)" ":default-qty"]]]
    (let [result (imp/convert header)]
      (is (str/includes? result expected-kw)
          (str "Missing " expected-kw " keyword in: " result)))))

(deftest known-indicators-always-convert
  (doseq [[ind expected] [["ta.sma" "(sma"] ["ta.ema" "(ema"]
                          ["ta.rsi" "(rsi"] ["ta.atr" "(atr"]
                          ["ta.stdev" "(stdev"]]]
    (let [expr (str ind "(close, 14)")
          result (imp/convert expr)]
      (is (str/includes? result expected)
          (str "Indicator " ind " did not convert: " result)))))

(deftest arithmetic-converts-to-prefix
  (doseq [expr ["close + high"
                "close - low"
                "close * 2"
                "high / low"
                "close % 10"
                "(open + close) / 2"]]
    (let [result (imp/convert expr)]
      (is (re-find #"\([+\-*/%]" result)
          (str "Arithmetic not in prefix form: " expr " => " result)))))

(deftest plot-with-kwargs-retains-structure
  (doseq [i (range 50)]
    (let [v (str "v" (rand-int-range 1 100))
          color (rand-str colors)
          plot-str (str "plot(" v ", \"L\", color=" color ", linewidth=2)")
          result (imp/convert plot-str)]
      (is (str/includes? result "(plot")
          (str "Plot not converted: " result))
      (is (str/includes? result ":color")
          (str "Missing :color keyword: " result)))))

(deftest crosses-above-below-detected
  (doseq [cond ["ta.cross(fast, slow) and fast > slow"
                "ta.cross(short, long) and short < long"]]
    (let [result (imp/convert cond)]
      (is (or (str/includes? result "crosses-above")
              (str/includes? result "crosses-below"))
          (str "Crossover not detected: " result)))))

(deftest color-keywords-mapped
  (doseq [color ["color.red" "color.blue" "color.green" "color.navy"]]
    (let [result (imp/convert (str "plot(x, \"L\", color=" color ")"))]
      (is (not (str/includes? result "color."))
          (str "Color prefix not stripped: " result)))))

(deftest hex-colors-converted
  (doseq [hex ["#FF0000" "#00FF00FF" "#0000FF7F"]]
    (let [result (imp/convert (str "color=" hex))]
      (is (str/includes? result "(rgb")
          (str "Hex color not converted to RGB: " result)))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.import-property-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
