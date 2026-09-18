# T0/T1 iOS/KMP Foundation — Handoff Report

> **Scope:** Stages 0 and 1 of `NEMORY_IOS_DELEGATION_PLAN.md` only.  
> **Status:** Complete on Android/Windows. Apple-target steps are **UNVERIFIED** until a macOS runner is available.  
> **Do not start T2 (storage/security) or T5 (RAG/Qwen) here.**

---

## What was delivered

### T0 — Audit and baseline

- `KMP_PORTABILITY_AUDIT.md` — evidence-backed inventory of modules, dependencies, portable vs. Android-only code, and blockers.
- `IOS_VALIDATION_LOG.md` — command-by-command evidence and `UNVERIFIED` labels.
- Documentation drift reconciled: `NEMORY_MASTER_DOCUMENTATION.md` now correctly states Room schema **20** (was 16).
- Baseline preserved: the worktree is treated as the active baseline; no reset/stash was performed.

### T1 — iOS foundation and one shared behavior

| Deliverable | Location | Status |
|---|---|---|
| Shared KMP module | `shared/` | VERIFIED (Android compiles); iOS target declared but **UNVERIFIED** |
| Shared domain value type | `shared/src/commonMain/kotlin/com/vaultbrain/shared/domain/LensId.kt` | VERIFIED |
| Shared deterministic behavior | `shared/src/commonMain/kotlin/com/vaultbrain/shared/intelligence/KeywordDictionary.kt` | VERIFIED (7/7 tests) |
| Portable tests | `shared/src/commonTest/kotlin/com/vaultbrain/shared/intelligence/KeywordDictionaryTest.kt` | VERIFIED |
| Bilingual CMP sample | `shared/src/commonMain/.../ui/sample/SharedSampleScreen.kt` | COMPILES on Android; **UNVERIFIED** on iOS |
| Android sample host | `app/src/main/java/com/vaultbrain/app/sample/SharedSampleActivity.kt` + `AndroidManifest.xml` | COMPILES; not visually rendered |
| iOS sample host | `iosApp/iosApp/ContentView.swift`, `shared/src/iosMain/.../MainViewController.kt` | CONFIGURED; **UNVERIFIED** |
| macOS simulator CI | `.github/workflows/ios.yml` + `iosApp/project.yml` | CONFIGURED; **UNVERIFIED** |

---

## Files changed for T0/T1

### New files

- `shared/build.gradle.kts`
- `shared/src/commonMain/kotlin/com/vaultbrain/shared/domain/LensId.kt`
- `shared/src/commonMain/kotlin/com/vaultbrain/shared/intelligence/KeywordDictionary.kt`
- `shared/src/commonMain/kotlin/com/vaultbrain/shared/ui/sample/SharedSampleScreen.kt`
- `shared/src/commonTest/kotlin/com/vaultbrain/shared/intelligence/KeywordDictionaryTest.kt`
- `shared/src/iosMain/kotlin/com/vaultbrain/shared/ui/sample/MainViewController.kt`
- `shared/src/iosMain/kotlin/com/vaultbrain/shared/ui/sample/SampleViewControllerFactory.kt`
- `shared/src/commonMain/kotlin/com/vaultbrain/shared/ui/sample/SampleKeywordDetector.kt`
- `app/src/main/java/com/vaultbrain/app/sample/SharedSampleActivity.kt`
- `iosApp/iosApp/ContentView.swift`
- `iosApp/iosApp/iosAppApp.swift`
- `iosApp/iosApp/Info.plist`
- `iosApp/project.yml`
- `.github/workflows/ios.yml`
- `KMP_PORTABILITY_AUDIT.md`
- `IOS_VALIDATION_LOG.md`

### Modified files

- `settings.gradle.kts` — includes `:shared`.
- `build.gradle.kts` — applies KMP/CMP plugins at the top level.
- `gradle/libs.versions.toml` — adds `composeMultiplatform = "1.10.3"` and plugin aliases.
- `app/build.gradle.kts` — `implementation(project(":shared"))`.
- `core/common/build.gradle.kts` — `api(project(":shared"))` so `LensId` stays available transitively.
- `core/ai/heuristics/build.gradle.kts` — `implementation(project(":shared"))`.
- `app/src/main/AndroidManifest.xml` — declares `SharedSampleActivity`.
- `NEMORY_MASTER_DOCUMENTATION.md` — schema header corrected to 20.

### Removed/moved files

- `core/common/src/main/java/com/vaultbrain/core/common/model/LensId.kt` → moved to `shared/.../LensId.kt`.
- `core/ai/heuristics/src/main/java/com/vaultbrain/core/ai/heuristics/KeywordDictionary.kt` → moved to `shared/.../KeywordDictionary.kt`.

All Android callers of `LensId` and `KeywordDictionary` were updated to import the shared versions (package change + new location).

---

## Evidence from this session

| Check | Command | Result |
|---|---|---|
| App debug build | `./gradlew.bat :app:assembleDebug -Pnemory.skipNativeForTests=true --console=plain` | BUILD SUCCESSFUL in 12s |
| Shared unit tests | `./gradlew.bat :shared:testDebugUnitTest --rerun-tasks --console=plain` | 7 tests, 0 failures |
| Heuristics regression tests | `./gradlew.bat :core:ai:heuristics:testDebugUnitTest --rerun-tasks --console=plain` | 15+ tests, 0 failures |
| Localization parity | `python scripts/check_localization.py` | PASS |

