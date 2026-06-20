# Agent A 產品審核：drawing_note_enhance_v3

日期：2026-06-20

## Current state

v2 已把繪圖記事從「資料列功能」推近「產品功能」：

- 首頁 drawing note 卡片有縮圖。
- 新增空白繪圖直接進 fullscreen canvas。
- 空白新草稿不誤留。
- fullscreen 有 Details 入口可回一般模式。
- 儲存、PNG、清除與競態已有測試保護。

目前最大不一致：已有筆畫的繪圖記事從列表打開時仍先進一般詳細模式；真正的畫布反而不是第一畫面。

## Jobs-style critique

繪圖記事的主角是那張圖，不是標題、資料夾、提醒或狀態列。v2 讓「建立」像一張紙，但「重新打開」仍像進一張表單。

這會削弱縮圖帶來的期待：使用者在首頁看到圖、點進去，理應立刻回到圖。

## Ranked opportunities

1. **P0：已保存繪圖預設開啟 fullscreen 畫布**
   - 小改動，高感知。
   - 讓 drawing note 像 drawing note，而不是 metadata form。

2. **P1：讓 fullscreen Details 更像「詳細/分享」入口**
   - 改善發現性，但不要新增第二套分享/匯出流程。

3. **P2：記住上次筆刷 / 顏色**
   - 實用，但不是最大品味缺口。

4. **P3：固定頁面、平移/縮放、避免自動縮放跳動**
   - 重要但不是小改，牽涉座標、手勢、匯出與既有測試，應另開大版。

## Recommended v3 direction

做「繪圖記事重新開啟即回到畫布」：

- 有筆畫的 drawing note 從首頁、搜尋、篩選、提醒列表開啟時，預設進 `fullscreen_drawing_mode`。
- fullscreen top strip 顯示標題 / 儲存狀態與 Details 按鈕。
- 命名、資料夾、提醒、分享 PNG、匯出 PNG 仍在一般詳細模式。
- 新空白草稿與 title-only drawing note 維持現狀。
- 不碰 schema、renderer、PNG、pan/zoom。

## Acceptance criteria

- 有筆畫繪圖從列表開啟後先看到 fullscreen 畫布，而不是 `drawing_note_title`。
- 點 `drawing_fullscreen_details_button` 後回一般模式，能看到標題、分享 PNG、匯出 PNG。
- 系統 Back 第一下一律只退出 fullscreen；第二下才保存 / 返回或套用空白草稿清理。
- 新空白繪圖第一秒仍不顯示 `Untitled drawing` 與儲存狀態。
- 既有空白繪圖不被刪；title-only 繪圖仍可進詳細模式處理。
- 更新 focused instrumentation tests，並保留 v2 的儲存、PNG、Premium gate 測試。

## Risks

- 想直接改標題或分享的使用者多一個 Details tap；用清楚的 Details 按鈕降低成本。
- 既有測試可能假設開啟 saved drawing 後看到 `drawing_note_title`，需要同步改測試期待。
- 不要順手做 fullscreen bottom sheet 或 pan/zoom；那會把 v3 從小改變成畫布模型重設。
