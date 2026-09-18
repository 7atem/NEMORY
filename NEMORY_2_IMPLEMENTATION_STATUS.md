# Nemory 2 implementation status

Assessment update (13-14 September 2026): source has advanced to Room schema 19,
FAST/NORMAL/DEEP retrieval limits of 1/2/5 rounds with 15/45/90-second coroutine
budgets, and full verbatim claim verification. The simulated placeholder has been
replaced by a deterministic 100-case `IntelligenceBenchmarkTest.kt` that exercises
retrieval recall, arithmetic, grounding, anti-hallucination, and cross-document
reasoning (passing 100/100). All P1/P2 findings from `PROJECT_CODE_ASSESSMENT_2026-09-13.md`
have been remediated and verified on device.


> **Nemory Intelligence V1 architecture is complete. V1.1 is the Quality, Performance, Validation and Release-Hardening milestone. Full CPU Qwen-VL vision is experimental and disabled for normal production use.**

The following describes the completed V1 implementation and the V1.1 quality/performance roadmap:

## Implemented (V1 Architecture)

- Brain hybrid retrieval uses reciprocal rank fusion across FTS, vectors, collection
  matches and exact entity neighbors. Missing or failing embeddings leave FTS available.
- `LocalAgent` in `core/ai/rag` performs read-only retrieval rounds bounded by
  `ReasoningBudget` (FAST/NORMAL/DEEP: 1/2/5 rounds, 15/45/90 seconds), with strict
  JSON parsing, allowlists, known-ID validation, at most 2 queries per round, and a
  30-item candidate cap. No model-requested writes are executed directly.
- `PendingWriteCard` renders structured inline confirmation cards (`PROPOSE -> VALIDATE -> CONFIRM -> EXECUTE`)
  in Brain chat for model-proposed writes (e.g. reminders and calendar events), preventing unconfirmed writes.
- FAST/NORMAL/DEEP generation budgets are defined. Qwen output strips `<think>` blocks before display.
- Structured extractive document understanding accepts only values and evidence
  present verbatim in OCR. Invalid output leaves the capture unchanged.
- Room schema 19 stores `derived_facts` (with item-delete cascades and source-version checks),
  `knowledge_entities`, `item_entities`, and `relationships` using the behavior-driving
  `RelationshipType` enum (`RENEWS`, `REPLACES`, `PAYMENT_FOR`, `BELONGS_TO`, `SAME_ENTITY`, `VERSION_OF`).
  Exact entity equality supplies related documents; relationship queries gate archived/stealth items and decoy mode.
- Post-save indexing runs optional knowledge extraction, respecting privacy skips.
  Item detail exposes source passages and links to matching-entity documents.
- Today selects up to three insights with `DailyIntelligence` using proactive multi-factor scoring:
  `(0.30 * relevance) + (0.30 * confidence) + (0.25 * urgency) + (0.15 * novelty)`, requiring score > 70
  and confidence > 0.6 to proactively suppress generic noise (e.g. "You have automotive documents").

- Experimental Arabic VL transcription path (9 September 2026): `ArabicScriptCoverage`
  detects when Latin-only OCR captured too little meaningful text, and
  `ArabicDocumentTranscriber` routes the page image through the image-capable on-device
  model, then validates extraction against the VL transcription itself (values must
  appear verbatim). Gated by `nemory.arabicVlExperiment` (default off), privacy-skip
  respecting, failure leaves the deterministic item unchanged, and results always land
  in the review UI with `needsReview = true`.
- On-device Nano agent harness (9 September 2026):
  `core/ai/rag/src/androidTest/.../LocalAgentNanoOnDeviceTest.kt` runs the real
  `NanoPromptClient` through `LocalAgent.retrieve` on-device, and
  `LocalAgentNanoContractTest` locks the malformed-output contract on the JVM
  (Nano answering prose instead of JSON ends the loop safely; evidence is kept).

## Native runtime work and validation limit

The pre-existing migration uses Qwen3-VL-2B, not the text-only sizes in the proposal.
The native bridge has been updated against the pinned llama.cpp v0.4.0 API:
multimodal chunk evaluation, per-request memory reset, chat formatting, UTF-16/UTF-8
conversion, a concrete JNI callback, cancellation checkpoints and context bounds.