Notes:
- The full `./gradlew.bat testDebugUnitTest` across every module was **not rerun** in this turn; only `:shared` and `:core:ai:heuristics` were executed.
- `:app:assembleDebug` uses `-Pnemory.skipNativeForTests=true` to avoid the NDK native build on this Windows host.

---

## What is intentionally UNVERIFIED

Because this session runs on **Windows** with no macOS/Xcode available, the following are configured but not proven:

1. **iOS simulator build** — requires `xcodebuild` on macOS.
2. **Kotlin/Native iOS compilation** — `iosArm64()` / `iosSimulatorArm64()` targets are declared but not compiled.
3. **`.github/workflows/ios.yml`** — file is present; actual execution on a `macos-15` runner has not happened.
4. **iOS CMP sample rendering** — `MainViewController.kt` and `SampleViewControllerFactory.kt` are present but not executed.
5. **iPhone 16 Pro device testing** — no device connected, no Apple Developer signing.

These must be validated on a macOS host (local machine or GitHub Actions) before claiming iOS success.

---

## Blockers for the next assignment

### Already resolved in T1
- No shared KMP module existed.

### Still unresolved (do not underestimate these)
1. **Hilt** permeates core modules; it cannot be used inside `shared/`.
2. **Room + SQLCipher Android** and **ObjectBox Android** are not portable. iOS storage needs native adapters behind shared repository interfaces.
3. **Android-only media/security stack** (`MediaVaultStorage`, Keystore, encrypted files) must be replicated on iOS with Keychain + file protection.
4. **Android-only capture pipeline** (CameraX, ML Kit, PDF/Bitmap decoding) needs Apple-native equivalents.
5. **JNI-based Qwen runtime** (`core/ai/llm`) needs a separate Metal llama.cpp build for iOS.
6. **Feature UI screens** use Android resources, Hilt ViewModels, and platform intents; CMP may help but will require heavy abstraction.

---

## Recommended next-agent scope

### Immediate: validate the iOS build on macOS
Run the T1 configuration on a macOS host before any further migration:

```bash
cd iosApp
xcodegen generate
cd ..
./gradlew :shared:embedAndSignAppleFrameworkForXcode
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.1' build
./gradlew :shared:iosSimulatorArm64Test
```

Fix any framework-linking, signing, or CMP resource issues first. Do not proceed to T2 until the simulator build and at least one shared Kotlin/Native test pass.

### T2 — Security and storage (next coding assignment)
- Keep Android Room schema 20 + SQLCipher intact.
- Add an iOS database prototype behind a shared repository contract; prove encryption end-to-end before choosing Room KMP vs. a native encrypted SQLite driver.
- Implement iOS app-lock/decoy isolation equivalent to `DecoySessionState`.
- Acceptance: save/restart/reopen works; wrong-key, corruption, and privacy-race tests pass.

### T5 — Brain and Qwen (separate spike, not mixed with T2)
- Extract `RagEngine` orchestration from Android image APIs, Hilt, and repositories.
- Build Metal llama.cpp runtime for iOS and benchmark on the iPhone 16 Pro **early**:
  - time to first token,
  - tokens/second,
  - peak memory,
  - cancellation responsiveness,
  - sustained thermal behavior.
- Implement grounding/verification tests on iOS; ensure missing models never block the vault.

---

## Boundaries for the next agent

- **Do not start T2 inside this branch without a new assignment.**
- **Do not claim iOS success without a real Apple-target build.**
- **Preserve the Android baseline.** Any refactor that breaks `:app:assembleDebug` or the existing unit tests must be reverted.
- **Keep deterministic logic platform-agnostic.** Inject clocks, locales, and platform gateways; never add `Context`, `Uri`, Hilt, or Android resources into `shared/`.

---

## T1B addendum — iOS foundation repairs (same Windows session)

### What changed in T1B

- `shared/build.gradle.kts`: explicit dynamic `shared` framework configuration (`baseName`, `isStatic = false`, `bundleId`).
- `iosApp/project.yml`: XcodeGen fixes — sandboxing disabled, simulator code signing disabled, runpath search paths, Swift 6, robust pre-build script with `chmod +x gradlew` and input/output files.
- `.github/workflows/ios.yml`: reproducibility fixes — `chmod +x gradlew`, toolchain introspection, pinned `XCODE_VERSION`, generic simulator destination (no OS assumption), pipefail+tee log capture, explicit derived data / result bundle paths, expanded artifact upload.
- New `SampleKeywordDetector.kt` exercising `KeywordDictionary` from the iOS sample.
- Updated `ContentView.swift`: shared detection result display, language toggle with accessibility labels, `.id(isArabic)` recreation for RTL updates, root layout-direction environment, `ScrollView` for large text.
- Renamed `SampleViewController.kt` → `MainViewController.kt` to match the exported function and docs.
- Corrected handoff claims that the CMP sample was "verified on Android"; it compiles but was not visually rendered.

### Still UNVERIFIED

All Apple-target steps remain unverified because this session still runs on Windows with no macOS/Xcode:

- `:shared:embedAndSignAppleFrameworkForXcode`
- `:shared:iosSimulatorArm64Test`
- `xcodegen generate` + `xcodebuild build`
- iOS simulator launch and sample rendering
- iPhone 16 Pro device testing

### Exact remaining access requirement

To finish T1, a macOS host (local or GitHub Actions `macos-15`) with Xcode 16.1 installed is required. The current workflow expects `/Applications/Xcode_16.1.app`. No Apple Developer credentials are required for simulator validation; device installation requires signing.
