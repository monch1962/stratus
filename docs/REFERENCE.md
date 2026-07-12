# Pine Script ↔ Stratus Cross-Reference

Stratus covers **514 tests** across **30 suites** with **~150 constructs** mapped to Pine Script v6.

## Declarations

| Pine Script | Stratus DSL |
|---|---|
| `//@version=6` | *(added automatically)* |
| `strategy("Name", overlay=true)` | `(strategy "Name" :overlay true)` |
| `indicator("Name", precision=2)` | `(indicator "Name" :precision 2)` |
| `library("Name")` | `(library "Name")` |
| `export myFun` | `(export my-fun)` |

## Inputs (all 10 types)

| Pine Script | Stratus DSL |
|---|---|
| `len = input.int(14, "Period", minval=1)` | `(input-int "Period" :def 14 :min 1)` |
| `mlt = input.float(2.0, "Multiplier")` | `(input-float "Multiplier" :def 2.0)` |
| `useStop = input.bool(true, "Stop")` | `(input-bool "Stop" :def true)` |
| `name = input.string("AAPL", "Ticker")` | `(input-string "Ticker" :def "AAPL")` |
| `src = input.source(close, "Source")` | `(input-source "Source" :def close)` |
| `sym = input.symbol("NASDAQ:AAPL", "Symbol")` | `(input-symbol "Symbol" :def "NASDAQ:AAPL")` |
| `tf = input.timeframe("D", "TF")` | `(input-timeframe "TF" :def "D")` |
| `c = input.color(color.blue, "Color")` | `(input-color "Color" :def blue)` |
| `p = input.price(100, "Price")` | `(input-price "Price" :def 100)` |
| `s = input.session("0930-1600", "Session")` | `(input-session "Session" :def "0930-1600")` |

## Variables

| Pine Script | Stratus DSL |
|---|---|
| `fast = ta.sma(close, 50)` | `(def fast (sma 50))` |
| `var count = 0` | `(defvar count 0)` |
| `varip count = 0` | `(defvarip count 0)` |
| `count := count + 1` | `(set! count (+ count 1))` |
| `myFun(x, y) => x + y` | `(defn my-fun [x y] (+ x y))` |

## Technical Indicators (44+)

| Pine Script | Stratus DSL |
|---|---|
| `ta.sma(close, 14)` | `(sma 14)` or `(sma src 14)` |
| `ta.ema(close, 20)` | `(ema 20)` |
| `ta.wma(close, 14)` | `(wma 14)` |
| `ta.hma(close, 14)` | `(hma 14)` |
| `ta.vwma(close, 14)` | `(vwma 14)` |
| `ta.rma(close, 14)` | `(rma 14)` |
| `ta.rsi(close, 14)` | `(rsi 14)` |
| `ta.macd(close, 12, 26, 9)` | `(macd :fast 12 :slow 26 :signal 9)` |
| `ta.bb(close, 20, 2)` | `(bb 20 2.0)` |
| `ta.atr(14)` | `(atr 14)` |
| `ta.adx(high, low, close, 14)` | `(adx 14)` |
| `ta.stoch(close, high, low, 14)` | `(stoch 14)` |
| `ta.supertrend(3, 10)` | `(supertrend 3 10)` |
| `ta.sar(0.02, 0.2)` | `(sar 0.02 0.2)` |
| `ta.vwap(hlc3)` | `(vwap hlc3)` |
| `ta.alma(close, 10, 6, 0.85)` | `(alma 10 6 0.85)` |
| `ta.cci(close, 20)` | `(cci 20)` |
| `ta.mfi(hlc3, 14)` | `(mfi 14)` |
| `ta.obv` | `(obv)` |
| `ta.linreg(close, 14)` | `(linreg 14)` |
| `ta.linreg(close, 14, 0, close)` | `(linreg 14 0 close)` |
| `ta.dema(close, 14)` | `(dema 14)` |
| `ta.tema(close, 14)` | `(tema 14)` |
| `ta.swma(close)` | `(swma)` |
| `ta.smma(close, 14)` | `(smma 14)` |
| `ta.tr` | `(tr)` |
| `ta.range(high, low)` | `(range high low)` |
| `ta.roc(close, 10)` | `(roc 10)` |
| `ta.cmo(close, 14)` | `(cmo 14)` |
| `ta.wad` | `(wad)` |
| `ta.cog(close, 10)` | `(cog 10)` |
| `ta.mama(close, 0.5, 0.05)` | `(mama)` or `(mama close 0.5 0.05)` |
| `ta.kc(close, 20, 2.0)` | `(kc 20)` or `(kc hlc3 20 2.5)` |
| `ta.vwmacd(close, 12, 26, 9)` | `(vwmacd)` or `(vwmacd hlc3 10 20 8)` |

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
| `ta.cross(a, b)` | `(cross a b)` |
| `ta.crossover(a, b)` | `(crossover a b)` |
| `ta.crossunder(a, b)` | `(crossunder a b)` |
| `rising(val, 1)` | `(rising val)` |
| `falling(val, 1)` | `(falling val)` |
| `na(x)` | `(na x)` |
| `nz(x, 0)` | `(nz x 0)` |
| `iff(cond, a, b)` | `(iff cond a b)` |
| `change(x, 1)` | `(change x 1)` |
| `fixnan(x)` | `(fixnan x)` |
| `valuewhen(cond, x, 0)` | `(valuewhen cond x)` |

