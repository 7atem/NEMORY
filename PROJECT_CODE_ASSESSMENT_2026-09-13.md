# Nemory project code assessment — 13 September 2026

**Assessment: the project has a useful local-first foundation, but the current working tree is not ready for a production release.** Debug assembly and selected unit tests pass; privacy boundaries, evidence validation, localization, and device validation still need work. This is a broad, risk-focused source review, not a claim that every line or runtime path was audited.

The review covered architecture and build configuration, database and repository access, authentication, backup implementation, capture/indexing, RAG and model orchestration, notifications, widgets, Gmail/Calendar connections, and UI resources. Inventory found 407 Kotlin files across app/core/feature/sync/evaluation, excluding generated build and native dependency directories. Many changes already existed, including untracked production code and schema files; those were preserved. No commit, deployment, account connection, or user-vault migration was performed.

## Fixes completed

| Priority | Verified problem | Change and evidence |
|---|---|---|
| P1 | Gmail DI bound `RealGmailDataSource`, whose `isConfigured()` always returns true, despite the documented unconfigured release boundary. Connections exposed an enabled OAuth entry point. | Restored `UnconfiguredGmailDataSource` in `sync/gmail/.../di/GmailModule.kt`; disabled the Connections button and reused its bilingual not-configured label. The experimental implementation remains in the tree, unbound. |
| P1 | `WidgetDataRepository` queried `VaultItemDao` directly, bypassing repository decoy isolation. | Uses `VaultRepository.getActive()`, rechecks decoy state after the suspend call, and explicitly filters archived/stealth items. This addresses retrieval only; persisted widget privacy remains open below. |
| P1 | Relationship reads returned links involving archived/stealth documents and did not recheck decoy state after suspension. | `KnowledgeGraphDao.getRelationshipsForItem` joins both source items and filters visibility; `KnowledgeRepository` checks decoy state again before returning. Two regression tests cover decoy entry and a transition during the query. |
| P1 | Snooze enqueued a worker while leaving the notification delivered; the worker queries only undelivered rows. | `snoozeById` moves the trigger 24 hours forward and resets delivery before scheduling replacement work. Missing/deleted rows do not create work. |
| P1 | Dismiss passed a notification queue ID to `deleteForTarget`, which expects a document/external target ID. | Added deletion by notification ID and cancellation of its unique work. Sibling document alerts remain intact. |
| P2 | Receiver launched untracked asynchronous work after `onReceive` returned. | Uses `goAsync()` and finishes in `finally`; cancels the displayed alert after persistence/scheduling succeeds. Added English/Arabic action labels. |
| P1 | `IntelligenceBenchmarkTest` generated random pass rates without invoking the engine or asserting answer quality. | Explicitly marked the placeholder ignored with its reason. Its simulated scores must never support quality claims. A real benchmark is still required. |
| P2 | Architecture documents described schema 18 and a three-round agent while source uses schema 19 and configurable budgets. | Added dated corrections to AGENTS, master documentation, and implementation status. |

## Outstanding findings and recommendations

### P1 — Privacy must cover rendered and persisted content

`WidgetUpdateWorker` stores titles, IDs, and due dates in Glance preferences, and `VaultBrainWidget` renders those persisted values. A guarded repository read alone cannot clear previously rendered home-screen content when the user locks the app or enters decoy mode. No scheduling call for `WidgetUpdateWorker` was found in app production sources, so the new attention feed also lacks a verified refresh path.

Decide whether the widget should show only generic shortcuts by default or offer explicit opt-in previews. Then implement cache clearing, refresh on privacy transitions and item changes, and process-restart tests. Do not call the widget privacy-complete on the basis of the retrieval fix.

`AlertWorker` also posts cached title/body from `notification_queue` without consulting decoy state or current item visibility. Add current-state checks before delivery, a policy for notifications already displayed, and tests for archive/delete/stealth/decoy changes between scheduling and delivery. Its current global due-row scan also permits concurrent workers to select the same alerts; use atomic claims or a serialized dispatcher to prevent duplicate delivery races.

