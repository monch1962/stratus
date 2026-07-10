(ns stratus.cli-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [stratus.core :as core])
  (:import [java.io File]))

(defn with-temp-file [fname f]
  (let [f (File. fname)]
    (try (f f) (finally (when (.exists f) (.delete f))))))

(deftest compile-outputs-pine
  (let [src "(strategy \"T\" :default-qty 100)\n(def x (sma 14))\n(plot x)"
        _ (spit "/tmp/_test_strat.stratus" src)
        out (with-out-str (core/-main "compile" "/tmp/_test_strat.stratus"))]
    (is (str/includes? out "//@version=6"))
    (is (str/includes? out "ta.sma(close, 14)"))))

(deftest compile-writes-file
  (let [src "(strategy \"T\" :default-qty 100)\n(def x (sma 14))\n(plot x)"
        _ (spit "/tmp/_test_strat.stratus" src)]
    (core/-main "compile" "/tmp/_test_strat.stratus" "-o" "/tmp/_test_output.pine")
    (let [content (slurp "/tmp/_test_output.pine")]
      (is (str/includes? content "//@version=6"))
      (is (str/includes? content "ta.sma")))
    (.delete (File. "/tmp/_test_output.pine"))))

(deftest list-shows-constructs
  (let [out (with-out-str (core/-main "list"))]
    (is (str/includes? out "sma"))
    (is (str/includes? out "strategy"))
    (is (str/includes? out "plot"))))

(deftest usage-shown-on-no-args
  (let [out (with-out-str (core/-main))]
    (is (str/includes? out "Usage"))
    (is (str/includes? out "compile"))))

(deftest compile-with-clip-flag
  (let [src "(strategy \"T\")\n(def x (sma 14))\n(plot x)"
        _ (spit "/tmp/_test_clip.stratus" src)
        out (with-out-str (core/-main "compile" "/tmp/_test_clip.stratus" "--clip"))]
    (is (str/includes? out "//@version=6"))
    (is (str/includes? (str/lower-case out) "clipboard"))))

(deftest compile-with-short-clip
  (let [src "(strategy \"T\")\n(def x (sma 14))\n(plot x)"
        _ (spit "/tmp/_test_clip2.stratus" src)
        out (with-out-str (core/-main "compile" "/tmp/_test_clip2.stratus" "-c"))]
    (is (str/includes? out "//@version=6"))
    (is (str/includes? (str/lower-case out) "clipboard"))))

(deftest usage-shows-clip-and-watch
  (let [out (with-out-str (core/-main))]
    (is (str/includes? out "clip"))
    (is (str/includes? out "watch"))))

(deftest scaffold-creates-file
  (core/-main "new" "strategy")
  (let [f (java.io.File. "my-strategy.stratus")]
    (is (.exists f))
    (.delete f)))

(deftest scaffold-with-name
  (core/-main "new" "indicator" "MyRSI")
  (let [f (java.io.File. "MyRSI.stratus")]
    (is (.exists f))
    (is (str/includes? (slurp "MyRSI.stratus") "indicator"))
    (.delete f)))

(deftest scaffold-library-works
  (core/-main "new" "library" "Utils")
  (let [f (java.io.File. "Utils.stratus")]
    (is (.exists f))
    (is (str/includes? (slurp "Utils.stratus") "library"))
    (.delete f)))

(deftest scaffold-default-uses-type-name
  (core/-main "new" "strategy" "Breakout")
  (let [f (java.io.File. "Breakout.stratus")]
    (is (.exists f))
    (is (str/includes? (slurp "Breakout.stratus") "strategy"))
    (.delete f)))

(deftest usage-shows-scaffold
  (let [out (with-out-str (core/-main))]
    (is (str/includes? out "new"))))

(deftest safe-compile-missing-file
  (let [out (with-out-str (core/-main "compile" "/tmp/_nonexistent.stratus"))]
    (is (str/includes? out "File not found"))))

(deftest safe-compile-bad-syntax
  (spit "/tmp/_bad.stratus" "(strategy \"Bad\"\n(def x (sma 14)\n")  ;; missing )
  (let [out (with-out-str (core/-main "compile" "/tmp/_bad.stratus"))]
    (is (or (str/includes? out "Unmatched")
            (str/includes? (str/lower-case out) "error")))))

(deftest new-shows-usage-on-no-type
  (let [out (with-out-str (core/-main "new"))]
    (is (str/includes? out "Usage"))))

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.cli-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
