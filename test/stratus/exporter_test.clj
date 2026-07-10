(ns stratus.exporter-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.exporter :as exp]))

;; ─── TV response parsing ───────────────────────────────────────────
;; TV history endpoint returns JSON like:
;; {"s":"ok","t":[1700000000],"o":[100.0],"h":[102.0],"l":[99.0],"c":[101.0],"v":[1000]}

(def sample-tv-ok
  "{\"s\":\"ok\",\"t\":[1700000000,1700000100],\"o\":[100.0,101.0],\"h\":[102.0,103.0],\"l\":[99.0,100.0],\"c\":[101.0,102.0],\"v\":[1000,1100]}")

(def sample-tv-no-data
  "{\"s\":\"no_data\"}")

(deftest parse-tv-response-ok
  (let [bars (exp/parse-tv-response sample-tv-ok)]
    (is (= 2 (count bars)))
    (is (= 1700000000 (:time (first bars))))
    (is (= 100.0 (:open (first bars))))
    (is (= 102.0 (:high (first bars))))
    (is (= 99.0 (:low (first bars))))
    (is (= 101.0 (:close (first bars))))
    (is (= 1000 (:volume (first bars))))))

(deftest parse-tv-response-no-data
  (is (nil? (exp/parse-tv-response sample-tv-no-data))))

(deftest parse-tv-response-empty-timestamps
  (let [bars (exp/parse-tv-response "{\"s\":\"ok\",\"t\":[],\"o\":[],\"h\":[],\"l\":[],\"c\":[],\"v\":[]}")]
    (is (zero? (count bars)))))

(deftest parse-tv-response-single-bar
  (let [bars (exp/parse-tv-response "{\"s\":\"ok\",\"t\":[1700000000],\"o\":[100.0],\"h\":[102.0],\"l\":[99.0],\"c\":[101.0],\"v\":[1000]}")]
    (is (= 1 (count bars)))
    (is (= 1700000000 (:time (first bars))))))

;; ─── CSV formatting ────────────────────────────────────────────────

(deftest csv-header
  (let [bars (exp/parse-tv-response sample-tv-ok)
        csv (exp/format-csv bars)]
    (is (str/starts-with? csv "time,open,high,low,close,volume"))))

(deftest csv-data-row
  (let [bars (exp/parse-tv-response sample-tv-ok)
        csv (exp/format-csv bars)
        lines (str/split-lines csv)]
    (is (= 3 (count lines)))  ;; header + 2 data rows
    (is (str/includes? (second lines) "100.0"))
    (is (str/includes? (nth lines 2) "102.0"))))

(deftest csv-empty-bars
  (is (= "time,open,high,low,close,volume" (str/trim (exp/format-csv [])))))

;; ─── JSON formatting ───────────────────────────────────────────────

(deftest json-format-valid
  (let [bars (exp/parse-tv-response sample-tv-ok)
        json (exp/format-json bars)]
    (is (str/includes? json "\"time\""))
    (is (str/includes? json "\"open\""))
    (is (str/includes? json "\"high\""))
    (is (str/includes? json "\"low\""))
    (is (str/includes? json "\"close\""))
    (is (str/includes? json "\"volume\""))
    (is (str/includes? json "100.0"))))

(deftest json-format-bar-count
  (let [bars (exp/parse-tv-response sample-tv-ok)
        json (exp/format-json bars)]
    (is (str/includes? json "1700000100"))))

(deftest json-format-empty
  (is (= "[]" (exp/format-json []))))

;; ─── URL construction ──────────────────────────────────────────────

(deftest url-build-defaults
  (let [url (exp/build-url "AAPL")]
    (is (str/includes? url "symbol=AAPL"))
    (is (str/includes? url "resolution=D"))
    (is (re-find #"chart/america/" url))))

(deftest url-build-custom
  (let [url (exp/build-url "BTCUSD" :market "crypto" :interval "60")]
    (is (str/includes? url "symbol=BTCUSD"))
    (is (str/includes? url "resolution=60"))
    (is (re-find #"chart/crypto/" url))))

(deftest url-includes-exchange-btc
  (let [url (exp/build-url "BTCUSD" :exchange "BINANCE")]
    (is (str/includes? url "BINANCE"))))

(deftest url-has-from-to
  (let [url (exp/build-url "AAPL" :from 1000000 :to 2000000)]
    (is (re-find #"from=1000000" url))
    (is (re-find #"to=2000000" url))))

;; ─── CLI integration ───────────────────────────────────────────────

(deftest export-flag-in-usage
  (require 'stratus.core)
  (let [out (with-out-str ((resolve 'stratus.core/-main)))]
    (is (str/includes? out "export"))))

(deftest export-missing-symbol-shows-usage
  (require 'stratus.core)
  (let [out (with-out-str ((resolve 'stratus.core/-main) "export"))]
    (is (str/includes? out "Usage"))))

(deftest export-with-symbol-and-format
  (require 'stratus.core)
  (let [out (with-out-str ((resolve 'stratus.core/-main) "export" "AAPL" "--format" "csv" "--dry-run"))]
    (is (not (str/includes? out "ERROR")))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.exporter-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
