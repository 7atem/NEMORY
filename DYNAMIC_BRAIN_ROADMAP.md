# Dynamic Brain: code audit and implementation direction

17 September 2026. Product direction: Gmail and tasks alongside Calendar; read
linked data and propose actions for user confirmation. Google Tasks is the selected
first task provider. All application processing and sync orchestration must run on
the user's phone; there is no Nemory VPS or backend.

## Why the downloaded model still feels static

- `RagEngine.executeAgentTool` advertises reminders, calculation, date and amount
  tools whose implementations return placeholders. Calendar results are strings
  consumed by the planner; `LocalAgent.Result` does not carry those results into
  final answer evidence. Both query paths stop when vault retrieval is empty,
  before using external evidence for an answer.
- `ClaimVerifier` accepts verbatim excerpts, not general supported paraphrases or
  cross-document deductions. This makes synthesis collapse back into quotations.
- Today used only five documents, three event titles, and three reminder titles,
  then suppressed refresh for six hours even after input changed.
- Gmail production DI uses `UnconfiguredGmailDataSource`. A source enum for
  Google Tasks is not a working connector. Calendar is the implemented connector.

## Implemented in this change

Today observes its data, model readiness, and decoy state in the ViewModel.
Changed data cancels outdated analysis and clears stale insights, with a one-second
delay to coalesce sync bursts. Identical inputs retain the six-hour cache.
Upcoming events/reminders and the existing bounded Gmail selection enter analysis.
Readiness changes trigger another pass; decoy entry cancels and clears it.

The bounded prompt now includes eight documents with summary, metadata and expiry,
six external records with descriptions and timestamps, and six active reminders
with due dates. Confidence and all ranking dimensions are validated, with score
computed by code. These remain model-generated suggestions, not independently
verified factual conclusions. They do not execute actions. No background proactive
worker or Gmail/task OAuth implementation was added by this change.

Validation: `:feature:vault:testDebugUnitTest :core:ai:rag:testDebugUnitTest
-Pnemory.skipNativeForTests=true` completed successfully. New tests cover changed
input, model readiness, decoy clearing, score/confidence rejection, richer document
context, and relationship endpoint filtering. Two existing compilation gaps were
repaired: a missing serialization import and the absent relationship-prompt helper.
Relationship context now includes only known types with both endpoints retrieved.
This is JVM validation, not a new physical-device model-quality benchmark.

## Next implementation sequence

1. Replace planner placeholder tools with typed executable tools. Advertise only
   available capabilities. Return typed evidence and errors; preserve cancellation.
   Route retrieved external evidence into both answer paths, including questions
   with no matching vault document. Test calendar-only and reminder-only answers.
2. Separate sourced facts, deterministic calculations, and model suggestions in
   the answer contract. Validate source IDs, dates and amounts in code. Keep
   deductions labeled and linked to their premises. Do not just disable grounding.
3. Add Gmail and task provider account authorization, account selection, bounded
   pagination, incremental sync, token expiry recovery and disconnect cleanup.
   Gmail starts with `gmail.readonly`; Google Tasks reads use `tasks.readonly`.
   Creating remote tasks needs a separately authorized write capability and the
   existing explicit action-confirmation pattern. Nemory reminders are not synced
   Google Tasks and must not be presented as such.
4. Schedule bounded analysis after meaningful data changes with persisted input
   revisions, battery constraints, foreground preemption, and deduplication of
   dismissed insights. Add source-linked proposals for conflicts, preparation,
   deadlines and follow-ups; measure useful suggestions and false positives.

## External configuration still required

Use Android Google Identity Services `AuthorizationClient` for device-side consent
and access-token acquisition, then call Gmail and Tasks HTTPS APIs directly from
the phone. WorkManager handles bounded polling; any renewed consent requirement
returns the connection to a user-action-required state. Do not introduce server
auth-code exchange, a backend client secret, or server push infrastructure. Local
AI consumes locally cached records. Google still hosts the linked Gmail/Tasks
accounts, so syncing those services requires internet access.

Production Gmail requires a configured OAuth project/client for Nemory's signing
identity and an appropriate consent flow; its read scope is restricted. Linking
one provider does not grant access to other apps. Provider credentials and actual
account consent cannot be replaced with model reasoning or mock sync records.

Official references:
- https://developers.google.com/workspace/gmail/api/auth/scopes
- https://developers.google.com/workspace/tasks/reference/rest/v1/tasks/get
- https://developer.android.com/identity/authorization
