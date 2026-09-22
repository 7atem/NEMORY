# VaultBrain / Nemory — Architecture Guide

On-device personal vault for documents and life admin. Kotlin, Jetpack Compose, Hilt,
Room (SQLCipher-encrypted), WorkManager, ML Kit, ObjectBox (vector store), TFLite.
App id is `com.nemory.app`; the user-facing name is Nemory.

The detailed product, architecture, privacy, testing, and release reference is
`NEMORY_MASTER_DOCUMENTATION.md`. Keep this implementation guide and that master
document aligned with source changes.

> **Nemory Intelligence V1 architecture is complete. V1.1 is the Quality, Performance, Validation and Release-Hardening milestone. Full CPU Qwen-VL vision is experimental and disabled for normal production use.**

Assessment & validation status (14 September 2026):
- Current Room schema is v20, extending the v19 `relationships` table (behavior-driving `RelationshipType` enum: `RENEWS`, `REPLACES`, `PAYMENT_FOR`, `BELONGS_TO`, `SAME_ENTITY`, `VERSION_OF`) with `evidence`, `confidence`, and `createdAt` columns. Relationship reads exclude archived/stealth endpoints and recheck decoy state after the query; writes go through `KnowledgeRepository.replaceRelationships` (decoy/archived/stealth/SKIPPED_PRIVACY guards, verbatim evidence, cap 24).
- Retrieval loop: `LocalAgent` in `core/ai/rag` performs read-only retrieval rounds bounded by `ReasoningBudget` (FAST/NORMAL/DEEP: 1/2/5 rounds, 15/45/90 seconds), with at most two queries per round and a 30-item candidate cap.
- Benchmark: 100-case deterministic JVM `IntelligenceBenchmarkTest.kt` passes 100/100 across retrieval recall, arithmetic, grounding, anti-hallucination, and cross-document reasoning. `ComplicatedScenariosBenchmarkTest.kt` adds 37 realistic messy-scenario contracts (cross-document sums, expiry/renewal windows, trap/anti-hallucination, lens-boundary classification, proactive ranking, bilingual retrieval) over a pinned clock; all 37 pass. The six issues it originally exposed are fixed in production: near-duplicate bills are suppressed and disclosed in sum facts, "renew in the next N days" parses to an expiry window (bilingual), expiring-soon excludes documents superseded via REPLACES/VERSION_OF/RENEWS relationships, and flight-invoice/visa/insurance-policy classification traps route correctly.
- Widget & alert privacy: `VaultBrainWidget` displays public launch shortcuts only (no unauthenticated previews on home screen); `WidgetUpdateWorker.clearLegacyState()` purges old Glance prefs; `AlertWorker` sets `NotificationCompat.VISIBILITY_PRIVATE` with generic titles/bodies to prevent lockscreen leakage, and uses atomic `claimVisible()` against duplicate delivery races.
- Claim Grounding & Verification: `ClaimVerifier` enforces verbatim citations across both `VaultItem` and `ExternalRecord` without length caps, failing closed to quoted excerpts. `RagEngine.queryStream` verifies evidence once after token generation finishes and retains `answer = result.text` during the verification status chunk to eliminate UI flicker and text wiping.
- Media Vault File-at-Rest Security: `MediaVaultStorage` stores files encrypted with AES-256-GCM at rest using hardware-backed keystore credentials, supports transparent streaming decryption via Coil's `VaultMediaFetcher`, and enforces complete fail-closed storage isolation in Decoy Mode (`DecoySessionState.isDecoy`).
- Vector Store Decoy Isolation: `VectorStore` checks `DecoySessionState.isDecoy`, returning empty results and suppressing mutations during decoy sessions.
- Interactive Mutex Preemption: `QwenLlmClient` implements `preemptBackground()` so foreground Brain chat queries immediately abort or preempt background analysis workers holding `nativeMutex`.
- Arabic Document Transparency: `ReviewScreen` renders honest bilingual guidance when Latin-optimized OCR detects sparse or missing text, prompting for Arabic titles and keywords.
- Decoy mode Detail: `ItemDetailViewModel` observes `DecoySessionState.isDecoy` and immediately cancels load jobs and wipes `_uiState` upon decoy entry.
- Agent write confirmation: `PendingWriteCard` renders structured inline confirm/cancel cards for agent write proposals in Brain chat.
- Proactive intelligence: Today selects up to three insights with `DailyIntelligence` using proactive multi-factor scoring: `(0.30 * relevance) + (0.30 * confidence) + (0.25 * urgency) + (0.15 * novelty)`, requiring score > 70 and confidence > 0.6. The urgency dimension is blended with a code-computed prior over an injectable clock (`clock: () -> Long` constructor param, provided in `di/RagModule`): days-to-expiry decay (1.0 ≤7d, 0.7 ≤30d, 0.4 ≤90d, 0.2 later, 0.0 expired), missing-document signals (trips without hotels, vehicles without insurance via the tool registry, plus utility-bill recent-month gaps computed locally), and near-due SCHEDULED/SNOOZED reminders. Final urgency = `max(llmUrgency, codeUrgency * 0.7)` so a low LLM self-reported urgency cannot hide genuine time pressure.
- Release & Quality Gates: `:app:assembleRelease` passed with full native CMake compilation (`arm64-v8a` + `x86_64`) and R8 minification. `:app:lintDebug` reports 0 errors and 0 warnings. `check_localization.py` reports 100% bilingual parity.
- Physical device testing: Verified on Samsung Galaxy Tab S7+ (SM-T975, Android 13). 13 database migration tests passed (including `Schema19MigrationTest`), backup/restore/rollback passed (`DriveBackupManagerInstrumentedTest`), and live tablet navigation, adaptive layout, and Decoy wipe verified.

