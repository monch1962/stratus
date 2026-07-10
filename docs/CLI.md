# CLI Reference

## Usage

```bash
# Via the stratus wrapper script (recommended)
./stratus compile <file.stratus> [-o|--output <file.pine>]  Compile to Pine Script
                          [-c|--clip]                       Copy to clipboard
./stratus import  <file.pine>      [-o <file.stratus>]      Convert Pine to Stratus
./stratus export  <symbol>          [--market <m>]           Export OHLCV to CSV/JSON
./stratus simulate <file.stratus>  [--bars N]                Backtest strategy
./stratus watch   <file.stratus>   [-o <file.pine>]         Watch for changes
                          [-c|--clip]                       Auto-copy on save
./stratus new     <type> [name]                             Generate scaffold
./stratus list                                               List all constructs

# Or via Babashka directly
bb -m stratus.core compile examples/golden-cross.stratus
bb -m stratus.core list
bb -m stratus.core new strategy "Breakout"
```

## Compile

Compiles a `.stratus` file to Pine Script v6.

```bash
# Print to terminal (pipe to clipboard manually)
./stratus compile crossover.stratus

# Save to file
./stratus compile crossover.stratus -o crossover.pine

# Compile and copy to clipboard in one step
./stratus compile crossover.stratus --clip
# 📋 Copied to clipboard (Ctrl+V into TradingView)
```

## Import

Converts existing Pine Script v6 source code to Stratus DSL.

```bash
# Convert my-strategy.pine to my-strategy.stratus
./stratus import my-strategy.pine
# ✓ Converted my-strategy.pine → my-strategy.stratus

# Specify output path
./stratus import my-strategy.pine -o output.stratus
```

This is a best-effort converter that handles common patterns:
headers, variable assignments, indicators (sma, ema, rsi, macd, bb, etc.),
strategy actions (entry, close, exit, order), plotting (plot, hline, bgcolor),
conditions (crosses-above/below, rising/falling), and colors.
Anything it cannot translate is flagged with a `; WARN` comment.

## Export

Fetches OHLCV data for a symbol from TradingView's chart history API and
outputs it as CSV or JSON. No API key or authentication is required.

```bash
# Export daily AAPL data as CSV (to terminal)
./stratus export AAPL

# Export as JSON, save to file
./stratus export AAPL --format json -o aapl.json

# Custom market and interval
./stratus export BTCUSD --market crypto --interval 60 -o btc-1h.csv

# Dry-run (print to stdout)
./stratus export AAPL --dry-run
```

**Options:**
- `--market <m>` — market segment: `america` (default), `crypto`, `forex`, `india`
- `--interval <i>` — bar resolution: `D` (default), `1`, `5`, `15`, `60`, `W`, `M`
- `--format <fmt>` — output format: `csv` (default) or `json`
- `-o <file>` — write to file instead of stdout
- `--dry-run` — print to stdout regardless of other flags

The exporter uses TradingView's internal chart history endpoint. It fetches
the last 90 days by default. No authentication is needed for public data.

## Simulate

Runs a `.stratus` strategy against synthetic bar data and reports backtest results.

```bash
# Default: 300 bars with moderate trend
./stratus simulate crossover.stratus
# ┌──────────────────────────────────────┐
# │ Simulation Results                    │
# ├──────────────────────────────────────┤
# │  File:   crossover.stratus            │
# │  Bars:   300                          │
# │  Trades: 12                           │
# │  Net P&L: +3.42                       │
# └──────────────────────────────────────┘

# Custom bar count
./stratus simulate crossover.stratus --bars 1000
```

The simulator generates synthetic OHLCV data with configurable trend
and volatility, then evaluates your strategy bar by bar. Results
include total trades and net profit/loss.

## Watch

Watches a `.stratus` file for changes and automatically recompiles on save.

```bash
# Watch and print to terminal
./stratus watch crossover.stratus

# Watch and save to file
./stratus watch crossover.stratus -o crossover.pine

# Watch, save, and copy to clipboard
./stratus watch crossover.stratus --clip
```

## Scaffold

Generates boilerplate `.stratus` files to get started quickly.

```bash
./stratus new strategy "My Strategy"
# → Creates my-strategy.stratus with SMA crossover boilerplate

./stratus new indicator "RSI Detector"
# → Creates rsi-detector.stratus with RSI + inputs + hlines

./stratus new library "Utils"
# → Creates utils.stratus with function definitions and exports
```

## List

Displays all available DSL constructs organised by category.

```bash
./stratus list
# Available constructs (42 total):
#   Indicators:
#     sma   Simple Moving Average
#     ema   Exponential Moving Average
#     ...
```

## Friendly Error Messages

```
# Bad construct name:
✕ Unknown construct: ema (should be sma)
  Check spelling or run `stratus list` for available constructs.

# Missing closing paren:
✕ Unmatched parenthesis in strategy.stratus
  Every ( must have a matching ). Check your brackets.

# Wrong number of args:
✕ Wrong number of arguments in strategy.stratus
  A construct is missing required arguments.
```
