# VaultBrain — Tier 1 Critical Fixes Implementation Plan

## Goal
Fix all critical blockers and deliver the foundational UX upgrades needed before any further feature work can proceed. This addresses the 10 items identified as "No Launch Without These" in the assessment.

## Proposed Changes

### Component 1: Compile Error Fixes

#### [MODIFY] [UnifiedAlertManager.kt](file:///d:/Vault%20Brain/core/notifications/src/main/java/com/vaultbrain/core/notifications/UnifiedAlertManager.kt)
- Add `scheduleAlerts(item: VaultItem)` convenience method that extracts expiry/alert dates from the item and calls the existing `schedule()` method

#### [MODIFY] [AiCapabilityManager.kt](file:///d:/Vault%20Brain/core/ai/llm/src/main/java/com/vaultbrain/core/ai/llm/AiCapabilityManager.kt)
- Add missing `import android.os.Build`

#### [MODIFY] [HomeScreen.kt](file:///d:/Vault%20Brain/feature/vault/src/main/java/com/vaultbrain/feature/vault/HomeScreen.kt)
- Add missing `import androidx.compose.foundation.layout.Row`

---

### Component 2: Auth Gate & Security

#### [NEW] [AuthGateScreen.kt](file:///d:/Vault%20Brain/feature/vault/src/main/java/com/vaultbrain/feature/vault/AuthGateScreen.kt)
- Full-screen auth gate with biometric prompt + PIN fallback
- Decoy vault detection (enters different nav state)
- Emergency access button
- Premium dark gradient background with VaultBrain logo

#### [NEW] [AuthGateViewModel.kt](file:///d:/Vault%20Brain/feature/vault/src/main/java/com/vaultbrain/feature/vault/AuthGateViewModel.kt)
- Manages auth state, PIN verification, biometric flow
- Auto-trigger biometric on launch

#### [MODIFY] [VaultBrainNavGraph.kt](file:///d:/Vault%20Brain/app/src/main/java/com/vaultbrain/app/navigation/VaultBrainNavGraph.kt)
- Change `startDestination` from `Screen.Home` to `Screen.AuthGate`
- Add auth gate route
- Add auto-lock observer

#### [MODIFY] [MainActivity.kt](file:///d:/Vault%20Brain/app/src/main/java/com/vaultbrain/app/MainActivity.kt)
- Track `lastActiveTimestamp` in onPause/onResume
- Auto-lock navigation when threshold exceeded

---

### Component 3: Theme & Visual Identity

#### [MODIFY] [Color.kt](file:///d:/Vault%20Brain/app/src/main/java/com/vaultbrain/app/ui/theme/Color.kt)
- Replace default purple palette with VaultBrain branded colors
- Add per-lens semantic colors (MoneyGreen, HealthBlue, TravelOrange, etc.)

#### [MODIFY] [Type.kt](file:///d:/Vault%20Brain/app/src/main/java/com/vaultbrain/app/ui/theme/Type.kt)
- Define all Material 3 typography slots using Inter font family
- Add proper line heights, letter spacing, weights

#### [MODIFY] [Theme.kt](file:///d:/Vault%20Brain/app/src/main/java/com/vaultbrain/app/ui/theme/Theme.kt)
- Dark mode as default
- Custom dark/light color schemes using branded palette
- Dynamic color as opt-in

---

### Component 4: Bottom Navigation & Home Redesign

#### [MODIFY] [VaultBrainNavGraph.kt](file:///d:/Vault%20Brain/app/src/main/java/com/vaultbrain/app/navigation/VaultBrainNavGraph.kt)
- Add `VaultBrainScaffold` wrapper with bottom navigation bar
- Routes: Home | Vault | Capture (center FAB) | Brain | Settings

#### [MODIFY] [HomeScreen.kt](file:///d:/Vault%20Brain/feature/vault/src/main/java/com/vaultbrain/feature/vault/HomeScreen.kt)
- Redesign with gradient Morning Briefing hero card
- Animated search bar (M3 SearchBar)
- Lens grid with branded icons and colors per lens
- Empty state with illustration

#### [MODIFY] [UiComponents.kt](file:///d:/Vault%20Brain/feature/vault/src/main/java/com/vaultbrain/feature/vault/components/UiComponents.kt)
- Lens cards with unique emoji + branded color backgrounds
- Image thumbnails using Coil

---

### Component 5: Image Loading & Release Config

#### [MODIFY] [app/build.gradle.kts](file:///d:/Vault%20Brain/app/build.gradle.kts)
- Add Coil Compose dependency
- Enable `isMinifyEnabled = true` for release
- Add ProGuard keep rules for SQLCipher, ObjectBox, ML Kit

#### [MODIFY] [ItemDetailScreen.kt](file:///d:/Vault%20Brain/feature/vault/src/main/java/com/vaultbrain/feature/vault/ItemDetailScreen.kt)
- Replace placeholder Icon with `AsyncImage` (Coil) for captured photos
- Add hero image with gradient overlay

---

### Component 6: Basic Animations

- Add `AnimatedNavHost` for screen transitions
- Staggered enter animations on list items
- Loading skeleton composables instead of "Loading..." text
- FAB expand/collapse animation

---

## Verification Plan

### Automated
- Gradle build must succeed without errors (`./gradlew assembleDebug`)
- Existing 6 unit tests must pass
- New auth gate tests

### Manual
- Cold launch → Auth gate appears with PIN/biometric prompt
- After auth → Home screen with branded theme, bottom nav, proper empty states
- Background app → Resume after auto-lock delay → Auth gate reappears
- Capture flow → Image visible in item detail via Coil
- Dark mode renders correctly with branded palette
