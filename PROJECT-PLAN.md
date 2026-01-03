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
- Same-day or near-term only
- No chat-first interaction; showing up is the point

### Event Model
- Events have limited slots
- Users opt in explicitly
- Group participation during the activity is voluntary
- Presence is mandatory once committed

### Ratings and Reputation
- Users rate:
  - Overall experience
  - Each participant individually (pairwise ratings)
- Low pairwise ratings result in soft blacklisting (no future pairing)
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

---

## Integrity Principles
- Absence is treated as worse than social mismatch
- Reputation is derived from behavior, not self-description
- Blacklisting is routing logic, not feedback
- No public shaming, explanations, or score exposure

---

## MVP Scope Guardrails
Explicitly excluded from MVP:
- Long-term planning
- Social feeds
- Public profiles or bios
- Chat-centric interaction
- Broad geographic coverage

---

## Product Spine
- Flow 1: Move me
- Flow 2: Change the vibe
- Flow 3: Find community

This structure defines the product. Additional features must attach cleanly to one of these flows or be rejected.

