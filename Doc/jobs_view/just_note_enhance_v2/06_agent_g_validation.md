# Agent G：Validation 與交付報告

日期：2026-06-21

## Emulator gate

- AVD：`LocalNotepad_API35`
- `adb devices`：`emulator-5554 device`
- `adb shell getprop sys.boot_completed`：`1`

曾遇到一次 emulator/system crash 與 install-write failure；那輪發生在最新修正完成前，因此視為 invalid run。之後已重啟 emulator、確認 online 與 boot complete，再重跑正式驗證。

## 驗證結果

- `git diff --check`：通過
- `testDebugUnitTest assembleDebugAndroidTest`：通過，`BUILD SUCCESSFUL in 20s`
- focused connected test：
  - `TextInputTest#premiumFallbackHidesCommerceAndShowsAllowedBenefits`
  - 結果：1 test，0 failed，`BUILD SUCCESSFUL in 1m 8s`
- full connected suite：
  - `connectedDebugAndroidTest`
  - 結果：170 tests，0 failed，`BUILD SUCCESSFUL in 8m 22s`

## APK 交付

- 來源：`D:\AndroidStudioProjects\app\build\outputs\apk\debug\app-debug.apk`
- 目的地：`G:\我的雲端硬碟\01_android_app\01_note_app\app-debug.apk`
- SHA256：`D05B1452BD3A05F14E389F3CCBE33D48EBCA1E55CD34E154699123D4DF6B9459`

來源與目的地 hash 一致。
