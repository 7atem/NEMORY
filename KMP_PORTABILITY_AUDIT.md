# Nemory iOS/KMP Portability Audit

> **Status:** T0 baseline audit + T1 foundation + T1B local repairs complete. Apple-target build is **UNVERIFIED** (Windows session).  
> **Baseline HEAD:** `1aa65e2f1354aae6f1fa5e4f3ff5077850583f4d`  
> **Worktree state:** 268 changed entries (167 tracked modifications + 101 untracked files).  
> **Audit date:** 18 September 2026  
> **Auditor environment:** Windows; no macOS/Xcode available in this session.

## 1. Executive summary

A Kotlin Multiplatform (KMP) iOS port is technically credible because the deterministic intelligence layer and domain models are largely plain Kotlin. However, the current repository is an Android-only Gradle project with no shared module, no iOS targets, and deep Android dependencies in storage, security, capture, RAG orchestration, vector store, LLM runtime, and UI. The first assignment (T0/T1) must therefore create a clean shared foundation and prove one real shared behavior before any large-scale migration is attempted.

Key findings:

- **No KMP/iOS targets exist.** Every module uses `com.android.library` or `com.android.application`.
- **Hilt** is used in core modules; it cannot compile in shared KMP code.
- **Room + SQLCipher Android** and **ObjectBox Android** are not portable. iOS needs native adapters behind shared repository interfaces.
- **RAG and UI** are tightly coupled to Android Context/Uri/Bitmap, resources, Hilt ViewModels, and navigation.
- **Qwen runtime** is a JNI/NDK shared library; iOS requires a separate Metal llama.cpp build.
- **Heuristics and keyword scoring** are the smallest viable T1 extraction: pure Kotlin + regex, with only a few `java.time`/`System.currentTimeMillis()` calls to abstract.
- **Documentation drift** exists: `NEMORY_MASTER_DOCUMENTATION.md` claimed Room schema 16; source is schema 20. Reconciled in this assignment.

## 2. Baseline provenance

The effective baseline is the current worktree, not `HEAD`. A branch or worktree created from `HEAD` would omit substantial in-flight work.

| Area | Nature of outstanding change | Evidence files |
|---|---|---|
| Brain / RAG | New untracked `LocalAgent`, `ClaimVerifier`, `AgentToolExecutor`, `DailyIntelligence`, `ProactiveInsightWorker`, benchmark tests, Android-only `DailyInsightStore`. | `core/ai/rag/src/main/java/.../LocalAgent.kt`, `ClaimVerifier.kt`, `AgentToolExecutor.kt`, `DailyIntelligence.kt`, `ProactiveInsightWorker.kt`; `core/ai/rag/src/test/java/.../IntelligenceBenchmarkTest.kt` |
| Today / Home | Insight strings, attention logic, dynamic refresh with cancellation/coalescing. | `feature/vault/.../HomeViewModel.kt`, `feature/vault/src/main/res/values*/insight_strings.xml` |
| LLM | New untracked `QwenLlmClient`, `ReasoningBudget`, `ArabicDocumentTranscriber`, `ArabicScriptCoverage`, `DocumentUnderstandingV2`, llama JNI bridge; deleted `GemmaLlmClient`/`GemmaModelManager`. | `core/ai/llm/src/main/java/.../QwenLlmClient.kt`, `ReasoningBudget.kt`, `llama/` package, `core/ai/llm/src/main/cpp/llama_bridge.cpp` |
| Database | Schema bumped to **20**; new `DerivedFactEntity`, `KnowledgeGraphEntities`, `KnowledgeRepository`, migrations 17–20. | `core/database/.../VaultDatabase.kt`, `VaultDatabaseMigrations.kt`, `Schema20MigrationTest.kt` |
| Security | `AuthManager` modified. | `core/security/.../AuthManager.kt` |
| Capture | `MediaVaultStorage`, `CaptureViewModel`, `DeferredItemAnalyzer`, review/share flow, `DocumentKnowledgeIndexer`. | `feature/capture/.../MediaVaultStorage.kt`, `CaptureViewModel.kt`, `CaptureFlowScreen.kt` |
| Settings / Connections | `PrivacyScoreScreen`, Google/connection strings, unconfigured Gmail. | `feature/settings/.../PrivacyScoreScreen.kt`, `feature/settings/src/main/res/values*/google_strings.xml` |
| Strings | Heavy bilingual additions across modules. | Many `res/values*/strings.xml`, `assessment_strings.xml`, `agent_strings.xml`, `google_strings.xml`, `insight_strings.xml` |
| Build / Tooling | Modified `libs.versions.toml`, `gradle.properties`, module `build.gradle.kts`; new CMake/llama bridge. | `gradle/libs.versions.toml`, `gradle.properties`, `core/ai/llm/build.gradle.kts` |
| Docs / artifacts | New planning docs, UX review images, evaluation artifacts, layout dumps. | `NEMORY_IOS_DELEGATION_PLAN.md`, `DYNAMIC_BRAIN_ROADMAP.md`, `device-ux-review/`, `evaluation_images/` |

