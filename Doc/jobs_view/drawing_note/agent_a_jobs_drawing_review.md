# Agent A Jobs-Style Drawing Note Product Review

1. **Stop keeping accidental blank drawing notes.**

   **User-facing problem:** Tapping New Drawing Note immediately creates a database row, opens a blank full-screen canvas, and Back/Done can leave an "Untitled drawing" even if the user never drew or named anything.

   **Why it matters:** A notes app earns trust by keeping only things the user meant to keep. Blank drawings create list clutter, make the app feel careless, and punish curiosity.

   **Concrete expected behavior:** A brand-new drawing draft with no title, no strokes, and no user-changed metadata should disappear when the user leaves. The moment the user draws a stroke, enters a title, changes folder/reminder, pins, or otherwise adds intent, the note should remain. Existing blank drawings from restore/import should not be deleted just because they are opened and closed.

   **Likely code areas to touch:** Add drawing-specific blank-draft cleanup alongside the text draft cleanup in `NotepadViewModel` and `NotepadRepository` (`app/src/main/java/com/example/notepad/viewmodel/NotepadViewModel.kt:641`, `app/src/main/java/com/example/notepad/data/NotepadRepository.kt:198`). Wire `DrawingEditorScreen` exit paths (`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5837`, `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5972`) to discard only eligible new drawing drafts. Reuse or extend DAO guard logic similar to `deleteBlankLocalTextDraftNote` (`app/src/main/java/com/example/notepad/data/NotepadDao.kt:129`).

   **Suggested validation/tests:** Add instrumentation coverage mirroring the text draft tests: create drawing then leave with no title/strokes and assert note ids return to the previous set; create drawing with a title and assert it remains; create drawing with a stroke and assert it remains; open an existing blank drawing row and assert it is not removed unless it is known to be a new draft.

2. **Replace the scrolling text-pill toolbar with a focused drawing toolbar.**

   **User-facing problem:** The current toolbar is three horizontal rows of text buttons/chips. Important controls are off-screen or clipped on phone width, color choices are words instead of colors, and every action has the same visual weight.

   **Why it matters:** Drawing should feel immediate. When undo, eraser, size, color, share, and export compete as scrolling labels, the feature feels like a settings panel instead of a pencil.

   **Concrete expected behavior:** Use a compact, stable toolbar: icon buttons for undo/redo/clear/share/export/full-screen, a segmented pen/eraser control, visual size controls, and actual color swatches. Keep destructive clear visually distinct and confirmed. Make primary controls visible without horizontal hunting on a 360 dp wide device. In full-screen mode, either keep share/export in a More menu or deliberately hide file actions behind an obvious Done-to-details flow, not an invisible omission.

   **Likely code areas to touch:** Rework `DrawingToolBar` and `DrawingCanvasWithFullscreenEntry` (`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:6187`, `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:6317`). Add missing content descriptions and short labels in `UiText` (`app/src/main/java/com/example/notepad/ui/UiText.kt:139`). Use the existing Material icon dependency and imports already present in the app (`app/build.gradle.kts:59`, `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:51`). Preserve existing test tags or replace them with clearer stable tags.

   **Suggested validation/tests:** Add Compose/instrumentation assertions that all primary toolbar controls are reachable and visible at a narrow viewport. Add screenshot review for normal and full-screen drawing modes. Assert selected pen/eraser, size, and color state is exposed via semantics and that long labels like "Pen size: Medium" are no longer clipped because they are no longer primary visible labels.

