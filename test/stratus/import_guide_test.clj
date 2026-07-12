(ns stratus.import-guide-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.import-guide :as guide]))

(deftest guide-prints-without-error
  (let [output (with-out-str (guide/print-guide))]
    (is (str/includes? output "Conversion Guide"))
    (is (str/includes? output "Pattern Reference"))))

(deftest conversion-table-not-empty
  (is (pos? (count guide/conversion-table))))

(deftest common-patterns-present
  (let [categories (set (map first guide/conversion-table))]
    (is (categories "SMA"))
    (is (categories "MACD"))
    (is (categories "Plot"))
    (is (categories "If"))))

(deftest syntax-guide-prints
  (let [output (with-out-str (guide/print-syntax-guide))]
    (is (str/includes? output "Syntax Rules"))))

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.import-guide-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
