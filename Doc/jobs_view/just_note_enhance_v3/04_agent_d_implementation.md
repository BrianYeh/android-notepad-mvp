# Agent D：Settings data-trust cleanup 實作報告

日期：2026-06-21

## 實作範圍

本輪只處理 Settings 的資料信任問題：預設隱藏尚未 release-ready 的 Google Account Sync 入口，讓一般使用者只看到目前可交付的手動 Backup & Restore 與 Import / Export。

沒有修改 Google sync engine、OAuth、Drive API、backup JSON、restore rollback、DAO、migration、Premium、文字記事或繪圖記事。

## 主要變更

- 在 `NotepadApp.kt` 新增 `shouldShowGoogleAccountSyncUi(...)`，條件為 `syncMetadata.accountEmail != null`。
- fresh/default Settings 不再顯示：
  - `Google Account Sync`
  - Google account status
  - Google sync status / last sync / error / progress
  - `Sign in with Google`
  - Google sync button
  - Google sign-out button
- 若既有裝置仍有 Google account sync state，Google 管理區仍會顯示帳號狀態、同步狀態、立即同步與登出路徑。
- `Backup & Restore` 區塊維持無條件可見，保留：
  - note count
  - backup target status
  - save backup
  - restore backup
  - choose/change backup file
  - backup details
  - forget file
  - restore rollback row
- `Import / Export` 區塊維持無條件可見。

## 文案調整

- Manual backup 文案改成只描述 Android file picker 的手動 JSON 備份，不再把 fresh 使用者導回 Google sync 心智模型。
- Google sync hint 改成既有連線管理語氣：查看狀態、立即同步或登出；本機記事不會被刪除。
- Backup details dialog 不再提「沒有啟用 Google 登入」。
- Auto backup row 改成更明確的「開啟 app 時覆寫備份檔」。

## 測試變更

- `TextInputTest#settingsHideGoogleSyncByDefaultAndExposeManualBackupControls`
  - 驗證 default Settings 沒有 Google sync tags、`Google Account Sync`、`Sign in with Google`、`Google Drive app data`。
  - 驗證 Backup & Restore 控制仍可見。
  - 驗證 Import / Export 控制仍可見。
- `NoteUiPureFunctionTest#googleAccountSyncUiOnlyShowsForExistingConnectedAccount`
  - 驗證 fresh/signed-out 不顯示 Google sync UI。
  - 驗證既有 account email 會保留 Google sync 管理 UI。

## 已完成驗證

- `git diff --check`：通過
- focused unit test：`NoteUiPureFunctionTest#googleAccountSyncUiOnlyShowsForExistingConnectedAccount` 通過
- Android build / test gate：`testDebugUnitTest assembleDebug assembleDebugAndroidTest` 通過
- emulator readiness gate：
  - `/mnt/d/android/Sdk/platform-tools/adb.exe devices` 顯示 `emulator-5554 device`
  - `adb shell getprop sys.boot_completed` 回傳 `1`
  - AVD：`LocalNotepad_API35`
- focused connected Settings test：`TextInputTest#settingsHideGoogleSyncByDefaultAndExposeManualBackupControls` 通過，1 test / 0 failed

## 待完成

- Agent F code review
- final validation document
- APK 交付與 commit / push