## Module map

17 September 2026 follow-up: Today insight refresh is input-sensitive (one-second
coalescing, cancellation of stale analysis, model-ready retry, decoy clearing),
with bounded upcoming context and code-computed confidence/ranking gates.
`DYNAMIC_BRAIN_ROADMAP.md` records remaining planner placeholders, external-only
answer limitations, and the Gmail/tasks authorization and sync work still needed.

- `shared/` — new Kotlin Multiplatform module (T1). Currently hosts `LensId`,
  `KeywordDictionary`, and a bilingual Compose Multiplatform sample. iOS targets
  (`iosArm64`, `iosSimulatorArm64`) are declared; Apple-target builds are pending
  verification on macOS.
- `app/` — application shell: `VaultBrainApplication` (Hilt + WorkManager config,
  keyword warmup), `MainActivity`, navigation (`navigation/VaultBrainNavGraph.kt`).
- `feature/vault` — home/Today screen (greeting, "Needs attention" prioritizer in
  `home/AttentionItem.kt`, ask composer), vault browser/search, item detail, review
  inbox, auth gate, onboarding, lens detail, personal collections
  (`CollectionsScreen.kt` list + detail, `CollectionsViewModel.kt`).
- `feature/capture` — capture pipeline (camera/gallery/share/voice/text), experience
  picker, review screen, `ShareActivity`, `ShareIngestionViewModel`,
  `DeferredItemAnalyzer`, `worker/DeferredAnalysisWorker`, `ExperienceDefinitions`,
  `PostSaveDuplicateDetector`, `MediaVaultStorage`.
- `feature/brain` — Brain chat (RAG Q&A over the vault, interactive `PendingWriteCard` for write confirmation).
- `feature/briefing` — daily briefing.
- `feature/voice` — on-device voice capture dialog.
- `feature/lens-money|lens-health|lens-travel|lens-bureaucracy|lens-media` — per-lens
  screens and lens-specific utilities (e.g. CSV export).
- `feature/settings` — settings.
- `core/common` — shared models (`VaultItem`, `ProcessingState`,
  `EnrichmentState`, `Classification`, `RelationshipType`), `ui/MetadataFields.kt`, user frequency tracking.
  `LensId` now lives in the KMP `shared` module and is re-exported via `api(project(":shared"))`.
- `core/database` — Room DB (encrypted, Schema 20), DAOs, `VaultRepository` (claim primitives for
  enrichment/indexing queues), external-context and Vault Reminder persistence.
- `core/integrations` — connector contracts, `PersonalContextEngine`, Android Calendar
  connector, Calendar sync and connection management.
