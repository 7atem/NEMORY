# VaultBrain / Nemory — GenAI Feature Roadmap Plan (Corrected v2)

> **Status:** Approved implementation plan — Phases 1–4 implementation is complete, but production device-matrix validation remains a blocking release gate. Phase 5 implementation includes privacy foundations, local-first translation, private voice capture with guided on-device language preparation, advanced read-only tools, consent-gated Brain, revocable category approvals, and confirmed pin/unpin/archive/restore/reminder/clear-reminder/media-completion tools; live cloud activation and further write tools remain staged.  
> **Purpose:** Consolidate the technical discussion with the product owner’s constraints and the SDK/UX corrections from the architecture review.

---

## What changed from v1

- Replaced version-based Nano selection with **capability-based detection** (`getBaseModelName()`, feature availability flags, download state).
- Switched on-device SDK recommendation to **direct ML Kit Prompt API**; Firebase AI Logic is reserved only for the optional cloud fallback.
- Removed WorkManager-based Nano enrichment; enrichment is now **foreground-only** with a full state machine.
- Split in-app camera/import review from the Android share target: shares are ingested immediately, while camera/import keeps its review step.
- Made manual classification authoritative through `userClassificationOverride ?: aiClassification`.
- Split extraction, Nano enrichment, and indexing into separately observable pipeline states.
- Added atomic claims, stale-claim recovery, bounded retries, stable error codes, and foreground cancellation.
- Added single-turn on-device chat constraints: local app-managed memory, token budgeting, no native multi-turn/function calling.
- Added structured-output fallback chain and code-bound citations.
- Replaced the simple cloud toggle with **contextual consent** (NEVER / ASK / ALLOWED) per document category.
- Made unknown or unresolved-sensitivity items fail closed: no cloud processing until deterministic evidence or an authoritative user classification resolves the category.
- Added conditional Nano warmup only when useful, foreground-safe, and permitted by battery/thermal state.
- Moved duplicate detection fully after persistence and moved the first deterministic query tools into Phase 3.
- Added a UX and trust-polish milestone so the product exposes calm outcomes rather than AI infrastructure.
- Tightened ad privacy rules: ads never see document metadata, queries, or lens names.
- Reordered milestones to prioritize capability detection, deterministic Today insights, and privacy-safe tooling before advanced features.

---

## 1. Product Constraints (Non-Negotiable)

| # | Constraint | Implication |
|---|---|---|
| 1 | **Users first, monetization later.** | The first public launch is free and ad-free. Monetization is added only after product-market fit and retention are proven. |
| 2 | **All users get all core product capabilities.** | No feature is gated by payment. Hardware/OS-dependent enhancements degrade gracefully: typed capture replaces unavailable API 31+ on-device voice, and deterministic Brain/search remains available without Nano. |
| 3 | **Monetization = ads + optional “Remove Ads” purchase.** | No paywalled features. Revenue comes from advertising and an affordable way to remove ads. Offer monthly subscription, discounted annual subscription, and one-time lifetime purchase. |
| 4 | **No LITE / STANDARD / PRO marketing tiers.** | `AiTier` is deleted. Runtime capabilities and ad entitlement are separate models. |
| 5 | **Global exposure / mass-market reach.** | Features must degrade gracefully on low-end hardware; the app must remain usable on devices without AICore. |
| 6 | **Privacy-first, on-device default.** | Cloud LLMs are not the default. Gemini Nano / ML Kit Prompt API is preferred; cloud fallback requires explicit, informed consent. |
| 7 | **Ads must never learn user vault content.** | No personalized ads based on documents, lens names, Brain queries, or capture metadata. |

---

## 2. Current Architecture Snapshot

The app is already a modular, Clean-Architecture-influenced MVVM/MVI Android app with on-device AI scaffolding:

```
:app
├── :core:common          → domain models (VaultItem, Classification, LensId)
├── :core:database        → encrypted Room + SQLCipher repository
├── :core:vectorstore     → ObjectBox HNSW embeddings store
├── :core:security        → PIN, biometric, decoy vault
├── :core:notifications   → expiry/reminder alerts
├── :core:billing         → BillingManager + FeatureGate (lens access already universal)
├── :core:ai:vision       → ML Kit OCR/labels/barcodes + TFLite DocumentClassifier
├── :core:ai:heuristics   → regex-based metadata extraction + classification fusion
├── :core:ai:embeddings  → text/vision embedding models
├── :core:ai:llm         → real ML Kit Nano provider + experimental QNN/ONNX alternatives
├── :core:ai:rag          → RagEngine (retrieval + rerank + optional LLM synthesis)
└── :feature:*            → capture, vault, brain, lenses, settings
```

### Verified implementation baseline

- `FeatureGate` now controls ads only; Brain and all lenses are universally accessible.
- Old Brain purchase and `AiTier` feature-routing scaffolding has been removed.
- `NanoPromptClient` uses the direct ML Kit Prompt API with official status and download APIs.
- Capability checks and privacy/consent decisions are separate, testable concerns.
- `RagEngine` keeps retrieval sources and falls back to deterministic local answers when Nano is unavailable.
- `OnnxLlmClient` and `QnnLlmClient` remain experimental alternatives and are not production fallbacks.
- Cloud inference is intentionally not wired yet; it remains an explicit-consent Phase 5 feature.

---

## 3. Strategic Goal

Turn VaultBrain into a **private, personalized RAG assistant** for every user:

- Every captured document is understood, embedded, and made searchable.
- The “Brain” chat answers natural-language questions over the user’s own vault, with citations and guardrails.
- Capture, reminders, and insights are enhanced by GenAI but remain fast and private by default.
- The public launch is free and ad-free; after retention is proven, monetization may use privacy-safe ads plus optional Remove Ads lifetime and subscription choices, without gating product capabilities.

---

## 4. Foundation Work (Required Before Any Feature)

### 4.1. Replace `AiTier` Feature Routing with Capability Detection

Introduce a device/runtime capability model populated at runtime. User consent does not belong in this model; it is owned by the privacy policy engine.

```kotlin
data class AiDeviceCapabilities(
    val localPromptAvailable: Boolean,
    val modelStatus: OnDeviceModelStatus,       // app-owned stable status model
    val modelName: String?,                     // from getBaseModelName()
    val imageInputAvailable: Boolean,
    val structuredOutputAvailable: Boolean
)
```

- Probe the official ML Kit generation client status; do not infer support from the AICore package being installed.
- Track model download state and surface it in UI.
- Delete `AiTier` entirely. Use `AiDeviceCapabilities` for runtime support and `AdEntitlement` only for ad visibility.
- Base throttling on measured device/runtime signals such as low-RAM, thermal, and battery state—not a product tier.

### 4.2. Implement a Real On-Device LLM Client via ML Kit Prompt API

Create `NanoPromptClient` (rename from `AicoreLlmClient`) in `:core:ai:llm`:

- Add dependency:
  ```kotlin
  com.google.mlkit:genai-prompt:1.0.0-beta4 // pinned; Nano v4 compatibility fixes
  ```
- Bind to the device model through ML Kit, not raw AICore internals.
- Expose `generate()` / `generateStream()` through the existing `LlmClient` interface.
- Implement capability checks and model download state.
- Keep `OnnxLlmClient` and `QnnLlmClient` as experimental alternatives behind `LlmClient`.

### 4.3. Add a Cloud Fallback via Firebase AI Logic (Opt-In Only)

For devices without on-device Nano, offer optional cloud processing:

- Add dependency:
  ```kotlin
  com.google.firebase:firebase-ai:...       // cloud + unified hybrid entry point
  ```
- Use Firebase AI Logic with `ONLY_IN_CLOUD` mode when the user explicitly consents.
- Use Firebase App Check, strict quotas, and a paid project with appropriate data-processing terms.
- **Never** silently fall back to cloud. `PREFER_ON_DEVICE` is unsafe as a privacy-default.
- Implement this after the local provider, privacy engine, and RAG contracts are stable. It is not part of the first implementation slice.

### 4.4. Add a Privacy Policy Engine

