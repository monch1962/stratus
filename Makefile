.PHONY: test compile binary vscode clean help all

BB = bb

help:
	@echo "Stratus — LISP-syntax Strategy DSL for Pine Script"
	@echo ""
	@echo "Targets:"
	@echo "  test       Run all tests (127 tests, 656 assertions)"
	@echo "  compile    Verify all .stratus examples compile"
	@echo "  binary     Set up ./stratus CLI wrapper script"
	@echo "  vscode     Validate VS Code extension JSON files"
	@echo "  clean      Remove generated .pine files"
	@echo "  help       Show this message"

all: test compile binary vscode

test:
	@echo "Running all tests..."
	$(BB) -m stratus.core-test
	$(BB) -m stratus.p0p1-test
	$(BB) -m stratus.p1p2-test
	$(BB) -m stratus.remaining-test

compile:
	@echo "Compiling examples..."
	@for f in examples/*.stratus; do \
		echo "  $$(basename $$f)"; \
		$(BB) -m stratus.core compile "$$f" -o /dev/null; \
	done
	@echo "  ✓ All examples compile"

binary:
	@chmod +x stratus
	@echo "✓ ./stratus is ready"
	@echo "  Usage: ./stratus compile examples/golden-cross.stratus --clip"

vscode:
	@python3 -m json.tool .vscode/package.json > /dev/null
	@python3 -m json.tool .vscode/syntaxes/stratus.tmLanguage.json > /dev/null
	@echo "✓ VS Code extension files valid"

clean:
	rm -f examples/*.pine
	@echo "Cleaned generated .pine files."
