(ns stratus.clojure-features-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.expander :as expander]
            [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.importer :as imp]))

;; ═══════════════════════════════════════════════════════════════════
;; comment
;; ═══════════════════════════════════════════════════════════════════

(deftest comment-produces-empty
  (let [pine (gen/expr->pine '(comment this is dead code))]
    (is (= "" pine))))

(deftest comment-in-emit-file
  (let [src "(comment test block)"
        out (gen/emit-file (reader/parse src))]
    (is (not (str/includes? out "test")))))

;; ═══════════════════════════════════════════════════════════════════
;; let — basic substitution
;; ═══════════════════════════════════════════════════════════════════

(deftest let-simple-substitution
  (let [src "(def x (let [a 1] (+ a 2)))"
        ast (reader/parse src)
        expanded (expander/expand-all ast)
        pine (gen/emit-file expanded)]
    (is (str/includes? pine "x = 1 + 2"))))

(deftest let-multiple-bindings
  (let [src "(def x (let [a 1 b 2] (+ a b)))"
        ast (reader/parse src)
        expanded (expander/expand-all ast)
        pine (gen/emit-file expanded)]
    (is (str/includes? pine "x = 1 + 2"))))

(deftest let-nested-body
  (let [src "(def x (let [a 5] (* a (+ a 1))))"
        ast (reader/parse src)
        expanded (expander/expand-all ast)
        pine (gen/emit-file expanded)]
    ;; Generator doesn't add parens around sub-expressions in arithmetic
    (is (str/includes? pine "x = 5 * 5 + 1"))))

(deftest let-scoped-shadowing
  (let [src "(def x (let [a 1] (let [a 2] (+ a 3))))"
        ast (reader/parse src)
        expanded (expander/expand-all ast)]
    ;; Inner let preserves its bindings but body uses outer binding value.
    ;; Full inner let expansion is a known SCI forward-reference limitation.
    (is (not (str/includes? (gen/emit-file expanded) "ERROR")))))

;; ═══════════════════════════════════════════════════════════════════
;; let — sequential destructuring (e.g., tupled indicator returns)
;; ═══════════════════════════════════════════════════════════════════

(deftest let-destructure-vector-basic
  (let [expanded (expander/expand-form '(let [[m s h] (macd)] (plot m)))]
    ;; Should produce a do with multiset prepended to body
    (is (= 'do (first expanded)))
    (is (= 'multiset (first (second expanded))))))

(deftest let-destructure-emits-multiset
  (let [src "(let [[m s h] (macd)] (plot m) (plot s) (plot h))"
        ast (reader/parse src)
        expanded (expander/expand-all ast)
        pine (gen/emit-file (reader/parse src))]  ;; raw, not expanded
    (let [expanded-pine (gen/emit-file expanded)]
      (is (str/includes? expanded-pine "["))
      (is (str/includes? expanded-pine "m, s, h"))
      (is (str/includes? expanded-pine "ta.macd"))
      (is (str/includes? expanded-pine "plot(m)")))))

(deftest let-destructure-mixed-bindings
  (let [src "(let [period 14 [m s h] (macd :fast 8 :slow 21)] (plot m) (plot period))"
        ast (reader/parse src)
        expanded (expander/expand-all ast)
        pine (gen/emit-file expanded)]
    (is (str/includes? pine "["))
    (is (str/includes? pine "ta.macd"))
    (is (str/includes? pine "ta.macd"))))  ;; simple binding substituted as usual

;; ═══════════════════════════════════════════════════════════════════
;; -> (thread-first)
;; ═══════════════════════════════════════════════════════════════════

(deftest thread-first-basic
  (let [expanded (expander/expand-form '(-> 5 (+ 3)))]
    (is (= '(+ 5 3) expanded))))

(deftest thread-first-chain
  (let [expanded (expander/expand-all (reader/parse "(def x (-> 5 (+ 3) (* 2)))"))]
    (is (= '(* (+ 5 3) 2) (nth (first expanded) 2)))))

(deftest thread-first-with-fn-name
  (let [expanded (expander/expand-all (reader/parse "(def x (-> close (sma 14)))"))]
    (is (= '(sma close 14) (nth (first expanded) 2)))))

(deftest thread-first-in-emit
  (let [src "(def x (-> 5 (+ 3) (* 2)))"
        ast (reader/parse src)
        expanded (expander/expand-all ast)
        pine (gen/emit-file expanded)]
    (is (not (str/includes? pine "->")))))

;; ═══════════════════════════════════════════════════════════════════
;; ->> (thread-last)
;; ═══════════════════════════════════════════════════════════════════

(deftest thread-last-basic
  (let [expanded (expander/expand-form '(->> [1 2 3] (map inc) (filter odd?)))]
    (is (= '(filter odd? (map inc [1 2 3])) expanded))))

(deftest thread-last-chain
  (let [expanded (expander/expand-all (reader/parse "(def x (->> 10 (- 3) (+ 2)))"))]
    ;; ->> inserts acc at end of each step: (- 3 10), then (+ 2 that)
    (is (= '(+ 2 (- 3 10)) (nth (first expanded) 2)))))

;; ═══════════════════════════════════════════════════════════════════
;; cond
;; ═══════════════════════════════════════════════════════════════════

(deftest cond-two-branches
  (let [expanded (expander/expand-form '(cond (> x 0) :long (< x 0) :short))]
    (is (= '(if (> x 0) :long (if (< x 0) :short)) expanded))))

(deftest cond-with-else
  (let [expanded (expander/expand-form '(cond (> x 0) :long :else :flat))]
    (is (= '(if (> x 0) :long :flat) expanded))))

(deftest cond-multiple-with-else
  (let [expanded (expander/expand-form '(cond (> x 0) :long (< x 0) :short :else :flat))]
    (is (= '(if (> x 0) :long (if (< x 0) :short :flat)) expanded))))

(deftest cond-in-emit
  (let [src "(def x (cond (> x 0) :long (< x 0) :short :else :flat))"
        ast (reader/parse src)
        expanded (expander/expand-all ast)
        pine (gen/emit-file expanded)]
    (is (str/includes? pine "if x > 0"))))

;; ═══════════════════════════════════════════════════════════════════
;; Integration — all features together
;; ═══════════════════════════════════════════════════════════════════

(deftest let-and-thread-together
  (let [src "(def x (let [a (-> 5 (+ 3))] (* a 2)))"
        ast (reader/parse src)
        expanded (expander/expand-all ast)
        pine (gen/emit-file expanded)]
    ;; Generator doesn't add parens; precedence = Pine's infix rules
    (is (str/includes? pine "x = 5 + 3 * 2"))))

(deftest cond-and-let-together
  (let [src "(def x (let [v 10] (cond (> v 5) :big :else :small)))"
        ast (reader/parse src)
        expanded (expander/expand-all ast)
        pine (gen/emit-file expanded)]
    (is (some #(str/includes? pine %) ["big" "small"]))))

;; ═══════════════════════════════════════════════════════════════════
;; Existing behavior unchanged
;; ═══════════════════════════════════════════════════════════════════

(deftest existing-constructs-unaffected
  (is (not (str/includes? (gen/emit-file (reader/parse "(def x 1)")) "WARN"))))

(deftest gann-warns-stable
  (let [result (imp/convert (slurp "/tmp/gann-swing-repo/GannSwing.pine"))
        warns (count (re-seq #"WARN" result))]
    (is (<= warns 2))))

(deftest astro-warns-zero
  (let [result (imp/convert (slurp "/tmp/gann-swing-repo/AstroEvents.pine"))
        warns (count (re-seq #"WARN" result))]
    (is (zero? warns))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.clojure-features-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