Three privacy modes with per-category rules:

| Category | Default Rule |
|---|---|
| Identity, health, and medical documents | `NEVER` cloud |
| Unknown classification or unresolved sensitivity | `NEVER` until deterministic evidence or a user override resolves the category |
| Financial and travel documents | `ASK` every operation; never remember category approval |
| General documents and notes | `ASK` every operation; no broad remembered approval |
| Movies, TV, books, and explicitly non-sensitive media | `ALLOWED` after opt-in; category approval may be remembered and revoked |

UI requirements:
- Show a badge on every AI result: **Local search**, **On-device AI**, or **Cloud AI**.
- For `ASK` scenarios, disclose: provider used, exact text/image being sent, why cloud is needed, whether choice is remembered, and a link to data terms.
- Never display “not used to train models” unless the configured service contract actually guarantees it.
- A low-confidence classification must never weaken a stricter lens or deterministic sensitivity signal.
- Manual classification immediately reevaluates the policy, but sensitive user-selected categories remain `NEVER`.

### 4.5. Instant Ingestion and Deferred Foreground Enrichment

**Critical:** Gemini Nano inference is allowed only while the app is in the foreground. Do **not** use WorkManager for Nano.

Product promise: **Save instantly. Organize automatically. Review anytime.**

In-app camera/import flow:

1. Take ownership of the media and show the review surface immediately.
2. Start OCR, vision, and heuristics opportunistically; populate suggestions when ready without disabling Save.
3. Let the user review or edit title, notes, category override, and smart spaces, or save immediately with the available evidence.
4. Persist as soon as Save is tapped and return to Vault without waiting for unfinished extraction, Nano, embeddings, or duplicate detection.
5. Signal the deferred queues after persistence.

Android share-target flow:

1. Copy shared media into private no-backup vault storage while the URI grant is valid.
2. Persist a minimal item and close the share target without OCR or Nano waiting.
3. Complete deterministic extraction and optional indexing later.
4. Run Nano only during a foreground application session.

Pipeline rules:

1. Track extraction, Nano enrichment, and indexing independently; derive one calm UI status from them.
2. Claim one item atomically before processing and allow only one Nano request at a time.
3. Cancel the active job when the application leaves the foreground.
4. Recover stale `RUNNING` claims, use bounded retry/backoff, and store stable non-sensitive error codes.
5. Pause bounded batches during severe thermal throttling or critically low battery.
6. Use WorkManager only for deterministic local tasks or explicitly authorized cloud enrichment, never Nano.
7. If Nano is ready and useful work exists, warm it once per process/model session before the first request; never warm on every launch.
8. Queue-driven warmup requires a foreground app plus acceptable battery and thermal state. A user-initiated Brain request may warm lazily in the foreground; neither path may delay save/navigation.

Enrichment states:

```kotlin
enum class EnrichmentState {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    SKIPPED_UNSUPPORTED,
    SKIPPED_PRIVACY
}
```

Operational fields: `enrichmentAttemptCount`, `enrichmentClaimedAt`,
`enrichmentLastAttemptAt`, and `enrichmentErrorCode`. Deterministic stages use
separate `extractionState` and `indexingState` values. Existing rows migrate as
already extracted/indexed, so upgrades never reprocess the entire vault.

Manual classification is stored separately as `userClassificationOverride`.
The effective routing value is computed, never duplicated:

```kotlin
val effectiveClassification = userClassificationOverride ?: aiClassification
```

Nano may suggest a different classification but must never silently replace a
user override. User corrections are structured feedback; the product must not
claim that they train a model until a deliberate personalization system exists.

Enrichment-complete notifications, if added, are optional and disabled by
default. Normal progress uses calm UI copy: **Saved**, **Organizing…**, **Ready**,
and **Needs review**.

### 4.6. Structured Output Fallback Chain

Because typed structured output is alpha and availability must be checked at runtime:

1. Typed structured output if `isStructuredOutputFeatureAvailable()`.
2. Constrained JSON prompt + validation/repair.
3. Existing deterministic heuristics.
4. Never overwrite reliable OCR/heuristic fields with a lower-confidence AI guess.

Typed output is an optional SDK adapter, not a domain or persistence contract:

```text
ML Kit annotated DTO
        ↓
validation + normalization
        ↓
Nemory domain enrichment result
        ↓
conservative merge
        ↓
Room entity
```

Room entities and domain metadata must not depend directly on ML Kit annotations or generated classes.

---

## 5. Feature Roadmap

### Phase 1 — Capability, Privacy & Real Nano Client

**Goal:** A production-ready on-device LLM client and a privacy engine that works everywhere.

| # | Feature | Description | Key Files |
|---|---|---|---|
| 1.1 | Capability manager | Runtime capability model via ML Kit status APIs. | `:core:ai/llm/AiCapabilityManager.kt` |
| 1.2 | `NanoPromptClient` | Real MLKit Prompt API client, renamed from `AicoreLlmClient`. | `:core:ai/llm/NanoPromptClient.kt`, `LlmModule.kt` |
| 1.3 | Privacy policy engine | Three privacy modes + per-category rules. | `:core:ai/llm/PrivacyEngine.kt` |
| 1.4 | Remove Pro-purchase scaffolding | Delete `purchasePro()` from Brain; make Brain universally accessible. | `:feature/brain/BrainChatViewModel.kt` |
| 1.5 | Prompt evaluation harness | Golden English/Arabic cases for each document category, provider-independent. | `:core:ai:llm/src/test/`, test fixtures |

#### Implementation progress — 2026-08-24

- [x] 1.1 Runtime capability manager backed by ML Kit feature status, base-model name, token limit, and download state.
- [x] 1.2 Real foreground-only `NanoPromptClient` with generation, streaming, graceful failure, and dependency injection.
- [x] 1.3 Privacy policy engine with `PRIVATE`, `ASK_BEFORE_CLOUD`, and `SMART_HYBRID` modes plus category rules.
- [x] 1.4 Brain purchase/paywall scaffolding and `AiTier` routing removed; deterministic RAG remains available everywhere.
- [x] 1.5 Provider-independent scoring harness plus 40 golden cases: English and Arabic coverage for every current classification.

Validation completed with the Gradle 8.14.5 wrapper: focused unit tests for LLM, privacy, billing, RAG, Brain, and the prompt evaluation corpus passed, and the app debug APK assembled successfully. Kotlin was moved to 2.2.21 for ML Kit Prompt API compatibility; Room was moved to 2.8.4 and Hilt to 2.57.2 for the matching KSP toolchain.

---

### Phase 2 — Instant Ingestion & Deferred Foreground Intelligence

**Goal:** Every captured document gets the best possible metadata without blocking the capture flow.

| # | Feature | Description | Key Files |
|---|---|---|---|
| 2.1 | Deferred foreground pipeline | Separate extraction/indexing states plus atomic `PENDING → RUNNING → COMPLETE/FAILED/SKIPPED` Nano claims. | `:core:common/model/`, `:core/database/VaultItemEntity.kt` |
| 2.1a | Split capture entry paths | Camera/import retains review; Android shares copy, persist, and close without analysis waiting. | `:feature:capture/CaptureViewModel.kt`, `ShareActivity.kt` |
| 2.1b | Foreground queue manager | One Nano request at a time, bounded batches, lifecycle cancellation, retry backoff, stale-claim recovery, thermal/battery checks. | `:feature:capture/ForegroundEnrichmentManager.kt` |
| 2.1c | Conditional Nano warmup | Warm once for claimed enrichment after device-policy gates, or lazily before the first foreground Brain inference. Warmup failure never blocks normal generation. | `:core:ai/llm/NanoRuntime.kt`, `NanoPromptClient.kt`, `MlKitNanoRuntime.kt`, `:feature:capture/ForegroundEnrichmentManager.kt` |
| 2.1d | Fail-closed privacy hardening | Treat `UNKNOWN`, unresolved sensitivity, and low-confidence document classifications as `NEVER` cloud until deterministic evidence or a user override resolves them. | `:core:ai/llm/PrivacyEngine.kt`, privacy policy tests |
| 2.2 | Category-aware enrichment | Generate module-specific titles, summaries, grounded metadata, and eligible proactive-action suggestions after heuristic save. | `:core:ai/llm/EnrichmentProfile.kt`, `CaptureEnrichmentPrompt.kt`, `CaptureEnrichmentResult.kt` |
| 2.3 | Structured metadata extraction | Complete typed per-document schemas, normalized amount/date projection, and an `IDENTITY_DOCUMENT` category with fail-closed privacy. | `:core:common/model/Classification.kt`, `:core:ai/heuristics/HeuristicExtractor.kt`, `:core:ai/llm/EnrichmentProfile.kt` |
| 2.4 | Multimodal enrichment | Send image + OCR text to Nano for confirmation and richer metadata when supported. | `:feature:capture/CaptureViewModel.kt` |
| 2.5 | Non-blocking duplicate detection | Persist first, then use embeddings for candidate discovery and conservative text/identifier corroboration; surface “Possible duplicate” without delaying or undoing save. | `:feature:capture/PostSaveDuplicateDetector.kt`, `ForegroundEnrichmentManager.kt`, `:core/database/` |

