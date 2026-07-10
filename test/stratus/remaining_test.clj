(ns stratus.remaining-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

;; ═══════════════════════════════════════════════════════════════════
;; P3: Math scalars
;; ═══════════════════════════════════════════════════════════════════

(deftest p3-math-scalars
  (is (= (gen/expr->pine '(log close)) "math.log(close)"))
  (is (= (gen/expr->pine '(log10 close)) "math.log10(close)"))
  (is (= (gen/expr->pine '(exp 2)) "math.exp(2)"))
  (is (= (gen/expr->pine '(sqrt 4)) "math.sqrt(4)"))
  (is (= (gen/expr->pine '(abs -5)) "math.abs(-5)"))
  (is (= (gen/expr->pine '(ceil 3.2)) "math.ceil(3.2)"))
  (is (= (gen/expr->pine '(floor 3.8)) "math.floor(3.8)"))
  (is (= (gen/expr->pine '(round 3.5)) "math.round(3.5)"))
  (is (= (gen/expr->pine '(pow 2 3)) "math.pow(2, 3)"))
  (is (= (gen/expr->pine '(min a b)) "math.min(a, b)"))
  (is (= (gen/expr->pine '(max a b c)) "math.max(a, b, c)"))
  (is (= (gen/expr->pine '(sign -5)) "math.sign(-5)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: syminfo builtins
;; ═══════════════════════════════════════════════════════════════════

(deftest p3-syminfo
  (is (= (gen/expr->pine '(mintick)) "syminfo.mintick"))
  (is (= (gen/expr->pine '(pointvalue)) "syminfo.pointvalue"))
  (is (= (gen/expr->pine '(sym-session)) "syminfo.session"))
  (is (= (gen/expr->pine '(sym-description)) "syminfo.description"))
  (is (= (gen/expr->pine '(sym-type)) "syminfo.type")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: strategy.risk
;; ═══════════════════════════════════════════════════════════════════

(deftest p3-strategy-risk
  (is (= (gen/expr->pine '(allow-entry-in "E")) "strategy.risk.allow_entry_in(\"E\")"))
  (is (= (gen/expr->pine '(max-intraday-orders 5)) "strategy.risk.max_intraday_filled_orders(5)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Input parameters
;; ═══════════════════════════════════════════════════════════════════

(deftest p4-input-params
  (is (= (gen/expr->pine '(input-int "Period" :def 14)) "input.int(14, \"Period\")"))
  (is (= (gen/expr->pine '(input-float "Threshold" :def 0.5 :step 0.1)) "input.float(0.5, \"Threshold\", step=0.1)"))
  (is (= (gen/expr->pine '(input-bool "Show" :def true)) "input.bool(true, \"Show\")"))
  (is (= (gen/expr->pine '(input-string "Label" :def "SMA")) "input.string(\"SMA\", \"Label\")"))
  (is (= (gen/expr->pine '(input-color "Line" :def blue)) "input.color(color.blue, \"Line\")"))
  (is (= (gen/expr->pine '(input-source "Src" :def close)) "input.source(close, \"Src\")"))
  (is (= (gen/expr->pine '(input-symbol "Ticker" :def "BTCUSD")) "input.symbol(\"BTCUSD\", \"Ticker\")"))
  (is (= (gen/expr->pine '(input-timeframe "TF")) "input.timeframe(\"TF\")")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Line drawing
;; ═══════════════════════════════════════════════════════════════════

(deftest p4-line-new
  (is (str/includes? (gen/expr->pine '(line.new x1 y1 x2 y2 :color blue :width 2))
                     "line.new(x1, y1, x2, y2"))
  (is (str/includes? (gen/expr->pine '(line.new x1 y1 x2 y2 :color blue :width 2))
                     "color=color.blue")))

(deftest p4-line-delete
  (is (= (gen/expr->pine '(line.delete my-line)) "line.delete(my_line)")))

(deftest p4-label-new
  (is (str/includes? (gen/expr->pine '(label.new x y "Text" :color blue :size :normal))
                     "label.new(x, y, \"Text\"")))

(deftest p4-box-new
  (is (str/includes? (gen/expr->pine '(box.new x1 y1 x2 y2 :bgcolor blue :border-color red))
                     "box.new(x1, y1, x2, y2")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: color.rgb / color.from-gradient / tostring
;; ═══════════════════════════════════════════════════════════════════

(deftest p4-color-fns
  (is (= (gen/expr->pine '(rgb 255 0 128)) "color.rgb(255, 0, 128)"))
  (is (= (gen/expr->pine '(rgb 255 0 128 50)) "color.rgb(255, 0, 128, 50)"))
  (is (str/includes? (gen/expr->pine '(from-gradient val min max "red" "blue"))
                     "color.from_gradient")))

(deftest p4-tostring
  (is (= (gen/expr->pine '(tostring close)) "str.tostring(close)"))
  (is (= (gen/expr->pine '(tostring close 2)) "str.tostring(close, 2)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: library export
;; ═══════════════════════════════════════════════════════════════════

(deftest p4-export
  (is (= (gen/expr->pine '(export my-fn)) "export my_fn"))
  (is (= (gen/expr->pine '(export my-indicator)) "export my_indicator")))

;; ═══════════════════════════════════════════════════════════════════
;; P5: Array basics
;; ═══════════════════════════════════════════════════════════════════

(deftest p5-array
  (is (= (gen/expr->pine '(array-int 10)) "array.new_int(10)"))
  (is (= (gen/expr->pine '(array-float 10 0.0)) "array.new_float(10, 0.0)"))
  (is (= (gen/expr->pine '(push arr 42)) "array.push(arr, 42)"))
  (is (= (gen/expr->pine '(pop arr)) "array.pop(arr)"))
  (is (= (gen/expr->pine '(size arr)) "array.size(arr)"))
  (is (= (gen/expr->pine '(get arr 0)) "array.get(arr, 0)"))
  (is (= (gen/expr->pine '(set arr 0 100)) "array.set(arr, 0, 100)"))
  (is (= (gen/expr->pine '(sort arr)) "array.sort(arr)")))

;; ═══════════════════════════════════════════════════════════════════
;; P5: request.dividends / splits / earnings
;; ═══════════════════════════════════════════════════════════════════

(deftest p5-request-fundamentals
  (is (= (gen/expr->pine '(dividends)) "request.dividends(syminfo.tickerid)"))
  (is (= (gen/expr->pine '(splits)) "request.splits(syminfo.tickerid)"))
  (is (= (gen/expr->pine '(earnings)) "request.earnings(syminfo.tickerid)")))

;; ═══════════════════════════════════════════════════════════════════
;; P5: table basics
;; ═══════════════════════════════════════════════════════════════════

(deftest p5-table
  (is (str/includes? (gen/expr->pine '(table.new :pos :top-right :cols 2 :rows 3 :bgcolor white))
                     "table.new"))
  (is (= (gen/expr->pine '(table-cell tbl 0 0 "Text")) "table.cell(tbl, 0, 0, \"Text\")")))

;; ═══════════════════════════════════════════════════════════════════
;; Integration: full library with exports
;; ═══════════════════════════════════════════════════════════════════

(deftest p3p4p5-full-library
  (let [src "
(library \"MyLib\" :overlay true)
(defn my-ema [src n] (ema src n))
(export my-ema)

(defn my-log-ratio [a b] (log (/ a b)))
(export my-log-ratio)"
        o (gen/emit-file (reader/parse src))]
    (is (str/includes? o "library(\"MyLib\""))
    (is (str/includes? o "export my_ema"))
    (is (str/includes? o "export my_log_ratio"))
    (is (str/includes? o "my_ema(src, n) =>"))
    (is (str/includes? o "ta.ema(src, n)"))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.remaining-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
