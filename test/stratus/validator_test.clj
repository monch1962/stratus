(ns stratus.validator-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.validator :as val]
            [stratus.reader :as reader]))

;; ═══════════════════════════════════════════════════════════════════
;; Known construct detection
;; ═══════════════════════════════════════════════════════════════════

(deftest validates-known-construct
  (let [issues (val/validate '[(sma 14)])]
    (is (empty? issues))))

(deftest warns-unknown-construct
  (let [issues (val/validate '[(zark 14)])]
    (is (str/includes? (first issues) "zark"))))

(deftest warns-typo-construct
  (let [issues (val/validate '[(smaa 14)])]
    (is (str/includes? (first issues) "smaa"))))

;; ═══════════════════════════════════════════════════════════════════
;; Argument count validation
;; ═══════════════════════════════════════════════════════════════════

(deftest warns-too-few-args
  (let [issues (val/validate '[(sma)])]
    (is (str/includes? (first issues) "at least 1"))))

(deftest warns-too-many-args
  (let [issues (val/validate '[(sma 14 15 16 17)])]
    (is (str/includes? (first issues) "at most 3"))))

(deftest valid-arg-count-passes
  (let [issues (val/validate '[(sma 14) (sma close 14) (sma close 14 5)])]
    (is (empty? issues))))

(deftest pow-requires-two-args
  (let [issues (val/validate '[(pow 2)])]
    (is (str/includes? (first issues) "at least 2"))))

;; ═══════════════════════════════════════════════════════════════════
;; Range validation — arity checks cover most real issues.
;; Full argument indexing depends on source detection (future work).

(deftest valid-args-pass
  (let [issues (val/validate '[(sma 14) (rsi 14) (atr 14)])]
    (is (empty? issues))))

;; ═══════════════════════════════════════════════════════════════════
;; Integration with reader
;; ═══════════════════════════════════════════════════════════════════

(deftest validate-from-parsed-file
  (let [forms (reader/parse "(sma 14)\n(zark 99)\n(def x 1)")
        issues (val/validate forms)]
    (is (= 1 (count issues)))
    (is (str/includes? (first issues) "zark"))))

(deftest report-function
  (let [forms (reader/parse "(sma 14)")]
    (is (val/report forms))))

(deftest report-with-warnings
  (let [forms (reader/parse "(zark 14)")]
    (is (not (val/report forms)))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.validator-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
