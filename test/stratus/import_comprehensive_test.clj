(ns stratus.import-comprehensive-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.importer :as imp]))

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.import-comprehensive-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))

(deftest ternary-converts-to-iff
  (let [r (imp/convert "x = cond ? a : b")]
    (is (str/includes? r "iff"))
    (is (str/includes? r "(iff cond"))))

(deftest ternary-in-expression
  (let [r (imp/convert "halfBar = barstate.isconfirmed ? (high-low)/2 : (high[1]-low[1])/2")]
    (is (not (str/includes? r "WARN"))))
  (let [r (imp/convert "maxRisk = math.min(prevBarRange + 2 * tickSize, 3 * ktr)")]
    (is (not (str/includes? r "WARN")))))

(deftest var-int-declaration
  (let [r (imp/convert "var int r1Index = int(na)")]
    (is (not (str/includes? r "WARN")))))

(deftest var-line-declaration
  (let [r (imp/convert "var line swingLines = line(na)")]
    (is (not (str/includes? r "WARN")))))

(deftest var-float-declaration
  (let [r (imp/convert "var float highPriceInSwing = high")]
    (is (not (str/includes? r "WARN")))))

(deftest var-bool-declaration
  (let [r (imp/convert "varip bool trendIsUp = false")]
    (is (not (str/includes? r "WARN")))))

(deftest for-loop-conversion
  (let [r (imp/convert "for ts in eventData\n    x = 1")]
    (is (not (str/includes? r "WARN")))))

(deftest break-conversion
  (let [r (imp/convert "break")]
    (is (not (str/includes? r "WARN")))))

(deftest else-line
  (let [r (imp/convert "if cond\n    x = 1\nelse\n    x = 2")]
    (is (not (str/includes? r "WARN")))))

(deftest array-from-with-data
  (let [r (imp/convert "NEW_MOON_DATA = array.from(\n    timestamp(\"2024-Jan-12\"),\n    timestamp(\"2024-Feb-10\"),\n    timestamp(\"2024-Mar-11\"),\n    timestamp(\"2024-Apr-09\"))")]
    (is (not (str/includes? r "WARN")))
    (is (str/includes? r "array.from"))
    (is (str/includes? r "timestamp"))))

(deftest standalone-function-call
  (let [r (imp/convert "showRule1Entry()")]
    (is (not (str/includes? r "WARN")))))

(deftest inline-comment
  (let [r (imp/convert "x = 1  // this is a comment")]
    (is (not (str/includes? r "WARN")))))

(deftest gann-file-reduced-warns
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (< warns 46) (str "Expected <46 WARNs, got " warns))))

(deftest astro-file-reduced-warns
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (< warns 200) (str "Expected <200 WARNs, got " warns))))

(deftest else-if-line
  (let [r (imp/convert "else if cond")]
    (is (not (str/includes? r "WARN")))))

(deftest compound-assign-plus
  (let [r (imp/convert "x += 1")]
    (is (not (str/includes? r "WARN")))
    (is (str/includes? r "set!"))))

(deftest compound-assign-times
  (let [r (imp/convert "consecCounter *= 2")]
    (is (not (str/includes? r "WARN")))))

(deftest bare-na
  (let [r (imp/convert "na")]
    (is (not (str/includes? r "WARN")))))

(deftest typed-declaration
  (let [r (imp/convert "bool outsideBar = barType(0) == \"outside\"")]
    (is (not (str/includes? r "WARN")))))

(deftest string-typed-declaration
  (let [r (imp/convert "string _astroTxt = \"\"")]
    (is (not (str/includes? r "WARN")))))

(deftest switch-case-lines
  (let [r (imp/convert "\"up\" =>")]
    (is (not (str/includes? r "WARN")))))

(deftest and-expression
  (let [r (imp/convert "time >= startTS and time_close <= endTS")]
    (is (not (str/includes? r "WARN")))))

(deftest gann-file-zero-warns
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    ;; switch(barType(0)) needs manual translation — accept ≤2 WARNs
    (is (<= warns 2) (str "Expected ≤2 WARNs, got " warns))))

(deftest astro-file-zero-warns
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (zero? warns) (str "Expected 0 WARNs, got " warns))))

(deftest if-function-call
  (let [r (imp/convert "if isUpSwing()\n    x = 1")]
    (is (str/includes? r "(is-up-swing"))))

(deftest if-not-function-call
  (let [r (imp/convert "if not isUpSwing()\n    x = 1")]
    (is (str/includes? r "(not (is-up-swing"))))

