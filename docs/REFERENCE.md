# Pine Script ↔ Stratus Cross-Reference

## Declarations

| Pine Script | Stratus DSL |
|---|---|
| `//@version=6` | _(added automatically)_ |
| `strategy("Name", overlay=true)` | `(strategy "Name" :overlay true)` |
| `indicator("Name", precision=2)` | `(indicator "Name" :precision 2)` |
| `library("Name")` | `(library "Name")` |
| `len = input.int(14, "Period")` | `(input-int "Period" :def 14)` |
| `src = input.source(close, "Source")` | `(input-source "Source" :def close)` |

## Variables

| Pine Script | Stratus DSL |
|---|---|
| `fast = ta.sma(close, 50)` | `(def fast (sma 50))` |
| `var count = 0` | `(defvar count 0)` |
| `varip count = 0` | `(defvarip count 0)` |
| `count := count + 1` | `(set! count (+ count 1))` |
| `myFun(x, y) => x + y` | `(defn my-fun [x y] (+ x y))` |
| `export myFun` | `(export my-fun)` |

## Technical Indicators

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

## Price References

| Pine Script | Stratus DSL |
|---|---|
| `close` | `close` |
| `close[1]` | `(close 1)` |
| `high`, `low`, `open`, `volume` | `high`, `low`, `open`, `volume` |
| `hl2`, `hlc3`, `ohlc4` | `hl2`, `hlc3`, `ohlc4` |

## Conditions

| Pine Script | Stratus DSL |
|---|---|
| `ta.cross(a, b) and a > b` | `(crosses-above a b)` |
| `ta.cross(a, b) and a < b` | `(crosses-below a b)` |
| `rising(val, 1)` | `(rising val)` |
| `falling(val, 1)` | `(falling val)` |
| `na(x)` | `(na x)` |
| `nz(x, 0)` | `(nz x 0)` |
| `iff(cond, a, b)` | `(iff cond a b)` |

## Logic & Comparison

| Pine Script | Stratus DSL |
|---|---|
| `a and b` | `(and a b)` |
| `a or b` | `(or a b)` |
| `not a` | `(not a)` |
| `a > b`, `a < b`, `a >= b` | `(> a b)`, `(< a b)`, `(>= a b)` |
| `a == b` | `(= a b)` |
| `a + b`, `a - b`, `a * b`, `a / b` | `(+ a b)`, `(- a b)`, `(* a b)`, `(/ a b)` |

## Strategy Actions

| Pine Script | Stratus DSL |
|---|---|
| `strategy.entry("E", strategy.long)` | `(long "E")` |
| `strategy.entry("E", strategy.short)` | `(short "E")` |
| `strategy.close("E")` | `(close "E")` |
| `strategy.exit("X", from="E", loss=100)` | `(exit "X" :from "E" :loss 100)` |
| `strategy.order("O", strategy.long, limit=50)` | `(order "O" :long 50.0 :limit 50)` |
| `strategy.cancel("O")` | `(cancel "O")` |

## Control Flow

| Pine Script | Stratus DSL |
|---|---|
| `if cond` | `(if cond action)` |
| `if cond action else other` | `(if cond action :else other)` |
| `if a x else if b y else z` | `(if a x b y :else z)` |
| `for i = 1 to 5` | `(for [i (range 1 5)] ...)` |
| `while cond` | `(while cond ...)` |
| `switch expr` | `(switch expr val action ...)` |

## Plotting & Visuals

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

## Multi-Timeframe

| Pine Script | Stratus DSL |
|---|---|
| `request.security(ticker, "60", expr)` | `(security "60" expr)` |
| `request.dividends(...)` | `(dividends)` |
| `request.splits(...)` | `(splits)` |

## Math & Statistics

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

## Built-in Values

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
| `syminfo.mintick` | `(mintick)` |
| `syminfo.pointvalue` | `(pointvalue)` |
| `barstate.isconfirmed` | `(bar-confirmed)` |
| `session.isregular("0930-1600")` | `(in-session "0930-1600")` |

## Arrays & Tables

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

## Colors

| Pine Script | Stratus DSL |
|---|---|
| `color.blue` | `blue` |
| `color.rgb(65, 105, 225)` | `(rgb 65 105 225)` |
| `color.new(color.blue, 90)` | `(color blue 90)` |
| `color.from_gradient(val, low, high, red, green)` | `(from-gradient val low high :red :green)` |

## Drawing Objects

| Pine Script | Stratus DSL |
|---|---|
| `line.new(x1, y1, x2, y2, color=blue)` | `(line.new x1 y1 x2 y2 :color blue)` |
| `line.delete(l)` | `(line.delete l)` |
| `label.new(x, y, "Text", color=yellow)` | `(label.new x y "Text" :color yellow)` |
| `box.new(x1, y1, x2, y2, bgcolor=blue)` | `(box.new x1 y1 x2 y2 :bgcolor blue)` |
