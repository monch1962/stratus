(ns stratus.simulator-data-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.simulator :as sim]))

(deftest load-csv-basic
  (let [csv "time,open,high,low,close,volume\n1,100,105,95,102,5000\n2,102,108,98,105,6000"
        bars (sim/load-csv csv)]
    (is (= 2 (count bars)))
    (is (= 100.0 (:open (first bars))))
    (is (= 102.0 (:close (first bars))))
    (is (= 5000 (:volume (first bars))))))

(deftest load-csv-case-insensitive
  (let [csv "Time,Open,High,Low,Close,Volume\n1,10,11,9,10.5,1000"
        bars (sim/load-csv csv)]
    (is (= 1 (count bars)))
    (is (= 10.0 (:open (first bars))))))

(deftest load-csv-empty-lines-skipped
  (let [csv "time,open,high,low,close,volume\n1,10,12,9,11,1000\n\n\n2,11,13,10,12,2000"
        bars (sim/load-csv csv)]
    (is (= 2 (count bars)))))

(deftest load-csv-missing-column-defaults
  (let [csv "open,close\n10,11\n20,22"
        bars (sim/load-csv csv)]
    (is (= 2 (count bars)))
    (is (= 0.0 (:high (first bars))))))

(deftest load-csv-invalid-number-handles
  (let [csv "time,open,high,low,close,volume\n1,abc,105,95,102,5000"
        bars (sim/load-csv csv)]
    (is (= 1 (count bars)))
    (is (= 0.0 (:open (first bars))))))

(deftest simulate-with-csv-data
  (let [csv "time,open,high,low,close,volume\n1,100,105,95,102,5000\n2,102,108,98,105,6000"
        bars (sim/load-csv csv)
        result (sim/simulate bars '(do (when (> close (sma 1)) (long "buy"))))]
    (is (map? result))
    (is (contains? result :trades))
    (is (contains? result :net-profit))))

(defn -main [& args]
  (let [result (clojure.test/run-tests 'stratus.simulator-data-test)]
    (System/exit (if (zero? (:fail result)) 0 1))))
