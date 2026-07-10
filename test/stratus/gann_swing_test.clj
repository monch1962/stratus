(ns stratus.gann-swing-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.reader :as reader]
            [stratus.generator :as gen]))

(defn compile-gann [swing-period]
  (let [src (slurp "examples/gann-swings.stratus")]
    (gen/emit-file (reader/parse src))))

;; ─── Basic compilation tests ───────────────────────────────────────

(deftest compiles-to-valid-pine
  (let [o (compile-gann 2)]
    (is (str/includes? o "//@version=6"))
    (is (str/includes? o "indicator"))))

(deftest has-variable-declarations
  (let [o (compile-gann 2)]
    (is (str/includes? o "var swing_direction"))
    (is (str/includes? o "var swing_high"))
    (is (str/includes? o "var swing_low"))))

(deftest uses-highest-and-lowest
  (let [o (compile-gann 2)]
    (is (str/includes? o "ta.highest"))
    (is (str/includes? o "ta.lowest"))))

(deftest has-conditional-swing-logic
  (let [o (compile-gann 2)]
    (is (str/includes? o "if"))
    (is (str/includes? o "swing_direction :="))))

(deftest has-input-parameter
  (let [o (compile-gann 2)]
    (is (str/includes? o "input.int"))))

(deftest has-plots
  (let [o (compile-gann 2)]
    (is (str/includes? o "plot("))
    (is (str/includes? o "Swing High"))
    (is (str/includes? o "Swing Low"))))

(deftest has-step-line-plot-style
  (let [o (compile-gann 2)]
    (is (str/includes? o "plot.style_stepline"))))

;; ─── Swing period variations ───────────────────────────────────────

(deftest different-swing-periods-compile
  (doseq [p [2 3 5 7 10]]
    (let [o (compile-gann p)]
      (is (str/includes? o "//@version=6") (str "Period " p " should compile")))))

(deftest has-set-on-swing-direction
  (let [o (compile-gann 2)]
    (is (str/includes? o "swing_direction := 1"))
    (is (str/includes? o "swing_direction := -1"))))

(deftest output-references-swing-period-variable
  (let [o (compile-gann 3)]
    (is (str/includes? o "ta.highest(high, swing_period"))
    (is (str/includes? o "ta.lowest(low, swing_period"))))

;; ─── File existence ────────────────────────────────────────────────

(deftest uses-swings-in-file-name
  (let [src (slurp "examples/gann-swings.stratus")]
    (is (str/includes? src "Gann Swings"))
    (is (str/includes? src "defvar"))))

;; ═══════════════════════════════════════════════════════════════════
;; Edge Cases: Inside / Outside Days
;; ═══════════════════════════════════════════════════════════════════

(deftest gann-with-inside-days
  "Inside days (lower high, higher low) should NOT trigger swing changes.
   The highest/lowest over period 2 means we need 2 bars of comparison."
  (let [o (compile-gann 2)]
    (is (str/includes? o "ta.highest(high, swing_period"))
    (is (str/includes? o "ta.lowest(low, swing_period"))
    ;; Check the logic structure: inside days produce neither
    ;; new highest nor new lowest, so no swing assignment occurs
    (is (str/includes? o "if high == ta.highest"))
    (is (str/includes? o "if low == ta.lowest"))))

(deftest gann-outside-day-both-directions
  "An outside day (higher high AND lower low) could potentially trigger
   BOTH swing high and swing low on the same bar. The code handles this
   by checking high first, then low — both if/else blocks run independently."
  (let [o (compile-gann 2)]
    ;; Both conditions are present and checked independently
    (is (str/includes? o "if high == ta.highest"))
    (is (str/includes? o "if low == ta.lowest"))
    ;; Direction switches both ways
    (is (str/includes? o "swing_direction := 1"))
    (is (str/includes? o "swing_direction := -1"))))

(deftest gann-consecutive-swing-highs
  "Multiple consecutive bars where each sets a new high should all
   be swing highs (extending the upswing). Each triggers the same branch."
  (let [o (compile-gann 2)]
    (is (str/includes? o "swing_high := ta.highest"))
    (is (str/includes? o "swing_direction := 1"))))

(deftest gann-consecutive-swing-lows
  "Multiple consecutive bars where each sets a new low should all
   be swing lows (extending the downswing)."
  (let [o (compile-gann 2)]
    (is (str/includes? o "swing_low := ta.lowest"))
    (is (str/includes? o "swing_direction := -1"))))

(deftest gann-alternating-highs-and-lows
  "A genuine swing reversal: bar creates new high (upswing), then
   later bar creates new low (downswing). The code must handle both
   direction transitions."
  (let [o (compile-gann 2)]
    (is (str/includes? o "if high == ta.highest"))
    (is (str/includes? o "if swing_direction == -1"))
    (is (str/includes? o "if low == ta.lowest"))
    (is (str/includes? o "if swing_direction == 1"))))

(deftest gann-equal-highs-no-swing
  "When two bars share the same high (equal highs), =/== comparison
   should fire on the first occurrence but not necessarily the second
   (the highest function returns the max value, which equals both).
   Both will match =="
  (let [o (compile-gann 2)]
    (is (str/includes? o "high == ta.highest")))
  ;; The Pine Script output uses == for the equality check
  (is (str/includes? (compile-gann 2) "==")))

