# Agent C - Stage 2 Plan Review

Date: 2026-06-19

Role: plan review only. Agent C did not modify app production code or test code.

## Decision

Approved with constraints.

I found no blocking issue in Agent B's plan. Agent D may implement Stage 2, provided the constraints below are treated as mandatory. If Agent D cannot satisfy the helper-test visibility, row-mode find scrolling, or mixed URL/format/find coverage constraints, implementation should pause for plan revision before app code changes are considered ready.

## Blocking Issues

None.

## Validation Against Current Code

- Data model: approved. The plan correctly keeps text-note checkbox state as markdown in `NoteEntity.textContent` and leaves rich text formatting as absolute raw-body offsets. `TextFormatRange` already uses absolute `start`/`end` values and has exclusive-end overlap logic in `app/src/main/java/com/example/notepad/data/TextFormattingJson.kt:16-23`. `NotepadRepository.saveTextNote(...)` already accepts optional `textFormattingJson` in `app/src/main/java/com/example/notepad/data/NotepadRepository.kt:115-126`.
- Existing read-mode limitation: confirmed. The current checkbox renderer is gated off when find is active, formatting exists, or auto URLs are detected in `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5091-5096`.
- Checkbox toggle/retry path: preserve exactly. `toggleReadModeCheckbox(...)` updates only the markdown marker, keeps formatting save values, and uses the existing save failure state in `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:4203-4220`. The parser/toggler currently live at `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:7950-7968`.
- URL tap priority: confirmed as required. The fallback read renderer checks `webUrlAt(...)` before entering edit mode in `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5159-5172`; the row renderer must preserve that priority for both plain text rows and checkbox labels.
- Find architecture: Agent B correctly identifies the main risk. Find matches are raw content ranges in `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:3813-3814`, while the current read-mode scroll paths assume one full-body `TextLayoutResult` in `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:4056-4082` and `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:4124-4141`.
- Formatting and link annotations: Agent B's segment-helper direction is correct. The existing helper adds formatting spans, explicit link annotations, find highlights, and auto URL annotations in `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:8109-8173`, and `webUrlAt(...)` uses the shared `WEB_URL_STRING_ANNOTATION_TAG` in `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:8266-8273`.
- Test surface: Agent B identified the right existing regression tests. Relevant current tests include `createTextNote(...)` in `app/src/androidTest/java/com/example/notepad/TextInputTest.kt:350-373`, tap-to-edit at `TextInputTest.kt:1616-1650`, URL/format helpers at `TextInputTest.kt:1653-1745`, checkbox toggle/retry at `TextInputTest.kt:1857-1909`, and read/find flows at `TextInputTest.kt:1963-2063`.

## Required Implementation Constraints For Agent D

