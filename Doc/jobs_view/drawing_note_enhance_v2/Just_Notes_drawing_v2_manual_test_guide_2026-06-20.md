# Just Notes 繪圖記事 v2 手動測試說明

日期：2026-06-20

## 本版改進重點

1. 首頁繪圖記事卡片新增縮圖預覽。
   - 有筆跡的繪圖記事會在列表卡片中顯示白底縮圖。
   - 點卡片仍會正常開啟該繪圖記事。

2. 新增空白繪圖記事時，第一眼更像一張空白畫布。
   - 新增繪圖記事後，先進入 fullscreen canvas。
   - 空白狀態不顯示 `Untitled drawing`。
   - 空白狀態不顯示儲存狀態文字。
   - 仍保留離開 fullscreen 的按鈕與工具列，方便開始畫圖。

3. fullscreen 畫布新增「Details」入口。
   - 右上角三點按鈕可回到一般詳細編輯模式。
   - 一般模式中仍可輸入標題、移動資料夾、設定提醒、分享 PNG、匯出 PNG。

4. 保留既有安全行為。
   - 空白新繪圖按返回會刪掉草稿，不留下垃圾卡片。
   - 畫過線、輸入標題、改 metadata 的繪圖記事仍會保留。
   - 儲存失敗、匯出/分享 PNG、Premium reminder gate 的狀態不被隱藏。

## 建議手動測試 checklist

### A. 空白繪圖記事第一眼

1. 安裝本次交付的 `app-debug.apk`。
2. 開啟 Just Notes。
3. 點新增選單，選擇 Drawing note / 繪圖記事。
4. 確認畫面直接進入 fullscreen 畫布。
5. 確認畫面上沒有 `Untitled drawing`。
6. 確認畫面上沒有 `Saved` / `Saving` 類儲存狀態。
7. 確認仍看得到畫筆工具列與右上角三點 Details 按鈕。

### B. Details 入口

1. 在空白 fullscreen 畫布點右上角三點。
2. 確認回到一般繪圖記事詳細模式。
3. 確認可以看到標題欄、分享 PNG、匯出 PNG、資料夾/提醒等既有控制。
4. 不畫任何東西，按返回。
5. 回首頁後確認沒有留下空白繪圖卡片。

### C. 繪圖縮圖

1. 新增一張繪圖記事。
2. 畫一小段線。
3. 返回首頁。
4. 確認該繪圖記事卡片中有白底縮圖預覽。
5. 點該卡片。
6. 確認可以正常回到繪圖記事，而且筆跡仍在。

### D. 有內容的草稿保留

1. 新增繪圖記事。
2. 畫一段線後直接按返回。
3. 回首頁確認這張繪圖記事仍存在。
4. 再打開確認筆跡還在。

### E. 只有標題或 metadata 的草稿保留

1. 新增繪圖記事後進 Details。
2. 只輸入標題，不畫線。
3. 返回首頁，確認該記事仍保留。
4. 也可測試只改資料夾或提醒，確認返回後仍保留。

### F. Share / Export PNG

1. 開啟有筆跡的繪圖記事。
2. 進 Details。
3. 測試分享 PNG 與匯出 PNG 入口仍存在。
4. 若匯出中，按鈕應避免重複觸發；失敗時不應卡住永久 disabled。

## 自動化驗證結果

- `git diff --check`：通過
- `testDebugUnitTest` / `assembleDebug` / `assembleDebugAndroidTest`：通過
- focused connected tests：5 tests，0 failed
- final `connectedDebugAndroidTest`：168 tests，0 failed，BUILD SUCCESSFUL in 8m 26s

## 交付檔案

- APK：`G:\我的雲端硬碟\01_android_app\01_note_app\app-debug.apk`
- 本測試說明：`G:\我的雲端硬碟\01_android_app\01_note_app\Just_Notes_drawing_v2_manual_test_guide_2026-06-20.md`
- APK SHA256：`e829e5233052d12cd2b40167b02a9542b72b391b2a672850933652434e9bb738`
