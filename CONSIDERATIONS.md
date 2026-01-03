# Nextplace — Considerations & Caveats

This document captures non-binding guidance, risks, design instincts, and expandable ideas that may influence product decisions without being part of the MVP contract.

---

## Flow Interdependence & Gating

### Flow 3 Is a Privilege, Not a Default
Flow 3 (social encounters) should not be the initial entry point for new users.

Recommended gating:
- Users must complete at least one solo experience from Flow 1 or Flow 2
- After completion, Flow 3 becomes available as:
  - an overlay on future suggestions
  - or a lightweight browsing surface for existing events

This ensures:
- Baseline trust and seriousness
- Shared behavioral data before pairing
- Reduced moderation and flake risk

Flow 3 should feel like an *unlock*, not a requirement.

### Commitment Limits Prevent Abuse
Users limited to 2 concurrent event commitments:
- Prevents toxic users from blocking others by mass-joining events
- Enforces intentionality and reduces flaking
- Maintains system health when combined with blacklisting

### Timing Constraints Enforce Spontaneity
Events must be created/joined:
- Minimum 4 hours from current time (prevents impulsive behavior)
- Maximum 2 days from current time (maintains spontaneity, prevents long-term planning)

This window balances:
- Enough time for participants to prepare and travel
- Short enough to maintain momentum and reduce cancellations

---

## Gentle Cautions

### Decision Fatigue Is the Enemy
- Avoid filters, sliders, and preference tuning in early flows
- The product should decide first, explain later (if at all)
- Confidence matters more than optimality

### Ratings Are Internal Plumbing
- Never expose pairwise ratings or blacklists
- Never tell users they were rated poorly
- Ratings should affect routing, not self-perception

### Weather Trust Is Fragile
- Weather-driven suggestions must be conservative
- One bad recommendation erodes confidence disproportionately
- Prefer fewer suggestions with higher confidence margins

---

## Expanded Ideas (Optional, Non-MVP)

### Behavioral Identity Over Profile Identity
User identity should be inferred, not declared.

Avoid:
- Long bios
- Personality labels
- Interest taxonomies

Prefer emergent traits:
- Average distance traveled
- Weather tolerance
- Novelty acceptance
- Punctuality consistency
- Reflection verbosity

This allows the system to understand users without forcing them to self-describe.

---

## App Structure & Page Model

### Suggested Core Surfaces

1. **Home / Suggestion**
   - One primary suggestion at a time
   - Clear framing: where, why, how long
   - Accept / regenerate / decline

2. **Today Map (Optional)**
   - Minimal geographic context
   - Emphasis on distance and direction, not exploration

3. **Commitment Screen**
   - Clear expectations
   - Time window and meeting instructions
   - Explicit acknowledgement of commitment

4. **Reflection Prompt**
   - Appears post-experience
   - Short, optional free-text
   - Ratings captured here

5. **History / Memory**
   - Personal log of places and experiences
   - No social comparison

6. **Social Overlay (Flow 3)**
   - Visible only after unlock
   - Shows upcoming events with open slots
   - Join is a deliberate action

---

## User Controls Philosophy

### What Users Can Control
- Accept or skip a suggestion
- Opt in or out of social participation
- Choose time window (implicit rather than explicit when possible)

### What Users Should Not Control (Initially)
- Fine-grained preferences
- Who they are matched with
- Event curation parameters

The system should shoulder responsibility.

---

## Profiles: Minimal by Design

Recommended profile surface:
- Name or alias
- Photo (optional but encouraged)
- Very limited editable fields
- **(Post-MVP)** Subtle activity rank badge displayed in corner
- **Icebreaker prompts** - Categorical "Ask me about..." fields visible only to event participants
  - Examples: "Ask me about: Coffee roasting, Bay Area hiking, indie games"
  - 3-5 short prompts maximum
  - Designed for conversation starting, not matching/filtering
  - Only visible to people in same event, not public

No:
- Bio paragraphs
- Traditional interest lists for matching/filtering
- Social links
- Public leaderboards or comparisons
- Prominent gamification elements

Profiles exist to reassure and facilitate conversation, not to sell or compete.

---

## Icebreakers & Light Chat

Chat should be constrained and purposeful.

Suggested approach:
- Pre-seeded prompts tied to the activity or weather
- Examples:
  - “What made you say yes to this?”
  - “Have you been here before?”
  - “What would make today a win?”

