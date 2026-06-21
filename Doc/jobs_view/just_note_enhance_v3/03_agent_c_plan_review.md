# Agent C：Just Notes enhance v3 實作計畫審查

日期：2026-06-21
工作目錄：`/mnt/d/AndroidStudioProjects`
審查對象：`02_agent_b_implementation_plan.md`
範圍：只審查與新增非程式碼文件；不修改 app code，不修改測試。

## 結論

**批准 Agent B plan，但採有條件批准。**

Agent B 的主方向正確：把 fresh/default Settings 裡尚未 release-ready 的 `Google Account Sync`、`Sign in with Google`、`Google Drive app data` 承諾收起，並讓目前可交付的手動 `Backup & Restore` 與 `Import / Export` 保持清楚可見。這符合 Agent A 的產品判斷，也符合本輪 v3 應守住的資料信任目標。

批准條件是：Agent D 實作時必須把 scope 嚴格鎖在 Settings UI gating、使用者可見文案、focused settings tests。不得把「既有登入狀態保護」擴大成新的 OAuth、Drive、sync engine、資料格式或 migration 工作。

## 我讀到的現況

- `SettingsScreen` 目前無條件顯示 Google account sync 區塊；只有 `google_sign_out_button` 依 `syncMetadata.accountEmail != null` 顯示。
- `NotepadViewModel` 初始化 `SyncMetadata.accountEmail` 來自 `GoogleDriveSyncClient.accountEmail`；fresh/default 未登入時為 `null`，狀態為 `SignedOut`。
- 現有 `settingsExposeManualBackupControls` 測試仍期待 `google_account_sync_title` 與 `google_sync_button` 預設可見，需更新為「預設隱藏 Google sync，仍保留 manual backup/import-export」。
- `UiText` 仍有幾句會把手動備份拉回 Google sync 心智模型，例如 `backupTargetHint`、`googleDriveHandledByFiles`、`googleAccountSyncHint`。

## 必須守住的 Scope Guardrails

Agent D 只能碰：

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt` 的 Settings UI 顯示條件。
- `app/src/main/java/com/example/notepad/ui/UiText.kt` 中 Settings 相關使用者文案。
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt` 的 focused Settings test。

不得碰：

- OAuth、Google Cloud Console、Drive API setup、release SHA-1、`google-services.json`、package identity。
- `GoogleDriveSyncClient` 行為、Google Sign-In flow、Drive app data 讀寫。
- sync engine、merge algorithm、conflict/tombstone rules、remote snapshot format。
- backup JSON 格式、restore preview、rollback checkpoint、repository import/export 資料流程。
- Room migration、DAO、資料模型。
- auto backup 排程或新的 sync system。
- Premium/Billing、text note、drawing note、checklist、OCR、reminder/calendar。
- 需要真 Google 帳號、外部 Google Drive 狀態、或 real-device Google sync 的 connected validation。

本輪不能把 manual backup 改名成 sync，也不能用文案暗示雲端同步已可依賴。

## 風險與修正建議

### 1. UI gating 條件批准，但不要再擴大

批准 Agent B 建議的最小條件：

```kotlin
val showGoogleAccountSyncUi = syncMetadata.accountEmail != null
```

這能讓 fresh/default 使用者看不到 Google sync 承諾，同時讓已登入狀態保留管理/登出路徑。

若 Agent D 想加入 `internalGoogleSyncUiEnabled`，需要非常謹慎：它不得成為一般使用者可見開關，也不得引入新的設定儲存、debug menu、build flavor 或測試基礎設施。除非現有架構已有安全接縫，否則本輪不建議新增。

### 2. 既有已登入狀態要能退出，但不必完成新測試接縫

已登入狀態下至少要保留：

- 帳號狀態。
- sync status / last sync / error 顯示可沿用現有 UI。
- `google_sign_out_button` 與現有 sign-out confirm flow。

`Sync now` 對既有已登入者可沿用以降低 diff；但 fresh/default 狀態絕不能看到 `Sign in with Google` 或 `google_sync_button`。

若目前沒有乾淨方式 fake/seed signed-in state，不要為了寫 connected test 去改 ViewModel 建構、Drive client 注入或 Google sign-in flow。可以在實作報告中明確說明 signed-in state 由 Agent F code review 靜態確認，Agent G 若有既有登入測試裝置再手動驗證。

### 3. `account_settings_button` 的對話框文案要同步收斂

即使 Google account sync 區塊被隱藏，Backup & Restore 裡的 `account_settings_button` 仍會開啟 `googleDriveHandledByFiles` 文案。目前繁中寫「目前沒有啟用 Google 登入」，英文寫「No Google sign-in is active」。這會重新提醒使用者一個已被隱藏的未完成功能。

建議照 Agent B 方向改成只描述當前可用能力：

- 只說此畫面透過 Android 檔案選擇器儲存與還原手動 JSON 備份。
- 不提 Google 登入、Drive appDataFolder、OAuth、setup pending。

### 4. 文案修正要小，不要 rename key

