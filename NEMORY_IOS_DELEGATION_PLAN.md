# Nemory iPhone implementation and coding-agent handoff

Prepared: 18 September 2026. Status: proposed plan, not an implemented port.
First physical test device: **iPhone 16 Pro**, as specified by the owner.

## 1. Recommendation

Use Kotlin Multiplatform (KMP) to share domain logic and deterministic intelligence.
Evaluate Compose Multiplatform (CMP) with one real iOS screen before committing to
sharing the full UI. Keep native iOS adapters for storage, security, capture,
notifications, lifecycle, and local inference. Preserve the Android app throughout.

Change the order in the original proposal: establish an iOS build and prove secure
storage early, then deliver a small working capture-to-vault flow. Do not move all
of RAG and the UI before discovering whether the iOS runtime and storage work.

KMP and CMP support stable iOS targets, but this does not make Android libraries or
JVM APIs portable. See the official [KMP platform status](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)
and [CMP iOS stability announcement](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/).

Cloud macOS solves access to Apple's build toolchain. It does not convert Android
source. A macOS host and Xcode are required for iOS builds; Windows remains useful
for Kotlin editing and Android/JVM validation. See [KMP quickstart](https://kotlinlang.org/docs/multiplatform/quickstart.html).

## 2. Evidence and current uncertainties

This is a targeted source review, not a completed portability audit. Existing test
successes in repository documentation are historical; no build or test suite was
rerun while preparing this plan.

| Source examined | Finding | Consequence |
| --- | --- | --- |
| `settings.gradle.kts` | Current modules are Android-oriented; no iOS/shared KMP module declaration was found. | Add an isolated foundation rather than converting the entire module graph. |
| `gradle/libs.versions.toml`, Gradle wrapper | Declared AGP 9.3.2, Kotlin 2.2.21, KSP 2.3.6, Room 2.8.4, SQLCipher Android 4.17.0, ObjectBox 5.4.2, Gradle 9.5.0. | Validate a compatible pinned KMP/CMP/Xcode matrix; these declarations are not proof of compatibility. Avoid incidental whole-project upgrades. |
| `core/common/.../model/VaultItem.kt` | Serializable domain model, but timestamp defaults call `System.currentTimeMillis()`. | Extract the model with a portable time strategy and preserve serialized fields/default behavior. |
| `core/ai/rag/.../LocalAgent.kt` | Constructor already takes `LlmClient`, but implementation calls `java.time` and accesses global decoy state. | A strong extraction candidate after portable clock/timezone and session contracts. |
| `core/ai/rag/.../ClaimVerifier.kt` | Depends on LLM/domain/external models and `javax.inject`. | Share verification after extracting its contracts; retain fail-closed behavior. |
| `core/ai/rag/.../QueryIntentParser.kt`, heuristics | JVM date/time calls and injection annotations remain. | Port behavior with date, currency, Arabic-digit, and regex fixtures. |
| `core/ai/rag/.../RagEngine.kt` | Direct Android Context, Uri, image decoding, logging, Hilt, repository and vector-store dependencies. | Separate orchestration from platform I/O; moving this file alone is insufficient. |
| `feature/vault/.../CollectionsScreen.kt` | Compose code uses Android resources and `hiltViewModel`. | Extract state-driven UI and portable resources; preserve Android composition roots. |
| `core/database/.../VaultDatabase.kt` | Actual schema version is 20. | Preserve existing Android databases and migrations. |
| `NEMORY_MASTER_DOCUMENTATION.md` | Header still says schema 16, conflicting with source and `AGENTS.md`. | Audit documentation drift against source before using it as a port specification. |
| `feature/capture/.../MediaVaultStorage.kt` | Android URIs/files, Java crypto, and Android key management. | Implement a native secure-media adapter on iOS. |
| `sync/drive/.../DriveBackupManager.kt` | Android SAF, SQLCipher snapshot export, Java crypto/ZIP, and key rewrapping. | Current backup format is not automatically an iOS migration format. |
| `.github/workflows/android.yml` | Existing native Android build, lint, unit tests, localization, migration/restore checks. | Extend validation with macOS jobs; retain Android gates. |
| Working tree | Substantial existing tracked and untracked work. | Establish an explicit source baseline; a worktree created from HEAD will omit uncommitted work. |

Read `AGENTS.md`, `NEMORY_MASTER_DOCUMENTATION.md`, and
`DYNAMIC_BRAIN_ROADMAP.md` before implementation. Recheck the actual code: the
roadmap and the working tree may describe different stages of ongoing Brain work.

Still to establish: installed iOS version on the iPhone 16 Pro, macOS access,
Apple Developer team/signing availability, deployment minimum, CI budget, and
whether importing existing Android vaults is required for the first beta.
Missing signing credentials should not block unsigned simulator work or shared
logic work. They do block a claim that TestFlight delivery is complete.

## 3. Delivery scope

| Milestone | Included | Exit evidence |
| --- | --- | --- |
| Engineering foundation | Android and iOS builds, shared logic sample, encrypted database/media spike, bilingual UI sample. | Both builds and native shared tests pass; security spike passes. |
| Private vault alpha | App lock/decoy, text/image/PDF import, OCR review, vault, item detail, collections, lexical search, local deletion. | An offline document survives save, restart, unlock, search, and reopen on iPhone 16 Pro. |
| Intelligence beta | Shared grounded retrieval, optional Qwen text runtime, capability-aware Brain, local reminders, resilient processing queue. | Real-device inference and privacy tests pass; vault remains useful without a model. |
| Distribution candidate | Recovery/export, accessibility/localization, performance, signed builds and TestFlight installation. | Reproducible artifact and device validation report. |

Defer widgets, full Qwen vision, Gmail/Tasks OAuth, Health integrations, background
sync parity, and specialist lens screens. Calendar and the Share Extension are
follow-up tickets after the core flow; move them into beta scope only if needed.
Camera capture may follow the initial picker/import slice but belongs in the
intended capture experience. Voice capture is a later platform integration.

App lock, decoy isolation, encrypted storage, review-before-save semantics, and
English/Arabic UI are part of the foundation, not optional post-launch work.
An engineering alpha can use test documents; distribution for valuable personal
documents requires a validated recovery/export story.

## 4. Target boundaries

Proposed modules, finalized by the audit:

```text
shared/domain       models, value types, repository/capability contracts
shared/intelligence heuristics, calculations, budgets, grounding, agent, RAG
shared/ui           selected state-driven Compose screens and resources
iosApp              iOS application and native adapters
existing Android    current app/features plus adapters to shared contracts
```

Keep shared domain independent of shared UI. Extract incrementally: the first
foundation needs only a small shared module, not every proposed module at once.
Android must consume each extracted implementation so there is one maintained
source of behavior. Temporary comparison fixtures are fine; permanent duplicate
business implementations are not the goal.

Use ordinary Kotlin interfaces with constructor injection for repositories,
media access, OCR, model generation, embeddings, vector search, session access,
clock/timezone, and scheduling capabilities. Use `expect`/`actual` selectively
where it simplifies platform construction. Keep Hilt in Android adapters; a
whole-app DI framework migration is outside this project.

Contract rules:

- No Android Context/Uri/Bitmap, UIKit objects, Room entities, ObjectBox entities,
  or filesystem handles in shared domain interfaces. Use opaque media IDs and
  platform-owned accessors.
- Preserve IDs, epoch timestamp units, metadata keys, serialization names,
  enum semantics, collection relationships, and source provenance.
- Model platform capabilities honestly: unavailable OCR language, missing model,
  denied permission, unavailable connector, and cancelled work are explicit states.
- Use session-aware access and invalidate asynchronous results after decoy/lock
  transitions. Checking only when a query starts is insufficient.
- Abstract repository operations at useful transaction boundaries. Privacy and
  atomicity must not depend exclusively on UI checks.

## 5. Work packages and acceptance gates

### T0 — Baseline and complete portability audit

Owner: lead coding agent. Dependencies: none. Production source changes: none.

1. Record HEAD, working-tree status, relevant untracked files, module graph,
   dependency versions, native components, and existing CI behavior. Do not
   reset, clean, stash, commit, or overwrite unrelated work automatically.
2. Audit the actual worktree. For implementation isolation, record whether a
   branch/worktree uses a committed baseline or a deliberately transferred set
   of in-progress changes. Never silently substitute HEAD for the user's work.
3. Classify every module and important dependency as reusable unchanged,
   reusable after refactor, native adapter required, or deferred/replaced.
   Include file evidence and transitive dependencies, not only import counts.
4. Inspect JVM-only APIs, Java regex behavior, locale/date handling, money
   arithmetic, synchronization, dispatcher assumptions, resources, navigation,
   persistence, billing, JNI, testing libraries, and generated code.
5. Record supported toolchain candidates and existing failures. Run the relevant
   Android baseline once; keep failures separate from future migration regressions.
6. Reconcile factual documentation drift, with source references. Do not mark
   planned iOS behavior as implemented or opportunistically repair unrelated code.

Deliver `KMP_PORTABILITY_AUDIT.md` and `IOS_VALIDATION_LOG.md`, including a module
matrix, baseline provenance, actual commands/results, unresolved blockers, and
the smallest viable extraction. Gate: reviewable evidence, no speculative reuse
percentage, and a reproducible source baseline.

### T1 — Working iOS toolchain and one shared behavior

Owner: build/shared-core agent. Depends on T0.

1. Add a minimal KMP module plus an iOS app target. Configure device arm64 and
   the simulator architecture actually used by the macOS runner. Add other
   simulator targets only when required.
2. Select and pin a supported Gradle/AGP/Kotlin/KSP/CMP/Xcode combination from
   official documentation. Isolate necessary upgrades and rerun Android checks.
   Do not copy alpha versions from current documentation without a reason.
3. Extract one small existing deterministic behavior and its fixtures. Call it
   from Android and iOS. Avoid a misleading new-only Hello World abstraction.
4. Build an English/Arabic collection/item-card sample using shared UI. Exercise
   RTL, keyboard, navigation/back, text scaling, accessibility, and platform
   lifecycle integration. Record whether CMP is suitable for the main screens.
5. Extend CI with a pinned macOS/Xcode configuration, simulator build and native
   shared tests. Preserve existing Android jobs. Keep simulator validation
   independent from release signing credentials.

Gate: Android actually uses shared code, the iOS app launches in a simulator,
shared tests run on JVM/Android and Kotlin/Native, and build commands are recorded.
If macOS is unavailable, report that gate as unverified; Windows compilation
cannot substitute for iOS evidence. Resolve access before scaling the migration.

### T2 — Encrypted persistence, secure media, and session isolation

Owner: storage/security agent. Depends on T1 contracts.

1. Keep Android Room schema 20, SQLCipher, migration history, and backup behavior
   intact. Prototype iOS storage behind shared repository interfaces first.
2. Evaluate Room KMP with a verified encrypted driver against a separate native
   SQLCipher adapter. Decide from encryption, FTS, transactions, migration,
   maintenance, and build evidence. A plain SQLite prototype is not the vault.
3. Verify that the iOS binary really links the intended SQLCipher implementation;
   check runtime cipher identity and wrong-key/plain-SQLite failure behavior.
   Test DB contents, WAL/journals, reopen, updates, deletion, and rollback.
4. Design random database/media keys and Keychain access policy, biometric/app
   authentication, file protection, and device-lock behavior. Secure Enclave is
   not a drop-in Android AES keystore: document the actual key protection scheme.
5. Implement authenticated media encryption with safe nonce handling, atomic
   writes, cancellation cleanup, authenticated reads, and bounded memory. Audit
   decoded images, PDF scratch files, thumbnails, logs, and disk caches for leaks.
6. Implement lock/decoy session behavior across repositories, UI, media readers,
   queues, retrieval and model work. Recheck results after asynchronous calls;
   clear stale state, proposals and app-switcher previews immediately.
7. Fail closed on corrupt/unavailable keys. Never silently generate replacement
   keys for an existing vault. Document device backup and Keychain restore policy.

Gate: encrypted item/media round-trip after restart; wrong key, corruption and
tamper failures; no plaintext vault material in durable caches/logs; lock/decoy
race tests; Android migration and backup/restore regression tests remain passing.

Room supports KMP, but its default SQLite drivers do not establish Nemory's
encryption guarantees. See [Room KMP setup](https://developer.android.com/kotlin/multiplatform/room).
Apple documents the constraints of [Secure Enclave keys](https://developer.apple.com/documentation/security/ksecattrtokenidsecureenclave).

### T3 — Shared domain and deterministic intelligence extraction

Owner: shared-core agent. Depends on T1; coordinates contracts with T2.

1. Move value types and selected models first, preserving public/serialized
   behavior. Keep Android mappings and platform UI types outside common code.
2. Replace JVM clock/date/timezone and locale dependencies with portable
   implementations or injected services. Use a monotonic source for elapsed
   budgets and wall-clock time for persisted dates.
3. Extract heuristics, keyword scoring, calculations, prompt contracts and
   metadata normalization in small batches. Retain precompiled regex groups;
   do not introduce regex compilation on every call.
4. Move portable tests to common tests using compatible test tools. Replace JVM
   mocking where necessary with small behavior fakes; keep Android adapter tests.
5. Exercise English/Arabic digits, Unicode boundaries, mixed-script text,
   currencies, malformed documents, timezone/DST boundaries, and rounding.

Gate: the same fixtures produce equivalent results on Android/JVM and native
iOS; Android calls the extracted code. No new platform dependencies leak into
common code, and no persisted format changes slip into a refactor.

### T4 — First complete private-vault flow

Owner: iOS capture/UI agent. Depends on T2 and relevant T3 models.

1. Implement onboarding/auth, Today/Vault navigation shell, picker-based image
   and PDF import, manual text capture, review/edit, save, detail, collections,
   archive/delete, and lexical search. Then integrate native camera capture.
2. Use native Apple capture/OCR/PDF facilities through adapters. Preserve source
   text, page association, rotation handling, resource limits, and user edits.
   Release security-scoped file access correctly after copying into the vault.
3. Discover OCR language support at runtime for the selected request revision.
   Test real Arabic samples; unsupported/poor recognition gets honest bilingual
   guidance and manual entry. Arabic UI is not evidence of Arabic OCR support.
4. Maintain review requirements and `SKIPPED_PRIVACY`. Failed OCR/enrichment must
   not destroy a saveable item. Preserve collection choice and import provenance.
5. Move reusable UI one screen at a time after the T1 UI result. Keep native
   permission, picker and biometric surfaces. If CMP has a demonstrated blocker,
   document a bounded native-screen alternative while retaining shared logic.

Gate: on iPhone 16 Pro in airplane mode, import a document, review/correct it,
save it to a collection, kill/reopen, unlock, find it and reopen its media.
Test denied permissions, corrupt/large PDFs, cancellation, Arabic RTL, Dynamic
Type, VoiceOver and decoy entry while a document is loading.

Apple provides a runtime [OCR language-support query](https://developer.apple.com/documentation/vision/recognizing-text-in-images).

### T5 — Retrieval and optional Qwen text inference

Owner: AI/runtime agent. Depends on shared contracts and T2/T3. A bounded native
model feasibility spike may start after T1 so memory/performance risk is known early.

1. Separate RagEngine orchestration from Android media decoding, logging,
   concrete repositories/vector entities, and DI. Extract LocalAgent,
   QueryIntentParser, ClaimVerifier and budget contracts incrementally.
2. Start with genuine lexical retrieval and quoted-evidence fallback. Unavailable
   generative inference must have an honest state; do not substitute fake answers.
3. Design an iOS vector/embedding adapter without assuming the Android ObjectBox
   SDK or TFLite wiring is reusable. Confirm model/tokenizer/preprocessing,
   dimensions, normalization, IDs and index-version compatibility. Preserve FTS
   fallback and rebuildability. Do not re-enable pseudo-image embeddings.
4. Build pinned llama.cpp for iOS through a small stable C-compatible bridge or
   an appropriately wrapped native framework. Define ownership, streaming,
   cancellation, shutdown and error handling; do not try to reuse JNI on iOS.
5. Evaluate Metal from the first real-device benchmark. Verify that the selected
   revision supports the exact Qwen GGUF/tokenizer/chat template. Treat the
   existing model as a candidate, not guaranteed to fit or perform adequately.
6. Support verified downloads, SHA-256, atomic installation, cancellation,
   disk-space failure, model deletion and readiness state. Separate text-model
   requirements from the vision projector if the runtime allows it; prove this
   before changing download assumptions. Keep Android model packaging intact.
7. Preserve foreground preemption, bounded context, generation cancellation,
   memory-pressure handling and decoy invalidation. Do not keep a large runtime
   resident merely to match an Android implementation detail.
8. Preserve 1/2/5 retrieval rounds, 15/45/90-second budgets, two queries per round,
   candidate cap 30, verbatim evidence verification, and explicit confirmation
   for every proposed write. Enforce guards when a proposal is confirmed, too.
9. Preserve relationship evidence/caps and endpoint visibility guards. Keep
   connector-owned ExternalRecord distinct from user-saved VaultItem.

Gate: port the deterministic 100-case benchmark and verify it on native and JVM;
record actual results, not the historical 100/100 claim. Separately validate real
OCR, retrieval relevance, generation quality, unsupported answers and streaming
on iPhone 16 Pro. A deterministic fake-model benchmark is not a device AI-quality
benchmark. App remains fully usable with model missing, cancelled or unavailable.

The upstream [llama.cpp project](https://github.com/ggml-org/llama.cpp) supports
Apple/Metal work, but device performance and the exact Nemory bridge require testing.

### T6 — Reminders and resilient processing

Owner: platform integration agent. Depends on T4; uses T5 when available.

1. Use persisted processing state and foreground resume as the reliable path for
   pending extraction/indexing. Make claims, retries and completion idempotent.
2. Use eligible BackgroundTasks as opportunistic progress with expiration and
   cancellation handling. Do not promise WorkManager-equivalent scheduling.
3. Schedule local reminder notifications directly with UserNotifications.
   Preserve snooze/complete/dismiss, stable IDs, reconciliation on reopen,
   timezone behavior, generic lock-screen text, and duplicate suppression.
4. Bound the scheduled notification set and reconcile pending reminders; inspect
   current platform limits rather than enqueueing the entire vault indefinitely.
   Handle permission denial and protected-storage unavailability explicitly.
5. Follow-up Calendar ticket: EventKit permissions, selected calendars, bounded
   retention, disconnect/revocation cleanup and system-editor confirmation for
   creation. Refresh on foreground; do not promise a fixed six-hour interval.
6. Follow-up Share Extension ticket: short bounded import, review semantics,
   encrypted App Group staging, explicit handoff, crash cleanup and isolated
   key access. No model loading or full vault exposure inside the extension.
   Do not assume the host app's in-memory decoy flag exists in another process.

Gate: a scheduled reminder is handled correctly while the app is not running;
duplicate/cancel/snooze cases pass; interrupted indexing resumes without duplicate
records; denied background execution does not prevent normal foreground use.

Apple explicitly does not guarantee the requested [background task start time](https://developer.apple.com/documentation/backgroundtasks/bgtaskrequest/earliestbegindate).
Use [local notification scheduling](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app)
for reminder delivery rather than depending on a background job to fire on time.

### T7 — Recovery and Android-to-iOS transfer decision

Owner: persistence agent. Depends on T2/T4; design format requirements during T2.

1. Provide a validated encrypted export/import or other explicitly selected
   recovery path before inviting users to store valuable data in the beta.
2. Treat existing Android snapshot archives as a separate compatibility problem:
   Room schema identity, keys, filenames, media layout and Android paths are
   not automatically portable to a different iOS storage implementation.
3. If transfer is in scope, prefer an explicitly versioned logical transfer
   format unless compatible snapshot import is demonstrated. Preserve IDs,
   metadata, timestamps, collections, memberships, relationships, reminders and
   source text. Exclude device credentials and rebuild derived indexes.
4. Validate manifests, bounds, path traversal, hashes, authentication, versions,
   duplicates, key rewrapping, staged import, rollback and interrupted restore.
   Keep the existing Android restore path compatible.

Gate: recovery round-trip is tested. If cross-platform transfer is included,
Android-to-iOS fixtures pass with linked media and collections; otherwise the UI
and release notes clearly state that transfer is unavailable. Cross-device sync
is a separate project and must not be implied by supporting both platforms.

### T8 — iPhone 16 Pro validation and distribution

Owner: lead/release agent. Depends on all in-scope previous gates.

1. Record phone model, actual iOS version, build SHA, toolchain, model hash,
   quantization, context size, thread/backend settings and corpus revision.
2. Measure cold/warm launch, OCR/page latency, search latency, time to first
   token, tokens/sec, complete-answer latency, peak memory and cancellation
   latency. Include a sustained session with thermal/battery observations.
3. Establish numeric performance budgets from the early device spike before
   optimization. Report distributions and failures, not only the best run.
   Current retrieval timeout budgets are ceilings, not promised UX latency.
4. Run capture/restart/recovery/decoy tests in airplane mode, low storage,
   permission denial, background interruption and memory-pressure scenarios.
5. Test English/Arabic string completeness, RTL, mixed-script documents,
   VoiceOver, large text, keyboard dismissal and iOS navigation conventions.
6. Prepare signing, bundle identifiers, entitlements, usage descriptions,
   dependency privacy manifests, app privacy disclosures, export-compliance
   responses and notices using the actual binary's behavior. Keep secrets out
   of the repository and avoid user vault data in CI artifacts.
7. Decide free-beta behavior explicitly. Android Play Billing cannot supply iOS
   entitlements; StoreKit and paid distribution are their own scoped work.
8. Produce a signed archive and complete the authorized TestFlight upload and
   physical installation when account access is available. Record unavailable
   access precisely; no claim of delivery from an unsigned simulator build.
9. Update AGENTS and master documentation to separate Android/iOS implementation,
   tested capability, planned work and known limitations. Re-run Android release
   gates, including native inference packaging, before declaring the port safe.

TestFlight distribution requires appropriate membership and account access;
see [Apple Developer Program](https://developer.apple.com/programs/).

## 6. Delegation and change ownership

Use one lead agent to own the baseline, shared contracts, integration and final
validation. The work packages can run sequentially with one coding agent.
If multiple agents are assigned later, parallelize only after T1 contracts:

| Assignment | Owned work | Coordination requirement |
| --- | --- | --- |
| Lead/build | T0/T1, root Gradle/version catalog, CI, documentation, integration | Sole owner of root build changes and shared API decisions. |
| Domain/intelligence | T3, shared portions of T5 | Agree storage/LLM contracts before moving callers. |
| iOS storage/security | T2/T7 | Own key policies, DB/media boundaries and migration semantics. |
| iOS experience/runtime | T4, native T5, T6 | Split capture and model tickets further only with separate file ownership. |

Each ticket or small batch gets a focused branch/PR, scope, dependencies, tests
and rollback description. Do not have several agents independently edit the
root version catalog or design competing repository contracts. Merge one shared
interface change before dependent implementations expand it.

Recommended first assignment: **T0 and T1 only**. This produces an honest audit,
a working iOS build and a real shared behavior before the costly migration.
Then assign T2 plus the early T5 device spike; continue the rest against those
results. Passing a gate permits the next assigned ticket; it does not require
repeated permission for ordinary reversible implementation choices.

## 7. Validation commands and reporting

Existing Android commands, to be verified against the chosen baseline:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :core:database:testDebugUnitTest :feature:capture:testDebugUnitTest :core:ai:vision:testDebugUnitTest :core:ai:heuristics:testDebugUnitTest :core:ai:rag:testDebugUnitTest :core:ai:llm:testDebugUnitTest
python scripts/check_localization.py
.\gradlew.bat :app:lintDebug :app:assembleRelease
```

Instrumented device/emulator checks:

```powershell
.\gradlew.bat :core:database:connectedDebugAndroidTest :sync:drive:connectedDebugAndroidTest
```

Use `-Pnemory.skipNativeForTests=true` only where appropriate for test iteration.
Such results are not production native-runtime validation; the final native
Android builds must run without that flag. Retain existing navigation and
feature tests for touched screens and add boundary tests where behavior changes.

T1 must record the actual new Gradle native-test tasks and `xcodebuild` project,
scheme and simulator destination. Do not invent these names before the project
exists. Run tests appropriate to each change; do not repeat the full release
matrix for every documentation edit.

Every agent handoff must include: baseline SHA and working-tree provenance,
changed files, behavior delivered, commands and results, artifact locations,
remaining failures/blockers, migration impact and next ticket. Use the labels
IMPLEMENTED, VERIFIED, UNVERIFIED and BLOCKED accurately. A mocked adapter,
skipped job, or placeholder connector is not a working capability.

## 8. Copy-paste first assignment

```text
Work in the Nemory repository. Read AGENTS.md, NEMORY_MASTER_DOCUMENTATION.md,
DYNAMIC_BRAIN_ROADMAP.md and NEMORY_IOS_DELEGATION_PLAN.md first.

Implement T0 and T1 from the iOS delegation plan. The first physical test device
is iPhone 16 Pro. The long-term direction is shared Kotlin domain/intelligence,
selective Compose Multiplatform UI, and native iOS platform adapters.

Begin by documenting the actual worktree and protecting all existing changes.
Do not assume HEAD includes the current implementation. Do not reset, clean,
stash or commit unrelated files. Audit every module/dependency and produce
KMP_PORTABILITY_AUDIT.md plus IOS_VALIDATION_LOG.md with file evidence and
baseline validation. Verify documentation claims against source, including the
current database schema and active Brain behavior.

Then create the smallest working KMP + iOS foundation: one existing deterministic
behavior shared by Android and iOS, portable tests on JVM and Kotlin/Native, one
bilingual shared-UI sample, and a reproducible macOS simulator build/CI job.
Preserve Android's database, security, native runtime, build checks and behavior.
Pin a proven compatible toolchain; avoid unrelated upgrades or a bulk rewrite.

Use platform contracts and constructor injection. Keep Hilt and Android APIs
out of shared code. Do not introduce cloud AI, fake connectors, weakened
encryption, automatic agent writes, production vision inference, or duplicated
long-term business logic. Do not claim iOS success without a real Apple-target
build. Continue independent authorized work if access is missing, and report
the exact unavailable environment or credentials without fabricating validation.

Finish this bounded assignment after T0/T1 and report evidence, changed files,
commands/results, artifacts, blockers, and recommended T2/T5 spike scope.
Do not begin the full database, RAG or UI migration in this assignment.
```

## 9. Scheduling expectations

Estimate after T0/T1, not from presumed code-reuse percentages. The project spans
build tooling, security, capture/UI, persistence, native inference and release
validation; agent-written code alone does not complete these areas. Use working
milestones as the schedule: simulator foundation, secure offline vault on the
phone, grounded text AI on the phone, then recoverable TestFlight beta.

The iPhone 16 Pro target makes the first benchmark concrete. It does not yet
establish the minimum supported iPhone, minimum iOS version, Qwen performance,
or whether the app will satisfy memory limits during sustained use.
