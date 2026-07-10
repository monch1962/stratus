# Pine Script v6 Feature Gap Analysis

Current coverage: **48 constructs** across indicators, conditions, logic, actions, plotting, alerts, and built-in price references. Below is everything missing, prioritised by how often the feature blocks a real-world strategy.

---

## P0 — Critical (blocks common strategies)

These are needed before Stratus can express the majority of public TradingView strategies and indicators.

### 1. `if`/`else` multi-branch in `on-bar`

Current DSL only supports `(when cond action)`. Pine allows full `if/else if/else` chains:

```pinescript
if cond1
    action1
else if cond2
    action2
else
    action3
```

**DSL proposal:**
```clojure
(if cond1 action1
    cond2 action2
    :else action3)
```

**Blocks:** Any strategy with fallback logic, tiered entry conditions, or multiple regimes.

### 2. `var` / `varip` persistent state

Pine Script's `var` keyword initialises a variable only on the first bar and preserves its value across bars. Essential for counters, state machines, cumulative calculations:

```pinescript
var int trade_count = 0
if condition
    trade_count := trade_count + 1
```

**DSL proposal:**
```clojure
(defvar trade-count 0)
(set! trade-count (+ trade-count 1))
```

**Blocks:** Trade counters, trailing stop state, position sizing, any multi-bar state machine.

### 3. `strategy.exit()` with stop-loss/take-profit

Without this, users can enter trades but cannot set stop-losses or take-profits. This is the #1 missing feature for strategy authors:

```pinescript
strategy.exit("XL", "ENTER", loss=100, profit=200, trail_points=50, trail_offset=10)
```

**DSL proposal:**
```clojure
(exit "XL" :from "ENTER" :loss 100 :profit 200 :trail 50 :trail-offset 10)
```

**Blocks:** Any real trading strategy.

### 4. `request.security()` multi-timeframe

Many strategies operate on higher timeframes for trend direction and lower timeframes for entry:

```pinescript
htf_sma = request.security(syminfo.tickerid, "60", ta.sma(close, 20))
```

**DSL proposal:**
```clojure
(def htf-sma (security "60" (sma 20)))
```

**Blocks:** Multi-timeframe strategies, trend filter + entry combos.

### 5. `fill()` between plots

Highlighting regions between two plots (e.g., between upper/lower Bollinger Bands) is extremely common in Pine indicators:

```pinescript
fill(plot1, plot2, color=color.new(color.blue, 90))
```

**DSL proposal:**
```clojure
(fill upper lower :color blue :transparency 90)
```

**Blocks:** Bollinger Bands, Keltner Channels, any channel/cloud indicator.

### 6. `highest()` / `lowest()` over N periods

```pinescript
highest(high, 20)
lowest(low, 20)
```

**DSL proposal:**
```clojure
(highest high 20)
(lowest low 20)
```

**Blocks:** Breakout strategies, Donchian channels, trailing stops.

---

## P1 — High (frequently used, high derivable value)

### 7. `change()` and `mom()` momentum

```pinescript
change(close, 1)   ; close - close[1]
mom(close, 10)     ; close - close[10]
```

**Blocks:** Rate-of-change filters, divergence confirmation.

### 8. `na()` / `nz()` / `iff()` handling

```pinescript
nz(value, 0)      ; replace na with 0
na(value)          ; check if na
iff(cond, a, b)    ; ternary
```

**Blocks:** Indicator initialisation, safe-default-value patterns.

### 9. Tuple unpacking for multi-return indicators

`macd`, `bb`, `stoch` return multiple values. Users need to unpack them:

```pinescript
[middle, upper, lower] = ta.bb(close, 20, 2)
[macdLine, signalLine, histLine] = ta.macd(close, 12, 26, 9)
```

**Current DSL:**
```clojure
(def bb-vals (bb 20))
```
But there's no way to access `bb_upper`, `bb_lower`.

**Proposal:**
```clojure
(multiset [middle upper lower] (bb 20))
(plot upper "Upper")
(plot lower "Lower")
```

**Blocks:** Bollinger Bands, MACD histograms, Stochastic %K/%D.

### 10. Additional indicators

| Indicator | Pine Script | DSL Proposal | Frequency |
|---|---|---|---|
| SuperTrend | `ta.supertrend(3, 10)` | `(supertrend 3 10)` | Very high |
| Parabolic SAR | `ta.sar(0.02, 0.2)` | `(sar 0.02 0.2)` | High |
| VWAP | `ta.vwap(hlc3)` | `(vwap)` | High |
| Standard Deviation | `ta.stdev(close, 20)` | `(stdev close 20)` | High |
| ATR trailing stop | Manual | Built into `(exit :trail ...)` | High |
| WMA / VWMA | `ta.wma`, `ta.vwma` | `(wma 14)` / `(vwma 14)` | Medium |
| Hull MA | `ta.hma(close, 20)` | `(hma 20)` | Medium |
| Ichimoku | `ta.ichimoku(h, l, 9, 26, 52)` | `(ichimoku)` | Medium |
| Money Flow Index | `ta.mfi(hlc3, 14)` | `(mfi 14)` | Medium |
| Commodity Channel Index | `ta.cci(close, 20)` | `(cci 20)` | Medium |
| On-Balance Volume | `ta.obv` | `(obv)` | Medium |

