# Agent A Product Review - Debug Google Sync Test Entry

Date: 2026-06-22

## Lens

Steve Jobs / product-trust review for Brian's request to test Google account sync after uninstalling and reinstalling Just Notes.

## Decision

Build an Internal Google Sync Test entry for debug builds only. Do not restore Google account sync as a normal fresh-install Settings promise.

## Why

The v3 decision was correct: fresh release users should not see unfinished cloud-sync promises. Brian still needs a way to validate sign-in, sync now, status/error display, and sign-out while the Google Drive app data path is being hardened. The right product shape is an internal test doorway, not a public feature.

## Non-Negotiable Guardrails

- Release builds must compile to an unavailable/no-op debug sync entry.
- Fresh/default release Settings must not expose Google Account Sync, Sign in with Google, Sync now, account email, sync status, sync error, or Google Drive app data copy.
- Manual Backup & Restore stays separate from account sync.
- Sign-out must not delete local notes.
- The internal/debug copy must clearly say this is test-only and not a release promise.
- No background/auto-sync promise is added for this test path.

## Acceptance Criteria

- In debug builds, Brian can reach an internal Google sync test path from Settings.
- On fresh debug install, the test path can expose Sign in with Google.
- After sign-in, the same section shows account status, sync status/errors, Sync now, and Sign out.
- Privacy lock dismisses sensitive Settings dialogs.
- Release unit coverage proves the debug entry cannot be enabled in release.
