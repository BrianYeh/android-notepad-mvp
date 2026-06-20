# Agent B 實作計畫：drawing_note_enhance_v2

日期：2026-06-20

## 目標

依 Agent A 的產品評審，v2 只做兩個高槓桿改善：

1. 首頁 / 列表的繪圖記事縮圖，讓沒有標題的草圖也能一眼辨識。
2. 新增空白繪圖的第一秒更像一張紙，並讓全螢幕模式有清楚的詳細 / 分享入口。

不新增繪圖格式、不改資料庫 schema、不做縮圖快取檔、不做畫布縮放模型重設。

## P0：繪圖記事列表縮圖

### 行為規則

- 所有使用 `NoteRow` 的列表列出 drawing note 時，若 `DrawingJson.decode(note.drawingData)` 有筆畫，顯示固定高度縮圖。
- 縮圖不可互動，點擊仍由 `note_card_<id>` 開啟 note。
- 縮圖使用現有 `drawDrawingStrokes` 的 eraser / pen 渲染邏輯，避免和編輯器、PNG 匯出不一致。
- 空白 drawing note 不顯示 raw JSON。可顯示安靜的空白預覽，或不顯示縮圖；本版建議顯示小型空白框並加 tag，方便測試刻意保留的空白繪圖。
- 不改 `noteTitle`、分享檔名、搜尋排序或資料模型。

### 主要程式觸點

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
  - `NoteRow(...)`：在文字 preview 與 reminder summary 之間插入 drawing preview。
  - 新增 `DrawingNoteThumbnail(noteId, strokes, isEmpty, text)` composable。
  - 新增可測的 tag：
    - `drawing_note_thumbnail_<id>`：有筆畫縮圖。
    - `empty_drawing_thumbnail_<id>`：刻意保留的空白繪圖。
  - 可重用 `DrawingJson.decode(note.drawingData)`。
  - 用 `Canvas` + `drawDrawingStrokes(strokes, viewportScale)`；viewport scale 可使用 `drawingViewportScale(strokes, measuredSize)`。

### 測試

- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
  - 新增 focused test：建立有筆畫但無標題的 drawing note，返回列表後 assert `drawing_note_thumbnail_<id>` 顯示且卡片可開啟。
  - 新增 existing blank drawing test 延伸：刻意保留空白 drawing note 時 assert 不顯示 raw JSON，且若實作空白框則 assert `empty_drawing_thumbnail_<id>`。
  - 搜尋 / drawing quick filter 可共用現有測試，補 assert 縮圖仍顯示即可。

## P1：乾淨的第一張紙與全螢幕詳細入口

### 行為規則

- 新增空白繪圖進入全螢幕時，如果還沒有標題、筆畫、資料夾 / 提醒 intent，不顯示：
  - `未命名繪圖`
  - `已儲存`
  - `剛剛已儲存`
- 一旦有筆畫、標題或 metadata intent，儲存狀態必須恢復顯示；失敗狀態與 retry 永遠不可隱藏。
- 全螢幕提供一個明確 `More / Details` 入口。建議先做成 icon button，點擊等同退出全螢幕回到一般模式；這比在全螢幕內塞完整 bottom sheet 風險低，也符合「詳細資料與檔案動作在一般模式」的定位。
- 保留現有系統 Back 行為：第一下退出全螢幕，第二下保存 / 丟棄 / 返回列表。
- 不改 premium reminder gate；免費使用者從一般模式點提醒仍走現有保存 / 丟棄空白草稿後進 Premium 的流程。

### 主要程式觸點

- `DrawingEditorScreen(...)` in `NotepadApp.kt`
  - 新增判斷：`isPristineNewDrawingDraft` 或等價變數。
  - 全螢幕 top strip 中，空白新草稿時隱藏 title/status column，只保留退出 / details control 和畫布。
  - `DrawingStatusLine(...)` 不要改成全域隱藏失敗；由呼叫端決定空白新草稿是否顯示。
  - 新增或調整 full-screen details button tag，例如 `drawing_fullscreen_details_button`。
  - `exitFullscreenDrawing()` 可作為 details button 的 action，並保留 `requestDrawingSave()`。

### 測試

- `newBlankDrawingHardwareBackExitsFullscreenThenDeletesDraftWithoutTombstone`：
  - 補 assert 初始全螢幕不存在 `drawing_note_save_status` 與「未命名繪圖」文字。
- 新增 focused test：
  - 新增繪圖，初始全螢幕 assert `drawing_fullscreen_details_button` 顯示。
  - 點 details 後進一般模式，看到 `drawing_note_title`、分享 / 匯出按鈕。
  - 畫第一筆後 assert `drawing_note_save_status` 可見且保存後仍可回列表。
- 保留既有 save failure test：失敗時仍顯示 `drawing_note_save_status` 和 `drawing_note_retry_save_button`。

## 實作順序

1. Agent C review 此計畫，特別檢查縮圖效能、全螢幕狀態隱藏是否會遮掉失敗。
2. Agent D 先做 P0 縮圖與測試。
3. Agent D 再做 P1 的初始空白全螢幕 chrome 隱藏與 details button。
4. Agent E 跑 local Gradle gate，若有 instrumentation flake，只修測試同步或測試資料，不改產品邏輯。
5. Agent F 用 `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review ...` 做 dedicated code review。
6. Agent G 依 Android gate 確認 emulator / ADB readiness 後跑 connected suite，通過後交付 APK 與繁中測試說明。

## 風險控制

- 縮圖 decode 在列表 composition 中發生，可能重複計算；先用 `remember(note.id, note.drawingData)` 包住 decoded strokes。
- 不要在縮圖使用 pointer input，避免和 row click / long click 衝突。
- 空白新草稿的 status 隱藏只能針對 `SaveStatus.Synced/Saved`；`Saving`、`Failed`、PNG message 必須顯示。
- 不要把 full-screen file actions 常駐放回工具列；v2 的目標是可發現但不打擾。
