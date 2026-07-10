(ns stratus.p1p3-final-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.generator :as gen]
            [stratus.constructs :as ct]
            [stratus.reader :as reader]))

;; ═══════════════════════════════════════════════════════════════════
;; P1a: on-bar emits flat if blocks (no wrapper)
;; ═══════════════════════════════════════════════════════════════════

(deftest on-bar-emits-flat-if-blocks
  (let [o (gen/expr->pine '(on-bar
                            (when (crosses-above fast slow) (long "E"))
                            (when (crosses-below fast slow) (close "X"))))]
    (is (re-find #"(?m)^if ta\.cross" o))
    (is (str/includes? o "strategy.entry"))
    (is (str/includes? o "strategy.close"))
    (is (not (str/includes? o "on-bar")))))

;; ═══════════════════════════════════════════════════════════════════
;; P1b: switch generates proper Pine syntax
;; ═══════════════════════════════════════════════════════════════════

(deftest switch-pine-syntax
  (let [o (gen/expr->pine '(switch regime 0 (long "TREND") 1 (short "MR") :else (close "X")))]
    (is (str/includes? o "switch"))
    (is (str/includes? o "=>"))
    (is (str/includes? o "strategy.close"))))

(deftest switch-in-emit-file
  (let [o (gen/emit-file (reader/parse "
(strategy \"T\" :default-qty 100)
(def x 0)
(switch x 1 (long) 2 (short) :else (close))"))]
    (is (str/includes? o "switch"))
    (is (str/includes? o "=>"))))

;; ═══════════════════════════════════════════════════════════════════
;; P2: plotchar / plotarrow
;; ═══════════════════════════════════════════════════════════════════

(deftest plotchar-generates
  (is (= (gen/expr->pine '(plotchar cond "Entry" :location top :char "▲" :color green))
         "plotchar(cond, \"Entry\", location=location.top, char=\"▲\", color=color.green)")))

(deftest plotarrow-generates
  (is (= (gen/expr->pine '(plotarrow cond "Arrow" :colorup blue :colordown red))
         "plotarrow(cond, \"Arrow\", colorup=color.blue, colordown=color.red)")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: math constants
;; ═══════════════════════════════════════════════════════════════════

(deftest math-constants
  (is (= (gen/expr->pine '(pi)) "math.pi"))
  (is (= (gen/expr->pine '(tau)) "math.tau"))
  (is (= (gen/expr->pine '(e)) "math.e")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: Strategy builtins — openprofit, trade counts
;; ═══════════════════════════════════════════════════════════════════

(deftest strategy-builtins
  (is (= (gen/expr->pine '(position-size)) "strategy.position_size"))
  (is (= (gen/expr->pine '(position-avg-price)) "strategy.position_avg_price"))
  (is (= (gen/expr->pine '(open-trades)) "strategy.opentrades"))
  (is (= (gen/expr->pine '(equity)) "strategy.equity"))
  (is (= (gen/expr->pine '(net-profit)) "strategy.netprofit"))
  (is (= (gen/expr->pine '(open-profit)) "strategy.openprofit"))
  (is (= (gen/expr->pine '(win-trades)) "strategy.wintrades"))
  (is (= (gen/expr->pine '(loss-trades)) "strategy.losstrades"))
  (is (= (gen/expr->pine '(closed-trades)) "strategy.closedtrades")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: Calendar/time builtins
;; ═══════════════════════════════════════════════════════════════════

(deftest calendar-builtins
  (is (= (gen/expr->pine '(year)) "year"))
  (is (= (gen/expr->pine '(dayofmonth)) "dayofmonth"))
  (is (= (gen/expr->pine '(dayofweek)) "dayofweek"))
  (is (= (gen/expr->pine '(month)) "month"))
  (is (= (gen/expr->pine '(weekofyear)) "weekofyear"))
  (is (= (gen/expr->pine '(quarter)) "quarter"))
  (is (= (gen/expr->pine '(hour)) "hour"))
  (is (= (gen/expr->pine '(minute)) "minute"))
  (is (= (gen/expr->pine '(second)) "second")))

;; ═══════════════════════════════════════════════════════════════════
;; P2: Barstate builtins
;; ═══════════════════════════════════════════════════════════════════

(deftest barstate-builtins
  (is (= (gen/expr->pine '(bar-confirmed)) "barstate.isconfirmed"))
  (is (= (gen/expr->pine '(bar-first)) "barstate.isfirst"))
  (is (= (gen/expr->pine '(bar-last)) "barstate.islast"))
  (is (= (gen/expr->pine '(bar-new)) "barstate.isnew"))
  (is (= (gen/expr->pine '(bar-realtime)) "barstate.isrealtime"))
  (is (= (gen/expr->pine '(bar-history)) "barstate.ishistory")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: Color component extractors
;; ═══════════════════════════════════════════════════════════════════

(deftest color-components
  (is (= (gen/expr->pine '(color.r c)) "color.r(c)"))
  (is (= (gen/expr->pine '(color.g c)) "color.g(c)"))
  (is (= (gen/expr->pine '(color.b c)) "color.b(c)"))
  (is (= (gen/expr->pine '(color.t c)) "color.t(c)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: array.fill / array.reverse
;; ═══════════════════════════════════════════════════════════════════

(deftest array-fill-reverse
  (is (= (gen/expr->pine '(array.fill arr val)) "array.fill(arr, val)"))
  (is (= (gen/expr->pine '(array.reverse arr)) "array.reverse(arr)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: matrix extended
;; ═══════════════════════════════════════════════════════════════════

(deftest matrix-extended
  (is (= (gen/expr->pine '(matrix.fill m 0.0)) "matrix.fill(m, 0.0)"))
  (is (= (gen/expr->pine '(matrix.det m)) "matrix.det(m)"))
  (is (= (gen/expr->pine '(matrix.rank m)) "matrix.rank(m)"))
  (is (= (gen/expr->pine '(matrix.pinv m)) "matrix.pinv(m)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: syminfo builtins
;; ═══════════════════════════════════════════════════════════════════

(deftest syminfo-extended
  (is (= (gen/expr->pine '(mintick)) "syminfo.mintick"))
  (is (= (gen/expr->pine '(pointvalue)) "syminfo.pointvalue"))
  (is (= (gen/expr->pine '(currency)) "syminfo.currency"))
  (is (= (gen/expr->pine '(base-currency)) "syminfo.basecurrency"))
  (is (= (gen/expr->pine '(price-scale)) "syminfo.pricescale"))
  (is (= (gen/expr->pine '(min-move)) "syminfo.minmov"))
  (is (= (gen/expr->pine '(sym-sector)) "syminfo.sector"))
  (is (= (gen/expr->pine '(sym-industry)) "syminfo.industry")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: Drawing object getters
;; ═══════════════════════════════════════════════════════════════════

(deftest line-getters
  (is (= (gen/expr->pine '(line.get-x1 l)) "line.get_x1(l)"))
  (is (= (gen/expr->pine '(line.get-x2 l)) "line.get_x2(l)"))
  (is (= (gen/expr->pine '(line.get-y1 l)) "line.get_y1(l)"))
  (is (= (gen/expr->pine '(line.get-y2 l)) "line.get_y2(l)"))
  (is (= (gen/expr->pine '(line.get-price l x)) "line.get_price(l, x)")))

(deftest label-getters
  (is (= (gen/expr->pine '(label.get-x l)) "label.get_x(l)"))
  (is (= (gen/expr->pine '(label.get-y l)) "label.get_y(l)"))
  (is (= (gen/expr->pine '(label.get-text l)) "label.get_text(l)")))

(deftest box-getters
  (is (= (gen/expr->pine '(box.get-left b)) "box.get_left(b)"))
  (is (= (gen/expr->pine '(box.get-top b)) "box.get_top(b)"))
  (is (= (gen/expr->pine '(box.get-right b)) "box.get_right(b)"))
  (is (= (gen/expr->pine '(box.get-bottom b)) "box.get_bottom(b)")))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.p1p3-final-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
