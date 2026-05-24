# Just Notes Feature Plan B (Agent B)

## Review verdict on feature_A.md

Feature A is directionally strong and aligned with paid-worthy priorities (trust, reliability, retrieval speed before monetization), but it overstates a few current capabilities and bundles some scopes too broadly for clean implementation/review cycles.
Verdict: **adopt with corrections and re-slicing**, not as-is.

## Corrections to current capability assumptions

1. **Google account sync status is inconsistent across docs and source.**
   - `README.md` says true Google account sync is blocked until OAuth/Drive setup is complete.
   - Source already contains `GoogleDriveSyncClient`, Google sign-in flow, snapshot merge/conflict-copy logic, and sync metadata UI.
   - `GOOGLE_ACCOUNT_SYNC_SETUP.md` indicates implementation exists but real account validation is gated by cloud setup and hardening.
   - Planning should treat this as: **implemented foundation, not release-ready trust layer**.

2. **Reminder capability is one-time local alarm, not full reminder workflow.**
   - Current implementation uses `AlarmManager` one-shot scheduling.
   - No recurrence, no snooze actions, no calendar/agenda view.
   - Notification tap opens app; note-target deep-link behavior is not explicit.

3. **Reminder filter/type filter are not fully realized in active filtering flow.**
   - Enum/UI scaffolding exists, but active note list filtering is currently driven by quick filters and search in ViewModel.
   - Do not claim overdue/upcoming reminder filtering as fully delivered user-facing capability yet.

4. **Share/import scope is narrower than baseline expectation.**
   - Inbound share currently handles text send flow (`text/*` parsing, manifest `text/plain` intent filter).
   - No dedicated import pipeline for `.txt/.md` batch import yet.
   - No inbound image share-to-OCR flow yet.

5. **Privacy/lock features are not present yet.**
   - No biometric/PIN app lock or per-note lock.
   - Reminder notification body can expose note content for text notes.

6. **Premium is presentation only.**
   - Premium tab exists with placeholder pricing copy.
   - No Play Billing integration or entitlement state machine.

## Revised priority backlog

### P0 - Data safety and trust baseline

1. Sync/backup clarity + restore safety hardening.
2. Sync reliability hardening for real cross-device use (account switch/error handling/conflict visibility).
3. Privacy baseline for content leakage prevention (start with notification/recents safeguards).

### P1 - Daily reliability and planning workflow

4. Reminder reliability completion (deep link, recurrence, snooze, timezone/reboot confidence).
5. Retrieval correctness and filter wiring (ensure declared filters match actual behavior).
6. Structured checklist note type with migration-safe storage.

### P2 - Capture/retrieval speed accelerators

7. Home widgets + quick capture paths.
8. Import/export/share capture completion (batch import/export, richer inbound capture).

### P3 - Monetization plumbing after fundamentals are stable

9. Billing + entitlement + graceful fallback.

## Implementation slices and acceptance gates

### Slice 0: Capability truth alignment (docs + UI wording safety)

- Scope:
  - Align user-facing settings copy and docs so "Google account sync" vs "manual backup file target" are unambiguous.
  - Remove/adjust any wording that implies completed capability where only scaffolding exists.
- Acceptance gates:
  - No screen claims features that are not actually usable.
  - README/feature docs and settings labels do not conflict on sync status.
  - Manual QA: settings flows are understandable to a new user.

### Slice 1: Restore safety checkpoint and rollback

- Scope:
  - Add restore pre-checkpoint and one-step rollback for failed/undesired restore.
  - Keep reminder reschedule behavior intact after restore and rollback.
- Acceptance gates:
  - Restore creates a rollback checkpoint before replace-all.
  - Rollback restores pre-restore note/folder counts and content.
  - Corrupt/invalid backup cannot destroy existing local data.

### Slice 2: Sync reliability hardening (existing Google sync foundation)

- Scope:
  - Harden account-switch/sign-out safeguards with unsynced-change warning.
  - Add sync run history metadata (status, timestamp, changed-note count, error category).
  - Surface conflict copies in a dedicated "needs review" list entry point.
- Acceptance gates:
  - Switching account with pending local changes requires explicit confirmation.
  - Sync errors are user-readable and retryable.
  - Conflict copies are visible and actionable, not silent background artifacts.

### Slice 3: Privacy baseline (no lock yet)

- Scope:
  - Add "hide reminder content" mode default-on.
  - Prevent sensitive previews in recents/snapshots where possible.