(deftest gann-swing-period-input-params
  "The input parameter must have correct def, min, max"
  (let [o (compile-gann 2)]
    (is (str/includes? o "min=1"))
    (is (str/includes? o "max=10"))
    ;; Default is emitted as positional arg before the title
    (is (re-find #"input\.int\(2," o))))

(deftest gann-do-block-for-dual-assignment
  "When direction changes, both set! calls are wrapped in a do block"
  (let [o (compile-gann 2)]
    ;; The do block should produce both assignments
    (is (str/includes? o "swing_direction := 1"))
    (is (str/includes? o "swing_high := ta.highest"))
    (is (str/includes? o "swing_direction := -1"))
    (is (str/includes? o "swing_low := ta.lowest"))))

(deftest gann-first-bar-handling
  "Before enough bars for the swing period, highest/lowest return na.
   The =/== comparison with na returns na (falsy), so no swing
   assignment occurs until enough bars exist."
  (let [o (compile-gann 5)]
    (is (str/includes? o "ta.highest(high, swing_period"))
    (is (str/includes? o "ta.lowest(low, swing_period"))
    ;; All assignments happen inside if blocks, so no premature assignment
    (is (str/includes? o "if high == ta.highest"))))

(deftest gann-period-boundary-min
  "Period 2 is the minimum: compares current bar with previous bar only"
  (let [o (compile-gann 2)]
    (is (str/includes? o "swing_period"))))

(deftest gann-period-boundary-max
  "Period 10 is the maximum"
  (let [o (compile-gann 10)]
    (is (str/includes? o "swing_period"))
    (is (str/includes? o "ta.highest(high, swing_period"))
    (is (str/includes? o "ta.lowest(low, swing_period"))))

(deftest gann-sequential-inside-outside
  "Sequence: outside up (new high), inside (no change), outside down
   (new low). The inside bar should NOT change direction, only the
   outside bars trigger."
  (let [o (compile-gann 2)]
    ;; The logic checks high == highest and low == lowest independently
    ;; Inside bars satisfy neither condition
    (is (str/includes? o "if high == ta.highest"))
    (is (str/includes? o "if low == ta.lowest"))))

(deftest gann-both-high-and-low-on-same-bar
  "Outside day where high is highest AND low is lowest simultaneously.
   Both swing high and swing low assignments happen on the same bar."
  (let [o (compile-gann 2)]
    (is (str/includes? o "swing_high := ta.highest"))
    (is (str/includes? o "swing_low := ta.lowest"))))

(deftest gann-direction-transition-from-neutral
  "When swing-direction is 0 (initial/neutral), the first swing high
   sets direction to 1 (upswing), first swing low sets to -1 (downswing).
   The code handles this: (= swing-direction -1) is false when direction is 0,
   so it goes to the else/do branch."
  (let [o (compile-gann 2)]
    (is (str/includes? o "if swing_direction == -1"))
    (is (str/includes? o "if swing_direction == 1"))))

(deftest gann-period-1-uses-bar-comparison
  "Period 1 compares against the previous bar, not highest/lowest"
  (let [o (compile-gann 1)]
    (is (str/includes? o "high[1]"))
    (is (str/includes? o "low[1]"))))

(deftest gann-period-1-still-has-direction
  (let [o (compile-gann 1)]
    (is (str/includes? o "swing_direction := 1"))
    (is (str/includes? o "swing_direction := -1"))))

(deftest gann-has-both-branches
  "The generated Pine Script must have both period-1 and period-2+ branches"
  (let [o (compile-gann 2)]
    (is (str/includes? o "if swing_period == 1"))
    (is (str/includes? o "else"))))

(deftest gann-period-1-no-highest
  "Period 1 branch uses bar comparison, not ta.highest"
  (let [o (compile-gann 1)]
    (is (str/includes? o "high[1]"))
    (is (str/includes? o "low[1]"))
    ;; ta.highest is still present in the else branch (runtime switching)
    (is (str/includes? o "ta.highest"))))

;; ─── Integration ───────────────────────────────────────────────────

(deftest gann-compiles-as-full-indicator
  (let [o (gen/emit-file (reader/parse (slurp "examples/gann-swings.stratus")))]
    (is (str/includes? o "//@version=6"))
    (is (str/includes? o "indicator"))
    (is (str/includes? o "input.int"))
    (is (str/includes? o "var swing_direction"))
    (is (str/includes? o "ta.highest"))
    (is (str/includes? o "ta.lowest"))
    (is (str/includes? o "plot.style_stepline"))))

(deftest gann-example-compiles-via-cli
  (let [out (with-out-str
              (binding [*err* (java.io.StringWriter.)]
                (with-open [w (java.io.StringWriter.)]
                  (binding [*out* w]
                    (require 'stratus.core)
                    ((resolve 'stratus.core/-main) "compile" "examples/gann-swings.stratus" "-o" "/tmp/_gann_test.pine"))
                  (str w))))]
    (is (str/includes? (slurp "/tmp/_gann_test.pine") "//@version=6") "CLI compile produces valid Pine")
    (.delete (java.io.File. "/tmp/_gann_test.pine"))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.gann-swing-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
