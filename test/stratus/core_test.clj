(ns stratus.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.constructs :as ct]))

(deftest test-reader-parses-simple-form
  (let [result (reader/parse "(sma 14)")]
    (is (= 1 (count result)))
    (is (= 'sma (ffirst result)))))

(deftest test-reader-parses-multiple-forms
  (let [result (reader/parse "(def fast (sma 50))\n(def slow (sma 200))")]
    (is (= 2 (count result)))
    (is (= 'def (ffirst result)))
    (is (= 'def (ffirst (rest result))))))

(deftest test-reader-handles-comments
  (let [result (reader/parse ";; this is a comment\n(sma 14)")]
    (is (= 1 (count result)))
    (is (= 'sma (ffirst result)))))

(deftest test-reader-handles-keywords
  (let [result (reader/parse "(plot val \"Title\" :color blue :linewidth 2)")]
    (is (= 1 (count result)))
    (is (= 'plot (ffirst result)))))

(deftest test-reader-handles-strings
  (let [result (reader/parse "(strategy \"Golden Cross\")")]
    (is (= 1 (count result)))
    (is (= 'strategy (ffirst result)))
    (is (= "Golden Cross" (second (first result))))))

(deftest test-construct-lookup
  (is (= :sma (:name (ct/lookup :sma))))
  (is (= :indicator (:category (ct/lookup :rsi))))
  (is (= :plot (:category (ct/lookup :plot))))
  (is (= :decl (:category (ct/lookup :strategy))))
  (is (nil? (ct/lookup :nonexistent))))

(deftest test-indicator-default-source
  (is (true? (ct/has-default-source? (ct/lookup :sma))))
  (is (false? (ct/has-default-source? (ct/lookup :stoch)))))

(deftest test-generate-sma
  (let [result (gen/expr->pine '(sma 14))]
    (is (= "ta.sma(close, 14)" result))))

(deftest test-generate-ema
  (let [result (gen/expr->pine '(ema 20))]
    (is (= "ta.ema(close, 20)" result))))

(deftest test-generate-rsi
  (let [result (gen/expr->pine '(rsi 14))]
    (is (= "ta.rsi(close, 14)" result))))

(deftest test-generate-crosses-above
  (let [result (gen/expr->pine '(crosses-above (sma 50) (sma 200)))]
    (is (str/includes? result "ta.cross("))
    (is (str/includes? result "ta.sma(close, 50)"))
    (is (str/includes? result "ta.sma(close, 200)"))))

(deftest test-generate-logic-and
  (let [result (gen/expr->pine '(and (rising close) (> volume (sma 20))))]
    (is (str/includes? result " and "))))

(deftest test-generate-long-entry
  (let [result (gen/expr->pine '(long "ENTER"))]
    (is (str/includes? result "strategy.entry"))
    (is (str/includes? result "ENTER"))))

(deftest test-generate-plot
  (let [result (gen/expr->pine '(plot (sma 50) "Fast" :color blue))]
    (is (str/includes? result "plot("))
    (is (str/includes? result "ta.sma(close, 50)"))
    (is (str/includes? result "color=color.blue"))))

(deftest test-generate-hline
  (let [result (gen/expr->pine '(hline 70 "Overbought" :color red :linestyle dashed))]
    (is (str/includes? result "hline(70"))
    (is (str/includes? result "color=color.red"))
    (is (str/includes? result "hline.style_dashed"))))

(deftest test-generate-whole-strategy
  (let [src "
(strategy \"Test\"
  :default-qty 100
  :pyramiding 1)

(def fast (sma 50))
(def slow (sma 200))

(on-bar
  (when (crosses-above fast slow)
    (long \"ENTER\")))

(plot fast \"Fast\" :color blue)
"
        forms  (reader/parse src)
        output (gen/emit-file forms)]
    (is (str/includes? output "//@version=6"))
    (is (str/includes? output "strategy(\"Test\""))
    (is (str/includes? output "ta.sma(close, 50)"))
    (is (str/includes? output "ta.cross("))
    (is (str/includes? output "strategy.entry"))
    (is (str/includes? output "plot("))))

(deftest test-construct-index-completeness
  (doseq [c ct/constructs]
    (is (:name c) (str "Construct missing :name: " (pr-str c)))
    (is (:category c) (str "Construct " (:name c) " missing :category"))
    (is (:doc c) (str "Construct " (:name c) " missing :doc"))))

(defn -main [& args]
  (let [results (clojure.test/run-tests 'stratus.core-test)]
    (System/exit (if (zero? (:fail results)) 0 1))))
