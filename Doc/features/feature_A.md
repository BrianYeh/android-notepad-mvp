# Just Notes Feature Backlog (Agent A)

## Current Capability Snapshot

Based on `README.md`, `Doc/release-notes/v1.5.0.md`, and current app sources:

- Core notes exist: text notes and drawing notes, folders, pinning, trash/restore/permanent delete.
- Editing ergonomics are strong for text notes (read/edit modes, in-note find, quick insert, save status, font size).
- Search/filtering exists (global search, note-type filters, reminder filters, sort options, quick chips).
- Reminders exist but are one-time local alarms (no recurring/snooze/calendar view).
- Backup/sync exists in two forms:
  - Manual JSON backup/restore with preview and validation.
  - Google account sync path with Drive appData merge logic and conflict-copy behavior.
- Share/export exists for text and drawing; inbound share currently handles `text/*` only.
- OCR from picked image exists.
- System-language behavior matches ColorNote-style expectation (follows OS language).
- Premium tab exists as a preview, but billing/entitlement is not connected yet.

Gaps vs ColorNote-like paid-worthy baseline are mainly checklist depth, widget/calendar/reminder completeness, lock/privacy hardening, richer import/share capture/export, and production-grade sync UX/reliability polish.

## Prioritized Missing/Partial Feature List

### Must-Have Paid-Worthy Fundamentals

### 1) Trustworthy Sync/Backup/Restore Hardening (P0)
- User value:
  - Users can trust that notes are not silently lost across devices, offline edits, or account switches.
- ColorNote parity rationale:
  - Backup/sync trust is core to ColorNote-style daily use; without trust, users will not pay.
- Implementation scope:
  - Unify settings language and flows so users clearly understand Google account sync vs file backup target.
  - Add explicit sync history (last N runs, status, device, note counts changed).
  - Add conflict inbox/resolution UI (not only auto-created conflict copies).
  - Add account-switch safety checks and preflight warnings.
  - Add restore rollback checkpoint so failed/undesired restore can be undone once.
- Acceptance criteria:
  - Sync status screen shows last success/failure time, source device, changed note count, and error category.
  - Conflicts are visible in a dedicated list with actions: keep local, keep remote, keep both, merge manually.
  - Restoring backup shows before/after delta summary and creates one-tap rollback checkpoint.
  - Account sign-out/switch requires explicit confirmation when unsynced local changes exist.
- Regression risks/tests:
  - Risks: accidental overwrite, duplicate notes from bad conflict handling, broken reminder rescheduling after restore.
  - Tests: unit tests for merge/rollback edge cases, integration tests for sign-in/switch/offline/online transitions, emulator smoke across two devices/accounts.

### 2) Privacy/Security Baseline (P0)
- User value:
  - Sensitive notes are protected on shared or lost devices.
- ColorNote parity rationale:
  - Note lock is a familiar ColorNote expectation and a key paid-quality trust signal.
- Implementation scope:
  - App lock with biometric + fallback PIN.
  - Per-note lock toggle for text/drawing notes.
  - Sensitive mode: optional hide content preview in recents/notifications.
  - Export/share guard for locked notes (re-auth required before export/share).
- Acceptance criteria:
  - Locked app requires auth after timeout/resume.
  - Locked notes cannot be opened/exported/shared without successful re-auth.
  - Reminder notifications for locked notes avoid leaking full content by default.
- Regression risks/tests:
  - Risks: lock-out due to auth loop, accidental plaintext leak in notifications or recents.
  - Tests: auth timeout tests, process-death relaunch tests, notification content tests on Android 13+.

### 3) Reminders + Calendar Workflow Completion (P0)
- User value:
  - Reminder workflows become dependable for daily planning, not just one-off alarms.
- ColorNote parity rationale:
  - Calendar/reminder workflows are central in ColorNote-like usage.
- Implementation scope:
  - Recurring reminders (daily/weekly/monthly/custom weekday).
  - Snooze actions from notification.
  - Calendar/agenda view grouped by date and overdue state.
  - Deep-link from notification directly into target note.
- Acceptance criteria:
  - Users can create and edit recurring schedules; next trigger is always visible.
  - Notification offers at least one snooze option and opens exact note on tap.
  - Calendar view displays upcoming/overdue reminders with correct timezone behavior.
- Regression risks/tests:
  - Risks: duplicate alarms, drift across DST/timezone changes, missed triggers after reboot.
  - Tests: scheduler unit tests for recurrence math, reboot/reschedule tests, manual device clock/timezone change tests.

### 4) Home-Screen Widgets + Quick Capture (P1)
- User value:
  - Faster capture and retrieval from launcher; higher daily stickiness.
- ColorNote parity rationale:
  - Widgets are a classic ColorNote differentiator and high-frequency entry point.
- Implementation scope:
  - Quick-add widget (new text/checklist).
  - Note list widget (folder/filter scoped, refresh action).
  - Reminder widget (today/upcoming).
  - Widget tap behavior opens exact note/editor context.
- Acceptance criteria:
  - Users can add at least two widget types and configure folder/filter scope.
  - Widget actions create/open notes within one tap and survive device reboot/update.
- Regression risks/tests:
  - Risks: stale data, ANRs from widget update load, broken deep links.
  - Tests: widget instrumentation tests, launcher reboot tests, battery impact sampling.

