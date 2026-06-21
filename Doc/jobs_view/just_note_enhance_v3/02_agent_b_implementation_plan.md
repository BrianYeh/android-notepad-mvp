# Agent B：Just Notes enhance v3 實作計畫

日期：2026-06-21
工作目錄：`/mnt/d/AndroidStudioProjects`
依據：`01_agent_a_jobs_review.md`
範圍：本文件只規劃下一輪實作；本輪不修改 app code 或測試。

## 本輪 target 摘要

v3 target 是 **Settings data-trust cleanup**：在 fresh/default 狀態下，把尚未 release-ready 的 `Google Account Sync` 與 `Sign in with Google` 從一般使用者的 Settings 第一層入口收起，讓目前真正可承諾的 `Backup & Restore` 成為資料保護主路徑。

產品效果應該是：

- 新使用者進 Settings 時，只看到可用的手動備份、還原與 Import / Export。
- 不再看到 Google 帳號同步、Google 登入、Google Drive app data 等會被理解成「雲端同步已可依賴」的承諾。
- 既有已登入 Google sync 的使用者仍有安全管理路徑，至少看得到帳號狀態並可登出。
- 不刪 Google sync foundation，不做 OAuth / Drive / merge / 資料格式調整。

## 建議最小 code change scope

只建議碰三個面向：

1. `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
   - 在 `SettingsScreen` 內新增單一 UI gating 條件，例如：
     - `val showGoogleAccountSyncUi = syncMetadata.accountEmail != null`
   - 用這個條件包住目前無條件顯示的 Google Account Sync 區塊：
     - `google_account_sync_title`
     - `google_account_status`
     - `google_last_sync_status`
     - `google_sync_progress`
     - `google_sync_error`
     - `google_sync_button`
     - `google_sign_out_button`
   - Backup & Restore 區塊維持獨立且無條件可見。
   - Import / Export 區塊維持獨立且無條件可見。

2. `app/src/main/java/com/example/notepad/ui/UiText.kt`
   - 只調整 Settings 裡 Google sync / manual backup 相關使用者文案。
   - 不為了 key 名稱漂亮而大改 `UiText` 欄位名；例如 `backupToGoogleDrive`、`chooseGoogleDriveSyncFile` 這類歷史 key 可先保留，避免擴大 diff。

3. `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
   - 更新現有 `settingsExposeManualBackupControls` 測試，不要再期待 Google sync 預設可見。
   - 補 focused coverage 驗證「Google sync hidden + backup/import-export still visible」。
   - 若要覆蓋既有已登入 Google state，需用 fake/seedable 狀態，不可依賴真 Google 帳號。

不建議本輪改：

- `GoogleDriveSyncClient`
- `SyncMerge`
- remote snapshot / backup JSON 格式
- repository / DAO / migration
- OAuth setup / Google Cloud 設定
- billing、text、drawing、checklist 等非 Settings data-trust 範圍

## 建議 UI gating 條件

最小且可解釋的條件：

```kotlin
val showGoogleAccountSyncUi = syncMetadata.accountEmail != null
```

原因：

- fresh/default install 中 `NotepadViewModel` 會用 `driveSyncClient.accountEmail` 初始化 `SyncMetadata.accountEmail`；未登入時為 `null`。
- 已有 Google sync account 且仍被 `GoogleDriveSyncClient` 辨識時，`accountEmail` 不為 `null`，Settings 仍可顯示帳號狀態與登出入口。
- 不使用 `syncMetadata.status != SignedOut` 當條件，避免 fresh 狀態若落在 `SetupRequired` 等狀態時誤顯示 Google 同步入口。

若 Agent D 需要保留內部測試入口，可採：

```kotlin
val showGoogleAccountSyncUi = syncMetadata.accountEmail != null || internalGoogleSyncUiEnabled
```

但 `internalGoogleSyncUiEnabled` 必須預設為 `false`，且不應新增一般使用者可見的 Settings 開關。本輪最推薦仍是只用 `syncMetadata.accountEmail != null`。

### 已登入狀態保護

當 `syncMetadata.accountEmail != null` 時，Google 區塊不能完全消失。至少保留：

- 帳號狀態：`signedInAsAccount(email)` 或等價顯示。
- 同步狀態 / 上次同步 / 錯誤訊息：可沿用現有 UI，方便使用者判斷目前狀態。
- 登出按鈕：`google_sign_out_button` 與現有 `signOutGoogleAccount()` flow。

`Sync now` 對既有已登入者可先沿用，以維持最小 diff；若產品想更保守，也可在既有帳號區只保留「狀態 + 登出」，但那會比本輪必要範圍多一點 UI 決策。無論哪個選擇，都不能在 fresh/default 狀態露出 `Sign in with Google`。

## 需更新的 strings / 文案方向

文案方向是：manual backup 就說 manual backup，不再把使用者帶回 Google sync 心智模型。

建議調整：

- `backupTargetHint`
  - 目前提到「與 Google 帳號同步分開」。
  - 建議改成單純承諾可用行為：
    - 繁中：`透過 Android 檔案選擇器儲存或還原手動 JSON 備份。備份檔由你選擇並保存在本機或雲端硬碟位置。`
    - 英文：`Save or restore a manual JSON backup through Android's file picker. You choose where the backup file is stored.`

