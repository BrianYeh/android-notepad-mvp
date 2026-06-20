# Agent G Full Connected Validation：drawing_note_enhance_v2

日期：2026-06-20

## Emulator / ADB readiness

- 使用 emulator：`LocalNotepad_API35`
- 最終 full suite 前重新啟動 emulator，避免前一輪中斷後 Android system services 不穩。
- `adb devices`：`emulator-5554 device`
- `adb shell getprop sys.boot_completed`：`1`
- `pm path android` 可正常回應，確認 package/activity services 恢復。

## Interrupted / invalid runs

完整 suite 第一輪遇到 `blankDrawingPremiumReminderPickerCancelKeepsDraft` 的 native DatePicker teardown 競態。已做 test-only cleanup，單測重跑通過。

完整 suite 第二輪在 emulator system services 壞掉後出現無效狀態：

- `am` / `package` 回 `Can't find service`
- 單測嘗試顯示 `Starting 0 tests`
- 這不是 app assertion failure；該 emulator 狀態不可作為產品判定。

處理：

- 停止殘留 Windows Gradle / UTP process。
- 將舊 `androidTest-results` 目錄改名備份，避免 Gradle MD5 cache 讀取被鎖住的輸出。
- 重啟 `LocalNotepad_API35`，等待 boot completed。

## Focused rerun after emulator restart

命令：

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#deletedOpenDrawingNoteHardwareBackNavigatesToList" --no-daemon
```

結果：

- 1 test
- 0 failed
- BUILD SUCCESSFUL in 39s

## Final full connected suite

命令：

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

結果：

- 168 tests
- 0 failures
- 0 errors
- 0 skipped
- BUILD SUCCESSFUL in 8m 26s

報告：

- `app/build/outputs/androidTest-results/connected/debug/TEST-LocalNotepad_API35(AVD) - 15-_app-.xml`

## Status

Agent G full validation 通過。drawing note enhance v2 可以交付測試。
