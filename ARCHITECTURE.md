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
- **Console System:** Namespace-based REPL consoles for database interrogation and admin tasks

## Component Lifecycle

Components are managed by Integrant with dependencies declared in `config.edn`:

1. `:nextplace/db` - RocksDB database connection
2. `:nextplace/redis` - Redis connection for geospatial indexing
3. `:nextplace/email` - Email service (Postmark) for magic links
4. `:nextplace/interfaces` - External interface configurations (NWS, Overpass)
5. `:nextplace/auth` - Authentication configuration (token TTL, session TTL)
6. `:nextplace/schema` - GraphQL schema (depends on db, redis, email, interfaces, auth)
7. `:nextplace/server` - HTTP server (depends on schema)

Components start in order and halt in reverse order.

Example configuration:
```clojure
{:nextplace/db     {:path "data/nextplace.db"}
 :nextplace/redis  {:uri "redis://localhost:6379"}
 :nextplace/email  {:provider :postmark
                    :from     "hello@nextplace.app"}
 :nextplace/interfaces
 {:nws      {:base-url "https://api.weather.gov"}
  :overpass {:base-url "https://overpass-api.de/api/interpreter"}}
 :nextplace/auth   {:token-ttl-minutes 15
                    :session-ttl-days  7}
 :nextplace/schema {:db         #ig/ref :nextplace/db
                    :redis      #ig/ref :nextplace/redis
                    :email      #ig/ref :nextplace/email
                    :interfaces #ig/ref :nextplace/interfaces
                    :auth       #ig/ref :nextplace/auth}
 :nextplace/server {:schema #ig/ref :nextplace/schema
                    :port   8888
                    :env    :dev}}
```

## Current Implementation Status

See [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md) for detailed progress tracking, completed features, pending work, and lessons learned.

## Development Workflow

### Prerequisites

1. **Redis** - Required for geospatial indexing and caching:
```bash
docker run -d -p 6379:6379 --name nextplace-redis redis:latest
```

2. **Environment Variables** - Copy `.env.example` to `.env` and configure:
```bash
cd web
cp .env.example .env
# Edit .env with your API keys
```

Required environment variables:
- `POSTMARK_API_KEY` - For sending magic link emails
- `JWT_SECRET` - 256-bit secret for JWT signing (generate with `openssl rand -hex 32`)
- `BASE_URL` - Base URL for magic link generation (default: `http://localhost:8888`)
- `REDIS_URL` - Redis connection URL (default: `redis://localhost:6379`)

### Quick Start

```bash
# Start REPL with dev environment
cd web
clojure -M:dev

# In REPL:
(go)    # Start server and initialize components
(halt)  # Stop server and cleanup components
(reset) # Reload code and restart

# Console commands:
(commands)           # List available commands
(select :rocksdb)    # Enter RocksDB console
  # Now in nextplace.console.rocksdb namespace
  (commands)         # List RocksDB console commands
  (list-keys)        # List all keys
  (list-keys "user:") # List keys with prefix
  (get-value "user:test@example.com") # Get value
  (scan "auth_token:") # Scan and show entries
  (count-keys)       # Count all keys
  (stats)            # Show database statistics
  (back)             # Return to user namespace
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

Resolvers are organized by domain in subdirectories:
- `resolvers/queries/` - Query resolver implementations by domain
  - `suggestion.clj`, `weather.clj`, `social.clj`, `user.clj`, `experience.clj`
- `resolvers/mutations/` - Mutation resolver implementations by domain
  - `auth.clj`, `suggestion.clj`, `social.clj`, `experience.clj`

## Console System

The console system provides namespace-based REPL interfaces for database interrogation and administrative tasks.

### Architecture
- **Console Registry** (`nextplace.console.util`) - Manages available consoles and provides introspection utilities
- **Console Namespaces** - Each console is a full Clojure namespace with public functions
- **Navigation** - Switch between consoles using `(select :console-kw)` and return with `(back)`
- **Self-Documenting** - Each console implements `(commands)` to list available operations

### Available Consoles
- **:rocksdb** (`nextplace.console.rocksdb`) - RocksDB database operations

### Usage Pattern
```clojure
;; From user namespace
(select :rocksdb)              ; Enter RocksDB console

