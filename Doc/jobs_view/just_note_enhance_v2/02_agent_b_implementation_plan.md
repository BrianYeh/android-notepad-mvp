# Agent B：Premium unavailable benefits preview 實作計畫

日期：2026-06-21
範圍：只規劃 Premium unavailable fallback；不實作 Play Console、Billing backend、RTDN 或新 Premium 功能。

## 目標

把目前「無價格／不可購買」時仍像壞掉商店的 Premium 頁，改成乾淨的進階功能預覽。使用者應看見 app 正在準備進階版、免費記事可繼續使用、未來會提供資料夾、文字格式、提醒／日曆工具；不應看見還原購買、訂閱準備中、工程錯誤、Google Play／後端驗證或法律 chrome。

## UI 狀態切分

建議在 `app/src/main/java/com/example/notepad/ui/NotepadApp.kt` 明確新增小型狀態：

```kotlin
private enum class PremiumUiMode {
    ActiveEntitlement,
    CommerceReady,
    PreviewUnavailable,
}
```

判斷順序必須固定：

1. `billingState.hasPremiumAccess == true` -> `ActiveEntitlement`
2. `billingState.billingAvailable && (billingState.annualPrice != null || billingState.monthlyPrice != null)` -> `CommerceReady`
3. 其他 -> `PreviewUnavailable`

各狀態畫面規則：

- `ActiveEntitlement`：顯示 `premiumActive` 或等價啟用狀態與三個 benefits；不可因價格暫時不可用而落入 preview unavailable。購買按鈕與方案列不應成為主內容。
- `CommerceReady`：只在至少一個價格可用時顯示 `annual_plan_option` / `monthly_plan_option`、`premium_subscribe_button`、`premium_restore_button`、Privacy / Terms 與必要揭露；方案列只顯示有價格的方案。
- `PreviewUnavailable`：顯示一個產品語氣標題與說明，例如「進階功能正在準備中」「你可以繼續免費使用記事；進階版開放後會提供資料夾、文字格式、提醒／日曆工具。」；保留三個 benefits 和返回記事路徑；隱藏所有 commerce chrome 與工程診斷文字。

## likely code files

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
  - 在 `PremiumScreen` 內或旁邊加入 `PremiumUiMode` 與 `premiumUiMode(billingState)`。
  - 將目前的 `showCommerceUi` 改為 `mode == CommerceReady`，並讓 `ActiveEntitlement` 優先。
  - 將 restore/status/detail/legal row 移入 `CommerceReady`，或至少從 `PreviewUnavailable` 排除。
  - 可把 content 拆成 `PremiumScreenContent`，方便用 fake `PremiumBillingState` 做 Compose 測試。
- `app/src/main/java/com/example/notepad/ui/UiText.kt`
  - 新增或替換 preview 專用字串：`premiumPreviewTitle`、`premiumPreviewBody`。
  - 將 `premiumFeatures` 調成產品語氣，例如英文 `Premium preview`、繁中 `進階功能預覽`。
  - 避免 unavailable 主畫面使用 `premiumSubscribePending`、`premiumTrial`、`premiumRenewal`、`premiumBillingUnavailable` 這類工程／設定文案。
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
  - 更新既有 `premiumFallbackHidesCommerceAndShowsAllowedBenefits()`，目前它仍期待 `premium_restore_button` 顯示；新行為應改成 absent。
- 可選新增：`app/src/androidTest/java/com/example/notepad/ui/PremiumScreenContentTest.kt`
  - 若有拆 `PremiumScreenContent`，用 fake state 覆蓋 commerce-ready 與 active-entitlement UI，不依賴真 Google Play Billing。
- `app/src/test/java/com/example/notepad/ui/NoteUiPureFunctionTest.kt`
  - 若 `premiumUiMode` 是純函式且可測，補狀態分類單元測試。

## 測試案例

1. `PreviewUnavailable` full app test
   - 入口：點 `premium_tab`。
   - Assert absent：`annual_plan_option`、`monthly_plan_option`、`premium_subscribe_button`、`premium_restore_button`。
   - Assert absent text：`Price not available`、`Subscribe (setup pending)`、`No trial or introductory offer is configured.`、`Google Play`、`backend verification`、`Privacy Policy`、`Terms of Service`；繁中同理避免「價格尚未開放」「訂閱（準備中）」「後端驗證」。
   - Assert visible：三個 benefits 與 sample tags：`premium_folder_sample`、`premium_format_sample_*`、`premium_schedule_sample`。
   - Assert 可回記事：點 `notes_tab` 後 `add_note_button` 顯示。
2. `CommerceReady` fake-state Compose test
   - `billingAvailable = true` 且至少一個價格非 null。
   - Assert 顯示對應方案列、`premium_subscribe_button`、`premium_restore_button`、Privacy / Terms。
   - Assert 不顯示 preview unavailable 標題。
3. `ActiveEntitlement` fake-state Compose 或純函式測試
   - `hasPremiumAccess == true` 且價格為 null。
   - Assert mode 是 `ActiveEntitlement`，畫面顯示 active 狀態，不顯示 preview unavailable 標題，也不顯示 unavailable 診斷文字。
4. `premiumUiMode` pure tests
   - no billing/no prices -> `PreviewUnavailable`
   - billing available/monthly price only -> `CommerceReady`
   - billing available/annual price only -> `CommerceReady`
   - active/debug premium/no prices -> `ActiveEntitlement`

## 驗收標準

- `PreviewUnavailable` 不顯示 purchase chrome、restore、plan rows、subscribe、legal row、價格不可用、setup pending、Google Play 商品設定、後端驗證或 raw billing error。
- `PreviewUnavailable` 仍清楚顯示資料夾、文字格式、提醒／日曆工具三個 benefits，且語氣像產品預覽。
- `ActiveEntitlement` 永遠優先於 unavailable；已啟用使用者不可被呈現成「尚未開放」。
- `CommerceReady` 只在至少一個真價格可顯示時出現購買流程，並保留 restore 與必要法律揭露。
- 不修改 billing backend、catalog、Play Console product ids、entitlement store 或 text/drawing 已完成流程。
- 英文與繁中文案都自然；繁中避免工程口吻與未完成模板感。

## 風險

- `TextInputTest` 目前依賴真 app 啟動時 Billing 不可用；若測試環境日後能取到真價格，fallback 測試會不穩。commerce/active 建議用 fake-state content test 或純函式測試隔離。
- `debugPremiumOverride` 會讓 `hasPremiumAccess` 為 true，但不代表後端訂閱；測 active precedence 時要明確說明這是 UI gate 測試，不是付款驗證測試。
- 若只改 `showCommerceUi` 而沒有搬動 status/detail/legal row，使用者仍會看到工程文案，產品問題不算解掉。
- 若新增字串欄位，`EnglishText` 與 `TraditionalChineseText` 必須同步填值，避免編譯失敗。
- 法律連結在 commerce-ready 可保留，但 unavailable preview 隱藏後，後續正式開賣前需再檢查商店揭露與政策連結是否完整。