**Performance rule:** on low-end devices, skip LLM enrichment and rely on heuristics/TFLite. The user retains all core product capabilities; AI enhancement quality adapts to device capability.

#### Phase 2 implementation progress — 2026-08-24

- [x] 2.1 Persistent deferred foreground pipeline, Room `1 → 2 → 3` migrations, separate extraction/indexing states, guarded transitions, retryable/final failures, and conservative result merging.
- [x] 2.1a Android shares now take ownership and persist immediately; in-app capture still provides review and returns immediately after database save.
- [x] 2.1b Lifecycle-aware single-request manager with atomic claims, stale recovery, bounded retry/backoff, foreground cancellation, and thermal/battery gating.
- [x] 2.1c Foreground-safe conditional Nano warmup with once-per-process serialization, queue device-policy gates, lazy Brain coverage, cancellation propagation, and non-blocking failure behavior.
- [x] 2.1d Unknown, missing, low-confidence document, and unresolved-review sensitivity now fail closed; an authoritative user override reevaluates the selected category policy.
- [x] Manual classification override, effective routing, and calm `Saved / Organizing… / Ready / Needs review` status derivation.
- [x] 2.2 All 20 classifications now have module-specific enrichment profiles for titles, summaries, allowlisted grounded metadata, and eligible proactive actions. User category/title/summary edits remain authoritative; classification conflicts become `Needs review`; actions require an explicit user tap and system confirmation.
- [x] 2.3 Provider-independent typed metadata contracts now validate text, enum, non-negative integer, HTTP URL, ISO currency, canonical decimal, and strict date fields before merge. Locale-specific and Arabic-number amounts normalize without device-locale dependence; ambiguous/invalid new dates are rejected; valid expiry, due, travel, and warranty dates project into the indexed Room-backed date fields. `IDENTITY_DOCUMENT` has separate heuristic/prompt routing, typed subtypes, safe titles/summaries, expiry review actions, and fail-closed local-only privacy.
- [x] 2.4 Capability-gated multimodal enrichment now combines a bounded, downscaled in-memory image with OCR, labels, barcodes, classification, lens, and existing metadata in the foreground-only Nano request. PDFs, missing/invalid images, unsupported runtimes, generation failures, and invalid multimodal output fall back to a truthful OCR-only prompt; cancellation stops work instead of starting a retry. Images are never persisted as prompt payloads or sent to cloud by this path.
- [x] 2.5 Post-save duplicate detection now uses text embeddings only for candidate discovery, excludes the current item and stealth-boundary leaks, and requires exact normalized OCR, a stable identifier, a receipt identity tuple, or very high semantic plus lexical agreement before flagging. Visual similarity alone cannot create a match. Suggestions and confidence are committed atomically with indexing completion through Room migration `3 → 4`; save, retry, and embedding failures never delete, merge, or block the item. The detail screen shows a localized “Possible duplicate” review card linking to the earlier item, while an explicit edit clears the suggestion.

Validation completed with the Gradle 8.14.5 wrapper: 91 focused unit tests across
the common model and metadata validators, database migrations/mappers, heuristic
extraction, LLM contracts/profiles, prompt evaluation, proactive actions, and
capture/multimodal/duplicate flow passed with zero failures. The complete debug APK
was then assembled successfully.

---

### Phase 2.6 — UX & Trust Polish

**Goal:** Hide infrastructure complexity and make Nemory feel immediate, calm, private, and useful before expanding Brain.

| # | Feature | Description | Key Files |
|---|---|---|---|
| 2.6.1 | Instant-save interaction | Camera/import returns to Vault immediately after review and persistence; shares close immediately. Never block on OCR, Nano, embeddings, or duplicate checks. | `:feature:capture/`, `ShareActivity.kt` |
| 2.6.2 | Progressive document cards | Replace filenames with category-specific title, key fact, date, thumbnail, and subtle `Saved / Organizing… / Ready / Needs review` state as data becomes available. | `:feature:vault/` |
| 2.6.3 | Needs-review inbox | Batch uncertain categories, conflicting classifications, ambiguous dates, and possible duplicates into one-tap choices without interrupting capture. | New `:feature:vault/review/` |
| 2.6.4 | Tangible privacy and consent | Subtle tappable `On this device` / `Cloud assisted` badges and contextual, data-specific cloud consent; never show cloud options for `NEVER` items. | `:feature:vault/`, `:feature/settings/` |
| 2.6.5 | Friendly capability states | Use `Preparing Private AI` and quiet unsupported-device explanations only when relevant; downloads and setup never block normal use. | `:feature/settings/`, Brain/capture entry surfaces |
| 2.6.6 | Simple app shell | Use `Today | Vault | Brain` as top-level destinations with one prominent Capture action; categories remain filters/lenses, not destinations. Ship a useful Today shell before advanced insights. | `:app/navigation/`, `:feature:briefing/` or new `:feature:today/` |
| 2.6.7 | RTL and mixed-language QA | Test Arabic UI with English merchant/document content, Arabic queries over English documents, BiDi isolation, numerals, currencies, search, and truncation. | UI test suites and string resources |
| 2.6.8 | Modern Android baseline | Verify edge-to-edge/insets, predictive back, adaptive phone/tablet/foldable layouts, accessibility semantics, contrast, touch targets, and reduced-motion behavior. | `:app/`, shared Compose components |

UX rules:

- AI implementation terms such as AICore, embeddings, RAG, confidence thresholds, providers, and model versions belong only in advanced diagnostics.
- Normal settings stay focused on Privacy, Notifications, Language, Security, and Remove Ads.
- Internal privacy modes may remain in the policy engine, but the normal UI presents plain-language privacy choices rather than provider or model controls.
- Do not show a blocking “AI processing” modal. Status lives quietly on the item and in the review inbox.
- Privacy badges describe only the successful result: `On this device` for a local result and `Cloud assisted` only for a successful cloud result. A failed cloud attempt is a separate calm status and must never change successful-result provenance.
- The Today shell may ship early, but deterministic insights remain Phase 4 and must not be simulated with placeholder AI content.
- Make Today the default destination only when it reliably contains useful attention items, recent saves, or supported questions; until then, Vault remains the safer default.

#### Phase 2.6 implementation progress — 2026-08-24

