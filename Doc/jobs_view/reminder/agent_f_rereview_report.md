# Agent F Re-review Report: Reminder Follow-up Fixes

Date: 2026-06-09

## Verdict: PASS

## Findings

No blocking findings.

## Confirmation Status

1. **Fixed: Calendar Add no longer offers a cross-day "Next hour" fallback for today's selected day near 23:xx.**

   `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:3374` now keeps `calendarReminderPresetOptions` available for focused instrumentation coverage, and `NotepadApp.kt:3385` computes the fallback once. The fallback is only returned when both the selected day is today and `startOfDayMillis(fallback) == dayStart` at `NotepadApp.kt:3386`, so a 23:xx selection cannot produce a 00:00 reminder on the next day. The new focused coverage asserts the empty near-midnight result at `app/src/androidTest/java/com/example/notepad/ui/ReminderCalendarPresetOptionsInstrumentedTest.kt:10` and the same-day 22:30 fallback at `ReminderCalendarPresetOptionsInstrumentedTest.kt:20`.

2. **Fixed: The calendar reminder display test no longer assumes `now + 10 minutes` is still today.**

   `app/src/androidTest/java/com/example/notepad/TextInputTest.kt:1030` still seeds a reminder at `System.currentTimeMillis() + 600_000`, but the test now derives `reminderDayStart` at `TextInputTest.kt:1031` and navigates the calendar there before asserting the row at `TextInputTest.kt:1054`. The helper at `TextInputTest.kt:162` can move either forward or backward from the current selected day baseline.

## Exact-alarm Scope

No exact-alarm scope slipped in. Search found no `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `setExact*`, `setAlarmClock`, or exact-minute/guarantee wording in app source/tests. The only alarm scheduling APIs found are the existing `setAndAllowWhileIdle` calls at `app/src/main/java/com/example/notepad/reminder/ReminderScheduler.kt:44` and `ReminderScheduler.kt:64`.

## Internal Visibility / New Test Check

Making `calendarReminderPresetOptions` internal does not introduce a serious production regression by inspection; the function remains a top-level helper in `NotepadApp.kt`, and the existing dialog call site still uses it at `NotepadApp.kt:3345`. The new instrumentation test is scoped to deterministic fixed June 9, 2026 timestamps and passed in the latest connected XML.

## Missing Tests / Residual Risks

- Full `connectedDebugAndroidTest` was not inspected as passing after the follow-up; only the focused 4-test connected result was present.
- The display test is substantially less time-fragile now, but it still assumes the calendar's initial selected day matches the helper's current-day baseline. A date rollover after calendar composition and before `moveCalendarToDay` starts could still cause a rare test flake.
- Notification-denied/channel-disabled UI persistence remains covered by helper-level tests only, not by an end-to-end UI/integration test.

## Commands / Artifacts Inspected

- `git status --short`
- `git diff --check` - passed
- `git diff --stat`
- `git diff --name-only`
- `git diff --unified=60 -- app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `git diff --unified=50 -- app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- `git diff --unified=40 -- app/src/main/java/com/example/notepad/reminder/ReminderScheduler.kt app/src/main/java/com/example/notepad/ui/UiText.kt app/src/main/java/com/example/notepad/viewmodel/NotepadViewModel.kt app/src/androidTest/java/com/example/notepad/reminder/ReminderNotificationTextTest.kt`
- `nl -ba app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `nl -ba app/src/androidTest/java/com/example/notepad/ui/ReminderCalendarPresetOptionsInstrumentedTest.kt`
- `nl -ba app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- `rg -n "setAndAllowWhileIdle|setExact|setAlarmClock|SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM|exact[- ]minute|exact minute|guaranteed|guarantee" app/src/main app/src/androidTest app/src/test -S`
- `app/build/outputs/androidTest-results/connected/debug/TEST-LocalNotepad_API35(AVD) - 15-_app-.xml` - 4 tests, 0 failures, 0 errors, 0 skipped

No code was modified, no commit/push/APK copy was performed, and no Gradle or connected test suite was run by Agent F.