Avoid open-ended DMs before meeting.

---

## Commitment & Friction Design

Friction should appear only at commitment points.

Examples:
- Explicit confirmation when joining a group
- Reminder shortly before start time
- Clear consequence framing (without shaming)

The goal is seriousness, not punishment.

---

## Blacklisting: Bidirectional and Transitive

### Core Principle
If User A blacklists User B (via low rating), protection is bidirectional:
- User A cannot see User B's events
- User B cannot see User A's events
- If User A joins Event X, User B is ineligible to see or join Event X
- If User B hosts Event Y, User A cannot see or join Event Y

### Rationale
- Protects both parties from uncomfortable re-encounters
- Prevents toxic users from targeting specific individuals
- Combined with 2-event limit, prevents one bad actor from blocking system participation

### Implementation Note
Blacklisting operates at the event visibility and eligibility layer, not at the user discovery layer (since there is no user browsing in MVP).

---

## Find My Vibe: Location + Weather Browse

### No Browse Events Page
Instead of browsing events, users browse locations with weather context.

### "Find My Vibe" Page Concept
- Combines locations with current/forecasted weather conditions
- Filter by weather type: sunny, foggy, cool breeze, golden hour, etc.
- Filter by location type: parks, beaches, neighborhoods, hiking trails, etc.
- Each location card shows:
  - Location name and distance
  - Current/forecasted weather at that location
  - Suggested activities based on conditions
  - "Check it out" button

### "Check It Out" Pattern
- User browses locations with weather filters
- Clicks "Check it out" on a location
- System creates new event hosted by that user
- Event becomes visible to other eligible users (subject to blacklist filtering)

### Benefits
- Merges Flow 1 (place discovery) and Flow 2 (weather-driven) into browsable format
- Maintains place-first philosophy with weather context
- Reduces abandoned or low-commitment events
- Creator implicitly commits by hosting
- Makes weather-driven discovery more accessible

---

## Friend System (Post-MVP)

### Entry Point
- After rating another participant 5/5, user prompted to send friend request
- Opt-in only, no automatic friending

### Friend Capabilities
- View friends list
- Remove friends
- Optional notifications when friends join/host events (enables "tag along")

### Privacy Boundaries Maintained
- No user profile browsing
- No bios, interest lists, or social feeds
- Cannot discover friends outside of event participation
- Friendship enables notifications, not surveillance

### Why Post-MVP
- Adds complexity to notification system
- Requires additional state management (friend requests, acceptance, removal)
- Social graph introduces edge cases (unfriending, blocking friends, etc.)
- Core product must prove value without social scaffolding first

---

## Removal of Flow 3 (If Needed)

Flow 3 should be designed as a detachable module.

If social complexity becomes overwhelming:
- The product remains complete with Flows 1 and 2
- Social can be reintroduced later without re-architecting

This optionality is a strategic safety valve.

---

## Design Tone

### Visual & Communication Style
- Calm, confident, minimal
- No urgency theatrics or dark patterns
- Trust through restraint and clarity

### Gamification Approach (Post-MVP)
When activity tracking is introduced:
- **Subtle, not central** - Badge in profile corner, not hero element
- **Background tracking** - Points/ranks exist but don't dominate UI
- **Privacy-first** - No leaderboards, no comparisons, no public scores
- **Existing terminology unchanged** - Keep "events" and "suggestions", not "quests"

The product should feel like a quiet nudge toward exploration, not a game to optimize.

---

## Guiding Question for All Decisions

"Does this make it easier for someone to actually go somewhere today?"

If not, it is likely out of scope.

---

## Permanent Architecture Constraints

### No Map Rendering Ever

**This constraint must never be violated in any version of the product.**

The app will NEVER:
- Render maps (web or mobile)
- Include Google Maps SDK or any map library
- Generate or serve static map images
- Display map tiles or embedded maps

The app WILL:
- Display location text (name, address, distance)
- Provide "Open in Maps" button
- Deep link to user's native map app

**Why this is permanent:**
- Security: No API keys in mobile apps
- Simplicity: No map rendering complexity
- UX: Users already know their map app
- Size: Smaller binary, no map dependencies
- Cost: Backend geocoding only, no per-device API costs

**If someone proposes adding maps:**
The answer is NO. Point them to this document.

---

