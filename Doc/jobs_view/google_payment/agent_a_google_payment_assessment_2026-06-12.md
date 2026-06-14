# Agent A：Just Notes Google 付款評估（2026-06-12）

## 結論

Brian 應實作 **Google Play Billing** 的訂閱或應用程式內商品，不應實作 Google Pay。Just Notes 的付費內容是 app 內數位功能（資料夾、文字格式、提醒／日曆工具），Google 官方 FAQ 明確說 Android app 內販售數位商品與服務需使用 Google Play In-app Billing；Google Pay API 主要適用於實體商品與服務結帳。

就目前產品設計與程式碼來看，最自然的方向是 **Premium 訂閱**，而不是 Google Pay，也不是外部信用卡付款。現有程式已經朝 Play Billing 訂閱走了一半：有 BillingClient、兩個訂閱 product id、查價、啟動購買、查詢既有訂閱、基本 acknowledge、restore/refresh 入口，以及 premium gates。

## 專案現況

- Gradle 已引用 `com.android.billingclient:billing-ktx:7.1.1`（`app/build.gradle.kts`）。
- `PremiumBilling.kt` 已建立 `BillingClient`，使用 `ProductType.SUBS` 查詢：
  - `just_notes_premium_monthly`
  - `just_notes_premium_annual`
- Premium 頁在查到價格時才顯示 plan rows 與訂閱按鈕；查不到商品時只留福利展示與「更新購買狀態」。
- `NotepadViewModel` 初始化時會 `premiumBilling.start()`，並把 Billing state 與 debug premium override 合併。
- Premium gates 已串在主要付費功能：
  - 資料夾管理／移動到非預設資料夾
  - 文字格式工具
  - 提醒、重複提醒、日曆/提醒篩選
- debug build 有 `DebugPremiumAccess` 開關；release build stub 永遠不可啟用，並有 release test 覆蓋。
- `PremiumBillingStateTest` 只測 entitlement boolean 合併邏輯，尚未測 BillingClient 互動、錯誤、restore、ack 重試或訂閱生命週期。

## 已有但還不夠的部分

- 已有基本 client-side purchase flow，但尚未達正式付費上線標準。
- `acknowledgePurchase()` 目前在 client 端做，且 entitlement 會在 ack 成功前就寫入本機快取；若 ack 失敗沒有持久化重試機制，3 天內未 acknowledge 會被退款/撤銷。
- 本機只快取 `premium_entitled: Boolean`，沒有 product id、purchase token、expiry、最後驗證時間、狀態（active/grace/on hold/expired/revoked）或來源。
- 沒有安全後端驗證 purchase token；client-only entitlement 可被竄改，只適合早期測試或低風險 MVP。
- 沒有 RTDN（Real-time Developer Notifications）與 Google Play Developer API 同步，所以退款、撤銷、取消、到期、account hold 等狀態只能靠 app 下次查詢，且離線快取可能過度授權。
- 沒有 billing 錯誤重試策略；目前 connection disconnected 只把 `billingAvailable=false`，沒有 backoff/retry 或新版 Billing Library 的 auto service reconnection。
- 目前使用 Billing Library 7.1.1；官方最新文件已列出 9.0.0（2026-05-19），且 PBL 7 發布新 app/update 的最後日期是 2026-08-31。若要現在做正式付款，應直接評估升到 PBL 9，至少不要把新付款工作建立在快到期的 PBL 7 上。
- Premium 頁文案仍偏「預覽／尚未連接 Billing」，正式啟用前必須改成真實訂閱揭露：價格、週期、續訂、取消方式、試用/優惠條款、隱私權/服務條款連結。
- 隱私權政策與服務條款目前只是底線文字，不是可點擊的有效連結。

## Play Console 產品設定

Brian 需要先決定產品 catalog 形狀，這會影響程式碼。

保守低改動路徑：

- 在 Play Console 建立兩個 subscription product，product id 必須完全符合現有程式：
  - `just_notes_premium_monthly`
  - `just_notes_premium_annual`
- 每個 subscription 建立並啟用一個 auto-renewing base plan：
  - monthly：月付
  - annual：年付
- 每個 base plan 設定可販售國家/地區、價格、grace period/account hold、是否可 pause。
- 暫時不要建立多個 offer，或至少確保 app 不會因 `.firstOrNull()` 拿到錯誤 offer token。

較乾淨的長期路徑：

- 建立單一 subscription，例如 `just_notes_premium`，底下放 monthly / annual 兩個 base plans。
- 程式改為查單一 subscription product，依 `basePlanId` 選 offer token。
- 這比較符合「同一組 Premium benefits，不同計費週期」的 Play 訂閱模型，但需要改程式與測試。

不論選哪條路，正式前還需要：

- 上傳 app 到 internal testing track（或其他測試 track），package name 必須與 Play Console app 相同。
- 啟用 monetization setup、商家/付款資料、app country distribution。
- 確認 merged manifest 仍含 `com.android.vending.BILLING` 與 billing client version metadata。
- 完成資料安全、隱私權政策、訂閱政策、付費功能說明、取消/管理訂閱入口。

## 測試策略

- 在 Play Console 的 license testing 加入 Brian/QA Gmail 或 Google Group；發布者帳號本身通常也被視為 license tester。
- Internal testing：上傳 signed AAB/APK 到 internal track，建立 tester list，讓 tester 透過 opt-in URL 加入。測試版購買若不是 license tester 仍可能真實收費。
- Debug sideload：官方允許 license testers 在 package name 符合 Play Console app 時 sideload debug build 測 Billing，不一定每次都要上傳新版本。
- 使用 Play Billing Lab 測試：
  - 不同國家/地區價格與 eligibility
  - trial/introductory offer
  - subscription state transition：active、grace period、account hold、restore、expired
