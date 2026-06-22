# Agent C Plan Review - Debug Google Sync Test Entry

Date: 2026-06-22

## Verdict

Approved with guardrails.

## Review Notes

- The compile-time debug/release source-set split is the important safety boundary. A runtime preference alone would be too easy to leak into release.
- The Settings UI should keep the internal entry visually under Developer tools and keep Backup & Restore separate.
- Signed-out state must launch Google sign-in. Showing `Sync now` before account connection would be confusing.
- Release coverage should test the no-op implementation directly, not just assert UI absence in a debug test.

## Required Validation

- `git diff --check`
- `testDebugUnitTest`
- `testReleaseUnitTest`
- `assembleDebug`
- `assembleDebugAndroidTest`
- Emulator readiness check before any connected test claim.
- Focused connected test for the debug Settings path if `LocalNotepad_API35` is online.
- Agent F code review with Codex `gpt-5.5` xhigh before final handoff.
