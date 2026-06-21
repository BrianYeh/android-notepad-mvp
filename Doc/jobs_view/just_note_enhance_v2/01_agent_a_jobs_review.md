# Agent A：Just Notes v2 賈伯斯眼光功能評審

日期：2026-06-21
範圍：檢視目前 Just Notes 功能與既有專案脈絡；本輪不修改 App 程式碼。

## 一句話結論

v2 最該優先處理的不是文字或繪圖，因為 text_note / drawing_note v3、v4 已經把主要摩擦修掉很多。下一個最刺眼、最小而關鍵的產品破口，是 **Premium 尚未可購買時的頁面仍像壞掉的商店**：沒有價格與購買按鈕時，還露出還原購買、狀態、錯誤／準備中文案、法律連結等 chrome。

一個還沒準備好收錢的頁面，應該像高級功能預覽，不應該像結帳失敗。

## 現況觀察

- 文字記事已經接近「打開就寫」：主 FAB 直接新增文字記事，空白草稿更乾淨，閱讀模式可點內容編輯，Find / checkbox / 格式化基礎已穩定。
- 繪圖記事已經完成近期重點：首頁縮圖、空白草稿清理、全螢幕畫布、PNG 分享／匯出、清除確認、記住上一支筆。
- 免費日常寫作不再需要先理解 Premium；這是好的。
- Premium gate 仍出現在資料夾、文字格式、提醒／日曆等重要路徑，所以 Premium 頁會被使用者實際看見。
- 目前 `PremiumScreen` 已會在價格不可用時隱藏 plan rows 與訂閱按鈕，但仍固定顯示：
  - `premium_restore_button`
  - `premiumStatusText(...)`
  - `billingState.lastError ?: premiumDetailText(...)`
  - Privacy Policy / Terms of Service 文字列
  - Premium features 區塊
- `UiText` 仍有「價格尚未開放」、「訂閱（準備中）」、「目前未設定試用或優惠方案」、「價格會在 Google Play 商品設定完成後顯示；正式解鎖需要後端驗證」等工程狀態感文案。

## 最大產品問題

### 1. Premium 頁第一印象像故障，不像高級

使用者點到 Premium，是因為他想知道「多付錢會得到什麼」。如果畫面出現還原購買、狀態、準備中、法律文字，情緒會從期待變成懷疑。

### 2. 還原購買在 Billing 不可用時沒有產品意義

當價格與購買都不可用時，「更新購買狀態」像一個可以救回什麼的按鈕，但多半只會讓使用者再遇到不可用狀態。這是互動噪音。

### 3. 法律連結現在只是未完成感

Privacy / Terms 以底線文字出現，但沒有成為真正可信的購買揭露。當商店尚未可用時，它們不是信任訊號，反而像模板殘留。

### 4. 工程狀態不該出現在消費者第一層

「Google Play 商品設定」、「後端驗證」、「Billing 未連接」對開發正確，對使用者不優雅。Premium 頁要先賣價值，不要先暴露施工現場。

### 5. 這是 v2 最好的單點

資料安全與同步整理也重要，但範圍較大。Premium fallback 是更小、更可驗收、也尚未被 text/drawing v3/v4 完成的破口；它會直接改善所有 Premium gate 的落地感。

## v2 單一優先增強

**把 Premium unavailable 狀態改成「乾淨的高級功能預覽」。**

當 Google Play Billing 不可用、或沒有任何可顯示價格時，Premium 頁只應呈現：

- 一句清楚標題：Premium 尚未開放，或進階功能正在準備中。
- 一句產品語氣說明：目前可以繼續使用免費記事；進階功能開放後會提供資料夾、文字格式、提醒／日曆。
- 三個功能價值區塊：
  - 資料夾
  - 文字格式
  - 提醒／日曆工具
- 返回記事的自然路徑。

不要在 unavailable 狀態顯示還原購買、價格不可用、訂閱準備中、錯誤堆疊、後端驗證、Privacy / Terms chrome。

## 驗收標準

- 當 `showCommerceUi == false` 時，不顯示：
  - `premium_subscribe_button`
  - `annual_plan_option`
  - `monthly_plan_option`
  - `premium_restore_button`
  - 「價格尚未開放」
  - 「訂閱（準備中）」
  - 「目前未設定試用或優惠方案」
  - 「Google Play Billing」
  - 「後端驗證」
  - Privacy Policy / Terms of Service 列
- unavailable 狀態仍清楚顯示三個 Premium benefits，且畫面語氣像產品預覽，不像錯誤頁。
- 若 `billingState.hasPremiumAccess == true`，不可把使用者降級成 unavailable；必須仍顯示 Premium active 或等價狀態。
- 當至少一個價格可用時，現有 commerce flow 才顯示 plan rows、Subscribe、Restore、必要法律揭露。
- Billing 錯誤可以保留給 debug log 或非首屏診斷，但不可在 unavailable 的一般使用者主畫面當主要內容。
- 中英文文案都要自然；Traditional Chinese 不要出現工程語氣或未完成模板感。
- 補 focused UI test：
  - Billing unavailable：只看到 benefits preview，不看到 restore / subscribe / plan / legal / setup 文案。
  - Billing available with price：看到 plan、subscribe、restore 與必要揭露。
  - Already premium：即使價格暫時不可用，也仍看到 active entitlement。

## 後續 Agent 應避免

- 不要在本輪實作 Play Console catalog、Billing Library 升級、後端驗證或 RTDN。
- 不要重做整個 Premium 商業模式；這輪只修 unavailable fallback 的品味與信任。
- 不要把工程錯誤、商品設定狀態、後端驗證狀態寫給一般使用者看。
- 不要用灰色 disabled button 代表「未來可購買」；沒有可購買商品時就不要露出購買 chrome。
- 不要碰 text_note / drawing_note v3、v4 已完成的體驗。
- 不要為了清爽而隱藏真正已啟用的 Premium entitlement。
- 不要新增 AI、模板、圖示包、主題或其他高級功能展示；只保留目前已規劃且已被 gate 使用的三個 benefits。

## Agent B 起手建議

先在 `PremiumScreen` 明確分出三種狀態：

- active entitlement
- commerce ready
- commerce unavailable

Agent B 應規劃最小 UI 分支與測試，不碰 billing backend。v2 成功的畫面應該讓使用者覺得：「這個 App 還沒開始收費，但它知道自己在做什麼。」