## Logic & Comparison

| Pine Script | Stratus DSL |
|---|---|
| `a and b` | `(and a b)` |
| `a or b` | `(or a b)` |
| `not a` | `(not a)` |
| `a > b`, `a < b`, `a >= b` | `(> a b)`, `(< a b)`, `(>= a b)` |
| `a == b` | `(= a b)` |
| `a + b`, `a - b`, `a * b`, `a / b` | `(+ a b)`, `(- a b)`, `(* a b)`, `(/ a b)` |

## Math & Constants

| Pine Script | Stratus DSL |
|---|---|
| `math.log(x)` | `(log x)` |
| `math.exp(x)` | `(exp x)` |
| `math.sqrt(x)` | `(sqrt x)` |
| `math.abs(x)` | `(abs x)` |
| `math.ceil(x)`, `math.floor(x)` | `(ceil x)`, `(floor x)` |
| `math.round(x)` | `(round x)` |
| `math.round(x, 2)` | `(round x 2)` |
| `math.pow(x, y)` | `(pow x y)` |
| `math.min(a, b)`, `math.max(a, b)` | `(min a b)`, `(max a b)` |
| `math.sign(x)` | `(sign x)` |
| `math.cum(x)` | `(cum x)` |
| `math.sum(x, 20)` | `(sum x 20)` |
| `math.avg(x, y)` | `(avg x y)` |
| `math.pi` | `(pi)` |
| `math.tau` | `(tau)` |
| `math.e` | `(e)` |
| `math.phi` | `(phi)` |

## Statistics

| Pine Script | Stratus DSL |
|---|---|
| `ta.correlation(x, y, 20)` | `(correlation x y 20)` |
| `ta.covariance(x, y, 20)` | `(covariance x y 20)` |
| `ta.median(x, 20)` | `(median x 20)` |
| `ta.mode(x, 20)` | `(mode x 20)` |
| `ta.percentile_nearest_rank(x, 20, 90)` | `(percentile x 20 90)` |
| `ta.stdev(x, 20)` | `(stdev x 20)` |
| `mom(x, 10)` | `(mom x 10)` |
| `ta.highest(high, 20)` | `(highest high 20)` |
| `ta.lowest(low, 20)` | `(lowest low 20)` |
| `ta.highestbars(high, 20)` | `(highestbars high 20)` |
| `ta.lowestbars(low, 20)` | `(lowestbars low 20)` |

## Strategy Actions

| Pine Script | Stratus DSL |
|---|---|
| `strategy.entry("E", strategy.long)` | `(long "E")` |
| `strategy.entry("E", strategy.short)` | `(short "E")` |
| `strategy.close("E")` | `(close "E")` |
| `strategy.close_all()` | `(close-all)` |
| `strategy.reverse()` | `(reverse)` |
| `strategy.exit("X", from_entry="E", loss=100)` | `(exit "X" :from "E" :loss 100)` |
| `strategy.order("O", strategy.long, limit=50)` | `(order "O" :long 50.0 :limit 50)` |
| `strategy.cancel("O")` | `(cancel "O")` |

## Control Flow & Clojure Expansions

