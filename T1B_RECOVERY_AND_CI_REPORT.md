# T1B repository recovery and CI validation

18 September 2026. This report supersedes the repository-state and workflow
descriptions in `T1B_REPAIR_REPORT.md`; historical test results remain historical.

## Repository recovery

The interrupted previous session left `ios-kmp-foundation` unborn, with 936
staged entries including a plaintext credential file. No new orphan commit had
been created. The private GitHub repository had no branches or workflow runs.

- Restored the current branch reference to the preserved `0f724aa` checkpoint.
  `main` and `ios-kmp-foundation-old` remain intact. No source files were reset.
- Removed `.tmp/.git-credentials` from the index and filesystem, and removed
  the repository-local credential helper pointing to it.
- Added ignores for temporary credentials, release bundles, and generated Xcode
  projects. Other pre-existing staged changes remain staged.
- Created a separate source snapshot repository at
  `.toolchain-tmp/ios-ci-snapshot`. It has its own initial history because the
  original history contains oversized release bundles rejected by GitHub.
- Published the snapshot to `7atem/NEMORY`, branch `ios-kmp-foundation`. No
  force-push or rewrite of the original local history was used.
- Preserved Android model assets and source-controlled test fixtures. Excluded
  local credentials, generated release bundles, local IDE settings and device
  capture artifacts. Scanned selected source bytes for GitHub tokens/private keys
  and checked file sizes before publishing. This is not a complete secrets audit.

The original local branch and remote snapshot have different histories. Do not
attempt to resolve that difference with a force push. Future remote work can
use the snapshot checkout or a fresh clone; transfer reviewed local changes
deliberately. The snapshot contains `CI_SNAPSHOT_PROVENANCE.md`, and an ignored
local SHA-256 source manifest records the copied files.

The token supplied in the conversation must be revoked. Transport used it only
in process memory, without embedding it in a remote URL or committed file.

## Further T1B repairs

- Xcode invokes framework generation in its build environment. Removed the
  standalone embedding task invocation and the unresolved duplicate framework
  file reference from XcodeGen.
- The Gradle build phase runs on every Xcode build so edits to any shared source
  are considered by Gradle. It uses `bash gradlew` and permits the required
  writes outside Xcode script sandboxing.
- Simulator signing is disabled by the CI command, not globally for all device
  builds in the project specification.
- Fixed the SwiftUI `frame` argument ordering. Added accessibility identifiers
  and a UI test that checks shared keyword detection, actual Compose text,
  English-to-Arabic switching and switching back. Screenshots attach to xcresult.
- CI selects Xcode 16.4 and an installed iPhone 16 Pro/iOS 18.5 simulator by UDID,
  boots it, runs native shared tests, and runs the application UI test.
- CI records toolchain/runtime details and uploads logs, reports and xcresult.
  It does not claim physical iPhone testing or TestFlight delivery.
- The first CI attempt exposed a removed Android SDK `tools` package requested
  by setup-android v3's defaults. Explicit `packages: platform-tools` fixes that
  dependency setup failure.

## Validation

- Local Android debug build: PASS with `-Pnemory.skipNativeForTests=true`.
  This is not Android native-runtime or release validation.
- Shared/heuristics unit-test rerun: PASS, 26 tests (7 shared and 19 heuristics),
  zero failures. `--rerun-tasks` executed all 55 tasks in the selected test graph.
- Bilingual resource checker: PASS.
- Workflow and XcodeGen YAML parse: PASS. `git diff --check`: PASS.
- First macOS run: failed during Android SDK setup, before Apple compilation.
  <https://github.com/7atem/NEMORY/actions/runs/35341012921>
- Second macOS run: in progress; Apple compilation and simulator acceptance
  remain UNVERIFIED until the result is recorded.
  <https://github.com/7atem/NEMORY/actions/runs/35341109713>

T2 storage/security and T5 runtime migration have not been started.
