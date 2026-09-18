# Nemory — Master Product, Engineering, Privacy, Testing, and Release Documentation

> **Document status:** Authoritative repository documentation

> **2026-09-17 dynamic Brain follow-up:** Today now invalidates insight analysis
> when input or downloaded-model readiness changes, coalesces updates for one
> second, cancels stale work, and clears on decoy entry. Its bounded context includes
> upcoming events/reminders, document summaries/metadata/expiry, and external record
> descriptions/times. Ranking and confidence thresholds are enforced in code.
> Gmail/tasks linking is not implemented by this change. See
> [Dynamic Brain roadmap](DYNAMIC_BRAIN_ROADMAP.md) for audited planner and sync gaps.
> **Last revised:** 2026-09-06
> **Applies to:** Current candidate worktree: Nemory `1.1.2` (`versionCode 15`); release candidate `v1.1.2-rc2`.
> **Android application ID:** `com.nemory.app`
> **Repository package namespace:** primarily `com.vaultbrain.*`
> **Current Room schema:** 20

This document is the single detailed reference for the current Nemory product. It
supersedes the older versioned specifications, implementation plans, assessments,
store drafts, release notes, Play Console guides, and testing plans in the repository.
`AGENTS.md` remains the concise implementation guide for coding agents; when it and
this document differ, verify the behavior in source and update both documents.

The document distinguishes three delivery states:

- **Implemented:** present in the current source tree and intended to work.
- **Scaffolded or limited:** foundations exist, but the feature is not a complete
  production integration or is intentionally restricted.
- **Planned:** product direction only; it must not be advertised as shipped.

## Contents