- [x] Capture review now completes after Room persistence; reminder scheduling and foreground enrichment no longer hold the save screen open. Share ingestion remains immediate.
- [x] Vault cards progressively render a category-specific key fact and actionable date with localized `Saved / Organizing… / Ready / Needs review` states, thumbnail fallback, BiDi isolation, and combined accessibility semantics.
- [x] The Needs-review inbox batches possible duplicates, terminal enrichment failures, category uncertainty, and detail review. Accepting a suggestion preserves the user’s category authority and never deletes or silently merges an item.
- [x] Item detail now exposes a tappable, truthful `On this device` privacy receipt. Settings reports Private AI availability in product language and optional model preparation never blocks core features.
- [x] Top-level navigation is `Today | Vault | Brain` with one Capture action. Settings and review are secondary destinations rather than permanent tabs.
- [x] Onboarding no longer requests permissions. Camera permission is requested only for direct camera entry; gallery/share flows do not request it, and notification permission remains off until a reminder experience requests it contextually.
- [x] English/Arabic onboarding, review, card, privacy, navigation, and capability copy is present; mixed-direction titles, facts, and classifications use BiDi isolation and dates respect the active locale.
- [x] Edge-to-edge, predictive-back opt-in, RTL support, meaningful card/FAB semantics, and minimum Material touch targets are present in the implementation baseline.
- [ ] Before production rollout, complete physical TalkBack, contrast, reduced-motion, large-font, tablet, foldable, and Arabic/English device-matrix QA. Add the `Cloud assisted` receipt and data-specific consent sheet only when the Phase 5 cloud path actually exists; sensitive `NEVER` categories must not expose it.

Validation: 98 focused unit tests across the Phase 2 common, database, heuristic, LLM, capture, and Vault suites pass with zero failures or errors using the Gradle 8.14.5 wrapper. A fresh debug APK assembled successfully (`SHA-256 03A71487B37F4A2C3B9354772B9E205C11C820464888B9C244EB5F97F4ED9A7B`).

---

### Phase 3 — Reliable Personalized RAG Brain

**Goal:** The Brain chat becomes a true personal assistant over the vault.

| # | Feature | Description | Key Files |
|---|---|---|---|
| 3.1 | Token-budget manager | Keep single-turn on-device prompts under the ~4K-token limit. | `:core:ai/llm/TokenBudget.kt` |
| 3.2 | App-managed chat memory | Store history locally; summarize older turns; reconstruct each request as a single-turn prompt. | `:feature/brain/BrainChatViewModel.kt`, `:core:database/ChatMessageEntity.kt` |
| 3.3 | Streaming RAG answers | Use `RagEngine.queryStream()` with a real LLM while preserving deterministic results and source IDs. | `:core:ai/rag/RagEngine.kt`, `:feature/brain/BrainChatViewModel.kt` |
| 3.4 | Code-bound citations | Bind citations to retrieved item IDs in Kotlin; render visible tappable source cards and never trust model-generated IDs. | `:core:ai/rag/RagEngine.kt`, `:feature/brain/BrainChatScreen.kt` |
| 3.5 | Basic deterministic ToolRegistry | Implement `filterByMerchant`, `filterByDateRange`, `sumAmounts`, `findExpiringSoon`, and `findUpcomingTravel` in Kotlin. The LLM may explain results but never calculate authoritative totals. | `:core:ai/rag/DeterministicToolRegistry.kt`, repositories, `BrainChatViewModel.kt` |
| 3.6 | Natural-language search | Convert queries into validated tool/filter requests or use LLM re-rank; document text remains untrusted input. | `:feature:vault/VaultBrowserViewModel.kt`, `QueryIntentParser.kt` |
| 3.7 | Evidence-first answer UX | Show the computed value first, supporting rows/counts next, and expandable tappable vault sources; do not imitate a generic chatbot transcript for structured answers. | `:feature:brain/BrainChatScreen.kt` |
| 3.8 | Contextual suggested questions | Derive suggestions deterministically from categories and available metadata so empty Brain states teach only capabilities that can currently answer. | `:feature:brain/BrainChatViewModel.kt` |
| 3.9 | Guardrails | Medical/financial disclaimers, refusal when evidence is missing, no hallucinated facts, and confirmation before every write/action. | `:core:ai/rag/RagEngine.kt`, system prompts |

**Note:** On-device chat is single-turn. Multi-turn, function calling, PDF/audio/video inputs are not supported natively by the on-device path.

#### Phase 3 implementation progress — completed 2026-08-24

- [x] 3.1 A conservative provider-independent token budget caps complete on-device input prompts at 3,584 estimated tokens, reserves runtime headroom, handles Arabic/mixed text conservatively, and truncates without splitting Unicode surrogate pairs.
- [x] 3.2 Brain turns and their app-bound source IDs are stored in the encrypted Room database through migration `4 → 5`. A bounded extractive memory keeps recent turns authoritative, compresses older context, and reconstructs each Nano request as a single prompt; clearing Brain history does not affect vault items.
- [x] 3.3 Existing streaming RAG now receives the bounded local conversation context while retaining deterministic fallback when the LLM is unavailable.
- [x] 3.4 Database IDs are no longer placed in the model prompt or trusted from generated text. Model-written item/source citation patterns are removed, while visible source cards remain bound directly to the retrieved `VaultItem` objects in Kotlin and survive process recreation.
- [x] 3.5 A read-only bilingual deterministic tool registry now handles merchant/date filtering, receipt/invoice lookup, currency-safe totals, expiry, and upcoming travel over active non-stealth records. Totals are parsed and summed with `BigDecimal` in Kotlin and are never combined across currencies or delegated to the model.
- [x] 3.6 English/Arabic query wording is converted by a small fail-closed parser into validated intents before embeddings or generation. The same intents filter the Vault browser; unknown wording keeps the existing encrypted FTS/semantic fallback.
- [x] 3.7 Tool answers now carry a typed evidence contract and render the authoritative headline first, supporting facts/counts second, explanatory text next, and code-bound tappable source cards last. Evidence survives process recreation through the encrypted Room `5 → 6` migration.
- [x] 3.8 Suggested questions are derived only from categories and actionable dates actually present in the active vault, capped to four, and localized in English and Arabic. An empty vault advertises no unavailable capability.
- [x] 3.9 Vault record text and prior conversation are explicitly delimited as untrusted prompt data; missing evidence fails closed, model citation claims are ignored, and health/financial disclaimers are appended deterministically in Kotlin. Confirmation-required write tools remain deferred because this slice exposes no Brain writes.

Validation: all 150 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. Tests cover bilingual intent routing, currency separation, expiry/travel windows, both synchronous and streaming model bypass for authoritative totals, structured evidence persistence, and the Room migration. Room schema v6 was generated and a fresh debug APK assembled successfully (`SHA-256 A3E338D2A2907BF454FB8507A8C2018D5867A05A40BA752692E3FF95A6DA7651`).

---

### Phase 4 — Proactive “Today” Insights

**Goal:** The app surfaces useful cards without the user asking.

| # | Feature | Description | Key Files |
|---|---|---|---|
| 4.1 | Deterministic insight engine | Calculate expiring documents, duplicate captures, spending deltas, unwatched media, upcoming travel from metadata. | `:feature:briefing/MorningBriefingCard.kt` or new `:feature:today/` |
| 4.2 | LLM explanation layer | Use Nano only to write the explanation text; the trigger is deterministic code. | `:core:ai/llm/PromptEngine.kt` |
| 4.3 | Confirmed actions | Nothing is scheduled, deleted, shared, or modified without explicit user confirmation. | `:feature:today/TodayScreen.kt` |

Examples:
- “Your passport expires in 83 days.”
- “This warranty expires next month.”
- “Two captures look like duplicates.”
- “Your recurring bill increased.”

#### Phase 4 implementation progress — completed 2026-08-24

- [x] 4.1 The old three-day expiry-only Morning Briefing is now a typed, priority-ranked deterministic Today engine. It derives up to five calm cards for documents expiring within 90 days, persisted duplicate suggestions, explicitly recurring same-provider/same-currency charge increases, upcoming travel, and media left unfinished for at least seven days. Ordinary purchases, cross-currency records, completed media, expired dates, and unsupported metadata fail closed.
- [x] 4.2 Today publishes every deterministic card immediately and may then request one short explanation for the leading card from the foreground-only Nano provider. Generated prose cannot create or suppress a trigger, is capped to 240 characters, and is discarded if it invents a number, spans multiple lines, or claims that an action already happened. The Nano client now serializes text, image, streaming, Brain, enrichment, and Today generation globally through one request mutex.
- [x] 4.3 Today is localized in English and Arabic with locale-aware dates and plural forms. Insight cards only navigate to the underlying vault record for review; they never schedule, delete, share, or modify data. State-changing actions remain outside Today and continue to require an explicit user confirmation flow before they are added.

