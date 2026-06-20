# Agent B 實作計畫：drawing_note_enhance_v4

日期：2026-06-21

## 目標

讓 drawing editor 記住上一支 Pen 的顏色與粗細，下一次進入繪圖記事時直接套用。

## 實作範圍

### 1. 新增本機偏好讀寫

在 UI 層新增小型 helper，使用 app 既有 SharedPreferences：

- `last_drawing_pen_color`
- `last_drawing_pen_brush_size`

儲存 enum name，讀取時安全 fallback：

- color fallback：`Black`
- brush size fallback：`Medium`

### 2. 初始化 drawing editor 工具狀態

`DrawingEditorScreen` 初始狀態：

- `selectedTool` 仍固定是 `Pen`
- `selectedBrushSize` 從最後 Pen brush size preference 讀取
- `selectedColor` 從最後 Pen color preference 讀取

### 3. 更新偏好時機

- 使用者在 Pen 模式選 brush size：寫入最後 Pen brush size。
- 使用者選 color：寫入最後 Pen color。
- 使用者切到 Eraser：不寫入預設 tool。
- 使用者在 Eraser 模式改 brush size：不覆蓋最後 Pen brush size。

### 4. 測試

新增 focused instrumentation test：

- 開啟 drawing editor。
- 選 Red + Thin。
- 切到 Eraser 後返回首頁。
- 再新增 drawing note。
- Assert：
  - `drawing_tool_Pen` selected
  - `drawing_brush_Thin` selected
  - `drawing_color_Red` selected
  - `drawing_tool_Eraser` not selected

## 風險控管

- 不改 note schema。
- 不改 drawing data。
- 不影響 save / discard / export。
- 保留 v2 / v3 connected tests。
