# Agent A：Just Notes enhance v3 賈伯斯眼光產品審核

日期：2026-06-21
工作目錄：`/mnt/d/AndroidStudioProjects`
範圍：只做產品審核與下一輪增強目標選擇；不修改 app code，不修改測試。

## 一句話結論

這輪 v3 最值得做的不是再加文字或繪圖功能，也不是重開 Premium。最值得做的是：**把 Settings 裡尚未 release-ready 的 Google Account Sync 從一般使用者的第一層入口收起，讓資料保護只承諾目前真正可交付的手動 Backup & Restore。**

記事 app 的第一信任不是功能多，是使用者相信「我的東西不會不明不白地消失，也不會被一個看起來可用但其實還沒準備好的同步功能誤導」。

## 我讀過的現況

- `just_note_enhance_v2` 已完成 Premium unavailable preview：沒有商品價格時不再像壞掉的商店，也通過 focused / full connected validation。
- `text_note_enhance_v4` 已完成文字記事的空白稿乾淨化與 body-only 首行呈現，不應在本輪重複。
- `drawing_note_enhance_v4` 已完成記住上一支筆的顏色與粗細，並保留 v2/v3 繪圖行為，不應在本輪重複。
- Settings 目前已有 `Google Account Sync`、`Backup & Restore`、`Import / Export` 分區，且 backup/restore 已有 rollback checkpoint 與 preview 文案。
- `README.md` 與 `GOOGLE_ACCOUNT_SYNC_SETUP.md` 都明確說：Google account sync 只有 Android-side foundation，還沒完成 Google Cloud OAuth / Drive setup / real-device hardening；目前不能把它當 release-ready 的使用者功能。

## 目前 app 從 Jobs 眼光看到的主要產品問題

### 1. Settings 正在同時說「我很安全」與「我還沒準備好」

使用者進 Settings 看到 `Google Account Sync`、`Sign in with Google`、`Sync now`、`Google Drive app data`，自然會以為這是可依賴的雲端同步。但專案文件同時說 OAuth、release client、real-device validation、腐敗 JSON / 權限撤銷 / 多裝置同步硬化都還沒完成。

這不是小文案問題。對記事 app 來說，資料保護是信任核心；半完成的同步入口比沒有同步更危險。

### 2. 手動備份是目前可信的資料保護路徑，卻不是最乾淨的第一訊號

目前真正能承諾的是 Android file picker 的手動 JSON backup / restore、restore preview、rollback checkpoint、import/export。這些已經比一個未硬化的帳號同步更誠實。但 Settings 第一層仍先放 Google Account Sync，讓已完成的安全路徑被未完成的雲端承諾稀釋。

### 3. 使用者不該替產品讀 release checklist

「Google Drive app data」、「Sync status」、「Not signed in」這些字對工程師有意義，對使用者是承諾。Jobs 眼光下，尚未準備好上市的承諾不應出現在主體驗裡；產品要替使用者做選擇，而不是把施工狀態公開給他判斷。

### 4. 這比再微調 editor 更能提升信任

文字與繪圖近期已經連續改善。下一個會明顯傷害使用者信任的不是筆刷或工具列，而是資料安全的心智模型：到底這個 app 是本機記事、手動備份，還是已經能跨裝置同步？

## v3 單一 target

**Settings data-trust cleanup：預設隱藏未 release-ready 的 Google Account Sync 入口，讓 Backup & Restore 成為唯一對一般使用者承諾的資料保護主路徑。**

具體產品方向：

- fresh/default 狀態下，Settings 不顯示 Google Account Sync 區塊與 Google sign-in CTA。
- Settings 的資料保護區優先呈現 `Backup & Restore`：目前記事數、儲存備份、還原備份、選擇/更換備份檔、上次備份/還原、restore rollback。
- `Import / Export` 保持清楚獨立且免費。
- 如果裝置上已經存在 Google sync account/state，不要讓使用者失去管理或登出的路徑；但不要對新使用者露出「可開始使用同步」的承諾。Agent B/C 應決定最小可測的保護分支。

## 為什麼選它

- **信任優先**：記事 app 的底線是資料安全。半完成的 sync button 會讓使用者做錯信任判斷。
- **範圍窄**：主要是 Settings UI gating、文案與 focused UI tests；不需要重寫 sync engine、OAuth、Drive API 或資料模型。
- **不重複剛完成工作**：不碰 Premium unavailable preview、不碰 text-note v4、不碰 drawing-note v4。
- **日常效率會變好**：使用者進 Settings 只看到現在真的能用的資料保護操作，不必分辨 Google sync、manual backup、import/export 的半成品狀態。
- **符合產品品味**：沒有準備好的功能不該看起來像已經可用。產品應該安靜地隱藏未完成承諾。

## 不做什麼（Out of Scope）