Validation: all 161 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. Phase 4 tests cover priority routing, today-boundary dates, duplicate grouping, recurring-charge/currency isolation, completed-media exclusion, generated-number/action-claim rejection, global Nano serialization, and the no-write Today invariant. A fresh debug APK assembled successfully (`SHA-256 698A4F8FFF26FEE0B53CF78349DD915097BE3AC8D1716E8E58592BD989516E8E`).

---

### Phase 5 — Optional Cloud & Advanced Modalities

**Goal:** Add explicitly authorized cloud help and richer modalities after the local assistant is proven.

| # | Feature | Description | Key Files |
|---|---|---|---|
| 5.1 | Cloud fallback client | Firebase AI Logic `ONLY_IN_CLOUD`, App Check, quotas, and explicit consent. The provider must sit behind a fail-closed runtime boundary; never use a provider mode that can silently fall back from device to cloud. | `:core:ai:llm/CloudAiRuntime.kt`, `HybridAiCoordinator.kt`, future `FirebaseCloudAiRuntime.kt` |
| 5.2 | Advanced tool expansion | Add bounded cross-document workflows only after the Phase 3 local tools are proven; execute authoritative calculations and every future write in Kotlin. | `:core:ai/rag/DeterministicToolRegistry.kt`, `QueryIntentParser.kt`, `:feature:brain/BrainSuggestionProvider.kt` |
| 5.3 | Cloud-assisted reasoning | Extend local retrieval and deterministic results only when cloud reasoning provides material value and contextual consent is granted. | `:feature:brain/BrainChatViewModel.kt` |
| 5.4 | Document translation | Translate non-sensitive text locally or via cloud opt-in. | `:feature/vault/ItemDetailScreen.kt` |
| 5.5 | Voice capture | Strictly on-device STT → local extraction → reviewed manual vault item creation. Never substitute the generic network-capable recognizer when the on-device service is unavailable. | `:feature:voice/`, `HomeScreen.kt`, capture review flow |

#### Phase 5 implementation progress — started 2026-08-24

- [x] 5.1 privacy/runtime foundation: `HybridAiCoordinator` always tries the existing Nano client first and is the only route to the optional cloud boundary. `PrivacyEngine` is re-evaluated immediately before every cloud call; private mode, sensitive categories, health items, unknown classifications, and unresolved sensitive-looking documents fail closed even if a caller passes consent. The default `DisabledCloudAiRuntime` cannot open a network connection.
- [x] Contextual consent contract: when an eligible local request cannot complete, Nemory returns a typed consent disclosure containing the operation, provider, exact item text, and whether category-level consent could ever be remembered. Consent is request data, not a global bypass. No image bytes, item IDs, hidden metadata, or unrelated vault context are admitted to the cloud request type.
- [x] 5.4 local-first translation UX: Item Detail can translate saved title/summary/OCR text to English or Arabic. It shows progress and successful-result provenance and displays the exact data in a scrollable one-time consent dialog before any eligible cloud attempt. A failed authorized cloud attempt is reported separately and never relabels a retained local result as cloud-assisted. Sensitive or unresolved items never expose a cloud action.
- [x] 5.5 privacy-correct voice capture: the former external `ACTION_RECOGNIZE_SPEECH` activity was removed. Nemory now checks API and service capability before asking for microphone permission, creates only `SpeechRecognizer.createOnDeviceSpeechRecognizer()` on supported API 31+ devices, sets its listener before starting, and cancels/destroys it with the composable lifecycle. Unsupported devices, denied permission, unavailable language models, timeouts, and recognizer errors receive calm typed-entry/retry paths; Nemory never silently substitutes the generic recognizer. A successful transcript is classified locally and enters the existing editable review screen as `SourceType.VOICE` before the user can save it. When the selected language is supported but its local model is missing, a user-initiated recovery action uses Android's on-device recognizer model-download API: API 34+ reports bounded progress/success/scheduled/failure states and retries capture only after success, while API 33 honestly reports a request-only scheduled state. Unsupported languages and API 31–32 never expose an unavailable preparation action.
- [x] 5.2 first advanced read-only tool slice: Brain now builds a nearest-trip briefing from related ticket/hotel records, compares the latest two recurring charges for the same normalized provider and currency, and lists unfinished movies, TV series, and books. The Kotlin registry performs every date, money, grouping, completion-status, and percentage decision; generated text is not used for calculations. Results use dedicated evidence kinds and source IDs bound to actual vault items, work without Nano or network access, and expose no state-changing operation. English/Arabic intent routing, contextual Brain suggestions, and Vault natural-language filtering share the same bounded intent model.
- [x] 5.3 cloud-assisted Brain boundary: deterministic tools still return immediately and local retrieval, ranking, calculations, source selection, and citation binding never leave the device. Only optional answer synthesis can cross the existing `HybridAiCoordinator`, after every retrieved record independently passes `PrivacyEngine`. One sensitive, unresolved, health, or private-mode source vetoes the complete multi-record request. When the verified cloud runtime is ready and Nano cannot answer, Brain shows the exact bounded prompt and provider in a one-time English/Arabic consent dialog, then retries the same assistant turn without duplicating the user message. A declined, unavailable, or failed cloud request keeps the local summary and source cards. On-device/cloud provenance is rendered on the answer and persisted in the encrypted Room history through migration 6→7; consent payloads are never persisted. The disabled runtime does not expose a dead consent action.
- [x] Revocable category-level opt-in: after reviewing an exact one-time cloud payload, users may optionally remember approval only for classifications that `PrivacyEngine` marks `ALLOWED_IF_OPTED_IN` (movies, TV series, books, and explicitly non-sensitive image categories). The typed `CloudConsentStore` rejects corrupt, removed, sensitive, financial, travel, health, identity, general-document, and unresolved values on both read and write. Brain and translation use remembered approval only in `SMART_HYBRID`; a mixed request still requires every source category to be independently approved and any `NEVER` source vetoes cloud. Preferences are device-only SharedPreferences excluded from Android backup/transfer, contain classification names rather than vault content, and can be revoked individually or all at once from the localized Settings screen.
- [x] First confirmed-write tool slice: Brain can pin an existing active item or set its secondary reminder from a bounded English/Arabic command grammar. Planning is deterministic Kotlin and performs no write; it resolves an exact title first, accepts only a unique partial match of at least three characters, rejects ambiguity, invalid or out-of-range dates, already-applied actions, and changed/hidden/archived items, then shows the exact item and action in a localized confirmation dialog. Execution occurs only after the user presses Confirm, revalidates the item snapshot, records the edit through `VaultRepository`, serializes the reminder through `UnifiedAlertManager`, and attempts to restore the prior item and alerts if scheduling fails. Android 13+ notification permission is requested contextually only after reminder confirmation; denial makes no write. LLM output can neither create nor execute these actions.
- [x] Confirmed media-completion expansion: English/Arabic commands can mark a classified movie or TV series as watched and a classified book as read. Category validation is repeated during planning and execution; questions such as “Have I watched Dune?” are not treated as writes, category mismatches and already-completed items fail closed, and the canonical `media_status` is written to both parsed metadata and user fields only after confirmation. Completion clears the associated reminder and cancels its alert; cancellation failure attempts to restore the prior record and alert schedule. The same localized preview, stale-snapshot check, and no-LLM-execution rules apply.
- [x] Reversible write expansion: Brain accepts bounded English/Arabic commands to unpin an active pinned item or clear an existing secondary reminder. Both actions remain read-only during planning, resolve only an exact or unique bounded title match, reject already-applied state, show the exact localized confirmation, and revalidate the item snapshot before execution. Clearing a reminder persists `null` and cancels the scheduled alert; cancellation failure attempts to restore both the original item and its alert schedule. Unpinning never touches alerts. Questions and malformed commands do not mutate data, and no LLM output can plan or execute either action.
- [x] Confirmed archive expansion: Brain accepts only bounded English/Arabic imperative archive commands for an existing active item. Planning remains read-only, exact-title-first, uniquely bounded, and fail-closed; conversational questions are not interpreted as writes. The localized confirmation names the exact target and explains that reminder settings are preserved. Execution revalidates the snapshot, records `isArchived`, `updatedAt`, and `userEditedAt` only after confirmation, and deliberately performs no alert operation, matching the vault’s existing reversible archive behavior. Archived items remain recoverable through the app’s archive UI; deletion is not exposed to Brain.
- [x] Confirmed archive-restore expansion: Brain accepts bounded English/Arabic restore or unarchive commands and searches only the archived collection, never falling back to an identically named active item. The one-shot archived repository query is decoy-aware, and both it and the archived observable exclude stealth records at the SQL layer. Planning remains read-only and fail-closed; execution requires the exact item to remain archived, visible, and unchanged before setting `isArchived = false` and recording user-edit timestamps. Restoring preserves reminder data and performs no alert operation, matching the existing vault UI behavior. Conversational questions do not trigger planning, and deletion remains unavailable to Brain.
- [ ] Live Firebase activation remains deliberately disabled until the `com.nemory.app` Firebase project is supplied and its Android app, production SHA-256 certificate, Firebase AI Logic API, App Check Play Integrity provider, server quotas/budgets, abuse monitoring, privacy disclosures, retention policy, and production model are configured and verified. Activation must use Firebase AI Logic `ONLY_IN_CLOUD`; the app must not use automatic hybrid fallback for vault data.
- [ ] Further confirmed-write tools and physical-device privacy/accessibility/notification/language-model QA remain subsequent Phase 5 slices.