- 測試矩陣至少包含：
  - 新購買 monthly/annual
  - 使用者取消購買
  - pending purchase 不解鎖
  - purchase 後 acknowledge 成功/失敗與重試
  - 重新安裝後 restore
  - 同裝置不同 Google Play 帳號
  - billing unavailable/offline 時 free features 可用，premium cache 行為可預期
  - grace period 保留 access，account hold/expired/revoked 移除 access
  - release build 不可啟用 debug premium

## 後端與安全建議

最保守正式路徑是建立小型 backend，而不是完全信任 app 本機：

- app purchase 成功後把 `purchaseToken`、product/base plan、app instance/user identifier 傳到 backend。
- backend 用 Google Play Developer API 查詢 subscription 狀態，確認未被使用、未撤銷、狀態有效，再寫入 entitlement。
- backend 透過 Developer API acknowledge 初次 subscription purchase；app 端 acknowledge 可保留為 fallback，但正式權威應在 backend。
- 設定 RTDN + Cloud Pub/Sub，收到 subscription purchased/renewed/canceled/on hold/in grace/revoked/expired 等事件後，再用 Developer API 查完整狀態並更新 entitlement。
- app 本機只快取 backend 已驗證的 entitlement 與最後驗證時間；離線可給短期 grace cache，但要可過期。
- 如果 Just Notes 暫時沒有 Brian 自己的帳號系統，entitlement 會自然綁 Google Play 帳號與本機安裝；若未來要跨裝置/跨平台，需先定義 user identity。

若 Brian 想先做 client-only MVP：

- 可以先靠 `queryPurchasesAsync()` + client acknowledge 上 internal testing，但要明確標成測試階段。
- 至少要加入 ack retry、狀態快取結構、restore UI、錯誤重試與測試矩陣，不要直接上正式付費。

## 保守實作計畫

1. 產品決策：確認 Premium 是訂閱，不是 Google Pay；決定「兩個現有 product ids」或「單一 subscription + monthly/annual base plans」。
2. 升級 Billing Library：優先升到 PBL 9；若短期只維持 PBL 7，必須排程 2026-08-31 前升級，避免新版本發布被擋。
3. Play Console 建 catalog：建立/啟用 subscription、base plans、價格、測試 track、license testers。
4. 補 Billing state machine：active、pending、grace、on hold、expired、billing unavailable、last verified、last error、restore result。
5. 補 purchase handling：明確選 base plan/offer token、處理 pending、ack 成功/失敗重試、on resume/foreground refresh。
6. 補 UI/文案：正式訂閱揭露、可點擊 privacy/terms、管理訂閱/取消入口、restore 成功/無訂閱/錯誤訊息。
7. 補後端：Developer API 驗證與 acknowledge、RTDN、entitlement endpoint；若延後後端，先限制在 internal testing。
8. 測試：unit fake Billing adapter、instrumented premium gates、license tester 實機購買、Play Billing Lab 狀態轉換、release build debug gate。
9. 上線前 gate：source review、xhigh Codex review、internal testing 全矩陣通過，再進 closed/open/production。

## 風險與 blockers

- **最大 blocker：Play Console 未建立 matching subscription products/base plans**。目前 app 查不到商品時不會顯示購買按鈕。
- **產品 catalog 一旦啟用不易更改 product id/base plan id**。Brian 要先決定是否保留 `just_notes_premium_monthly/annual` 兩個 product ids。
- **PBL 7 時程風險**：2026-08-31 後不能用 PBL 7 發新 app/update；現在開始正式付款最好直接升級。
- **client-only entitlement 安全性不足**：SharedPreferences 可被竄改；退款/撤銷/到期也不能即時同步。
- **acknowledgement 可靠性不足**：ack 失敗沒有持久化 retry，可能造成退款與 entitlement mismatch。
- **訂閱生命週期不足**：grace/account hold/expired/revoked 的文案與狀態未完整呈現。
- **法務/政策風險**：訂閱頁需清楚揭露價格、週期、取消方式、隱私權與服務條款；目前只是 placeholder。
- **測試環境風險**：license tester、internal test opt-in、Play Store cache、package name/signature/track propagation 都可能造成「商品查不到」或看到真實付款工具。

## 官方參考

- Google Pay FAQ：數位商品/服務需用 Google Play In-app Billing，Google Pay app API 只適用實體商品與服務。https://developers.google.com/pay/api/android/support/faq
- Play Billing 整合與 acknowledge 建議。https://developer.android.com/google/play/billing/integrate
- Play Billing 測試、license testers、Play Billing Lab。https://developer.android.com/google/play/billing/test
- License testing。https://support.google.com/googleplay/android-developer/answer/6062777
- Internal testing。https://support.google.com/googleplay/android-developer/answer/9845334
- 建立/管理 subscription、base plan、offer。https://support.google.com/googleplay/android-developer/answer/140504
- Backend、RTDN、purchase lifecycle。https://developer.android.com/google/play/billing/backend
- Subscription lifecycle：grace period、account hold、expired。https://developer.android.com/google/play/billing/lifecycle/subscriptions
- Play Billing Library release/deprecation。https://developer.android.com/google/play/billing/release-notes / https://developer.android.com/google/play/billing/deprecation-faq

## 本次限制

本次只做評估與文件輸出；未修改 source code、未 commit、未 push、未執行 release。也未跑 Gradle/connected tests，因為任務範圍是 assessment only。
