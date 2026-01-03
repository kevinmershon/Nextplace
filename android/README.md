# Nextplace Android

Native Android application for Nextplace.

## Status

**NOT YET IMPLEMENTED** - Project setup in progress

## Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **GraphQL Client:** Apollo Kotlin
- **Navigation:** Jetpack Navigation Compose
- **Dependency Injection:** Hilt
- **Networking:** OkHttp + Retrofit
- **Image Loading:** Coil
- **Maps:** Deep links to native map apps (no map rendering in-app)
- **Minimum SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 14 (API 34)

## Project Structure

```
android/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/nextplace/
│       │   │   ├── ui/           # Composable screens and components
│       │   │   │   ├── auth/     # Magic link authentication flow
│       │   │   │   ├── home/     # Suggestion screen
│       │   │   │   ├── browse/   # Location browse
│       │   │   │   ├── event/    # Event details, chat, commitment
│       │   │   │   ├── history/  # Experience history
│       │   │   │   └── shared/   # Reusable components
│       │   │   ├── data/         # GraphQL clients, repositories
│       │   │   │   ├── graphql/  # Apollo GraphQL setup
│       │   │   │   ├── model/    # Data models
│       │   │   │   └── repo/     # Repository pattern implementations
│       │   │   ├── domain/       # Business logic, use cases
│       │   │   └── di/           # Hilt dependency injection modules
│       │   ├── res/              # Resources (layouts, drawables, strings)
│       │   └── AndroidManifest.xml
│       └── androidTest/          # Instrumented tests
├── build.gradle.kts              # Project build configuration
└── settings.gradle.kts           # Project settings
```

## Screens (Planned)

### Authentication Flow
- **Email Entry** - Magic link request screen
- **Email Sent** - Confirmation with instructions
- **Session Restoration** - Auto-login with stored JWT

### Core Surfaces
1. **Home/Suggestion** - Primary suggestion card with accept/regenerate actions
2. **Location Browse** - Scrollable location cards with "Check it out" button
3. **Active Events** - List of user's committed events (max 2 concurrent)
4. **Event Details** - Location info, activity details, participant list, timing
5. **Event Chat** - Simple chat for committed participants only
6. **Commitment Confirmation** - Explicit commitment screen with requirements
7. **Reflection** - Post-event rating (overall + pairwise) and free-text reflection
8. **History** - Past experiences log with filters

### Flow 3 (Unlocked After First Experience)
- Event join confirmation with slot availability
- Participant list (no profiles, just names/photos)
- Pairwise rating interface

## Key Features

### Location Display
- Location name, address, distance
- Price range (FREE, $, $$, $$$, $$$$)
- Amenities icons (alcohol, drinks, food, dog-friendly)
- Parking information with pricing
- "Open in Maps" button → launches native map app with coordinates

**⚠️ IMPORTANT: NO MAP RENDERING**
- This app will NEVER render maps
- Do NOT add Google Maps SDK or any map library
- Use deep links to native map apps ONLY

### Event Constraints (Enforced Client-Side)
- Cannot create/join events < 4 hours from now
- Cannot create/join events > 2 days from now
- Maximum 2 concurrent event commitments
- Visual feedback when limits reached

### Privacy & Blacklisting
- No user profile browsing
- Blacklisted users' events hidden from feed
- Users blocked from joining same events as blacklisters
- No indication to user that they've been filtered

## Development Setup

(To be written after Android Studio project initialization)

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17+
- Android SDK with API 26-34

### Environment Configuration
Create `local.properties`:
```properties
GRAPHQL_ENDPOINT=http://10.0.2.2:8888/graphql
```

**Note:** No map rendering in the app. Locations open in the user's preferred native map app (Google Maps, Apple Maps, etc.) via deep links.

## Build Targets

- **Debug** - Development build with logging and debugging enabled
- **Release** - Production build with ProGuard/R8 minification

## Dependencies (Planned)

```kotlin
// Jetpack Compose
implementation("androidx.compose.ui:ui:1.6.0")
implementation("androidx.compose.material3:material3:1.2.0")
implementation("androidx.navigation:navigation-compose:2.7.6")

// Apollo GraphQL
implementation("com.apollographql.apollo3:apollo-runtime:3.8.2")

// Hilt
implementation("com.google.dagger:hilt-android:2.50")
kapt("com.google.dagger:hilt-compiler:2.50")

// Location services only (for user location)
implementation("com.google.android.gms:play-services-location:21.1.0")

// Image loading
implementation("io.coil-kt:coil-compose:2.5.0")

// Networking
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

## Testing Strategy

- **Unit Tests** - ViewModels and business logic with JUnit 5
- **UI Tests** - Compose testing framework for screen interactions
- **Integration Tests** - GraphQL client with MockWebServer
- **Screenshot Tests** - Visual regression with Roborazzi

## GraphQL Code Generation

Apollo Kotlin generates type-safe Kotlin code from GraphQL schema:

```bash
./gradlew :app:downloadApolloSchema \
  --endpoint="http://localhost:8888/graphql" \
  --schema="app/src/main/graphql/schema.graphqls"
```

## Next Steps

1. ✅ Document requirements and architecture
2. Initialize Android Studio project with Kotlin and Compose
3. Configure build.gradle.kts with dependencies
4. Set up Apollo GraphQL code generation from backend schema
5. Implement authentication flow (magic link entry and session handling)
6. Create core composable screens:
   - Home/Suggestion screen with card layout
   - Location browse with filter chips
   - Event details with "Open in Maps" deep link button
7. Build commitment flow with 2-event limit enforcement
8. Implement event chat (simple text messages only)
9. Create post-event reflection UI with rating sliders
10. Add blacklist filtering logic to event visibility
11. Test timing constraints (4h min, 2 days max)
12. Test on physical devices (Android 8.0+)

## Design System

### Colors (Material 3)
- **Primary:** Deep blue (#1976D2)
- **Secondary:** Warm orange (#FF9800)
- **Surface:** Light gray (#F5F5F5)
- **Error:** Red (#D32F2F)

### Typography
- **Headings:** Roboto Bold
- **Body:** Roboto Regular
- **Captions:** Roboto Light

### Components
- Material 3 design language
- Adaptive layouts for tablets
- Bottom navigation for main surfaces
- Floating action button for primary actions

## Accessibility

- Minimum touch target: 48dp
- Content descriptions for all interactive elements
- High contrast mode support
- Screen reader compatibility
- Dynamic font sizing support
