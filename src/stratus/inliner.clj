(ns stratus.inliner
  "Inline function expansion for Stratus DSL.
   (definline name [params] body...)
   is replaced at compile-time by expanding calls to name with positional
   parameter substitution on the body forms."
  (:require [clojure.string :as str]))

(declare expand-all)

(defn definline? [form]
  "True if form is a (definline ...) definition."
  (and (list? form) (= 'definline (first form))))

(defn collect-definitions
  "Scan a list of top-level forms and return a map of
   {symbol {:params [params] :body [form1 form2 ...]}}
   containing only the definline definitions. Non-definline forms are ignored."
  [forms]
  (reduce (fn [acc form]
            (if (definline? form)
              (let [[_ name params & body] form]
                (assoc acc name {:params (vec params), :body (vec body)}))
              acc))
          {} forms))

(defn- substitute-params
  "Given a body form, a vector of param symbols, and a vector of arg forms,
   replace each occurrence of a param symbol with the corresponding arg.
   Returns the substituted form (not a string)."
  [form params args]
  (let [subst (zipmap params args)]
    (clojure.walk/postwalk
      (fn [node]
        (if (and (symbol? node) (contains? subst node))
          (get subst node)
          node))
      form)))

(defn expand-call
  "If form is a call to an inline function (i.e., (list? form) and the head
   symbol is in definline-map), expand it by substituting params with args
   in the body. Multi-statement bodies are wrapped in (do ...).
   Non-inline forms are returned unchanged."
  [form definline-map]
  (if (and (list? form) (symbol? (first form)))
    (let [head (first form)]
      (if-let [defn-entry (get definline-map head)]
        (let [{:keys [params body]} defn-entry
              args (rest form)
              substituted (mapv #(substitute-params % params args) body)]
          (if (= 1 (count substituted))
            (first substituted)
            (cons 'do substituted)))
        form))
    form))

(defn expand-all
  "Full inline expansion pipeline:
   1. Collect all (definline ...) definitions from the forms list
   2. Recursively expand inline calls throughout the entire AST tree
   3. Remove definline definitions from the output
   4. Return the remaining forms with calls expanded everywhere"
  [forms]
  (let [defs (collect-definitions forms)
        ;; Expand nested: iterate to handle chains like A→B→C
        expanded-defs (loop [prev {} current defs]
                        (if (= prev current)
                          current
                          (recur current
                            (reduce-kv (fn [acc name {:keys [params body]}]
                                         (assoc acc name
                                           {:params params
                                            :body (mapv #(expand-call % current) body)}))
                                       {} current))))
        ;; Walk the entire AST, expanding inline calls at every level
        expand-walk (fn expand-walk [node]
                      (if (list? node)
                        (let [expanded (expand-call node expanded-defs)]
                          ;; After expanding the call, recursively walk
                          ;; the result (in case of nested inline calls)
                          (if (not= expanded node)
                            (expand-walk expanded)
                            (map expand-walk node)))
                        node))
        ;; Remove definline definitions and walk remaining forms
        non-def-forms (remove definline? forms)]
    (mapv expand-walk non-def-forms)))
