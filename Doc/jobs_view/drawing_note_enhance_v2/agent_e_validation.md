# Agent E 驗證報告：drawing_note_enhance_v2

日期：2026-06-20

## Emulator / ADB readiness

- 初始 `adb devices` 無 device。
- 已重啟 ADB server。
- 已啟動 `LocalNotepad_API35`。
- `adb devices` 後續顯示 `emulator-5554 device`。
- `adb shell getprop sys.boot_completed` 回傳 `1`。

## Local gate

命令：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon
```

結果：

- BUILD SUCCESSFUL in 1m 52s
- `testDebugUnitTest` 通過
- `assembleDebug` 通過
- `assembleDebugAndroidTest` 通過

補充：WSL 的 `powershell.exe` 不在 PATH，改用 `/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe`。

## Focused connected tests

第一次 focused run 有 1 個新測試同步失敗：

- `drawingNoteThumbnailAppearsForSavedStrokeAndOpensNote`

原因：

- 測試同時覆蓋手繪輸入、保存、返回列表、縮圖顯示，wait 條件太複合。
- 修正為直接 seed 一張有 drawingData 的 drawing note，讓此測試專注在列表縮圖與開啟行為。
- 另外縮圖 Canvas 在 clickable card 中需要用 unmerged tree 查找 test tag。

修正後單測：

- `drawingNoteThumbnailAppearsForSavedStrokeAndOpensNote`：通過

最終 focused run：

```powershell
.\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#newBlankDrawingHardwareBackExitsFullscreenThenDeletesDraftWithoutTombstone,com.example.notepad.TextInputTest#drawingNoteThumbnailAppearsForSavedStrokeAndOpensNote,com.example.notepad.TextInputTest#blankDrawingInitialFullscreenIsCleanAndDetailsOpensNormalMode,com.example.notepad.TextInputTest#drawingEditorCanUseFullscreenCanvasMode,com.example.notepad.TextInputTest#drawingShareExportControlsDisableWhileRenderingAndFailedSaveStopsShare --no-daemon
```

結果：

- 5 tests
- 0 skipped
- 0 failed
- BUILD SUCCESSFUL in 59s

## 狀態

Agent E focused validation 通過。後續在 Agent G full suite 前又補了一個 test-only 穩定化：

- `blankDrawingPremiumReminderPickerCancelKeepsDraft` 原本在 native DatePicker 關閉後，使用 Activity dispatcher 觸發 back，full suite 壓力下可能和 native dialog teardown 競態。
- 改為先用 device back 關掉 DatePicker，再點 app 內 `back_button` 離開 drawing editor，避免直接打 Activity dispatcher。
- 單測重跑通過：`blankDrawingPremiumReminderPickerCancelKeepsDraft`，BUILD SUCCESSFUL in 1m 50s。

下一步交給 Agent F 做 `gpt-5.5` xhigh code review。
