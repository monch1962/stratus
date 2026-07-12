(ns stratus.constructs-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.constructs :as ct]))

(deftest constructs-not-empty
  (is (pos? (count ct/constructs))))

(deftest constructs-contains-expectations
  (let [names (set (map :name ct/constructs))]
    (is (names :sma))
    (is (names :macd))
    (is (names :plot))
    (is (names :input-int))
    (is (names :strategy))))

(deftest each-construct-has-required-keys
  (doseq [c ct/constructs]
    (is (:name c) (str "Missing :name: " (pr-str c)))
    (is (:category c) (str (or (:name c) "?") " missing :category"))
    (is (:doc c) (str (or (:name c) "?") " missing :doc"))))

(deftest fewer-than-static-list-but-accurate
  ;; Old static list had 51 stale entries. New list has 307 live entries.
  (is (>= (count ct/constructs) 250)))

(deftest lookup-works
  (let [c (ct/lookup :sma)]
    (is c)
    (is (= :sma (:name c)))
    (is (= :indicator (:category c)))))

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.constructs-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
