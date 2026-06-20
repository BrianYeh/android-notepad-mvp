# Agent D 實作報告：drawing_note_enhance_v3

日期：2026-06-20

## 完成內容

1. **有筆畫 drawing note 重新開啟預設進 fullscreen**
   - `DrawingEditorScreen` 初始 focus 判斷改為：
     - 有 strokes：進 `fullscreen_drawing_mode`
     - title blank：維持 v2 的空白 first-paper fullscreen 行為
     - title-only 且無 strokes：維持一般詳細模式

2. **保留 v2 的乾淨空白畫布規則**
   - 新空白 drawing 仍不顯示 `Untitled drawing` 與 save status。
   - 有筆畫 drawing 在 fullscreen 不套用 pristine hide chrome，所以 title / status / Details 仍可見。

3. **測試同步更新**
   - `drawingNoteThumbnailAppearsForSavedStrokeAndOpensNote`
     - 點有筆畫 drawing card 後先 assert fullscreen。
     - 點 Details 後確認 title、share PNG、export PNG controls 可見。
   - `drawingTitleAndStrokeShowSavedStatusAndPersistAfterReopen`
     - saved drawing 重新開啟後先 assert fullscreen，再點 Details 驗證 title / strokes。
   - 新增 `titleOnlyDrawingReopensInDetailsMode`
     - 有標題但無筆畫的 drawing note 重新開啟仍先進一般詳細模式。

## 修改檔案

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

## Out of scope

- 沒有新增 fullscreen bottom sheet。
- 沒有改 drawing JSON / PNG export / thumbnail renderer。
- 沒有改 pan / zoom / page model。
