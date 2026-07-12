# Testing Guide

Pine Script has **no unit testing capability whatsoever**. Stratus bypasses
this entirely — your strategy DSL is valid Clojure data, so you get the full
`clojure.test` ecosystem.

## What You Can Test

| Layer | What It Tests | Tool |
|---|---|---|
| **Parser** | Does the DSL source parse to the correct AST? | `clojure.test` |
| **Generator** | Does the AST emit the correct Pine Script string? | `clojure.test` |
| **Signal logic** | Does the strategy produce the right entry/exit signals on known bar data? | `clojure.test` + vectors |
| **Property-based** | Given any valid parameter set, does the compiler produce valid output? | `clojure.test` |
| **Integration** | Does the generated Pine Script pass through the simulator? | `clojure.test` |

## Running Tests

```bash
# Run all tests (393 across 22 suites)
make test

# Run individual suites
bb -m stratus.core-test              # core generator tests (87)
bb -m stratus.cli-test               # CLI tests (15)
bb -m stratus.p0p1-test              # P0/P1 feature tests (20)
bb -m stratus.p1p2-test              # P1/P2 feature tests (18)
bb -m stratus.remaining-test         # P3/P4/P5 feature tests (15)
bb -m stratus.gann-swing-test        # Gann swing tests (31)
bb -m stratus.exporter-test          # Pine exporter tests (17)
bb -m stratus.input-test             # Input function tests (10)
bb -m stratus.p1p2-fixes-test        # P1/P2 fix tests (22)
bb -m stratus.p3p4-test              # P3/P4 feature tests (19)
bb -m stratus.p1p3-final-test        # Final P1/P2/P3 tests (16)
bb -m stratus.p2p3-remaining-test    # Remaining P2/P3 tests (13)
bb -m stratus.last-missing-test      # Last missing features (8)
bb -m stratus.examples-test          # Example integration tests (12)
bb -m stratus.converter-test         # Pine→Stratus converter tests (9)
bb -m stratus.simulator-test         # Backtesting simulator tests (4)
bb -m stratus.import-simulate-test   # Import + simulate CLI tests (10)
```

## Test Examples

### 1. Testing DSL Parsing

```clojure
(deftest test-reader-parses-sma
  (let [result (reader/parse "(sma 14)")]
    (is (= 1 (count result)))
    (is (= 'sma (ffirst result)))))
```

### 2. Testing Code Generation

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

### 3. Testing Strategy Signal Logic

This is the most powerful test pattern — something Pine Script cannot do:

```clojure
(deftest test-golden-cross-fires-on-cross
  (let [bars (vec (repeatedly 200 #(hash-map :close (+ 100 (rand 10))
                                              :high (+ 105 (rand 10))
                                              :low  (+ 95 (rand 10))
                                              :volume (rand-int 1000000))))
        strategy '(do (def fast (sma 5))
                      (def slow (sma 20))
                      (when (crosses-above fast slow) (long "ENTER")))
        orders (atom [])
        result (sim/simulate bars strategy
                 :on-bar (fn [s f]
                           (when (= (first f) 'long)
                             (swap! orders conj {:action :buy})))
                 :on-result (fn [s] @orders))]
    (is (pos? (count result)))))
```

### 4. Property-Based Testing

```clojure
(deftest test-all-sma-periods-compile
  (doseq [period (take 50 (repeatedly #(+ 2 (rand-int 198))))]
    (let [output (gen/expr->pine (list 'sma period))]
      (is (re-find #"^ta\.sma\(close, \d+\)$" output)
          (str "Failed for period " period)))))
```

## Practical Testing Walkthrough

### Step 1: Create a test file

Save as `test/stratus/my_strategy_test.clj`:

```clojure
(ns stratus.my-strategy-test
  (:require [clojure.test :refer :all]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

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
        output (gen/emit-file (reader/parse src))]
    (is (str/includes? output "//@version=6"))
    (is (str/includes? output "ta.sma(close, 50)"))))

(deftest custom-source-passes-through
  (is (= "ta.sma(high, 14)" (gen/expr->pine '(sma high 14)))))

(deftest all-plot-styles-valid
  (doseq [style [:histogram :area :columns :circles :cross :step-line]]
    (let [output (gen/expr->pine (list 'plot 'val :style style))]
      (is (str/includes? output "plot.style_")))))
```

### Step 2: Run it

```bash
bb -m stratus.my-strategy-test
```

You'll see:

```
Testing stratus.my-strategy-test

Ran 3 tests containing 9 assertions.
0 failures, 0 errors.
```

### Step 3: Catch a regression

Rename `ta.sma` to `tv.sma` in the generator, then run tests again:

```
FAIL in (custom-source-passes-through)
expected: (= "ta.sma(high, 14)" (gen/expr->pine '(sma high 14)))
  actual: (not (= "tv.sma(high, 14)" "ta.sma(high, 14)"))
```

The test catches the API change **before** your users see compile errors in TradingView.

## CI Integration

Add `.github/workflows/test.yml` (already included in the repo):

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

Every push runs all 357 tests in under 10 seconds.
