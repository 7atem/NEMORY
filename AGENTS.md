# VaultBrain / Nemory — Architecture Guide

On-device personal vault for documents and life admin. Kotlin, Jetpack Compose, Hilt,
Room (SQLCipher-encrypted), WorkManager, ML Kit, ObjectBox (vector store), TFLite.
App id is `com.nemory.app`; the user-facing name is Nemory.

## Module map

- `app/` — application shell: `VaultBrainApplication` (Hilt + WorkManager config,
  keyword warmup), `MainActivity`, navigation (`navigation/VaultBrainNavGraph.kt`).
- `feature/vault` — home screen, vault browser/search, item detail, review inbox,
  auth gate, onboarding, lens detail, personal collections
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
  enrichment/indexing queues).
- `core/vectorstore` — ObjectBox vector store (`VectorStore`, `VaultEmbedding`).
- `core/ai/vision` — `VisionAnalyzer` (colors/labels/barcodes/classifier, parallelized,
  shared injected ML Kit clients), `DocumentClassifier` (TFLite, lazy interpreter),
  `di/VisionModule`.
- `core/ai/heuristics` — `HeuristicExtractor`, `KeywordDictionary`,
  `experience/ExperienceKeywordLibrary` (precompiled keyword regexes),
  `experience/parsers/*` per-experience parsers.
- `core/ai/embeddings` — text/vision embedding models (All-MiniLM, MobileCLIP).
- `core/ai/rag` — `RagEngine` retrieval/rerank/answer pipeline (reads the vector store).
- `core/ai/llm` — tiered on-device LLM. `TieredLlmClientSelector` (the unqualified
  `LlmClient` binding) delegates per call: Tier 1 Gemini Nano via ML Kit Prompt API
  (`NanoPromptClient`, `AiCapabilityManager`, foreground-gated), Tier 2 Gemma 3 1B
  int4 via MediaPipe `LlmInference` (`GemmaLlmClient`, `gemma/GemmaModelManager`,
  `gemma/GemmaModelDownloadWorker` — on-demand ~530 MB download to
  `filesDir/models/`, URL/SHA-256 configured via `gradle.properties`, no foreground
  gate so it also serves background workers). Hybrid cloud coordinator (cloud
  disabled by default).
- `core/notifications` — `UnifiedAlertManager`, `PersistentCaptureNotificationManager`,
  `GemmaDownloadPromptManager` (one-tap Tier 2 model download prompt for supported
  non-AICore devices; max two prompts, 7 days apart).
- `core/security`, `core/billing` — app lock/decoy mode, Play Billing.
- `sync/drive`, `sync/gmail` — optional sync integrations.

## Navigation

Single-activity Compose nav (`VaultBrainNavGraph`). Three bottom tabs: Today (`home`),
Vault (`search`), Brain (`brain`). Capture is a modal route (`capture?imageUris&text&lens&source`,
slides up). Other routes: `auth_gate`, `onboarding`, `settings`, `review`,
`detail/{itemId}`, `collections`, `collection/{collectionId}`, `lens/{lensId}`.

## Personal collections (P0, manual-first)

User-facing organization comes from the user's own `PersonalCollection`s, not from the
internal lens taxonomy. Room schema v11: `personal_collections` +
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
     preview. Plain images are decoded once at a 1200 px cap and reused for OCR + vision.
   - OCR runs raw + contrast-enhanced passes **concurrently** through the shared
     `TextRecognizer` (`recognizeBestDocument`), with a conditional 90°/270° retry.
   - `VisionAnalyzer.analyze()` runs colors/labels/barcodes/classifier concurrently.
   - `HeuristicExtractor` merges OCR text, barcodes, vision objects, and the neural
     classification into metadata/lens tags/confidence.
4. Experience suggestion via `ExperienceKeywordLibrary.quickScore` (runs on
   `Dispatchers.Default`; auto-select above the threshold).
5. User reviews/edits; `save()` persists with `extractionState = COMPLETE`,
   `indexingState = PENDING`, schedules alerts, and enqueues `DeferredAnalysisWorker`.

### Deferred share flow

`ShareActivity` → `ShareIngestionViewModel.ingest()` saves **PENDING placeholders**
(`extractionState = PENDING` for media) and enqueues unique work `deferred_analysis`
(`APPEND_OR_REPLACE`), then returns immediately with a toast. `DeferredAnalysisWorker`
(Hilt `CoroutineWorker`) loops `VaultRepository.claimNextIndexing`, runs
`DeferredItemAnalyzer.analyze` for items whose extraction is pending, saves, generates
embeddings, marks `completeIndexing`, and schedules alerts. Failures become
`FAILED_RETRYABLE`; the worker returns `Result.retry()` up to 3 attempts with WorkManager
backoff. A low-priority foreground-style notification is shown while processing.

### Embeddings

`VaultEmbeddingGenerator` (feature/capture) is the indexing entry point for the vector
store. Per item it stores up to three 100-dim, L2-normalized `VaultEmbedding` rows via
`VectorStore.putEmbeddingsForItem`: `ocr_text` (raw OCR, capped), `summary` (title +
summary + metadata in `RagEngine.buildPrompt`'s labeled record layout), and `image`
(visual embedding of the stored image / first PDF page). `RagEngine` reads all content
types (image embeddings get a 0.85 cross-modal penalty); `PostSaveDuplicateDetector`
only queries `ocr_text`/`summary`. Embedding failures are logged and never fail the
item — it stays searchable via FTS. `EmbeddingBackfillWorker` (unique work
`embedding_backfill`, `KEEP`, enqueued from `VaultBrainApplication`) one-time embeds
older items that have no rows in the vector store.

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
untouched. Cloud AI is disabled by default and requires explicit per-request consent.

## Conventions

- All user-facing strings bilingual: `values/strings.xml` (EN) + `values-ar/strings.xml` (AR).
- Minimal, surgical edits; match the surrounding style.
- Build: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew :core:database:testDebugUnitTest :feature:capture:testDebugUnitTest :core:ai:vision:testDebugUnitTest :core:ai:heuristics:testDebugUnitTest`
