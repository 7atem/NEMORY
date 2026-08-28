# VaultBrain Tier 1 — Task Tracker

## Component 1: Compile Error Fixes
- [x] Fix `scheduleAlerts()` missing method in UnifiedAlertManager
- [x] Fix `Build` import in AiCapabilityManager
- [x] Fix `Row` import in HomeScreen

## Component 2: Auth Gate & Security
- [x] Create AuthGateScreen composable
- [x] Create AuthGateViewModel
- [x] Update VaultBrainNavGraph with auth gate as start destination
- [x] Add auto-lock enforcement in MainActivity

## Component 3: Theme & Visual Identity
- [x] Replace Color.kt with VaultBrain branded palette
- [x] Define full typography with Inter font in Type.kt
- [x] Update Theme.kt with dark-mode-first branded schemes

## Component 4: Bottom Navigation & Home Redesign
- [x] Add bottom navigation bar scaffold
- [x] Redesign HomeScreen with gradient briefing, animated search, branded lens grid
- [x] Update UiComponents with lens-specific icons/colors and image thumbnails

## Component 5: Image Loading & Release Config
- [x] Add Coil dependency to build.gradle.kts
- [x] Enable R8/ProGuard for release builds
- [x] Replace ItemDetailScreen placeholder with AsyncImage

## Component 6: Basic Animations
- [x] Add AnimatedNavHost for screen transitions
- [x] Loading skeleton composables
- [x] FAB animation (Expandable FAB)
