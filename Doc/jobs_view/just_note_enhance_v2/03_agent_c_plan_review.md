# Agent C：Premium unavailable 實作計畫審查

日期：2026-06-21
範圍：審查 Agent A 產品評審與 Agent B 實作計畫；只檢視 Premium UI／測試相關程式碼，不修改 App 程式碼。

## 審查結論

Agent A 的問題定義正確，Agent B 的主方向可執行：Premium 沒有可購買商品時，應呈現乾淨的進階功能預覽，而不是殘留購買流程。

但 Agent B 計畫在實作前必須補強三件事：

1. 不可把 `loading` 狀態誤判為「尚未開放」。
2. `PremiumUiMode` 與 Premium content 必須可用 fake state 測試，不可只靠真 Billing 的完整 App test。
3. Preview / Commerce / Active 三種畫面的文案與 chrome 必須完全切乾淨。

## 必要修改

1. **補上 loading 判斷**
   - `PremiumBillingState()` 預設 `loading = true`，此時還不是 unavailable。
   - 建議 mode 順序改為：
     1. `hasPremiumAccess` -> `ActiveEntitlement`
     2. `loading` -> `CheckingAvailability` 或等價中性狀態
     3. `billingAvailable && annualPrice/monthlyPrice 至少一個非空白` -> `CommerceReady`
     4. 其他 -> `PreviewUnavailable`
   - 若不新增第 4 個 enum，也必須保證 loading 不顯示「進階功能正在準備中」。

2. **mode 判斷要可測**
   - `premiumUiMode(billingState)` 應是 `internal` 純函式，不要做成 `private`。
   - 「有價格」應以可顯示價格為準，建議用 `!isNullOrBlank()`，避免空字串也開 commerce UI。

3. **拆出可測的 Premium content**
   - 若要做 fake-state UI test，`PremiumScreenContent` 或等價 composable 需可由 androidTest 呼叫。
   - 完整 `PremiumScreen` 可以保留 navigation / callback 包裝，但核心畫面不應只能透過真 `NotepadViewModel` 與真 Billing 測。

4. **PreviewUnavailable 必須完全移除購買 chrome**
   - 不顯示 `annual_plan_option`、`monthly_plan_option`、`premium_subscribe_button`、`premium_restore_button`。
   - 不顯示 status/detail/legal row。
   - 不顯示 `billingState.lastError`、`premiumStatusText(...)`、`premiumDetailText(...)`。
   - 必須使用產品語氣的 `premiumPreviewTitle` / `premiumPreviewBody`，並保留三個 benefits sample。

5. **ActiveEntitlement 必須優先且獨立**
   - 已有 Premium access 時，即使價格暫時不可用，也不能落入 preview unavailable。
   - 畫面至少要有明確 active 狀態與三個 benefits。
   - 預設不顯示 subscribe / restore / legal，除非另有明確帳務管理設計。

6. **CommerceReady 只在真價格可顯示時出現**
   - 只顯示有價格的方案列。
   - selected plan 若不可用，必須自動切到可用方案。
   - Restore 與法律揭露只在 commerce-ready 顯示。
   - 若保留錯誤文字，必須是使用者可理解的文案；不要把 raw Billing debug message 當首屏內容。

7. **文案需一併收斂**
   - `premiumFeatures` 建議改成中性「Premium features / 進階功能」，不要在 active 使用者眼前顯示「規劃」或 `paid-worthy`。
   - 若 commerce-ready 仍顯示法律文字，繁中建議用「隱私權政策」「服務條款」，目前「保密政策」「服務條約」不夠自然。

## 主要風險

1. **完整 App fallback test 會受真 Billing 環境影響**
   - 如果測試機日後能取到 Play 價格，`premiumFallbackHidesCommerceAndShowsAllowedBenefits()` 可能不再穩定。
   - Commerce / Active / Preview 的主要判斷應由 fake-state UI test 或純函式測試覆蓋。

2. **loading flicker 會傷害第一印象**
   - 使用者快速進 Premium tab 時，若先看到「尚未開放」再跳到價格，是錯誤心智模型。

3. **debug premium 不是付款驗證**
   - `debugPremiumOverride` 可用來測 UI active precedence，但測試名稱與報告要明確說這不是訂閱驗證。

4. **只改 `showCommerceUi` 不算完成**
   - status/detail/legal/error 任何一個留在 preview，都會讓畫面繼續像壞掉的商店。

5. **工作樹可能已有他人變更**
   - 實作者開始前要看 `git status` 與相關 diff，不要覆蓋別人的 app code 或測試變更。

## 最低測試門檻

1. **靜態與本地測試**
   - `git diff --check`
   - `testDebugUnitTest`
   - `assembleDebug`
   - `assembleDebugAndroidTest`

2. **純函式測試**
   - no billing / no prices -> preview unavailable
   - billing available / no prices -> preview unavailable
   - loading / no prices -> loading 或中性狀態，不是 preview unavailable
   - monthly price only -> commerce ready
   - annual price only -> commerce ready
   - blank price -> 不進 commerce ready
   - active entitlement or debug premium / no prices -> active entitlement

3. **Premium UI focused tests**
   - PreviewUnavailable：看得到 preview title/body、三個 benefits、`notes_tab` 可回首頁；看不到 plan / subscribe / restore / legal / setup pending / Google Play / backend verification / raw error。
   - CommerceReady fake state：看得到有價格的方案、subscribe、restore、legal；看不到 preview title。
   - ActiveEntitlement fake state：看得到 active 狀態；看不到 preview title、subscribe、restore、legal。

4. **Connected test gate**
   - 跑 connected / Compose instrumentation 前，先確認 `LocalNotepad_API35` 已啟動、`adb devices` 是 online device、`sys.boot_completed = 1`。
   - 至少跑 focused Premium fallback instrumentation test。
   - 若本輪要交付 APK 或合併到主線，再跑完整 connected suite。

5. **Agent F code review**
   - Just Notes app code 若有任何修改，完成實作與測試後必須請 Agent F 只做 review：
     `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review ...`

## 最終判定

Agent B 計畫可以進入實作，但必須先把 loading、可測性、active precedence 與文案邊界補進計畫。這輪成功標準不是「按鈕少幾個」，而是 Premium unavailable 狀態看起來像有意識的產品預覽。