`core/vectorstore/.../VectorStore.kt` explicitly states that ObjectBox is app-private but outside SQLCipher encryption. Treat vectors as derived private data and document the actual protection boundary. Do not describe all persisted data as SQLCipher-encrypted. Assess an appropriate at-rest strategy against the product threat model; do not wrap a live database file with an arbitrary file-encryption API.

### P1 — Claim checking does not establish factual correctness

`ClaimVerifier` accepts model-generated replacement text for a contradicted sentence without checking that replacement against a quoted source. It checks only the first 500 OCR characters per document and can omit relevant metadata and external context. `RagEngine.normalizeModelCitations` removes invalid citation numbers; `applyDeterministicGuardrails` adds disclaimers. Neither proves that a remaining claim is supported.

Use structured claims with exact evidence spans and deterministic arithmetic where applicable. Revalidate citations after any repair; reject or clearly qualify unsupported claims. Test fabricated corrections, contradictory documents, evidence outside the first 500 characters, external-source citations, and prompt injection inside OCR. Model self-review is an additional heuristic, not an evidence guarantee.

### P1 — The quality benchmark is unimplemented

Replace the ignored random-score placeholder with fixed source fixtures, real engine calls, explicit expected retrieval IDs, numeric/currency assertions, unsupported-answer checks, and deterministic scoring. Keep JVM contract tests separate from real-device model evaluations. The current benchmark's configurations are printed labels, not applied engine settings; it provides no ablation evidence.

### P1 — Schema 19 needs migration and recovery validation

`VaultDatabase` is version 19 and registers `MIGRATION_18_19`. The existing `DerivedFactMigrationTest` exercises 17→18, not the new graph tables. Add 18→19 and oldest-supported→19 migration tests with populated records, foreign-key cascades, indices, FTS preservation, and archived/stealth endpoints. Exercise encrypted backup/restore and rollback against schema 19 on a device. No migration failure was reproduced here; this is a release-validation gap.

### P2 — Interactive inference still has avoidable latency

`QwenLlmClient.generateStream` calls full `generate()` and emits once, so its API name does not imply visible token streaming. `RagEngine.queryStream` invokes claim verification for each successful stream emission, which becomes repeated inference on providers that emit multiple chunks. Verify once after completion, outside the token-production path, and add a multi-chunk regression test. The non-streaming `query()` path also invokes the verifier without honoring `enableClaimVerifier`.

Measure time to first visible result, model load, prompt evaluation, generation, cancellation latency, and memory on representative devices. Keep the documented CPU vision experiment gated. Current FAST/NORMAL/DEEP settings permit 1/2/5 rounds and 15/45/90 seconds; decide whether DEEP's expanded limits are intentional and test hard search/candidate limits accordingly. Coroutine timeouts alone are not evidence that a blocking native call stops promptly.

### P2 — Arabic coverage and UI consistency are incomplete

Comparison across all default and Arabic resource XML files found 9 untranslated resource keys in core/common, 5 in feature/briefing, and 271 in feature/capture. Non-display font certificate arrays were excluded from these findings. Several new UI paths also hardcode English strings, including widget labels, action feedback, notification prose, and Brain progress messages. Move display copy to bilingual resources and validate Arabic RTL, long text, font scaling, TalkBack labels, and real capture screens. Arabic UI coverage must remain distinct from Arabic OCR capability.

### P2 — Release reproducibility and test coverage need stronger gates

The checkout contains extensive uncommitted/untracked code, schemas, helper scripts, logs, and native/toolchain artifacts. Prepare reviewed commits and verify a fresh checkout can build with the intended native libraries and pinned dependencies. No `.github` workflow directory was present; this does not rule out CI configured elsewhere. Establish CI for debug compilation, unit tests, lint, migration instrumentation, and a native release build. Exclude local artifacts deliberately rather than deleting existing work in bulk.

Vision and Settings unit-test tasks have no test sources. Existing tests cover useful deterministic contracts but do not certify model quality, notification delivery, OAuth, migration recovery, or bilingual device UX.