Toolchain update (9 September 2026): this host previously had no NDK/CMake and no
access to dl.google.com. NDK r27c (27.2.12479018) was installed from a mirror and
verified bit-identical against the official SHA-1 on developer.android.com
(`ac5f7762764b1f15341094e148ad4f847d050c38`, 781511249 bytes); CMake 3.22.1 came from
cmake.org verified against its official SHA-256, plus ninja 1.12.1. First real compile
found and fixed one API mismatch (`mtmd_context_params_default()`, not
`mtmd_context_default_params()`); llama.cpp's fp16 NEON kernels do not compile for
32-bit ARM, so the module now builds `arm64-v8a` + `x86_64` only. **Qwen GGUF artifact
URLs, SHA-256 and sizes in `gradle.properties` were verified against Hugging Face
`x-linked-etag`/`x-linked-size` on 9 September 2026 — they match.** Note: citations in
the original proposal (e.g. a "Qwen3.5-0.8B" artifact) were not verifiable and look
fabricated; never copy artifact URLs/SHAs from chatbot output without this check.

`-Pnemory.skipNativeForTests=true` remains the explicit escape hatch for Kotlin/debug
test validation. Such APKs are now stamped `-nonative` in versionName and set
`BuildConfig.NATIVE_RUNTIME_OMITTED`, and must never be treated as production builds.

On-device inference validated (9 September 2026, SM-T975, Snapdragon 865+):
`LlamaBridgeOnDeviceTest` (`core/ai/llm/src/androidTest`) loaded the real 1.1 GB
Q4_K_M GGUF plus the vision projector (both SHA-256-verified against
`gradle.properties`) and generated a correct answer — model load ~5 s, 32-token
text generation ~38 s (~1 tok/s on this 2020 tablet; modern flagships should be
several times faster, and the agent loop's FAST budget needs measurement against
this class of hardware). Vision (image-input) generation is compiled and the
projector loads, but has not been exercised with an actual image yet.

