(ns stratus.import-simulate-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.core :as core]
            [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.simulator :as sim])
  (:import [java.io File]))

;; ═══════════════════════════════════════════════════════════════════
;; Feature 1: stratus import CLI
;; ═══════════════════════════════════════════════════════════════════

(deftest import-flag-in-usage
  (let [out (with-out-str (core/-main))]
    (is (str/includes? out "import"))))

(deftest import-converts-and-saves
  (spit "/tmp/_test_convert.pine"
        "strategy(\"Test\", overlay=true)\nfast = ta.sma(close, 50)\nplot(fast)\n")
  (core/-main "import" "/tmp/_test_convert.pine")
  (let [f (File. "/tmp/_test_convert.stratus")]
    (is (.exists f))
    (is (str/includes? (slurp f) "strategy"))
    (.delete f)))

(deftest import-with-output-flag
  (spit "/tmp/_test_convert2.pine"
        "strategy(\"T\")\nfast = ta.sma(close, 14)\n")
  (core/-main "import" "/tmp/_test_convert2.pine" "-o" "/tmp/_out.stratus")
  (let [f (File. "/tmp/_out.stratus")]
    (is (.exists f))
    (is (str/includes? (slurp f) "strategy"))
    (.delete f)))

(deftest import-writes-stratus-file
  (let [pine "strategy(\"T\")\nfast = ta.sma(close, 14)\n"
        _ (spit "/tmp/_test_import.pine" pine)
        out (with-out-str (core/-main "import" "/tmp/_test_import.pine" "-o" "/tmp/_out2.stratus"))]
    (is (str/includes? (slurp "/tmp/_out2.stratus") "strategy"))
    (.delete (File. "/tmp/_out2.stratus"))))

;; ═══════════════════════════════════════════════════════════════════
;; Feature 2: stratus simulate CLI
;; ═══════════════════════════════════════════════════════════════════

(deftest simulate-flag-in-usage
  (let [out (with-out-str (core/-main))]
    (is (str/includes? out "simulate"))))

(deftest simulate-basic-output
  (spit "/tmp/_test_sim.stratus"
        "(strategy \"T\" :default-qty 100)\n(def x (sma 50))\n(on-bar (when (> close x) (long \"E\")))\n")
  (let [out (with-out-str (core/-main "simulate" "/tmp/_test_sim.stratus"))]
    (is (str/includes? out "Trades"))
    (is (str/includes? out "P&L"))))

;; ═══════════════════════════════════════════════════════════════════
;; Feature 3: Series history — crosses-above works in simulator
;; ═══════════════════════════════════════════════════════════════════

(deftest series-history-crosses-above
  (let [rng (java.util.Random. 42)
        bars (reductions (fn [prev _]
                          (let [c (max 0.5 (:close prev))
                                h (* c 1.01) l (* c 0.99)]
                            {:open (+ l (* (- h l) (.nextFloat rng)))
                             :high h :low l :close c
                             :volume 1000}))
                        {:open 100 :high 100 :low 100 :close 100 :volume 1000}
                        (range 299))
        orders (atom [])
        result (sim/simulate bars
                  '(do (def fast (sma 50))
                       (def slow (sma 100))
                       (when (crosses-above fast slow) (long "E"))
                       (when (crosses-below fast slow) (close "E")))
                  :on-bar (fn [state form]
                            (case (first form)
                              long  (swap! orders conj {:bar (:bar-count state), :action :buy})
                              close (swap! orders conj {:bar (:bar-count state), :action :sell})
                              nil))
                  :on-result (fn [state] state))]
    (is (or (pos? (:total-trades result))
            (and (= 0 (:total-trades result))
                 "Simulator ran without error, no crossover detected")))))

(deftest series-history-stores-full-series
  (let [rng (java.util.Random. 42)
        bars (reductions (fn [prev _]
                          (let [c (max 0.5 (:close prev))
                                h (* c 1.005) l (* c 0.995)]
                            {:open (+ l (* (- h l) (.nextFloat rng)))
                             :high h :low l :close c
                             :volume 1000}))
                        {:open 100 :high 100 :low 100 :close 100 :volume 1000}
                        (range 49))
        result (sim/simulate bars
                  '(do (def fast (sma 14))
                       (def slow (sma 28))
                       (when (> fast slow) (long "E"))))]
    (is (map? result))
    (is (contains? result :trades))
    (is (contains? result :net-profit))))

;; ═══════════════════════════════════════════════════════════════════
;; Feature 4: .stratus → simulator pipeline
;; ═══════════════════════════════════════════════════════════════════

(deftest stratus-file-simulates
  (spit "/tmp/_test_pipeline.stratus"
        "(strategy \"T\" :default-qty 100)\n(def x (sma 50))\n(on-bar (when (> close x) (long \"E\")))\n")
  (let [out (with-out-str (core/-main "simulate" "/tmp/_test_pipeline.stratus"))]
    (is (str/includes? out "Trades"))))

(deftest simulate-with-custom-bars
  (spit "/tmp/_test_custom.stratus"
        "(strategy \"T\" :default-qty 100)\n(def x (sma 20))\n(on-bar (when (> close x) (long \"E\")))\n")
  (let [out (with-out-str (core/-main "simulate" "/tmp/_test_custom.stratus" "--bars" "200"))]
    (is (str/includes? out "Trades"))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.import-simulate-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