**Decision:** Preserve the worktree as the T0/T1 baseline. Do not reset, clean, stash, or commit unrelated files automatically.

## 3. Module and dependency inventory

### 3.1 Module graph (`settings.gradle.kts`)

```text
app
shared                              <-- new KMP module (T1)
core/common, core/database, core/integrations, core/vectorstore,
core/security, core/notifications, core/billing,
core/ai/embeddings, core/ai/rag, core/ai/llm, core/ai/heuristics, core/ai/vision
feature/vault, feature/capture, feature/brain, feature/briefing,
feature/lens-money, feature/lens-health, feature/lens-travel,
feature/lens-bureaucracy, feature/lens-media, feature/settings, feature/voice
sync/drive, sync/gmail
evaluation (opt-in via includeEvaluation=true)
```

### 3.2 Key dependency versions

| Component | Version | iOS portability |
|---|---|---|
| Gradle | 9.5.0 | Host supports KMP; iOS compilation requires macOS. |
| AGP | 9.3.2 | Android-only. |
| Kotlin | 2.2.21 | KMP stable; CMP available. |
| KSP | 2.3.6 | Android-only code gen (Room/Hilt). |
| Room | 2.8.4 | Android-only today. Room KMP exists but needs a verified encrypted driver. |
| SQLCipher | 4.17.0 (`sqlcipher-android`) | Android-only. iOS needs separate SQLCipher/encrypted SQLite. |
| ObjectBox | 5.4.2 | Android-only SDK in this graph. |
| Compose BOM | 2024.12.01 | Android Jetpack Compose; CMP would use a separate catalog. |
| Hilt | 2.57.2 | Android-only DI; cannot be used in shared KMP code. |
| WorkManager | 2.10.0 | Android-only scheduling. |
| CameraX | 1.4.1 | Android-only. |
| ML Kit text/barcode/labeling | 19.0.1 / 18.3.1 / 16.0.8 | Android-only; iOS uses Apple Vision/AVFoundation. |
| MediaPipe Tasks Text | 1.0.0 | Android-only embedding runtime. |
| TFLite / LiteRT | 1.4.2 / 2.16.1 | Android-only runtime. |
| ONNX Runtime | 1.19.0 (`onnxruntime-android`) | Android-only. |

### 3.3 KMP/iOS target declaration

`shared/build.gradle.kts` applies `kotlin-multiplatform` and declares `androidTarget()`, `iosArm64()`, and `iosSimulatorArm64()`. The iOS frameworks are configured as a dynamic `shared` framework (`baseName = "shared"`, `isStatic = false`). Android compilation is verified; iOS compilation is **UNVERIFIED** on this Windows host.

## 4. Portable-code classification

### 4.1 `core/common` — models and value types

**Verdict:** Mostly portable with targeted fixes.

- `VaultItem.kt:18–19` uses `System.currentTimeMillis()` as default parameter values. Must replace with injected `Clock` or `expect`/`actual`.
- `VaultReminder.kt:16–17` has the same `System.currentTimeMillis()` default issue.
- `MetadataValueNormalizer.kt` uses `java.time.LocalDate`, `DateTimeFormatter`, `Locale.ROOT`, `Locale.ENGLISH`. Must be replaced with `kotlinx-datetime` or injected date/locale services.
- `OnboardingState.kt`, `UserExperienceFrequency.kt`, `AppearancePreferences.kt`, `PermissionHelper.kt` use `Context`/`SharedPreferences` and should stay Android-only.