Validation: all 170 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. Nine focused coordinator tests cover local-first routing, exact-text disclosure, explicit consent, sensitive/unknown/private-mode refusal, disabled-runtime behavior, cloud-attempt provenance, and invalid disclosures. A fresh debug APK assembled successfully (`SHA-256 D1ECCBAF4ED226148F70D68A6AB06F5D1C334DC31CD4D514D1D6E5236B8EFF21`).

Voice validation: all 177 debug unit tests now pass with zero failures, errors, or skips. Seven new policy tests cover permission timing, the API 31 boundary, absent-service refusal, ready-state routing, timeout handling, and unavailable-language handling. A fresh debug APK assembled successfully (`SHA-256 73C092062E93DDD84597F1836EE612C6650C3DEF43BEA93D3553BBE2ADECC317`). Physical-device microphone, installed-language, rotation, TalkBack, and interruption testing remains required before release.

Advanced-tool validation: all 187 debug unit tests across the complete multi-module project pass with zero failures or errors using the Gradle 8.14.5 wrapper. New tests cover English/Arabic routing, conservative trip grouping, same-provider/same-currency recurring comparisons, decrease/cross-currency refusal, completed-media exclusion, evidence binding, contextual suggestions, and the no-write invariant. The Vault browser also handles the expanded sealed intent set consistently. A fresh debug APK assembled successfully (`SHA-256 72D124DDE388EF036B5280B016229B518BB90E81746986CEC824CBFC2CA3E05C`).

Cloud-assisted Brain validation: all 196 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. New coverage verifies multi-record sensitive veto, exact disclosed/cloud prompt equality, no consent action for an unconfigured runtime, explicit one-time retry on the same conversation turn, local fallback preservation, code-bound sources, citation stripping, persisted provenance, and the Room 6→7 migration. A fresh debug APK assembled successfully (`SHA-256 D71F9617F2ABC61623B55BBBDD1715E01A4114FA13A5446404459335C91DC7E1`). Live network behavior remains intentionally untested because `DisabledCloudAiRuntime` is still the production binding.

Remembered-consent validation: all 200 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. New policy tests verify safe persisted-value loading, refusal to remember per-request or sensitive categories, individual/global revocation, disclosure category binding, and consent skipping only for an approved category in `SMART_HYBRID`. A fresh debug APK assembled successfully (`SHA-256 12CD7849D401FDADCC74950D40E9D02C9D578DF396452A832A7949657B11EC43`). Live cloud traffic remains impossible in this build.

Confirmed-write validation: all 212 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. Twelve new tests verify the no-write preview invariant, explicit confirm and cancel paths, ordinary-query isolation, exact and ambiguous title resolution, English/Arabic reminder parsing, invalid-date refusal, stale-snapshot rejection, pin persistence, reminder scheduling, and rollback after scheduling failure. A fresh debug APK assembled successfully (`SHA-256 A08F7C28CBE0F58032CBB0658E851827FF12D39826DBD476FACDC06198DBB6E3`). Physical-device notification permission, alarm delivery, rotation, process-death, RTL, and TalkBack testing remains required before release.

Media-completion validation: all 219 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. Seven new tests verify question/command separation, no-write planning, Arabic book routing, movie/book category mismatch refusal, already-completed refusal, canonical dual-field persistence with reminder clearing, and rollback plus alert restoration after cancellation failure. A fresh debug APK assembled successfully (`SHA-256 C0FD77A96D9845633DABB51A60C8140D5D49A67A527B395ADBA94B316C848A12`). Physical-device media-card refresh, reminder cancellation, RTL, rotation, and TalkBack testing remains required before release.

SDK and provenance validation: all 220 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. ML Kit Prompt is pinned to `1.0.0-beta4`; Play Billing is migrated to `9.1.0`, including its `QueryProductDetailsResult` callback API. A new Item Detail regression test verifies that a failed cloud translation attempt reports its error separately and cannot relabel a retained on-device result as cloud-assisted. A fresh debug APK assembled successfully (`SHA-256 D40436A3F591AA9B4FC807EE52D54D5FE3684962B8E08DC0D3BF3A111CF20D04`). Physical Nano v4 and Play Billing sandbox validation remain part of the blocking production release gate.

Voice-model preparation validation: all 225 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. Five new policy tests verify that unsupported and merely unavailable languages remain distinct, API 31–32 do not advertise preparation, API 33 uses request-only behavior, API 34+ enables observable progress, and only a downloadable missing model exposes the recovery action. A fresh debug APK assembled successfully (`SHA-256 6C5CC9D377D18731984DA1FDC67059967E4A271F6608610E3976E468109F3B6E`). Physical English/Arabic model-download approval, progress, scheduled completion, retry, rotation, and interruption testing remains part of the blocking production release gate.

Reversible-write validation: all 232 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. Seven new tests verify English unpin planning, Arabic clear-reminder planning, no-write previews, rejection of already-applied state, confirmed unpin persistence without alert changes, confirmed reminder clearing with alert cancellation, and item-plus-alert restoration after cancellation failure. A fresh debug APK assembled successfully (`SHA-256 2634A6420264DF2F8B2610D1FCF056D924BF51489919D83ACACD0DF8460B1D51`). Physical confirmation-dialog, vault refresh, alert cancellation/restoration, RTL, rotation, process-death, and TalkBack testing remains part of the blocking production release gate.

Confirmed-archive validation: all 236 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. Four new tests verify English and Arabic no-write planning, rejection of conversational archive questions, and confirmed persistence that preserves reminder data without scheduling or cancelling alerts. A fresh debug APK assembled successfully (`SHA-256 BADDCFEB824596F8E40E27CF7F40E55BA994670C8DBC41BDE732DE3D744486C0`). Physical confirmation-dialog, archived-list recovery, reminder delivery while archived, RTL, rotation, process-death, and TalkBack testing remains part of the blocking production release gate.

Archive-restore validation: all 242 debug unit tests across the complete multi-module project pass with zero failures, errors, or skips using the Gradle 8.14.5 wrapper. Six new tests verify English and Arabic archived-only planning, refusal to fall back to active items, rejection of conversational restore questions, confirmed reminder-preserving restoration without alert operations, and stale archive-state rejection before writing. Room query generation, focused database/Brain tests, and full debug assembly pass. A fresh debug APK assembled successfully (`SHA-256 E3699E244B18D06733920D7C91B7A5351B6CE9E9A396CE79B8520D38D1A8A3FD`). Physical archived-list refresh, same-title active/archived behavior, reminder delivery after restoration, RTL, rotation, process-death, and TalkBack testing remains part of the blocking production release gate.

