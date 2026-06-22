# Agent B Implementation Plan - Debug Google Sync Test Entry

Date: 2026-06-22

## Target

Add a debug-only/internal-only Google Sync test entry that lets Brian validate Google sign-in, Drive app data sync, retryable errors, and sign-out after a fresh install, while keeping release Settings clean.

## Implementation Steps

1. Add debug/release source-set gate.
   - Debug source set: `DebugGoogleSyncAccess.isAvailable = true`.
   - Release source set: `DebugGoogleSyncAccess.isAvailable = false`, read always false, write no-op.

2. Wire Settings state through `NotepadViewModel`.
   - Expose whether debug Google sync tooling is available.
   - Persist a debug-only entry toggle so the test surface is explicit and internal.

3. Adjust Google sync visibility.
   - Keep current behavior for connected accounts.
   - Additionally allow the section when the debug test entry is enabled.
   - Signed-out debug test state should show `Sign in with Google`, not `Sync now`.
   - Hide sign-out until an account is connected.

4. Tests.
   - Unit test `shouldShowGoogleAccountSyncUi` for account state and debug-entry state.
   - Connected Settings test for fresh debug install: hidden by default, enable debug entry, then see sign-in path.
   - Release unit test proving the debug entry cannot be enabled.

## Out of Scope

- No changes to Drive merge behavior.
- No production OAuth readiness claim.
- No release Google sync entry.
- No background sync or automatic cross-device promise.
