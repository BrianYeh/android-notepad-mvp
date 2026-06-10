# Agent G Full Connected Android Test Report

- Verdict: FAIL
- Generated: 2026-06-10 00:10:08 +0800 CST
- Exact command run:
  `/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat connectedDebugAndroidTest --no-daemon'`
- Device/emulator used: `emulator-5554`, `LocalNotepad_API35(AVD) - 15`; `adb devices -l` reported `product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa`.

## Result Artifacts

- XML result: `app/build/outputs/androidTest-results/connected/debug/TEST-LocalNotepad_API35(AVD) - 15-_app-.xml`
  - XML timestamp: `2026-06-09T16:09:15`
  - File modified: `2026-06-10 00:09:15.734224100 +0800`
- HTML report: `app/build/reports/androidTests/connected/debug/index.html`
  - File modified: `2026-06-10 00:09:16.593329400 +0800`

## Test Summary

- Total: 129
- Failures: 5
- Errors: 0
- Skipped: 0
- Gradle summary: `BUILD FAILED in 4m 54s`; `:app:connectedDebugAndroidTest` reported 5 test failures after finishing all 129 tests.

## Failing Tests

1. `com.example.notepad.TextInputTest.textNoteTitleAndContentAcceptInput`
   - Error: `java.lang.AssertionError: Assert failed: The component is not displayed!`
   - Location: `TextInputTest.kt:392`
2. `com.example.notepad.TextInputTest.findInNoteNextScrollsReadViewportAndNavigatesEditMatches`
   - Error: `java.lang.AssertionError: Failed to inject touch input. Reason: Expected exactly '1' node but found '2' nodes that satisfy: (Text + EditableText contains 'Find scroll title 1781021149463' (ignoreCase: false))`
   - Location: `TextInputTest.kt:1753`
3. `com.example.notepad.TextInputTest.headingFormattingPersistsAfterLeavingAndReopeningNote`
   - Error: `java.lang.AssertionError: Assert failed: The component is not displayed!`
   - Location: `TextInputTest.kt:867`
4. `com.example.notepad.TextInputTest.newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode`
   - Error: `java.lang.AssertionError: Assert failed: The component is not displayed!`
   - Location: `TextInputTest.kt:1217`
5. `com.example.notepad.TextInputTest.highlightLinkAndClearFormattingPersistThroughEditor`
   - Error: `java.lang.AssertionError: Assert failed: The component is not displayed!`
   - Location: `TextInputTest.kt:800`

## Ownership And Next Action

- Likely owner: D/E/code, not infrastructure. The emulator booted, the suite completed all 129 tests, and the failures are Compose UI assertions in `TextInputTest`.
- Recommended next action: investigate the text-note editor/read-mode visibility and duplicate-title semantics issues, then rerun the affected focused tests before rerunning the full connected suite.
