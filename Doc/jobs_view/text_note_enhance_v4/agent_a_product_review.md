# Agent A - Steve Jobs-Style Text Note Review V4

Date: 2026-06-20
Workspace: `D:\AndroidStudioProjects`
Reviewer: Agent A using Codex `gpt-5.5` with `model_reasoning_effort="xhigh"`

## Role

Review the Just Notes text-note functionality from a Steve Jobs / product simplicity lens: ruthless simplicity, focus, emotional clarity, obvious first action, reduction of chrome, delightful defaults, trust, and end-to-end user experience.

Agent A made no code changes and did not run builds.

## Product Verdict

Just Notes text notes have moved from a feature page toward something people can actually use to write: creation is fast, reading is cleaner, saving is reliable, and mixed markdown checkbox content is more trustworthy. V4 should not add more features. It should remove the remaining moments where the user feels they are operating an app instead of simply writing, reading, and finding text.

## What Is Already Good After V2/V3

- The main FAB can create a text note directly and focus the body, making the first action clearer.
- Blank drafts already hide the title/metadata form, which supports the body-first direction.
- Read mode has less routine metadata noise while still preserving important reminder and save-failure visibility.
- Read mode can enter editing from body taps, and Find works across read/edit modes.
- Markdown checkbox behavior in mixed text, links, formatting, and find states is more complete, with CRLF/format-offset risk covered.

## 1. Make Blank New Notes Feel More Like Blank Paper

### User Problem

A blank draft is already body-focused, but it can still show status/chrome such as an untitled note label, saved state, checkbox/bullet tools, and Premium formatting affordances before the user has written anything. The first moment still feels like a UI instead of a sheet of paper.

### Desired Experience

After tapping New Text Note, the first signal should be only: start writing. A blank draft that will be discarded should not claim to be saved.

### Minimal Implementation Idea

Reuse the existing blank-draft detection. For a standard blank new text draft, hide top-bar derived title/save subtitle and defer the accessory bar until the user has typed content or explicitly opens tools. Do not hide reminder-draft metadata, save-failure state, or the Details route.

### Acceptance Criteria

- A new blank text note focuses the body.
- The blank draft does not show "Untitled text note", "Saved", metadata card, compact metadata, or Premium lock/chrome.
- After typing, save status and relevant editor controls return normally.
- Reminder-created text drafts still show reminder information.
- Blank, whitespace-only, and cleared new drafts still discard cleanly.

### Risk / Deferral Notes

If Brian wants to keep Premium exposure visible, start by removing only blank-draft status/title chrome and defer accessory-bar changes.

## 2. Body-Only Notes Should Show The First Line Only Once

### User Problem

Notes without explicit titles use the first body line as their display title. In read mode that first line can still appear again in the body, and tapping the visible title can open an empty title field. This makes users feel like the text they tapped disappeared.

### Desired Experience

When the first body line is acting as the natural title, the read page should present it once. Tapping that line should edit that body line. Only notes with a real custom title should route title taps to title/details editing.

### Minimal Implementation Idea

When `note.title` is blank and the displayed title is derived from the body, avoid rendering a separate read-title copy. Either render the first body line as part of the body with title-like styling, or omit the independent read-title and keep the body as the source of truth. Route taps on the derived first line to body edit offset instead of the title field. Do not change the data model.

### Acceptance Criteria

- A body-only note still uses the first nonblank body line as the home-card title.
- The read page does not duplicate that first body line.
- Tapping the first line enters body edit mode with the cursor near that first line.
- The database title remains an empty string for body-only notes.
- Notes with explicit titles keep the existing title/details behavior.

### Risk / Deferral Notes

If the first line gets special read styling, Agent B/D must protect Find, formatting ranges, checkbox offsets, and URL priority. Stage 1 can choose the lower-risk "do not render independent title" approach.

## 3. Make Formatting Tools Appear By Context

### User Problem

The writing toolbar can feel like a small word processor. For free users, a persistent Premium formatting affordance can appear beside simple writing tools and weaken focus.

### Desired Experience

Default tools should serve fast writing: checkbox, bullet, hide keyboard. Formatting should appear when the user selects text or explicitly opens formatting.

### Minimal Implementation Idea

Keep the existing formatting model. Collapse the full formatting controls behind a single formatting entry or selection-aware expansion. For non-Premium users, do not keep a lock in the first writing layer; route to Premium only when the user asks for formatting.

### Acceptance Criteria

- The default writing toolbar does not require horizontal scrolling to reach basic tools.
- Premium formatting still applies and persists.
- Free users can still discover Premium formatting, but it does not interrupt first-layer writing.
- Existing icon semantics/raw-label tests are updated without regressing accessibility.

### Risk / Deferral Notes

This may reduce Premium feature visibility. It should follow suggestions 1 and 2, not lead the first implementation slice.

## Recommended Stage 1 Scope For Agent B

Agent B should plan only:

- Suggestion 1: blank-draft chrome/trust cleanup.
- Suggestion 2: body-only first-line single presentation and correct body-edit tap behavior.

Agent B should not plan a Stage 1 rewrite of the formatting toolbar, data model, checkbox renderer, sync/backup, or unrelated app surfaces. Tests should focus on:

- `newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode`
- `bodyOnlyTextNoteUsesFirstContentLineAsTitle`
- blank/whitespace/cleared draft discard behavior
- reminder-created drafts
- Find and tap-to-edit regressions