- `core/vectorstore` — ObjectBox vector store (`VectorStore`, `VaultEmbedding`, app-private sandbox).
- `core/ai/vision` — `VisionAnalyzer` (colors/confidence-bearing labels/barcodes/classifier, parallelized,
  shared injected ML Kit clients), `DocumentClassifier` (TFLite, lazy interpreter behind a
  `TfliteRunner` seam provided in `di/VisionModule`), `di/VisionModule`. Pure label
  filtering/merge/color-bucketing logic and classifier argmax/buffer normalization live in
  companion-level helpers covered by JVM unit tests (`DocumentClassifierTest`,
  `VisionAnalyzerLogicTest`); `analyze()` and bitmap paths remain instrumented-only.
- `core/ai/heuristics` — `HeuristicExtractor`,
  `experience/ExperienceKeywordLibrary` (precompiled keyword regexes),
  `experience/parsers/*` per-experience parsers.
  `KeywordDictionary` now lives in the KMP `shared` module and is consumed via `implementation(project(":shared"))`.
- `core/ai/embeddings` — text/vision embedding models (All-MiniLM, MobileCLIP).
- `core/ai/rag` — `RagEngine` retrieval/rerank/answer pipeline (reads the vector store,
  validates numbered claim citations, and can add matching bounded external context).
- `core/ai/llm` — tiered on-device providers. `TieredLlmClientSelector` prefers the
  available Qwen3-VL-2B provider (`QwenLlmClient`, llama.cpp JNI), with Gemini Nano
  (`NanoPromptClient`, foreground-gated) as the text fallback and Gemma 3 1B int4 as
  download fallback. `OnDeviceModelManager` verifies the language GGUF plus vision projector
  in `filesDir/models/` using URL/SHA-256 configuration in `gradle.properties` (about 1.55 GB combined).
  Legacy Gemma type aliases/qualifiers remain for compatibility. Native compilation
  requires NDK 27.2.12479018 + CMake 3.22.1 in the SDK; `-Pnemory.skipNativeForTests=true`
  skips it for test builds and stamps the app versionName with `-nonative` (plus
  `BuildConfig.NATIVE_RUNTIME_OMITTED`) so such APKs are never mistaken for production.
  Text inference validated on-device (SM-T975) via `LlamaBridgeOnDeviceTest`.
  Multimodal vision on CPU was benchmarked on SM-T975 (taking ~50 min for a 768x1024 receipt, pinning CPU >400%);
  full CPU vision is therefore hardware-gated and disabled for production use, with Qwen3-VL-2B serving
  as an on-device text LLM over deterministic OCR evidence.
  `ArabicScriptCoverage` + `ArabicDocumentTranscriber` add an experimental VL
  transcription path for Arabic documents (ML Kit OCR is Latin-only), gated by
  `nemory.arabicVlExperiment` (default false); extracted values must appear verbatim
  in the VL transcription.
  Hybrid interfaces exist, but the build binds `DisabledCloudAiRuntime`.
- `core/notifications` — `UnifiedAlertManager`, `PersistentCaptureNotificationManager`,
  `GemmaDownloadPromptManager` (one-tap model download prompt for supported
  non-AICore devices; max two prompts, 7 days apart), Vault Reminder scheduling and
  notification actions.
- `core/security` — app lock/decoy mode.
- `sync/drive`, `sync/gmail` — optional sync integrations.

## Navigation

Single-activity Compose nav (`VaultBrainNavGraph`). Three bottom tabs: Today (`home`),
Vault (`search`), Brain (`brain`, optional `?query=` prefill from the Today composer —
prefill only, never auto-sends). Capture is a modal route (`capture?imageUris&text&lens&source`,
slides up). Other routes: `auth_gate`, `onboarding`, `settings`, `review`,
`detail/{itemId}`, `collections`, `collection/{collectionId}`, `lens/{lensId}`.

## Personal collections (P0, manual-first)