| Pine Script | Stratus DSL |
|---|---|
| `if cond` | `(if cond action)` |
| `if cond action else other` | `(if cond action :else other)` |
| `if a x else if b y else z` | `(if a x b y :else z)` |
| `for i = 1 to 5` | `(for [i (range 1 5)] ...)` or `(for [i 1 5] ...)` |
| `for i in [1, 2, 3]` | `(for [i [1 2 3]] ...)` *(compile-time unroll)* |
| `while cond` | `(while cond ...)` |
| `switch expr\n  val => action` | `(switch expr val action ...)` |
| *(no Pine equivalent)* | `(let [x expr] body)` — inline substitution |
| *(no Pine equivalent)* | `(let [[a b] (macd)] ...)` — tuple destructuring |
| *(no Pine equivalent)* | `(-> x (f a) (g b))` — thread-first macro |
| *(no Pine equivalent)* | `(->> x (f a) (g b))` — thread-last macro |
| *(no Pine equivalent)* | `(some-> x (f a))` — nil-safe thread (na guard) |
| *(no Pine equivalent)* | `(cond-> x test step)` — conditional thread |
| *(no Pine equivalent)* | `(as-> x name step)` — named thread |
| *(no Pine equivalent)* | `(for [x [1 2 3]] body)` — list comprehension |
| *(no Pine equivalent)* | `(defmacro name [args] body)` — compile-time macro |
| *(no Pine equivalent)* | `(defn f ([x] body1) ([x y] body2))` — multi-arity |
| *(no Pine equivalent)* | `(cond test expr :else default)` — multi-branch |
| *(no Pine equivalent)* | `(comment ...)` — removed from output |
| `val =>` default | `(switch expr val action :else action)` |

## Plotting & Visuals

| Pine Script | Stratus DSL |
|---|---|
| `plot(x, "Title", color=blue)` | `(plot x "Title" :color blue)` |
| `plotshape(cond, "Label", style=shape.triangleup)` | `(plotshape cond "Label" :style :triangle-up)` |
| `plotchar(cond, "Label", char="▲")` | `(plotchar cond "Label" :char "▲")` |
| `plotarrow(cond, "Direction")` | `(plotarrow cond "Direction" :colorup green :colordown red)` |
| `hline(level, "Label", color=red)` | `(hline level "Label" :color red)` |
| `bgcolor(cond, color=color.new(red, 90))` | `(bgcolor cond :color (color red 90))` |
| `barcolor(cond, color=green)` | `(barcolor cond :color green)` |
| `barcolor(color.green)` | `(barcolor :color green)` |
| `fill(p1, p2, color=color.new(blue, 90))` | `(fill p1 p2 :color blue :alpha 90)` |
| `alertcondition(cond, "Msg")` | `(alertcondition cond "Msg")` |

## Multi-Timeframe & Requests

| Pine Script | Stratus DSL |
|---|---|
| `request.security(ticker, "60", expr)` | `(security "60" expr)` |
| `request.security_lower_tf("5", expr)` | `(security-lower-tf "5" expr)` |
| `request.dividends(...)` | `(dividends)` |
| `request.splits(...)` | `(splits)` |
| `request.earnings(...)` | `(earnings)` |
| `request.financial(syminfo.tickerid, "income", "netIncome", "ttm")` | `(financial "income" "netIncome" "ttm")` |
| `request.seed(42)` | `(seed 42)` |
| `request.random(seed, 0, 100)` | `(random seed 0 100)` |

## Built-in Values (40+)

| Pine Script | Stratus DSL |
|---|---|
| `time` | `(time)` |
| `time_close` | `(time.close)` |
| `time_close[1]` | `(time.close 1)` |
| `time_tradingday` | `(time.tradingday)` |
| `year` | `(year)` |
| `dayofmonth` | `(dayofmonth)` |
| `dayofweek` | `(dayofweek)` |
| `weekofyear` | `(weekofyear)` |
| `month` | `(month)` |
| `quarter` | `(quarter)` |
| `hour` | `(hour)` |
| `minute` | `(minute)` |
| `second` | `(second)` |
| `bar_index` | `(bar-index)` |
| `syminfo.tickerid` | `(ticker)` |
| `timeframe.period` | `(timeframe)` |
| `barstate.isconfirmed` | `(bar-confirmed)` |
| `barstate.isnew` | `(bar-new)` |
| `barstate.isrealtime` | `(bar-realtime)` |
| `barstate.ishistory` | `(bar-history)` |
| `syminfo.mintick` | `(mintick)` |
| `syminfo.pointvalue` | `(pointvalue)` |
| `syminfo.currency` | `(currency)` |
| `syminfo.basecurrency` | `(base-currency)` |
| `syminfo.pricescale` | `(price-scale)` |
| `syminfo.minmov` | `(min-move)` |
| `syminfo.sector` | `(sym-sector)` |
| `syminfo.industry` | `(sym-industry)` |
| `session.isregular("0930-1600")` | `(in-session "0930-1600")` |

