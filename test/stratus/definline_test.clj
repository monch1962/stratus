(ns stratus.definline-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.inliner :as il]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

;; ═══════════════════════════════════════════════════════════════════
;; Inliner: collect definitions
;; ═══════════════════════════════════════════════════════════════════

(deftest collect-single-definition
  (let [forms '[(definline add [a b] (+ a b))]
        defs (il/collect-definitions forms)]
    (is (= #{'add} (set (keys defs))))
    (is (= '[a b] (:params (get defs 'add))))
    (is (= '[(+ a b)] (:body (get defs 'add))))))

(deftest collect-multiple-definitions
  (let [forms '[(definline add [a b] (+ a b))
                (definline double [x] (* x 2))]
        defs (il/collect-definitions forms)]
    (is (= #{'add 'double} (set (keys defs))))
    (is (= '(* x 2) (first (:body (get defs 'double)))))))

(deftest collect-multi-body
  (let [forms '[(definline setup [x]
                  (set! a (+ x 1))
                  (set! b (+ x 2)))]
        defs (il/collect-definitions forms)]
    (is (= 2 (count (:body (get defs 'setup)))))))

(deftest ignore-non-definline
  (let [forms '[(def x 1) (defn foo [] (set! a 1))]
        defs (il/collect-definitions forms)]
    (is (empty? defs))))

;; ═══════════════════════════════════════════════════════════════════
;; Inliner: expand individual calls
;; ═══════════════════════════════════════════════════════════════════

(deftest expand-simple-call
  (let [defs {'add {:params '[a b] :body '[(+ a b)]}}
        expanded (il/expand-call '(add 1 2) defs)]
    (is (= '(+ 1 2) expanded))))

(deftest expand-multi-arg-call
  (let [defs {'mul {:params '[x y] :body '((* x y))}}
        expanded (il/expand-call '(mul 3 4) defs)]
    (is (= '(* 3 4) expanded))))

(deftest expand-multi-statement
  (let [defs {'setup {:params '[x] :body '[(set! a (+ x 1)) (set! b (+ x 2))]}}
        expanded (il/expand-call '(setup 10) defs)]
    (is (= '(do (set! a (+ 10 1)) (set! b (+ 10 2))) expanded))))

(deftest no-expand-unknown
  (let [defs {'add {:params '[a b] :body '[(+ a b)]}}
        form '(def x 1)]
    (is (= form (il/expand-call form defs)))))

(deftest no-expand-definline-def
  (let [defs {}
        form '(definline foo [x] (set! x 1))]
    (is (= form (il/expand-call form defs)))))

;; ═══════════════════════════════════════════════════════════════════
;; Inliner: full pipeline
;; ═══════════════════════════════════════════════════════════════════

(deftest expand-full-pipeline
  (let [forms '[(definline add [a b] (+ a b))
                (def x 1)
                (def y (add x 2))]
        expanded (il/expand-all forms)]
    (is (= 2 (count expanded)))
    (is (not-any? #(= 'definline (first %)) expanded))
    (is (= '(+ x 2) (nth (nth expanded 1) 2)))))

(deftest expand-nested-inlines
  (let [forms '[(definline double [x] (* x 2))
                (definline quad [x] (double (double x)))
                (def y (quad 3))]
        expanded (il/expand-all forms)]
    (is (not-any? #(= 'definline (first %)) expanded))))

;; ═══════════════════════════════════════════════════════════════════
;; Inliner: interaction with generator
;; ═══════════════════════════════════════════════════════════════════

(deftest compile-inlined-stratus
  (let [source "(definline add [a b] (+ a b))\n(def x 1)\n(def y (add x 2))"
        ast (reader/parse source)
        expanded (il/expand-all ast)
        pine (gen/emit-file expanded)]
    (is (str/includes? pine "x = 1"))
    (is (str/includes? pine "y = x + 2"))))

;; ═══════════════════════════════════════════════════════════════════
;; defmacro — same compile-time substitution as definline
;; ═══════════════════════════════════════════════════════════════════

(deftest defmacro-collects
  (let [forms '[(defmacro ma-cross [fast slow] (when (crosses-above fast slow) (long "E")))]
        defs (il/collect-definitions forms)]
    (is (= #{'ma-cross} (set (keys defs))))))

(deftest defmacro-expands-simple
  (let [defs {'ma-cross {:params '[fast slow]
                          :body '[(when (crosses-above fast slow) (long "E"))]}}
        expanded (il/expand-call '(ma-cross (sma 14) (sma 50)) defs)]
    (is (= '(when (crosses-above (sma 14) (sma 50)) (long "E")) expanded))))

(deftest defmacro-full-compile
  (let [source "(defmacro ma-cross [fast slow]
                  (when (crosses-above fast slow) (long \"E\")))
                 (def x (sma 50))
                 (def y (sma 200))
                 (on-bar
                   (ma-cross x y))"
        ast (reader/parse source)
        inlined (il/expand-all ast)
        pine (gen/emit-file inlined)]
    (is (str/includes? pine "ta.cross"))
    (is (str/includes? pine "strategy.entry"))
    (is (not (str/includes? pine "defmacro")))
    (is (not (str/includes? pine "ma-cross")))))

(deftest defmacro-removed-from-output
  (let [source "(defmacro ignore [x] x)\n(def y (ignore 5))"
        ast (reader/parse source)
        inlined (il/expand-all ast)
        pine (gen/emit-file inlined)]
    (is (not (str/includes? pine "defmacro")))
    (is (str/includes? pine "y = 5"))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.definline-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