User-facing organization comes from the user's own `PersonalCollection`s, not from the
internal lens taxonomy. Collections were introduced in Room schema v11:
`personal_collections` +
`personal_collection_memberships` (many-to-many, composite PK, duplicate memberships
ignored, membership FKs cascade on collection/item delete — items are never deleted by
collection removal). Removal is soft archival (`archivedAt`); archived collections keep
memberships and can be restored. `PersonalCollectionSource` is `USER` (P0) or
`SYSTEM_SUGGESTED` (reserved, unused). All access goes through `VaultRepository`
collection APIs, which hide everything in decoy mode. Home and the vault browser put
collections first; fixed lens IDs remain only as internal metadata and in the
specialized `lens/{lensId}` feature screens. P0 has no semantic/AI collection
matching — do not insert suggestions into the membership table.

## Capture pipeline

1. Input arrives via camera, gallery, paste, voice, or external share.
2. `MediaVaultStorage.import()` copies shared media into app-private storage.
3. `CaptureViewModel.analyze()`:
   - PDF pages are rendered **once** (`renderPdfPages`); page 1 doubles as the vision
     preview. Plain images are decoded once at a 2400 px cap and reused for OCR + vision.
   - OCR runs raw + contrast-enhanced passes **concurrently** through the shared
     `TextRecognizer` (`recognizeBestDocument`), with a conditional 90°/180°/270° retry.
     The installed recognizer is Latin-script only; Arabic UI support does not imply Arabic OCR.
   - `VisionAnalyzer.analyze()` runs colors/labels/barcodes/classifier concurrently.
   - `HeuristicExtractor` merges OCR text, barcodes, vision objects, and the neural
     classification into metadata/lens tags/confidence.
4. Experience suggestion via `ExperienceKeywordLibrary.quickScore` (runs on
   `Dispatchers.Default`; auto-select above the threshold).
5. User reviews/edits; `save()` persists with `extractionState = COMPLETE`,
   `indexingState = PENDING`, schedules alerts, and enqueues `DeferredAnalysisWorker`.
   Model confidence never bypasses review. Enrichment preserves omitted metadata and
   app-owned Share provenance; explicit skip is persisted as `SKIPPED_PRIVACY`.
   Invalid model classification/title responses are rejected. Classification corrections
   replace the obsolete automatic lens and do not inherit the old classification confidence.

### Deferred share flow

The current exported `ShareActivity` validates incoming text/URL/image/PDF shares,
shows `ShareReviewScreen`, converts accepted content to `CaptureInput`, and continues
through `CaptureFlowScreen`. The input preserves `targetCollectionId`,
`provenanceMetadata`, and `skipLlmEnrichment`; provenance can retain `ANDROID_SHARE`,
source package, URL, and received time. The separate `ShareIngestionViewModel` placeholder pipeline
and `DeferredAnalysisWorker` remain available for deferred ingestion: media may be
saved with pending extraction, claimed from the indexing queue, analyzed, embedded,
and completed with WorkManager retry/backoff. Do not assume the visible ShareActivity
flow and the placeholder API are the same path when changing or testing them.

### Embeddings

