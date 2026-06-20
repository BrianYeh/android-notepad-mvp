# Just Notes Text Note Enhancement V4

Date: 2026-06-20

Purpose: track non-code outputs for the Just Notes text-note enhancement v4 workflow.

## Source Request

Brian asked to start a new v4 workflow:

- Agent A: use Codex `gpt-5.5` with `model_reasoning_effort="xhigh"` to review the Just Notes text-note feature from a Steve Jobs product lens.
- Later agents should each own their role and continue the workflow to completion.
- Store non-code agent outputs for this text-note enhancement work in this directory:

`D:\AndroidStudioProjects\Doc\jobs_view\text_note_enhance_v4`

## Agent Roles

- Agent A: product review and top suggestions.
- Agent B: implementation plan from Agent A's accepted suggestions.
- Agent C: strict plan review and scope gate.
- Agent D: implementation.
- Agent E: focused issue fixing only if Agent C/F/G finds blockers.
- Agent F: dedicated Just Notes code-change review using Codex `gpt-5.5` with xhigh reasoning.
- Agent G: validation runner, including full connected suite when ready.
- Agent H: status monitor only if Brian requests a new monitor for v4.

## Tracking Rules

- Keep all v4 non-code reports in this directory.
- Do not mix unrelated `google_payment`, `text_note_enhance_v2`, `text_note_enhance_v3`, old connected-test logs, or emulator logs into v4 commits unless Brian explicitly asks.
- Preserve user and prior-agent changes already present in the working tree.
- After completed Android feature work, the final debug APK must be copied to Brian's Google Drive test folder per project `AGENTS.md`.

## Initial State

- Project root: `D:\AndroidStudioProjects`
- Branch state at v4 start: `main` ahead of `origin/main` by 1 commit.
- Existing dirty/untracked files from previous work are present; v4 agents should avoid treating those as their own changes.
- Agent A status: pending.