**Action:** Extract pure models (`VaultItem`, `PersonalCollection`, `RelationshipType`, `Classification`, `LensId`, `ExperienceId`, `ProcessingState`, `EnrichmentState`, etc.) first. Keep `MetadataValueNormalizer` in Android until date handling is abstracted.

### 4.2 `core/ai/rag` — retrieval, agent, grounding

**Verdict:** Refactor required; strong extraction candidate after abstraction.

| File | Issue | Evidence |
|---|---|---|
| `RagEngine.kt` | `Context`, `Uri`, `ImageDecoder`, `MediaStore`, `android.util.Log`, `@Singleton @Inject`, `System.currentTimeMillis()`, `SimpleDateFormat`, Room entity references, `kotlinx.coroutines.android`. | imports and constructor at top of file; image decoding near line 187; logging and date formatting throughout |
| `LocalAgent.kt` | `java.time.LocalDate.now()` and `ZoneId.systemDefault()` in prompts; references global `DecoySessionState`. Otherwise mostly pure Kotlin + `kotlinx.serialization`. | line ~90 |
| `ClaimVerifier.kt` | `javax.inject.Inject`, `java.time.Instant`, `ZoneId`. | imports |
| `QueryIntentParser.kt` | Heavy `java.time.Instant/LocalDate/ZoneId`, `System.currentTimeMillis()`, default `ZoneId.systemDefault()`. | imports and date helpers |
| `AgentToolExecutor.kt` | `java.time.LocalDate/ZoneId/Instant`, `java.math.BigDecimal`, `javax.inject.Inject`, `VaultReminderDao` Room dependency. | imports and tool implementations |
| `DailyIntelligence.kt` | `javax.inject.Inject`, `System.currentTimeMillis()`. | imports and ranking code |
| `DailyInsightStore.kt` | Android `Context`, `EncryptedFile`, `SharedPreferences`. | imports |
| `ProactiveInsightWorker.kt` | Android `Context`, `HiltWorker`, `WorkManager`. | imports |

**Action:** Extract `LocalAgent` parsing logic and `ClaimVerifier` after abstracting clock/timezone and `LlmClient`. Split `RagEngine` into portable orchestration versus Android image decoding/logging/repository wiring.

### 4.3 `core/ai/heuristics` — deterministic extraction

**Verdict:** Mostly portable; the best T1 shared-behavior candidate.

- `HeuristicExtractor.kt` (~1122 lines) is largely regex/text math. Uses `javax.inject.Inject`, `java.time.LocalDate.now()` at ~line 522, `System.currentTimeMillis()` at ~line 818, default `Locale` via `lowercase()`. No Android types.
- `ReceiptFieldExtractor.kt` is pure Kotlin + regex + `MetadataValueNormalizer`.
- `KeywordDictionary.kt` / `ExperienceKeywordLibrary.kt` are pure regex; compilation is lazy (`by lazy`). No Android dependencies.
- `ExperienceParserRegistry.kt` uses `javax.inject.Inject`.

**Action:** Extract heuristics and keyword scoring into `shared/intelligence` with an injected clock/locale. This becomes the first deterministic behavior shared by Android and iOS.

### 4.4 `core/ai/llm` — on-device LLM

**Verdict:** Android-only; no reuse path to iOS.

- `QwenLlmClient.kt` uses `Context`, `android.util.Log`, `System.loadLibrary("nemory_llama")`, JNI bridge `LlamaBridge`, `java.io.File`.
- `LlamaBridge.kt` declares `external fun nativeLoad/nativeGenerate/nativeFree` for `libnemory_llama.so`.
- `build.gradle.kts` uses CMake, NDK `27.2.12479018`, `abiFilters arm64-v8a, x86_64`.
- `CMakeLists.txt` fetches llama.cpp `5ac84719` (tagged v0.4.0) and builds a JNI shared library.
- Other clients (`NanoPromptClient`, `OnnxLlmClient`, `QnnLlmClient`) are Android-only or stubs.

**Action:** Preserve the `LlmClient` interface as a shared contract; keep Android JNI implementation; build a separate Metal llama.cpp runtime for iOS behind the same interface.

### 4.5 `core/database` — Room + SQLCipher