`VaultEmbeddingGenerator` (feature/capture) is the indexing entry point for the vector
store. Per item it stores up to three 100-dim, L2-normalized `VaultEmbedding` rows via
`VectorStore.putEmbeddingsForItem`: `ocr_text` (raw OCR, capped) and `summary` (title +
summary + metadata in `RagEngine.buildPrompt`'s labeled record layout). The old
grayscale pseudo-image embedding is disabled and must not be re-enabled; a real,
licensed semantic image encoder needs a compatible separate index. `PostSaveDuplicateDetector`
only queries `ocr_text`/`summary`. Embedding failures are logged and never fail the
item — it stays searchable via FTS. `EmbeddingBackfillWorker` (unique work
`embedding_backfill`, `KEEP`, enqueued from `VaultBrainApplication`) one-time embeds
older items that have no rows in the vector store.

## Backup and Gmail integration

`sync/drive/DriveBackupManager` implements portable SAF export/import, not direct Drive
API sync. Archives use a user passphrase, PBKDF2-HMAC-SHA256, AES-256-GCM, a
checkpointed, transactionally stable SQLCipher snapshot plus its re-wrapped key, and private media.
Restore validates and stages entries, closes Room before creating its rollback copy,
and requires an app restart.
Snapshots use `sqlcipher_export` in a transaction on a separate connection and retain
Room schema identity/version. Import requires the current schema. Key ciphertext is
staged under its final basename, verified, and atomically installed; unreadable keys
are never silently regenerated. Legacy `.tmp`-authenticated keys are recovered when possible.

`sync/gmail/GmailConnector` is a read-only connector foundation with sanitization,
bounded local retention/polling, decoy isolation, and Today/Brain context. Its default
`GmailDataSource` is intentionally unconfigured. Do not add fake email records or claim
Gmail works until verified production OAuth credentials and the `gmail.readonly` consent
flow exist. The current settings control is disabled and explicitly reports Gmail as
not configured.

## Experience taxonomy (two tiers)

- `PersonalExperience` with `parentId`: primaries (`parentId == null`) are the
  high-frequency experiences; sub-experiences point to their closest primary.
- `ExperienceDefinitions.resolvePrimary(id)` walks to the primary;
  `effectiveLensId(id)` returns the experience's lens or its primary's lens.
- Suggestions use `ExperienceKeywordLibrary.quickScore` over `COMPILED_GROUPS` — ~944
  precompiled, boundary-aware regexes compiled once per process (warmed up in
  `VaultBrainApplication.onCreate`). **Do not compile regexes per call**; add keywords to
  the existing groups instead.

## Lens model

`primaryLensId` is the single primary lens used for counts/ordering; `lensTags` is the
full tag set used for browsing (`observeByLens` matches `primary_lens_id` first, falling
back to `lens_tags` when null). `primaryLensId == null` means General (unassigned).

## Metadata display

`core/common/ui/MetadataFields.kt` defines per-`Classification` priority key lists and
human labels; vault cards and the detail screen order metadata through it.

## AI gating

Everything deterministic (OCR, heuristics, keyword scoring, vision) runs on-device,
always, and produces the base item. On-device LLMs are an optional enrichment layer on
top (categorization/details/highlights for capture, Brain answers, briefing): Tier 1
Gemini Nano (AICore / ML Kit Prompt API), Tier 2 downloadable Gemma 3 1B for
non-AICore devices with ≥4 GB RAM. Any LLM failure leaves the heuristic item
untouched. The current build wires `DisabledCloudAiRuntime` and cannot send vault
content to cloud AI. Any future implementation would require explicit per-request
consent plus updated privacy and store disclosures.

## External context, Calendar, and reminders

`VaultItem` is durable user-saved content; `ExternalRecord` is bounded connector-owned
context and is never silently promoted to the vault. Room schema v14 contains
`external_connections`, `external_records`, and `vault_reminders`; v15 adds non-unique
indices on `vault_items` (`primary_lens_id`, `is_archived`, `is_stealth`, `created_at`);
v16 adds indices for the worker queue and reminder columns (`enrichment_state`,
`indexing_state`, `needs_review`, `expiry_date`).
All access is decoy-aware.

Calendar is the most complete live connector. It requests `READ_CALENDAR`, lets the
user select calendars, caches events from 30 days past through 90 days future, and
periodically syncs approximately every 6 hours. Disconnect or permission revocation
clears locally cached Calendar records. Nemory does not request `WRITE_CALENDAR`;
creation launches the system Calendar editor with prefilled data for user confirmation.

Health Connect is planned but disabled for v1.1.1. The production path contains no
Health Connect manifest permissions, settings permission flow, connector registration,
or worker scheduling. Treat enabling it as a future product/release decision, not a
debugging change.

Vault Reminders are local records with scheduled/snoozed/completed/dismissed state and
optional links to a vault item, external record, or personal collection. WorkManager
schedules one-time notifications with open, snooze-15-minutes, complete, and dismiss
actions. Today combines reminders and selected Calendar events deterministically; this
ordering does not require an LLM.

## Conventions

- All user-facing strings bilingual: `values/strings.xml` (EN) + `values-ar/strings.xml` (AR).
- Minimal, surgical edits; match the surrounding style.
- Build: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew :core:database:testDebugUnitTest :feature:capture:testDebugUnitTest :core:ai:vision:testDebugUnitTest :core:ai:heuristics:testDebugUnitTest`
