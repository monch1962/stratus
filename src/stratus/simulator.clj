(ns stratus.simulator
  "Lightweight strategy simulator. Walks through bar data, evaluates
   DSL conditions, tracks positions, and returns P&L results."
  (:require [clojure.string :as str]))

;; ─── Bar data ──────────────────────────────────────────────────────

(defn make-bar
  "Create a bar map: {:open o, :high h, :low l, :close c, :volume v}"
  [o h l c v]
  {:open o, :high h, :low l, :close c, :volume v})

;; ─── Technical indicator calculations ──────────────────────────────

(defn sma
  "Simple moving average of values over period."
  [values period]
  (if (< (count values) period) nil
      (/ (reduce + (take-last period values)) period)))

(defn ema
  "Exponential moving average."
  [values period]
  (let [alpha (/ 2.0 (inc period))
        seed  (sma values period)]
    (if (nil? seed) nil
        (loop [i (count values), result seed]
          (if (>= i (count values)) result
              (recur (inc i) (+ (* alpha (nth values i)) (* (- 1 alpha) result))))))))

(defn rsi
  "Relative Strength Index over period."
  [values period]
  (when (>= (count values) (inc period))
    (let [changes (map (fn [a b] (- a b)) (drop 1 values) values)
          gains   (map (fn [c] (if (pos? c) c 0.0)) changes)
          losses  (map (fn [c] (if (neg? c) (- c) 0.0)) changes)
          avg-gain (sma gains period)
          avg-loss (sma losses period)]
      (if (and avg-gain avg-loss (pos? (+ avg-gain avg-loss)))
        (- 100.0 (/ 100.0 (inc (/ avg-gain (max 0.001 avg-loss)))))
        nil))))

(defn- cross [a b]
  "True if series a just crossed series b."
  (and (>= (count a) 2) (>= (count b) 2)
       (let [a-prev (nth a (- (count a) 2))
             b-prev (nth b (- (count b) 2))
             a-curr (last a)
             b-curr (last b)]
         (and (<= a-prev b-prev) (>= a-curr b-curr)))))

(defn- crosses-above [a b]
  (and (cross a b) (> (last a) (last b))))

(defn- crosses-below [a b]
  (and (cross a b) (< (last a) (last b))))

(defn- highest [values n]
  (when (>= (count values) n)
    (apply max (take-last n values))))

(defn- lowest [values n]
  (when (>= (count values) n)
    (apply min (take-last n values))))

;; ─── State machine ─────────────────────────────────────────────────

(defn initial-state
  "Create initial simulation state."
  []
  {:position nil, :trades [], :bars {}, :vars {}, :bar-count 0})

(defn update-bars!
  "Append bar data to state history for all series.
   Each bar's keys (:open, :high, :low, :close, :volume) get a vector
   appended to in the state's :bars map."
  [state bar]
  (let [bars (:bars state)]
    (-> (reduce-kv (fn [s k v]
                     (update-in s [:bars k] (fnil conj []) v))
                   state bar)
        (assoc :bar-count (inc (:bar-count state)))
        (assoc :current-bar bar))))

(defn series
  "Get the value series for a symbol from state."
  [state sym]
  (case sym
    close (:close (:bars state))
    high   (:high (:bars state))
    low    (:low (:bars state))
    open   (:open (:bars state))
    volume (:volume (:bars state))
    nil))

(defn current-value
  "Get the current (most recent) value for a symbol."
  [state sym]
  (let [s (series state sym)]
    (when (seq s) (last s))))

;; ─── Expression evaluator ──────────────────────────────────────────

(declare eval-expr get-series)