---

### Phase 6 — Privacy-safe monetization

**Goal:** Add monetization only after product-market fit and retention are proven. The launch is free and ad-free; ads and the Remove Ads offer are introduced later. Every lens, Brain capability, capture path, and AI provider remains available without payment.

**Product decision:** offer a simple Remove Ads purchase that grants no extra features. The first implementation uses one Play Console non-consumable one-time product named `remove_ads` (lifetime). Later, add monthly and annual subscriptions alongside the lifetime option so users can choose. Display only localized Play-provided prices; never hard-code a price. A recurring subscription must not silently replace an existing lifetime entitlement.

| # | Feature | Implementation contract | Key files | Status |
|---|---|---|---|---|
| 6.1 | Entitlement foundation | Replace monetization `Tier.PRO` language with explicit `AD_SUPPORTED` / `AD_FREE` state. Treat local preferences as a cache, preserve the last verified state during transient Play outages, and accept entitlement only for a `PURCHASED` transaction containing the exact `remove_ads` product ID. | `:core:billing/FeatureGate.kt` | Implemented; sandbox gate remains |
| 6.2 | Billing lifecycle | First implementation uses Play Billing `INAPP` for the one-time `remove_ads` product. Later extend to `SUBS` for monthly/annual options while preserving lifetime entitlements. Await connection before querying; query after connection and foreground resume; handle pending, purchased, cancelled, refunded/revoked, unavailable, reconnect, account-change, and reinstall flows. Grant no benefit for `PENDING`. Locally validate product/state, acknowledge completed purchases, and define the production backend-verification decision before release. | `:core:billing/BillingManager.kt` | One-time INAPP implemented; subscriptions staged |
| 6.3 | Purchase UX | Add a localized Settings row and purchase sheet showing Play-provided product details, progress, pending, success, already-owned, unavailable, and retry states. Removing ads must update visible surfaces immediately without an app restart. | `:feature:settings/` | In progress: localized offer/price/purchase/restore UI implemented |
| 6.4 | Consent controller | Integrate Google UMP. Refresh consent information at every launch, show a form when required, expose the required privacy-options entry point, and permit SDK initialization/ad loading only after the controller reports that ads may be requested. Unknown, failed, or interrupted consent state fails closed to no ad request. ATT is not part of the Android plan. | `:core:billing/AdConsentController.kt`, `:app/MainActivity.kt`, `:feature:settings/` | Not started |
| 6.5 | Ad boundary | Add a single app-owned `AdManager` that accepts no vault item, title, classification, lens, query, OCR text, source URI, user ID, content URL, keyword, neighboring-content URL, or custom-targeting input. Do not log ad payloads alongside vault telemetry. Test devices and test ad-unit IDs are mandatory outside signed production builds. | `:core:billing/AdManager.kt` | Not started |
| 6.6 | Launch placements | Ship adaptive banners only on the generic Vault Browser surface, after consent and only for `AD_SUPPORTED`. No banner when the surface is empty or in a sensitive/stealth session. No interstitial, rewarded, app-open, native-content, timed, capture-completion, or action-triggered ads in the first release. | `:feature:vault/` | Not started |
| 6.7 | Store/privacy operations | Configure the exact `com.nemory.app` product, license testers, internal track, AdMob app/ad units, UMP messages, Play Data Safety answers, privacy policy, developer website, and verified `app-ads.txt`. Review applicable regional disclosures before production; SDK integration alone is not a legal-compliance claim. | Play Console, AdMob, developer website | External gate |
| 6.8 | Release validation | Unit-test state reduction and data-boundary policies; then test Play approval/decline/pending-complete/pending-cancel/already-owned/refund-revoke/reinstall/offline/account-change flows, UMP required/not-required/error/privacy-options flows, test ads, rotation/process death, accessibility, and Arabic RTL. | multi-module tests + physical devices | Not started |

**Ad placement and frequency rules:**
- Never show ads in Brain, Today, capture/import/share, review, item detail, reminder/notification, Settings security/privacy, stealth/decoy, health, identity, financial, or cloud-consent experiences.
- Never show an ad as a response to saving, classifying, editing, archiving, restoring, completing media, clearing a reminder, or another user action.
- Version 1 uses generic adaptive banners only. Interstitial, rewarded, app-open, and native-content formats require a later product/privacy review and are not silently enabled by remote configuration.
- Never pass vault-derived context or stable app user identifiers to an ad request. UMP consent and non-personalized serving are privacy controls, not permission to disclose vault context.
- If consent, SDK initialization, network, or inventory is unavailable, collapse the placement without an error dialog or layout-blocking placeholder.

---

### Phase 6 Cost Rule: lean launch first, monetize after retention

Before monetization is enabled, all direct cash costs should be minimized. Money is spent only on unavoidable fees and one-time validation; time is the real investment.

| Tier | Cash outlay | What it covers |
|---|---|---|
| **Bare minimum** | ~$25–$50 | Google Play Developer account ($25), free Firebase Spark plan, emulator/cloud-AI dev, free privacy policy template. |
| **Recommended** | ~$100–$300 | Play account + Firebase Test Lab credits + used Pixel 9 or device rental + domain. |
| **Comfortable** | ~$500–$800 | Play account + owned Pixel 9/10 + device matrix testing + privacy-policy review. |

Cost-control rules:
- The public launch is free and ad-free.
- Ads and Remove Ads are added only after product-market fit is proven.
- On-device AI is the default; cloud AI is opt-in and disabled until a Firebase project is configured.
- Set a hard monthly cloud budget cap (e.g., $20–$50) if cloud testing is enabled.
- Do not buy a Pixel 9/10 until Nano/Brain testing becomes the blocking gate; use the emulator and Firebase Test Lab for earlier phases.

---

## 6. Technical Implementation Notes

### 6.1. Dependencies

```toml
[versions]
mlkit-genai-prompt = "1.0.0-beta4"   # Nano v4 compatibility fixes; verify on physical supported devices
firebase-ai = "17.15.0"              # add only with Phase 5 cloud fallback
billing = "9.1.0"
ads = "25.4.0"                       # add only during Phase 6 after v25 migration review

[libraries]
mlkit-genai-prompt = { module = "com.google.mlkit:genai-prompt", version.ref = "mlkit-genai-prompt" }
firebase-ai = { module = "com.google.firebase:firebase-ai", version.ref = "firebase-ai" }
play-billing = { module = "com.android.billingclient:billing-ktx", version.ref = "billing" }
play-services-ads = { module = "com.google.android.gms:play-services-ads", version.ref = "ads" }
```

### 6.2. Key Files to Modify

