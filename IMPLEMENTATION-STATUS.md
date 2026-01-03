# Nextplace — Implementation Status

This document tracks current implementation progress, technical decisions, and completed features.

---

## Current Status

**Phase:** MVP Foundation - Authentication and Infrastructure
**Last Updated:** 2026-01-03

---

## Completed Features

### ✅ Authentication System (Phase 1)
**Status:** Complete

- Magic link email authentication via Postmark
- Cryptographically secure 256-bit token generation (15-minute TTL)
- Single-use token validation with expiration checking
- JWT session tokens (7-day TTL) with HS256 signing
- Automatic user creation on first authentication
- GraphQL mutations: `user_auth_request`, `user_auth_verify`

**Implementation Details:**
- `nextplace.auth` - Token generation, validation, JWT creation
- `nextplace.email` - Postmark SMTP integration with HTML templates
- RocksDB storage for authentication tokens
- Environment-based configuration (JWT_SECRET, POSTMARK_API_KEY, BASE_URL)

### ✅ Database Layer
**Status:** Complete

- RocksDB embedded key-value store
- EDN serialization for all stored data
- Integrant lifecycle management
- Console system for REPL-based interrogation
- Basic operations: get, put, delete

**Storage Patterns:**
- `user:<email>` - User profiles
- `auth_token:<token>` - Authentication tokens

### ✅ GraphQL API Foundation
**Status:** Complete

- Lacinia GraphQL with EDN schema
- Ring/Reitit HTTP server on port 8888
- Resolver organization by domain (queries/ and mutations/)
- Schema-first development approach
- Naming convention: `nounVerb` for all mutations/queries

**Implemented Mutations:**
- `user_signup` - Create new user account
- `user_auth_request` - Request magic link
- `user_auth_verify` - Verify token and create session

**Implemented Queries:**
- `user_profile` - Get current user (stub)
- `experience_history` - Get past experiences (stub)
- `current_suggestion` - Get suggestion (stub)
- `weather_escape` - Get weather escape (stub)
- `available_social_events` - Get social events (stub)

### ✅ Development Infrastructure
**Status:** Complete

- Integrant REPL for component lifecycle
- Console system for database interrogation
  - Namespace-based consoles (`:rocksdb`)
  - Self-documenting with `(commands)` function
  - Navigation with `(select :console)` and `(back)`
- Code formatting with cljfmt (custom alignment rules)
- Makefile build targets

**Available Consoles:**
- `:rocksdb` - Database operations (list-keys, get-value, scan, stats, etc.)

### ✅ Component Architecture
**Status:** Complete

- Integrant-based dependency injection
- Six managed components:
  1. `:nextplace/db` - RocksDB connection
  2. `:nextplace/redis` - Redis connection (prepared)
  3. `:nextplace/email` - Email service
  4. `:nextplace/interfaces` - External API configs (NWS, Overpass)
  5. `:nextplace/auth` - Authentication config
  6. `:nextplace/schema` - GraphQL schema with all dependencies
  7. `:nextplace/server` - HTTP server

---

## Data Conventions

### Naming Standards
- **Persisted data:** snake_case (e.g., `signed_up_at`, `flow3_unlocked`)
- **GraphQL fields:** snake_case with nounVerb pattern (e.g., `user_signup`, `suggestion_accept`)
- **Clojure symbols:** kebab-case (e.g., `user-id`, `signed-up-at`)
- **Filenames:** snake_case (e.g., `resolvers.clj`, `auth.clj`)

### User Model
```clojure
{:id                          "uuid"
 :email                       "user@example.com"
 :name                        "User Name"
 :signed_up_at                "2026-01-03T12:00:00Z"
 :flow3_unlocked              false
 :completed_experiences_count 0
 :active_event_count          0}
```

### Authentication Token Model
```clojure
{:token      "256-bit-hex-token"
 :email      "user@example.com"
 :created_at "2026-01-03T12:00:00Z"
 :expires_at "2026-01-03T12:15:00Z"
 :used       false}
```

---

## In Progress

### 🔄 External Integrations
**Status:** Scaffolded

- Redis connection prepared for geospatial queries
- NWS Weather API configuration added
- Overpass API configuration added
- Google Maps APIs (planned)
- Eventbrite API (planned)

---

## Pending Implementation

### 📋 Location Data Model
**Status:** Schema defined, implementation pending

Location attributes to track:
- Price range (FREE, $, $$, $$$, $$$$)
- Serves alcohol (boolean)
- Serves non-alcoholic drinks (boolean)
- Serves food (boolean)
- Dog friendly (boolean)
- Parking location (closest garage/lot)
- Parking price (or FREE)

### 📋 Flow 1: Place + Activity Discovery
**Dependencies:** Location indexing, external event APIs

- Location discovery and indexing
- Activity pairing logic using location attributes
- Suggestion generation algorithm
- Novelty tracking
- Acceptance/regeneration flow

### 📋 Flow 2: Weather-Driven Escapes
**Dependencies:** NWS integration, geospatial queries

- Current weather detection
- Microclimate identification (fog/sun/wind/temperature)
- Drive time calculation
- Weather-activity coupling
- Escape suggestion generation