**Verdict:** Android-only; migration path must be preserved on Android while iOS builds its own storage.

- `VaultDatabase.kt` declares Room database with `version = 20` and 14 entity types.
- `VaultDatabaseMigrations.kt` contains migrations 1→2 through 19→20 plus an FTS rebuild.
- Uses `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` and `System.loadLibrary("sqlcipher")`.

**Action:** Keep Android Room schema 20 + SQLCipher + full migration history intact. Prototype iOS storage behind shared repository interfaces; decide later whether Room KMP with an encrypted driver is viable.

### 4.6 `core/vectorstore` — ObjectBox

**Verdict:** Android-only.

- `VectorStore.kt` uses `Context`, `io.objectbox.BoxStore`, `MyObjectBox.builder().androidContext(...)`, Hilt.

**Action:** Implement an iOS vector/embedding adapter (e.g., Core ML/Metal ANN or SQLite vec0) behind a shared interface.

### 4.7 `feature/capture/MediaVaultStorage.kt`

**Verdict:** Android-only; iOS needs native secure-media adapter.

- Uses `Context`, `Uri`, `contentResolver`, `OpenableColumns`, `java.io.File`, `java.security.SecureRandom`, `javax.crypto.Cipher` (AES/GCM), `KeystoreManager`, `DecoySessionState`.
- Custom encrypted format: magic `NMED` + version + IV + AES-GCM ciphertext.

**Action:** Replicate the encryption format on iOS using Keychain, file protection, and security-scoped URLs; implement behind a shared media-repository contract.

### 4.8 Feature UI screens

| Screen | Blocking Android coupling |
|---|---|
| `CollectionsScreen.kt` | `stringResource`, `pluralStringResource`, `hiltViewModel()`, Material3 Compose. |
| `HomeScreen.kt` | `hiltViewModel()`, `ActivityResultContracts`, `ClipboardManager`, `LocalContext`, `stringResource`, `collectAsStateWithLifecycle`, intent launching. |
| `ItemDetailScreen.kt` | `hiltViewModel()`, `Context`, `PdfDocument`, `MediaStore`, `Toast`, `Palette`, `java.time.*`, Coil, `AnnotatedString`, export to Downloads. |
| `BrainChatScreen.kt` | `hiltViewModel()`, `LocalContext`, `stringResource`, `POST_NOTIFICATIONS` permission, `PackageManager`, `ActivityResultContracts.RequestPermission`, `Toast`, `DateFormat`. |

**Action:** Extract state holders and resources first. Evaluate Compose Multiplatform with one small shared screen; fall back to native iOS screens if CMP proves blocked.

### 4.9 Sync modules

- `DriveBackupManager.kt`: Android `Context`, `Uri`, `contentResolver`, SQLCipher snapshot export, Java crypto/ZIP, key rewrapping. Android-only.
- `GmailConnector.kt`: Mostly pure Kotlin/Hilt but depends on unconfigured `GmailDataSource` and Google Identity Services.

**Action:** Backup format and Gmail connector need iOS-native adapters. Concepts (archive, passphrase, bounded retention) can be shared.

## 5. Documentation drift

| Claim | Location | Actual | Resolution |
|---|---|---|---|
| Room schema **16** | `NEMORY_MASTER_DOCUMENTATION.md:16` and `:464` | `VaultDatabase.kt` declares `version = 20` | Updated master doc header and section 4.2 to schema 20; added schema 20 bullet. |
| Room schema **20** | `AGENTS.md` | Source uses schema 20 | Already correct. |
| Schema 19 added relationships | `NEMORY_MASTER_DOCUMENTATION.md:496` | True; schema 20 extended them | Added schema 20 bullet explaining `evidence`/`confidence`/`createdAt` extension. |
| Gmail/tasks linking | `DYNAMIC_BRAIN_ROADMAP.md` | `GmailConnector` exists but data source is unconfigured; no Tasks OAuth | Confirmed as planned, not implemented. |

## 6. Native components

