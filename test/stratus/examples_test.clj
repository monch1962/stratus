(ns stratus.examples-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.constructs :as ct])
  (:import [java.io File]))

(def example-dir "examples")

(defn load-example [name]
  (slurp (str example-dir "/" name ".stratus")))

(defn compile-example [name]
  (let [src (load-example name)]
    (gen/emit-file (reader/parse src))))

;; ═══════════════════════════════════════════════════════════════════
;; Existing examples (backward compatibility)
;; ═══════════════════════════════════════════════════════════════════

(deftest golden-cross-compiles
  (let [o (compile-example "golden-cross")]
    (is (str/includes? o "//@version=6"))
    (is (str/includes? o "strategy"))
    (is (str/includes? o "sma"))
    (is (str/includes? o "cross"))
    (is (str/includes? o "plot"))))

(deftest rsi-divergence-compiles
  (let [o (compile-example "rsi-divergence")]
    (is (str/includes? o "indicator"))
    (is (str/includes? o "rsi"))
    (is (str/includes? o "plotshape"))
    (is (str/includes? o "hline"))
    (is (str/includes? o "alertcondition"))))

(deftest adaptive-regime-compiles
  (let [o (compile-example "adaptive-regime")]
    (is (str/includes? o "adx"))
    (is (str/includes? o "rising"))
    (is (str/includes? o "falling"))
    (is (str/includes? o "rsi"))))

;; ═══════════════════════════════════════════════════════════════════
;; New P0-P1 examples
;; ═══════════════════════════════════════════════════════════════════

(deftest trailing-bollinger-compiles
  (let [o (compile-example "trailing-bollinger")]
    (is (str/includes? o "strategy"))
    (is (str/includes? o "bb"))
    (is (str/includes? o "strategy.exit"))
    (is (str/includes? o "atr"))
    (is (str/includes? o "var stop_dist"))
    (is (str/includes? o "stop_dist :="))
    (is (str/includes? o "if"))
    (is (str/includes? o "else"))))

(deftest mtf-macd-compiles
  (let [o (compile-example "multi-timeframe-macd")]
    (is (str/includes? o "request.security"))
    (is (str/includes? o "macd"))
    (is (str/includes? o "[m, s, h]"))
    (is (str/includes? o "na"))
    (is (str/includes? o "nz"))
    (is (str/includes? o "iff"))
    (is (str/includes? o "alertcondition"))))

(deftest inputs-library-compiles
  (let [o (compile-example "inputs-library")]
    (is (str/includes? o "library"))
    (is (str/includes? o "input.int"))
    (is (str/includes? o "input.source"))
    (is (str/includes? o "input.bool"))
    (is (str/includes? o "input.color"))
    (is (str/includes? o "input.string"))
    (is (str/includes? o "export"))
    (is (str/includes? o "=>"))
    (is (str/includes? o "tostring"))
    (is (str/includes? o "color.rgb"))))

(deftest math-and-stats-compiles
  (let [o (compile-example "math-and-stats")]
    (is (str/includes? o "math.sum"))
    (is (str/includes? o "math.avg"))
    (is (str/includes? o "stdev"))
    (is (str/includes? o "ta.correlation"))
    (is (str/includes? o "ta.median"))
    (is (str/includes? o "ta.percentile_nearest_rank"))
    (is (str/includes? o "math.cum"))
    (is (str/includes? o "mom"))
    (is (str/includes? o "ta.highest"))
    (is (str/includes? o "ta.lowest"))
    (is (str/includes? o "ta.highestbars"))
    (is (str/includes? o "math.floor"))
    (is (str/includes? o "math.ceil"))
    (is (str/includes? o "math.sqrt"))
    (is (str/includes? o "math.abs"))
    (is (str/includes? o "z_score"))))  ;; verifies kebab→snake_case

(deftest array-table-drawing-compiles
  (let [o (compile-example "array-table-drawing")]
    (is (str/includes? o "array.new_float"))
    (is (str/includes? o "array.push"))
    (is (str/includes? o "array.pop"))
    (is (str/includes? o "array.size"))
    (is (str/includes? o "array.get"))
    (is (str/includes? o "table.new"))
    (is (str/includes? o "table.cell"))
    (is (str/includes? o "line.new"))))

(deftest control-flow-compiles
  (let [o (compile-example "control-flow")]
    (is (str/includes? o "if"))
    (is (str/includes? o "else"))
    (is (str/includes? o "for"))
    (is (str/includes? o "sum :="))
    (is (str/includes? o "math.sqrt"))
    (is (str/includes? o "strategy.entry"))))

(deftest color-fills-bg-compiles
  (let [o (compile-example "color-fills-bg")]
    (is (str/includes? o "barcolor"))
    (is (str/includes? o "bgcolor"))
    (is (str/includes? o "color.rgb"))
    (is (str/includes? o "color.from_gradient"))))

(deftest advanced-exit-compiles
  (let [o (compile-example "advanced-exit")]
    (is (str/includes? o "strategy.exit"))
    (is (str/includes? o "from=\"ENTER\""))
    (is (str/includes? o "profit=20"))
    (is (str/includes? o "loss="))
    (is (str/includes? o "trail_points="))
    (is (str/includes? o "trail_offset="))
    (is (str/includes? o "alertcondition"))))

;; ═══════════════════════════════════════════════════════════════════
;; All examples compile without errors
;; ═══════════════════════════════════════════════════════════════════

(def all-examples
  ["golden-cross" "rsi-divergence" "adaptive-regime"
   "trailing-bollinger" "multi-timeframe-macd"
   "inputs-library" "math-and-stats"
   "array-table-drawing" "control-flow"
   "color-fills-bg" "advanced-exit"
   "gann-swings"])

(deftest all-examples-compile
  (doseq [name all-examples]
    (let [o (try (compile-example name)
                 (catch Exception e (str "ERROR: " (.getMessage e))))]
      (is (str/includes? o "//@version=6")
          (str name " must compile to valid Pine Script"))
      (is (not (str/starts-with? o "ERROR"))
          (str name " must not throw: " o)))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.examples-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