- 不做 Google Cloud Console、OAuth consent、Drive API、release SHA-1、`google-services.json`、package identity 或 real-device sync setup。
- 不改 `GoogleDriveSyncClient`、sync merge algorithm、sync snapshot format、tombstone / conflict rules。
- 不刪除現有 sync foundation code；這輪只處理一般使用者 Settings 入口與承諾。
- 不重做 backup/restore 的資料格式、rollback store、restore preview 或 repository import/export 邏輯。
- 不重新設計 auto backup，也不把 `Overwrite backup when app opens` 擴成新的排程/同步系統。
- 不碰 Premium、Billing、text note、drawing note、checklist、OCR、reminder/calendar 的功能範圍。
- 不改測試基礎架構或引入需要真 Google 帳號的 connected validation。

## 成功標準 / Acceptance Criteria

- 在 fresh/default Settings 狀態，不顯示：
  - `google_account_sync_title`
  - `google_sync_button`
  - `google_account_status`
  - `google_last_sync_status`
  - `google_sync_error`
  - `Google Account Sync` / `Google 帳號同步`
  - `Sign in with Google` / `使用 Google 登入`
  - `Google Drive app data` 或同等「雲端同步已可用」文案
- `Backup & Restore` 仍清楚可見且免費，至少保留：
  - `online_sync_title`
  - `online_sync_target_status`
  - `online_sync_note_count`
  - `backup_button`
  - `restore_button`
  - `choose_sync_file_button`
  - 已有備份檔時的 change/forget file path
  - restore rollback row / button（當 checkpoint 存在時）
- Backup/restore 文案要把它定位成「目前支援的手動備份」，不要再把一般使用者帶回一個不可用的 Google account sync 心智模型。
- `Import / Export` 仍獨立可見，且不被 Premium 或 Google account sync gating 影響。
- 如果已有 Google sync account/state，必須保留安全管理路徑：至少能看到目前帳號狀態並登出，或 Agent B/C 提出等價保護方案。不能讓既有狀態變成無法退出的黑盒。
- 不改任何 note/folder/reminder/drawing/checklist 資料儲存行為；這輪不應產生資料 migration。
- Focused UI test 更新：
  - default Settings hides Google Account Sync / sign-in CTA。
  - default Settings still exposes Backup & Restore 與 Import / Export。
  - 若可行，fake/seed signed-in state 覆蓋既有帳號仍可管理或登出。
- 基本 gate：
  - `git diff --check`
  - `testDebugUnitTest assembleDebug assembleDebugAndroidTest`
  - focused connected Settings test
  - 若 Agent D 有實際 code/test 修改，依專案規則交 Agent F 用 Codex `gpt-5.5` xhigh review。

## 給 Agent B 的交接重點

- 請把方案限制在 Settings UI gating、UiText 文案與 focused tests。
- 建議先定義一個明確 UI mode，例如 `showGoogleAccountSyncUi = syncMetadata.accountEmail != null || internalSyncUiEnabled`。預設 fresh install 應為 false；已有帳號或內部測試旗標才顯示。
- 不要規劃 OAuth、Drive setup、sync merge 或資料格式變更。
- 不要把 manual backup 改名成 sync。它是手動 backup/restore，不是帳號同步。

## 給 Agent C 的交接重點

- 嚴格擋 scope creep：任何 Google Cloud、Drive API、Credential Manager、multi-device sync hardening 都不屬於 v3。
- 檢查 Agent B 是否保護了既有 signed-in state 的管理/登出路徑。
- 確認 default 使用者看不到 release-unready 的同步承諾，但仍看得到完整 manual backup/restore。
- 確認測試不是只刪 assertion，而是真的驗證「Google sync hidden + backup still usable」。

## 給 Agent D 的交接重點

- 只改 Settings composable、strings、focused tests；不要碰 repository/sync engine。
- 保留現有 test tags 或用最小方式調整；不要讓 settings test 變成依賴真 Google account。
- 文案請保持繁中自然：不要出現工程語氣、setup checklist、Drive appDataFolder 等一般使用者不需要理解的詞。
- 實作後先跑 focused Settings test，再跑基本 Gradle gate。

## 給 Agent F 的交接重點

- Code review 請優先看三件事：
  - fresh/default Settings 是否真的不再承諾 Google account sync；
  - manual backup/restore 是否完全保留；
  - 既有 signed-in state 是否仍有安全管理/登出路徑。
- 特別檢查是否不小心改到 sync merge、backup JSON、restore rollback 或資料 migration。

## 給 Agent G 的交接重點

- 先做 emulator gate：`LocalNotepad_API35` online，`adb devices` 是 `device`，`sys.boot_completed` 是 `1`。
- 驗證 default Settings：
  - 沒有 Google sync / sign-in CTA。
  - Backup & Restore 與 Import / Export 都可見。
  - Save backup / Restore backup / Choose backup file 控制仍在。
- 若 Agent D 加了既有 signed-in state 測試，請跑該 focused test。
- 最後依專案 gate 跑 unit/build/androidTest assemble；若 full connected suite 有跑，明確記錄 test count、failures、emulator restart 狀態。
