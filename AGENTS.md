# VaultBrain / Nemory — Architecture Guide

On-device personal vault for documents and life admin. Kotlin, Jetpack Compose, Hilt,
Room (SQLCipher-encrypted), WorkManager, ML Kit, ObjectBox (vector store), TFLite.
App id is `com.nemory.app`; the user-facing name is Nemory.

The detailed product, architecture, privacy, testing, and release reference is
`NEMORY_MASTER_DOCUMENTATION.md`. Keep this implementation guide and that master
document aligned with source changes.

## Module map

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
- `feature/brain` — Brain chat (RAG Q&A over the vault).
- `feature/briefing` — daily briefing.
- `feature/voice` — on-device voice capture dialog.
- `feature/lens-money|lens-health|lens-travel|lens-bureaucracy|lens-media` — per-lens
  screens and lens-specific utilities (e.g. CSV export).
- `feature/settings` — settings.
- `core/common` — shared models (`VaultItem`, `LensId`, `ProcessingState`,
  `EnrichmentState`, `Classification`), `ui/MetadataFields.kt`, user frequency tracking.
- `core/database` — Room DB (encrypted), DAOs, `VaultRepository` (claim primitives for
  enrichment/indexing queues), external-context and Vault Reminder persistence.
- `core/integrations` — connector contracts, `PersonalContextEngine`, Android Calendar
  connector, Calendar sync and connection management.
- `core/vectorstore` — ObjectBox vector store (`VectorStore`, `VaultEmbedding`).
- `core/ai/vision` — `VisionAnalyzer` (colors/confidence-bearing labels/barcodes/classifier, parallelized,
  shared injected ML Kit clients), `DocumentClassifier` (TFLite, lazy interpreter),
  `di/VisionModule`.
- `core/ai/heuristics` — `HeuristicExtractor`, `KeywordDictionary`,
  `experience/ExperienceKeywordLibrary` (precompiled keyword regexes),
  `experience/parsers/*` per-experience parsers.
- `core/ai/embeddings` — text/vision embedding models (All-MiniLM, MobileCLIP).
- `core/ai/rag` — `RagEngine` retrieval/rerank/answer pipeline (reads the vector store,
  validates numbered claim citations, and can add matching bounded external context).
- `core/ai/llm` — tiered on-device LLM. `TieredLlmClientSelector` (the unqualified
  `LlmClient` binding) delegates per call: Tier 1 Gemini Nano via ML Kit Prompt API
  (`NanoPromptClient`, `AiCapabilityManager`, foreground-gated), Tier 2 Gemma 3 1B
  int4 via MediaPipe `LlmInference` (`GemmaLlmClient`, `gemma/GemmaModelManager`,
  `gemma/GemmaModelDownloadWorker` — on-demand ~530 MB download to
  `filesDir/models/`, URL/SHA-256 configured via `gradle.properties`, no foreground
  gate so it also serves background workers). Hybrid coordinator interfaces exist,
  but the current build binds `DisabledCloudAiRuntime`.
- `core/notifications` — `UnifiedAlertManager`, `PersistentCaptureNotificationManager`,
  `GemmaDownloadPromptManager` (one-tap Tier 2 model download prompt for supported
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
