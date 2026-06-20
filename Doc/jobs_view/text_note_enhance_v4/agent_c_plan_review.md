# Agent C - Plan Review For Text Note Enhancement V4

Date: 2026-06-20
Workspace: `D:\AndroidStudioProjects`
Reviewer: Agent C using Codex `gpt-5.5` with `model_reasoning_effort="xhigh"`

## Scope

Review Agent B's implementation plan and act as the strict scope gate before Agent D implementation.

Agent C made no code changes and did not run builds or tests.

## Verdict

Approved with required modifications before Agent D.

Agent B's Stage 1 scope is correct and is not blocked.

## Required Modifications Before Agent D

- Blank-draft hiding must exclude `SaveStatus.Failed` or otherwise provide obvious failed-save / retry UI. Save failure must never be hidden.
- Stage 1 must not add a new "expand tools" affordance and must not redesign the formatting toolbar.
- Stage 1 may hide the accessory bar only for standard blank new text drafts. After real content is typed, the accessory bar should return normally.
- Body-only read mode must use the low-risk approach: do not render `text_note_read_title`; let the first line exist only in the body renderer. Do not extract the first line into a custom renderer.
- Body-only tests should tap `text_note_read_content` at the first-line/body area, not use a vague `onNodeWithText(firstLine)` read-mode tap target.
- Blank-draft tests must explicitly verify that `text_note_top_save_status`, metadata, compact metadata, accessory bar, and Premium lock/chrome are absent while the standard draft is blank, while overflow Details still opens title/details.

## Approved Agent D Scope

- Modify only:
  - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
  - `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- `UiText.kt` should not need changes.
- Implement standard blank new text draft hiding for:
  - top-bar title/subtitle chrome;
  - accessory bar / Premium formatting chrome.
- Preserve:
  - reminder-created draft metadata visibility;
  - Details route;
  - save failure / retry visibility;
  - blank/whitespace/cleared draft discard behavior.
- Body-only notes:
  - home card still uses the first nonblank body line as the display title;
  - read page does not duplicate the first line;
  - tapping the visible first line enters body editing;
  - explicit-title notes keep current title tap/details behavior.

## Out Of Scope

- Formatting-toolbar contextual redesign.
- Data model, migrations, or storage semantics.
- Markdown checkbox renderer.
- Find core logic.
- URL parser.
- Sync, backup/restore, drawing, checklist, OCR, billing, or unrelated app surfaces.
- APK copy, commit, or push before Agent F/G gates.

## Risk Controls

- Preserve URL tap priority.
- Preserve Find highlighting and scrolling.
- Preserve checkbox raw offsets.
- Preserve formatting range offsets.
- Use existing tags and helpers where possible.
- Do not weaken tests to pass.
- `isBlankStandardNewTextDraft` must check:
  - text note;
  - `isNewDraft`;
  - `reminderAt == null`;
  - `isBlankDraftContent(...)`;
  - save failure is not hidden.

## Test Gate Expectations

Agent D must at least run:

- `git diff --check -- app/src/main/java/com/example/notepad/ui/NotepadApp.kt app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- assemble/unit gate
- focused connected `TextInputTest` methods from Agent B's plan

Required behavior gates:

- reminder draft;
- blank/whitespace/cleared draft discard;
- Find read/edit;
- explicit title tap;
- body-only first-line tap.

Before connected tests, verify emulator readiness per project `AGENTS.md`:

- ADB shows an online `device`.
- `sys.boot_completed` returns `1`.
- Use WSL/PowerShell/JBR paths for Brian's Just Notes project.
