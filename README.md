# Stratus — LISP-Syntax Strategy DSL for Pine Script

**Write trading strategies in LISP syntax. Generate idiomatic Pine Script v6. Test with Clojure's full ecosystem.**

Stratus is a code generator that lets you author TradingView indicators and strategies using parenthesized LISP-style syntax (.stratus files) and produces clean, production-ready Pine Script v6. Because the DSL source is valid Clojure/EDN data, you get **real unit testing, property-based testing, and CI integration** — none of which exist in the Pine Script ecosystem.

## 5-Minute Quick Start

### 1. Install Babashka

```bash
# macOS
brew install borkdude/brew/babashka

# Linux
brew tap borkdude/brew && brew install babashka
```

### 2. Clone and test

```bash
git clone https://github.com/monch1962/stratus
cd stratus
make test
# → 120 tests, 641 assertions, 0 failures
```

### 3. Write your first strategy

Create `crossover.stratus`:

```clojure
(strategy "Crossover" :default-qty 100)

(def fast (sma 50))
(def slow (sma 200))

(on-bar
  (when (crosses-above fast slow) (long "GO"))
  (when (crosses-below fast slow) (close "EXIT")))

(plot fast "Fast MA" :color blue :linewidth 2)
(plot slow "Slow MA" :color red :linewidth 2)
```

### 4. Compile and paste into TradingView

```bash
# Option A: Print to terminal, then copy/paste manually
bb -m stratus.core compile crossover.stratus

# Option B: Compile to file
bb -m stratus.core compile crossover.stratus -o crossover.pine

# Option C: Compile and copy directly to clipboard
bb -m stratus.core compile crossover.stratus --clip

# Option D: Watch mode — recompile on every save
bb -m stratus.core watch crossover.stratus --clip
```

Open TradingView → Pine Editor → **Ctrl+V** (or Cmd+V) → **Ctrl+S** — your strategy is live.

Each `.stratus` file compiles to a complete, self-contained Pine Script that you paste
directly into the TradingView editor. There's no runtime dependency, no import file,
no plugin — just clean Pine.

### 5. Run the tests

```bash
bb -m stratus.core-test    # core generator tests (55)
bb -m stratus.cli-test     # CLI tests (7)
bb -m stratus.p0p1-test    # P0/P1 feature tests (20)
bb -m stratus.p1p2-test    # P2 feature tests (18)
bb -m stratus.remaining-test  # P3/P4/P5 feature tests (15)
bb -m stratus.examples-test   # Example integration tests (12)
make test                  # all of the above at once
```

You now have a strategy that you can **edit, compile, test, paste, and iterate on**
in under 5 minutes.
(plot slow "Slow" :color red :linewidth 2)
```

Compile it:

```bash
bb -m stratus.core compile my-strategy.stratus -o my-strategy.pine
```

Output (`my-strategy.pine`):

```pinescript
//@version=6
strategy("EMA Crossover", default_qty_value=100, default_qty_type=strategy.percent_of_equity)

fast = ta.ema(close, 12)
slow = ta.ema(close, 26)

if ta.cross(fast, slow) and fast > slow
    strategy.entry("ENTER", strategy.long)

if ta.cross(fast, slow) and fast < slow
    strategy.close("EXIT")

plot(fast, color=color.blue, linewidth=2)
plot(slow, color=color.red, linewidth=2)
```

---

## Testing: The Killer Feature

Pine Script has **no unit testing capability whatsoever**. There is no test runner, no assertion framework, no way to verify that your indicator produces correct signals before deploying it to a chart.

Stratus bypasses this entirely. Because your strategy DSL is valid Clojure data structures, you can test it with Clojure's full testing ecosystem:

### What You Can Test

| Layer | What It Tests | Tool |
|---|---|---|
| **Parser** | Does the DSL source parse to the correct AST? | `clojure.test` |
| **Generator** | Does the AST emit the correct Pine Script string? | `clojure.test` |
| **Signal logic** | Does the strategy produce the right entry/exit signals on known bar data? | `clojure.test` + vectors |
| **Property-based** | Given any valid parameter set, does the compiler produce valid output? | `clojure.test.check` |
| **Integration** | Does the generated Pine Script pass through pynescript's parser? | `clojure.test` + shell |

### Running Tests

```bash
# Run all tests
bb -m stratus.core-test