| File | Change |
|---|---|
| `:core:ai/llm/AicoreLlmClient.kt` | Rename to `NanoPromptClient.kt`; implement real ML Kit Prompt API client. |
| `:core:ai/llm/LlmModule.kt` | Provide `NanoPromptClient` by default; keep cloud client as opt-in binding. |
| `:core:ai/llm/AiCapabilityManager.kt` | Replace package/RAM feature routing with `AiDeviceCapabilities` based on official ML Kit status. |
| `:core:ai/llm/NanoRuntime.kt` | Add a testable conditional `warmup()` boundary; implementation remains isolated from UI and persistence. |
| `:core:ai/llm/PrivacyEngine.kt` | New: privacy modes and category rules. |
| `:core:ai/llm/PromptEngine.kt` | New: reusable prompt builder with JSON schema + validation. |
| `:core:ai/llm/TokenBudget.kt` | New: token budget manager for single-turn on-device inference. |
| `:core:ai/rag/DeterministicToolRegistry.kt` | Execute evidence-bound local tools and authoritative Kotlin calculations without model-generated writes. |
| `:core:database/VaultItemEntity.kt` | Track extraction/indexing states, classification override, enrichment claims, retries, and stable errors. |
| `:core:database/ChatMessageEntity.kt` | New: persist Brain chat history. |
| `:core:billing/FeatureGate.kt` | Remove brain/lens gating; add ad-removal entitlement check. |
| `:core:billing/AdManager.kt` | New: ad loading/showing wrapper with privacy rules. |
| `:feature:capture/CaptureViewModel.kt` | Save reviewed captures without waiting; signal foreground enrichment and add duplicate detection later. |
| `:feature:capture/ShareActivity.kt` | Own shared media, persist a minimal item, and close without analysis waiting. |
| `:feature:capture/ForegroundEnrichmentManager.kt` | Process a serialized bounded queue while the app is foregrounded. |
| `:feature/brain/BrainChatViewModel.kt` | Remove `purchasePro()`; add chat memory and token budgeting. |
| `:feature/vault/VaultBrowserViewModel.kt` | Add natural-language query path. |
| `:feature:settings/SettingsScreen.kt` | Keep normal settings simple; provide contextual privacy controls and place model/provider details under Advanced diagnostics. |
| `:feature:vault/review/` | New: calm one-tap queue for uncertain classifications, dates, and possible duplicates. |
| `:feature/brain/BrainChatScreen.kt` | Render computed answers, supporting rows, and code-bound tappable source cards. |

---

## 7. Privacy & Safety Guardrails

- **On-device default:** Gemini Nano via ML Kit runs locally unless the user explicitly enables cloud.
- **Opt-in cloud:** Cloud processing requires explicit consent and a clear warning per request or category.
- **Data minimization:** For cloud, send retrieved text snippets only — never original passport/prescription images.
- **Category rules:** Identity/medical/unknown = never cloud; financial/travel/general documents = ask every operation without remembered category approval; explicitly non-sensitive media may remember revocable opt-in.
- **Fail closed:** `UNKNOWN`, unresolved sensitivity, and low-confidence document classification remain local-only until deterministic evidence or an authoritative user category resolves them.
- **Result badges:** Every AI result shows **Local search**, **On-device AI**, or **Cloud AI**.
- **Tangible trust:** User-facing badges use **On this device** and **Cloud assisted** for successful results only. Failed cloud attempts are separate status messages and never rewrite result provenance; technical provider/model details are available on demand.
- **Medical/financial disclaimers:** Hard-coded in system prompts and UI.
- **Refusal training:** LLM must say “I don’t have that information in your vault” when context is missing.
- **Ad privacy:** Ads never see document content, titles, queries, or lens metadata.

---

## 8. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| AICore unavailable on most devices | Maintain fast heuristic/TFLite path; offer opt-in cloud fallback; never block feature access. |
| Nano foreground-only limitation | Use the process lifecycle, persistent atomic claims, cancellation, and stale-claim recovery; never invoke Nano from WorkManager. |
| LLM latency hurts capture UX | Persist first and run deferred foreground enrichment; the capture/share completion path never waits for Nano. |
| LLM hallucinates categories/metadata | Validate against OCR/heuristics; use JSON schema + confidence scores; let user edit; never overwrite reliable data. |
| Cloud costs | Strict quotas, Firebase App Check, paid project; cloud is opt-in only. |
| Ads harm premium feel | Limit to generic surfaces; never inside sensitive screens; offer cheap Remove Ads. |
| Storage/battery drain from Nano | Download model lazily; batch jobs; respect battery saver. |
| Token limit on device | Build token-budget manager; summarize chat history; retrieve only relevant vault chunks. |
| Single-turn limitation | Reconstruct prompts with summarized context + retrieved items. |
| Prompt injection | Treat document text as untrusted; execute tools in Kotlin; require confirmation for writes. |
| Sensitive item misclassified as unknown | Fail closed for unknown/unresolved sensitivity; never let low confidence weaken a stricter deterministic or lens policy. |
| Warmup wastes memory or battery | Warm only for pending work or an opened Brain session, once per process/model session, with foreground, thermal, and battery gates. |
| AI complexity leaks into UX | Expose outcomes and trust receipts, keep diagnostics advanced, and centralize uncertainty in a Needs-review inbox. |
| RTL and mixed-language defects | Test Arabic/English BiDi, merchant names, numerals, currency, search, truncation, TalkBack, and mirrored navigation explicitly. |

---

## 9. Revised Implementation Order

1. **Completed foundation: capability/privacy layers, real Nano client, evaluation harness, instant ingestion, deferred foreground claims, category-aware enrichment, conditional warmup, fail-closed privacy, identity-document routing, typed metadata normalization/projection, and capability-gated multimodal confirmation.**
2. **Completed Phase 2.5 post-save duplicate detection.**
3. **Completed Phase 2.6 core UX/trust polish: progressive cards, review inbox, on-device trust receipts, simple navigation, contextual permissions, RTL, and Android behavior; retain physical device-matrix QA as a release gate.**
4. **Completed Phase 3 hardened RAG with token budgeting, app-managed memory, deterministic local tools, evidence-first answers, and code-bound source cards.**
5. **Completed Phase 4 deterministic Today insight engine, optional bounded Nano explanations, localized cards, and read-only review actions.**
6. **Phase 5 in progress: completed fail-closed cloud boundaries, local-first translation, private voice capture, advanced read-only tools, consent-gated Brain orchestration, revocable category approvals, and confirmed pin/unpin/archive/restore/reminder/clear-reminder/media-completion writes; live Firebase activation and further writes remain staged.**
7. **Launch free and ad-free; add privacy-safe advertising + Remove Ads purchase only after product-market fit and retention are proven.**

> **Note:** The billing/ad framework plumbing should be scaffolded earlier than the final milestone so the monetization flow can be tested, but actual ad placements and the Remove Ads offer come last.

### Blocking production release-validation gate

Do not submit the production AAB until a signed release build, Play Billing sandbox purchase/restore/cancel/pending flows, Play pre-launch report, and physical-device matrix all pass. The matrix must cover representative low-end API 29, API 31 on-device voice capability/unavailability, API 36, supported Nano generations including v4, Arabic RTL and mixed-language content, large font, TalkBack, contrast, reduced motion, phone/tablet/foldable layouts, rotation, process death, notification permission and delivery, offline/bad-network behavior, thermal pressure, and low battery. Unit tests and debug APK assembly are necessary evidence but do not satisfy this gate.

### Phase 1 definition of done

- Brain and every lens remain usable without Nano, network access, or a subscription.
- A device without Prompt API support never sees an AICore error; it receives the deterministic local-search experience.
- Capability and privacy policy decisions are covered by unit tests and contain no document content in logs.
- The Nano provider is never invoked from WorkManager or while the application is backgrounded.
- Provider failures fall back to the existing deterministic RAG answer without losing retrieved sources.

---

## 10. Summary

VaultBrain is already architected as a personalized, on-device RAG system. The corrected roadmap focuses on:

1. **Capability-based model detection** instead of version selection.
2. **Direct ML Kit Prompt API** for on-device Nano; Firebase AI Logic only for cloud.
3. **Instant ingestion and foreground-only enrichment** with separate pipeline states, atomic claims, and calm derived UX status.
4. **Single-turn on-device chat** with app-managed memory and token budgeting.
5. **Contextual cloud consent** with per-category privacy rules.
6. **Fail-closed unknown sensitivity** plus conditional, non-blocking Nano warmup.
7. **Progressive, low-noise UX** with a review inbox, tangible privacy receipts, simple navigation, and Arabic/English quality.
8. **Deterministic Today insights** powered by rules, explained by LLM.
9. **Evidence-first Brain answers, code-bound citations, and Kotlin tools** for safe cross-document reasoning.
10. **Users-first monetization:** free, ad-free public launch; add privacy-safe ads and Remove Ads (lifetime + optional subscriptions) only after retention is proven.
11. **Lean launch cost balance:** keep cash spending minimal until product-market fit; time is the primary investment.

This plan respects all stated constraints and gives the next AI agent a concrete, corrected, buildable roadmap.
