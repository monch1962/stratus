# Stratus — LISP-Syntax DSL for Pine Script

**Write TradingView indicators and strategies in LISP syntax. Generate idiomatic Pine Script v6.
Test with Clojure's full ecosystem. Import existing Pine. Simulate backtests.**

Stratus lets you author both **indicators** and **strategies** using parenthesized
LISP-style syntax (`.stratus` files) and produces clean, production-ready Pine Script v6.
Because the DSL source is valid Clojure/EDN data, you get **unit testing,
property-based testing, CI integration, and backtesting** — none of which exist
in native Pine Script.

---

## Quick Start

```bash
# Install Babashka (15MB native Clojure)
brew install borkdude/brew/babashka

# Clone and run tests
git clone https://github.com/monch1962/stratus
cd stratus
make test
# → 357 tests, 0 failures

# Write a strategy
cat > crossover.stratus << 'EOF'
(strategy "Crossover" :default-qty 100)
(def fast (sma 50))
(def slow (sma 200))
(on-bar
  (when (crosses-above fast slow) (long "GO"))
  (when (crosses-below fast slow) (close "EXIT")))
(plot fast "Fast MA" :color blue :linewidth 2)
(plot slow "Slow MA" :color red :linewidth 2)
EOF

# Or write an indicator
cat > rsi-indicator.stratus << 'EOF'
(indicator "RSI Detector" :overlay false :precision 2)
(input-int "RSI Period" :def 14)
(def r (rsi rsi-period))
(plot r "RSI" :color purple :linewidth 2)
(hline 70 "Overbought" :color red :linestyle dashed)
(hline 30 "Oversold" :color green :linestyle dashed)
EOF

# Compile and copy to clipboard
./stratus compile crossover.stratus --clip

# Or convert existing Pine Script
./stratus import my-strategy.pine -o my.stratus

# Or backtest
./stratus simulate crossover.stratus --bars 500
# ┌──────────────────────────────────────┐
# │ Simulation Results                    │
# ├──────────────────────────────────────┤
# │  Trades:       12                    │
# │  Net P&L:      +3.42                 │
# └──────────────────────────────────────┘
```

Open TradingView → Pine Editor → **Ctrl+V** → **Ctrl+S** — your indicator or strategy is live.

---

## Documentation

| Document | Description |
|---|---|
| [`docs/TUTORIAL.md`](docs/TUTORIAL.md) | Step-by-step walkthrough: build a volatility-adaptive RSI from scratch |
| [`docs/REFERENCE.md`](docs/REFERENCE.md) | Pine Script ↔ Stratus cross-reference: 150+ construct mappings |
| [`docs/CLI.md`](docs/CLI.md) | Full CLI reference: compile, import, simulate, watch, new, list |
| [`docs/TESTING.md`](docs/TESTING.md) | Testing guide: unit tests, property tests, simulator, CI |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | TradingView deployment guide: paste, configure, iterate |

## Coverage

**392 tests, 1308 assertions, 0 failures** across 25 suites (`make test`).
**480 tests, 1670 assertions, 0 failures** across all 29 suites.

~150 constructs across indicators, conditions, logic, arithmetic, strategy actions,
plotting, colours, arrays, tables, matrix, map, strings, drawing objects,
ticker types, chart points, and built-in values.

### Examples

| File | Features |
|---|---|
| `examples/golden-cross.stratus` | SMA crossover with colour-coded plots |
| `examples/rsi-divergence.stratus` | RSI divergence with plotshape + alertcondition |
| `examples/adaptive-regime.stratus` | ADX regime detection with regime-dependent entries |
| `examples/trailing-bollinger.stratus` | BB with trailing stop, var/varip, strategy.exit |
| `examples/multi-timeframe-macd.stratus` | request.security, macd, multiset, na/nz/iff |
| `examples/inputs-library.stratus` | library, input-*, defn, export, tostring |
| `examples/math-and-stats.stratus` | 15 math/stat constructs, cumulative functions |
| `examples/array-table-drawing.stratus` | arrays, tables, line/label drawing |
| `examples/control-flow.stratus` | if/elseif/else, for loops, persistent state |
| `examples/color-fills-bg.stratus` | barcolor, bgcolor, color.rgb, from-gradient |
| `examples/advanced-exit.stratus` | strategy.exit with stops/targets/trailing |

## Project Structure

```
stratus/
├── stratus              CLI wrapper script
├── Makefile             make test, make compile
├── bb.edn               Babashka project config
├── src/stratus/
│   ├── core.clj         CLI entry point
│   ├── reader.clj       .stratus parser
│   ├── generator.clj    Pine Script code generator
│   ├── constructs.clj   DSL construct definitions
│   ├── importer.clj     Pine Script → Stratus converter
│   └── simulator.clj    Strategy backtesting engine
├── test/stratus/        17 test suites, 357 tests, 1343 assertions
├── examples/            11 example .stratus strategies
├── docs/                Split documentation
└── .vscode/             VS Code syntax highlighting
```
