.PHONY: clj/clean clj/build clj/format clj/server clj/mcp

clj/clean:
	cd web && rm -rf .cpcache target classes

clj/build:
	cd web && clojure -M -e "(compile 'nextplace.server)"

clj/format:
	cd web && clojure -M:fmt fix src test deps.edn resources/schema.edn resources/config.edn

clj/server:
	cd web && clojure -M:main

clj/mcp:
	cd web && clojure -M:mcp
