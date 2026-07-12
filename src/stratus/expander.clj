(ns stratus.expander
  "AST-level expansion for Clojure-inspired features.
   Expands let, ->, ->>, and cond into standard Stratus forms
   before the generator sees them."
  (:require [clojure.string :as str]))

(defn walk-subst
  "Walk a form, replacing symbols from subst map.
   Stops at inner let boundaries (preserves binding vectors for shadowing)."
  [f subst]
  (cond
    (and (list? f) (= 'let (first f)))
    (cons 'let (cons (second f) (map #(walk-subst % subst) (nthrest f 2))))
    (list? f)
    (map #(walk-subst % subst) f)
    (vector? f)
    (mapv #(walk-subst % subst) f)
    (and (symbol? f) (contains? subst f))
    (get subst f)
    :else f))

(defn expand-thread-first
  "Expand (-> x (f a) (g b)) → (g (f x a) b)."
  [form]
  (let [[_ initial & steps] form]
    (reduce (fn [acc step]
              (if (list? step)
                (cons (first step) (cons acc (rest step)))
                (list step acc)))
            initial steps)))

(defn expand-thread-last
  "Expand (->> x (f a) (g b)) → (g b (f a x))."
  [form]
  (let [[_ initial & steps] form]
    (reduce (fn [acc step]
              (if (list? step)
                (concat step [acc])
                (list step acc)))
            initial steps)))

(defn expand-cond
  "Expand (cond (> x 0) :long (< x 0) :short :else :flat)
   → (if (> x 0) :long (if (< x 0) :short :flat))."
  [form]
  (let [pairs (partition 2 (rest form))]
    (loop [remaining pairs, result nil]
      (if (empty? remaining)
        result
        (let [[test expr] (first remaining)]
          (if (and (= :else test) (nil? result))
            expr
            (let [branch (if (= :else test) expr (list 'if test expr))
                  combined (if result
                            (clojure.walk/postwalk
                              (fn [node]
                                (if (and (list? node) (= 'if (first node)) (= 3 (count node)))
                                  (list 'if (nth node 1) (nth node 2) branch) node))
                              result)
                            branch)]
              (recur (rest remaining) combined))))))))

(defn expand-form
  "Expand a single form, recursing into sub-forms."
  [form]
  (letfn [(expand-let [f]
            (let [[_ bindings & body] f
                  expanded-bindings (mapv expand-form bindings)
                  subst (apply hash-map expanded-bindings)
                  substituted (if (= 1 (count body))
                                (walk-subst (first body) subst)
                                (cons 'do (map #(walk-subst % subst) body)))]
              substituted))]
    (if (list? form)
      (let [head (first form)]
        (cond
          (= head 'let)   (expand-form (expand-let form))
          (= head '->>)   (expand-thread-last form)
          (= head '->)    (expand-thread-first form)
          (= head 'cond)  (expand-cond form)
          :else (map expand-form form)))
      form)))

(defn expand-all
  "Expand all forms in a collection (vector of top-level forms)."
  [forms]
  (mapv expand-form forms))
