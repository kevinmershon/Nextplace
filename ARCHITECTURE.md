# Nextplace Architecture

Technical documentation for developers working on Nextplace.

## Project Structure

```
Nextplace/
├── web/          # Clojure backend + Preact frontend (MVP)
│   ├── src/
│   │   ├── main/          # Production code
│   │   │   └── nextplace/
│   │   │       ├── server.clj      # Ring/Reitit HTTP server
│   │   │       ├── schema.clj      # GraphQL schema loader
│   │   │       ├── resolvers.clj   # GraphQL resolvers
│   │   │       └── db.clj          # RocksDB storage layer
│   │   ├── dev/           # Development-only code
│   │   │   └── user.clj   # REPL initialization
│   │   └── mcp/           # MCP server code
│   │       └── mcp.clj    # Claude MCP integration
│   ├── resources/
│   │   ├── schema.edn     # GraphQL schema definition
│   │   ├── config.edn     # Integrant configuration
│   │   └── public/        # Static frontend files
│   │       ├── index.html
│   │       ├── css/main.css
│   │       └── js/app.js
│   └── deps.edn           # Clojure dependencies
├── android/      # Android app (post-MVP)
├── ios/          # iOS app (post-MVP)
└── shared/       # Cross-platform utilities
```

## Build Targets

The project uses Clojure deps.edn with multiple build targets for code isolation:

### `:main` (Production)
- Paths: `["src/main" "resources"]`
- Contains server, schema, resolvers, and database code
- Used by production server and build processes

### `:dev` (Development REPL)
- Additional paths: `["src/dev"]`
- Includes Integrant REPL tools
- Provides (go), (halt), (reset) commands

### `:mcp` (MCP Server)
- Additional paths: `["src/mcp"]`
- Isolated MCP server code for IDE integration
- Uses nREPL connection

### `:fmt` (Code Formatting)
- Uses custom cljfmt fork with alignment settings
- Formats .clj, .cljc, and .edn files

## Technology Stack

### Backend
- **Language:** Clojure 1.12.0
- **HTTP Server:** Ring + Reitit
- **GraphQL:** Lacinia with EDN schema
- **Component Management:** Integrant
- **Storage:** RocksDB (embedded), Redis (caching via Carmine)
- **Logging:** SLF4J + Logback

### Frontend
- **Framework:** Preact with HTM and Signals (standalone from CDN)
- **Styling:** Custom CSS with Material Design principles
- **Icons:** Material Icons
- **Fonts:** Roboto

### Development Tools
- **Code Formatting:** cljfmt with custom alignment rules
- **REPL:** Integrant REPL for component lifecycle
- **IDE Integration:** Claude MCP server via nREPL

## Component Lifecycle

Components are managed by Integrant with dependencies declared in `config.edn`:

1. `:nextplace/db` - RocksDB database connection
2. `:nextplace/schema` - GraphQL schema (depends on db)
3. `:nextplace/server` - HTTP server (depends on schema)

Components start in order and halt in reverse order.

Example configuration:
```clojure
{:nextplace/db     {:path "data/nextplace.db"}
 :nextplace/schema {:db #ig/ref :nextplace/db}
 :nextplace/server {:schema #ig/ref :nextplace/schema
                    :port   8888
                    :env    :dev}}
```

## Development Workflow

### Quick Start

```bash
# Start REPL with dev environment
cd web
clojure -M:dev

# In REPL:
(go)    # Start server and initialize components
(halt)  # Stop server and cleanup components
(reset) # Reload code and restart
```

### Makefile Commands

From project root:

```bash
make clj/server   # Start production server (port 8888)
make clj/mcp      # Start MCP server for IDE integration
make clj/format   # Format all Clojure code and EDN files
make clj/build    # Compile code
make clj/clean    # Clean build artifacts
```

### MCP Server Setup

Add Clojure MCP to this project for IDE integration:

```bash
claude mcp add clojure "/bin/bash" -- -c "exec clojure -X:mcp"
```

## API Endpoints

- `http://localhost:8888/` - Landing page
- `http://localhost:8888/graphql` - GraphQL API
- `http://localhost:8888/playground.html` - GraphQL Playground

## GraphQL Schema

### Naming Convention
All mutations and queries use `nounVerb` naming:
- `user_signup` (not `signupUser`)
- `suggestion_accept` (not `acceptSuggestion`)
- `experience_complete` (not `completeExperience`)

### Schema Structure
Schema is defined in EDN format at `resources/schema.edn` with:
- `:enums` - Weather conditions, commitment status
- `:objects` - Location, Activity, Suggestion, User, etc.
- `:queries` - Data retrieval operations
- `:mutations` - Data modification operations
- `:input-objects` - Input types for mutations

### Resolvers
GraphQL resolvers use multimethod pattern in `resolvers.clj`:
- `resolve-query` - Query resolvers
- `resolve-mutation` - Mutation resolvers (with DB access)
- `resolver-map` - Maps GraphQL field names to resolver functions

## Database Layer

### RocksDB Storage
- Embedded key-value store at `data/nextplace.db`
- Email-keyed user profiles: `user:<email>`
- EDN serialization for values
- Integrant lifecycle management

### Operations
- `db/get-value` - Retrieve value by key
- `db/put-value` - Store value by key
- `db/delete-value` - Remove value by key

## Code Style

### Naming Conventions
- **Clojure symbols/variables:** kebab-case (`user-id`, `signed-up-at`)
- **Filenames:** snake_case (`resolvers.clj`, `schema.clj`)
- **GraphQL fields:** snake_case with nounVerb (`user_signup`, `suggestion_accept`)

### Formatting Rules
Configured in `web/cljfmt.edn`:
```clojure
{:align-binding-columns?          true
 :align-form-columns?             true
 :align-map-columns?              true
 :blank-lines-separate-alignment? true
 :indent-line-comments?           true}
```

## Production Deployment

The `-main` function in `server.clj` provides standalone server startup:
```bash
cd web
clojure -M:main
```

Server runs on port 8888 by default. Database and schema initialize automatically.

## Testing Strategy

(To be implemented)
- Unit tests for resolvers and business logic
- Integration tests for GraphQL API
- End-to-end tests for user flows

## Performance Considerations

- RocksDB provides fast embedded storage
- Redis (via Carmine) for caching layer
- Ring middleware for content-type handling
- Reitit for efficient routing

## Security

- Input validation on all GraphQL mutations
- Email format validation for user signup
- No authentication/authorization in MVP (waitlist only)
- Database path configurable for environment isolation
