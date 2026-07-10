(ns stratus.last-missing-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.generator :as gen]
            [stratus.constructs :as ct]
            [stratus.reader :as reader]))

;; ═══════════════════════════════════════════════════════════════════
;; P3: ta.mama
;; ═══════════════════════════════════════════════════════════════════

(deftest mama-generates
  (is (= (gen/expr->pine '(mama)) "ta.mama(close, 0.5, 0.05)"))
  (is (= (gen/expr->pine '(mama close 0.5 0.05)) "ta.mama(close, 0.5, 0.05)"))
  (is (= (gen/expr->pine '(mama hlc3 0.6 0.1)) "ta.mama(hlc3, 0.6, 0.1)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: math.phi
;; ═══════════════════════════════════════════════════════════════════

(deftest phi-generates
  (is (= (gen/expr->pine '(phi)) "math.phi")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: request.seed
;; ═══════════════════════════════════════════════════════════════════

(deftest seed-generates
  (is (= (gen/expr->pine '(seed 42)) "request.seed(42)"))
  (is (= (gen/expr->pine '(seed 12345)) "request.seed(12345)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Polygon lifecycle
;; ═══════════════════════════════════════════════════════════════════

(deftest polygon-extended
  (is (= (gen/expr->pine '(polygon.delete p)) "polygon.delete(p)"))
  (is (= (gen/expr->pine '(polygon.set-fillcolor p color.red)) "polygon.set_fillcolor(p, color.red)"))
  (is (= (gen/expr->pine '(polygon.set-bordercolor p color.blue)) "polygon.set_bordercolor(p, color.blue)"))
  (is (= (gen/expr->pine '(polygon.set-borderwidth p 2)) "polygon.set_borderwidth(p, 2)"))
  (is (= (gen/expr->pine '(polygon.get-fillcolor p)) "polygon.get_fillcolor(p)"))
  (is (= (gen/expr->pine '(polygon.get-bordercolor p)) "polygon.get_bordercolor(p)"))
  (is (= (gen/expr->pine '(polygon.get-borderwidth p)) "polygon.get_borderwidth(p)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: ta.cog
;; ═══════════════════════════════════════════════════════════════════

(deftest cog-generates
  (is (= (gen/expr->pine '(cog 10)) "ta.cog(close, 10)"))
  (is (= (gen/expr->pine '(cog close 10)) "ta.cog(close, 10)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: ta.vwmacd
;; ═══════════════════════════════════════════════════════════════════

(deftest vwmacd-generates
  (is (= (gen/expr->pine '(vwmacd)) "ta.vwmacd(close, 12, 26, 9)"))
  (is (= (gen/expr->pine '(vwmacd close 12 26 9)) "ta.vwmacd(close, 12, 26, 9)"))
  (is (= (gen/expr->pine '(vwmacd hlc3 10 20 8)) "ta.vwmacd(hlc3, 10, 20, 8)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Matrix type variants
;; ═══════════════════════════════════════════════════════════════════

(deftest matrix-type-variants
  (is (= (gen/expr->pine '(matrix.float 3 3 0.0)) "matrix.new<float>(3, 3, 0.0)"))
  (is (= (gen/expr->pine '(matrix.int 3 3 0)) "matrix.new<int>(3, 3, 0)"))
  (is (= (gen/expr->pine '(matrix.bool 3 3 true)) "matrix.new<bool>(3, 3, true)"))
  (is (= (gen/expr->pine '(matrix.color 3 3 color.red)) "matrix.new<color>(3, 3, color.red)"))
  (is (= (gen/expr->pine '(matrix.string 3 3 "")) "matrix.new<string>(3, 3, \"\")"))
  (is (= (gen/expr->pine '(matrix.line 3 3)) "matrix.new<line>(3, 3)"))
  (is (= (gen/expr->pine '(matrix.label 3 3)) "matrix.new<label>(3, 3)"))
  (is (= (gen/expr->pine '(matrix.box 3 3)) "matrix.new<box>(3, 3)"))
  (is (= (gen/expr->pine '(matrix.table 3 3)) "matrix.new<table>(3, 3)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: ticker.modify
;; ═══════════════════════════════════════════════════════════════════

(deftest ticker-modify-generates
  (is (= (gen/expr->pine '(ticker.modify t "")) "ticker.modify(t, \"\")"))
  (is (= (gen/expr->pine '(ticker.modify t "60")) "ticker.modify(t, \"60\")"))
  (is (= (gen/expr->pine '(ticker.modify t "60" session)) "ticker.modify(t, \"60\", session)")))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.last-missing-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
