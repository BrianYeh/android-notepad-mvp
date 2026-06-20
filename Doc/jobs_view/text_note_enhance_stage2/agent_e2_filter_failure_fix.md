# Agent E2 Filter Failure Fix - Stage 2

Date: 2026-06-19 17:40 CST

## Scope

Agent E2 triaged the remaining Stage 2 full-suite failure from Agent G2:

- `TextInputTest.premiumReminderFiltersSeparateWithOverdueAndUpcomingNotes`
- Failure location: `TextInputTest.kt:1390`, during `selectReminderFilter("Upcoming")`
- Failure type: `ComposeNotIdleException` / `AppNotIdleException`, busy `Compose-Espresso link`

No production code was modified by Agent E2.

## Emulator And ADB Readiness

Checked before connected validation:

```text
/mnt/d/android/Sdk/platform-tools/adb.exe devices
List of devices attached
emulator-5554    device

/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed
1
```

Checked again before the reminder-filter subset:

```text
emulator-5554    device
1
```

No emulator restart was needed in this pass.

## Triage

The failed full-suite log showed the test had already filtered to `WithReminder` and `Overdue`, then timed out while trying to select `Upcoming`.

The failed-test logcat showed heavy IME and popup-window churn around the failure window, including Gboard input connection timeouts, IME hide/show transitions, skipped frames, and `SurfaceSyncGroup` timeout messages for popup windows. The search field uses `ImeAction.Search`, and its production `SearchBar` hides the keyboard on the Search action.

Conclusion: this looked like test idling/flakiness caused by selecting the reminder dropdown while the search field/IME was still active, not a Stage 2 mixed-checkbox product regression and not a reminder-filter production bug.

## Fix

Changed only:

- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

Added a narrow helper:

```kotlin
private fun submitSearchImeAction() {
    composeRule.onNodeWithTag("note_search_input").performImeAction()
    composeRule.waitForIdle()
}
```

Applied it after entering the search query and before opening reminder-filter controls in:

- `premiumReminderFiltersSeparateWithOverdueAndUpcomingNotes`
- `overdueReminderRepeatCanBeChangedFromTextOverflow`

This uses the app's existing Search IME action instead of adding sleeps or touching production UI.

## Validation

Hygiene:

```text
git diff --check
PASS
```

Initial focused command attempts:

- Two PowerShell wrapper attempts failed before running tests due argument quoting issues (`--no-daemon;` and lost `-P`). These were command-wrapper failures, not app/test failures.
- The working command was run through `cmd.exe /C` to preserve the Gradle `-Pandroid.testInstrumentationRunnerArguments.class=...#method` argument.

Focused failed-test rerun:

```text
gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#premiumReminderFiltersSeparateWithOverdueAndUpcomingNotes --no-daemon

Starting 1 tests on LocalNotepad_API35(AVD) - 15
Finished 1 tests on LocalNotepad_API35(AVD) - 15
BUILD SUCCESSFUL in 1m 21s
AGENT_E2_FOCUSED_EXIT_STATUS=0
```

Reminder-filter subset rerun:

```text
gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#premiumReminderFiltersSeparateWithOverdueAndUpcomingNotes,com.example.notepad.TextInputTest#overdueReminderRepeatCanBeChangedFromTextOverflow --no-daemon

Starting 2 tests on LocalNotepad_API35(AVD) - 15
Finished 2 tests on LocalNotepad_API35(AVD) - 15
BUILD SUCCESSFUL in 1m 12s
AGENT_E2_SUBSET_EXIT_STATUS=0
```

Note: the wrappers printed intended log paths, but the expected `connectedDebugAndroidTest-stage2-agent-e2*.log` files were not found in the project root afterward. The validation result above is from captured Gradle stdout.

## Review

Required Just Notes code-review gate was run with:

```text
codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review -
```

Scope was restricted to the E2 stabilization changes: `performImeAction`, `submitSearchImeAction`, and the two call sites.

Result:

```text
No actionable findings in the scoped E2 changes.
```

## Status

Agent E2 status: focused fix complete.

Agent F review: required for this E2 test-code change and completed with no actionable findings.

Agent G full connected suite rerun: still required. Agent E2 did not run the full 177-test suite by instruction.
