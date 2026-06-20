# Text Note Enhance Stage 2

Status: in progress.

Scope: mixed text-note markdown checkbox rendering.

Baseline:

- Branch: `main`
- Starting commit: `31835a0 Improve text note editing flow`
- Stage 1 delivered body-first text note creation, read-mode tap-to-edit, full connected validation, APK Drive handoff, commit, and push.

Agent flow:

- Agent B: implementation plan
- Agent C: plan review
- Agent D: implementation
- Agent E: fix agent only if review or validation finds blockers
- Agent F: dedicated Just Notes code review
- Agent G: validation / full connected suite
- Agent H: hourly status monitor

Rules:

- Do not mix unrelated `google_payment`, `text_note_enhance_v2`, old connected-test logs, or emulator logs into Stage 2 commits.
- Preserve Stage 1 behavior and tests.
- Preserve existing markdown checkbox storage format.
- Follow Android emulator readiness checks before connected or instrumentation tests.
- Copy the final debug APK to Google Drive only after Stage 2 implementation, review, and validation gates are green.
