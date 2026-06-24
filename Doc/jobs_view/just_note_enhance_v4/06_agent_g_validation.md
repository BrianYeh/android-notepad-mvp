# Agent G Validation - Checklist Use Mode

Date: 2026-06-24
Workspace: `/mnt/d/AndroidStudioProjects`

## Emulator Gate

- AVD available: `LocalNotepad_API35`
- Initial `adb devices`: no device attached.
- Launched `LocalNotepad_API35`.
- Readiness confirmed:
  - `adb devices` showed `emulator-5554	device`
  - `adb shell getprop sys.boot_completed` returned `1`

## Local Build/Test Gate

Command:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon
```

Result: PASS.

Notes:

- Build completed successfully after the final patch.
- Existing warnings remained limited to known KAPT language fallback and pre-existing deprecated icon warnings.

## Connected Focused Tests

All focused connected tests passed on `LocalNotepad_API35(AVD) - 15`:

- `TextInputTest#checklistNoteCanAddCheckAndPersistItems`
- `TextInputTest#checklistBlankAddedRowPersistsAfterImmediateBack`
- `TextInputTest#checklistReminderGateSavesDraftBeforePremium`

## APK Handoff

Fresh debug APK:

- Source: `D:\AndroidStudioProjects\app\build\outputs\apk\debug\app-debug.apk`
- Size: `66,300,769` bytes
- Timestamp: `2026-06-24 21:39`

Copied to Brian's stable Google Drive test path:

- `G:\我的雲端硬碟\01_android_app\01_note_app\app-debug.apk`

Copy result: PASS.

## Limits

The full connected instrumentation suite was not run. This pass focused on the checklist behavior changed in v4 plus the existing Premium reminder gate regression.
