# File Recovery - Photo Recovery

Production-oriented Android foundation for a file recovery utility built with Kotlin and Jetpack Compose.

## Current Slice

This repository now includes the Phase 1 foundation requested in the prompt:

- Single-activity Compose app shell
- Material 3-inspired custom design system
- Clean-architecture package layout
- Hilt dependency injection
- Navigation Compose routing
- DataStore-backed theme preferences
- Home screen closely modeled after the supplied reference
- Honest placeholder screens for scan, categories, recovered, tools, settings, and premium
- Real storage usage loading via Android storage APIs

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- MVVM + Clean Architecture
- Hilt
- Coroutines + StateFlow
- Navigation Compose
- DataStore Preferences

## Package Overview

```text
com.nexappra.filerecovery
├── core
│   ├── common
│   ├── designsystem
│   ├── ui
│   └── utils
├── data
│   └── repository
├── di
├── domain
│   ├── model
│   ├── repository
│   └── usecase
└── presentation
    ├── app
    ├── home
    ├── navigation
    ├── premium
    ├── recovered
    ├── scan
    ├── settings
    └── tools
```

## Implemented Behavior

### Home

- Premium-styled top app bar
- Device storage card with real computed usage
- Full scan CTA card
- Responsive adaptive category grid
- Bottom navigation
- Light and dark Compose previews

### Settings

- Persisted appearance mode:
  - System
  - Light
  - Dark

### Secondary Screens

- Scan screen with honest non-fake state messaging
- Category detail placeholders that explain supported future behavior
- Recovered history placeholder
- Tools dashboard placeholder
- Premium architecture placeholder

## Build

```powershell
.\gradlew.bat assembleDebug
```

Latest verified result in this workspace:

- `BUILD SUCCESSFUL`
- Date: August 7, 2026
- Task: `assembleDebug`

## Important Build Note

This project currently uses a compatibility flag for AGP 9 built-in Kotlin with KSP:

- `android.disallowKotlinSourceSets=false`

That flag is present only to keep Hilt + KSP compiling cleanly in the current environment.

## Next Recommended Slice

1. Add permission orchestration for media access by Android version.
2. Introduce scanner abstractions and supported MediaStore-backed queries.
3. Build real category result screens for photos, videos, audio, documents, downloads, and screenshots.
4. Add Room-backed recovered history.
5. Add recovery destination flow with SAF/MediaStore support.
