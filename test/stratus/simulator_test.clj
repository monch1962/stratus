(ns stratus.simulator-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.simulator :as sim]))

(defn make-bars
  "Generate N bars of synthetic price data.
   Each bar: {:open n, :high n, :low n, :close n, :volume n}"
  [n & {:keys [trend volatility start]
        :or {trend 0.002, volatility 0.01, start 100.0}}]
  (let [rng (java.util.Random. 42)]
    (reductions
      (fn [prev _]
        (let [ret (+ trend (* volatility (.nextGaussian rng)))
              prev-close (:close prev)
              c (max 0.5 (* prev-close (Math/exp ret)))
              h (max c (* c (+ 1 (* volatility (.nextGaussian rng) 0.3))))
              l (min c (* c (- 1 (* volatility (.nextGaussian rng) 0.3))))
              o (+ l (* (- h l) (.nextFloat rng)))]
          {:open o, :high h, :low l, :close c, :volume (int (+ 1000 (* 500 (.nextGaussian rng))))}))
      {:open start, :high start, :low start, :close start, :volume 1000}
      (range (dec n)))))

(deftest simulator-bars-are-valid
  (let [bars (make-bars 100)]
    (is (= 100 (count bars)))
    (is (every? :close bars))
    (is (every? (fn [b] (<= (:low b) (:close b))) bars))))

(deftest simulate-sma-crossover-fires
  (let [bars    (make-bars 300 {:trend 0.005})
        orders  (atom [])
        result  (sim/simulate bars
                  '(do (def fast (sma 50))
                       (def slow (sma 100))
                       (def cross-up (crosses-above fast slow))
                       (def cross-down (crosses-below fast slow))
                       (when cross-up (long "ENTER"))
                       (when cross-down (close "EXIT")))
                  :on-bar (fn [state form]
                            (case (first form)
                              long  (swap! orders conj {:bar (:bar-count state), :action :buy})
                              close (swap! orders conj {:bar (:bar-count state), :action :sell})
                              nil))
                  :on-result (fn [_] @orders))]
    (is (map? result))
    (is (contains? result :trades))
    (is (contains? result :net-profit))
    (is (contains? result :total-trades))))

(deftest simulate-profit-calculated
  (let [bars    (make-bars 100 {:trend 0.005 :volatility 0.01})
        result  (sim/simulate bars
                  '(do
                     (def fast (sma 20))
                     (def slow (sma 50))
                     (when (crosses-above fast slow) (long "E"))
                     (when (crosses-below fast slow) (close "E"))))]
    (is (map? result))
    (is (contains? result :trades))
    (is (contains? result :net-profit))
    (is (contains? result :total-trades))))

(deftest simulate-empty-bars-no-trades
  (let [bars    (make-bars 50 {:trend -0.001 :volatility 0.005})
        result  (sim/simulate bars
                  '(do (def fast (sma 50))
                       (def slow (sma 200))
                       (when (crosses-above fast slow) (long "E"))))]
    (is (map? result))
    (is (= 0 (:total-trades result)))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.simulator-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