## Strategy Built-in Values

| Pine Script | Stratus DSL |
|---|---|
| `strategy.position_size` | `(position-size)` |
| `strategy.position_avg_price` | `(position-avg-price)` |
| `strategy.opentrades` | `(open-trades)` |
| `strategy.equity` | `(equity)` |
| `strategy.netprofit` | `(net-profit)` |
| `strategy.openprofit` | `(open-profit)` |
| `strategy.wintrades` | `(win-trades)` |
| `strategy.losstrades` | `(loss-trades)` |
| `strategy.closedtrades` | `(closed-trades)` |
| `strategy.grossprofit` | `(gross-profit)` |
| `strategy.grossloss` | `(gross-loss)` |
| `strategy.maxdrawdown` | `(max-drawdown)` |
| `strategy.maxrunup` | `(max-runup)` |
| `strategy.risk.allow_entry_in` | `(allow-entry-in)` |
| `strategy.risk.max_intraday_filled_orders` | `(max-intraday-orders)` |

## Arrays

| Pine Script | Stratus DSL |
|---|---|
| `array.new_float(50)` | `(array-float 50)` |
| `array.new_int(10)` | `(array-int 10)` |
| `array.new_bool(10)` | `(array-bool 10)` |
| `array.new_string(5)` | `(array-string 5)` |
| `array.new_color(5)` | `(array-color 5)` |
| `array.new_line(5)` | `(array-line 5)` |
| `array.new_label(5)` | `(array-label 5)` |
| `array.new_box(5)` | `(array-box 5)` |
| `array.new_table(5)` | `(array-table 5)` |
| `array.push(arr, val)` | `(push arr val)` or `(array.push arr val)` |
| `array.pop(arr)` | `(pop arr)` |
| `array.size(arr)` | `(size arr)` |
| `array.get(arr, i)` | `(get arr i)` |
| `array.set(arr, i, val)` | `(set arr i val)` |
| `array.sort(arr)` | `(sort arr)` |
| `array.insert(arr, i, val)` | `(array.insert arr i val)` |
| `array.remove(arr, i)` | `(array.remove arr i)` |
| `array.clear(arr)` | `(array.clear arr)` |
| `array.concat(a, b)` | `(array.concat a b)` |
| `array.slice(arr, 0, 5)` | `(array.slice arr 0 5)` |
| `array.copy(arr)` | `(array.copy arr)` |
| `array.shift(arr)` | `(array.shift arr)` |
| `array.unshift(arr, val)` | `(array.unshift arr val)` |
| `array.includes(arr, val)` | `(array.includes arr val)` |
| `array.indexof(arr, val)` | `(array.indexof arr val)` |
| `array.lastindexof(arr, val)` | `(array.lastindexof arr val)` |
| `array.min(arr)` | `(array.min arr)` |
| `array.max(arr)` | `(array.max arr)` |
| `array.avg(arr)` | `(array.avg arr)` |
| `array.median(arr)` | `(array.median arr)` |
| `array.sum(arr)` | `(array.sum arr)` |
| `array.stdev(arr)` | `(array.stdev arr)` |
| `array.mode(arr)` | `(array.mode arr)` |
| `array.range(arr)` | `(array.range arr)` |
| `array.fill(arr, val)` | `(array.fill arr val)` |
| `array.reverse(arr)` | `(array.reverse arr)` |

## Tables

