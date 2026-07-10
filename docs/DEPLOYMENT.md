# TradingView Deployment Guide

Compiling Stratus to Pine Script is the first step. Here is the complete
workflow to get your strategy live on a TradingView chart.

## Step 1: Compile

```bash
./stratus compile my-strategy.stratus --clip
```

This outputs the Pine Script code and copies it to your clipboard.

## Step 2: Open TradingView Pine Editor

1. Open TradingView and go to any chart
2. Click **Pine Editor** at the bottom of the screen
3. **Select all** existing code (Ctrl+A) and **delete** it

## Step 3: Paste Compiled Code

1. **Ctrl+V** (or Cmd+V) — the compiled Pine Script appears in the editor
2. **Ctrl+S** (or Cmd+S) — TradingView saves and compiles the script

## Step 4: Add to Chart

**Indicator:**
- After saving, the indicator automatically appears on the chart.
- Toggle settings via the gear icon next to the script name in the Data Window.

**Strategy:**
1. After saving, click **Add to Chart** in the Pine Editor toolbar.
2. The strategy appears above the chart — click its name to open the
   **Strategy Tester** tab below the chart.
3. Configure initial capital, order size, commission, and slippage in
   the **Properties** tab of the Strategy Tester.

## Step 5: Iterate

The edit-compile-paste loop:

```
1.  Edit your .stratus file in any text editor
2.  Run:  ./stratus compile my-strategy.stratus --clip
3.  Switch to TradingView (Alt+Tab / Cmd+Tab)
4.  Ctrl+A → Ctrl+V → Ctrl+S
5.  Check the chart and Strategy Tester
6.  Go to step 1
```

With **watch mode**, steps 1-2 merge into one:

```
1.  Run once:     ./stratus watch my-strategy.stratus --clip
2.  Edit .stratus and save → automatically compiles and copies
3.  Switch to TradingView → Ctrl+A → Ctrl+V → Ctrl+S
4.  Repeat from step 2
```

## Library Scripts

For `library` scripts (compiled with `(library "Name" ...)`):

1. Paste the compiled code into the Pine Editor
2. Click **Save As** → give it a name matching the library name
3. The library is now available for import in other scripts via:
   ```pinescript
   import <username>/<library-name>/<version>
   ```

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Pine Editor shows red error highlights | Unmatched brackets in `.stratus` file | Check parens match — every `(` needs a `)` |
| "Undeclared identifier" errors | Variable name uses kebab-case | Hyphens become underscores in Pine — reference `fast_ma` not `fast-ma` |
| "No matching function" errors | Wrong argument count for an indicator | Check `./stratus list` for correct usage |
| Strategy not showing in tester | Missing `strategy()` header | Add `(strategy "Name" :default-qty 100)` |