### 5) Checklist Note Type (Structured, Not Just Text Prefix) (P1)
- User value:
  - Real task tracking with fast check/uncheck and completed-item behavior.
- ColorNote parity rationale:
  - ColorNote supports dedicated checklist notes; text-only checkbox prefix is partial parity.
- Implementation scope:
  - New note type `CHECKLIST` with structured items and completion state.
  - Reorder, bulk-check/uncheck, hide/show completed, quick-add row.
  - Convert between text note and checklist note.
- Acceptance criteria:
  - Checklist items persist independently from plain text body.
  - Marking items complete updates list instantly and survives sync/backup/restore.
  - Existing quick-insert text checkboxes remain supported for legacy notes.
- Regression risks/tests:
  - Risks: migration issues, sync schema mismatch, broken search indexing.
  - Tests: DB migration tests, sync merge tests with checklist edits on two devices, UI tests for rapid toggling/reordering.

### 6) Import/Export + Share Capture Completion (P1)
- User value:
  - Easier migration in/out and capture from external apps/channels.
- ColorNote parity rationale:
  - Practical backup/export/share breadth is expected for serious note apps.
- Implementation scope:
  - Import `.txt`/`.md` files into notes.
  - Batch export selected notes (zip/text bundle + metadata).
  - Extend inbound share to support URLs, image share-to-OCR, and multi-item text share.
  - Add duplicate-handling rules on import.
- Acceptance criteria:
  - User can import multiple text files and preserve titles/content reliably.
  - User can export selected notes in one operation with deterministic filenames.
  - Shared image can be turned into OCR text note directly from share intent.
- Regression risks/tests:
  - Risks: malformed import causing crashes, encoding issues, duplicate explosions.
  - Tests: import fuzz tests (bad encoding/large files), intent integration tests for share targets, export round-trip validation.

### 7) Monetization Plumbing (Billing + Entitlement + Graceful Fallback) (P1)
- User value:
  - Clear subscription behavior and confidence that payment unlocks durable value.
- ColorNote parity rationale:
  - Paid-worthy product needs reliable commerce mechanics, not only a preview tab.
- Implementation scope:
  - Integrate Google Play Billing.
  - Entitlement state machine (trial/active/grace/expired) with offline cache.
  - Feature gating only after fundamentals above are stable.
  - Purchase restore flow and account mismatch messaging.
- Acceptance criteria:
  - Real purchase, restore purchase, and expiry paths work on test tracks.
  - App remains fully usable for free-core features without paywall dead-ends.
  - Entitlement status is visible and debuggable in settings.
- Regression risks/tests:
  - Risks: users lose paid access after reinstall/network loss, broken app flow on billing outage.
  - Tests: billing sandbox scenario matrix, entitlement cache tests, offline launch tests.

### Later Nice-to-Have Enhancements

### 8) Color Organization Layer (P2)
- User value:
  - Faster visual scanning and lightweight categorization.
- ColorNote parity rationale:
  - Note colors are a common ColorNote mental model.
- Implementation scope:
  - Per-note color tags with palette picker.
  - Color filter chips in list/search.
- Acceptance criteria:
  - Users can set/clear colors and filter notes by color.
- Regression risks/tests:
  - Risks: poor contrast/accessibility, inconsistent color rendering.
  - Tests: contrast checks, UI tests for filter combinations.

### 9) Archive Workflow (P2)
- User value:
  - Separate "not active but kept" notes from trash/deleted notes.
- ColorNote parity rationale:
  - Archive is a useful middle state between active and delete.
- Implementation scope:
  - Add archived state, archive filters, archive bulk actions.
- Acceptance criteria:
  - Archived notes are hidden from default active list but searchable by archive filter.
- Regression risks/tests:
  - Risks: confusion with trash semantics, restore edge cases.
  - Tests: state transition tests (active/archive/trash), filter/sort coverage.

### 10) Advanced Search and Organization Polish (P2)
- User value:
  - Faster retrieval as note volume grows.
- ColorNote parity rationale:
  - Power users expect quick retrieval at scale.
- Implementation scope:
  - Saved searches, folder-level sort presets, optional indexing/perf improvements for large datasets.
- Acceptance criteria:
  - Search latency remains acceptable on large local datasets and common filters are one tap away.
- Regression risks/tests:
  - Risks: query bugs, stale indexes, memory growth.
  - Tests: benchmark dataset tests, query correctness tests against known fixtures.

## Recommended Implementation Order

1. P0.1 Sync/Backup/Restore hardening
2. P0.2 Privacy/Security baseline
3. P0.3 Reminders + calendar workflow completion
4. P1.1 Widgets + quick capture
5. P1.2 Structured checklist note type
6. P1.3 Import/export + share capture completion
7. P1.4 Billing/entitlement plumbing
8. P2 Color organization
9. P2 Archive workflow
10. P2 Advanced search/perf polish

Rationale: this order maximizes trust and daily reliability first, then capture/retrieval velocity, then monetization mechanics.

## Explicit Exclusions / Not Now

- AI writing assistant, grammar generation, or any recurring API-cost writing features.
- Heavy visual redesign unrelated to note reliability/productivity goals.
- Multi-platform expansion (web/desktop) before Android paid-worthy fundamentals are stable.
- Complex collaboration/shared notebooks before single-user trust baseline is complete.
