# Agent G Stage 2 Full Connected Rerun After E4

Date: 2026-06-19

## Scope

Validation only. No production code, test code, docs outside this report, commits, pushes, or APK delivery were modified or performed.

## Emulator / ADB Readiness

- AVD target: `LocalNotepad_API35`
- Active-suite check before launch:
  - WSL `ps -ef | rg -i 'connectedDebugAndroidTest|gradlew|gradle' || true`: no active connected suite found.
  - Windows CIM process query for `connectedDebugAndroidTest|gradlew|gradle`: no active Gradle/connected suite found.
- ADB readiness before launch:
  - `/mnt/d/android/Sdk/platform-tools/adb.exe devices` showed `emulator-5554	device`.
  - `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed` returned `1`.
- Restart steps: none. Emulator was online and boot-complete before testing.

## Command

Root log:

`/mnt/d/AndroidStudioProjects/connectedDebugAndroidTest-stage2-agent-g-full-20260619-213052.log`

Command run from WSL using Windows PowerShell:

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
$env:ANDROID_HOME = "D:\android\SDK"
$env:ANDROID_SDK_ROOT = "D:\android\SDK"
$env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"
Set-Location "D:\AndroidStudioProjects"
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

The actual invocation teed stdout/stderr to the root log above.

## Result

- Gradle task: `:app:connectedDebugAndroidTest` failed.
- Console/log summary: `BUILD FAILED in 9m 46s`.
- Runner started the expected full suite: `Starting 177 tests on LocalNotepad_API35(AVD) - 15`.
- XML summary: `tests="49" failures="2" errors="0" skipped="0"`.
- Abort summary: `Test run failed to complete. Expected 177 tests, received 48. onError: commandError=false message=INSTRUMENTATION_ABORTED: System has crashed.`

## Failures

1. `com.example.notepad.TextInputTest.checklistBlankAddedRowPersistsAfterImmediateBack`
   - Root-cause note: ActivityScenario teardown did not reach destroyed state.
   - Primary failure: `java.lang.AssertionError: Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")`
   - Stack points through `ActivityScenario.close` and `ActivityScenarioRule.after`.

2. `com.example.notepad.TextInputTest.premiumTextFormattingAccessoryChromeOmitsOldRawLabels`
   - Root-cause note: the emulator/system process died while the next test was launching, causing instrumentation abort.
   - XML has an empty failure body for this test, but `test-results.log` records `INSTRUMENTATION_ABORTED: System has crashed`.
   - Logcat around the abort shows `system_server` pid 601 killed, `DeadSystemException`, zygote exit because system server terminated, and app/instrumentation fatal JNI output caused by pending `DeadSystemRuntimeException`.

## Gate Verdict

FAIL / RED.

The full 177-test connected gate did not complete. It failed after the ActivityScenario teardown assertion and then aborted due to emulator/system crash during the next test.

## Suggested Next Owner

Return to the Stage 2 test-stabilization owner for `TextInputTest` teardown handling around checklist/read-mode navigation. If that owner determines the second failure is purely emulator infrastructure, rerun the full gate only after emulator restart and after the teardown failure is addressed or explicitly waived.
