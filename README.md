# Stratus — LISP-Syntax Strategy DSL for Pine Script

**Write trading strategies in LISP syntax. Generate idiomatic Pine Script v6. Test with Clojure's full ecosystem.**

Stratus is a code generator that lets you author TradingView indicators and strategies using parenthesized LISP-style syntax (.stratus files) and produces clean, production-ready Pine Script v6. Because the DSL source is valid Clojure/EDN data, you get **real unit testing, property-based testing, and CI integration** — none of which exist in the Pine Script ecosystem.

## Quick Start

### Install

Prerequisites: [Babashka](https://babashka.org/) (a 15MB native Clojure interpreter).

```bash
# macOS
brew install borkdude/brew/babashka

# Linux (via brew)
brew tap borkdude/brew && brew install babashka

# Or download binary from GitHub releases
```

### Build Stratus

```bash
git clone <repo> ~/Projects/stratus
cd ~/Projects/stratus

# Run tests to verify everything works
bb -m stratus.core-test
# → Ran 55 tests, 373 assertions, 0 failures
bb -m stratus.p0p1-test
bb -m stratus.p1p2-test
bb -m stratus.remaining-test
# → 108 total tests, 539 assertions, 0 failures

# Or use the Makefile
make test

# List available DSL constructs
bb -m stratus.core list

# Compile a strategy to Pine Script
bb -m stratus.core compile examples/golden-cross.stratus

# Save to file
bb -m stratus.core compile examples/golden-cross.stratus -o golden-cross.pine
```

### Your First Strategy

Write `my-strategy.stratus`:

```clojure
(strategy "EMA Crossover"
  :default-qty 100)

(def fast (ema 12))
(def slow (ema 26))

(on-bar
  (when (crosses-above fast slow)
    (long "ENTER"))
  (when (crosses-below fast slow)
    (close "EXIT")))

(plot fast "Fast" :color blue :linewidth 2)
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
