# Agent D：Premium fallback 實作報告

日期：2026-06-21

## 實作範圍

本輪只處理 Premium 不可購買時的畫面質感，不碰 Play Console、Billing backend、RTDN、entitlement store，也不重做文字或繪圖記事功能。

## 主要變更

- 在 `NotepadApp.kt` 新增 `PremiumUiMode` 與 `premiumUiMode(...)`，把 Premium UI 分成：
  - `ActiveEntitlement`
  - `AccountStatus`
  - `CheckingAvailability`
  - `CommerceReady`
  - `PreviewUnavailable`
- `hasPremiumAccess` 永遠優先，避免已啟用使用者因價格暫時不可用而看到「尚未開放」。
- Commerce 只在 Billing available 且至少一個非空白價格存在時顯示。
- Pending purchase / backend verification pending 保留帳號狀態與 restore，不被 preview 蓋掉。
- Billing 還在 loading，或 BillingClient 已連線但 product details 尚未回來時，先顯示中性的 checking copy；若 emulator / Billing 長時間沒有回應，短暫 grace timeout 後改顯示乾淨的 preview。
- Preview unavailable 不再顯示：
  - plan rows
  - subscribe button
  - restore button
  - status/detail/legal row
  - raw Billing / Google Play / backend verification copy
- Preview unavailable 保留三個核心 benefits：
  - 資料夾
  - 文字格式
  - 提醒／日曆工具
- Premium section 標題從工程／規劃語氣收斂成「Premium features / 進階功能」。
- 繁中法律文字改為較自然的「隱私權政策」「服務條款」。

## 測試變更

- `TextInputTest#premiumFallbackHidesCommerceAndShowsAllowedBenefits`
  - 改成等待 preview 出現。
  - 驗證 plan / subscribe / restore / legal / setup copy / Google Play / backend verification 都不存在。
  - 保留三個 benefits 與回到記事 tab 的驗證。
- `NoteUiPureFunctionTest`
  - 補 `premiumUiMode` 純函式測試：
    - loading 是 checking，不是 preview。
    - billing available 但 product details 尚未回來時仍是 checking。
    - pending purchase / verification pending 是 account status，不被 preview 隱藏。
    - no billing / no prices 是 preview。
    - blank price 不進 commerce。
    - monthly-only / annual-only 可進 commerce。
    - debug premium / active entitlement 優先。

## 實作中修正

第一版曾直接新增 `UiText` 欄位；connected test 在 Android verifier 因 `UiTextKt.<clinit>` 崩潰。已改為 `UiText` helper function，不再增加巨大 data class constructor 的參數數量。

Agent F 第一輪 code review 抓到 pending / verification 狀態會被 preview 蓋掉；已補 `AccountStatus`。第二輪抓到 BillingClient 連線後、product details callback 前會短暫誤顯示 preview；已把「billing available + no prices + no error」歸為 `CheckingAvailability`，並保留 UI timeout fallback。

## 已完成驗證

- `git diff --check`：通過
- `testDebugUnitTest`：通過
- `assembleDebugAndroidTest`：通過
- focused connected test：`TextInputTest#premiumFallbackHidesCommerceAndShowsAllowedBenefits` 通過
- full connected suite：170 tests，0 failed，`BUILD SUCCESSFUL in 8m 22s`