(defn eval-fn
  "Evaluate a DSL function call against current state."
  [state form]
  (let [fn-name (first form)
        args (rest form)
        eval-args (map #(eval-expr state %) args)]
    (case fn-name
      sma    (let [[src period] (if (number? (first eval-args))
                                  [(current-value state nil) (first eval-args)]
                                  (if (number? (second eval-args))
                                    eval-args
                                    [(current-value state (first eval-args)) (second eval-args)]))]
               (sma (or (:close (:bars state)) []) (int (or period 14))))
      ema    (let [[src period] (if (number? (first eval-args))
                                  [nil (first eval-args)]
                                  eval-args)]
               (ema (:close (:bars state)) (int (or period 14))))
      rsi    (let [p (first eval-args)] (rsi (:close (:bars state)) (int (or p 14))))
      >      (if (>= (count eval-args) 2) (> (first eval-args) (second eval-args)) false)
      <      (if (>= (count eval-args) 2) (< (first eval-args) (second eval-args)) false)
      =      (if (>= (count eval-args) 2) (= (first eval-args) (second eval-args)) false)
      >=     (if (>= (count eval-args) 2) (>= (first eval-args) (second eval-args)) false)
      <=     (if (>= (count eval-args) 2) (<= (first eval-args) (second eval-args)) false)
      and    (every? true? eval-args)
      or     (some true? eval-args)
      not    (not (first eval-args))
      +      (apply + eval-args)
      -      (apply - eval-args)
      *      (apply * eval-args)
      /      (apply / eval-args)
      %      (apply mod eval-args)
      na     (nil? (first eval-args))
      nz     (or (first eval-args) (second eval-args) 0.0)
      iff    (if (first eval-args) (second eval-args) (nth eval-args 2 nil))
      rising (let [n (int (or (second eval-args) 1))]
               (when (>= (count(:close (:bars state))) n)
                 (< (nth (:close (:bars state)) (- (count (:close (:bars state))) 2 n))
                    (last (:close (:bars state))))))
      falling (let [n (int (or (second eval-args) 1))]
                (when (>= (count(:close (:bars state))) n)
                  (> (nth (:close (:bars state)) (- (count (:close (:bars state))) 2 n))
                     (last (:close (:bars state))))))
      crosses-above (let [a (get-series state (nth args 0))
                          b (get-series state (nth args 1))]
                      (crosses-above a b))
      crosses-below (let [a (get-series state (nth args 0))
                          b (get-series state (nth args 1))]
                      (crosses-below a b))
      highest (let [src (current-value state (first (rest form)))
                    n (int (or (second eval-args) 20))]
                (highest (:close (:bars state)) n))
      lowest  (let [src (current-value state (first (rest form)))
                    n (int (or (second eval-args) 20))]
                (lowest (:close (:bars state)) n))
      nil)))

(defn eval-expr
  "Evaluate a single expression against current state."
  [state expr]
  (cond
    (nil? expr) nil
    (number? expr) expr
    (list? expr) (eval-fn state expr)
    (symbol? expr) (or (let [v (get (:vars state) expr)]
                          (if (vector? v) (last v) v))
                       (current-value state expr)
                       (case expr
                         close (current-value state :close)
                         high (current-value state :high)
                         low (current-value state :low)
                         open (current-value state :open)
                         volume (current-value state :volume)
                         nil))
    :else expr))

(defn eval-def
  "Evaluate a (def name expr) form, appending the result to a series history."
  [state form]
  (let [[_ name expr] form
        value (eval-expr state expr)]
    (update-in state [:vars name] (fnil conj []) value)))

(defn get-series
  "Get the full computed series for a variable, or the raw bar series."
  [state sym]
  (if (contains? (:vars state) sym)
    (get (:vars state) sym)
    (case sym
      close (:close (:bars state))
      high   (:high (:bars state))
      low    (:low (:bars state))
      open   (:open (:bars state))
      volume (:volume (:bars state))
      nil)))

(defn eval-when
  "Evaluate a (when cond action) form."
  [state form on-action]
  (let [[_ cond & actions] form]
    (when (eval-expr state cond)
      (doseq [action actions]
        (when-let [f (:on-bar on-action)]
          (f state action))))))

;; ─── Main simulation ───────────────────────────────────────────────

(defn simulate
  "Run a strategy DSL form against bar data.
   Returns {:trades [...], :net-profit n, :total-trades n}."
  [bars strategy-form & {:keys [on-bar on-result]
                         :or {on-bar (fn [state form]
                                      (case (first form)
                                        long  (println "LONG at" (-> state :current-bar :close))
                                        short (println "SHORT at" (-> state :current-bar :close))
                                        nil))
                              on-result (fn [state] state)}}]
  (let [forms (if (= :do (first strategy-form)) (rest strategy-form) [strategy-form])
        init  (initial-state)]
    (loop [state init, remaining bars]
      (if (empty? remaining)
        (let [final (on-result state)]
          {:trades (:trades final)
           :net-profit (reduce + (map (fn [t] (- (:close t) (:open t))) (:trades final)))
           :total-trades (count (:trades final))})
        (let [bar (first remaining)
              state' (-> state (update-bars! bar))
              ;; Pre-evaluate def forms to calculate indicator values
              defs (filter #(= :def (first %)) forms)
              state'' (reduce eval-def state' defs)
              ;; Evaluate on-bar conditionals
              on-bar-forms (filter #(= :when (first %)) forms)]
          (doseq [f on-bar-forms]
            (eval-when state'' f on-bar))
          (recur state'' (rest remaining)))))))