1. Keep the data model unchanged. Do not add migrations, new note types, Checklist JSON conversion, or relative-to-label formatting ranges. Checkbox state remains markdown in text-note `textContent`.
2. Keep the Stage 2 renderer selection based on at least one markdown checkbox line in nonblank content. It must not opt out because `findQuery` is active, `formatRanges` is nonempty, or `content.webUrlRanges()` is nonempty.
3. Do not silently broaden checkbox grammar. Current production parsing recognizes markers with the trailing marker space (`- [ ] `, `- [x] `, `- [X] `). If Agent D decides to support marker-only lines without the trailing space, that is a product-scope change and must come with parser/toggler tests.
4. Helper tests must be real tests of the offset contract. If helper tests stay in `TextInputTest.kt`, the helpers they exercise cannot remain `private` in `NotepadApp.kt`; expose a minimal top-level API as already done for `findHighlightedLinkedText(...)`, or move the pure helpers into a JVM-testable file and add JVM tests.
5. Use half-open ranges internally (`start` and `endExclusive`). Avoid storing inclusive `IntRange` values for exclusive ends except where existing APIs require `until`. Sanitize formatting against full content length before cropping to a visible segment.
6. Cropping must preserve visible overlap when a formatting range spans the hidden checkbox marker and label. Hidden marker-only formatting should not render, but the label portion of a spanning range must render.
7. Segment annotation order must preserve current behavior: explicit formatting/link annotations first, global find highlights using the normalized global active match index, and auto URL annotations last while still using `WEB_URL_STRING_ANNOTATION_TAG`.
8. Segment tap handling must always check `AnnotatedString.webUrlAt(localOffset)` before edit mode. A URL tap should not open the editor. A non-URL tap must enter edit mode at the mapped absolute raw-body offset. Checkbox control taps must not fall through to label/body edit.
9. Render blank and trailing blank lines with stable height and a tap target, not as zero-height lost content. Blank-line taps should map to a sensible raw offset, usually that line start/end, and enter edit mode.
10. The line model used by rendering must stay line-index compatible with `toggleMarkdownCheckboxLine(...)`, or the toggler must be rewritten to use the same parsed line model. Do not introduce a mismatch where `text_note_read_checkbox_<lineIndex>` toggles a different source line.
11. Row-mode find scrolling must update both paths: the immediate `selectFindMatch(...)` scroll path and the read-mode `LaunchedEffect` scroll path. Updating only the effect leaves next/previous navigation dependent on timing and stale layout.
12. Do not use stale full-body `readContentLayout` while row mode is active. Clear it or guard every read-mode scroll/edit call with the current renderer mode.
13. Compose row layout state must actually trigger scrolling after layout updates. A stable `mutableStateMapOf` object in a `LaunchedEffect` key is not enough by itself. Use an explicit layout version, immutable snapshot key, or `snapshotFlow` so newly recorded row/text positions can drive the active find match into view.
14. Track the top/bottom of the actual `Text` composable whose `TextLayoutResult` is used for glyph boxes. For checkbox labels, row top is acceptable only as the fallback for marker-only matches; exact label matches need the label text origin, not just the row origin.
15. Clear row layout/position maps when content, parsed lines, renderer mode, or note id changes so removed lines do not leave stale positions.
16. Preserve retry behavior and formatting stability during checkbox toggles. The toggle is length-preserving for current markers, so existing absolute formatting ranges should remain valid; do not run unrelated formatting adjustment logic during a toggle.
17. Find status and navigation must continue to use raw-body matches. Marker-only matches may scroll to the row without a visible highlight, but label/plain-line matches must highlight visible glyphs and next/previous must keep the active match visible enough to satisfy instrumentation.
18. Keep scope tight to Stage 2. Do not disturb Stage 1 behavior, unrelated untracked docs/logs, payment docs, or older text-note enhancement folders.

## Required Test Additions Or Adjustments

Agent B's test plan is directionally sufficient, with these additions/clarifications:

- Add helper coverage for manual line parsing, including trailing newline and multiple blank lines.
- Add helper coverage for formatting that starts in the hidden marker and ends in the visible label.
- Add helper coverage for explicit link formatting on a checkbox label and auto URL detection in a separate segment. Assert `webUrlAt(...)` at local offsets.
- Add helper coverage for active find index based on global raw-body match order, including a match before the segment.
- Add helper coverage for marker-only find matches on checkbox lines. The expected result should be row-scroll fallback and no visible fragment.
- Add instrumentation for a mixed note containing plain lines, blank lines, at least one checkbox, a URL, and trailing text. Assert the checkbox remains rendered and toggles only the marker.
- Add instrumentation for active find while row mode is rendered. It must assert status, checkbox visibility during find, and scroll movement for an off-screen row-mode match.
- Add instrumentation or helper-level coverage for mixed checkbox plus formatting. If UI visual spans are hard to assert, assert the segment annotated text spans and verify the checkbox renderer still selected when `textFormattingJson` is nonempty.
- Add tap-to-edit coverage for checkbox label text. If URL launching is too flaky to assert externally, at minimum assert through helper coverage that the URL annotation exists and structure row tap code so URL and non-URL paths share the same URL-first handler.
- Add uppercase `- [X] ` toggle coverage to confirm it renders checked and toggles to `- [ ] `.
- If `createTextNote(...)` is extended with optional `textFormattingJson`, keep the new argument optional and preserve all current call sites.
- If helper tests are added only to `TextInputTest.kt`, remember they run with `connectedDebugAndroidTest`; `testDebugUnitTest` will not validate them unless Agent D also creates JVM tests.

## Validation Gate

Agent B's validation commands are acceptable. Agent D must start with `git status --short --branch`, verify emulator readiness before any connected/UI/instrumentation claims, run focused build and instrumentation for the changed surface, and run `git diff --check`.

After Agent D modifies app or test code, Agent F must perform the required Just Notes code review with:

```bash
codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review ...
```

Agent F is review-only and should not modify files.