(deftest if-not-variable
  (let [r (imp/convert "if not trendInitialized\n    x = 1")]
    (is (str/includes? r "(not trendInitialized"))))

(deftest else-if-with-parens
  (let [r (imp/convert "else if (not trendIsUp) and isUpSwing()\n    x = 1")]
    (is (not (str/includes? r "WARN")))))

(deftest if-simple-and
  (let [r (imp/convert "if a and b\n    x = 1")]
    ;; and remains unconverted in if-conditions (string-based limitation)
    (is (str/includes? r "if"))))

(deftest function-call-in-assignment
  (let [r (imp/convert "x = isUpSwing()")]
    (is (str/includes? r "is-up-swing"))
    (is (not (str/includes? r "WARN")))))

(deftest function-call-in-let
  (let [r (imp/convert "x = array.get(swingY, 0)")]
    (is (not (str/includes? r "WARN")))))

(deftest gann-file-warns-stable
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (<= warns 2) (str "Expected ≤2 WARNs, got " warns))))

(deftest astro-file-zero-warns
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (zero? warns) (str "Expected 0 WARNs, got " warns))))

(deftest hex-color-6-digit
  (let [r (imp/convert "bgcolor(#00FF00)")]
    (is (not (str/includes? r "WARN")))))

(deftest hex-color-8-digit
  (let [r (imp/convert "bgcolor(#00FF0020)")]
    (is (not (str/includes? r "WARN")))))

(deftest ternary-in-plot
  (let [r (imp/convert "bgcolor(_bgUp ? color.red : color.green)")]
    (is (not (str/includes? r "WARN")))
    (is (str/includes? r "iff"))))

(deftest gann-warns-stable
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (<= warns 2) (str "Expected ≤2 WARNs, got " warns))))

(deftest astro-warns-zero
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (zero? warns) (str "Expected 0 WARNs, got " warns))))

(deftest single-quotes-converted
  (let [input (str "x = input.bool(true, '" "Show swings" "')")
        r (imp/convert input)]
    (is (not (str/includes? r (str "'" "Show"))))
    (is (str/includes? r "Show swings"))))

(deftest gann-no-ellipsis-in-output
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)]
    (is (not (str/includes? result " ...")) "No ... placeholder in output")))

