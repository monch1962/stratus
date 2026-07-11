(ns stratus.import-if-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.importer :as imp]
            [stratus.reader :as reader]))

;; ═══════════════════════════════════════════════════════════════════
;; If: == → = in conditions
;; ═══════════════════════════════════════════════════════════════════

(deftest if-equality-operator
  (let [r (imp/convert "if x == 0\n    y = 1")]
    (is (not (str/includes? r "==")) "No == in output")
    (is (str/includes? r "(= x 0"))))

(deftest if-equality-with-call
  (let [r (imp/convert "if (array.size swingY) == 0\n    y = 1")]
    (is (str/includes? r "(= (array.size swingY) 0"))))

(deftest if-not-equal
  (let [r (imp/convert "if x != 0\n    y = 1")]
    (is (not (str/includes? r "!=")) "No != in output")
    (is (str/includes? r "(not (= x 0"))))

;; ═══════════════════════════════════════════════════════════════════
;; If: and/or in conditions
;; ═══════════════════════════════════════════════════════════════════

(deftest if-two-ands
  (let [r (imp/convert "if year == (year ts) and month == (month ts) and dayofmonth == (dayofmonth ts)\n    x = 1")]
    (is (str/includes? r "(and"))
    (is (not (re-find #"(?<!\()and" r)) "No bare 'and' in output")))

(deftest if-and-or
  (let [r (imp/convert "if a and b or c\n    x = 1")]
    (is (str/includes? r "(and"))
    (is (str/includes? r "(or"))))

(deftest if-and-with-not
  (let [r (imp/convert "if trendIsUp and (not isUpSwing()) and a > b\n    x = 1")]
    (is (str/includes? r "(not"))
    (is (not (re-find #"(?<!\()and" r)) "No bare 'and'")))

;; ═══════════════════════════════════════════════════════════════════
;; If: comparison operators > < >= <=
;; ═══════════════════════════════════════════════════════════════════

(deftest if-greater-than
  (let [r (imp/convert "if ts > barMidnight and ts < barMidnight + nextGap\n    x = 1")]
    (is (str/includes? r "(and"))
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; If: else if with complex condition
;; ═══════════════════════════════════════════════════════════════════

(deftest else-if-and
  (let [r (imp/convert "else if (not trendIsUp) and isUpSwing() and a < b\n    x = 1")]
    (is (not (str/includes? r "WARN")))))

;; ═══════════════════════════════════════════════════════════════════
;; If: in assignments (convert-expr context)
;; ═══════════════════════════════════════════════════════════════════

(deftest assignment-and-condition
  (let [r (imp/convert "x = a and b")]
    (is (str/includes? r "(and a b"))))

;; ═══════════════════════════════════════════════════════════════════
;; Params-as-stratus: output parses as valid EDN
;; ═══════════════════════════════════════════════════════════════════

(deftest if-output-parses
  (let [src (imp/convert "if x == 0\n    y = 1\nif a and b\n    y = 1\nif (array.size swingY) == 0\n    y = 1")]
    (try
      (reader/parse src)
      (is true "Parsed successfully")
      (catch Exception e
        (is false (str "Parse failed: " (.getMessage e)))))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.import-if-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