| Pine Script | Stratus DSL |
|---|---|
| `table.new(position.top_right, 2, 2)` | `(table.new :position :top-right :cols 2 :rows 2)` |
| `table.cell(tbl, 0, 0, "Text")` | `(table-cell tbl 0 0 "Text")` |
| `table.clear(tbl)` | `(table.clear tbl)` |
| `table.delete(tbl)` | `(table.delete tbl)` |
| `table.merge_cells(tbl, 0, 0, 1, 2)` | `(table.merge-cells tbl 0 0 1 2)` |
| `table.set_position(tbl, position.top_right)` | `(table.set-position tbl top-right)` |
| `table.set_size(tbl, 3, 4)` | `(table.set-size tbl 3 4)` |
| `table.set_color(tbl, color.red)` | `(table.set-color tbl color.red)` |
| `table.set_bgcolor(tbl, color.blue)` | `(table.set-bgcolor tbl color.blue)` |
| `table.set_border_color(tbl, color.gray)` | `(table.set-border-color tbl color.gray)` |
| `table.set_border_width(tbl, 2)` | `(table.set-border-width tbl 2)` |

## Colors

| Pine Script | Stratus DSL |
|---|---|
| `color.blue` | `blue` |
| `color.rgb(65, 105, 225)` | `(rgb 65 105 225)` |
| `color.rgb(65, 105, 225, 90)` | `(rgb 65 105 225 90)` (with alpha) |
| `color.new(color.blue, 90)` | `(color blue 90)` |
| `color.from_gradient(val, low, high, red, green)` | `(from-gradient val low high :red :green)` |
| `color.r(c)` | `(color.r c)` |
| `color.g(c)` | `(color.g c)` |
| `color.b(c)` | `(color.b c)` |
| `color.t(c)` | `(color.t c)` |

## Drawing Objects

| Pine Script | Stratus DSL |
|---|---|
| `line.new(x1, y1, x2, y2, color=blue)` | `(line.new x1 y1 x2 y2 :color blue)` |
| `line.delete(l)` | `(line.delete l)` |
| `line.set_color(l, color.red)` | `(line.set-color l :color red)` |
| `line.set_width(l, 2)` | `(line.set-width l 2)` |
| `line.set_style(l, line.style_dashed)` | `(line.set-style l :style :line)` |
| `line.set_extend(l, extend.right)` | `(line.set-extend l :extend :right)` |
| `line.set_xloc(l, xloc.bar_index)` | `(line.set-xloc l :xloc :bar-index)` |
| `line.get_x1(l)` | `(line.get-x1 l)` |
| `line.get_y1(l)` | `(line.get-y1 l)` |
| `line.get_price(l, x)` | `(line.get-price l x)` |
| `label.new(x, y, "T", color=yellow)` | `(label.new x y "T" :color yellow)` |
| `label.delete(l)` | `(label.delete l)` |
| `label.set_color(l, color.red)` | `(label.set-color l :color red)` |
| `label.set_text(l, "New")` | `(label.set-text l "New")` |
| `label.set_x(l, 100)` | `(label.set-x l 100)` |
| `label.set_y(l, 200)` | `(label.set-y l 200)` |
| `label.set_style(l, label.style_label_down)` | `(label.set-style l :style :label-down)` |
| `label.set_textcolor(l, color.red)` | `(label.set-textcolor l :color red)` |
| `label.set_textalign(l, text.align_center)` | `(label.set-textalign l :align :center)` |
| `label.set_size(l, size.normal)` | `(label.set-size l :size :normal)` |
| `label.get_x(l)` | `(label.get-x l)` |
| `label.get_text(l)` | `(label.get-text l)` |
| `box.new(x1, y1, x2, y2, bgcolor=blue)` | `(box.new x1 y1 x2 y2 :bgcolor blue)` |
| `box.delete(b)` | `(box.delete b)` |
| `box.set_color(b, color.red)` | `(box.set-color b :color red)` |
| `box.set_width(b, 2)` | `(box.set-width b 2)` |
| `box.set_extend(b, extend.right)` | `(box.set-extend b :extend :right)` |
| `box.set_style(b, line.style_dashed)` | `(box.set-style b :style :dashed)` |
| `box.get_left(b)` | `(box.get-left b)` |
| `box.get_top(b)` | `(box.get-top b)` |
| `polygon.new(p1, p2, p3, color=blue)` | `(polygon.new p1 p2 p3 :color blue)` |
| `polygon.delete(p)` | `(polygon.delete p)` |
| `polygon.set_fillcolor(p, color.red)` | `(polygon.set-fillcolor p :color red)` |
| `polygon.set_bordercolor(p, color.blue)` | `(polygon.set-bordercolor p :color blue)` |
| `polygon.set_borderwidth(p, 2)` | `(polygon.set-borderwidth p 2)` |