### 📋 Flow 3: Small-Group Social Encounters
**Dependencies:** Flows 1 & 2, user unlock system

**Event Management:**
- Event creation from suggestions and location browse
- "Check it out" button creates user-hosted events
- Slot management (2-6 participants)
- Timing constraints (4 hours min, 2 days max from current time)
- Commitment tracking with 2-event limit per user
- Host tracking and event ownership

**Social Filtering:**
- Pairwise rating system
- Bidirectional blacklisting (if A blocks B, neither sees other's events)
- Transitive event filtering (blocked users can't join same events)
- No-show detection

**Privacy:**
- No event browsing page (only location browse)
- No user profile discovery
- Users visible only in event chat and post-event ratings

### 📋 Experience Reflection
**Dependencies:** Flows 1, 2, 3

- Post-experience prompt
- Rating capture
- Free-text reflection storage
- Participant rating (Flow 3)

### 📋 Frontend Application
**Status:** Minimal landing page only

- Preact + HTM + Signals implementation
- GraphQL client integration
- Core surfaces (Home, Commitment, Reflection, History)
- Mobile-first responsive design

---

## Technical Decisions

### Database Strategy
- **RocksDB** for primary storage (embedded, fast, simple)
- **Redis** for geospatial indexing (GEORADIUS for location queries)
- No PostgreSQL or traditional RDBMS (reduces operational complexity)

### Authentication Approach
- Passwordless magic links (reduces friction, improves security)
- JWT sessions (stateless, scalable)
- No OAuth providers in MVP (simpler onboarding)

### API Design
- GraphQL over REST (flexible client queries, single endpoint)
- Schema-first with EDN (better tooling for Clojure)
- Mutations use nounVerb naming (consistent with product language)

### Code Organization
- Resolvers organized by domain (queries/, mutations/)
- Each flow can have dedicated resolver namespaces
- Console system allows new admin tools without cluttering main code

### Environment Management
- Environment variables for secrets (JWT_SECRET, API keys)
- Docker for Redis (simple local development)
- No environment-specific builds (configuration-driven)

---

## Cost Estimates (MVP)

Based on current architecture:

- **Postmark:** Free tier (100 emails/month) or $15/month (10k emails)
- **Redis:** Self-hosted via Docker (free) or managed ~$15/month
- **NWS Weather API:** Free (public)
- **Overpass API:** Free (OpenStreetMap)
- **Google Maps APIs:** ~$0-50/month depending on usage
- **Hosting:** TBD (likely $5-20/month for simple VPS)

**Total estimated:** ~$15-50/month for MVP scale

---

## Development Workflow

### Starting the REPL
```bash
cd web
clojure -M:dev

# In REPL:
(go)                  # Load components and start server
(select :rocksdb)     # Enter database console
(commands)            # See available commands
(back)                # Return to user namespace
(halt)                # Stop server
(reset)               # Reload and restart
```

### Running Production Server
```bash
make clj/server       # Starts on port 8888
```

### Code Formatting
```bash
make clj/format       # Format all Clojure and EDN files
```

---

## Next Steps

1. **Location Indexing System**
   - Implement location discovery via Overpass API
   - Store locations in RocksDB with geospatial metadata
   - Create Redis GEORADIUS integration for proximity queries

2. **Weather Integration**
   - NWS API client implementation
   - Weather condition classification (fog, sun, wind, etc.)
   - Microclimate detection logic

3. **Suggestion Algorithm (Flow 1)**
   - Place selection based on novelty and distance
   - Activity pairing based on place characteristics
   - Time window generation

4. **Frontend Scaffolding**
   - GraphQL client setup
   - Authentication flow UI
   - Suggestion card component
   - Commitment screen

5. **Experience Tracking**
   - Experience completion mutation
   - Reflection capture
   - Rating system

---

## Lessons Learned

### What Works
- **Magic links:** Simpler than password management, users understand the flow
- **Console system:** REPL-based database interrogation is powerful for debugging
- **Snake_case everywhere:** Consistency in persisted data prevents bugs
- **Integrant:** Component lifecycle makes REPL development smooth
- **Schema-first GraphQL:** EDN schema is easier to maintain than code-based schemas

### What Needed Adjustment
- **Component requires:** Had to explicitly require all component namespaces in REPL `(go)` function for Integrant to find lifecycle methods
- **RocksDB direct access:** Console needed to handle raw RocksDB instance, not wrapped object
- **Resolver organization:** Initial single-file approach didn't scale; domain-based split much cleaner

### Open Questions
- How aggressive should weather escape suggestions be?
- Should Flow 3 unlock after 1 completion or multiple?
- What's the right balance between novelty and safety in suggestion algorithm?

---

## References

- [PROJECT-PLAN.md](PROJECT-PLAN.md) - Product vision and flow definitions
- [CONSIDERATIONS.md](CONSIDERATIONS.md) - Design philosophy and principles
- [ARCHITECTURE.md](ARCHITECTURE.md) - Technical architecture details
- [README.md](README.md) - Project overview and quick start