- Acceptance gates:
  - Reminder notifications can avoid plain-text content leakage.
  - Recents preview respects privacy mode.
  - No auth-lock feature is claimed yet in UI copy.

### Slice 4: Reminder workflow completion

- Scope:
  - Add deep-link from reminder notification to target note.
  - Add recurrence rules (daily/weekly/monthly + selected weekdays).
  - Add notification snooze actions.
- Acceptance gates:
  - Reminder tap opens exact note.
  - Recurrence next-trigger calculation is stable across timezone changes and reboot.
  - Snooze creates predictable next alarm without duplicate alarms.

### Slice 5: Filter correctness and retrieval trust

- Scope:
  - Ensure all visible filters have actual data-path effect in ViewModel.
  - Add overdue/upcoming reminder filtering only if wired end-to-end.
- Acceptance gates:
  - Every filter control changes note list deterministically.
  - Search + filters compose correctly on large datasets.
  - No "dead controls" in UI.

### Slice 6: Structured checklist note type

- Scope:
  - Add first-class checklist note storage and UI interactions.
  - Add conversion path from text note to checklist with safe fallback.
- Acceptance gates:
  - Checklist items persist independently and survive backup/sync/restore.
  - Migration keeps legacy notes intact.
  - Rapid check/uncheck/reorder remains stable.

### Slice 7: Widgets and quick capture

- Scope:
  - Quick-add widget and list widget first; reminder widget optional second pass.
- Acceptance gates:
  - Widget actions open/create within one tap.
  - Widgets survive reboot/update and reflect latest data without ANR behavior.

### Slice 8: Import/export/share completion

- Scope:
  - Batch import `.txt/.md`, batch export with deterministic naming.
  - Inbound share expansion (multi-text payload, URL-first capture, optional image->OCR entry).
- Acceptance gates:
  - Large/odd-encoding import files fail gracefully.
  - Export artifacts are readable and round-trip verifiable.
  - Share entry paths do not create duplicate spam notes.

### Slice 9: Billing + entitlements

- Scope:
  - Play Billing integration, entitlement cache, restore purchase path.
  - Gate only additive premium features after baseline reliability is validated.
- Acceptance gates:
  - Purchase/restore/expiry/grace/offline scenarios are all deterministic.
  - Free-core workflows never dead-end behind entitlement outages.

## Required validation and independent review gates

For every non-trivial slice:

1. **Automated checks**
   - `testDebugUnitTest`
   - `assembleDebug`
   - Targeted `connectedAndroidTest` for touched flows (full suite optional if environment noisy, but targeted evidence required).

2. **Risk-based focused tests**
   - Data-loss sensitive slices: backup/restore/sync conflict/account switch/offline transitions.
   - Reminder slices: reboot/timezone/clock-change/manual notification behavior.
   - Capture slices: share intent matrix, malformed input, large payload behavior.

3. **Manual emulator/device evidence**
   - Before/after screenshots or concise run logs for primary user flows.

4. **Independent code review gate (mandatory)**
   - After non-trivial code changes, run independent review pass (not self-approval).
   - Reviewer checklist: regression risk, data loss risk, UX breakage, test coverage gaps.
   - Block release tagging/APK delivery until findings are fixed or explicitly documented as accepted risk.

## Deferred items and reasons

1. **AI writing/grammar assistant features**
   - Deferred due to ongoing API cost and low priority versus reliability fundamentals.

2. **Large visual redesign**
   - Deferred; current bottleneck is trust/reliability, not visual novelty.

3. **Cross-platform expansion (web/desktop)**
   - Deferred until Android single-user trust baseline is truly stable.

4. **Advanced collaboration/shared notebooks**
   - Deferred; conflict/data-loss surface area is too high before single-user sync hardening is complete.

## Recommended first implementation prompt themes

1. "Harden backup/restore trust: add restore rollback checkpoint and tests for invalid backup safety."
2. "Make sync state truthful: unify settings copy and add explicit sync run history fields with error categories."
3. "Prevent privacy leaks: add reminder notification content privacy mode and recents snapshot protection."
4. "Complete reminder reliability slice: deep-link reminder notifications to note detail and validate reboot/timezone paths."
5. "Wire filter correctness: ensure every exposed filter changes list results through ViewModel tests and Compose UI checks."
6. "Add independent review gate workflow doc + checklist and require it after each non-trivial feature slice."