| Component | Location | iOS implication |
|---|---|---|
| llama.cpp JNI bridge | `core/ai/llm/src/main/cpp/llama_bridge.cpp` | Android-only JNI. iOS needs a C/Objective-C++ bridge with Metal backend. |
| CMake build | `core/ai/llm/src/main/cpp/CMakeLists.txt` | Fetches llama.cpp `5ac84719` (v0.4.0). iOS should pin the same revision with `-DGGML_METAL=ON`. |
| STB image | `core/ai/llm/src/main/cpp/third_party/stb_image.h` | Reusable on iOS. |
| NDK ABI filter | `core/ai/llm/build.gradle.kts` | `arm64-v8a`, `x86_64`. iOS needs `arm64` (device) and `arm64` simulator on Apple Silicon. |
| Model config | `gradle.properties:23–28` | Qwen GGUF + mmproj URLs/SHA-256/sizes. Download/verify logic can be shared conceptually; packaging differs. |

## 7. Blockers and smallest viable extraction

### 7.1 Blockers

1. ~~No shared KMP module exists.~~ **Resolved in T1** (`:shared` module added with `androidTarget()`, `iosArm64()`, and `iosSimulatorArm64()`).
2. Hilt permeates core modules.
3. Room + SQLCipher and ObjectBox are Android-only.
4. Android-only media/security stack.
5. Android-only capture pipeline.
6. JNI-based Qwen runtime.
7. Android resources and navigation in feature screens.

### 7.2 Smallest viable T1 extraction

**`core/ai/heuristics` deterministic logic** is the best first shared behavior:

- No Android imports in `HeuristicExtractor.kt` (only `javax.inject`).
- `ExperienceKeywordLibrary.kt` and `KeywordDictionary.kt` are pure Kotlin + regex.
- `ReceiptFieldExtractor.kt` is pure Kotlin.
- Deterministic, testable with identical fixtures on JVM and Kotlin/Native.
- Only blockers: `java.time.LocalDate.now()`, `System.currentTimeMillis()`, `javax.inject.Inject`, and `MetadataValueNormalizer` JVM date usage.

**T1 actual extraction:**

```text
shared/
  build.gradle.kts
  src/
    commonMain/kotlin/com/vaultbrain/shared/
      domain/LensId.kt                 (moved from core/common)
      intelligence/KeywordDictionary.kt (moved from core/ai/heuristics)
      ui/sample/SharedSampleScreen.kt   (new bilingual CMP card)
    commonTest/kotlin/com/vaultbrain/shared/intelligence/KeywordDictionaryTest.kt
    androidMain/...                     (placeholder for future actual/expect Android code)
    iosMain/kotlin/com/vaultbrain/shared/ui/sample/
      MainViewController.kt
      SampleViewControllerFactory.kt
```

- `LensId` was removed from `core/common/.../LensId.kt` and recreated in `shared/src/commonMain/kotlin/com/vaultbrain/shared/domain/LensId.kt`. `core/common/build.gradle.kts` now exposes it via `api(project(":shared"))`, so existing Android callers did not change packages.
- `KeywordDictionary` was removed from `core/ai/heuristics/.../KeywordDictionary.kt` and recreated in `shared/src/commonMain/kotlin/com/vaultbrain/shared/intelligence/KeywordDictionary.kt`. `core:ai:heuristics` consumes it via `implementation(project(":shared"))`.
- `app/build.gradle.kts` also depends on `:shared` directly.
- `HeuristicExtractor` remains in `core:ai:heuristics` for now because it still depends on `MetadataValueNormalizer` (JVM date/locale) and a couple of `java.time`/`System.currentTimeMillis()` calls; those must be abstracted before extraction.

**Recommended next extractions (T2/T3):**

```text
shared/
  domain/        VaultItem, PersonalCollection, RelationshipType, Classification, ExperienceId, ProcessingState, EnrichmentState
  intelligence/  HeuristicExtractor (after clock/locale abstraction), ExperienceKeywordLibrary, ReceiptFieldExtractor, bounded arithmetic/date contracts
```

## 8. Environment limits for this session

- **Windows host:** Cannot compile Kotlin/Native iOS targets or run `xcodebuild`.
- **No Apple Developer credentials:** Cannot sign or install on iPhone 16 Pro.
- **macOS CI runner:** Unknown whether an Apple Silicon GitHub Actions runner is available.

Therefore, T1 must configure the Apple targets and CI job, but the actual simulator/device build must be labeled **UNVERIFIED** until run on macOS.
