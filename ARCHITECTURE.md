# Nextplace Architecture

## Project Structure

```
Nextplace/
├── web/              # Clojure backend + Preact frontend (MVP)
├── android/          # Android application (post-MVP)
├── ios/              # iOS application (post-MVP)
├── shared/           # Cross-platform utilities and shared logic
└── Makefile          # Build automation
```

## Web Application Architecture

### Directory Organization

```
web/
├── src/
│   ├── main/         # Production code (always on classpath)
│   ├── dev/          # REPL utilities and development helpers (:dev alias)
│   └── mcp/          # MCP server integration (:mcp alias)
├── resources/
│   ├── public/       # Static frontend assets (HTML, CSS, JS)
│   ├── schema.edn    # GraphQL schema definition
│   └── config.edn    # Integrant component configuration
├── test/             # Test suites (:test alias)
├── deps.edn          # Clojure dependencies and build aliases
└── cljfmt.edn        # Code formatting configuration
```

### Build Target Separation

**Production** (`src/main/`)
- Core application logic
- GraphQL resolvers and schema loader
- HTTP server with Pedestal
- Always included on classpath

**Development** (`src/dev/`)
- REPL initialization (user.clj)
- Integrant workflow helpers (go, halt, reset)
- Development utilities
- Only loaded with `:dev` alias

**MCP Server** (`src/mcp/`)
- nREPL connection management
- MCP protocol integration
- Only loaded with `:mcp` alias
- Isolated from production builds

### Technology Stack

**Backend:**
- Clojure 1.12.0
- Lacinia GraphQL (schema + resolvers)
- Pedestal HTTP server (with static file serving)
- Integrant (component lifecycle)
- core.async (asynchronous workflows)
- Claypoole (parallel processing)
- Muuntaja (content negotiation)

**Storage:**
- RocksDB (embedded key-value store)
- Redis via Carmine (caching, sessions)

**Frontend:**
- Preact with HTM and Signals (standalone, CDN-loaded)
- Served from `resources/public/`

**Development:**
- Integrant REPL workflow
- cljfmt for code formatting
- MCP server for IDE integration

### Component Management

Components defined in `resources/config.edn` using Integrant:

```clojure
{:nextplace/schema  {}
 :nextplace/server  {:schema #ig/ref :nextplace/schema
                     :port   8888
                     :env    :dev}}
```

Lifecycle methods:
- `ig/init-key` - Component initialization
- `ig/halt-key!` - Component shutdown

### API Endpoints

- `/` - Frontend application
- `/graphql` - GraphQL API endpoint
- `/playground.html` - GraphQL Playground IDE

### Development Workflow

1. Start REPL: `clojure -M:dev`
2. Load user namespace (automatic)
3. Start system: `(go)`
4. Develop with hot reload: `(reset)`
5. Stop system: `(halt)`

### Code Organization Principles

**Namespace Structure:**
- `nextplace.server` - HTTP server and lifecycle
- `nextplace.schema` - GraphQL schema compilation
- `nextplace.resolvers` - GraphQL query/mutation resolvers
- `user` - REPL initialization and helpers
- `mcp` - MCP server connection

**Dependency Flow:**
- Configuration → Schema → Server
- Integrant manages initialization order
- Components reference dependencies via `#ig/ref`

### Build Commands

From project root:

```bash
make clj/server   # Production server (uses :main alias)
make clj/mcp      # MCP server (uses :mcp alias)
make clj/format   # Format all Clojure and EDN files
make clj/build    # AOT compilation
make clj/clean    # Remove build artifacts
```

### Configuration Management

**Environment-specific:**
- Development: `resources/config.edn` with `:env :dev`
- Production: Programmatic config in `-main` with `:env :prod`

**Formatting:**
- `cljfmt.edn` controls code style
- Alignment enabled for bindings, forms, and maps

### Future Extensions

**Android/iOS Apps:**
- Shared GraphQL endpoint
- Platform-specific native implementations
- Common business logic in `shared/`

**Shared Module:**
- GraphQL queries and mutations
- Data transformation utilities
- Common validation logic