;; Now in nextplace.console.rocksdb namespace
(commands)                     ; See available commands
(list-keys "user:")            ; Call functions directly
(get-value "user:foo@bar.com") ; No namespace prefix needed
(back)                         ; Return to user namespace
```

### Adding New Consoles
1. Create namespace under `src/dev/nextplace/console/`
2. Implement public functions for console operations
3. Implement `(commands)` function using `console.util/list-public-vars`
4. Add `(set-top-level-ns! [ns-sym])` and `(back)` for navigation
5. Register in `nextplace.console.util/available-consoles`

## Database Layer

### RocksDB Storage
- Embedded key-value store at `data/nextplace.db`
- EDN serialization for all values
- Integrant lifecycle management

#### Storage Patterns
- `user:<email>` - User profiles with fields: `id`, `email`, `name`, `signed_up_at`, `flow3_unlocked`, `completed_experiences_count`
- `auth_token:<token>` - Authentication tokens with fields: `token`, `email`, `created_at`, `expires_at`, `used`

All persisted values use snake_case field names for consistency.

### Operations
- `db/get-value` - Retrieve value by key
- `db/put-value` - Store value by key
- `db/delete-value` - Remove value by key

## Code Style

### Naming Conventions
- **Persisted data (RocksDB values):** snake_case (`signed_up_at`, `flow3_unlocked`, `expires_at`) - **CRITICAL:** All persisted data uses snake_case to ensure consistency in serialization
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

## Maps and Location Architecture

### ⚠️ CRITICAL: No In-App Maps (PERMANENT CONSTRAINT)
**Design principle: No map rendering in the application. Ever.**

**This is a permanent architectural constraint. Do not add:**
- ❌ Google Maps SDK (Android/iOS)
- ❌ Map rendering libraries (Leaflet, Mapbox, etc.)
- ❌ Static map image generation
- ❌ Map tiles or embedded maps
- ❌ `map_image_url` or similar fields in GraphQL schema

**Backend:**
- Google Maps API key used for geocoding and place lookups only
- Returns location data: coordinates, name, address
- No map image generation

**Mobile Apps:**
- Display location name, address, distance
- Display meeting point instructions (e.g., "By the main fountain")
- "Open in Maps" button creates deep link to device's native map app
- Deep link uses `meeting_point_latitude/longitude` if available, else general `latitude/longitude`
- Android: `geo:` URI scheme or Google Maps intent
- iOS: Apple Maps URL scheme
- No map rendering, no Maps SDK dependencies

**Deep Link Pattern:**
```kotlin
// Android - opens in user's preferred map app
// Uses precise meeting point if available
fun openInMaps(location: Location) {
    val lat = location.meetingPointLatitude ?: location.latitude
    val lng = location.meetingPointLongitude ?: location.longitude
    val name = location.name

    val uri = "geo:$lat,$lng?q=$lat,$lng($name)"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    context.startActivity(intent)
}
```

```swift
// iOS - opens in Apple Maps
func openInMaps(latitude: Double, longitude: Double, name: String) {
    let url = "http://maps.apple.com/?ll=\(latitude),\(longitude)&q=\(name)"
    UIApplication.shared.open(URL(string: url)!)
}
```

**Benefits:**
- No API key in mobile apps
- Uses device's preferred navigation app
- Zero map rendering complexity
- Smaller app binary size
- Native map experience users already know

---

## Performance Considerations

- RocksDB provides fast embedded storage
- Redis (via Carmine) for caching layer and geospatial queries
- Ring middleware for content-type handling
- Reitit for efficient routing
- Geocoding result caching reduces Google Maps API calls

## Authentication

### Magic Link Flow
1. User requests authentication with email via `user_auth_request` mutation
2. System generates cryptographically random 256-bit token (15-minute TTL)
3. Token stored in RocksDB with email and expiration
4. Magic link email sent via Postmark containing authentication URL
5. User clicks link, token verified via `user_auth_verify` mutation
6. Token marked as used (single-use only)
7. JWT session token created (7-day TTL) and returned to client
8. User created automatically if doesn't exist

### JWT Sessions
- HS256 signing algorithm with JWT_SECRET from environment
- 7-day expiration (configurable via `session-ttl-days`)
- Contains user email and ID in claims
- Client includes session token in GraphQL context for authenticated operations

### Security Considerations
- Tokens are single-use and expire after 15 minutes
- Email-based passwordless authentication reduces credential exposure
- JWT secrets must be 256-bit minimum
- Input validation on all GraphQL mutations
- Email format validation for user operations
- Database path configurable for environment isolation
