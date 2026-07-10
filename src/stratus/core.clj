(ns stratus.core
  (:require [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.constructs :as constructs])
  (:gen-class))

;; ─── Cross-platform clipboard ───────────────────────────────────────

(defn- copy-to-clipboard
  "Copy text to system clipboard via platform CLI tool."
  [text]
  (try
    (let [cmd (let [os (System/getProperty "os.name")]
                (cond (.contains os "Mac")     ["pbcopy"]
                      (.contains os "Linux")   (if (zero? (.waitFor (.exec (Runtime/getRuntime) (into-array String ["which" "wl-copy"]))))
                                                 ["wl-copy"] ["xclip" "-selection" "clipboard"])
                      :else nil))]
      (if cmd
        (let [proc (.exec (Runtime/getRuntime) (into-array String cmd))]
          (with-open [w (.getOutputStream proc)]
            (.write w (.getBytes text))
            (.flush w))
          (.waitFor proc) (zero? (.exitValue proc)))
        false))
    (catch Exception _ false)))

;; ─── File watcher ───────────────────────────────────────────────────

(defn- watch-file
  "Watch a .stratus file for changes and recompile on save."
  [in-path out-path clip?]
  (let [file (java.io.File. in-path)]
    (println "👀 Watching" in-path "for changes...")
    (println "   Press Ctrl+C to stop")
    (loop [mtime (.lastModified file)]
      (Thread/sleep 800)
      (let [new-mtime (.lastModified file)]
        (if (> new-mtime mtime)
          (let [pine-code (do (Thread/sleep 100)  ; let file finish writing
                              (gen/emit-file (reader/parse (slurp in-path))))
                ts (java.time.LocalTime/now)]
            (if out-path
              (do (spit out-path pine-code)
                  (println (str "⏱ " ts) "Compiled" in-path "→" out-path))
              (println (str "⏱ " ts)))
            (when clip?
              (if (copy-to-clipboard pine-code)
                (println "📋 Copied to clipboard")
                (println "⚠️  Clipboard copy not available on this system")))
            (recur new-mtime))
          (recur mtime))))))

;; ─── CLI ---der--- ──────────────────────────────────────────────────

(defn usage
  "Print usage information."
  []
  (println "Stratus — LISP-syntax Strategy DSL for Pine Script")
  (println)
  (println "Usage:")
  (println "  compile <file.stratus> [-o|--output <file.pine>]  Compile to Pine Script")
  (println "                            [-c|--clip]             Copy to clipboard")
  (println "  watch   <file.stratus> [-o|--output <file.pine>]  Watch for changes")
  (println "                            [-c|--clip]             Auto-copy on save")
  (println "  list                                            List available constructs")
  (println)
  (println "Quick start:")
  (println "  bb -m stratus.core compile examples/golden-cross.stratus")
  (println "  bb -m stratus.core compile examples/golden-cross.stratus -o strat.pine")
  (println "  bb -m stratus.core compile examples/golden-cross.stratus --clip")
  (println "  bb -m stratus.core watch examples/golden-cross.stratus --clip"))

(defn compile-strategy
  "Compile a .stratus file to Pine Script. Supports -o/--output and -c/--clip."
  [in-path args]
  (let [args-vec  (vec args)
        out-idx   (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["-o" "--output"])
        out-path  (when (and out-idx (< (inc out-idx) (count args-vec))) (nth args-vec (inc out-idx)))
        clip?     (some #{"-c" "--clip"} args-vec)
        source    (slurp in-path)
        ast       (reader/parse source)
        pine-code (gen/emit-file ast)]
    (if out-path
      (do (spit out-path pine-code)
          (println "✓ Compiled" in-path "→" out-path))
      (println pine-code))
    (when clip?
      (if (copy-to-clipboard pine-code)
        (println "📋 Copied to clipboard (Ctrl+V into TradingView)")
        (println "⚠️  Clipboard copy unavailable. Install xclip (Linux) or use macOS.")))))

(defn list-constructs
  "List all available DSL constructs."
  []
  (println "Available constructs (" (count constructs/constructs) "total):\n")
  (doseq [[cat-label cat-sym] [["Declarations" :decl] ["Price Sources" :builtin]
                                 ["Indicators" :indicator] ["Conditions" :condition]
                                 ["Logic / Comparison" :logic] ["Actions" :action]
                                 ["Plotting / Alerts" :plot] ["Inputs" :input]
                                 ["Math & Stats" :stat] ["Control Flow" :control]
                                 ["Drawing & Tables" :drawing]]]
    (let [cat-constructs (filter #(= (:category %) cat-sym) constructs/constructs)]
      (when (seq cat-constructs)
        (println "  " cat-label ":")
        (doseq [c cat-constructs]
          (println (str "    " (name (:name c)) "  " (or (:summary c) (:doc c) ""))))
        (println)))))

(defn -main
  "CLI entry point."
  [& args]
  (let [cmd (first args)
        rest-args (rest args)]
    (case cmd
      "compile" (let [in-path (first rest-args)]
                  (if in-path
                    (compile-strategy in-path (vec rest-args))
                    (println "Usage: bb -m stratus.core compile <file.stratus> [-o output.pine] [-c]")))

      "watch" (let [in-path (first rest-args)]
                (if in-path
                  (let [out-idx   (some #(when (#{"-o" "--output"} %) (.indexOf (vec rest-args) %)) ["-o" "--output"])
                        out-path  (when (and out-idx (< (inc out-idx) (count rest-args))) (nth rest-args (inc out-idx)))
                        clip?     (some #{"-c" "--clip"} (vec rest-args))]
                    (watch-file in-path out-path clip?))
                  (println "Usage: bb -m stratus.core watch <file.stratus> [-o output.pine] [-c]")))

      "list" (list-constructs)
      (usage))))
