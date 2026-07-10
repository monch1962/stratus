# Tutorial: Building a Volatility-Adaptive RSI Indicator from Scratch

This walkthrough creates a complete TradingView indicator using Stratus, from
an empty file to a deployed indicator on the chart. You'll use inputs,
indicators, control flow, drawing, and plotting — all testable with Clojure.

---

## Step 1: Scaffold the indicator

```bash
cd ~/Projects/stratus

# Generate the boilerplate
./stratus new indicator "Volatility Adaptive RSI"
```

This creates `volatility-adaptive-rsi.stratus`:

```clojure
(indicator "Volatility Adaptive RSI" :overlay false :precision 2)
```

The indicator runs in a separate pane (`:overlay false`) and shows 2 decimal
places. You can also write this file by hand:

```bash
cat > volatility-adaptive-rsi.stratus << 'EOF'
(indicator "Volatility Adaptive RSI" :overlay false :precision 2)
EOF
```

---

## Step 2: Add user inputs

We need three inputs — RSI period, ATR period for volatility, and an overbought level:

```clojure
(indicator "Volatility Adaptive RSI" :overlay false :precision 2)
(input-int "RSI Period" :def 14 :min 2 :max 50)
(input-int "ATR Period"  :def 10 :min 1 :max 50)
(input-int "Overbought"  :def 70 :min 50 :max 95)
```

Each `input-*` maps to `input.int()` in Pine Script. Keyword args
(`:def`, `:min`, `:max`) translate directly.

**Test it** — create `test/volatility_test.clj`:

```clojure
(ns volatility-test
  (:require [clojure.test :refer :all]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

(deftest inputs-appear-in-output
  (let [src "(indicator \"T\" :precision 2)\n(input-int \"P\" :def 14 :min 2 :max 50)\n(input-int \"A\" :def 10)\n(input-int \"O\" :def 70)"
        out (gen/emit-file (reader/parse src))]
    (is (str/includes? out "input.int(14, \"P\", minval=2, maxval=50)"))
    (is (str/includes? out "input.int(10, \"A\")"))
    (is (str/includes? out "input.int(70, \"O\")"))))

;; Run: bb -m volatility-test
```

---

## Step 3: Define variables

Now compute RSI and ATR. The RSI period comes from the input, and so does
the ATR period:

```clojure
(indicator "Volatility Adaptive RSI" :overlay false :precision 2)
(input-int "RSI Period" :def 14 :min 2 :max 50)
(input-int "ATR Period"  :def 10 :min 1 :max 50)
(input-int "Overbought"  :def 70 :min 50 :max 95)

(def rsi-val (rsi rsi-period))
(def atr-val (atr atr-period))
```

`(rsi rsi-period)` becomes `ta.rsi(close, rsi-period)` in Pine Script.
Because RSI period is an input, the user can adjust it in TradingView.

---

## Step 4: Add adaptive thresholds

Instead of fixed 70/30 levels, scale them by volatility (ATR as a percentage
of price):

```clojure
(defvol (atr (/ close)))  ;; WRONG — atr / close doesn't work in LISP
```

In Pine-style thinking: `atr / close * 100`. In Stratus:

```clojure
(def vol-pct (avg 100 (* (/ atr-val close))))
```

Floored to 5% minimum to avoid degenerate bands:

```clojure
(def raw-vol (* (/ atr-val close) 100))
(def vol (max raw-vol 5.0))
(def ob-level (+ 50 (/ vol 2)))
(def os-level (- 50 (/ vol 2)))
```

When volatility is high, the overbought/oversold bands widen. When volatility
is low, they tighten.

**Test the threshold logic:**

```clojure
(deftest thresholds-widen-with-volatility
  ;; Simulate: close=100, atr=10 → vol=10%, ob=55, os=45
  (let [atr-val 10.0 close 100.0]
    (is (= 55 (+ 50 (/ (* (/ atr-val close) 100) 2))))))
```

---

## Step 5: Add bar coloring logic

Use control flow to colour bars based on RSI regime:

```clojure
(on-bar
  (if (> rsi-val ob-level)
    (barcolor :color (color red 60))
    (if (< rsi-val os-level)
      (barcolor :color (color green 60))
      (barcolor :color (color gray 40)))))
```

`(color red 60)` is `color.new(color.red, 60)` — transparent red.

---

## Step 6: Add alert conditions

Fire alerts when RSI enters or exits extreme zones:

```clojure
(alertcondition (> rsi-val ob-level) "Overbought")
(alertcondition (< rsi-val os-level) "Oversold")
```

---

## Step 7: Plot everything

```clojure
(plot rsi-val "RSI" :color blue :linewidth 2)
(plot ob-level "Overbought Band" :color red :linewidth 1)
(plot os-level "Oversold Band" :color green :linewidth 1)
(hline 50 "Midline" :color gray :linestyle dotted)
```

---

## Complete indicator

```clojure
(indicator "Volatility Adaptive RSI" :overlay false :precision 2)
(input-int "RSI Period" :def 14 :min 2 :max 50)
(input-int "ATR Period"  :def 10 :min 1 :max 50)
(input-int "Overbought"  :def 70 :min 50 :max 95)

(def rsi-val (rsi rsi-period))
(def atr-val (atr atr-period))

(def vol (max (* (/ atr-val close) 100) 5.0))
(def ob-level (+ 50 (/ vol 2)))
(def os-level (- 50 (/ vol 2)))

(on-bar
  (if (> rsi-val ob-level)
    (barcolor :color (color red 60))
    (if (< rsi-val os-level)
      (barcolor :color (color green 60))
      (barcolor :color (color gray 40)))))

(alertcondition (> rsi-val ob-level) "Overbought")
(alertcondition (< rsi-val os-level) "Oversold")

(plot rsi-val "RSI" :color blue :linewidth 2)
(plot ob-level "Overbought Band" :color red :linewidth 1)
(plot os-level "Oversold Band" :color green :linewidth 1)
(hline 50 "Midline" :color gray :linestyle dotted)
```

---

## Step 8: Compile and deploy

```bash
# Compile to Pine Script
./stratus compile volatility-adaptive-rsi.stratus

# Or compile and copy to clipboard in one step
./stratus compile volatility-adaptive-rsi.stratus --clip

# Open TradingView → Pine Editor → Ctrl+V → Ctrl+S
```

The generated output:

```pinescript
//@version=6

indicator("Volatility Adaptive RSI", precision=2)

rsi_period = input.int(14, "RSI Period", minval=2, maxval=50)
atr_period = input.int(10, "ATR Period", minval=1, maxval=50)
overbought = input.int(70, "Overbought", minval=50, maxval=95)

rsi_val = ta.rsi(close, rsi_period)
atr_val = ta.atr(atr_period)

vol = math.max((atr_val / close) * 100, 5.0)
ob_level = 50 + vol / 2
os_level = 50 - vol / 2

if rsi_val > ob_level
    barcolor(color.new(color.red, 60))
else if rsi_val < os_level
    barcolor(color.new(color.green, 60))
else
    barcolor(color.new(color.gray, 40))

alertcondition(rsi_val > ob_level, "Overbought")
alertcondition(rsi_val < os_level, "Oversold")

plot(rsi_val, "RSI", color=color.blue, linewidth=2)
plot(ob_level, "Overbought Band", color=color.red, linewidth=1)
plot(os_level, "Oversold Band", color=color.green, linewidth=1)
hline(50, "Midline", color=color.gray, linestyle=hline.style_dotted)
```

---

## Step 9: Iterate with `watch`

For rapid iteration, use watch mode — it recompiles every time you save:

```bash
./stratus watch volatility-adaptive-rsi.stratus --clip
# Watching volatility-adaptive-rsi.stratus...
# Change the .stratus file and save — it auto-copies to clipboard.
```

Now switch to TradingView and paste.

---

## Next Steps

| Learn more | Go to |
|---|---|
| CLI reference | `docs/CLI.md` |
| Full construct reference | `docs/REFERENCE.md` |
| Testing strategies | `docs/TESTING.md` |
| Backtesting with simulator | `./stratus simulate` |
| Converting existing Pine Script | `./stratus import` |
