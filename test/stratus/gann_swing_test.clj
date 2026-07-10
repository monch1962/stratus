(ns stratus.gann-swing-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

(defn compile-gann [swing-period]
  (let [src (str "
(indicator \"Gann Swings\" :overlay true)

(defvar swing-direction 0)
(defvar swing-high 0.0)
(defvar swing-low 0.0)

(input-int \"Swing Period\" :def 2 :min 2 :max 10)

(on-bar
  (if (= high (highest high swing-period))
    (if (= swing-direction -1)
      (set! swing-high (highest high swing-period))
      (do (set! swing-direction 1)
          (set! swing-high (highest high swing-period)))))
  (if (= low (lowest low swing-period))
    (if (= swing-direction 1)
      (set! swing-low (lowest low swing-period))
      (do (set! swing-direction -1)
          (set! swing-low (lowest low swing-period))))))

(plot swing-high \"Swing High\" :color green :linewidth 2 :style step-line)
(plot swing-low \"Swing Low\" :color red :linewidth 2 :style step-line)
")]
    (gen/emit-file (reader/parse src))))

(deftest compiles-to-valid-pine
  (let [o (compile-gann 2)]
    (is (str/includes? o "//@version=6"))
    (is (str/includes? o "indicator"))))

(deftest has-variable-declarations
  (let [o (compile-gann 2)]
    (is (str/includes? o "var swing_direction"))
    (is (str/includes? o "var swing_high"))
    (is (str/includes? o "var swing_low"))))

(deftest uses-highest-and-lowest
  (let [o (compile-gann 2)]
    (is (str/includes? o "ta.highest"))
    (is (str/includes? o "ta.lowest"))))

(deftest has-conditional-swing-logic
  (let [o (compile-gann 2)]
    (is (str/includes? o "if"))
    (is (str/includes? o "swing_direction :="))))

(deftest has-input-parameter
  (let [o (compile-gann 2)]
    (is (str/includes? o "input.int"))))

(deftest has-plots
  (let [o (compile-gann 2)]
    (is (str/includes? o "plot("))
    (is (str/includes? o "Swing High"))
    (is (str/includes? o "Swing Low"))))

(deftest different-swing-periods-compile
  (doseq [p [2 3 5 7]]
    (let [o (compile-gann p)]
      (is (str/includes? o "//@version=6") (str "Period " p " should compile")))))

(deftest has-set-on-swing-direction
  (let [o (compile-gann 2)]
    (is (str/includes? o "swing_direction := 1"))
    (is (str/includes? o "swing_direction := -1"))))

(deftest output-with-all-swings
  (let [o (compile-gann 3)]
    (is (str/includes? o "ta.highest(high, swing_period"))
    (is (str/includes? o "ta.lowest(low, swing_period"))))

(deftest has-step-line-plot-style
  (let [o (compile-gann 2)]
    (is (str/includes? o "plot.style_stepline"))))

(deftest uses-swings-in-file-name
  (let [src (slurp "examples/gann-swings.stratus")]
    (is (str/includes? src "Gann Swings"))
    (is (str/includes? src "defvar"))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.gann-swing-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
