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

;; ═══════════════════════════════════════════════════════════════════
;; Main
;; ═══════════════════════════════════════════════════════════════════

(defn -main [& args]
  (let [r (clojure.test/run-tests 'stratus.cli-test)]
    (System/exit (if (zero? (:fail r)) 0 1))))