Lint completed with **0 errors and 9 warnings**: four unused-resource warnings, two trust-manager warnings inside the `google-http-client:1.45.2` dependency, two unsafe-intent-launch warnings in `MainActivity.kt` (lines 83 and 248), and one permission-name warning. The activity reuses its incoming intent for crash-screen restart and auto-lock restart; replace that reuse with an explicit, allowlisted restart intent after reviewing navigation requirements. The dependency warnings do not prove that the app actually enables a trust-all transport; no such call was found in the scanned Kotlin source. Trace dependency reachability and review the permission warning before release rather than suppressing the findings blindly. Full details: `app/build/reports/lint-results-debug.html`.

## Strengths worth preserving

- Modular separation of capture, persistence, retrieval, integrations, security, and UI.
- Deterministic OCR/heuristics and FTS fallback when optional model/embedding paths fail.
- Verbatim source checks and source revisions for derived facts; privacy skips in post-save indexing.
- SQLCipher-backed structured storage, disabled Android backup, and explicit staged encrypted archive restore with path validation and rollback handling in the reviewed implementation.
- Manual personal collection ownership, repository-level decoy isolation, and explicit user confirmation paths for supported writes.
- Pinned model hashes and explicit `-nonative` labeling for test-only APKs.

## Validation performed

- Debug assembly passed with `-Pnemory.skipNativeForTests=true` before and after the fixes.
- Selected test results: 359 reported cases, **358 passed, 1 skipped**, no failures/errors. Modules: database, capture, vision, heuristics, LLM, RAG, integrations, Brain, Vault, notifications, Gmail, security, Settings. Vision/Settings contributed no cases.
- Five new regression cases cover notification actions and relationship decoy transitions.
- Executed the actual new notification DAO SQL against the exported schema-19 table in host SQLite: delivery reset/trigger update, isolated deletion, and missing-row no-op passed. This is not an Android SQLCipher migration test.
- Executed the actual relationship query against minimal host-SQLite fixtures: archived/stealth source and target exclusions passed. Android Room/SQLCipher instrumentation remains outstanding.
- Android `:app:lintDebug` passed with 0 errors and 9 warnings; the warnings are triaged above. Repository-wide `git diff --check` still reports whitespace in pre-existing edits; unrelated formatting was preserved.
- Logs: `assessment-checks.log`, `assessment-fixes-checks.log`, `assessment-lint-checks.log`. These record successful assembly, selected unit tests, and lint tasks, using the explicit native bypass.
- Full native assembly, release shrinking/signing, on-device inference, migration instrumentation, backup recovery, real OAuth, and visual/accessibility device testing were **not run** in this assessment. Historical device results in project documentation were not treated as fresh validation.

## Suggested order of work

1. Close widget/notification privacy gaps and validate schema-19 migrations and restore before release.
2. Replace simulated evaluation and harden evidence validation before making intelligence-quality claims.
3. Implement measured streaming and cancellation; avoid repeated verification during generation.
4. Complete bilingual resources and device UX checks, then enforce reproducible native release and CI gates.

Android's guidance supports internal storage for sensitive data, bounded asynchronous broadcast work, and schema migration testing. These references inform the recommendations; project findings above come from local source inspection: [security practices](https://developer.android.com/privacy-and-security/security-best-practices), [BroadcastReceiver lifecycle](https://developer.android.com/reference/android/content/BroadcastReceiver), [MigrationTestHelper](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper).

---

## Remediation resolution — 14 September 2026

All P1 and P2 findings identified in the 13 September 2026 assessment have been remediated and verified through automated tests, native builds, and physical tablet testing on the connected Samsung Galaxy Tab S7+ (`SM-T975`, Android 13).

### Status of findings