3. **Make saving and PNG work visibly trustworthy.**

   **User-facing problem:** Drawing saves happen silently after title edits, strokes, undo, redo, clear, and Back. Share/export render in the background, but the user only gets a toast after success or failure and no persistent confidence that the latest stroke is saved.

   **Why it matters:** Trust is the product. A user who sketches something important should never wonder whether the last stroke is stored, whether leaving is safe, or whether the PNG includes the current drawing.

   **Concrete expected behavior:** Show a small drawing-editor status such as "Saving...", "Saved", and "Last updated" in the top bar or footer, matching the text-note trust model. While rendering a PNG, disable duplicate share/export taps and show an inline busy state. If export is canceled, clear pending bytes quietly. If rendering or file writing fails, keep the note intact and show an actionable inline retry or clear message instead of a transient-only toast.

   **Likely code areas to touch:** Add drawing save status state to `DrawingEditorScreen` around the current save calls (`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5830`, `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5927`, `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5949`). Consider returning or observing save status from `NotepadViewModel.saveDrawingNote` / `saveDrawingNoteNow` (`app/src/main/java/com/example/notepad/viewmodel/NotepadViewModel.kt:585`). Improve share/export pending state around `pendingPngBytes`, `shareCurrentDrawingPng`, and `exportCurrentDrawingPng` (`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5794`, `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5878`).

   **Suggested validation/tests:** Add tests that title/stroke changes surface a saving then saved state; share/export buttons cannot be double-tapped while rendering; canceling the Android document picker clears pending export bytes; export/share uses the latest stroke data after undo/redo/clear.

4. **Give drawing notes a visual memory in the notes list.**

   **User-facing problem:** Drawing notes in the list are essentially just a title, metadata, and a type chip. Untitled drawings all look alike, and there is no visual payoff after making a sketch.

   **Why it matters:** Drawings are visual notes. A user should recognize a sketch at a glance instead of opening several "Untitled drawing" cards to find the right one.

   **Concrete expected behavior:** Show a small, lightweight drawing thumbnail or stroke-preview strip in `NoteRow` for drawing notes with strokes. For empty drawings, show a quiet "Empty drawing" state only if the note is intentionally kept. The preview should be non-interactive, fast, and never expose raw drawing JSON. It should respect the same eraser rendering behavior as the editor/export.

   **Likely code areas to touch:** Extend `NoteRow` and `notePreview` behavior for `NoteTypes.DRAWING` (`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:3478`, `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:7077`). Decode strokes with `DrawingJson` (`app/src/main/java/com/example/notepad/data/DrawingJson.kt:50`) and render a small Compose preview using the existing drawing stroke renderer logic near `drawDrawingStrokes` (`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:6509`) or a shared preview renderer. Keep PNG rendering in `DrawingPng` aligned if shared code is extracted (`app/src/main/java/com/example/notepad/data/DrawingPng.kt:17`).

   **Suggested validation/tests:** Add instrumentation that seeds a drawing note with stroke JSON and asserts a thumbnail/test tag appears in the list; assert empty drawings do not show raw JSON or misleading content text; add screenshot validation for list rows with text, checklist, drawing-with-preview, and drawing-empty states.

5. **Make the canvas space predictable instead of silently shrinking drawings.**

   **User-facing problem:** The canvas auto-fits saved strokes by scaling them down when they exceed the measured canvas. A user can draw near the edge, lift their finger, and see the entire drawing reframe smaller without an explicit zoom or page model.

   **Why it matters:** Handwriting depends on spatial confidence. Silent rescaling feels like the paper moved under the pen, and export dimensions can diverge from what the user thought they made.

   **Concrete expected behavior:** Pick one simple model and make it visible: either a fixed page with clear bounds and scroll/pan for overflow, or an explicit zoomable canvas with a zoom indicator and reset. Do not silently rescale content immediately after a stroke. PNG export should match the visible page/zoom model and preserve expected margins.

   **Likely code areas to touch:** Revisit `DrawingCanvas` viewport handling (`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:6369`), `drawingViewportScale` and bounds helpers (`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:6443`, `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:6471`), and `drawingExportCanvasSizePx` (`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:6488`). Update `DrawingPng.renderDrawingPng` if export needs a shared transform/margin contract (`app/src/main/java/com/example/notepad/data/DrawingPng.kt:17`).

   **Suggested validation/tests:** Add unit tests for the chosen canvas contract, especially tall/wide drawings, eraser-only strokes, and export bounds. Add an instrumentation path that draws beyond the initial visible area and confirms the visible scale does not change unexpectedly after finger-up. Compare a saved screenshot/PNG dimensions against the expected page or zoom behavior.
