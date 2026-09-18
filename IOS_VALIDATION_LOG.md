# Nemory iOS Validation Log

> **Assignment:** T0/T1 of `NEMORY_IOS_DELEGATION_PLAN.md`  
> **Baseline HEAD:** `1aa65e2f1354aae6f1fa5e4f3ff5077850583f4d`  
> **Worktree state:** 268 changed entries (167 tracked modifications, 101 untracked)  
> **Host:** Windows  
> **macOS/Xcode available in this session:** No  
> **iPhone 16 Pro connected:** No  
> **Apple Developer signing available:** No

## T0 — Baseline and portability audit

### Baseline provenance

- `git rev-parse HEAD` → `1aa65e2f1354aae6f1fa5e4f3ff5077850583f4d`
- `git status --short | wc -l` → `268`
- `git status --short | grep -c '^??'` → `101`
- `git diff --stat` summary → 167 files changed, 4785 insertions, 4431 deletions

Status: **VERIFIED**. The worktree is significantly ahead of HEAD and must be treated as the active baseline.

### Documentation drift check

| Document | Claim | Source | Action |
|---|---|---|---|
| `NEMORY_MASTER_DOCUMENTATION.md` | Room schema 16 | Header and section 4.2 | Updated to schema 20 and added schema 20 bullet. |
| `AGENTS.md` | Room schema 20 | Line ~14 | Confirmed correct; no change. |
| `DYNAMIC_BRAIN_ROADMAP.md` | Gmail/tasks not implemented | Connector exists, data source unconfigured | Confirmed; no code change. |

Status: **VERIFIED / RECONCILED**.

### Portability audit

