# Just Notes 繪圖記事 v4 手動測試說明

日期：2026-06-21

## 這版主要要測什麼

這次 v4 的重點是：繪圖記事會記住上次使用的「筆」顏色與「筆」粗細，讓下一次開新的繪圖記事時可以直接延續你的筆觸偏好。

但它不會記住橡皮擦當作預設工具。也就是說，就算你最後停在橡皮擦，下一次進入繪圖記事仍然要從 Pen 開始，避免使用者打開畫布卻一開始畫不出東西。

## v4 改進重點

- 記住上次選的 Pen 顏色。
- 記住上次選的 Pen 粗細。
- 下一次新增繪圖記事時，預設仍從 Pen 開始。
- Eraser 的粗細不會覆蓋 Pen 的記憶粗細。
- 延續 v2/v3：空白繪圖草稿乾淨、已有筆畫的繪圖從首頁重新開啟會直接進 fullscreen canvas、Details 三點入口仍可回一般詳細模式。

## 建議測試 Checklist

### 1. Pen 顏色與粗細會被記住

1. 開啟 Just Notes。
2. 新增一則 Drawing note。
3. 在 fullscreen 畫布選擇 Pen。
4. 選擇 Red。
5. 選擇 Thin。
6. 返回首頁。
7. 再新增一則 Drawing note。

預期結果：

- 一進入新繪圖記事，工具是 Pen。
- Red 仍是選取狀態。
- Thin 仍是選取狀態。

### 2. 最後停在 Eraser，下次仍從 Pen 開始

1. 新增一則 Drawing note。
2. 選擇 Pen + Red + Thin。
3. 切到 Eraser。
4. 可以把 Eraser 粗細改成 Thick。
5. 返回首頁。
6. 再新增一則 Drawing note。

預期結果：

- 下次進入時工具是 Pen，不是 Eraser。
- Pen 顏色仍是 Red。
- Pen 粗細仍是 Thin。
- Eraser 的 Thick 不應該覆蓋 Pen 的 Thin。

### 3. 既有繪圖內容不受影響

1. 新增 Drawing note。
2. 用任一顏色與粗細畫幾筆。
3. 返回首頁。
4. 從首頁重新打開該繪圖記事。

預期結果：

- 已有筆畫的 drawing note 會直接進 fullscreen canvas。
- 既有筆畫仍存在。
- 上方仍顯示標題與儲存狀態。
- Details 三點按鈕仍可打開一般詳細模式。

### 4. 空白草稿安全行為仍正常

1. 新增 Drawing note。
2. 不畫任何筆畫，也不要輸入標題。
3. 返回首頁。

預期結果：

- 空白 drawing draft 會被清掉，不會留下空卡片。

## 自動化驗證摘要

- Local Gradle gate：通過
- Focused connected drawing tests：6 tests，0 failed
- Full connected Android suite：170 tests，0 failed
- Agent F code review：第二輪通過，沒有 actionable findings
