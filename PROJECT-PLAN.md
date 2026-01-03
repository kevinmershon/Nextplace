# Nextplace — Project Plan

## Product Summary
Nextplace is a spontaneous event and place discovery product for people with abundant free time but limited social momentum. The system removes decision-making friction by proactively suggesting places, activities, and optional social encounters on same-day or very short timelines.

The product centers on motion, presence, and follow-through rather than optimization, feeds, or long-term planning.

---

## Core Constraints
- Same-day or near-term only
- Low cognitive load
- Few confident suggestions instead of exhaustive lists
- Emphasis on doing, not browsing
- Social participation is optional and secondary

---

## North Star Loop
Idle → Suggestion → Movement → Experience → Reflection

All features must reinforce this loop. Anything that does not strengthen it should be excluded from MVP.

---

## Flow 1: Place + Activity Discovery

### Goal
Automatically generate a concrete reason to leave the house by selecting both a destination and an activity.

### Description
- User is in a specific city (MVP focus: South San Francisco Bay Area)
- The system selects:
  - A place (park, neighborhood, nearby city, drive target)
  - An activity appropriate to that place
- An "event" may be:
  - A listed event from external sources (e.g., Meetup, Eventbrite)
  - A constructed experience (walk a trail, explore an area, sit somewhere scenic)

### Key Characteristics
- Place-first is insufficient; activity must be paired
- Suggestions prioritize novelty and feasibility over popularity
- The app acts as an agent, not a directory

### Completion
- User attends the activity
- User reflects and rates the experience

---

## Flow 2: Weather-Driven Escapes

### Goal
Enable users to change how it feels by traveling short distances to different weather conditions.

### Description
- User’s current weather is treated as an input constraint
- The system identifies nearby microclimates or weather pockets
- Suggestions are framed by distance and time, not city names

### Weather Conditions
Examples include:
- Coastal breeze vs inland heat
- Fog vs sun
- Wind corridors
- Snowline (seasonal)

### Activity Coupling
Weather conditions drive activity selection:
- Wind → kite flying
- Cool + clear → trail walking
- Warm + dry → outdoor yoga
- Fog or golden hour → photography

### Differentiation
- Condition → Place → Activity
- Not city lookup, not forecast browsing

---

## Flow 3: Small-Group Social Encounters

### Goal
Enable low-pressure, high-integrity social interaction anchored to real activities.

### Description
- Small groups (2–6 people)
- Anchored to places and activities from Flows 1 and 2
- Near-term only (4 hours minimum, 2 days maximum from current time)
- No chat-first interaction; showing up is the point

### Event Model
- Events have limited slots
- Users opt in explicitly
- Group participation during the activity is voluntary
- Presence is mandatory once committed
- Events cannot be created or joined less than 4 hours from current time
- Events cannot be created or joined more than 2 days from current time

### Event Creation
- No general "browse events" page
- Users can browse locations page
- "Check it out" button on location creates new event hosted by that user
- System-generated suggestions also create events when accepted

### Commitment Limits
- Users can only commit to maximum 2 events at a time
- Prevents toxic users from blocking others' participation
- Enforces intentionality and reduces flaking

### Ratings and Reputation
- Users rate:
  - Overall experience
  - Each participant individually (pairwise ratings)
- Low pairwise ratings result in soft blacklisting (no future pairing)
- Blacklisting is bidirectional and transitive:
  - If User A blacklists User B, neither can see each other's events
  - If User A joins Event X, User B cannot see or join Event X
  - If User A hosts Event Y, User B cannot see or join Event Y
- Ratings are never exposed to other users

### Commitment Enforcement
- No-show results in automatic zero rating
- Late arrival caps maximum possible rating
- Early departure requires a private reason

### Matching Philosophy
- Avoid interest-based matching in MVP
- Prefer behavioral signals:
  - Punctuality
  - Willingness to travel
  - Activity tolerance (distance, weather, novelty)
  - Reflection depth

### Privacy and Discovery
- No user browsing or profile discovery
- Users only visible in:
  - Event chat (for committed participants)
  - Post-event rating screens
- No public profiles or bios

---

## Integrity Principles
- Absence is treated as worse than social mismatch
- Reputation is derived from behavior, not self-description
- Blacklisting is routing logic, not feedback
- No public shaming, explanations, or score exposure

---

## Location Data Model

### Core Location Attributes
All locations tracked in the system include:

**Geographic Data:**
- **Latitude/Longitude** - General location coordinates
- **Meeting point coordinates** - Precise spot within location (for large venues)
- **Meeting point instructions** - Human-readable directions (e.g., "By the main fountain", "North parking lot entrance")
- **Address** - Full address for display and deep linking

**Venue Information:**
- **Price range** - Estimated cost level for the venue
- **Serves alcohol** - Boolean flag for alcohol availability
- **Serves non-alcoholic drinks** - Boolean flag for beverage availability
- **Serves food** - Boolean flag for food availability
- **Dog friendly** - Boolean flag (some parks/hikes restrict dogs)

**Parking Information:**
- **Closest parking location** - Name/address of garage or lot
- **Expected parking price** - Cost estimate or marked as free

**Meeting Point Rationale:**
- Large parks/venues need precise meeting spots
- "Golden Gate Park" → "By the carousel in Koret Children's Quarter"
- "Dolores Park" → "Top of the hill near tennis courts"
- Deep link opens to precise coordinates, not general location

