# Nextplace

**Something to do. Somewhere to go.**

For people with abundant free time but limited social momentum.

## The Problem

Free time is abundant. Energy to plan and coordinate is not. You want to get out and experience things, but the friction of deciding where to go, what to do, and who to do it with creates inertia.

## The Solution

Nextplace removes the friction. We give you spontaneous, personalized suggestions for places and activities when you're ready to go. No planning paralysis. No endless scrolling. Just open the app and move.

## How It Works

### 1. Spontaneous Discovery
Get personalized suggestions for places and activities based on your location, preferences, and the moment. When you're ready to go, we tell you where and what.

### 2. Weather Escapes
Need a change of scenery? We find better weather within driving distance. Escape the fog for sunshine, or the heat for coastal breeze.

### 3. Small-Group Encounters (Unlocked Feature)
Low-pressure social meetups anchored to activities. Small groups (2-4 people), commitment accountability, and activity-focused interaction for people who want optional social discovery.

## Getting Started

Visit [http://localhost:8888](http://localhost:8888) to join the waitlist.

---

## For Developers

**Quick Links:**
- [ARCHITECTURE.md](ARCHITECTURE.md) - Technical details, development setup, and contribution guidelines
- [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md) - Current progress, completed features, and next steps
- [PROJECT-PLAN.md](PROJECT-PLAN.md) - Product vision and flow definitions
- [CONSIDERATIONS.md](CONSIDERATIONS.md) - Design philosophy and principles

**Tech Stack:**
- **Backend:** Clojure with Lacinia GraphQL, Reitit, RocksDB
- **Web Frontend:** Preact with HTM and Signals (standalone from CDN)
- **Android:** Kotlin with Jetpack Compose, Apollo GraphQL, Hilt
- **Development:** Integrant REPL with console system for database interrogation

**Current Status:** MVP backend complete with authentication. Android app initialized.

**Architecture Note:** No in-app map rendering. Backend uses Google Maps API for geocoding/place lookups only. Mobile apps display location info and open coordinates in device's native map app via deep links.

---

## Project History

Nextplace is a continuation of the idea originally started with [temperatr](https://github.com/kevinmershon/temperatr). It aims to be an automated community-and-friend discovery service akin to [Timeleft](https://www.timeleft.com/) and [Urban Diversion](https://urbandiversion.com/), with a focus on spontaneous, low-friction experiences.