批准保留歷史 key 名稱，例如 `backupToGoogleDrive`、`chooseGoogleDriveSyncFile`、`changeGoogleDriveSyncFile`。這些 key 名稱雖不理想，但不是使用者可見內容；本輪不應為了命名乾淨擴大 diff。

使用者可見文案要修正：

- `backupTargetHint` 不再提「與 Google 帳號同步分開」。
- `googleDriveHandledByFiles` 不再提「沒有啟用 Google 登入」。
- `googleAccountSyncHint` 只在既有已登入管理區顯示，語氣應是管理/登出，不是推廣雲端同步。
- `autoSyncOnStart` 可更明確成「開啟 app 時覆寫備份檔」。

### 5. Tests 不應只刪除舊 assertion

`settingsExposeManualBackupControls` 不能只是移除 Google sync assertion。它應明確驗證：

- 預設不存在 Google sync tags。
- 預設不存在 `Google Account Sync`、`Sign in with Google`、`Google Drive app data` 等原始文字。
- `Backup & Restore` 控制仍存在。
- `Import / Export` 控制仍存在。

現有 `assertTagAbsent` 與 `onAllNodesWithText` helper 足夠支援這個 default-state focused test；不需要真 Google 帳號。

## Agent D 實作前 Checklist

- [ ] 確認只改 `NotepadApp.kt`、`UiText.kt`、`TextInputTest.kt`；如需碰其他檔，先停下重新評估 scope。
- [ ] 在 `SettingsScreen` 定義單一 gating 條件，首選 `syncMetadata.accountEmail != null`。
- [ ] 用該 gating 包住整個 Google account sync UI：title、status、hint、last sync、progress、error、sync/sign-in button、sign-out button。
- [ ] 確認 `Backup & Restore` 區塊仍無條件顯示。
- [ ] 確認 `Import / Export` 區塊仍無條件顯示。
- [ ] 修正 Settings 相關使用者文案，但不大量 rename `UiText` 欄位或 test tags。
- [ ] 更新 focused test：default Settings hides Google sync and exposes manual backup/import-export。
- [ ] 不引入需要真 Google 帳號的 test。
- [ ] 不改 sync engine、backup JSON、restore rollback、repository、DAO、migration。
- [ ] connected/UI test 前先完成 emulator gate：`LocalNotepad_API35` online、`adb devices` 是 `device`、`sys.boot_completed` 回傳 `1`。
- [ ] 實作後至少跑 `git diff --check`、unit/build gate、focused Settings connected test；若有跑不動，記錄原因與已完成驗證。

## Agent F 應特別審查的點

- fresh/default Settings 是否真的看不到：
  - `google_account_sync_title`
  - `google_account_status`
  - `google_last_sync_status`
  - `google_sync_progress`
  - `google_sync_error`
  - `google_sync_button`
  - `google_sign_out_button`
  - `Google Account Sync`
  - `Sign in with Google`
  - `Google Drive app data`
- Backup & Restore 是否仍保留：
  - `online_sync_title`
  - `online_sync_target_status`
  - `online_sync_note_count`
  - `online_sync_auto_checkbox`
  - `backup_button`
  - `restore_button`
  - `choose_sync_file_button`
  - `account_settings_button`
  - 已有備份檔時的 change/forget/disconnect path
  - restore rollback row/button 條件顯示
- Import / Export 是否仍保留：
  - `import_export_title`
  - `batch_export_button`
  - `batch_import_button`
- 已登入 Google sync 狀態下是否仍能看到帳號狀態並登出。
- 是否完全沒有改到 `GoogleDriveSyncClient`、sync merge、snapshot format、backup JSON、rollback、repository、DAO、migration。
- Tests 是否在驗證新產品承諾，而不是只刪掉舊 assertion。

## Agent G 應特別驗證的點

- 先記錄 emulator readiness：
  - `LocalNotepad_API35` 已啟動。
  - `adb devices` 顯示 online `device`。
  - `adb shell getprop sys.boot_completed` 回傳 `1`。
- 跑 focused Settings connected test，確認 default Settings：
  - 看不到 Google sync / Google sign-in CTA。
  - 看得到 Backup & Restore。
  - 看得到 Save backup / Restore backup / Choose backup file / Account settings。
  - 看得到 Import / Export / batch export / batch import。
- 若 Agent D 有 signed-in fake state test，驗證 signed-in 管理/登出路徑。
- 若沒有 signed-in fake state test，不要臨時要求真 Google 帳號；請把它列為 code review 或可用測試裝置上的手動驗證項。
- 回報 full gate 時要分清楚 app failure 與 emulator/setup failure。

## 最終批准狀態

**批准：是，有條件批准。**

Agent B plan 可以交給 Agent D 實作，但 Agent D 必須守住本文件 guardrails。這輪 v3 的成功不是讓 Google sync 更接近完成，而是讓一般使用者不再被未完成的 Google sync 承諾誤導，同時完整保留目前真正可用的手動資料保護路徑。
