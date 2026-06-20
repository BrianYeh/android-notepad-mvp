# Agent C 計畫審核：drawing_note_enhance_v2

日期：2026-06-20

## 結論

Agent B 的範圍可以接受，但要收斂成可安全交付的版本：P0 縮圖必做；P1 先做「乾淨初始畫布」與「回詳細資料入口」，不要在全螢幕內新增完整 Details bottom sheet。這樣能改善可發現性，同時不碰提醒、資料夾、PNG 分享、Premium gate 的既有保存契約。

## 必修修正

1. **縮圖 renderer 必須和編輯器共用筆畫繪製邏輯。**  
   不要重新寫 eraser 算法。優先呼叫現有 `drawDrawingStrokes`，必要時只抽出最小 helper。

2. **空白新草稿不要隱藏錯誤。**  
   初始 blank state 可以不顯示 `未命名繪圖` / `已儲存`，但 `Saving`、`Save failed`、retry、PNG error message 一律要顯示。

3. **Details button 第一版只做 exit-to-details。**  
   不要在同一輪導入全螢幕 bottom sheet，避免 duplicate title/folder/reminder/share/export paths 破壞保存 gate。

4. **測試應鎖行為，不鎖過度視覺細節。**  
   Instrumentation 驗證 tag、可開啟、狀態是否存在 / 不存在；不要用像素級縮圖差異當唯一 gate。

5. **列表縮圖效能要有基本保護。**  
   `DrawingJson.decode` 用 `remember(note.id, note.drawingData)`，縮圖固定高度，避免在 row 裡產生大 bitmap 或檔案。

## 最小可交付範圍

- `DrawingNoteThumbnail` in `NoteRow`：
  - 有筆畫顯示 `drawing_note_thumbnail_<id>`。
  - 空白刻意保留繪圖顯示 `empty_drawing_thumbnail_<id>` 或不顯示任何 raw content；若採不顯示空白框，測試要 assert raw JSON 不存在。
- 新增空白繪圖全螢幕：
  - 初始不顯示 `未命名繪圖` 和 `drawing_note_save_status`。
  - 顯示 `drawing_fullscreen_details_button`。
  - 點擊後進一般模式並看到 title / share / export controls。
- 既有空白草稿丟棄、儲存失敗、PNG share/export 防重複測試保持綠燈。

## 驗證要求

- `git diff --check`
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest`
- focused connected tests 至少包含：
  - drawing thumbnail appears for saved stroke note
  - blank drawing initial fullscreen hides chrome and details opens normal mode
  - existing drawing save/status/share/export tests still pass
- 最後仍需要 Agent F `gpt-5.5` xhigh code review，以及 Agent G full connected suite。
