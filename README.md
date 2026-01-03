# Nextplace

Spontaneous event and place discovery for people with abundant free time but limited social momentum.

## Project Structure

```
Nextplace/
├── web/          # Clojure backend + Preact frontend (MVP)
├── android/      # Android app (post-MVP)
├── ios/          # iOS app (post-MVP)
└── shared/       # Cross-platform utilities
```

## Web Development

### Quick Start

```bash
# Start REPL with dev environment
cd web
clojure -M:dev

# In REPL:
(go)    # Start server
(halt)  # Stop server
(reset) # Reload and restart
```

### Makefile Commands

From project root:

```bash
make clj/server   # Start production server
make clj/mcp      # Start MCP server (dev)
make clj/format   # Format code
make clj/build    # Compile code
make clj/clean    # Clean build artifacts
```

### MCP Server Setup

Add Clojure MCP to this project:

```bash
claude mcp add clojure "/bin/bash" -- -c "exec clojure -X:mcp"
```

### Endpoints

- `http://localhost:8888/` - Frontend
- `http://localhost:8888/graphql` - GraphQL API
- `http://localhost:8888/playground.html` - GraphQL Playground

## Core Flows

1. **Place + Activity Discovery** - Move me somewhere with something to do
2. **Weather-Driven Escapes** - Change how it feels by traveling to different weather
3. **Small-Group Social Encounters** - Optional low-pressure social interaction anchored to activities

## Tech Stack

**Backend:**
- Clojure with deps.edn
- Lacinia GraphQL
- Pedestal HTTP server
- Integrant component management
- RocksDB + Redis for storage

**Frontend:**
- Preact with HTM and Signals (standalone)
- Served from CDN

See [PROJECT-PLAN.md](PROJECT-PLAN.md) and [CONSIDERATIONS.md](CONSIDERATIONS.md) for detailed product specification.
