.PHONY: test compile clean help all

BB = bb
SRC = src/stratus/*.clj

help:
	@echo "Stratus — LISP-Syntax Strategy DSL for Pine Script"
	@echo ""
	@echo "Targets:"
	@echo "  test       Run all tests (17 tests, 166 assertions)"
	@echo "  compile    Compile all .stratus examples to .pine"
	@echo "  quickcheck Quick sanity check on a single example"
	@echo "  clean      Remove generated .pine files"
	@echo "  help       Show this message"

all: test compile

test:
	@echo "Running all tests..."
	$(BB) -m stratus.core-test
	$(BB) -m stratus.p0p1-test
	$(BB) -m stratus.p1p2-test
	$(BB) -m stratus.remaining-test

compile:
	@echo "Compiling examples..."
	@for f in examples/*.stratus; do \
		base=$$(basename "$$f" .stratus); \
		echo "  $$base.stratus → $$base.pine"; \
		$(BB) -e "(require '[stratus.reader :as r] '[stratus.generator :as g]) \
		          (spit \"examples/$$base.pine\" \
		            (g/emit-file (r/parse (slurp \"$$f\"))))"; \
	done
	@echo "Done."

quickcheck:
	$(BB) -e "(require '[stratus.reader :as r] '[stratus.generator :as g]) \
	          (println (g/emit-file (r/parse (slurp \"examples/golden-cross.stratus\"))))" \
	| head -20

clean:
	rm -f examples/*.pine
	@echo "Cleaned generated files."
