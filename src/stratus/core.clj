(ns stratus.core
  (:require [stratus.reader :as reader]
            [stratus.generator :as gen]
            [stratus.constructs :as constructs]
            [clojure.string :as str])
  (:gen-class))

(declare safe-compile)

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
          (let [pine-code (do (Thread/sleep 100)
                              (safe-compile in-path))
                ts (java.time.LocalTime/now)]
            (when pine-code
              (if out-path
                (do (spit out-path pine-code)
                    (println (str "⏱ " ts) "Compiled" in-path "→" out-path))
                (println (str "⏱ " ts)))
              (when clip?
                (if (copy-to-clipboard pine-code)
                  (println "📋 Copied to clipboard")
                  (println "⚠️  Clipboard unavailable"))))
            (recur new-mtime))
          (recur mtime))))))

;; ─── Compilation with friendly errors ──────────────────────────────

(defn safe-compile
  "Compile a .stratus file, returning Pine code or printing a friendly error."
  [in-path]
  (try
    (let [source (slurp in-path)
          ast    (reader/parse source)
          pine   (gen/emit-file ast)]
      pine)
    (catch java.io.FileNotFoundException e
      (println "✕ File not found:" in-path)
      (println "  Check the path and try again.")
      nil)
    (catch Exception e
      (let [msg (.getMessage e)]
        (cond (str/includes? msg "No method")
              (println "✕ Unknown construct:" (second (re-find #":(\w+)" msg))
                       "\n  Check spelling or run `stratus list` for available constructs.")
              (str/includes? msg "EOF while reading")
              (println "✕ Unmatched parenthesis in" in-path
                       "\n  Every ( must have a matching ). Check your brackets.")
              (str/includes? msg "IndexOutOfBounds")
              (println "✕ Wrong number of arguments in" in-path
                       "\n  A construct is missing required arguments.")
              :else
              (println "✕ Compilation error:" msg))))))

;; ─── Scaffold generator ────────────────────────────────────────────

(def scaffolds
  {:strategy
   (str "(strategy \"MyStrategy\" :default-qty 100)\n\n"
        "(def fast (sma 50))\n(def slow (sma 200))\n\n"
        "(on-bar\n"
        "  (when (crosses-above fast slow) (long \"ENTER\"))\n"
        "  (when (crosses-below fast slow) (close \"EXIT\")))\n\n"
        "(plot fast \"Fast MA\" :color blue :linewidth 2)\n"
        "(plot slow \"Slow MA\" :color red :linewidth 2)\n")

   :indicator
   (str "(indicator \"MyIndicator\" :overlay false :precision 2)\n\n"
        "(input-int \"Period\" :def 14)\n"
        "(input-source \"Source\" :def close)\n\n"
        "(def val (rsi 14))\n\n"
        "(plot val \"RSI\" :color blue :linewidth 2)\n"
        "(hline 70 \"Overbought\" :color red :linestyle dashed)\n"
        "(hline 30 \"Oversold\" :color green :linestyle dashed)\n")

   :library
   (str "(library \"MyLibrary\" :overlay true)\n\n"
        "(defn my-sma [src n] (sma src n))\n"
        "(export my-sma)\n\n"
        "(defn my-ema [src n] (ema src n))\n"
        "(export my-ema)\n")})

(defn scaffold
  "Generate a scaffold .stratus file."
  [type-str name]
  (let [type (keyword type-str)]
    (if-let [template (get scaffolds type)]
      (let [fname (str (or name (str "my-" type-str)) ".stratus")
            content (str/replace template "MyS" (or name "MyS"))]
        (spit fname content)
        (println "✓ Created" fname)
        (println "  Compile: bb -m stratus.core compile" fname "--clip"))
      (println "✕ Unknown type:" type-str
               "\n  Available: strategy, indicator, library"))))

;; ─── CLI ───────────────────────────────────────────────────────────

(defn usage
  []
  (println "Stratus — LISP-syntax Strategy DSL for Pine Script")
  (println)
  (println "Usage:")
  (println "  compile <file.stratus>  [-o <file.pine>]  Compile to Pine Script")
  (println "                          [-c|--clip]       Copy to clipboard")
  (println "  watch   <file.stratus>  [-o <file.pine>]  Watch for changes")
  (println "                          [-c|--clip]       Auto-copy on save")
  (println "  new     <type> [name]                    Generate scaffold")
  (println "  list                                      List constructs")
  (println)
  (println "Types: strategy, indicator, library")
  (println)
  (println "Quick start:")
  (println "  ./stratus new strategy \"Breakout\"")
  (println "  ./stratus compile breakout.stratus --clip")
  (println "  ./stratus watch breakout.stratus --clip")
  (println "  ./stratus list"))

(defn compile-strategy
  [in-path args]
  (let [args-vec  (vec args)
        out-idx   (some #(let [i (.indexOf args-vec %)] (when (>= i 0) i)) ["-o" "--output"])
        out-path  (when (and out-idx (< (inc out-idx) (count args-vec))) (nth args-vec (inc out-idx)))
        clip?     (some #{"-c" "--clip"} args-vec)
        pine-code (safe-compile in-path)]
    (when pine-code
      (if out-path
        (do (spit out-path pine-code)
            (println "✓ Compiled" in-path "→" out-path))
        (println pine-code))
      (when clip?
        (if (copy-to-clipboard pine-code)
          (println "📋 Copied to clipboard (Ctrl+V into TradingView)")
          (println "⚠️  Clipboard copy unavailable. Install xclip (Linux) or use macOS."))))))

(defn list-constructs
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
  [& args]
  (let [cmd (first args)
        rest-args (rest args)]
    (case cmd
      "compile" (let [in-path (first rest-args)]
                  (if in-path
                    (compile-strategy in-path (vec rest-args))
                    (println "Usage: bb -m stratus.core compile <file.stratus> [-o file.pine] [-c]")))

      "watch" (let [in-path (first rest-args)]
                (if in-path
                  (let [out-idx   (some #(let [i (.indexOf (vec rest-args) %)] (when (>= i 0) i)) ["-o" "--output"])
                        out-path  (when (and out-idx (< (inc out-idx) (count rest-args))) (nth rest-args (inc out-idx)))
                        clip?     (some #{"-c" "--clip"} (vec rest-args))]
                    (watch-file in-path out-path clip?))
                  (println "Usage: bb -m stratus.core watch <file.stratus> [-o file.pine] [-c]")))

      "new" (let [type-str (first rest-args)
                  name     (second rest-args)]
              (if type-str
                (scaffold type-str name)
                (println "Usage: bb -m stratus.core new <type> [name]\n  Types: strategy, indicator, library")))

      "list" (list-constructs)
      (usage))))
