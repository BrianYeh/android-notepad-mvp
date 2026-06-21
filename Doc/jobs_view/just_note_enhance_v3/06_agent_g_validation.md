# Agent G：Settings data-trust cleanup validation

日期：2026-06-21

## Emulator readiness

Connected / instrumentation tests were run only after confirming emulator readiness:

- `/mnt/d/android/Sdk/emulator/emulator.exe -list-avds` listed `LocalNotepad_API35`.
- `/mnt/d/android/Sdk/platform-tools/adb.exe devices` showed `emulator-5554 device`.
- `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed` returned `1`.

After the later full-suite abort, ADB was checked again and still showed `emulator-5554 device`; `sys.boot_completed` still returned `1`.

## Passed gates

- `git diff --check`: passed.
- `NoteUiPureFunctionTest#googleAccountSyncUiOnlyShowsForExistingConnectedAccount`: passed.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: passed.
- `TextInputTest#settingsHideGoogleSyncByDefaultAndExposeManualBackupControls`: passed on `LocalNotepad_API35`, 1 test / 0 failed.
- Agent F code review: no blocking findings.
- Codex CLI review with `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review -`: no blocking issues.

## Full connected suite attempt

Command:

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

Result:

- Started 170 tests on `LocalNotepad_API35`.
- Reached 63 tests before aborting.
- Reported 2 failures before abort:
  - `TextInputTest#drawingEditorRemembersLastPenColorAndSizeButStartsWithPen`
  - `TextInputTest#markerOnlyFindMatchTargetsRowWithoutVisibleFragment`
- Final failure message:
  - `INSTRUMENTATION_ABORTED: System has crashed.`
  - `Expected 170 tests, received 62.`

The first reported failure was a teardown/lifecycle issue:

```text
Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")
```

This happened in `ActivityScenarioRule.after(...)`, not in the v3 Settings assertions.

## Follow-up reruns

To distinguish app regression from instrumentation/emulator instability, both reported non-Settings tests were rerun individually after force-stopping the app:

- `TextInputTest#drawingEditorRemembersLastPenColorAndSizeButStartsWithPen`: passed, 1 test / 0 failed.
- `TextInputTest#markerOnlyFindMatchTargetsRowWithoutVisibleFragment`: passed, 1 test / 0 failed.

## Validation conclusion

The v3 Settings target is validated by the focused connected test and local build/unit gates. The full connected suite did not complete because the instrumentation/system crashed mid-run; the two reported tests were outside the Settings change and both passed in isolation afterward.

Residual test gap remains the one noted by Agent F: there is no connected/fake signed-in Google state UI test. The signed-in branch is covered by the pure visibility helper test plus code review.
