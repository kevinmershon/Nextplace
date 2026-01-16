# Nextplace Makefile
# NOTE: This project uses a single Makefile at the root level only.
#       Do not create Makefiles in subdirectories.

.PHONY: clj/clean clj/build clj/format clj/check clj/server clj/mcp clj/repl clj/dev clj/test clj/test-unit clj/test-live mcp/build mcp/setup

# =============================================================================
# Clojure Web Backend
# =============================================================================

# Clean build artifacts
clj/clean:
	cd web && rm -rf .cpcache target classes

# Compile the server
clj/build:
	cd web && clojure -M -e "(compile 'nextplace.server)"

# Format code
clj/format:
	cd web && clojure -M:fmt fix src test resources/*.edn deps.edn

# Check formatting without fixing
clj/check:
	cd web && clojure -M:fmt check src test resources

# Run production server
clj/server:
	cd web && clojure -M:main

# Run MCP server
clj/mcp:
	cd web && clojure -M:mcp

# Start REPL with dev profile
clj/repl:
	cd web && clojure -M:dev

# Start development server
clj/dev:
	cd web && clojure -M:dev -e "(require 'dev) (dev/go)"

# Run all tests
clj/test:
	cd web && clojure -M:test -e "(require 'nextplace.events-test 'nextplace.discovery-test) \
		(clojure.test/run-tests 'nextplace.events-test 'nextplace.discovery-test)"

# Run only unit tests (no live HTTP calls)
clj/test-unit:
	cd web && clojure -M:test -e "(require 'nextplace.events-test 'nextplace.discovery-test) \
		(clojure.test/run-tests 'nextplace.events-test 'nextplace.discovery-test)" \
		2>&1 | grep -v "^=== LITMUS"

# Run live integration tests (hits external APIs)
clj/test-live:
	cd web && clojure -M:test -e "(require 'nextplace.events-test) \
		(clojure.test/test-vars [#'nextplace.events-test/red-rock-coffee-open-mic-litmus \
		                         #'nextplace.events-test/seven-stars-karaoke-litmus])"

# =============================================================================
# MCP Server (Rust)
# =============================================================================

# Build the MCP server
mcp/build:
	cd nextplace-mcp && cargo build --release

# Generate .mcp.json for local development
# This file is gitignored and contains machine-specific paths
mcp/setup:
	@echo '{"mcpServers":{"nextplace":{"command":"$(CURDIR)/nextplace-mcp/target/release/nextplace-mcp","env":{"NEXTPLACE_DB_PATH":"$(CURDIR)/web/data/nextplace.db"}}}}' > .mcp.json
	@echo "Created .mcp.json - restart Claude Code to use the MCP server"