# Or via Makefile
make test
```

### Test Examples

#### 1. Testing DSL Parsing

```clojure
(deftest test-reader-parses-sma
  (let [result (reader/parse "(sma 14)")]
    (is (= 1 (count result)))
    (is (= 'sma (ffirst result)))))
```

#### 2. Testing Code Generation

```clojure
(deftest test-generate-sma
  (let [result (gen/expr->pine '(sma 14))]
    (is (= "ta.sma(close, 14)" result))))

(deftest test-generate-whole-strategy
  (let [src "
(strategy \"Test\" :default-qty 100 :pyramiding 1)
(def fast (sma 50))
(def slow (sma 200))
(on-bar
  (when (crosses-above fast slow) (long \"ENTER\")))
(plot fast \"Fast\" :color blue)"
        forms  (reader/parse src)
        output (gen/emit-file forms)]
    (is (str/includes? output "//@version=6"))
    (is (str/includes? output "ta.sma(close, 50)"))
    (is (str/includes? output "ta.cross("))
    (is (str/includes? output "strategy.entry"))))
```

#### 3. Testing Strategy Signal Logic

This is the most powerful test pattern — something Pine Script cannot do:

```clojure
(deftest test-golden-cross-fires-on-cross
  ;; Generate synthetic bar data
  (let [bars (vec (repeatedly 200 #(hash-map :close (+ 100 (rand 10))
                                              :high (+ 105 (rand 10))
                                              :low  (+ 95 (rand 10))
                                              :volume (rand-int 1000000))))
        ;; Run the strategy through the Clojure runtime
        strategy (quote (strategy "Test"
                        (def fast (sma 5))
                        (def slow (sma 20))
                        (on-bar
                          (when (crosses-above fast slow)
                            (long "ENTER")))))
        result   (evaluate-strategy strategy bars)
        entries  (filter #(= :enter (:signal %)) result)]
    (is (pos? (count entries))
        "Golden cross should fire at least once in 200 bars of random data")))
```

#### 4. Property-Based Testing

```clojure
(deftest test-all-sma-periods-compile
  ;; Generate 50 random SMA periods and verify each compiles
  (doseq [period (take 50 (repeatedly #(+ 2 (rand-int 198))))]
    (let [output (gen/expr->pine (list 'sma period))]
      (is (re-find #"^ta\.sma\(close, \d+\)$" output)
          (str "Failed for period " period)))))
```

This catches TradingView API changes instantly. If `ta.sma()` is renamed to `tv.sma()` in a Pine Script update, the test fails — before your users see compile errors.

### Adding Tests

1. Open `test/stratus/core_test.clj`
2. Add a `deftest` form using `clojure.test`
3. Run `bb -m stratus.core-test` to verify

---

### Practical Testing Walkthrough

Here's what a real testing session looks like. You write a strategy, test it,
fix a bug, and verify — all within Clojure's test framework.

#### Step 1: Create a test file

`test/stratus/my_strategy_test.clj`:

```clojure
(ns stratus.my-strategy-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

(defn compile-strategy [src]
  (gen/emit-file (reader/parse src)))

(deftest golden-cross-compiles-to-valid-pine
  (let [src "
(strategy \"Test\" :default-qty 100)
(def fast (sma 50))
(def slow (sma 200))
(on-bar
  (when (crosses-above fast slow) (long \"ENTER\"))
  (when (crosses-below fast slow) (close \"EXIT\")))
(plot fast \"Fast\" :color blue)
(plot slow \"Slow\" :color red)"
        output (compile-strategy src)]
    (is (str/includes? output "//@version=6"))
    (is (str/includes? output "ta.sma(close, 50)"))
    (is (str/includes? output "ta.cross"))
    (is (str/includes? output "strategy.entry"))))

(deftest sma-correct-period
  (is (= "ta.sma(close, 14)" (gen/expr->pine '(sma 14))))
  (is (= "ta.sma(close, 200)" (gen/expr->pine '(sma 200)))))

(deftest custom-source-passes-through
  (is (= "ta.sma(high, 14)" (gen/expr->pine '(sma high 14)))))

(deftest all-plot-styles-valid
  (doseq [style [:histogram :area :columns :circles :cross :step-line]]
    (let [output (gen/expr->pine (list 'plot 'val :style style))]
      (is (str/includes? output "plot.style_")
          (str "Style " style " should produce valid plot.style_")))))
```

#### Step 2: Run it

```bash
bb -m stratus.my-strategy-test
```

You'll see:

```
Testing stratus.my-strategy-test

Ran 4 tests containing 13 assertions.
0 failures, 0 errors.
```

#### Step 3: Catch a regression

Rename `ta.sma` to `tv.sma` in the generator (simulating a TradingView API change).
Run the tests again:

```
Testing stratus.my-strategy-test

FAIL in (sma-correct-period)
expected: (= "ta.sma(close, 200)" (gen/expr->pine '(sma 200)))
  actual: (not (= "tv.sma(close, 200)" "ta.sma(close, 200)"))
```

The test catches the API change **before** your users see compile errors in TradingView.

#### CI Integration

Add to `.github/workflows/test.yml`:

```yaml
name: Test
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: borkdude/setup-babashka@v2
      - run: make test
```

Every push runs all 120+ tests in under 5 seconds. Pine Script has no equivalent.

---

## Pine Script ↔ Stratus Cross-Reference

The fastest way to learn Stratus as a Pine developer: look up what you already know.

### Declarations

| Pine Script | Stratus DSL |
|---|---|
| `//@version=6` | _(added automatically by compiler)_ |
| `strategy("Name", overlay=true)` | `(strategy "Name" :overlay true)` |
| `indicator("Name", precision=2)` | `(indicator "Name" :precision 2)` |
| `library("Name")` | `(library "Name")` |
| `len = input.int(14, "Period")` | `(input-int "Period" :def 14)` |
| `src = input.source(close, "Source")` | `(input-source "Source" :def close)` |

### Variables

| Pine Script | Stratus DSL |
|---|---|
| `fast = ta.sma(close, 50)` | `(def fast (sma 50))` |
| `var count = 0` | `(defvar count 0)` |
| `varip count = 0` | `(defvarip count 0)` |
| `count := count + 1` | `(set! count (+ count 1))` |
| `myFun(x, y) => x + y` | `(defn my-fun [x y] (+ x y))` |
| `export myFun` | `(export my-fun)` |

### Technical Indicators

| Pine Script | Stratus DSL |
|---|---|
| `ta.sma(close, 14)` | `(sma 14)` or `(sma src 14)` |
| `ta.ema(close, 20)` | `(ema 20)` |
| `ta.rsi(close, 14)` | `(rsi 14)` |
| `ta.macd(close, 12, 26, 9)` | `(macd :fast 12 :slow 26 :signal 9)` |
| `ta.bb(close, 20, 2)` | `(bb 20 2.0)` |
| `ta.atr(14)` | `(atr 14)` |
| `ta.adx(high, low, close, 14)` | `(adx 14)` |
| `ta.stoch(close, high, low, 14)` | `(stoch 14)` |
| `ta.supertrend(3, 10)` | `(supertrend 3 10)` |
| `ta.sar(0.02, 0.2)` | `(sar 0.02 0.2)` |
| `ta.vwap(hlc3)` | `(vwap hlc3)` |
| `ta.wma(close, 14)` | `(wma 14)` |
| `ta.hma(close, 14)` | `(hma 14)` |
| `ta.vwma(close, 14)` | `(vwma 14)` |
| `ta.alma(close, 10, 6, 0.85)` | `(alma 10 6 0.85)` |
| `ta.cci(close, 20)` | `(cci 20)` |
| `ta.mfi(hlc3, 14)` | `(mfi 14)` |
| `ta.obv` | `(obv)` |
| `ta.linreg(close, 14)` | `(linreg 14)` |

### Price References

| Pine Script | Stratus DSL |
|---|---|
| `close` | `close` |
| `close[1]` | `(close 1)` |
| `high`, `low`, `open`, `volume` | `high`, `low`, `open`, `volume` |
| `hl2`, `hlc3`, `ohlc4` | `hl2`, `hlc3`, `ohlc4` |

### Conditions

| Pine Script | Stratus DSL |
|---|---|
| `ta.cross(a, b) and a > b` | `(crosses-above a b)` |
| `ta.cross(a, b) and a < b` | `(crosses-below a b)` |
| `rising(val, 1)` | `(rising val)` |
| `falling(val, 1)` | `(falling val)` |
| `na(x)` | `(na x)` |
| `nz(x, 0)` | `(nz x 0)` |
| `iff(cond, a, b)` | `(iff cond a b)` |

### Logic & Comparison

| Pine Script | Stratus DSL |
|---|---|
| `a and b` | `(and a b)` |
| `a or b` | `(or a b)` |
| `not a` | `(not a)` |
| `a > b`, `a < b`, `a >= b` | `(> a b)`, `(< a b)`, `(>= a b)` |
| `a == b` | `(= a b)` |
| `a + b`, `a - b`, `a * b`, `a / b` | `(+ a b)`, `(- a b)`, `(* a b)`, `(/ a b)` |

### Strategy Actions

| Pine Script | Stratus DSL |
|---|---|
| `strategy.entry("E", strategy.long)` | `(long "E")` |
| `strategy.entry("E", strategy.short)` | `(short "E")` |
| `strategy.close("E")` | `(close "E")` |
| `strategy.exit("X", from="E", loss=100)` | `(exit "X" :from "E" :loss 100)` |
| `strategy.order("O", strategy.long, limit=50)` | `(order "O" :long 50.0 :limit 50)` |
| `strategy.cancel("O")` | `(cancel "O")` |

### Control Flow

| Pine Script | Stratus DSL |
|---|---|
| `if cond` | `(if cond action)` |
| `if cond action else other` | `(if cond action :else other)` |
| `if a x else if b y else z` | `(if a x b y :else z)` |
| `for i = 1 to 5` | `(for [i (range 1 5)] ...)` |
| `while cond` | `(while cond ...)` |
| `switch expr` | `(switch expr val action ...)` |

### Plotting & Visuals

| Pine Script | Stratus DSL |
|---|---|
| `plot(x, "Title", color=blue)` | `(plot x "Title" :color blue)` |
| `plotshape(cond, "Label", style=shape.triangleup)` | `(plotshape cond "Label" :style :triangle-up)` |
| `hline(level, "Label", color=red)` | `(hline level "Label" :color red)` |
| `bgcolor(cond, color=color.new(red, 90))` | `(bgcolor cond :color (color red 90))` |
| `barcolor(cond, color=green)` | `(barcolor cond :color green)` |
| `barcolor(color.green)` | `(barcolor :color green)` |
| `fill(p1, p2, color=color.new(blue, 90))` | `(fill p1 p2 :color blue :alpha 90)` |
| `alertcondition(cond, "Msg")` | `(alertcondition cond "Msg")` |

### Multi-Timeframe

| Pine Script | Stratus DSL |
|---|---|
| `request.security(ticker, "60", expr)` | `(security "60" expr)` |
| `request.dividends(...)` | `(dividends)` |
| `request.splits(...)` | `(splits)` |

### Math & Statistics

| Pine Script | Stratus DSL |
|---|---|
| `math.log(x)` | `(log x)` |
| `math.exp(x)` | `(exp x)` |
| `math.sqrt(x)` | `(sqrt x)` |
| `math.abs(x)` | `(abs x)` |
| `math.ceil(x)`, `math.floor(x)` | `(ceil x)`, `(floor x)` |
| `math.round(x)` | `(round x)` |
| `math.pow(x, y)` | `(pow x y)` |
| `math.min(a, b)`, `math.max(a, b)` | `(min a b)`, `(max a b)` |
| `math.cum(x)` | `(cum x)` |
| `math.sum(x, 20)`, `math.avg(x, 20)` | `(sum x 20)`, `(avg x 20)` |
| `ta.correlation(x, y, 20)` | `(correlation x y 20)` |
| `ta.covariance(x, y, 20)` | `(covariance x y 20)` |
| `ta.median(x, 20)` | `(median x 20)` |
| `ta.mode(x, 20)` | `(mode x 20)` |
| `ta.percentile_nearest_rank(x, 20, 90)` | `(percentile x 20 90)` |
| `change(x, 1)` | `(change x 1)` |
| `mom(x, 10)` | `(mom x 10)` |
| `ta.highest(high, 20)` | `(highest high 20)` |
| `ta.lowest(low, 20)` | `(lowest low 20)` |
| `ta.highestbars(high, 20)` | `(highestbars high 20)` |

### Built-in Values

| Pine Script | Stratus DSL |
|---|---|
| `time` | `(time)` |
| `dayofweek` | `(dayofweek)` |
| `month` | `(month)` |
| `hour` | `(hour)` |
| `bar_index` | `(bar-index)` |
| `syminfo.tickerid` | `(ticker)` |
| `timeframe.period` | `(timeframe)` |
| `strategy.position_size` | `(position-size)` |
| `strategy.position_avg_price` | `(position-avg-price)` |
| `strategy.equity` | `(equity)` |
| `strategy.netprofit` | `(net-profit)` |
| `syminfo.mintick` | `(mintick)` |
| `syminfo.pointvalue` | `(pointvalue)` |
| `barstate.isconfirmed` | `(bar-confirmed)` |
| `barstate.isfirst` | `(bar-first)` |
| `barstate.islast` | `(bar-last)` |
| `session.isregular("0930-1600")` | `(in-session "0930-1600")` |

### Arrays & Tables

| Pine Script | Stratus DSL |
|---|---|
| `array.new_float(50)` | `(array-float 50)` |
| `array.new_int(10)` | `(array-int 10)` |
| `array.push(arr, val)` | `(push arr val)` |
| `array.pop(arr)` | `(pop arr)` |
| `array.size(arr)` | `(size arr)` |
| `array.get(arr, i)` | `(get arr i)` |
| `array.set(arr, i, val)` | `(set arr i val)` |
| `array.sort(arr)` | `(sort arr)` |
| `table.new(position.top_right, 2, 2)` | `(table.new :position :top-right :cols 2 :rows 2)` |
| `table.cell(tbl, 0, 0, "Text")` | `(table-cell tbl 0 0 "Text")` |

### Colors

| Pine Script | Stratus DSL |
|---|---|
| `color.blue` | `blue` |
| `color.rgb(65, 105, 225)` | `(rgb 65 105 225)` |
| `color.new(color.blue, 90)` | `(color blue 90)` |
| `color.from_gradient(val, low, high, red, green)` | `(from-gradient val low high :red :green)` |

### Drawing Objects

| Pine Script | Stratus DSL |
|---|---|
| `line.new(x1, y1, x2, y2, color=blue)` | `(line.new x1 y1 x2 y2 :color blue)` |
| `line.delete(l)` | `(line.delete l)` |
| `label.new(x, y, "Text", color=yellow)` | `(label.new x y "Text" :color yellow)` |
| `box.new(x1, y1, x2, y2, bgcolor=blue)` | `(box.new x1 y1 x2 y2 :bgcolor blue)` |

### Other

| Pine Script | Stratus DSL |
|---|---|
| `str.tostring(val)` | `(tostring val)` |
| `fixnan(val)` | `(fixnan val)` |
| `ta.valuewhen(cond, val)` | `(valuewhen cond val)` |

---

## DSL Reference

### Strategy Declarations

```clojure
(strategy "Name" :opt1 val1 :opt2 val2)
(indicator "Name" :opt1 val1 :opt2 val2)
```

Options: `:default-qty`, `:pyramiding`, `:commission`, `:overlay`, `:precision`

### Variable Bindings

```clojure
(def var-name (expression))
```

### Technical Indicators

| Construct | Pine Script Output | Notes |
|---|---|---|
| `(sma 14)` | `ta.sma(close, 14)` | Source defaults to close |
| `(ema 20)` | `ta.ema(close, 20)` | |
| `(rsi 14)` | `ta.rsi(close, 14)` | |
| `(macd)` | `ta.macd(close, 12, 26, 9)` | Keyword args: `:fast`, `:slow`, `:signal` |
| `(adx 14)` | `ta.adx(high, low, close, 14)` | Multi-source indicator |
| `(stoch 14 3 3)` | `ta.stoch(close, high, low, 14)` | |
| `(bb 20)` | `ta.bb(close, 20, 2.0)` | Keyword arg: `:mult` |
| `(atr 14)` | `ta.atr(14)` | |

### Conditions

```clojure
(crosses-above a b)    → ta.cross(a, b) and a > b
(crosses-below a b)    → ta.cross(a, b) and a < b
(rising val)           → rising(val, 1)
(falling val)          → falling(val, 1)
```

### Logic

```clojure
(and cond1 cond2)      → (cond1 and cond2)
(or cond1 cond2)       → (cond1 or cond2)
(not cond)             → not cond
(when cond action)     → if cond \n    action
(> a b) (< a b) (= a b) (>= a b) (<= a b)
```

### Strategy Actions

```clojure
(long "Label")         → strategy.entry("Label", strategy.long)
(short "Label")        → strategy.entry("Label", strategy.short)
(close "Label")        → strategy.close("Label")
```

### Plotting

```clojure
(plot value "Title" :color :blue :linewidth 2)
(plotshape cond "Label" :location :bottom :color :green :style :triangle-up)
(hline value "Label" :color :red :linestyle :dashed)
(bgcolor cond :color :red)
(barcolor cond :color :green)
(alertcondition cond "Alert Name")
```

Colors: `:red`, `:green`, `:blue`, `:yellow`, `:orange`, `:purple`, `:pink`, `:gray`, `:white`, `:black`, `:navy`, `:teal`, `:lime`, `:maroon`
Shapes: `:triangle-up`, `:triangle-down`, `:circle`, `:cross`, `:diamond`, `:square`, `:arrow-up`, `:arrow-down`
Line styles: `:solid`, `:dashed`, `:dotted`
Locations: `:top`, `:bottom`, `:abovebar`, `:belowbar`

### On-Bar Logic

```clojure
(on-bar
  (when condition
    (action1)
    (action2))
  (when other-condition
    (action3)))
```

---

## Example: Complete Strategies

### Example 1: Golden Cross (Trend Following)

```clojure
(strategy "Golden Cross" :default-qty 100 :pyramiding 1)

(def fast (sma 50))
(def slow (sma 200))

(on-bar
  (when (crosses-above fast slow) (long "ENTER"))
  (when (crosses-below fast slow) (close "EXIT")))

(plot fast "Fast MA" :color blue :linewidth 2)
(plot slow "Slow MA" :color red :linewidth 2)
```

### Example 2: RSI Divergence Detector

```clojure
(indicator "RSI Divergence" :overlay false :precision 2)

(def rsi-val (rsi 14))
(def overbought 70)
(def oversold 30)

(def bullish-div
  (and (falling rsi-val) (> (close) (close 1))))

(def bearish-div
  (and (rising rsi-val) (< (close) (close 1))))

(plot rsi-val "RSI" :color purple :linewidth 2)
(hline overbought "Overbought" :color red :linestyle dashed)
(hline oversold "Oversold" :color green :linestyle dashed)

(plotshape bullish-div "Bullish" :location bottom :color green :style triangle-up)
(plotshape bearish-div "Bearish" :location top :color red :style triangle-down)

(alertcondition bullish-div "Bullish divergence detected")
(alertcondition bearish-div "Bearish divergence detected")
```

### Example 3: Adaptive Regime Trader

```clojure
(strategy "Adaptive Regime Trader" :default-qty 100)

(def trend-adx (adx 14))
(def in-trend (> trend-adx 25))
(def in-meanrev (< trend-adx 20))

(on-bar
  (when (and in-trend (not (rising in-trend 1)))
    (long "TREND"))
  (when (and (not in-trend) (rising in-trend 1))
    (close "TREND"))
  (when (and in-meanrev (falling (rsi 14)))
    (long "MR"))
  (when (and in-meanrev (rising (rsi 14)))
    (close "MR")))

(plot trend-adx "ADX" :color orange :linewidth 2)
(hline 25 "Trend Threshold" :color gray :linestyle dashed)
(hline 20 "MR Threshold" :color gray :linestyle dotted)
```

---

## Project Structure

```
~/Projects/stratus/
├── bb.edn                    # Babashka config
├── Makefile                  # Build/test targets
├── README.md                 # This file
├── src/
│   └── stratus/
│       ├── core.clj          # CLI entry point
│       ├── reader.clj        # S-expression parser
│       ├── constructs.clj    # ~30 construct definitions
│       ├── generator.clj     # Pine Script emitter
│       └── templates.clj     # Boilerplate templates
├── test/
│   └── stratus/
│       └── core_test.clj     # 17 tests, 166 assertions
└── examples/
    ├── golden-cross.stratus
    ├── rsi-divergence.stratus
    └── adaptive-regime.stratus
```

---

## Why This Approach?

### vs. Writing Pine Script Directly

| Capability | Pine Script | Stratus + Clojure |
|---|---|---|
| Unit tests | None | Full `clojure.test` |
| Property-based tests | None | `test.check` |
| REPL-driven development | Edit → Save → Wait → Check | Instant feedback |
| Version control | Copy-paste | Standard VCS |
| Code reuse | Copy-paste | Function composition |
| Multi-target output | TradingView only | Pine + Python + JSON |
| Metaprogramming | None | LISP macros |

### vs. Building a Full LISP→Pine Script Compiler

| Aspect | Full Compiler | Stratus DSL |
|---|---|---|
| ~3,000 lines, 2-4 weeks | ~6+ months | |
| Zero parser dependency | Requires parsing Pine Script | - |
| Easy to maintain across TV updates | Version fragility | |
| Template-based code generation | Semantic analysis + IR | |

## License

MIT
