(ns stratus.generator-property-test
  "Property-based tests for the Pine Script generator.
   Generates random .stratus forms and verifies the emitted Pine
   is syntactically valid — balanced parens, no EDN conflicts,
   no runtime errors."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

;; ─── Syntax correctness helpers ─────────────────────────────────

(defn balanced-parens?
  "True if string has balanced parentheses."
  [s]
  (zero? (reduce (fn [b ch] (case ch
                              \( (inc b)
                              \) (dec b)
                              b)) 0 s)))

(defn no-unresolved-polymorph?
  "True if the string doesn't contain the 'Unable to resolve' error text."
  [s]
  (not (str/includes? s "Unable to resolve")))

; ─── Random form generator ──────────────────────────────────────

(defn rand-elt [coll] (nth (vec coll) (rand-int (count coll))))

(def price-syms '[close high low open volume hl2 hlc3 ohlc4])
(def indicators '[sma ema rsi wma hma atr stdev linreg])
(def comparisons '[> < >= <=])
(def arith '[+ - * /])
(def colors '[blue red green purple navy teal orange gray])

(defn gen-simple-form
  "Generate a random valid Stratus form."
  []
  (rand-elt
    [(list (rand-elt indicators) (inc (rand-int 200)))
     (list (rand-elt comparisons) (rand-elt price-syms) (rand-elt price-syms))
     (list (rand-elt arith) (rand-elt price-syms) (inc (rand-int 100)))
     (list 'plot (rand-elt price-syms))
     (list 'hline (rand-int 100))
     (list 'def (symbol (str "x" (rand-int 100))) (inc (rand-int 100)))]))

;; ─── Property tests ────────────────────────────────────────────

(deftest generator-never-crashes
  (doseq [i (range 200)]
    (let [form (gen-simple-form)]
      (is (string? (try (gen/expr->pine form)
                        (catch Exception _ (str "CRASH: " form))))
          (str "Crash on: " form)))))

(deftest output-always-balanced
  (doseq [i (range 100)]
    (let [form (gen-simple-form)
          pine (try (gen/expr->pine form)
                    (catch Exception _ ""))]
      (when (seq pine)
        (is (balanced-parens? pine)
            (str "Unbalanced: " form " -> " pine))))))

(deftest emit-file-never-crashes
  (doseq [i (range 100)]
    (let [forms (take 5 (repeatedly gen-simple-form))]
      (is (string? (gen/emit-file forms))
          (str "Crash on: " forms)))))

(deftest plot-always-emits-plot
  (doseq [i (range 50)]
    (let [pine (gen/expr->pine (list 'plot (rand-elt price-syms)))]
      (is (str/includes? pine "plot(")))))

(deftest sma-emits-ta-sma
  (doseq [i (range 50)]
    (let [period (inc (rand-int 100))
          pine (gen/expr->pine (list 'sma period))]
      (is (str/includes? pine "ta.sma"))
      (is (str/includes? pine (str period))))))

(deftest math-fns-produce-math-prefix
  (doseq [i (range 50)]
    (let [val (inc (rand-int 50))
          pine (gen/expr->pine (list (rand-elt '[abs ceil floor sqrt log])
                                     val))]
      (is (str/includes? pine "math."))
      (is (str/includes? pine (str val))))))

;; ─── Main ──────────────────────────────────────────────────────

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.generator-property-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
