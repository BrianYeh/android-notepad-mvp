# Agent G3 Full Connected Rerun

Date: 2026-06-19
Workspace: `/mnt/d/AndroidStudioProjects`

## Scope

Validate the current Stage 2 dirty diff with a full `connectedDebugAndroidTest`.

G3 did not modify production or test code. The dirty diff under validation includes E2's test stabilization in `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`, including `submitSearchImeAction()` and `waitForIdle()` usage after reminder search query input. E2's scoped Codex `gpt-5.5` xhigh review was already completed with no actionable findings per:

`/mnt/d/AndroidStudioProjects/Doc/jobs_view/text_note_enhance_stage2/agent_e2_filter_failure_fix.md`

## Emulator Readiness

Initial readiness before G3 run:

- `adb devices`: `emulator-5554 device`
- `adb shell getprop sys.boot_completed`: `1`

First G3 attempt:

- Command started against the ready emulator.
- Root log: `/mnt/d/AndroidStudioProjects/connectedDebugAndroidTest-stage2-agent-g3-full-20260619-174125.log`
- Result: instrumentation aborted around 50/177 with `INSTRUMENTATION_ABORTED: System has crashed`.
- Failed/aborted test shown in output: `TextInputTest.blankNewTextDraftIsDiscardedWhenActivityStops`
- This attempt was not treated as a green validation. It also exposed a PowerShell wrapper caveat where the printed exit marker was `AGENT_G3_GRADLE_EXIT=0` despite `BUILD FAILED`.

Fresh emulator restart before corrected full rerun:

- Sent `adb -s emulator-5554 emu kill`.
- Relaunched `LocalNotepad_API35` with `-no-snapshot-load`.
- The first detached restart attempts did not appear in ADB; G3 then launched the emulator in foreground verbose mode and waited for readiness.
- Confirmed before rerun:
  - `adb devices`: `emulator-5554 device`
  - `adb shell getprop sys.boot_completed`: `1`

## Command

Corrected full rerun command, using Windows PowerShell/JBR/Android SDK from WSL:

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
$env:ANDROID_HOME = "D:\android\SDK"
$env:ANDROID_SDK_ROOT = "D:\android\SDK"
$env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"
Set-Location "D:\AndroidStudioProjects"
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

Root log:

`/mnt/d/AndroidStudioProjects/connectedDebugAndroidTest-stage2-agent-g3-full-20260619-175601-rerun.log`

## Result

Corrected full rerun completed all tests:

- Tests: `177`
- Failures: `2`
- Errors: `0`
- Skipped: `0`
- XML suite time: `477.898s`
- Gradle output: `BUILD FAILED in 8m 37s`

XML artifact:

`/mnt/d/AndroidStudioProjects/app/build/outputs/androidTest-results/connected/debug/TEST-LocalNotepad_API35(AVD) - 15-_app-.xml`

HTML report:

`/mnt/d/AndroidStudioProjects/app/build/reports/androidTests/connected/debug/index.html`

Failed tests:

1. `TextInputTest.mainScreenShowsContentFirstHomeCardWithOverflowActions`
   - Failure: `java.lang.AssertionError: Assert failed: The component is not displayed!`
   - Location: `TextInputTest.kt:2853`

2. `TextInputTest.longPressEnablesMultiSelectAndDeletesSelectedNotes`
   - Failure: `java.lang.AssertionError: Failed to inject touch input.`
   - Reason: expected exactly one node containing `Multi select first 1781863276061`, but no node matched.
   - Location: `TextInputTest.kt:1936`

Previously failing G2/E2 target:

- `TextInputTest.premiumReminderFiltersSeparateWithOverdueAndUpcomingNotes` passed in this full rerun.

## Exit Status Note

G3 avoided `tee` for the corrected rerun, but the Windows PowerShell/Gradle wrapper still printed `AGENT_G3_GRADLE_EXIT=0` even though Gradle output and XML both show `BUILD FAILED` with two test failures. G3 therefore treats the wrapper exit marker as non-authoritative for this run and uses the Gradle log/XML result as the validation status.

Effective validation status: failed.

## Gate

Full Stage 2 validation is not green.

Agent E is needed again to triage the two full-suite failures above. The current evidence does not reproduce the prior `premiumReminderFiltersSeparateWithOverdueAndUpcomingNotes` failure, and the corrected rerun did not abort with a system crash.

No APK handoff, commit, or push was performed.