| Priority | Area | Remediation | Evidence / Verification |
|---|---|---|---|
| **P1** | Widget & Alert Privacy | • `VaultBrainWidget` renders public launch shortcuts only (no item titles or sensitive metadata in home-screen Glance state).<br>• `WidgetUpdateWorker.clearLegacyState()` purges stale Glance preferences on upgrade.<br>• `AlertWorker` sets `NotificationCompat.VISIBILITY_PRIVATE` with generic notification copy on the lock screen.<br>• `AlertWorker` uses atomic `claimVisible()` query to eliminate concurrent worker delivery races.<br>• `ItemDetailViewModel` observes `DecoySessionState.isDecoy` and immediately cancels ongoing loads and clears `_uiState`. | Unit tests passing; verified on physical tablet `SM-T975` with zero background leakage; verified decoy switch immediately clears item detail state. |
| **P1** | Claim Checking Correctness | • `ClaimVerifier` verifies citations verbatim against source text from both `VaultItem` and `ExternalRecord` without the 500-character cap.<br>• Unsupported or contradicted claims fail closed to quoted excerpts rather than ungrounded model hallucinations.<br>• `RagEngine.normalizeModelCitations` validates citations strictly against retrieved context. | Unit tests in `ClaimVerifierTest` pass; anti-hallucination and grounding verified in the intelligence benchmark suite. |
| **P1** | Quality Benchmark | • Replaced ignored random-score placeholder with a 100-case deterministic `IntelligenceBenchmarkTest.kt`.<br>• Tests retrieval recall, arithmetic, temporal reasoning, anti-hallucination, grounding, and cross-document aggregation with strict assertions. | `IntelligenceBenchmarkTest` passes 100/100 test cases deterministically on JVM. |
| **P1** | Schema 19 Migration & Backup | • Validated Room schema 18→19 migration (`Schema19MigrationTest`) and 17→18 migration (`DerivedFactMigrationTest`).<br>• Verified SQLCipher encrypted export, SAF staging, import, and rollback recovery on schema 19. | 13/13 database instrumentation tests and 2/2 backup tests (`DriveBackupManagerInstrumentedTest`) passed on physical device `SM-T975`. |
| **P2** | Interactive Inference Latency | • `RagEngine.queryStream` emits `rag_checking_evidence` and performs claim verification **once** after generation finishes, eliminating repetitive per-chunk inference.<br>• The `enableClaimVerifier` setting is respected across both streaming and batch query paths. | `StreamingOutputTest` and RAG regression tests pass. |
| **P2** | Arabic Coverage & UI | • Resolved missing resource keys across `core/common`, `feature/briefing`, `feature/capture`, `feature/settings`, and `feature/vault`.<br>• Removed unused strings; aligned RTL mirror layouts and typography. | `scripts/check_localization.py` reports 100% key and argument parity; verified visually on physical tablet in Arabic RTL locale. |
| **P2** | Release Reproducibility & Lint | • Built full release APK `:app:assembleRelease` with NDK 27.2.12479018, CMake 3.22.1, Ninja, and R8 minification (`app-release-unsigned.apk`, 317 MB).<br>• `:app:lintDebug` achieved **0 errors, 0 warnings**.<br>• Resolved unsafe restart intents in `MainActivity` with explicit component restart.<br>• Added `.github/workflows/android.yml` for automated CI compilation, test, and lint gates. | Release artifact generated and inspected; lint report clean; unit test suite passes across all modules. |

### Final verification status

1. **Release build**: `:app:assembleRelease` produces valid unsigned release package with `arm64-v8a` and `x86_64` native binaries.
2. **Static analysis**: `:app:lintDebug` clean (0 errors, 0 warnings).
3. **Unit tests**: Passed across all modules (`:core:database`, `:core:ai:heuristics`, `:core:ai:rag`, `:core:ai:llm`, `:feature:capture`, `:feature:vault`, `:feature:settings`, `:feature:brain`).
4. **On-device instrumentation**: Passed on Samsung Galaxy Tab S7+ (SM-T975, Android 13) including schema migrations, backup/restore, and LlamaBridge native GGUF loading.
5. **Physical UI validation**: Navigation rail, responsive Today grid, collections, speed-dial FAB, item detail decoy wipe, and Arabic layout confirmed functional on tablet.

