# Agent F：Settings data-trust cleanup code review

日期：2026-06-21
工作目錄：`/mnt/d/AndroidStudioProjects`
審查範圍：目前未提交 tracked diff、Agent A/B/C/D 文件、`git diff`

## Findings

未發現 blocking findings。

未發現需要在本輪 merge 前修正的非 blocking code findings。

## Codex CLI Review

另依 workspace 規則執行：

```bash
codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review -
```

CLI review 結論同樣為：未發現 blocking issues。它確認 default Google account sync / sign-in UI 已隱藏、manual backup / import-export 控制仍可見、既有 signed-in account 仍保留 sync 與 sign-out controls。

## 審查重點結論

- Fresh/default Settings：符合目標。`shouldShowGoogleAccountSyncUi(syncMetadata)` 只在 `syncMetadata.accountEmail != null` 時顯示 Google Account Sync UI；預設未登入狀態會隱藏 `google_account_sync_title`、帳號狀態、last sync、progress/error、`google_sync_button`、`google_sign_out_button`，因此不再露出 `Google Account Sync` / `Sign in with Google` / `Google Drive app data` 的一般使用者承諾。
- Backup & Restore：符合目標。`online_sync_title`、target status、note count、auto overwrite、save backup、restore backup、choose/change backup file、backup details、forget file、restore rollback row/button 都在 Google gating 外，仍無條件可見或依既有 backup/rollback state 顯示。
- Import / Export：符合目標。`import_export_title`、ZIP export、text import 仍在 Google gating 外，未被 Premium 或 Google sync gating 影響。
- 既有 signed-in Google sync state：符合目標。當 `accountEmail != null` 時，Google 管理區仍顯示帳號狀態、sync status、last sync/error/progress、`Sync now` 與 `Sign out`，保留管理與登出路徑。
- Scope：符合目標。tracked diff 只有 `NotepadApp.kt`、`UiText.kt`、`TextInputTest.kt`、`NoteUiPureFunctionTest.kt`；未修改 `GoogleDriveSyncClient`、sync merge、backup JSON、restore rollback store、repository、DAO、migration、Premium、text/drawing 功能程式碼。
- Tests：方向正確。Instrumentation test 不只是刪除舊 assertion，而是明確驗證 Google sync 預設不存在，並同時驗證 Backup & Restore 與 Import / Export 控制仍存在；pure unit test 覆蓋 `accountEmail == null` 隱藏、`accountEmail != null` 顯示的 gating 判斷。

## Evidence

- Gate 定義：`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:323`。
- Settings 使用 gate：`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:2017`、Google 區塊包在 `if (showGoogleAccountSyncUi)`：`2318` 到 `2407`。
- Backup & Restore 在 gate 外：`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:2408` 到 `2584`。
- Import / Export 在 gate 外：`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:2585` 之後。
- Restore rollback 邏輯仍走既有 checkpoint path：`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:2476`、`2667` 到 `2695`。
- Backup details dialog 文案不再提 Google sign-in：`app/src/main/java/com/example/notepad/ui/NotepadApp.kt:2698` 到 `2707`，字串在 `UiText.kt:592` / `849`。
- Focused connected test 覆蓋 default hidden + backup/import visible：`app/src/androidTest/java/com/example/notepad/TextInputTest.kt:2444` 到 `2468`。
- Pure function test 覆蓋 signed-out/signed-in gate：`app/src/test/java/com/example/notepad/ui/NoteUiPureFunctionTest.kt:16` 到 `39`。

## Residual Risk / Test Gaps

- 沒有 connected/fake signed-in UI test 實際渲染 `accountEmail != null` 時的 Google 管理區；目前由 pure function test 加靜態審查確認。若未來 refactor Google 區塊內容，這條路徑仍值得補一個安全 fake state UI test。
- Default UI test 的 raw text absence 主要驗證英文 `Google Account Sync` / `Sign in with Google` 與 `Google Drive app data`；tag absence 已能抓到主要 Google 區塊，但若未來有人在其他未加 tag 的中文文案中重新提到 Google 登入/同步，現有 test 不一定直接攔到。
- `accountEmail != null` 依賴 Google sign-in account email 作為既有連線訊號。現有 `GoogleSignInOptions.requestEmail()`、`GoogleDriveSyncClient.accountEmail` 與 `NotepadViewModel` metadata flow 支持這個假設；但若某些裝置回傳 email 為 null，管理區會被隱藏。此風險低，且不建議本輪擴大 scope。
- 本次 review 執行了 `git diff --check` 且通過；未重新跑完整 Gradle / connected test gate。Agent D 報告已跑過 focused unit、`testDebugUnitTest assembleDebug assembleDebugAndroidTest`、emulator readiness、focused connected Settings test。
