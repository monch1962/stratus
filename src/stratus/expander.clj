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

(defn expand-some-thread-first
  "Expand (some-> x (f a) (g b)) → nested let/if with na check at each step.
   (let [g1 x] (if (na g1) na (let [g2 (f g1 a)] (if (na g2) na (g b g2)))))"
  [form]
  (let [[_ initial & steps] form]
    (reduce (fn [acc step]
              (let [g (gensym "s")]
                (list 'let [g acc]
                      (list 'if (list 'na g) 'na
                            (if (list? step)
                              (cons (first step) (cons g (rest step)))
                              (list step g))))))
            initial steps)))

(defn expand-some-thread-last
  "Expand (some->> x (f a) (g b)) → nested let/if with na check, thread-last insertion."
  [form]
  (let [[_ initial & steps] form]
    (reduce (fn [acc step]
              (let [g (gensym "s")]
                (list 'let [g acc]
                      (list 'if (list 'na g) 'na
                            (if (list? step)
                              (concat step [g])
                              (list step g))))))
            initial steps)))

(defn- subst-symbol
  "Replace all occurrences of a symbol in a form with a replacement."
  [form sym replacement]
  (cond
    (list? form) (map #(subst-symbol % sym replacement) form)
    (vector? form) (mapv #(subst-symbol % sym replacement) form)
    (= form sym) replacement
    :else form))

(defn expand-cond-thread-first
  "Expand (cond-> x test1 step1 test2 step2) →
   nested let/if: each step applies only when its test is truthy,
   otherwise the accumulated value passes through unchanged."
  [form]
  (let [[_ initial & clauses] form]
    (loop [acc initial, clauses clauses]
      (if (empty? clauses)
        acc
        (let [test (first clauses)
              step (second clauses)
              g (gensym "c")]
          (recur
            (list 'let [g acc]
                  (list 'if test
                        (if (list? step)
                          (cons (first step) (cons g (rest step)))
                          (list step g))
                        g))
            (drop 2 clauses)))))))

(defn expand-as-thread
  "Expand (as-> expr name step1 step2 ...) →
   threads expr through each step, substituting name with the
   accumulated value at each position."
  [form]
  (let [[_ initial name & steps] form]
    (reduce (fn [acc step]
              (subst-symbol step name acc))
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
                  pairs (partition 2 expanded-bindings)
                  ;; Regular pairs: [sym expr] — symbols get substituted
                  regular-pairs (remove #(vector? (first %)) pairs)
                  ;; Destructuring pairs: [[syms] expr] — become multiset
                  dest-pairs (filter #(vector? (first %)) pairs)
                  subst (apply hash-map (mapcat identity regular-pairs))
                  ;; Prepend multiset forms for destructured bindings
                  preamble (map (fn [[names expr]]
                                  (list 'multiset (vec names) expr))
                                dest-pairs)
                  full-body (concat preamble body)]
              (if (= 1 (count full-body))
                (walk-subst (first full-body) subst)
                (cons 'do (map #(walk-subst % subst) full-body)))))]
    (if (list? form)
      (let [head (first form)]
        (cond
          (= head 'let)   (expand-form (expand-let form))
          (= head '->>)   (expand-thread-last form)
          (= head '->)    (expand-thread-first form)
          (= head 'some->) (expand-some-thread-first form)
          (= head 'some->>) (expand-some-thread-last form)
          (= head 'cond->) (expand-cond-thread-first form)
          (= head 'as->) (expand-as-thread form)
          (= head 'cond)  (expand-cond form)
          :else (map expand-form form)))
      form)))

(defn expand-all
  "Expand all forms in a collection (vector of top-level forms)."
  [forms]
  (mapv expand-form forms))
