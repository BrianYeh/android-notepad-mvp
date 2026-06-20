# Agent B 實作計畫：drawing_note_enhance_v3

日期：2026-06-20

## Scope

本輪只做一個產品行為改動：

> 有筆畫的 drawing note 從列表或其他入口重新開啟時，預設進 fullscreen canvas。

不新增 bottom sheet、不改 PNG export、不改 drawing JSON schema、不改 pan/zoom/page model。

## Code plan

### 1. 調整初始 fullscreen 判斷

檔案：`app/src/main/java/com/example/notepad/ui/NotepadApp.kt`

目前 `DrawingEditorScreen` 的 `LaunchedEffect(loadedNoteId)` 只在 `title.isBlank() && strokes.isEmpty()` 時初始進 fullscreen。

改成：

- `strokes.isNotEmpty()`：開啟有筆畫 drawing note 時進 fullscreen。
- `title.isBlank() && strokes.isEmpty()`：保留 v2 空白 drawing 的 fullscreen first-paper 行為。
- `title.isNotBlank() && strokes.isEmpty()`：維持一般詳細模式，方便處理 title-only drawing。

### 2. 不改 fullscreen chrome 規則

`hidePristineChrome` 仍只針對「new blank draft 且無 user intent 且沒有 saving / failed / PNG message」。

所以有筆畫的 saved drawing 在 fullscreen 會顯示：

- title 或 `Untitled drawing`
- save status
- Details 三點按鈕
- 畫布與工具列

### 3. 更新 tests

檔案：`app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

更新或新增 focused tests：

- `drawingNoteThumbnailAppearsForSavedStrokeAndOpensNote`
  - 點有筆畫 drawing card 後應看到 `fullscreen_drawing_mode`。
  - 再點 `drawing_fullscreen_details_button` 應看到 `drawing_note_title` 與 PNG share/export controls。

- `drawingTitleAndStrokeShowSavedStatusAndPersistAfterReopen`
  - 重新開啟 saved drawing 後應先看到 fullscreen。
  - 按 Details 後確認 title/strokes 還在。

- 新增 / 保留 title-only behavior 覆蓋：
  - 建立只有 title、沒有 strokes 的 drawing note，從列表打開仍先看到 `drawing_note_title`。

## Focused validation set

- `drawingNoteThumbnailAppearsForSavedStrokeAndOpensNote`
- `drawingTitleAndStrokeShowSavedStatusAndPersistAfterReopen`
- `newBlankDrawingHardwareBackExitsFullscreenThenDeletesDraftWithoutTombstone`
- `blankDrawingInitialFullscreenIsCleanAndDetailsOpensNormalMode`
- `drawingShareExportControlsDisableWhileRenderingAndFailedSaveStopsShare`

## Out of scope

- 新 fullscreen Details sheet。
- 常駐分享 / 匯出在 fullscreen toolbar。
- 筆刷記憶。
- pan / zoom / fixed page。
- persisted thumbnail cache。
