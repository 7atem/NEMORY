# Review of the 43-scenario benchmark and CI claims

Verified 26 October 2026 against commit `ddc7940776045ee0cd373f754fe126020b9cde9a`. Local HEAD and the GitHub `main` branch both resolve to this commit. The attached pasted file was not used.

The benchmark result is confirmed. The claim of fully passing dual-platform validation needs correction: the iOS workflow is green while its UI test execution failed.

## Verified results

| Check | Evidence | Result |
| --- | --- | --- |
| Benchmark expansion | Commit changes only the benchmark runner and JSON fixtures; 43 fixtures, six new cases, zero `knownIssue` flags | Confirmed |
| Fresh local benchmark | Forced execution of the test task; JUnit XML reports 43 tests, 0 failures, 0 errors, 0 skipped | PASS |
| Remote benchmark | Android `validation-reports` artifact: `core/ai/rag/build/reports/tests/testDebugUnitTest/gKQD2lgbRiw/index.html` | 43 tests, 0 failures, 0 skipped |
| Remote RAG module | Same artifact, RAG test summary | 231 tests, 0 failures, 0 skipped |
| Android build/validation | `verify` job logs include native builds and `:app:assembleRelease`; final build succeeds | PASS |
| Android instrumented validation | `migrations` job logs show 14 database tests and 2 backup tests finishing successfully | PASS |
| Bilingual resource script | Re-run `python scripts/check_localization.py`; also successful in Android CI | PASS within the script's scope |
| iOS shared native tests | Job logs show `:shared:iosSimulatorArm64Test` and successful Gradle execution | PASS |
| iOS unsigned device build | Job logs contain `** BUILD SUCCEEDED **` | PASS |
| iOS UI smoke tests | Job logs contain three failed tests, six assertion failures, `** TEST FAILED **`, exit code 65 | FAIL |

Fresh local command:

```powershell
.\gradlew.bat :core:ai:rag:testDebugUnitTest --tests '*ComplicatedScenariosBenchmarkTest' --rerun --max-workers=1 --no-parallel --console=plain
```

The test task executed; dependency tasks remained up to date. Fresh XML timestamp: `2026-10-26T06:10:49.410Z`. Execution reported 43/43 passing, zero skipped.

## Findings

### P1 — iOS UI failures are hidden by the workflow's success status

`.github/workflows/ios.yml:116` sets `continue-on-error: true` on the Xcode UI test step. For this exact commit, the logs record:

- `testArabicModeAppliesLayoutAndKeepsSharedResult`: failed.
- `testLanguageToggleChurnRestoresHeaders`: failed.
- `testSharedDetectionAndLanguageSwitch`: failed.
- Summary: 3 tests, 6 assertion failures, exit code 65.

All three fail while finding/tapping the `openSample` element in `iosApp/iosAppUITests/FoundationSmokeTests.swift:92`. No production Swift file currently declares that accessibility identifier. The tests never reach the bilingual assertions they were intended to validate.

Restore a reachable sample entry or update the tests to a supported route, then make the required UI test step fail the workflow on test failures. Re-run the iOS workflow before describing UI validation as passing. The green status currently confirms the build and shared native tests; it does not establish working UI tests.

### P2 — The 43 cases do not establish iOS behavior parity

`ComplicatedScenariosBenchmarkTest.kt` lives in `core/ai/rag/src/test/java`, imports Android `Context` and Android repositories, and invokes `DeterministicToolRegistry` with mocked repository inputs. The iOS workflow runs `:shared:iosSimulatorArm64Test`, which is a different suite.

Use the wording “43 Android JVM contracts passed; iOS shared tests and build passed; iOS UI tests failed.” Shared fixtures and equivalent iOS behavior tests are needed to claim that these six scenarios work on both platforms.

### P2 — Unknown fixture modes can silently pass without exercising a contract

The dispatcher at `ComplicatedScenariosBenchmarkTest.kt:48` has no failing `else` branch. A misspelled or newly introduced mode can run no handler and still be counted as a passing parameterized test. All 43 current fixture modes are recognized, so this does not invalidate the observed 43-case result.

Make unsupported modes fail explicitly and require assertions for each fixture contract.

## What the six additions actually cover

| Cases | Confirmed coverage | Limits |
| --- | --- | --- |
| 38: trip briefing | Expected headline and three formatted flight/hotel facts | All items are within five days. No item outside seven days or unrelated nearby trip tests the clustering boundary. |
| 39 and 43: missing documents | English and Arabic positive signals for a flight without a hotel and a vehicle without insurance | No matching hotel/insurance negative controls, unrelated policy, or different vehicle tests suppression and matching. |
| 40: recurring increase | USD 40 to USD 52 yields +12 and 30% for one provider | Does not test mixed currencies, multiple providers, unchanged/decreased charges, or duplicate charges. |
| 41: media backlog | Headline of two items and facts for the unfinished movie/book | Does not assert exact source IDs or exact fact-list contents. Completed media exclusion is not checked directly by ID. |
| 42: Arabic utility sum | Two explicitly supplied amounts total SAR 100 with Arabic merchant/period facts | Metadata is supplied directly to the repository mock; this is not a test of OCR or extracting the merchant from a document. |

These are useful deterministic regression contracts. They do not exercise capture/OCR, a downloaded language model, live connectors, or the SwiftUI app end to end.

## Other reporting corrections

- The remote Android lint artifact contains one `SelectedPhotoAccess` warning. Lint succeeds, but the current artifact does not support “zero warnings.”
- The bilingual script checks resource keys and format arguments under `app`, `core`, `feature`, and `sync`. Its pass does not validate all iOS user-facing text or visual RTL layout.
- The benchmark class comment and `AGENTS.md` still refer to 37 cases. The class and fixture count now enforce 43.
- This commit adds tests and fixtures; it does not add the underlying production tool implementations.

## Evidence

- [Reviewed commit](https://github.com/7atem/NEMORY/commit/ddc7940776045ee0cd373f754fe126020b9cde9a)
- [Android workflow](https://github.com/7atem/NEMORY/actions/runs/40189648429)
- [Android verification job and report artifact](https://github.com/7atem/NEMORY/actions/runs/40189648429/job/121157719633)
- [Android instrumented validation job](https://github.com/7atem/NEMORY/actions/runs/40189648429/job/121157719572)
- [iOS job, including failing Xcode test logs](https://github.com/7atem/NEMORY/actions/runs/40189648281/job/121157718748)

Read-only CI verification used the GitHub API and downloaded the existing job logs and Android reports. No workflow was triggered and no production source was changed by this review.