- Audited `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, and representative module `build.gradle.kts` files.
- Audited `core/common/model`, `core/ai/rag`, `core/ai/heuristics`, `core/ai/llm`, `core/database`, `core/vectorstore`, `feature/capture/MediaVaultStorage.kt`, `feature/vault/*Screen.kt`, `feature/brain/BrainChatScreen.kt`, and `sync/drive/DriveBackupManager.kt`.
- Deliverable: `KMP_PORTABILITY_AUDIT.md` with module matrix, file evidence, blockers, and smallest viable extraction.

Status: **VERIFIED**.

### Android baseline build

**Command:** `./gradlew.bat :app:assembleDebug :shared:testDebugUnitTest :core:ai:heuristics:testDebugUnitTest -Pnemory.skipNativeForTests=true --console=plain`  
**Environment:** Windows, Java/Gradle daemon reused from existing `.gradle`.  
**Result:** `BUILD SUCCESSFUL in 12s` (650 actionable tasks: 19 executed, 631 up-to-date).  
**Status:** VERIFIED.

## T1 — iOS foundation and one shared behavior

### T1.1 Add shared KMP module

- Added module: `shared/`
- `shared/build.gradle.kts` plugins: `kotlin-multiplatform`, `compose-multiplatform`, `compose-compiler`, `android-library`.
- Targets configured: `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`.
- Registered in `settings.gradle.kts` and consumed by `app`, `core:common` (`api`), and `core:ai:heuristics` (`implementation`).
- Catalog updated: `composeMultiplatform = "1.10.3"`; plugin aliases added in `gradle/libs.versions.toml`.

Status: VERIFIED on Android; **UNVERIFIED on iOS** (Windows host cannot compile Kotlin/Native iOS targets).

### T1.2 Extract deterministic shared behavior

- Extracted:
  - `LensId` → `shared/src/commonMain/kotlin/com/vaultbrain/shared/domain/LensId.kt` (moved from `core/common`).
  - `KeywordDictionary` → `shared/src/commonMain/kotlin/com/vaultbrain/shared/intelligence/KeywordDictionary.kt` (moved from `core:ai:heuristics`).
- `KeywordDictionary` is pure Kotlin + regex; no JVM `Clock` or platform time calls were required.
- Added `KeywordDictionaryTest` in `shared/src/commonTest/...` with 7 deterministic cases.
- All existing Android callers updated to import the shared versions; `:app:assembleDebug` confirms no broken references.

Status: VERIFIED on JVM/Android (7/7 tests passed); **UNVERIFIED on Kotlin/Native iOS** (no macOS host).

### T1.3 Bilingual shared-UI sample

- Added `SharedSampleScreen` (`shared/src/commonMain/.../shared/ui/sample/SharedSampleScreen.kt`) with bilingual labels and RTL layout driven by caller parameters (no platform resource system).
- Android host: `app/src/main/java/com/vaultbrain/app/sample/SharedSampleActivity.kt`, declared in `app/src/main/AndroidManifest.xml`.
- iOS host: `MainViewController.kt` + `SampleViewControllerFactory.kt` in `shared/src/iosMain/...`, consumed by `iosApp/iosApp/ContentView.swift`.

Status: **COMPILES on Android** (APK packages successfully); not visually rendered on a device. **UNVERIFIED on iOS** (no macOS/Xcode to run the simulator).

### T1.4 macOS simulator CI job

- Added `.github/workflows/ios.yml`:
  - Runner: `macos-15`
  - Xcode: `16.1` (`/Applications/Xcode_16.1.app`)
  - Simulator: `iPhone 16 Pro`, iOS 18.1
  - Steps: checkout → Java 21 → XcodeGen → generate Xcode project → `:shared:embedAndSignAppleFrameworkForXcode` → `xcodebuild build` → `:shared:iosSimulatorArm64Test` → upload logs.
- XcodeGen spec: `iosApp/project.yml` with `shared` framework dependency and a pre-build script that invokes the Kotlin framework task.

Status: **UNVERIFIED** until run on GitHub Actions; the workflow file is syntactically present but has not executed.

### T1.5 Android regression checks

| Check | Command | Result |
|---|---|---|
| App debug build | `./gradlew.bat :app:assembleDebug -Pnemory.skipNativeForTests=true --console=plain` | BUILD SUCCESSFUL in 12s |
| Shared unit tests | `./gradlew.bat :shared:testDebugUnitTest --rerun-tasks --console=plain` | 7 tests, 0 failures |
| Heuristics unit tests | `./gradlew.bat :core:ai:heuristics:testDebugUnitTest --rerun-tasks --console=plain` | 15+ tests, 0 failures |
| Localization parity | `python scripts/check_localization.py` | PASS |

Status: VERIFIED. Note: only `:shared` and `:core:ai:heuristics` unit tests were rerun; full `./gradlew.bat testDebugUnitTest` across every module was not executed in this turn.

## Final T1 summary

| Item | Status | Evidence / command |
|---|---|---|
| Shared module added | VERIFIED | `shared/build.gradle.kts`, `settings.gradle.kts` includes `:shared` |
| Android consumes shared code | VERIFIED | `app/build.gradle.kts`, `core/common/build.gradle.kts`, `core/ai/heuristics/build.gradle.kts` reference `:shared` |
| Deterministic behavior extracted | VERIFIED | `LensId.kt` + `KeywordDictionary.kt` moved to `shared/`; all Android imports updated |
| JVM common tests pass | VERIFIED | `KeywordDictionaryTest`: 7 tests, 0 failures |
| Android tests pass | VERIFIED | `HeuristicExtractorTest`: 15 tests, 0 failures; assembleDebug passes |
| iOS target declared | VERIFIED | `iosArm64()` + `iosSimulatorArm64()` in `shared/build.gradle.kts` |
| iOS simulator build | **UNVERIFIED** | Windows host cannot run `xcodebuild`; configured via `iosApp/project.yml` + `.github/workflows/ios.yml` |
| macOS CI job added | CONFIGURED | `.github/workflows/ios.yml` (macos-15, Xcode 16.1, iPhone 16 Pro iOS 18.1) — **UNVERIFIED** until GitHub Actions run |
| Android baseline build | VERIFIED | `./gradlew.bat :app:assembleDebug ...` BUILD SUCCESSFUL |
| Android unit tests | VERIFIED (sampled) | `:shared:testDebugUnitTest` + `:core:ai:heuristics:testDebugUnitTest` passed |
| Localization parity | VERIFIED | `python scripts/check_localization.py` PASS |

## Environment limits that persist

- **Windows host:** Cannot compile Kotlin/Native iOS targets or run `xcodebuild`.
- **No Apple Developer credentials:** Cannot sign or install on iPhone 16 Pro.
- **macOS CI runner:** Workflow is configured but has not executed; GitHub Actions may reveal XcodeGen/xcodebuild issues.

---

## T1B — iOS foundation repair and acceptance gate

> Status: **local repairs complete; macOS execution unavailable in this session.**  
> T1 is **not marked complete** until the Apple-target build and simulator launch are verified.

### T1B.1 Framework and Xcode integration repairs

- `shared/build.gradle.kts`: explicitly declared a **dynamic** iOS framework named `shared` (`baseName = "shared"`, `isStatic = false`, `bundleId = com.vaultbrain.shared`) so the Swift module name matches the XcodeGen dependency.
- `iosApp/project.yml`:
  - Added `ENABLE_USER_SCRIPT_SANDBOXING: NO` so Gradle can write the framework outside the Xcode sandbox.
  - Disabled simulator code signing (`CODE_SIGNING_ALLOWED: NO`, `CODE_SIGN_IDENTITY: ""`).
  - Added `LD_RUNPATH_SEARCH_PATHS: @executable_path/Frameworks` for dynamic framework loading.
  - Set `SWIFT_VERSION: "6.0"`.
  - Fixed the pre-build script to `chmod +x ./gradlew` and run with `set -e`.
  - Added input/output file declarations for the framework to reduce redundant rebuilds.
- `.github/workflows/ios.yml`:
  - Added `chmod +x ./gradlew`.
  - Added toolchain introspection (`xcodebuild -version`, `-showsdks`, `simctl list runtimes/devices`).
  - Pinned `XCODE_VERSION: 16.1` env var; the workflow selects it explicitly.
  - Removed the hard-coded iOS 18.1 runtime from the simulator destination; now uses `platform=iOS Simulator,name=iPhone 16 Pro` so any compatible runtime succeeds.
  - Added `set -euo pipefail` and `tee` log capture for all build/test commands.
  - Added explicit `-derivedDataPath` and `-resultBundlePath` under `iosApp/build/`.
  - Added separate `:shared:embedAndSignAppleFrameworkForXcode` and `:shared:iosSimulatorArm64Test` steps.
  - Artifact upload now captures `iosApp/build/`, `shared/build/logs/`, `shared/build/reports/`, and `shared/build/bin/`.

Status: **REPAIRED / CONFIGURED**; **UNVERIFIED** on macOS.

### T1B.2 iOS sample completion

- Added `SampleKeywordDetector` in `shared/src/commonMain/.../ui/sample/SampleKeywordDetector.kt`; it exercises the shared `KeywordDictionary` and returns a plain string for SwiftUI.
- Updated `iosApp/iosApp/ContentView.swift`:
  - Displays the shared keyword-detection result above the CMP card.
  - Toggles English/Arabic with a button that has accessibility labels/hints.
  - Uses `.id(isArabic)` on `ComposeSampleView` so the Compose UIViewController is recreated on language changes, ensuring RTL layout updates.
  - Applies `.environment(\.layoutDirection, ...)` to the root so surrounding SwiftUI layout follows the selected language.
  - Wrapped content in a `ScrollView` to support Dynamic Type / large text.
- Renamed `shared/src/iosMain/.../SampleViewController.kt` to `MainViewController.kt` to match its exported `MainViewController` function and the documentation.

Status: **REPAIRED / CONFIGURED**; **UNVERIFIED** on iOS simulator.

### T1B.3 Documentation corrections

- Corrected claims that the CMP sample was "verified on Android"; it compiles/packages but was not visually rendered on a device.
- Corrected handoff filenames to reflect the actual `MainViewController.kt` name.
- Added this T1B section to record repairs and remaining unverified state.

### T1B.4 Android regression re-checks

| Check | Command | Result |
|---|---|---|
| App debug build | `./gradlew.bat :app:assembleDebug -Pnemory.skipNativeForTests=true --console=plain` | BUILD SUCCESSFUL in 33s (649 tasks) |
| Shared unit tests | `./gradlew.bat :shared:testDebugUnitTest -Pnemory.skipNativeForTests=true --console=plain` | 7 tests, 0 failures |
| Heuristics regression tests | `./gradlew.bat :core:ai:heuristics:testDebugUnitTest -Pnemory.skipNativeForTests=true --console=plain` | 15 tests, 0 failures |
| Localization parity | `python scripts/check_localization.py` | PASS |

Notes:
- All Android checks used `-Pnemory.skipNativeForTests=true` to skip the NDK native build on this Windows host; these are **not** native-runtime validation.
- `shared/build.gradle.kts` emits deprecation warnings for `compose.runtime`/`foundation`/`material3`/`ui` and for `Project.android`; these are non-fatal with CMP 1.10.3 + AGP 9.3.2 + Kotlin 2.2.21 and should be revisited when the catalog/plugins are next upgraded.
