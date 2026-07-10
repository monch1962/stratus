(ns stratus.p2p3-remaining-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.generator :as gen]
            [stratus.constructs :as ct]
            [stratus.reader :as reader]))

;; ═══════════════════════════════════════════════════════════════════
;; P2: ta.kc (Keltner Channels)
;; ═══════════════════════════════════════════════════════════════════

(deftest kc-generates
  (is (= (gen/expr->pine '(kc 20)) "ta.kc(close, 20, 2.0)"))
  (is (= (gen/expr->pine '(kc 20 2.5)) "ta.kc(close, 20, 2.5)"))
  (is (= (gen/expr->pine '(kc hlc3 20 2.0)) "ta.kc(hlc3, 20, 2.0)")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: ticker.new
;; ═══════════════════════════════════════════════════════════════════

(deftest ticker-new-generates
  (is (= (gen/expr->pine '(ticker.new prefix sym)) "ticker.new(prefix, sym)"))
  (is (= (gen/expr->pine '(ticker.new "BATS" "AAPL")) "ticker.new(\"BATS\", \"AAPL\")"))
  (is (= (gen/expr->pine '(ticker.new prefix sym "BLX")) "ticker.new(prefix, sym, \"BLX\")")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: request.security_lower_tf
;; ═══════════════════════════════════════════════════════════════════

(deftest security-lower-tf-generates
  (is (= (gen/expr->pine '(security-lower-tf "5" close)) "request.security_lower_tf(syminfo.tickerid, \"5\", close)"))
  (is (= (gen/expr->pine '(security-lower-tf "15" (sma 20))) "request.security_lower_tf(syminfo.tickerid, \"15\", ta.sma(close, 20))")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: Strategy performance — grossprofit, grossloss, maxdrawdown, maxrunup
;; ═══════════════════════════════════════════════════════════════════

(deftest strategy-performance
  (is (= (gen/expr->pine '(gross-profit)) "strategy.grossprofit"))
  (is (= (gen/expr->pine '(gross-loss)) "strategy.grossloss"))
  (is (= (gen/expr->pine '(max-drawdown)) "strategy.maxdrawdown"))
  (is (= (gen/expr->pine '(max-runup)) "strategy.maxrunup")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: Type predicates
;; ═══════════════════════════════════════════════════════════════════

(deftest type-predicates
  (is (= (gen/expr->pine '(series x)) "series(x)"))
  (is (= (gen/expr->pine '(array x)) "array(x)"))
  (is (= (gen/expr->pine '(string x)) "string(x)"))
  (is (= (gen/expr->pine '(int x)) "int(x)"))
  (is (= (gen/expr->pine '(float x)) "float(x)"))
  (is (= (gen/expr->pine '(bool x)) "bool(x)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: ta.cmo, ta.wad
;; ═══════════════════════════════════════════════════════════════════

(deftest cmo-generates
  (is (= (gen/expr->pine '(cmo 14)) "ta.cmo(close, 14)"))
  (is (= (gen/expr->pine '(cmo close 14)) "ta.cmo(close, 14)")))

(deftest wad-generates
  (is (= (gen/expr->pine '(wad)) "ta.wad"))
  (is (= (gen/expr->pine '(wad high low close)) "ta.wad(high, low, close)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: ta.crossunder / ta.crossover (raw)
;; ═══════════════════════════════════════════════════════════════════

(deftest raw-cross-directional
  (is (= (gen/expr->pine '(crossover a b)) "ta.crossover(a, b)"))
  (is (= (gen/expr->pine '(crossunder a b)) "ta.crossunder(a, b)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: math.round with precision
;; ═══════════════════════════════════════════════════════════════════

(deftest round-with-precision
  (is (= (gen/expr->pine '(round x)) "math.round(x)"))
  (is (= (gen/expr->pine '(round x 2)) "math.round(x, 2)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: Additional label/line/box property setters
;; ═══════════════════════════════════════════════════════════════════

(deftest label-extended-setters
  (is (= (gen/expr->pine '(label.set-style l label.style-label-down)) "label.set_style(l, label.style_label_down)"))
  (is (= (gen/expr->pine '(label.set-textcolor l color.red)) "label.set_textcolor(l, color.red)"))
  (is (= (gen/expr->pine '(label.set-textalign l text.align-center)) "label.set_textalign(l, text.align_center)"))
  (is (= (gen/expr->pine '(label.set-size l size.normal)) "label.set_size(l, size.normal)")))

(deftest line-extended-setters
  (is (= (gen/expr->pine '(line.set-xloc l xloc.bar-index)) "line.set_xloc(l, xloc.bar_index)")))

(deftest box-extended-setters
  (is (= (gen/expr->pine '(box.set-extend b extend.right)) "box.set_extend(b, extend.right)"))
  (is (= (gen/expr->pine '(box.set-style b line.style-dashed)) "box.set_style(b, line.style_dashed)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: str.contains with position (3-arg)
;; ═══════════════════════════════════════════════════════════════════

(deftest str-contains-with-position
  (is (= (gen/expr->pine '(str.contains s "abc")) "str.contains(s, \"abc\")"))
  (is (= (gen/expr->pine '(str.contains s "abc" 5)) "str.contains(s, \"abc\", 5)")))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.p2p3-remaining-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
