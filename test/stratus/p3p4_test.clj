(ns stratus.p3p4-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.generator :as gen]
            [stratus.constructs :as ct]))

;; ═══════════════════════════════════════════════════════════════════
;; P3: Array type variants
;; ═══════════════════════════════════════════════════════════════════

(deftest array-type-variants
  (is (= (gen/expr->pine '(array-int 10)) "array.new_int(10)"))
  (is (= (gen/expr->pine '(array-float 10)) "array.new_float(10)"))
  (is (= (gen/expr->pine '(array-bool 5)) "array.new_bool(5)"))
  (is (= (gen/expr->pine '(array-string 5)) "array.new_string(5)"))
  (is (= (gen/expr->pine '(array-color 3)) "array.new_color(3)"))
  (is (= (gen/expr->pine '(array-line 3)) "array.new_line(3)"))
  (is (= (gen/expr->pine '(array-label 3)) "array.new_label(3)"))
  (is (= (gen/expr->pine '(array-box 3)) "array.new_box(3)"))
  (is (= (gen/expr->pine '(array-table 2)) "array.new_table(2)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: Array operations
;; ═══════════════════════════════════════════════════════════════════

(deftest array-crud-operations
  (is (= (gen/expr->pine '(array.insert arr 0 val)) "array.insert(arr, 0, val)"))
  (is (= (gen/expr->pine '(array.remove arr 0)) "array.remove(arr, 0)"))
  (is (= (gen/expr->pine '(array.clear arr)) "array.clear(arr)"))
  (is (= (gen/expr->pine '(array.concat a b)) "array.concat(a, b)"))
  (is (= (gen/expr->pine '(array.slice arr 0 5)) "array.slice(arr, 0, 5)"))
  (is (= (gen/expr->pine '(array.copy arr)) "array.copy(arr)")))

(deftest array-stack-operations
  (is (= (gen/expr->pine '(array.shift arr)) "array.shift(arr)"))
  (is (= (gen/expr->pine '(array.unshift arr val)) "array.unshift(arr, val)")))

(deftest array-query-operations
  (is (= (gen/expr->pine '(array.includes arr val)) "array.includes(arr, val)"))
  (is (= (gen/expr->pine '(array.indexof arr val)) "array.indexof(arr, val)"))
  (is (= (gen/expr->pine '(array.lastindexof arr val)) "array.lastindexof(arr, val)")))

(deftest array-statistics
  (is (= (gen/expr->pine '(array.min arr)) "array.min(arr)"))
  (is (= (gen/expr->pine '(array.max arr)) "array.max(arr)"))
  (is (= (gen/expr->pine '(array.avg arr)) "array.avg(arr)"))
  (is (= (gen/expr->pine '(array.median arr)) "array.median(arr)"))
  (is (= (gen/expr->pine '(array.sum arr)) "array.sum(arr)"))
  (is (= (gen/expr->pine '(array.stdev arr)) "array.stdev(arr)"))
  (is (= (gen/expr->pine '(array.mode arr)) "array.mode(arr)"))
  (is (= (gen/expr->pine '(array.range arr)) "array.range(arr)")))

;; ═══════════════════════════════════════════════════════════════════
;; P3: Table operations
;; ═══════════════════════════════════════════════════════════════════

(deftest table-lifecycle
  (is (= (gen/expr->pine '(table.clear tbl)) "table.clear(tbl)"))
  (is (= (gen/expr->pine '(table.delete tbl)) "table.delete(tbl)"))
  (is (= (gen/expr->pine '(table.merge-cells tbl 0 0 1 2)) "table.merge_cells(tbl, 0, 0, 1, 2)")))

(deftest table-setters
  (is (= (gen/expr->pine '(table.set-position tbl top.right)) "table.set_position(tbl, table.position_top_right)"))
  (is (= (gen/expr->pine '(table.set-size tbl 3 4)) "table.set_size(tbl, 3, 4)"))
  (is (= (gen/expr->pine '(table.set-color tbl color.red)) "table.set_color(tbl, color.red)"))
  (is (= (gen/expr->pine '(table.set-bgcolor tbl color.blue)) "table.set_bgcolor(tbl, color.blue)"))
  (is (= (gen/expr->pine '(table.set-border-color tbl color.gray)) "table.set_border_color(tbl, color.gray)"))
  (is (= (gen/expr->pine '(table.set-border-width tbl 2)) "table.set_border_width(tbl, 2)")))

(deftest table-getters
  (is (= (gen/expr->pine '(table.get-location tbl)) "table.get_location(tbl)"))
  (is (= (gen/expr->pine '(table.get-size tbl)) "table.get_size(tbl)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: String functions
;; ═══════════════════════════════════════════════════════════════════

(deftest string-functions
  (is (= (gen/expr->pine '(str.contains s "abc")) "str.contains(s, \"abc\")"))
  (is (= (gen/expr->pine '(str.length s)) "str.length(s)"))
  (is (= (gen/expr->pine '(str.split s ",")) "str.split(s, \",\")"))
  (is (= (gen/expr->pine '(str.lower s)) "str.lower(s)"))
  (is (= (gen/expr->pine '(str.upper s)) "str.upper(s)"))
  (is (= (gen/expr->pine '(str.replace-all s "a" "b")) "str.replace_all(s, \"a\", \"b\")"))
  (is (= (gen/expr->pine '(str.substring s 0 5)) "str.substring(s, 0, 5)"))
  (is (= (gen/expr->pine '(str.substr s 0 5)) "str.substr(s, 0, 5)"))
  (is (= (gen/expr->pine '(str.startswith s "a")) "str.startswith(s, \"a\")"))
  (is (= (gen/expr->pine '(str.endswith s "z")) "str.endswith(s, \"z\")")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Type conversion
;; ═══════════════════════════════════════════════════════════════════

(deftest type-conversion
  (is (= (gen/expr->pine '(int x)) "int(x)"))
  (is (= (gen/expr->pine '(float x)) "float(x)"))
  (is (= (gen/expr->pine '(str.tonumber s)) "str.tonumber(s)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Timestamp
;; ═══════════════════════════════════════════════════════════════════

(deftest timestamp-generates
  (is (= (gen/expr->pine '(timestamp "2024-01-01")) "timestamp(\"2024-01-01\")")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: request.financial / request.random
;; ═══════════════════════════════════════════════════════════════════

(deftest request-functions
  (is (= (gen/expr->pine '(financial "income_statement" "netIncome" "ttm")) "request.financial(syminfo.tickerid, \"income_statement\", \"netIncome\", \"ttm\")"))
  (is (= (gen/expr->pine '(random seed 0 100)) "request.random(seed, 0, 100)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: order.* functions
;; ═══════════════════════════════════════════════════════════════════

(deftest order-functions
  (is (= (gen/expr->pine '(order.entry-condition)) "order.entry_condition()"))
  (is (= (gen/expr->pine '(order.exit-condition)) "order.exit_condition()"))
  (is (= (gen/expr->pine '(order.filled-condition)) "order.filled_condition()"))
  (is (= (gen/expr->pine '(order.filled)) "order.filled()"))
  (is (= (gen/expr->pine '(order.entry-id)) "order.entry_id()")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: chart.point
;; ═══════════════════════════════════════════════════════════════════

(deftest chart-point
  (is (= (gen/expr->pine '(chart.point.now)) "chart.point.now()"))
  (is (= (gen/expr->pine '(chart.point.from-index 100)) "chart.point.from_index(100)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Polygon
;; ═══════════════════════════════════════════════════════════════════

(deftest polygon
  (is (= (gen/expr->pine '(polygon.new p1 p2 p3)) "polygon.new(p1, p2, p3)"))
  (is (= (gen/expr->pine '(polygon.new p1 p2 p3 :color blue)) "polygon.new(p1, p2, p3, color=color.blue)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Matrix (core operations)
;; ═══════════════════════════════════════════════════════════════════

(deftest matrix-basic
  (is (= (gen/expr->pine '(matrix.new 3 3 0.0)) "matrix.new<float>(3, 3, 0.0)"))
  (is (= (gen/expr->pine '(matrix.rows m)) "matrix.rows(m)"))
  (is (= (gen/expr->pine '(matrix.columns m)) "matrix.columns(m)"))
  (is (= (gen/expr->pine '(matrix.size m)) "matrix.size(m)"))
  (is (= (gen/expr->pine '(matrix.get m 0 1)) "matrix.get(m, 0, 1)"))
  (is (= (gen/expr->pine '(matrix.set m 0 1 val)) "matrix.set(m, 0, 1, val)"))
  (is (= (gen/expr->pine '(matrix.row m 0)) "matrix.row(m, 0)"))
  (is (= (gen/expr->pine '(matrix.col m 0)) "matrix.col(m, 0)"))
  (is (= (gen/expr->pine '(matrix.sum m)) "matrix.sum(m)"))
  (is (= (gen/expr->pine '(matrix.transpose m)) "matrix.transpose(m)"))
  (is (= (gen/expr->pine '(matrix.multiply a b)) "matrix.multiply(a, b)"))
  (is (= (gen/expr->pine '(matrix.inv m)) "matrix.inv(m)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Map
;; ═══════════════════════════════════════════════════════════════════

(deftest map-operations
  (is (= (gen/expr->pine '(map.new)) "map.new<color, int>()"))
  (is (= (gen/expr->pine '(map.put m k v)) "map.put(m, k, v)"))
  (is (= (gen/expr->pine '(map.get m k)) "map.get(m, k)"))
  (is (= (gen/expr->pine '(map.delete m k)) "map.delete(m, k)"))
  (is (= (gen/expr->pine '(map.contains m k)) "map.contains(m, k)"))
  (is (= (gen/expr->pine '(map.keys m)) "map.keys(m)"))
  (is (= (gen/expr->pine '(map.values m)) "map.values(m)"))
  (is (= (gen/expr->pine '(map.size m)) "map.size(m)")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Time (time_close, time_tradingday)
;; ═══════════════════════════════════════════════════════════════════

(deftest time-functions
  (is (= (gen/expr->pine '(time.close)) "time_close"))
  (is (= (gen/expr->pine '(time.close 1)) "time_close[1]"))
  (is (= (gen/expr->pine '(time.tradingday)) "time_tradingday")))

;; ═══════════════════════════════════════════════════════════════════
;; P4: Ticker functions
;; ═══════════════════════════════════════════════════════════════════

(deftest ticker-functions
  (is (= (gen/expr->pine '(ticker.heikinashi sym)) "ticker.heikinashi(sym)"))
  (is (= (gen/expr->pine '(ticker.renko sym)) "ticker.renko(sym)"))
  (is (= (gen/expr->pine '(ticker.linebreak sym)) "ticker.linebreak(sym)"))
  (is (= (gen/expr->pine '(ticker.kagi sym)) "ticker.kagi(sym)"))
  (is (= (gen/expr->pine '(ticker.pnf sym)) "ticker.pnf(sym)"))
  (is (= (gen/expr->pine '(ticker.range sym)) "ticker.range(sym)")))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.p3p4-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
