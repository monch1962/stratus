(ns stratus.exporter
  "Fetch OHLCV data from TradingView's internal chart history API
   and export to CSV or JSON. Zero external dependencies — uses
   babashka.curl (built-in) and a custom lightweight JSON parser."
  (:require [babashka.curl :as curl]
            [clojure.string :as str]))

;; ─── Lightweight JSON parser for TV response format ────────────────
;; TV history JSON is simple: top-level keys map to string/number/array.
;; We parse just enough to extract the arrays we need.

(defn- parse-number [s]
  (if (str/includes? s ".")
    (Double/parseDouble s)
    (Long/parseLong s)))

(defn- parse-number-seq [s]
  "Parse a JSON number array like [100.0,101.0] into a seq of numbers."
  (let [trimmed (str/trim s)]
    (when (and (str/starts-with? trimmed "[") (str/ends-with? trimmed "]"))
      (let [inner (subs trimmed 1 (dec (count trimmed)))
            parts (str/split inner #",")]
        (mapv #(parse-number (str/trim %))
              (remove empty? parts))))))

(defn- extract-array
  "Extract a JSON array value for a given key from a JSON string.
   e.g. (extract-array \"{\\\"s\\\":\\\"ok\\\",\\\"t\\\":[1,2]}\" \"t\") => \"[1,2]\""
  [json-str key]
  (let [pat (re-pattern (str "\"" key "\"\\s*:\\s*\\[([^]]+)\\]"))]
    (when-let [m (re-find pat json-str)]
      (second m))))

(defn- extract-string
  "Extract a JSON string value for a given key."
  [json-str key]
  (let [pat (re-pattern (str "\"" key "\"\\s*:\\s*\"([^\"]+)\""))]
    (when-let [m (re-find pat json-str)]
      (second m))))

(defn parse-tv-response
  "Parse a TradingView history API JSON response into a seq of bar maps.
   Returns nil if the response indicates no data.
   Each bar map: {:time long, :open double, :high double,
                   :low double, :close double, :volume long}"
  [json-str]
  (let [status (extract-string json-str "s")]
    (when (= status "ok")
      (let [t-str  (extract-array json-str "t")
            o-str  (extract-array json-str "o")
            h-str  (extract-array json-str "h")
            l-str  (extract-array json-str "l")
            c-str  (extract-array json-str "c")
            v-str  (extract-array json-str "v")
            times  (parse-number-seq (str "[" t-str "]"))
            opens  (parse-number-seq (str "[" o-str "]"))
            highs  (parse-number-seq (str "[" h-str "]"))
            lows   (parse-number-seq (str "[" l-str "]"))
            closes (parse-number-seq (str "[" c-str "]"))
            vols   (parse-number-seq (str "[" v-str "]"))]
        (mapv (fn [t o h l c v]
                {:time t, :open o, :high h, :low l, :close c, :volume (long v)})
              times opens highs lows closes vols)))))

(defn build-url
  "Construct the TradingView chart history API URL.
   Options:
     :market   - market segment (default: america, also: crypto, forex, india)
     :interval - bar resolution (default: D, also: 1, 5, 15, 60, W, M)
     :exchange - exchange prefix (e.g. BINANCE, NASDAQ)
     :from     - start unix timestamp (default: 90 days ago)
     :to       - end unix timestamp (default: now)"
  [symbol & {:keys [market interval exchange from to]
             :or {market "america", interval "D"}}]
  (let [full-sym (if exchange (str exchange ":" symbol) symbol)
        now-ts   (long (/ (System/currentTimeMillis) 1000))
        from-ts  (or from (- now-ts (* 90 86400)))
        to-ts    (or to now-ts)]
    (str "https://chart-data.tradingview.com/chart/" market
         "/history/?symbol=" full-sym
         "&resolution=" interval
         "&from=" from-ts
         "&to=" to-ts)))

(defn fetch-ohlcv
  "Fetch OHLCV bars from TradingView's history API.
   Returns a seq of bar maps, or nil on error/no-data."
  [url]
  (try
    (let [resp (curl/get url {:headers {"User-Agent" "Mozilla/5.0"}})]
      (when (= 200 (:status resp))
        (parse-tv-response (:body resp))))
    (catch Exception e
      (println "✕ Network error:" (.getMessage e))
      nil)))

;; ─── Formatting ────────────────────────────────────────────────────

(defn format-csv
  "Format a seq of bar maps as CSV string with header row."
  [bars]
  (str "time,open,high,low,close,volume\n"
       (str/join "\n"
         (map (fn [b]
                (str (:time b) ","
                     (:open b) ","
                     (:high b) ","
                     (:low b) ","
                     (:close b) ","
                     (:volume b)))
              bars))))

(defn format-json
  "Format a seq of bar maps as JSON string."
  [bars]
  (if (empty? bars)
    "[]"
    (str "[\n"
         (str/join ",\n"
           (map (fn [b]
                  (str "  {\"time\":" (:time b)
                       ",\"open\":" (:open b)
                       ",\"high\":" (:high b)
                       ",\"low\":" (:low b)
                       ",\"close\":" (:close b)
                       ",\"volume\":" (:volume b) "}"))
                bars))
         "\n]")))
