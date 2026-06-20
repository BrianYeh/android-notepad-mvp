# Agent A 產品審核：drawing_note_enhance_v4

日期：2026-06-21

## 審核角度

用 Steve Jobs / 賈伯斯式產品眼光看 Just Notes 繪圖記事：現在不是要堆更多工具，而是要讓使用者覺得 App 懂他的手。

v2 / v3 已完成：

- 首頁 drawing note 卡片有縮圖。
- 新增空白繪圖像白紙。
- 空白草稿安全清理。
- 有筆畫的繪圖重新開啟會直接回 fullscreen canvas。
- Details 承載標題、提醒、分享 PNG、匯出 PNG。

## v4 建議

**記住上一支畫筆的顏色與粗細。**

目前 `DrawingEditorScreen` 每次進入都從：

- Pen
- Medium
- Black

開始。v3 讓使用者重新打開舊圖直接回畫布，這個固定重置就更明顯：使用者上一筆明明是紅色細筆，下一次又被重設成黑色中筆。

這不是資料安全問題，但它是「App 不懂我」的日常摩擦。

## 產品原則

- 記住使用者的畫筆手感。
- 不記住 Eraser 作為下一次預設工具，避免新空白畫布第一筆變成看不見的橡皮擦。
- 不改每則 note 的資料格式；這是全域「最後畫筆偏好」，不是 per-note 狀態。

## 驗收標準

- 使用者選 Red + Thin 畫筆後，離開再新增或重開繪圖記事，預設仍是 Pen，且 Red / Thin 被選取。
- 使用者切到 Eraser 後離開，下一次進入仍預設 Pen，不預設 Eraser。
- 已有筆畫資料不被改寫；只影響下一次工具初始狀態。
- v2 / v3 行為仍成立：
  - 空白草稿不殘留。
  - 已畫過記事重開進 fullscreen。
  - Details 可看到分享 / 匯出。
- 新增 focused instrumentation test 覆蓋偏好記憶。

## Non-goals

- 不新增筆刷、色盤、形狀、文字框、圖層。
- 不改 drawing JSON。
- 不改 PNG export。
- 不改縮圖 renderer。
- 不做 per-note 工具記憶。
- 不碰 pan / zoom / page model。
