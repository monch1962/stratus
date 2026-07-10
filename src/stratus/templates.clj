(ns stratus.templates
  "Pine Script v6 boilerplate templates.
   These produce the structural scaffolding around generated strategy/indicator code."
  (:require [clojure.string :as str]))

(defn strategy-header
  "Generate a strategy() declaration line with all parameters."
  [name {:keys [default-qty commission pyramiding initial-capital currency]
         :or {default-qty 100, commission 0.1, pyramiding 1}}]
  (let [params (str/join ", "
                (filter some?
                  [(when default-qty
                     (str "default_qty_type=strategy.percent_of_equity, default_qty_value=" default-qty))
                   (when commission (str "commission_value=" commission))
                   (str "pyramiding=" pyramiding)
                   (when initial-capital (str "initial_capital=" initial-capital))
                   (when currency (str "currency=\"" currency "\""))]))]
    (str "strategy(\"" name "\"" (when (seq params) (str ", " params)) ")")))

(defn indicator-header
  "Generate an indicator() declaration line."
  [name {:keys [overlay precision format scale minline maxline]
         :or {overlay true, precision 4}}]
  (let [params (str/join ", "
                (filter some?
                  [(str "overlay=" (if overlay "true" "false"))
                   (str "precision=" precision)
                   (when format (str "format=" (name format)))
                   (when scale (str "scale=" (name scale)))
                   (when minline (str "minline=" minline))
                   (when maxline (str "maxline=" maxline))]))]
    (str "indicator(\"" name "\"" (when (seq params) (str ", " params)) ")")))

(defn version-line
  "Generate the Pine Script version comment."
  []
  "//@version=6")

(defn comment-block
  "Generate a block of comments."
  [& lines]
  (str/join "\n" (map #(str "// " %) lines)))

(defn empty-line
  "Generate a blank line for spacing."
  []
  "")