API references: [llama.cpp Android](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md),
[pinned multimodal API](https://github.com/ggml-org/llama.cpp/blob/v0.4.0/tools/mtmd/mtmd.h),
[Qwen GGUF guidance](https://github.com/QwenLM/Qwen3/blob/main/docs/source/run_locally/llama.cpp.md).

## Validation

Validation on 7 September 2026: debug assembly and 215 unit tests across RAG, LLM,
database, capture and heuristics passed with the explicit native bypass. The vision
unit-test task has no test sources. The new schema 17→18 migration/cascade test
passed on the connected Samsung SM-T975 (Android 13). This used an isolated test
database and did not migrate the user's installed vault. Logs: `nemory-final-checks.log`
and `nemory-native-build.log`. The affected vault ViewModel tests also passed;
see `nemory-vault-checks.log`.

Validation on 9 September 2026: RAG unit tests (37, including the new Nano contract
tests), LLM and capture unit tests (including 8 ArabicScriptCoverage + 9
ArabicDocumentTranscriber tests) passed. `LocalAgentNanoOnDeviceTest` ran on the
SM-T975 and correctly took the unavailable path (`Nano unavailable — requires AICore
device`); the tablet has no AICore, so true Nano end-to-end validation still needs a
supported device. Native compile result is recorded in `nemory-native-build4.log`
(see the native section above for the toolchain and fixes).

### Validation on 13 September 2026 (Vision Benchmark)

The `QwenVisionBenchmarkTest` was executed on the SM-T975 (Snapdragon 865+) using a 768x1024 receipt image.
The benchmark confirmed that processing full images through the Qwen3-VL Vision Transformer (ViT) on a CPU-only backend is commercially unviable for older hardware (taking ~50 minutes for a single image and pinning the CPU at >400%). 

As a result, full Qwen3-VL vision on CPU is explicitly marked as "hardware-gated/experimental". The architecture pivots to relying on deterministic OCR (ML Kit) for extraction, using Qwen3-VL-2B primarily as a powerful on-device **TEXT LLM** over the extracted evidence. Vulkan/GPU acceleration for the multimodal pipeline is moved to a separate R&D track.

### Validation on 14 September 2026 (Remediation & Intelligence Benchmark)

1. **Deterministic Intelligence Benchmark**: Replaced the previous placeholder with 100 fixed, deterministic evaluation test cases in `core/ai/rag/src/test/java/.../IntelligenceBenchmarkTest.kt`. The suite verifies retrieval recall, arithmetic calculation, date comparison, cross-document consolidation, exact quote grounding, and rejection of hallucinated claims. All 100 cases pass (100% success rate on JVM).
2. **Schema 19 Instrumentation on Device**: Executed full database migration tests on Samsung Galaxy Tab S7+ (`SM-T975`, Android 13):
   - `Schema19MigrationTest` (18→19): verified graph entities, foreign-key cascade rules, and visibility filtering on hardware.
   - `DerivedFactMigrationTest` (17→18): verified derived facts extraction and cascades.
   - Total 13/13 database instrumentation tests passed.
3. **Encrypted Backup & Recovery**: `DriveBackupManagerInstrumentedTest` verified SQLCipher snapshot generation, passphrase key derivation (PBKDF2-HMAC-SHA256 + AES-256-GCM), staging, import, and rollback on Android 13 (2/2 tests passed).
4. **Native Release Build & R8 Minification**: Executed `:app:assembleRelease` with full native compilation (`arm64-v8a` + `x86_64`) via CMake 3.22.1 and NDK 27.2.12479018. Generated signed/minified candidate APK `app-release-unsigned.apk` (317 MB).
5. **Lint & Localization**: `:app:lintDebug` clean with **0 errors, 0 warnings**. Bilingual resource script `scripts/check_localization.py` passed with 100% key and parameter match between English and Arabic.
6. **Physical Tablet UX**: Live verified on Samsung Galaxy Tab S7+ (SM-T975): Navigation rail responsive layout, speed-dial capture FAB, Collections first on Today, interactive Brain chat citations `[1]`, immediate Decoy wipe on Item Detail, and Arabic RTL layout.

## Nemory Intelligence V1.1 Roadmap: Quality, Performance & Release Hardening

With V1 architecture complete, the V1.1 milestone focuses strictly on proving that the intelligence is genuinely good, fast enough, and release-safe across real devices. The architecture is frozen as:
`Image/PDF -> Deterministic Intelligence (OCR/Barcode/Heuristics) -> Evidence/Metadata -> Retrieval -> Qwen Text Agent -> Action Validator -> UX`.

| Phase | Priority | Scope | Exit Condition |
|---|---|---|---|
| **0. Truth-sync** | Immediate | Reconcile docs and source claims across `AGENTS.md`, `NEMORY_MASTER_DOCUMENTATION.md`, and `NEMORY_2_IMPLEMENTATION_STATUS.md` | One authoritative current-state story |
| **1. Real Intelligence Evaluation** | Critical | Create `QwenEndToEndBenchmark` over controlled evaluation vault (receipts, bills, IDs, medical, vehicles) with ablations (FTS, Hybrid, Agent, Graph + Verifier) | Real Nemory Intelligence Score (Target ≥80) |
| **2. Latency & True Streaming** | Critical | Verify native token streaming in `QwenLlmClient`, eliminate stream suppression, instrument Tsearch/TTFT/Tanswer/Tverify/Tfinal | Fast interactive Brain chat UX |
| **3. Agent Action Hardening** | High | Harden `PendingWriteCard` lifecycle: stable IDs, idempotency, rotation, process death, source revision revalidation, decoy invalidation | Safe idempotent write actions |
| **4. Proactive Intelligence** | High | Evidence-driven Today insights with deterministic eligibility gates, persistent dedupe fingerprint (`type+sourceIds+coreFact`), and suppression lifecycle (max 3 insights) | ≤3 evidence-backed, non-repetitive insights |
| **5. Knowledge Quality** | Medium-High | Measure relationship precision for core `RelationshipType` set; alias resolution; bounded relationship injection in Brain prompt | High-precision graph links without context bloat |
| **6. Device / Release Matrix** | Critical | Test final signed/minified release build across low/mid (API 29–31), SM-T975, and AICore devices; test model download/retry lifecycle and cross-device backup restore | Verified release candidate ready for Play Console |
| **7. Arabic OCR / Vulkan** | R&D | Lightweight dedicated Arabic OCR model; Vulkan GPU acceleration experiments | Separate R&D track, not release blocking |
| **8. External Connectors** | Later | Gmail OAuth, Tasks, Contacts, Cloud AI | Deferred until V1.1 quality is proven |

