# Agent F：Code review 報告

日期：2026-06-21

## Review 設定

- 角色：Just Notes code-change reviewer
- 指令：`codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review --uncommitted`
- 範圍：本輪 Premium unavailable preview 的 source/test/doc changes

## 第一輪 findings

1. Pending purchase / verification pending 會被 preview 隱藏。
   - 風險：使用者已付款但仍在等待確認時，看不到帳號狀態與 restore。
   - 修正：新增 `PremiumUiMode.AccountStatus`，讓 `PendingPurchase` 與 `VerificationPending` 顯示 restore、status、detail，但不顯示 plan/legal commerce chrome。

2. 根目錄有舊的 generated connected test XML。
   - 風險：若誤 commit，會把 stale failing report 放進 patch。
   - 處理：未 stage 該檔，並新增 `.gitignore` 規則 `connectedDebugAndroidTest*.xml`。

## 第二輪 findings

1. BillingClient connected 後、product details callback 前，可能短暫誤顯示 preview。
   - 風險：真機載入商品價格時，使用者可能先看到「Premium is being prepared」再跳成可購買畫面。
   - 修正：`premiumUiMode(...)` 將 `billingAvailable == true`、兩個價格皆空、且 `lastError == null` 視為 `CheckingAvailability`；UI 仍保留 2.5 秒 fallback 到 preview，避免 emulator 或 Play Billing 卡住。

2. 舊 generated XML 仍不可進 commit。
   - 處理：同上，排除並 ignore。

## 第三輪結果

第三輪 review 未發現 source code correctness 問題。唯一 comment 仍是舊 generated XML 不應 commit；已用 `.gitignore` 與 stage scope 處理。

## 結論

Agent F 同意 source 方向：Premium fallback 現在能保護三種重要狀態。

- 已啟用 Premium：顯示 active entitlement。
- 付款／驗證進行中：顯示帳號狀態與 restore。
- 尚未可購買：顯示乾淨的功能預覽，不露出失敗商店 chrome。
