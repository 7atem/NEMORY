# T1B — iOS Foundation Repair and Acceptance Gate

> **Assignment:** Complete T1’s Apple-target acceptance gate.  
> **Status:** Local repairs complete. **T1 is not marked complete** because macOS/Xcode execution is unavailable in this Windows session.  
> **Source revision:** `1aa65e2f1354aae6f1fa5e4f3ff5077850583f4d` (HEAD), with the existing worktree preserved.

---

## What T1B repaired

### 1. Framework generation and Xcode integration

- **`shared/build.gradle.kts`**
  - Explicitly declared a **dynamic** iOS framework named `shared`:
    ```kotlin
    iosTarget.binaries.framework {
        baseName = "shared"
        isStatic = false
        binaryOption("bundleId", "com.vaultbrain.shared")
    }
    ```
  - This makes the Swift module name deterministic and matches the XcodeGen `shared.framework` reference.

- **`iosApp/project.yml`**
  - Added `ENABLE_USER_SCRIPT_SANDBOXING: NO` so the Gradle pre-build script can write the framework outside the Xcode sandbox.
  - Disabled simulator code signing (`CODE_SIGNING_ALLOWED: NO`, `CODE_SIGN_IDENTITY: ""`).
  - Added `LD_RUNPATH_SEARCH_PATHS: $(inherited) @executable_path/Frameworks` for dynamic framework loading.
  - Pinned `SWIFT_VERSION: "6.0"`.
  - Fixed the pre-build script to `chmod +x ./gradlew` and run with `set -e`.
  - Added input/output file declarations so Xcode can skip the script when the framework is already up to date.

- **`.github/workflows/ios.yml`**
  - Added `chmod +x ./gradlew`.
  - Added toolchain introspection: installed Xcodes, `xcodebuild -version`, `-showsdks`, `xcrun simctl list runtimes/devices`.
  - Pinned `XCODE_VERSION: 16.1` as an env var and selects it explicitly.
  - Removed the hard-coded iOS 18.1 runtime assumption; the destination is now `platform=iOS Simulator,name=iPhone 16 Pro`, which picks any compatible runtime.
  - Used `set -euo pipefail` and `tee` for every build/test command so failures are preserved and logs are captured.
  - Added explicit `-derivedDataPath iosApp/build/DerivedData` and `-resultBundlePath iosApp/build/iosApp.xcresult`.
  - Expanded artifact upload to `iosApp/build/`, `shared/build/logs/`, `shared/build/reports/`, and `shared/build/bin/`.

### 2. iOS sample completion

- Added `SampleKeywordDetector` in `shared/src/commonMain/.../ui/sample/SampleKeywordDetector.kt`; it calls the shared `KeywordDictionary.detectHierarchy(...)` and returns a plain string for SwiftUI.
- Updated `iosApp/iosApp/ContentView.swift`:
  - Shows the shared keyword-detection result.
  - Language toggle button with `accessibilityLabel`/`accessibilityHint`.
  - Uses `.id(isArabic)` on `ComposeSampleView` so the Compose UIViewController is recreated when the language changes, guaranteeing RTL layout updates.
  - Applies `.environment(\.layoutDirection, ...)` to the root for surrounding SwiftUI layout direction.
  - Wrapped the whole screen in a `ScrollView` for Dynamic Type / large-text support.
- Renamed `shared/src/iosMain/.../SampleViewController.kt` to `MainViewController.kt` to match the exported `MainViewController` function and the documentation.

### 3. Documentation corrections

- Corrected claims in `IOS_VALIDATION_LOG.md` and `T0_T1_HANDOFF_REPORT.md` that the CMP sample was "verified on Android"; it compiles/packages but was not visually rendered.
- Corrected the iOS file reference to `MainViewController.kt`.
- Updated `AGENTS.md` module map to reflect the `shared/` module and the moved `LensId`/`KeywordDictionary`.
- Updated `KMP_PORTABILITY_AUDIT.md` status and KMP/iOS target declaration section.

---

## Files changed in T1B

### Modified

- `shared/build.gradle.kts`
- `iosApp/project.yml`
- `.github/workflows/ios.yml`
- `iosApp/iosApp/ContentView.swift`
- `IOS_VALIDATION_LOG.md`
- `T0_T1_HANDOFF_REPORT.md`
- `KMP_PORTABILITY_AUDIT.md`
- `AGENTS.md`

### New

- `shared/src/commonMain/kotlin/com/vaultbrain/shared/ui/sample/SampleKeywordDetector.kt`

### Renamed

- `shared/src/iosMain/kotlin/com/vaultbrain/shared/ui/sample/SampleViewController.kt` → `MainViewController.kt`

---

## Android regression evidence (Windows, `-Pnemory.skipNativeForTests=true`)

| Check | Command | Result |
|---|---|---|
| App debug build | `./gradlew.bat :app:assembleDebug -Pnemory.skipNativeForTests=true --console=plain` | BUILD SUCCESSFUL in 33s (649 tasks) |
| Shared unit tests | `./gradlew.bat :shared:testDebugUnitTest -Pnemory.skipNativeForTests=true --console=plain` | 7 tests, 0 failures |
| Heuristics regression tests | `./gradlew.bat :core:ai:heuristics:testDebugUnitTest -Pnemory.skipNativeForTests=true --console=plain` | 15 tests, 0 failures |
| Localization parity | `python scripts/check_localization.py` | PASS |

**Important:** These results used `-Pnemory.skipNativeForTests=true`. They are **not** native-runtime validation for the Android Qwen/NDK path.

---

## What remains UNVERIFIED

Because this session is on **Windows** with no macOS/Xcode, the following are repaired/configured but not executed:

- `xcodegen generate`
- `:shared:embedAndSignAppleFrameworkForXcode`
- `:shared:iosSimulatorArm64Test`
- `xcodebuild build` for `iosApp`
- iOS simulator launch and sample rendering
- Screenshots of English/Arabic sample
- iPhone 16 Pro device install/run

No CI run URL is available because the workflow has not executed.

---

## Exact remaining access requirement

To finish T1, a **macOS host** with the following is required:

- GitHub Actions `macos-15` runner, or a local Mac with macOS 15.x.
- **Xcode 16.1** installed at `/Applications/Xcode_16.1.app` (the workflow env var `XCODE_VERSION` controls this; change it if the runner image provides a different compatible version).
- Internet access for `brew install xcodegen` and Gradle dependency resolution.
- No Apple Developer credentials are needed for unsigned simulator validation. Device installation requires signing.

Recommended macOS validation commands:

```bash
cd iosApp
xcodegen generate
cd ..
chmod +x ./gradlew
./gradlew :shared:embedAndSignAppleFrameworkForXcode --console=plain
./gradlew :shared:iosSimulatorArm64Test --console=plain
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -derivedDataPath iosApp/build/DerivedData \
  -resultBundlePath iosApp/build/iosApp.xcresult \
  build
```

---

## Blockers

1. **Environment blocker:** Windows host cannot compile Kotlin/Native iOS targets or run `xcodebuild`.
2. **CI execution blocker:** `.github/workflows/ios.yml` is configured but has not run on a `macos-15` runner.
3. **Known non-fatal warnings:** `shared/build.gradle.kts` emits deprecation warnings for `compose.runtime`/etc. and `Project.android` with CMP 1.10.3 + AGP 9.3.2 + Kotlin 2.2.21. These do not break the Android build but should be revisited during the next plugin/catalog upgrade.

---

## Boundaries respected

- No storage migration, RAG extraction, or Qwen work was started.
- Existing Android behavior and worktree changes were preserved.
- No speculative iOS success claims were made.