1. [Product summary](#1-product-summary)
2. [User experience](#2-user-experience)
3. [Architecture](#3-architecture)
4. [Data and persistence](#4-data-and-persistence)
5. [On-device intelligence](#5-on-device-intelligence)
6. [Security and privacy architecture](#6-security-and-privacy-architecture)
7. [Privacy policy publication draft](#7-privacy-policy-publication-draft)
8. [Build and development guide](#8-build-and-development-guide)
9. [Testing strategy and acceptance plan](#9-testing-strategy-and-acceptance-plan)
10. [Google Play and release operations](#10-google-play-and-release-operations)
11. [Roadmap](#11-roadmap)
12. [Operations and troubleshooting](#12-operations-and-troubleshooting)
13. [Documentation maintenance](#13-documentation-maintenance)
14. [Glossary](#14-glossary)

---

## 1. Product summary

> **Nemory Intelligence V1 architecture is complete. V1.1 is the Quality, Performance, Validation and Release-Hardening milestone. Full CPU Qwen-VL vision is experimental and disabled for normal production use.**

Nemory is an on-device personal vault for documents and life administration. It lets
people capture images, PDFs, shared content, text, and voice; extract useful details;
organize records into personal collections; search and ask questions over their own
vault; and surface time-sensitive items through Today, Calendar context, and Vault
Reminders.

The product's defining principles are:

1. **Local-first.** Core capture, OCR, classification, search, retrieval, reminders,
   and supported language-model inference run on the device.
2. **Useful without AI.** Deterministic OCR, heuristics, metadata extraction, keyword
   scoring, and vision produce the base item. An unavailable LLM must never prevent a
   document from being saved or searched.
3. **User-owned organization.** Personal collections are the primary user-facing
   organization model. Internal lenses are metadata and specialist views, not a
   replacement for the user's own filing system.
4. **Explicit boundaries.** Imported vault items, temporary external context, and
   future cloud processing are separate concepts with separate controls.
5. **Private by default.** The database is encrypted, captured media is held in
   app-private storage, cloud AI is disabled in the current build, and decoy mode
   hides real content.
6. **Human confirmation for external writes.** Calendar creation is handed to the
   system Calendar editor. Nemory does not silently create calendar events.

### 1.1 Current release snapshot

**Current published release:** 1.1.1 / versionCode 10
**Current development candidate:** 1.1.2 / versionCode 15 (release candidate `v1.1.2-rc2`)

| Item | Current value |
|---|---|
| User-facing name | Nemory |
| Version | 1.1.2 (candidate); 1.1.1 published |
| Version code | 15 (candidate); 10 published |
| Application ID | `com.nemory.app` |
| Minimum Android | API 29 (Android 10) |
| Target / compile SDK | API 36 |
| Primary UI | Kotlin + Jetpack Compose |
| Dependency injection | Hilt |
| Structured storage | Room 2.8.4 + SQLCipher 4.17.0 (Schema 19) |
| Vector storage | ObjectBox 5.4.2 (app-private sandbox) |
| Background work | WorkManager 2.10.0 |
| Local LLM runtime | Tiered: Qwen3-VL-2B (llama.cpp JNI); Gemini Nano where available; Gemma 3 1B fallback |
| Languages | English and Arabic (100% string parity) |
| Ads | No ads in the current build |
| Analytics / remote crash SDK | None in the current build |
| Cloud AI | Disabled in the current build (`DisabledCloudAiRuntime`) |

### 1.2 Feature status

| Area | Status | Notes |
|---|---|---|
| Camera, gallery, PDF, text, paste, voice capture | Implemented | User reviews content before saving. |
| Android Share target | Implemented | Handles text, URLs, images, PDFs, and supported multi-file shares. |
| OCR and deterministic extraction | Implemented | On-device ML Kit and heuristics. Includes heuristic extraction that processes MM/YY dates across all supported document types. |
| Personal collections | Implemented | Manual-first, many-to-many membership, archive/restore. |
| Vault search | Implemented | Room FTS plus vector retrieval where embeddings exist. |
| Brain RAG chat | Implemented | Hybrid retrieval (FTS + Vector + Entities + Collections), LocalAgent tool loop with reasoning budgets (FAST/NORMAL/DEEP), ClaimVerifier single-pass post-stream verification. |
| Quality benchmark | Implemented | 100-case deterministic benchmark (`IntelligenceBenchmarkTest.kt`) passing 100/100 across retrieval, arithmetic, grounding, anti-hallucination, and multi-doc reasoning. |
| Daily briefing / Today insights | Implemented | Deterministic prioritization; not dependent on Gemma/Qwen. |
| Qwen3-VL-2B on-device | Implemented | llama.cpp JNI bridge (`arm64-v8a` + `x86_64`), verified text generation on device. |
| Gemini Nano | Implemented where device capability permits | Foreground-gated through the ML Kit Prompt API. |
| Gemma 3 1B int4 | Implemented | Optional roughly 530 MB verified download; supports background inference. |
| Calendar context | Implemented | User-selected Android calendars, bounded local cache, read-only provider access. |
| Calendar event proposal | Implemented | Opens the system Calendar editor with prefilled fields for user confirmation. |
| Vault Reminders | Implemented | Local scheduling, notification, snooze, complete, dismiss, and linked context. |
| Portable backup and restore | Implemented, device-verified | User-passphrase AES-GCM archive includes a transactionally stable SQLCipher snapshot, its re-wrapped key, and private media; verified on SM-T975. |
| App Widget | Implemented | Public launcher shortcuts only; hardened against lockscreen/unauthenticated data leakage. |
| Google Drive upload | Not configured | Portable files can be saved through Android's document picker, including to a user-selected Drive provider; direct Drive API upload is not enabled. |
| Gmail ingestion | Connector foundation, unavailable | The production data source and OAuth client are not configured. The settings control is disabled and no Gmail account or message is accessed. |
| Health Connect | Planned and disabled | It is not part of v1.1.1: there are no health permissions, connection UI, connector registration, or scheduled Health Connect work in the production path. |
| Tasks, Contacts, notification context | Planned | Model/source enums and integration architecture exist, but live connectors are not shipped. |
| Cloud AI | Scaffolded but disabled | Consent architecture exists; runtime currently rejects cloud execution. |
| Ads and Billing | Scaffolded | Ad manager is a no-op. Play Billing plumbing remains intact for optional premium upgrades (core/billing), and requires the INTERNET permission to verify purchases. |

---

## 2. User experience

### 2.1 App structure and navigation

Nemory is a single-activity Compose application. The three persistent bottom tabs are:

- **Today** (`home`): greeting, "Needs attention", ask composer, briefing, upcoming
  Calendar events, reminders, and shortcuts.
- **Vault** (`search`): personal collections first, followed by vault browsing and
  search.
- **Brain** (`brain`): retrieval-augmented questions and actions over vault content.

Capture opens as a modal route. Additional routes cover authentication, onboarding,
settings, connections, review, collections, collection detail, item detail, and
specialized internal lens screens.

Canonical routes include:

```text
auth_gate
onboarding
home
search
brain
settings
connections
review
collections
collection/{collectionId}
detail/{itemId}
lens/{lensId}
capture?imageUris&text&lens&source
```

Deep links are supported for item, lens, Brain, and collection destinations under the
`nemory://` scheme. A Quick Settings tile, app widget, FileProvider, and Android Share
entry point are also registered.

### 2.2 Authentication and onboarding

The authentication gate supports a local PIN and optional biometrics. A separate
decoy PIN can open a decoy session whose repositories return only decoy-visible
content. Auto-lock defaults to five minutes and is configurable through settings.

PIN verification uses PBKDF2-HMAC-SHA256 with a random salt, 210,000 iterations, and a
256-bit derived hash. Legacy hashes are migrated. After five failed attempts, the app
uses escalating cooldowns beginning at 30 seconds and capped at one hour.

Onboarding should explain:

1. that the vault is stored locally and encrypted;
2. how capture and Android Share work;
3. that optional permissions are requested in context;
4. that Calendar context is opt-in and user-selected;
5. that on-device models (Qwen3-VL-2B, Gemini Nano, or downloadable Gemma) can add optional local enrichment;
6. that deterministic capture and search work even without an LLM.

### 2.3 Capture and review

The capture speed dial provides camera, gallery, paste/text, and voice entry points.
External Android shares arrive through `ShareActivity`.

The normal interactive pipeline is:

1. Receive camera, gallery, PDF, pasted text, voice text, or shared content.
2. Copy external media into app-private storage through `MediaVaultStorage`.
3. Decode a plain image once, capped at 2400 px, and reuse it for OCR and vision.
4. Render PDF pages once; the first page also becomes the vision preview.
5. Run raw and contrast-enhanced OCR passes concurrently. Retry at 90°, 180°, or 270° only
   when orientation evidence justifies it.
6. Run colors, labels, barcodes, and the document classifier concurrently in
   `VisionAnalyzer`.
7. Merge OCR, barcode, visual, and classifier evidence in `HeuristicExtractor`.
8. Score experiences using precompiled keyword groups on `Dispatchers.Default`.
9. Present title, summary, type, lens, experience, and metadata for user review.
10. Save the confirmed item, schedule applicable alerts, and enqueue deferred
    enrichment/indexing.

PDF and image decoding must not be repeated unnecessarily; this is important for
memory use and thermal behavior on real devices.

The installed OCR recognizer supports Latin script, not Arabic script. EN/AR refers
to the interface and text handling; Arabic image transcription is not a shipped
capability. The review hint states this limitation. The higher-resolution OCR
candidate retains small-print evidence; its accuracy/latency tradeoff needs device evaluation.
Long model inputs retain the OCR head and tail within 3,500 characters and explicitly
mark the omitted middle. Full OCR remains on the item. The model is text-only when
Gemma is selected; it cannot recover unseen image text.

Enrichment preserves omitted metadata and authoritative Share provenance. Invalid
classification/title responses are rejected instead of replacing a valid class with
OTHER. Classification corrections remove the old automatic lens and use the new
confidence. Model confidence never skips visible review/save. Explicit skip is stored
as SKIPPED_PRIVACY and remains excluded from bulk enrichment scheduling.

### 2.4 Android Share

The exported Share entry point accepts:

- `ACTION_SEND`: images, PDFs, `text/plain`, and `text/html`;
- `ACTION_SEND_MULTIPLE`: supported image and PDF sets.

The current user-visible flow validates the incoming share, shows a review surface,
turns it into a capture input, and continues through the normal Capture review/save
experience. The accepted `CaptureInput` preserves the selected `targetCollectionId`,
share `provenanceMetadata`, and `skipLlmEnrichment` choice through the normal save
path. Share provenance can include `ANDROID_SHARE`, source package, URL, and received
time. Provenance is local metadata.

A deferred placeholder pipeline also exists for fast ingestion: media placeholders
can be saved with pending extraction and processed by `DeferredAnalysisWorker`. Code
that changes Share behavior must test both the user-facing ShareActivity path and the
deferred worker primitives rather than assuming they are identical.

### 2.5 Saving and background analysis

Interactive saves set deterministic extraction complete and indexing pending. Work
then generates embeddings and optional LLM enrichment. Failure in an embedding or LLM
step must not remove or invalidate the base vault item; FTS and deterministic metadata
remain usable.

The deferred worker claims records from repository queues so process death or repeated
worker execution does not cause uncontrolled duplicate processing. Retryable failures
use WorkManager backoff and are capped by the worker's retry policy.

### 2.6 Personal collections

Personal collections are the principal organization surface. A collection:

- has a user-controlled name and metadata;
- can contain many items, while one item may appear in many collections;
- ignores duplicate memberships;
- can be archived and restored;
- retains membership rows while archived;
- never deletes member items when the collection is removed or archived.

`PersonalCollectionSource.USER` is the P0 behavior. `SYSTEM_SUGGESTED` and a separate
suggestion table reserve future capability, but automated suggestions must not write
directly to the membership table. The user remains the authority on membership.

### 2.7 Vault search and item detail

Vault search combines structured Room queries, FTS, and vector retrieval. Search must
continue to return useful results if embeddings are absent or model initialization
fails. Item detail displays metadata in the priority order and labels defined by
`core/common/ui/MetadataFields.kt` and offers relevant export, edit, collection, or
reminder actions (such as a one-tap action to create a Vault Reminder for expiring items).

### 2.8 Brain

Brain performs retrieval-augmented question answering over vault records. The engine:

1. builds a query embedding when available;
2. retrieves multiple content representations from ObjectBox;
3. supplements or falls back with FTS;
4. reranks records;
5. constructs a labeled prompt with source context;
6. asks the selected on-device LLM when one is ready;
7. returns an answer designed to remain traceable to retrieved vault sources.

Deterministic tools and an action planner support structured operations. Any operation
that writes or changes user data follows a strict lifecycle: `PROPOSE -> VALIDATE -> CONFIRM -> EXECUTE`.
The LLM may propose an action; it cannot execute writes directly or bypass deterministic validation.
Brain chat renders an inline `PendingWriteCard` with structured details and explicit Confirm / Cancel
controls. Execution requires deterministic revalidation and user confirmation, and automatically
invalidates upon decoy mode activation.

### 2.9 Today and daily briefing

Today combines vault-derived attention signals with Calendar events and Vault
Reminders. Important categories include expiry, documents needing review, spending or
money signals, travel timing, media backlog, and time-based external context.

Ordering of Calendar events and reminders is deterministic. It does not require Gemma
and must remain available offline after the permitted local Calendar cache is built.
The top of Today is a "Needs attention" section produced by a pure prioritizer
(reminders, today's Calendar events, expiring documents, review backlog) plus a
greeting header and an "Ask Nemory anything..." composer whose text prefills Brain
(`brain?query=`) without auto-sending.
The Home screen provides "Explainability Chips" that deterministically explain *why* an item is shown in Today (e.g., "Expires soon", "Possible Duplicate"), building user trust without relying on LLM inference. Additionally, proactive "Backup Nudges" appear here to remind users to perform an encrypted export.

### 2.10 Calendar context

Calendar is the first implemented live external-context connector.

Connection flow:

1. The user opens **Settings > Connections > Calendar**.
2. Nemory requests `READ_CALENDAR` in context.
3. The user chooses which device calendars Nemory may surface.
4. Nemory reads a bounded window: 30 days in the past through 90 days in the future.
5. Events are normalized and cached as `ExternalRecord` rows.
6. Periodic background sync runs approximately every six hours under WorkManager
   constraints.

Normalized Calendar records include event/instance identity, calendar ID, title,
description, location, start/end, all-day state, recurrence/time-zone context, and
deletion state where available. The stable external ID includes event identity and
instance start so recurring instances do not collapse into one row.

Privacy and lifecycle rules:

- only calendars explicitly selected by the user are surfaced;
- the cache is local and encrypted with the app database;
- stale or provider-deleted events are removed during reconciliation;
- disconnecting Calendar clears its cached external records;
- revoking permission clears cached Calendar records when detected;
- decoy sessions cannot read or update real Calendar context.

Nemory does **not** request `WRITE_CALENDAR`. “Create event” launches
`CalendarContract.Events.ACTION_INSERT` with prefilled values. The installed Calendar
app shows the final editor and the user decides whether to save or discard it.

### 2.11 Vault Reminders

Vault Reminders are local Nemory records, separate from Calendar events. Each reminder
contains a title, due time, status, timestamps, and optional links to:

- a vault item;
- an external record such as a Calendar event;
- a personal collection.

Statuses are scheduled, snoozed, completed, and dismissed. One-time WorkManager jobs
deliver notifications on the high-priority Vault Reminders channel. Notification
actions include opening linked context, snoozing for 15 minutes, completing, and
dismissing. Today also supports creation, editing, snoozing, and completion.

On Android 13 and later, notification delivery requires `POST_NOTIFICATIONS`. Denial
must not prevent the reminder from remaining visible inside the app.

---

## 3. Architecture

### 3.1 Module map

```text
app/
  Application, MainActivity, navigation, top-level DI and UI shell

core/
  common/          shared models, lens/experience concepts, metadata presentation
  database/        encrypted Room database, DAOs, migrations, repositories
  integrations/    connector contracts, context engine, Calendar implementation
  vectorstore/     ObjectBox vector records and nearest-neighbor access
  security/        keystore, PIN, biometrics, decoy, session policy
  notifications/   alerts, persistent capture, Gemma prompt, reminders
  billing/         Play Billing plumbing
  ai/
    vision/        ML Kit clients, colors, labels, barcodes, classifier
    heuristics/    deterministic extraction and compiled experience keywords
    embeddings/    text embedding runtime; visual embedding contract is disabled until a real model ships
    rag/           retrieval, reranking, prompts, tools, action planning
    llm/           Nano, Gemma, tier selection, cloud-disabled coordinator

feature/
  vault/           Today/home, browser/search, detail, review, auth, collections
  capture/         capture/share/review, storage, analysis and workers
  brain/           RAG chat
  briefing/        daily briefing
  voice/           on-device voice capture dialog
  settings/        settings and connection management
  lens-*/          specialist money, health, travel, bureaucracy, media, real_estate, automotive, legal, pets, records screens

sync/
  drive/           portable encrypted backup/restore; direct Drive API disabled
  gmail/           read-only connector, parser, sanitizer, bounded worker; OAuth provider unconfigured
```

### 3.2 Dependency direction

Feature modules consume core contracts and models. Storage-specific entities stay in
`core/database`; typed external models and connector contracts stay in
`core/integrations`; shared enums stay in `core/common`. The application module wires
the graph and navigation. Avoid adding feature-to-feature dependencies when a narrow
core contract will do.

### 3.3 Important runtime flows

```text
Capture input
  -> private media import
  -> OCR + vision + deterministic extraction
  -> user review
  -> encrypted Room item
  -> deferred embedding / optional LLM enrichment
  -> FTS + ObjectBox retrieval
  -> Vault, Today, Brain
```

```text
Selected Android calendars
  -> Calendar Provider read
  -> normalized ExternalRecord cache
  -> PersonalContextEngine
  -> Today / Brain context
  -> optional user-created Vault Reminder
```

### 3.4 Background work

Important WorkManager responsibilities include:

- deferred capture analysis and indexing;
- optional LLM enrichment;
- embedding backfill for old items;
- derived vault insights;
- Gemma model download;
- Calendar periodic synchronization;
- one-time Vault Reminder scheduling;
- expiry and digest notifications.

Workers must be idempotent, use repository claim primitives where applicable, honor
decoy and privacy state, and leave deterministic user data intact when enrichment
fails.

---

## 4. Data and persistence

### 4.1 Storage technologies

- **Room + SQLCipher:** encrypted structured application data.
- **Android Keystore:** protects database key material and security secrets.
- **App-private files:** imported images, PDFs, and the downloaded Gemma model.
- **ObjectBox:** local vector records for semantic retrieval.
- **WorkManager database:** Android-managed job state.
- **DataStore / preferences:** non-content configuration where appropriate.

Android backup is disabled (`allowBackup=false`), and data extraction rules disable
platform data extraction. This reduces accidental cloud backup exposure but does not
replace export and deletion testing.

### 4.2 Room schema 20

The database currently includes:

| Table | Purpose |
|---|---|
| `vault_items` | Durable user-saved items and extracted metadata. |
| `vault_items_fts` | Full-text search index. |
| `audit_logs` | Local auditable events where used. |
| `notification_queue` | Alert scheduling state. |
| `brain_messages` | Brain conversation persistence. |
| `personal_collections` | User collections and archive state. |
| `personal_collection_memberships` | Many-to-many item membership. |
| `personal_collection_suggestions` | Separate future suggestion records. |
| `external_connections` | Connector/account state and configuration. |
| `external_records` | Bounded local external-context cache. |
| `vault_reminders` | Local reminder records and optional source links. |
| `derived_facts` | Extractive facts backed by verbatim OCR spans (Schema 18). |
| `knowledge_entities` | Distinct entities extracted across vault items (Schema 19). |
| `item_entities` | Many-to-many relationship linking vault items to knowledge entities (Schema 19). |
| `relationships` | Semantic edges linking vault items (RENEWS, REPLACES, etc.) with decoy and visibility gating (Schema 19). |

Schema history and indices:
- Schema 14 added `vault_reminders`. Its optional foreign keys use `SET NULL` when a
  linked vault item, collection, or external record is removed, so the reminder can
  remain as a standalone user record.
- Schema 15 added non-unique indices for `vault_items.primary_lens_id`, `is_archived`,
  `is_stealth`, and `created_at`.
- Schema 16 added indices for `enrichment_state`, `indexing_state`, `needs_review`,
  and `expiry_date`, which back the worker queues and reminder lookups.
- Schema 17/18 added `derived_facts` with foreign key cascades to `vault_items(id)` and
  hash/source version checks to discard stale facts when documents change.
- Schema 19 added `knowledge_entities`, `item_entities`, and `relationships` using the behavior-driving
  `RelationshipType` enum (`RENEWS`, `REPLACES`, `PAYMENT_FOR`, `BELONGS_TO`, `SAME_ENTITY`, `VERSION_OF`).
  Relationship retrieval joins both endpoints, filters archived/stealth documents, and enforces decoy-mode isolation.
- Schema 20 extended `relationships` with `evidence`, `confidence`, and `createdAt` columns, backed by
  `MIGRATION_19_20` and `Schema20MigrationTest`.

Every Room schema change must include:

1. an incremented database version;
2. an explicit migration;
3. exported schema JSON;
4. migration tests from all supported upgrade paths;
5. a physical-device upgrade check before release.

Destructive fallback is not an acceptable migration strategy for production vault
data.

### 4.3 Vault items, lenses, and experiences

`VaultItem` is durable user-owned content. `primaryLensId` is the single internal lens
used for counts and ordering; `lensTags` is the complete tag set used by lens browsing.
A null primary lens means General/unassigned.

The experience taxonomy has two levels. Primary experiences have no parent;
sub-experiences point to the closest primary. `resolvePrimary` walks the relationship,
and `effectiveLensId` inherits the primary lens where necessary.

Suggestion scoring uses approximately 944 precompiled, boundary-aware regular
expressions warmed during application startup. Add terms to existing keyword groups;
never compile the full regex corpus per capture.

### 4.4 Embeddings and vector records

`VaultEmbeddingGenerator` stores up to two active 100-dimensional, L2-normalized text
embeddings per item:

- `ocr_text`: capped raw OCR text;
- `summary`: title, summary, and labeled metadata in the RAG record layout.

The previous grayscale pseudo-embedding has been disabled because it was not a semantic
model and must not be mixed with text vectors. A future MobileCLIP or equivalent visual
encoder requires a licensed, versioned model and a dimension-compatible index before
image-vector rows may be enabled. Duplicate detection queries OCR and summary
representations. Embedding failures do not fail the item; FTS remains the fallback.

### 4.5 External context model

External context is not automatically a vault import. An `ExternalRecord` is a
temporary or indexed reference owned by a connector and keyed by connector, account,
and external ID. It can contain title, description, temporal fields, sensitivity,
payload, a deep link, retention policy, expiry, seen, and resolved state.

Defined provenance types are Android Share, Calendar, Vault Reminder, Google Tasks,
Gmail, Contacts, Health Connect, and Notification. Defined record categories include
event, email, task, contact, health sample, notification, shared text, shared URL,
shared file, and other. Enum presence does not mean a live connector is shipped.

Supported connector actions are intentionally narrow: open the original record,
promote it into the vault, or mark the contextual record resolved. Each connector
declares its capabilities. Current Calendar capabilities include read, search, create
proposal, open original, and background sync; “create” still means opening a
user-confirmed Calendar editor. Gmail's connector is registered but its safe default
data source reports unavailable until real OAuth configuration is installed. It requests
read/search/open/background-sync capabilities only, never write capability.

---

## 5. On-device intelligence

### 5.1 Deterministic layer

The deterministic layer always runs when its input is available:

- ML Kit OCR;
- image orientation and contrast handling;
- barcode signals and confidence-bearing ML Kit visual labels;
- conservative visual-label fusion that ignores generic labels and requires confidence
  plus separation from the runner-up;
- optional TFLite document classification when a genuine model asset is installed;
- heuristic field extraction (including Luhn/Mod97/MRZ validation, prescription medication/patient extraction, and lens-aware sub-category prioritization);
- compiled keyword experience scoring;
- Room FTS and deterministic rule-based insights.

This layer creates the base item and is the product's reliability floor.

ML Kit label confidence and provenance are preserved through capture and deferred
analysis, stored as internal evidence, and supplied to optional Gemma/Nano enrichment.
OCR remains authoritative for exact text and identifiers. A missing visual classifier
model degrades to scored-label fusion; the app no longer reports a fabricated visual
embedding as available.

### 5.2 Brain evidence, claim verification, and streaming

Retrieved records are numbered in the model prompt. The model is instructed to attach
`[1]`-style citations to factual claims. The RAG engine normalizes `record_1`/`source_1` forms,
removes internal item-ID citations, rejects citation numbers outside the returned source
set, and renders matching numbered cards with supporting excerpts. Locally cached Gmail
or Calendar context can join the prompt only when it matches the question and retention
window. External content is explicitly untrusted data and is never treated as an instruction.

**Claim verification:**
`ClaimVerifier` validates structured claims with exact verbatim OCR/source text matching
across both `VaultItem` and `ExternalRecord` without arbitrary character caps. Contradicted
or unsupported claims fail closed to quoted source excerpts rather than hallucinated model
substitutions.

**Inference streaming:**
`RagEngine.queryStream` emits a `rag_checking_evidence` progress step during generation and
performs claim verification **once** after token generation finishes. This ensures low time-to-first-token
latency without invoking repeated claim-check inference on each streamed chunk. The `enableClaimVerifier`
toggle is strictly honored across both streaming and batch query paths.

### 5.3 LLM tier selection and local runtimes

The unqualified `LlmClient` is backed by `TieredLlmClientSelector`.

**Tier 1 — Qwen3-VL-2B (On-Device via llama.cpp JNI)**

- Preferred primary on-device LLM for devices with ≥4 GB RAM.
- Native runtime built for `arm64-v8a` and `x86_64` using pinned llama.cpp v0.4.0 JNI bridge.
- Uses Q4_K_M language GGUF (~1.1 GB) plus vision projector in `filesDir/models/`, verified
  against SHA-256 hashes defined in `gradle.properties`.
- Primarily operated as an on-device text LLM over deterministic OCR/heuristic evidence; full
  multimodal vision ViT generation on CPU is hardware-gated due to high CPU compute requirements.

**Tier 2 — Gemini Nano**

- Accessed through the ML Kit Prompt API / AICore capability.
- Used when the device reports AICore support and the app is in an allowed foreground state.
- Capable of multimodal image prompting on supported devices.

**Tier 3 — Gemma 3 1B int4 Fallback**

- Downloaded on demand for supported non-AICore devices.
- Roughly 530 MB and executed through MediaPipe `LlmInference`.
- Serves as a lightweight text-only fallback.

All callers must handle unavailable, loading, failed, and privacy-blocked states cleanly.
Deterministic extraction and search remain fully functional even if all LLM tiers are offline.

### 5.4 Gemma model lifecycle

The model is not bundled in the APK/AAB. A supported device may receive at most two
download prompts, separated by seven days. The user initiates download. WorkManager
downloads to a temporary destination, validates size/hash, and only then promotes the
model to its active private-file path. Settings exposes model state and retry/removal
controls.

Troubleshooting order:

1. confirm at least 4 GB device RAM and adequate free storage;
2. confirm network access and no interrupted WorkManager constraint;
3. verify the configured URL and SHA-256 in the release build inputs;
4. inspect download-worker state and model-manager error text;
5. confirm the final private model file exists and hash validation completed;
6. run one fresh text capture and inspect native MediaPipe initialization logs.

Never work around verification by accepting an unverified model file.

### 5.5 Cloud AI boundary

The current build wires `DisabledCloudAiRuntime`. It does not send vault content to a
cloud model, even if a consent-related UI path is visible. The coordinator and privacy
policy layer are preparation for a possible later implementation.

If a future release enables cloud inference, it requires all of the following before
shipping:

- an explicit per-request consent experience;
- a concrete provider and data-processing disclosure;
- updated privacy policy and Play Data safety answers;
- secure transport, authentication, logging redaction, and retention controls;
- tests proving local-only and deny paths;
- category restrictions for highly sensitive content.

Current never-cloud policy categories include passports/identity, prescriptions, lab
results, health-lens content, and unresolved low-confidence documents. That policy is
defense in depth; the present runtime remains globally disabled.

---

## 6. Security and privacy architecture

### 6.1 Security controls

- SQLCipher encrypts the Room database at rest.
- Android Keystore protects database and authentication key material.
- Imported media and Gemma files live in app-private storage.
- PIN and optional biometric authentication protect app access.
- Decoy credentials create a repository-filtered decoy session.
- Failed authentication attempts trigger escalating cooldowns.
- Sensitive activities use screenshot/screen-capture protection where configured.
- Platform backup and data extraction are disabled.
- File sharing uses a scoped FileProvider rather than public raw file paths.
- Portable backups use a user-supplied passphrase, PBKDF2-HMAC-SHA256 (310,000
  iterations), and AES-256-GCM. The archive authenticates its contents and includes
  a transactionally stable SQLCipher snapshot plus private capture media; the
  passphrase is not stored and cannot be recovered. Export builds the snapshot from
  the open database instead of copying a live database file. Restore closes Room
  before copying the rollback database and installing the validated staged archive.
  Snapshot export uses a separate SQLCipher connection and a transaction, and retains
  the Room schema version. Import checks integrity and requires the current schema.
  Key persistence verifies ciphertext staged under the final basename and installs
  it atomically. It recovers legacy `.tmp`-authenticated keys where possible and never
  silently replaces an unreadable key. A lost key cannot be reconstructed.
- A native "Privacy & Security Dashboard" inside Settings surfaces these offline guarantees and local execution characteristics to build user trust.
- **Widget privacy hardening:** `VaultBrainWidget` displays public launcher shortcuts only (Capture, Scan, Ask Brain) and does not store unauthenticated document titles or dates in Glance preferences. `WidgetUpdateWorker.clearLegacyState()` automatically purges legacy Glance preferences on app upgrade.
- **Notification lockscreen privacy:** `AlertWorker` sets `NotificationCompat.VISIBILITY_PRIVATE` with generic notification copy on the device lockscreen to prevent document title/body leakage. Delivery uses an atomic `claimVisible()` query to eliminate concurrent worker delivery races.
- **Decoy mode state wiping:** In addition to repository-level SQLCipher query filtering, `ItemDetailViewModel` actively observes `DecoySessionState.isDecoy` and immediately cancels active retrieval jobs and clears `_uiState` to prevent sensitive cached data from persisting across decoy mode transitions.
- **Media vault file-at-rest encryption & decoy quarantine:** `MediaVaultStorage` encrypts captured photos and documents on disk with authenticated AES-256-GCM using hardware-backed keystore credentials. Decryption is streamed in-memory to Coil via `VaultMediaFetcher` without leaving plaintext files on disk. In Decoy Mode, media queries, imports, and streams fail closed, and the media root isolates to a decoy storage space.
- **Vector storage threat boundary & decoy isolation:** ObjectBox semantic vector data is maintained in app-private sandbox storage (`filesDir/objectbox`). All queries (`nearestNeighbors`, `nearestCollectionNeighbors`) and mutations are gated by `DecoySessionState`; when decoy mode is active, the vector store returns empty results and suppresses writes, preventing vector leakage.
- **Interactive LLM mutex preemption:** `QwenLlmClient` supports `preemptBackground()` so foreground Brain chat user requests immediately preempt or abort long-running background analysis workers holding `nativeMutex`.

Security is a system property, not a claim based on one component. Release review must
still inspect logs, exported components, intent validation, database migrations,
FileProvider paths, Web/URI handling, and third-party SDK behavior.

### 6.2 Android permissions

| Permission | Why it exists | User-control expectation |
|---|---|---|
| `CAMERA` | Capture document images. | Request when camera capture is chosen. |
| `RECORD_AUDIO` | Voice capture. | Request when voice is chosen. |
| `POST_NOTIFICATIONS` | Alerts, reminders, model/capture status where applicable. | Request in context on Android 13+. |
| `READ_CALENDAR` | Read only user-selected device calendars. | Request from Calendar connection flow. |
| `INTERNET` | Optional Gemma download, Play Billing, and configured network connectors. | Does not imply cloud AI upload. |
| Foreground-service permissions | Long-running data sync / short-service operations. | Notifications and service type must match Android rules. |

Nemory does not request `WRITE_CALENDAR`. It relies on the system Calendar editor for
user-confirmed creation.

### 6.3 Network and data-flow boundaries

In the current build:

- OCR, extraction, classification, embeddings, retrieval, and supported LLM inference
  occur locally;
- Calendar is read from the Android Calendar Provider and cached locally;
- a Gemma download retrieves model bytes from the configured host, but inference stays
  on device;
- portable backup export/import uses only the destination chosen in Android's document
  picker; Nemory has no direct Drive upload credential;
- the Gmail data source is unconfigured in this build, so no Gmail account or message is
  accessed; enabling it later requires `gmail.readonly`, OAuth verification, and updated
  release disclosures;
- Play Billing communicates with Google Play if the billing path is used;
- there is no ad SDK, analytics SDK, or remote crash-reporting SDK;
- local crash information can be explicitly shared by the user;
- cloud AI execution is disabled.

Before every store submission, inspect the final release dependency graph and AAB.
Documentation cannot substitute for verifying the exact shipped SDKs and endpoints.

### 6.4 Retention and deletion

- Vault items remain until the user deletes them or clears application data.
- Deleting a collection does not delete its items.
- Archived collections retain memberships for restoration.
- Calendar and other external records are bounded by connector retention and sync
  reconciliation rather than treated as permanent vault imports.
- Disconnecting or losing Calendar permission removes its cached records.
- Removing the app or clearing its storage removes private local data according to
  Android platform behavior.
- User-initiated exports leave Nemory's private boundary and are then governed by the
  chosen destination.

---

## 7. Privacy policy publication draft

> **Effective date:** 2026-08-31
> **Publisher action required:** Review contact details, Play Data safety answers, and
> the final release artifact before publishing. This draft is product documentation,
> not legal advice.

### Nemory Privacy Policy

Nemory is a local-first personal document vault. This policy explains what information
the app accesses, how it is used, and the choices available to you.

#### Information you add

Nemory processes content that you choose to capture, type, record, select, or share to
the app. This may include images, PDFs, recognized text, titles, notes, metadata,
collection membership, reminders, and Brain conversations. It can include sensitive
information depending on what you choose to store.

#### Device information and permissions

Nemory may ask for camera, microphone, notification, or Calendar permission
when you use a feature that needs it. You can deny or revoke permissions in Android
Settings. Core vault content remains available subject to the permission needed for a
specific action.

When Calendar is connected, Nemory reads only the calendars you select and stores a
bounded local cache of event context. Disconnecting Calendar or revoking permission
removes that cached Calendar context when the app processes the change. Nemory does
not request permission to write directly to your Calendar. Event proposals open your
Calendar app for your review and confirmation.

#### How processing works

Nemory performs OCR, deterministic extraction, search, embeddings, and supported AI
inference on your device. If you choose to download the optional Gemma model, the app
uses the internet to download model files, verifies them, and then runs the model
locally. The current release does not send vault content to a cloud AI provider.

#### Storage and security

Vault records are stored in an encrypted local database. Imported media and downloaded
model files are stored in app-private storage. Nemory offers a PIN, optional biometric
unlock, automatic locking, and an optional decoy mode. No security measure is
infallible; keep your device and Android security updates current.

#### Sharing and external services

Nemory does not sell your personal information. The current build contains no
advertising, analytics, or remote crash-reporting SDK. Google Play services may process
technical or purchase information if you use Play Billing. The configured model host
receives ordinary network information, such as IP address, when you choose to download
Gemma; the model download does not include your vault documents.

If you export or share a vault item, Android sends the selected content to the app or
destination you choose. That destination's privacy practices then apply.

#### Retention and deletion

Your vault data remains on your device until you delete it, clear application storage,
or uninstall the app, subject to Android behavior. External Calendar context is a
bounded cache and is removed when disconnected, permission is revoked and processed,
or records expire/reconcile. Collection removal never silently deletes member items.

#### Children

Nemory is not directed to children under the age required for independent consent in
their jurisdiction. Store age targeting and content questionnaires must match the
final distribution settings.

#### Changes

If a future version enables cloud AI, adds analytics, advertising, account sync, or a
new external integration, this policy and the Play Data safety declaration must be
updated before that version is released. Material changes should be communicated in
the app or store listing as appropriate.

#### Contact

Privacy questions may be sent to `fedrev92@gmail.com`. Replace this address in both the
app and store listing if the publisher adopts a dedicated support or privacy address.

---

## 8. Build and development guide

### 8.1 Toolchain

- Android Gradle Plugin 9.3.2
- Kotlin 2.2.21
- Java/JVM 17
- compileSdk / targetSdk 36
- minSdk 29
- Compose + Material 3
- Hilt dependency injection

Use the checked-in Gradle wrapper. Android SDK components and a Java 17-compatible JDK
must be installed.

### 8.2 Common commands

PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :core:database:testDebugUnitTest :feature:capture:testDebugUnitTest :core:ai:vision:testDebugUnitTest :core:ai:heuristics:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Unix-like shell:

```bash
./gradlew :app:assembleDebug
./gradlew :core:database:testDebugUnitTest :feature:capture:testDebugUnitTest :core:ai:vision:testDebugUnitTest :core:ai:heuristics:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Run the narrowest relevant tests while developing, then the documented unit suite and
debug assembly before handoff. Database changes require migration instrumentation
tests. Device-dependent Nano, Gemma, Calendar, notification, camera, and Share behavior
requires physical-device validation.

### 8.3 Release signing

Release signing is supplied through environment variables:

```text
NEMORY_UPLOAD_STORE_FILE
NEMORY_UPLOAD_STORE_PASSWORD
NEMORY_UPLOAD_KEY_ALIAS
NEMORY_UPLOAD_KEY_PASSWORD
```

Never commit a keystore, passwords, service credentials, model access tokens, or local
signing configuration. The release build enables shrinking, resource shrinking, and
optimized ProGuard rules. Always smoke-test the signed, minified artifact because
reflection and generated-code behavior can differ from debug.

### 8.4 Engineering conventions

- Keep all user-facing strings in both `values/strings.xml` and
  `values-ar/strings.xml`; changed release-facing text must not be hard-coded in only
  one language.
- Test Arabic right-to-left layout, pluralization, truncation, and date/time formats.
- Prefer minimal changes that match surrounding code.
- Keep deterministic capture functional without LLMs.
- Never compile the experience keyword regex corpus per request.
- Never make collection suggestions silently mutate membership.
- Preserve FTS fallback when vector generation fails.
- Keep external records distinct from durable vault imports.
- Validate external intents, MIME types, URIs, and persisted read grants.
- Do not log OCR text, secrets, PINs, full paths, calendar descriptions, or prompt
  contents in release builds.

---

## 9. Testing strategy and acceptance plan

### 9.1 Automated layers

1. **Unit tests:** parsers, heuristics, data mapping, repositories, view models,
   reminder logic, and policy rules.
2. **Room migration tests:** every supported schema upgrade, including foreign-key and
   index validation.
3. **Instrumentation tests:** Android components, permissions, encrypted database,
   provider behavior, and Compose interactions where practical.
4. **Build verification:** debug and signed/minified release artifacts.
5. **Static review:** permissions, exported components, dependency graph, ProGuard,
   translated strings, secrets, and policy-sensitive SDKs.

### 9.2 Core manual regression

#### Install, onboarding, and security

- Fresh install completes onboarding without granting optional permissions.
- PIN create, unlock, change, and cooldown behavior work.
- Biometrics accept, reject, cancel, and unavailable paths work.
- Auto-lock triggers after the configured interval and after process recreation.
- Decoy PIN exposes no real vault, collection, Calendar, reminder, or Brain content.

#### Capture

- Camera capture handles a clear receipt and a rotated document.
- Gallery handles large images without out-of-memory failure.
- PDF handles one page and multiple pages.
- Text/paste and voice text save correctly.
- OCR, type, title, summary, dates, amounts, and metadata can be reviewed and edited.
- Save remains successful with Nano/Gemma unavailable.
- Duplicate detection does not block legitimate saves.

#### Android Share

- Share text from another app.
- Share a URL from a browser.
- Share one image from Gallery.
- Share a PDF from Files.
- Share multiple supported images/PDFs.
- Reject unsupported or inaccessible URIs with a useful message.
- Kill/recreate the app during review and confirm persisted URI/private-copy behavior.
- Verify source provenance without exposing it unnecessarily in the UI or logs.

#### Vault and collections

- FTS returns OCR text matches.
- Semantic search works when embeddings exist and falls back when they do not.
- Create, rename, archive, restore, and delete a collection.
- Add/remove one item in multiple collections; duplicate membership is ignored.
- Collection deletion never deletes the member item.
- Metadata labels and order match the document classification.

#### Brain and briefing

- Ask a question with one clear supporting item and verify its source.
- Ask when no source exists; the response must not fabricate a vault fact.
- Run with Nano, with Gemma, and with neither.
- Proposed write actions require confirmation and deterministic validation.
- Today insights remain available with LLMs disabled.

#### Calendar

- Deny permission, grant later, and revoke after connection.
- List calendars and choose a subset.
- Confirm only selected-calendar events appear.
- Test all-day, recurring, time-zone, updated, and deleted events.
- Confirm the 30-day past / 90-day future bounds.
- Force periodic/manual sync and check duplicate-free reconciliation.
- Open the original event.
- Propose a new event; verify the Calendar editor is prefilled and no event exists if
  the editor is discarded.
- Disconnect and verify cached Calendar records are cleared.

#### Vault Reminders

- Create a standalone reminder and reminders linked to an item, event, and collection.
- Edit title and due time; verify the old WorkManager schedule is replaced.
- Receive a notification with app foregrounded, backgrounded, and after reboot.
- Open, snooze 15 minutes, complete, and dismiss from the notification.
- Deny notification permission; verify in-app reminder state remains correct.
- Delete linked content; verify foreign keys become null without losing the reminder.

#### Gemma

- Unsupported/low-RAM device does not offer an invalid download path.
- Cancel/interruption leaves no active partial model.
- Hash mismatch is rejected.
- Valid download survives app restart.
- Fresh capture produces optional enrichment with the native engine initialized.
- Remove and redownload work; low-storage failure is understandable.

#### Internationalization and accessibility

- Switch between English and Arabic; verify RTL navigation, dialogs, cards, metadata,
  dates, and numerals.
- Test large font, display scaling, dark theme, TalkBack order/labels, keyboard focus,
  and touch targets.

### 9.3 Device matrix

At minimum, cover:

- API 29 low/mid-range phone;
- Android 13+ device for notification permission behavior;
- current Android target-level device;
- non-AICore Samsung or equivalent device using Gemma;
- AICore-capable device using Gemini Nano;
- tablet or foldable layout;
- Arabic locale and RTL;
- low-storage, offline, background restriction, battery saver, low-memory, and thermal
  stress scenarios.

### 9.4 Latest known validation baseline

#### 14 September 2026 Validation Baseline:

- **Release Build & Packaging:** `:app:assembleRelease` passed with full native CMake compilation (`arm64-v8a` + `x86_64`) via NDK 27.2.12479018, producing `app-release-unsigned.apk` (317,693,402 bytes) with R8 minification and resource shrinking.
- **Static Analysis & Lint:** `:app:lintDebug` completed with **0 errors and 0 warnings**. Unused resources removed; intent launches in `MainActivity` made explicit.
- **Localization:** `python scripts/check_localization.py` confirmed 100% key and format argument parity between `values/strings.xml` and `values-ar/strings.xml`.
- **Unit & Contract Testing:** All unit tests pass across database, heuristics, vision, LLM, RAG, capture, vault, settings, briefing, and brain modules.
- **Intelligence Benchmark:** `IntelligenceBenchmarkTest.kt` passes 100/100 deterministic test cases across retrieval recall, arithmetic, temporal queries, anti-hallucination, grounding, and cross-document reasoning.
- **Database & Migration Instrumentation (Samsung Galaxy Tab S7+, SM-T975, Android 13):**
  - 13/13 database instrumentation tests passed, including `Schema19MigrationTest` (18→19), `DerivedFactMigrationTest` (17→18), and `NotificationVisibilityTest`.
  - Backup & restore: 2/2 passed (`DriveBackupManagerInstrumentedTest`), confirming SQLCipher snapshot creation, AES-256-GCM staging, import, and rollback recovery on the current schema.
  - Native inference: `LlamaBridgeOnDeviceTest` confirmed Q4_K_M GGUF model loading and on-device text generation.
- **Live Tablet UX (SM-T975):** Verified navigation rail, adaptive layout, speed-dial capture FAB, Collections first on Today, interactive Brain chat citations `[1]`, immediate Decoy wipe on Item Detail, and Arabic RTL layout.

#### Historical baseline (commit `45c24767`):
- 128 unit tests with zero failures;
- Room migration instrumentation completed 6/6 on SM-T975;
- Physical Android Share checks covered text, image, PDF, and multiple files;
- Calendar sync and prefilled event editor exercised;
- Vault Reminder created and delivered.

---

## 10. Google Play and release operations

### 10.1 Policy source of truth

Google Play requirements can change and can differ by developer-account history,
region, app category, and track. The current Play Console is authoritative. Do not
hard-code historic claims such as a universal tester count or duration into release
gates without checking the account's current Production access screen.

Useful official references:

- Production access testing requirements: <https://support.google.com/googleplay/android-developer/answer/14151465>
- Target API requirements: <https://developer.android.com/google/play/requirements/target-sdk>
- Data safety guidance: <https://support.google.com/googleplay/android-developer/answer/10787469>

### 10.2 Pre-release checklist

- [ ] Increment `versionCode` and set the intended `versionName`.
- [ ] Confirm application ID, name, icons, and adaptive icon rendering.
- [ ] Build and install the signed, minified release artifact.
- [ ] Run unit, migration, and targeted instrumentation tests.
- [ ] Re-run the physical matrix for changed device-dependent features.
- [ ] Confirm Room schema JSON and migrations are committed.
- [ ] Verify Gemma URL and SHA-256 release configuration.
- [ ] Search the release artifact and repository for secrets and debug endpoints.
- [ ] Audit the final dependency graph for data-collecting SDKs.
- [ ] Review exported activities, deep links, provider paths, and intent validation.
- [ ] Review English and Arabic strings and screenshots.
- [ ] Verify support/privacy contact addresses and hosted privacy-policy URL.
- [ ] Confirm content-rating, ads, app-access, permissions, and Data safety answers
      against the final AAB, not an old draft.
- [ ] Upload mapping/native symbols if Play requests them for the artifact.

### 10.3 Data safety review guide

Do not copy a blanket “no data collected” answer from an old document. For Play's
definitions, distinguish on-device processing, ephemeral transmission, collection by
the developer, sharing with third parties, and service-provider processing. Audit:

- Play Billing behavior and purchase information;
- the optional Gemma model download host and transmitted network metadata;
- any enabled Drive/Gmail code in the exact build variant;
- Calendar content, which is read and retained locally in the current implementation;
- user-initiated exports/shares;
- crash logs and whether the user explicitly initiates sharing;
- any SDK or endpoint added since this revision.

Current product facts for the questionnaire are: no ads, no analytics SDK, no remote
crash SDK, cloud AI disabled, and core vault processing local. The final declaration
must still be made from Play's current definitions and the shipped artifact.

### 10.4 Store listing draft

**App name:** Nemory

**Short description (English):**
Capture, organize, search, and remember documents—privately on your device.

**Full description (English):**

Nemory turns scattered documents and life-admin details into a private, searchable
vault on your Android device.

Capture a receipt, bill, prescription, ticket, form, note, image, or PDF. Nemory uses
on-device recognition and extraction to make it easier to review, organize, and find
later. Create your own collections, search recognized text, ask Brain questions based
on your saved items, and keep important dates visible with Today, selected Calendar
context, and Vault Reminders.

Key features:

- capture from camera, gallery, PDF, text, voice, or Android Share;
- on-device OCR and document understanding;
- personal collections and fast vault search;
- source-grounded Brain questions over your own saved content;
- optional on-device Gemini Nano or downloadable Gemma enrichment;
- user-selected Calendar context with read-only access;
- local reminders linked to items, collections, or events;
- encrypted local database, app lock, biometrics, and decoy mode;
- English and Arabic interface.

Nemory is designed to remain useful without cloud AI. The current release performs
supported AI processing on the device and contains no advertising or analytics SDK.

**Short description (Arabic):**
التقط مستنداتك ونظّمها وابحث فيها وتذكّر مواعيدها بخصوصية على جهازك.

**Full description (Arabic):**

يحوّل Nemory المستندات وتفاصيل المهام اليومية المتفرقة إلى خزنة خاصة قابلة للبحث
على جهاز Android.

التقط إيصالاً أو فاتورة أو وصفة أو تذكرة أو نموذجاً أو ملاحظة أو صورة أو ملف PDF.
يستخدم Nemory التعرّف والاستخراج على الجهاز لتسهيل المراجعة والتنظيم والعثور على
المحتوى لاحقاً. أنشئ مجموعاتك الخاصة، وابحث في النصوص المستخرجة، واسأل Brain عن
المعلومات المحفوظة، وتابع المواعيد المهمة عبر شاشة اليوم وسياق التقويم الذي تختاره
وتذكيرات الخزنة.

أهم الميزات:

- الالتقاط بالكاميرا أو المعرض أو PDF أو النص أو الصوت أو المشاركة من Android؛
- تعرّف ضوئي وفهم للمستندات على الجهاز؛
- مجموعات شخصية وبحث سريع في الخزنة؛
- إجابات Brain مستندة إلى المحتوى الذي حفظته؛
- تحسين اختياري على الجهاز باستخدام Gemini Nano أو نموذج Gemma القابل للتنزيل؛
- سياق تقويم للقراءة فقط ومن تقاويم يختارها المستخدم؛
- تذكيرات محلية مرتبطة بالمستندات أو المجموعات أو الأحداث؛
- قاعدة بيانات محلية مشفّرة وقفل للتطبيق وبصمة ووضع تمويه؛
- واجهة بالإنجليزية والعربية.

صُمم Nemory ليبقى مفيداً من دون ذكاء اصطناعي سحابي. ينفّذ الإصدار الحالي المعالجة
المدعومة على الجهاز ولا يحتوي على إعلانات أو أدوات تحليلات.

Do not mention unfinished Gmail, Tasks, Contacts, cloud sync, or cloud
AI in the public listing as available features.

### 10.5 Asset checklist

- 512 × 512 high-resolution app icon;
- 1024 × 500 feature graphic;
- current phone screenshots for onboarding, capture/review, collections/search,
  Brain, and Today/Calendar/reminders;
- tablet screenshots if tablet distribution is enabled;
- English and Arabic screenshots where the listing locale supports them;
- no sensitive real documents, names, addresses, calendar details, or notifications in
  screenshots.

### 10.6 Release notes for 1.1.1 (code 10, last published release)

Suggested English release notes:

> Nemory now brings selected device Calendar events into Today and adds local Vault
> Reminders you can link to documents, collections, or events. Calendar access is
> read-only, and creating an event always opens your Calendar app for confirmation.
> This update also improves Android Share handling, on-device Gemma testing, encrypted
> database migrations, and reliability across capture and background processing.

Suggested Arabic release notes:

> يعرض Nemory الآن أحداث التقويم التي تختارها في شاشة اليوم، ويضيف تذكيرات محلية
> يمكن ربطها بالمستندات أو المجموعات أو الأحداث. الوصول إلى التقويم للقراءة فقط،
> وإنشاء حدث يفتح تطبيق التقويم دائماً لتأكيدك. يتضمن التحديث أيضاً تحسينات على
> المشاركة من Android واختبار Gemma على الجهاز وترحيل قاعدة البيانات والموثوقية.

### 10.7 Track progression

1. Upload to internal testing and resolve install, signing, pre-launch, and policy
   warnings.
2. Run a focused internal regression on the signed artifact.
3. Configure closed testing according to the requirements shown for the actual
   developer account.
4. Collect structured feedback and crash/reproduction details without requesting real
   sensitive documents.
5. Fix blockers, increment the version code, and repeat the relevant regression.
6. Apply for production access only after the account-specific criteria and testing
   evidence are satisfied.
7. Use staged rollout where available and monitor Play vitals, reviews, support mail,
   ANR/crash trends, and permission-related failures.

---

## 11. Roadmap

> **Nemory Intelligence V1 architecture is complete. V1.1 is the Quality, Performance, Validation and Release-Hardening milestone. Full CPU Qwen-VL vision is experimental and disabled for normal production use.**

### 11.1 Completed foundations (V1 Architecture)

- Local encrypted vault (Room schema 20 + SQLCipher) and biometric/PIN security gate with Decoy mode;
- Multimodal capture, PDF parsing, OCR, heuristics, and Android Share integration;
- Personal collections (manual-first, many-to-many, non-destructive);
- FTS + ObjectBox vector hybrid retrieval;
- Extractive knowledge graph (`derived_facts`, `knowledge_entities`, `item_entities`, `relationships` via `RelationshipType`);
- Brain RAG with `LocalAgent` bounded retrieval loop (`ReasoningBudget`: FAST/NORMAL/DEEP);
- `ClaimVerifier` verbatim evidence checking;
- `PendingWriteCard` structured write confirmation (`PROPOSE -> VALIDATE -> CONFIRM -> EXECUTE`);
- Tiered on-device LLM runtime (Tier 1 Qwen3-VL-2B via llama.cpp JNI, Tier 2 Gemini Nano, Tier 3 Gemma 3 1B fallback);
- Deterministic Today engine and `DailyIntelligence` proactive scoring;
- Vault Reminders and read-only Android Calendar connector;
- Encrypted portable SAF backup/restore with staged rollback.

### 11.2 Nemory Intelligence V1.1 Quality & Release Hardening Plan

| Phase | Priority | Objective | Focus & Exit Condition |
|---|---|---|---|
| **0. Truth-sync** | Immediate | Reconcile docs and source claims | Single authoritative story across all docs; verified source alignment. |
| **1. Real Intelligence Evaluation** | Critical | `QwenEndToEndBenchmark` | Real-model evaluation on controlled vault; Nemory Intelligence Score (Target ≥80) and retrieval/agent/graph ablations. |
| **2. Latency & True Streaming** | Critical | Interactive Brain UX | Verify native token streaming; instrument Tsearch/TTFT/Tanswer/Tverify/Tfinal; keep status messages ephemeral. |
| **3. Agent Action Hardening** | High | Safe idempotent actions | `PendingWriteCard` lifecycle: stable IDs, idempotency, rotation, process death, source revision revalidation, decoy invalidation. |
| **4. Proactive Intelligence** | High | Selective Today insights | Deterministic eligibility gates, persistent dedupe fingerprint (`type+sourceIds+coreFact`), suppression lifecycle (≤3 insights). |
| **5. Knowledge Quality** | Medium-High | Graph precision over size | Measure relationship precision for `RelationshipType`; alias resolution; bounded Brain prompt context. |
| **6. Device / Release Matrix** | Critical | Release candidate verification | Test signed/minified APK/AAB on low/mid (API 29–31), SM-T975, and AICore hardware; test model download/retry and cross-device restore. |
| **7. Arabic OCR / Vulkan** | R&D | Future multimodal capabilities | Dedicated lightweight Arabic OCR model; Vulkan GPU acceleration experiments (separate non-blocking track). |
| **8. External Connectors** | Later | Expansion integrations | Gmail OAuth, Tasks, Contacts, Cloud AI — strictly deferred until V1.1 quality is proven. |

### 11.3 Future connectors

Google Tasks, Contacts, and notification-derived context remain future
work. Gmail has a disabled-by-configuration read-only foundation but is not a shippable
account connection until OAuth verification and release configuration are complete.
Each connector must ship with:

- a narrow capability declaration;
- least-privilege permissions or OAuth scopes;
- explicit connection and disconnection UX;
- bounded retention and deletion behavior;
- decoy-mode isolation;
- provider rate-limit and error handling;
- local search/indexing privacy rules;
- current privacy-policy and Play Data safety updates;
- tests proving that external context is not silently converted into permanent vault
  content.

### 11.4 Cloud features

No cloud AI or account sync should be enabled merely because coordinator interfaces or
sync modules exist. Enabling one is a product, privacy, security, policy, and operations
project requiring explicit approval and a new documentation revision.

---

## 12. Operations and troubleshooting

### 12.1 Capture appears stuck

Check URI access, private import completion, image/PDF memory use, OCR client state,
deferred worker state, and whether the item was saved with pending or retryable
processing. The UI should distinguish “saved, enrichment pending” from “not saved.”

### 12.2 Search misses a known document

Verify the Room item exists, FTS fields were updated, indexing state is not permanently
claimed, ObjectBox rows exist where expected, and the query is not hidden by decoy or
collection filters. A missing vector must not prevent an FTS result.

### 12.3 Calendar shows no events

Check permission, connection state, selected calendar IDs, event window bounds,
provider availability, sync worker result, time zone, and decoy mode. If permission was
revoked, an empty view plus cache deletion is correct behavior.

### 12.4 Reminder did not notify

Check the reminder status/due time, notification permission, channel state, unique
WorkManager job, device battery/background restrictions, reboot rescheduling, and
whether the notification action already completed or dismissed the reminder. The
in-app reminder is the source of truth even when Android suppresses delivery.

### 12.5 Brain cannot answer

Confirm a relevant vault item exists, then test FTS retrieval independently of the LLM.
Inspect model readiness, privacy policy result, foreground requirement for Nano, Gemma
download/native initialization, prompt size, and source reranking. Report lack of
evidence clearly; do not invent an answer.

---

## 13. Documentation maintenance

Update this file in the same change whenever any of the following changes:

- version name/code, SDK levels, permissions, or application identity;
- Room schema, entities, retention, migrations, or encryption behavior;
- capture, Share, collection, Brain, Calendar, or reminder workflows;
- AI models, download host/hash requirements, or cloud processing status;
- active third-party SDKs, network destinations, analytics, billing, or advertising;
- Play listing, Data safety posture, privacy contact, or release procedures;
- implemented/scaffolded/planned feature status.

Repository documents consolidated here:

- `VaultBrain_Unified_Specification_v5.md`
- `vaultbrain_assessment.md`
- `GENAI_ROADMAP_PLAN.md`
- `implementation_plan.md`
- `task.md`
- `delegation_bots.txt`
- `CLOSED_TESTING_TEST_PLAN.md`
- `PLAY_STORE_LAUNCH_CHECKLIST.md`
- `PLAY_CONSOLE_SETUP_v5.md`
- `PLAY_CONSOLE_SETUP_v6.md`
- `PRIVACY_POLICY_DRAFT.md`
- `STORE_LISTING_DRAFT.md`
- `RELEASE_NOTES_v5.md`
- `RELEASE_NOTES_v6.md`

Generated build logs, including `feature/brain/out.txt`, and empty scratch files such
as `list.txt` are not product documentation and are intentionally excluded.

---

## 14. Glossary

| Term | Meaning |
|---|---|
| Vault item | Durable content deliberately saved by the user. |
| Personal collection | User-controlled many-to-many grouping of vault items. |
| Lens | Internal high-level metadata category and specialist view. |
| Experience | User-intent classification, organized as primary and sub-experiences. |
| External record | Bounded connector-owned context that is not automatically a vault item. |
| Vault Reminder | Local Nemory reminder, optionally linked to vault or external context. |
| FTS | Room full-text search fallback and lexical retrieval path. |
| Embedding | Local normalized numeric representation used for semantic retrieval. |
| RAG | Retrieval-augmented generation grounded in retrieved vault records. |
| Gemini Nano | Device-provided AICore/ML Kit LLM tier where supported. |
| Gemma | Optional downloadable on-device MediaPipe text LLM tier. |
| Decoy mode | Separate session that hides real repositories and external context. |