- `googleDriveHandledByFiles`
  - 目前說「目前沒有啟用 Google 登入」。
  - 預設 Google 區塊隱藏後，這句會不必要地提醒使用者一個看不到的功能。
  - 建議改成：
    - 繁中：`此畫面只透過 Android 檔案選擇器儲存與還原手動 JSON 備份。`
    - 英文：`This screen saves and restores manual JSON backups through Android's file picker.`

- `autoSyncOnStart`
  - 目前「開啟時覆寫備份」可用，但建議更明確：
    - 繁中：`開啟 app 時覆寫備份檔`
    - 英文：`Overwrite the backup file when the app opens`

- `googleAccountSyncHint`
  - 只會在既有已登入狀態顯示時，避免 `Google Drive app data` / `hidden sync file` 這種工程語氣。
  - 建議改成管理語氣：
    - 繁中：`此裝置仍連接 Google 同步。你可以查看狀態、立即同步或登出；本機記事不會被刪除。`
    - 英文：`Google sync is still connected on this device. You can review its status, sync now, or sign out. Local notes stay on this device.`

不建議：

- 把 manual backup 改名成 sync。
- 在一般使用者可見文案裡出現 `appDataFolder`、OAuth、Drive API、release client、setup pending 等工程細節。
- 大量 rename `UiText` 欄位或 test tag；本輪以使用者可見文案與 gating 為主。

## Focused test plan 與 Gradle gate

### Focused UI tests

更新或拆分現有 `settingsExposeManualBackupControls`：

1. `settingsHideGoogleSyncByDefaultAndExposeManualBackupControls`
   - 進 Settings。
   - 驗證預設不存在：
     - `google_account_sync_title`
     - `google_account_status`
     - `google_last_sync_status`
     - `google_sync_progress`
     - `google_sync_error`
     - `google_sync_button`
     - `google_sign_out_button`
   - 驗證預設文字不存在：
     - `Google Account Sync`
     - `Sign in with Google`
     - `Google Drive app data`
   - 驗證仍存在：
     - `online_sync_title`
     - `online_sync_target_status`
     - `online_sync_note_count`
     - `online_sync_auto_checkbox`
     - `backup_button`
     - `restore_button`
     - `choose_sync_file_button`
     - `account_settings_button`
     - `import_export_title`
     - `batch_export_button`
     - `batch_import_button`

2. `settingsKeepsGoogleAccountManagementWhenAlreadySignedIn`
   - 只有在能用 fake/seeded state 時加入；不可要求真 Google 帳號。
   - 建議方式是建立極小測試接縫，讓 instrumentation 能啟動 app 時提供 `SyncMetadata(accountEmail = "person@example.com", status = SyncStatus.Idle)` 或等價 fake client。
   - 驗證已登入時：
     - `google_account_sync_title` 可見。
     - `google_account_status` 顯示已登入帳號。
     - `google_sign_out_button` 可見。
     - 點登出後走現有 confirm dialog；不需要真的打 Google / Drive。
   - 若本輪不建立 fake injection，至少要有純函式或小單元測試覆蓋 `showGoogleAccountSyncUi` 的判斷，並在實作報告中說明 signed-in UI connected test 暫無安全接縫。

### Emulator gate

任何 connected / Compose UI test 前先做 emulator gate：

```powershell
D:\android\SDK\platform-tools\adb.exe devices
D:\android\SDK\platform-tools\adb.exe shell getprop sys.boot_completed
```

要求：

- `LocalNotepad_API35` 已啟動。
- `adb devices` 顯示 online `device`，不是 `offline`。
- `sys.boot_completed` 回傳 `1`。

若不符合，先重啟 ADB 或重開 emulator，再跑測試；不要把 emulator 問題當成 app test failure。

### Gradle gate

在 WSL 透過 Windows PowerShell 跑，沿用專案既有 Android/JBR 設定：

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
$env:ANDROID_HOME = "D:\android\SDK"
$env:ANDROID_SDK_ROOT = "D:\android\SDK"
$env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"
Set-Location "D:\AndroidStudioProjects"
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon
```

Focused connected test 建議先跑：

```powershell
.\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#settingsHideGoogleSyncByDefaultAndExposeManualBackupControls --no-daemon
```

若加入 signed-in fake state 測試，再跑：

```powershell
.\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#settingsKeepsGoogleAccountManagementWhenAlreadySignedIn --no-daemon
```

最後基本檢查：

```bash
git diff --check
```

若 Agent D 實際修改 code/test，依 workspace 規則交 Agent F 用 Codex `gpt-5.5` xhigh reasoning 做 code review；Agent F 僅 review，不修改檔案。

## 明確 out of scope

本輪不做：

- Google Cloud Console、OAuth consent、Drive API、release SHA-1、`google-services.json`、package identity、real-device sync setup。
- `GoogleDriveSyncClient` 行為、Google Sign-In flow、Drive app data 讀寫。
- sync merge algorithm、conflict rules、tombstone、remote snapshot format。
- backup JSON 格式、restore preview、rollback checkpoint、repository import/export 資料流程。
- migration、DAO、資料模型調整。
- auto backup 排程或新的同步系統。
- Premium / Billing / subscription 文案與行為。
- text note、drawing note、checklist、OCR、reminder/calendar 功能。
- 需要真 Google 帳號或外部 Google Drive 狀態的 connected validation。

## 交付重點

Agent D 實作時應讓 default Settings 看起來像一個誠實的本機記事 app：資料保護主路徑是手動備份與還原，Import / Export 清楚獨立；Google 帳號同步只在既有已登入狀態下作為管理/登出路徑出現，不再對新使用者發出尚未準備好的同步承諾。
