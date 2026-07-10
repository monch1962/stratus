(ns stratus.input-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.constructs :as ct]))

;; ═══════════════════════════════════════════════════════════════════
;; Missing input types
;; ═══════════════════════════════════════════════════════════════════

(deftest input-price-generates
  (is (= (gen/expr->pine '(input-price "Stop Loss" :def 100.0))
         "input.price(100.0, \"Stop Loss\")"))
  (is (= (gen/expr->pine '(input-price "Price" :def 50.0 :step 0.5))
         "input.price(50.0, \"Price\", step=0.5)")))

(deftest input-session-generates
  (is (= (gen/expr->pine '(input-session "Session"))
         "input.session(\"Session\")"))
  (is (= (gen/expr->pine '(input-session "Trading Hours" :def "0930-1600"))
         "input.session(\"0930-1600\", \"Trading Hours\")")))

;; ═══════════════════════════════════════════════════════════════════
;; Keyword arg name mapping (min→minval, max→maxval)
;; ═══════════════════════════════════════════════════════════════════

(deftest input-int-uses-minval
  (is (str/includes? (gen/expr->pine '(input-int "Period" :def 14 :min 1 :max 50))
                     "minval=1"))
  (is (str/includes? (gen/expr->pine '(input-int "Period" :def 14 :min 1 :max 50))
                     "maxval=50")))

(deftest input-float-uses-minval
  (is (str/includes? (gen/expr->pine '(input-float "Level" :def 0.5 :min 0.0 :max 1.0))
                     "minval=0.0"))
  (is (str/includes? (gen/expr->pine '(input-float "Level" :def 0.5 :min 0.0 :max 1.0))
                     "maxval=1.0")))

(deftest input-int-preserves-other-kwargs
  (let [o (gen/expr->pine '(input-int "P" :def 14 :step 2 :group "G1" :confirm true))]
    (is (str/includes? o "step=2"))
    (is (str/includes? o "group=\"G1\""))
    (is (str/includes? o "confirm=true"))))

(deftest input-int-default-single-arg
  (is (= (gen/expr->pine '(input-int "Period"))
         "input.int(\"Period\")"))
  (is (= (gen/expr->pine '(input-int "Period" :def 20))
         "input.int(20, \"Period\")")))

;; ═══════════════════════════════════════════════════════════════════
;; Constructs registry entries
;; ═══════════════════════════════════════════════════════════════════

(deftest constructs-contains-all-inputs
  (let [names (set (map :name ct/constructs))]
    (doseq [req [:input-int :input-float :input-bool :input-string
                 :input-color :input-source :input-symbol :input-timeframe
                 :input-price :input-session]]
      (is (names req) (str "Missing construct: " req)))))

(deftest input-constructs-have-categories
  (doseq [c (filter #(str/starts-with? (name (:name %)) "input-") ct/constructs)]
    (is (= :input (:category c)) (str (:name c) " should be category :input"))
    (is (:doc c) (str (:name c) " missing :doc"))))

;; ═══════════════════════════════════════════════════════════════════
;; Integration: Gann swings example generates correct minval/maxval
;; ═══════════════════════════════════════════════════════════════════

(deftest gann-swings-uses-minval-maxval
  (let [o (gen/emit-file (reader/parse (slurp "examples/gann-swings.stratus")))]
    (is (str/includes? o "minval=1"))
    (is (str/includes? o "maxval=10"))
    ;; Should NOT have the old incorrect forms
    (is (not (str/includes? o "min=1")))
    (is (not (str/includes? o "max=10")))))

(deftest gann-swing-test-minval
  (require 'stratus.gann-swing-test)
  (let [r (eval '(clojure.test/run-tests 'stratus.gann-swing-test))]
    (is (zero? (:fail r)))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.input-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