### 11. Plot styles

```clojure
(plot val :style :histogram :color blue)
;; → plot(val, style=plot.style_histogram, color=color.blue)
```

**Supported styles:** `:line` (default), `:histogram`, `:area`, `:columns`, `:circles`, `:cross`, `:step-line`, `:step-line-diamond`

**Blocks:** Histogram-style MACD, volume columns, area fills.

### 12. Strategy position info

```pinescript
strategy.position_size  ; current position size
strategy.position_avg_price
strategy.opentrades
strategy.equity
```

**DSL proposal:**
```clojure
;; Access as built-in symbols in expressions
(def pos-size position-size)
(def pnl equity-starting)
```

**Blocks:** Position-based exit logic, pyramiding limits, equity curve indicators.

### 13. `barcolor` with no condition

Currently `(barcolor cond :color red)`. Should also support unconditional:
```clojure
(barcolor :color green :title "Up bars")
```

---

## P2 — Medium (valuable for specific use cases)

### 14. `library()` scripts

Pine v6 supports `library()` scripts that export reusable functions. Stratus could generate library modules.

### 15. User-defined functions

```clojure
(fn [x y] (+ x y))

;; or as a named defn:
(defn my-sma [src n]
  (sma src n))
```

**Note:** This is less critical because users can already compose built-in constructs. True value comes when users want reusable strategy building blocks.

### 16. Time/session built-ins

```clojure
(time)           ;; current bar time
(dayofweek)      ;; 0=Sunday, 1=Monday...
(month)          ;; 1-12
(hour)           ;; 0-23
(bar-index)      ;; current bar index
(ticker)         ;; syminfo.tickerid
(timeframe)      ;; current timeframe string
(in-session "0930-1600")  ;; session check
```

**Blocks:** Session filters, time-based exits, intraday strategies.

### 17. Loop constructs

```clojure
(for [i (range 1 10)]
  (when (> (close i) (sma 20))
    (long)))

(while cond
  action)
```

### 18. `array` / `matrix` / `map`

```clojure
(def arr (array :int))
(push arr 10)
(pop arr)
```

### 19. `switch` statement

```clojure
(switch (regime)
  0 (long "TREND")
  1 (short "MEANREV")
  :else (close))
```

### 20. Plot fills inside the on-bar block

Beyond simple `(fill p1 p2)`, there's the conditional region fill pattern used in indicators like MACD histogram coloring.

### 21. `strategy.risk.*` position sizing

```clojure
(strategy "T" :default-qty 100 :risk-percent 2.0 :max-loss 500)
```

### 22. `color.new()` transparency

```clojure
(plot x :color (color :red :alpha 50))
;; → color.new(color.red, 50)
```

### 23. `bar_state.*` filters

```clojure
(bar-confirmed)     ;; barstate.isconfirmed
(bar-first)         ;; barstate.isfirst
(bar-last)          ;; barstate.islast
```

---

## P3 — Low (edge cases / platform-specific)

| Feature | Notes |
|---|---|
| `line`, `label`, `box`, `table` objects | Visual elements; better handled by external tools |
| `matrix` ops | Rarely used in indicators |
| `strategy.convert_position` to FIFO | Very specific order management |
| Strategy parameter optimisation tuples | TradingView-specific UI feature |
| `request.dividends`, `request.splits` | Fundamental data — niche |
| `request.earnings` | Niche |
| `calendar.*` functions | Calendar-aware trading — very niche |
| `strategy.*` alerts from code | TradingView-specific |
| TradingView compiler directives | Platform-specific |

---

## Implementation Priority

```
Now (P0)
├── strategy.exit() with stops/targets    ← unlocks real strategies
├── if/else (multi-branch in on-bar)      ← replaces when-only limitation
├── var/varip (persistent state)          ← stops/position tracking
├── highest() / lowest()                  ← breakout patterns
├── fill()                                ← Bollinger Bands, channels
└── request.security()                    ← multi-timeframe

Next (P1)
├── Tuple unpacking for macd/bb/stoch
├── na() / nz() / iff()
├── change() / mom()
├── 10 additional indicators (supertrend, sar, vwap, stdev, wma, hma, etc.)
├── Plot styles (histogram, area, columns)
├── strategy.position_size info
├── barcolor unconditional form
└── SuperTrend + ATR trailing built into exit

Soon (P2)
├── Time/session built-ins
├── User-defined functions
├── for / while loops
├── library() script generation
├── switch statement
├── color.new() transparency
├── barstate.* filters
├── Colour transparency
└── array basics

Later (P3)
├── Line/label/box drawing
├── Matrix/map operations
├── Fundamental data requests
├── Compiler directives
└── Platform-specific order types
```

**Priority guide:** P0 + P1 represent ~95% of real-world TradingView strategy patterns. Implementing P0 first removes the hardest blockers; P1 rounds out the common toolset. P2 and P3 are nice-to-haves that unlock specific niches.