These attributes enable:
- Activity pairing logic (e.g., dog-friendly suggestions for dog owners)
- Budget-appropriate suggestions
- Practical planning (parking, refreshments)
- Precise meetup coordination for social events

---

## Post-MVP Features

### Activity Tracking & Ranking
Background system to encourage consistent participation:

**Points System:**
- Points awarded for attending events you committed to
- Double points awarded for creating AND attending an event (rewards initiative and follow-through)
- Points awarded for rating experiences and leaving feedback
- Points NOT awarded for event creation alone (prevents spam), profile activity, or social metrics
- Backend tracks points with 10% weekly decay during inactivity (7+ days without attendance)

**Personal Ranking:**
- Subtle rank badge in profile: Newcomer → Regular → Well-Known → Celebrity → Icon
- Badges show peak achievement (cannot be lost once earned)
- Badge displayed next to name in event participant lists
- Tap/hover badge to see detailed ranking and current points
- Rank visible only to event participants, not publicly browsable
- No leaderboards, no cross-user comparisons, no public rankings

**Implementation:**
- Backend calculates decay daily
- GraphQL API returns current points and rank
- Native apps display badge subtly in profile
- Optional: gentle notification if decay approaching

**Design Principles:**
- Gamification stays in background, not primary framing
- Existing terminology unchanged (events, suggestions, locations)
- Motivation through personal progression, not competition
- Privacy-first: scores remain private

### Friend System
Unlocked after positive social interactions:

**Friend Requests:**
- Prompted after rating another participant 5/5
- Opt-in friend request system
- No user browsing or profile discovery outside events

**Friend Management:**
- Friends list for managing connections
- Ability to remove friends
- Optional notifications when friends join/host events
- Enables spontaneous "tag along" behavior

**Privacy Maintained:**
- Friends cannot browse each other's profiles
- No bio, interest lists, or social feed
- Friendship enables notifications, not surveillance

---

## MVP Scope Guardrails
Explicitly excluded from MVP:
- Chat-centric interaction
- Broad geographic coverage
- Friend system (post-MVP)

## Permanent Architecture Constraints

### Never-Implement Features (PERMANENT)
**These features will NEVER be implemented, regardless of version or future scope:**

- ❌ **Long-term planning** - No calendar integration, future scheduling beyond 2 days
- ❌ **Social feeds** - No timeline, activity stream, or content browsing
- ❌ **Public profiles or bios** - No user profiles visible to others
- ❌ **User browsing or discovery** - No search/browse for other users

**Rationale:**
These features violate core product principles: spontaneity over planning, action over browsing. The product is about doing things now, not discovering people, consuming content, or optimizing schedules. Social interaction is anchored to real-world activities, not profiles or feeds.

### No In-App Map Rendering (PERMANENT)
**This application will NEVER render maps.** This is not a "for now" or "in MVP" decision. This is a permanent architectural constraint.

#### What is NOT Allowed
- ❌ **Google Maps SDK** (Android or iOS)
- ❌ **Map rendering libraries** (Leaflet, Mapbox, MapKit, etc.)
- ❌ **Static map images** (Google Maps Static API, etc.)
- ❌ **Map tiles or embedded maps**
- ❌ **Any GraphQL field like `map_image_url` or `map_tile_url`**
- ❌ **In-app navigation or route rendering**

#### What IS Allowed
- ✅ **Display location data as text** (name, address, coordinates, distance)
- ✅ **"Open in Maps" button** that deep links to native map app
- ✅ **Backend Google Maps API usage** for geocoding and place lookups ONLY
- ✅ **Storing/returning coordinates** for deep linking

#### Implementation Pattern
```kotlin
// Android
fun openInMaps(context: Context, latitude: Double, longitude: Double, name: String) {
    val uri = "geo:$latitude,$longitude?q=$latitude,$longitude($name)"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    context.startActivity(intent)
}
```

```swift
// iOS
func openInMaps(latitude: Double, longitude: Double, name: String) {
    let url = "http://maps.apple.com/?ll=\(latitude),\(longitude)&q=\(name)"
    UIApplication.shared.open(URL(string: url)!)
}
```

```javascript
// Web
function openInMaps(latitude, longitude, name) {
    const url = `https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`;
    window.open(url, '_blank');
}
```

#### Why This Constraint Exists
**Security**: No API keys in mobile apps - cannot be reverse-engineered from binaries

**Simplicity**: Zero map rendering complexity, smaller binary size, faster development

**User Experience**: Native app familiarity, better navigation, users can use their preferred map app

**Cost**: Backend geocoding only, no per-device API costs, centralized rate limiting

#### Enforcement
**Code Review Checklist:**
- No imports of map SDKs or libraries
- No `map_image_url` or similar fields in GraphQL schema
- No map rendering UI components
- No static map image generation code

**Auto-reject PRs that:**
- Add Google Maps SDK dependency
- Add map rendering library
- Add map-related fields to GraphQL schema
- Contain map rendering UI code

---

## Product Spine
- Flow 1: Move me
- Flow 2: Change the vibe
- Flow 3: Find community

This structure defines the product. Additional features must attach cleanly to one of these flows or be rejected.