## Matrix

| Pine Script | Stratus DSL |
|---|---|
| `matrix.new<float>(3, 3, 0.0)` | `(matrix.new 3 3 0.0)` or `(matrix.float 3 3 0.0)` |
| `matrix.new<int>(3, 3, 0)` | `(matrix.int 3 3 0)` |
| `matrix.new<bool>(3, 3, true)` | `(matrix.bool 3 3 true)` |
| `matrix.new<string>(3, 3, "")` | `(matrix.string 3 3 "")` |
| `matrix.new<line>(3, 3)` | `(matrix.line 3 3)` |
| `matrix.rows(m)` | `(matrix.rows m)` |
| `matrix.columns(m)` | `(matrix.columns m)` |
| `matrix.get(m, 0, 1)` | `(matrix.get m 0 1)` |
| `matrix.set(m, 0, 1, val)` | `(matrix.set m 0 1 val)` |
| `matrix.row(m, 0)` | `(matrix.row m 0)` |
| `matrix.sum(m)` | `(matrix.sum m)` |
| `matrix.transpose(m)` | `(matrix.transpose m)` |
| `matrix.multiply(a, b)` | `(matrix.multiply a b)` |
| `matrix.inv(m)` | `(matrix.inv m)` |
| `matrix.det(m)` | `(matrix.det m)` |
| `matrix.rank(m)` | `(matrix.rank m)` |
| `matrix.fill(m, 0.0)` | `(matrix.fill m 0.0)` |

## Map

| Pine Script | Stratus DSL |
|---|---|
| `map.new<color, int>()` | `(map.new)` |
| `map.put(m, k, v)` | `(map.put m k v)` |
| `map.get(m, k)` | `(map.get m k)` |
| `map.contains(m, k)` | `(map.contains m k)` |
| `map.keys(m)` | `(map.keys m)` |
| `map.size(m)` | `(map.size m)` |

## String Functions

| Pine Script | Stratus DSL |
|---|---|
| `str.tostring(x)` | `(tostring x)` |
| `str.contains(s, "abc")` | `(str.contains s "abc")` |
| `str.contains(s, "abc", 5)` | `(str.contains s "abc" 5)` |
| `str.length(s)` | `(str.length s)` |
| `str.split(s, ",")` | `(str.split s ",")` |
| `str.lower(s)` | `(str.lower s)` |
| `str.upper(s)` | `(str.upper s)` |
| `str.replace_all(s, "a", "b")` | `(str.replace-all s "a" "b")` |
| `str.substring(s, 0, 5)` | `(str.substring s 0 5)` |
| `str.startswith(s, "a")` | `(str.startswith s "a")` |

## Type Predicates

| Pine Script | Stratus DSL |
|---|---|
| `series(x)` | `(series x)` |
| `array(x)` | `(array x)` |
| `string(x)` | `(string x)` |
| `int(x)` | `(int x)` |
| `float(x)` | `(float x)` |
| `bool(x)` | `(bool x)` |

## Ticker Functions

| Pine Script | Stratus DSL |
|---|---|
| `ticker.heikinashi(sym)` | `(ticker.heikinashi sym)` |
| `ticker.renko(sym)` | `(ticker.renko sym)` |
| `ticker.new(prefix, sym)` | `(ticker.new prefix sym)` |
| `ticker.modify(t, "60")` | `(ticker.modify t "60")` |

## Order Introspection

| Pine Script | Stratus DSL |
|---|---|
| `order.entry_condition()` | `(order.entry-condition)` |
| `order.exit_condition()` | `(order.exit-condition)` |
| `order.filled()` | `(order.filled)` |
| `order.entry_id()` | `(order.entry-id)` |

## Chart Points

| Pine Script | Stratus DSL |
|---|---|
| `chart.point.now()` | `(chart.point.now)` |
| `chart.point.from_index(100)` | `(chart.point.from-index 100)` |