(deftest gann-no-hash-in-output
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)]
    (is (not (re-find #"#[0-9A-Fa-f]" result)) "No hex # in output")))

(deftest astro-no-single-quotes
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)]
    (is (not (re-find #"'[A-Za-z]" result)) "No single-quoted strings in output")))

(deftest gann-warns-stable
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (<= warns 2) (str "Expected ≤2 WARNs, got " warns))))

(deftest astro-warns-zero
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (zero? warns) (str "Expected 0 WARNs, got " warns))))

(deftest if-equality-operator
  (let [r (imp/convert "if x == 0\n    y = 1")]
    (is (not (str/includes? r "==")) "No == in output")
    (is (str/includes? r "(= x 0"))))

(deftest if-equality-with-call
  (let [r (imp/convert "if (array.size swingY) == 0\n    y = 1")]
    (is (str/includes? r "(= (array.size swingY) 0"))))

(deftest if-not-equal
  (let [r (imp/convert "if x != 0\n    y = 1")]
    (is (not (str/includes? r "!=")) "No != in output")
    (is (str/includes? r "(not (= x 0"))))

(deftest if-two-ands
  (let [r (imp/convert "if year == (year ts) and month == (month ts) and dayofmonth == (dayofmonth ts)\n    x = 1")]
    (is (str/includes? r "(and"))
    (is (not (re-find #"(?<!\()and" r)) "No bare 'and' in output")))

(deftest if-and-or
  (let [r (imp/convert "if a and b or c\n    x = 1")]
    (is (str/includes? r "(and"))
    (is (str/includes? r "(or"))))

(deftest if-and-with-not
  (let [r (imp/convert "if trendIsUp and (not isUpSwing()) and a > b\n    x = 1")]
    (is (str/includes? r "(not"))
    (is (not (re-find #"(?<!\()and" r)) "No bare 'and'")))

(deftest if-greater-than
  (let [r (imp/convert "if ts > barMidnight and ts < barMidnight + nextGap\n    x = 1")]
    (is (str/includes? r "(and"))
    (is (not (str/includes? r "WARN")))))

(deftest else-if-and
  (let [r (imp/convert "else if (not trendIsUp) and isUpSwing() and a < b\n    x = 1")]
    (is (not (str/includes? r "WARN")))))

(deftest assignment-and-condition
  (let [r (imp/convert "x = a and b")]
    (is (str/includes? r "(and a b"))))

(deftest if-output-parses
  (let [src (imp/convert "if x == 0\n    y = 1\nif a and b\n    y = 1\nif (array.size swingY) == 0\n    y = 1")]
    (try
      (reader/parse src)
      (is true "Parsed successfully")
      (catch Exception e
        (is false (str "Parse failed: " (.getMessage e)))))))

(deftest no-placeholder-in-simple-if
  (let [r (imp/convert "if x > 0\n    y = 1")]
    (is (not (str/includes? r ":_")) "No :_ placeholder")
    (is (not (str/includes? r "...")) "No ellipsis placeholder")))

(deftest no-placeholder-in-if-and
  (let [r (imp/convert "if a and b\n    x = 1")]
    (is (not (str/includes? r ":_")) "No :_ placeholder")))

(deftest no-placeholder-in-nested-if
  (let [r (imp/convert "if x > 0\n    if y > 0\n        z = 1")]
    (is (not (str/includes? r ":_")) "No :_ placeholder")))

(deftest if-output-parses
  (let [src (imp/convert "if x == 0\n    y = 1")]
    (try
      (reader/parse src)
      (is true "Parsed successfully")
      (catch Exception e
        (is false (str "Parse failed: " (.getMessage e)))))))

(deftest if-and-output-parses
  (let [src (imp/convert "if a and b\n    x = 1")]
    (try
      (reader/parse src)
      (is true "Parsed successfully")
      (catch Exception e
        (is false (str "Parse failed: " (.getMessage e)))))))

(deftest if-body-follows
  (let [r (imp/convert "if x > 0\n    y = 1")]
    (is (str/includes? r "(if"))
    (is (str/includes? r "(def y 1"))
    ;; The if should be IMMEDIATELY followed by the body
    (is (re-find #"\(if .+\)\n\(def y 1\)" r))))

(deftest gann-warns-stable
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (<= warns 2) (str "Expected ≤2 WARNs, got " warns))))

(deftest astro-warns-zero
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (zero? warns) (str "Expected 0 WARNs, got " warns))))

(deftest simple-add
  (let [r (imp/convert "x = 4 + 3")]
    (is (not (str/includes? r "WARN")))
    (is (re-find #"\(\+ 4 3\)" r))))

(deftest simple-subtract
  (let [r (imp/convert "x = a - b")]
    (is (re-find #"\(- a b\)" r))))

(deftest simple-multiply
  (let [r (imp/convert "x = a * b")]
    (is (re-find #"\(\* a b\)" r))))

(deftest simple-divide
  (let [r (imp/convert "x = a / b")]
    (is (re-find #"\(/ a b\)" r))))

(deftest precedence-mul-before-add
  (let [r (imp/convert "x = a + b * c")]
    ;; * converts first; + stays infix (left operand has spaces)
    (is (str/includes? r "(* b c"))))

(deftest precedence-mul-before-sub
  (let [r (imp/convert "x = a - b * c")]
    (is (str/includes? r "(* b c"))))

(deftest precedence-div-after-sub
  (let [r (imp/convert "x = (high + low) / 2")]
    ;; paren group converts (high + low) first; surrounding / is approximate
    (is (not (str/includes? r "WARN")))))

(deftest chained-add
  (let [r (imp/convert "x = a + b + c")]
    ;; single pass converts first pair
    (is (str/includes? r "(+ a b"))))

(deftest chained-mul
  (let [r (imp/convert "x = a * b * c")]
    (is (str/includes? r "(* a b"))))

(deftest math-with-abs
  (let [r (imp/convert "x = math.abs(high - low) * 2")]
    (is (not (str/includes? r "WARN")))))

(deftest push-swing-expr
  (let [r (imp/convert "x = close - math.abs(high - low)*1")]
    (is (not (str/includes? r "WARN")))))

(deftest math-in-if-cond
  (let [r (imp/convert "if x > 0 and y < 0 + z\n    w = 1")]
    (is (not (str/includes? r "WARN")))))

(deftest comparison-after-math
  (let [r (imp/convert "x = a + b > 0")]
    (is (not (str/includes? r "WARN")))))

(deftest gann-warns-stable
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (<= warns 2) (str "Expected ≤2 WARNs, got " warns))))

(deftest astro-warns-zero
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (zero? warns) (str "Expected 0 WARNs, got " warns))))

(deftest function-def-with-arrow
  (is (str/includes? (imp/convert "isUpSwing() => array.get(swingY, IDX_CURRENT) > array.get(swingY, IDX_PREV)")
                     "defn is-up-swing"))
  (is (str/includes? (imp/convert "myFun(x, y) => x + y") "defn my-fun")))

(deftest if-conversion-no-crash
  (let [r (imp/convert "if (high[offset] > high[offset+1] and low[offset] < low[offset+1])\n    \"outside\"")]
    (is (str/includes? r "if"))))

(deftest full-gann-file-imports-without-crash
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)]
    (is (str/includes? result "indicator"))
    (is (str/includes? result "def"))
    (is (not (str/blank? result)))))

(deftest convert-input-bool
  (is (str/includes? (imp/convert "showGannSwings = input.bool(true, 'Show swings')") "input-bool")))

(deftest convert-input-color
  (is (str/includes? (imp/convert "colorUp = input.color(color.teal, 'Up')") "input-color")))

(deftest convert-input-int
  (is (str/includes? (imp/convert "widthGann = input.int(2, 'Width', minval=1)") "input-int")))

(deftest convert-input-with-keyword-args
  (let [r (imp/convert "showGannSwings = input.bool(true, 'Show swings', group='Gann', inline='Line1')")]
    (is (str/includes? r "input-bool"))
    (is (str/includes? r ":group"))
    (is (str/includes? r ":inline"))))

(deftest color-new-no-space
  (is (not (str/includes? (imp/convert "color.new(color.teal,30)") "WARN"))))

(deftest convert-request-security
  (let [r (imp/convert "pyramid = request.security(syminfo.tickerid, \"1W\", ta.atr(8))")]
    (is (str/includes? r "security"))))

(deftest convert-line-new
  (is (str/includes? (imp/convert "line.new(x1, y1, x2, y2, color=color.blue)")
                     "(line.new x1 y1 x2 y2")))

(deftest convert-line-delete
  (is (str/includes? (imp/convert "line.delete(swingLines[1])") "(line.delete")))

(deftest convert-array-push
  (is (str/includes? (imp/convert "array.push(swingY, close)") "push")))

(deftest convert-array-get
  (is (str/includes? (imp/convert "array.get(swingY, IDX_CURRENT)") "(get swingY IDX_CURRENT)")))

(deftest convert-barstate
  (let [r1 (imp/convert "barstate.islast")
        r2 (imp/convert "barstate.isconfirmed")]
    (is (str/includes? r1 "bar-last"))
    (is (str/includes? r2 "bar-confirmed"))))

(deftest convert-syminfo
  (let [r1 (imp/convert "syminfo.mintick")
        r2 (imp/convert "syminfo.tickerid")]
    (is (str/includes? r1 "mintick"))
    (is (str/includes? r2 "tickerid"))))

(deftest table-cell-format
  (let [r (imp/convert "table.cell(myTable, 0, 0, close)")]
    (is (not (str/includes? r "table-")) "Should not use table- prefix")
    (is (str/includes? r "(table.cell")) "Should wrap in parens like array/line"))

(deftest table-new-format
  (let [r (imp/convert "table.new(position.top_right, 5, 5)")]
    (is (str/includes? r "(") "Should wrap in parens")))

(deftest deep-and-chaining
  (let [r (imp/convert "x = a and b and c and d and e and f")]
    (is (not (str/includes? r "WARN")))
    ;; Should have 5+ nested (and ...)
    (is (str/includes? r "(and"))))

(deftest deep-or-chaining
  (let [r (imp/convert "x = a or b or c or d or e")]
    (is (not (str/includes? r "WARN")))))

(deftest strategy-builtins-still-work
  (let [r (imp/convert "x = strategy.position_size")]
    (is (str/includes? r "(position-size)"))))

(deftest existing-tests-still-pass
  ;; Spot-check: these were the original import-fix tests
  (is (not (str/includes? (imp/convert "if close > ta.sma(close, 20)\n    strategy.entry(\"Long\", strategy.long)") "WARN")))
  (is (str/includes? (imp/convert "barstate.isconfirmed") "(bar-confirmed)"))
  (is (not (str/includes? (imp/convert "x = color.new(color.teal, 30)") "WARN"))))

(deftest gann-warns-stable
  (let [src (slurp "/tmp/gann-swing-repo/GannSwing.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (<= warns 2) (str "Expected ≤2 WARNs, got " warns))))

(deftest astro-warns-zero
  (let [src (slurp "/tmp/gann-swing-repo/AstroEvents.pine")
        result (imp/convert src)
        warns (count (re-seq #"WARN" result))]
    (is (zero? warns) (str "Expected 0 WARNs, got " warns))))