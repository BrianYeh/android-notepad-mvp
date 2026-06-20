# Agent C 計畫審核：drawing_note_enhance_v4

日期：2026-06-21

## 審核結論

計畫可執行，範圍適當。

這一版應維持小切面：只做全域最後 Pen 顏色與粗細記憶，不加入新工具，不碰資料格式。

## 必守邊界

1. **不能記住 Eraser 當預設工具**
   - 下一次進入 editor 必須仍是 Pen。
   - 這避免使用者在空白畫布第一筆畫出看不見的 eraser stroke。

2. **Eraser size 不應覆蓋 Pen size preference**
   - 使用者在 Eraser 模式調整大小，只影響當下 session。
   - 下次進入仍使用最後 Pen brush size。

3. **偏好讀取必須有 fallback**
   - 避免 enum name 變更或舊 preference 值造成 crash。

4. **測試要驗證 selected semantics**
   - 不是只確認按鈕存在，而是確認 Pen / Thin / Red selected。

## 建議驗證

- `git diff --check`
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest`
- focused connected drawing preference test
- focused v2 / v3 regression drawing tests
- Agent F code review
- final full connected suite
