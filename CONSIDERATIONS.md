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

No:
- Bio paragraphs
- Interest lists
- Social links

Profiles exist to reassure, not to sell.

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

## Removal of Flow 3 (If Needed)

Flow 3 should be designed as a detachable module.

If social complexity becomes overwhelming:
- The product remains complete with Flows 1 and 2
- Social can be reintroduced later without re-architecting

This optionality is a strategic safety valve.

---

## Design Tone

- Calm, confident, minimal
- No gamification language
- No urgency theatrics
- Trust through restraint

The product should feel like a quiet nudge, not a hype engine.

---

## Guiding Question for All Decisions

“Does this make it easier for someone to actually go somewhere today?”

If not, it is likely out of scope.

