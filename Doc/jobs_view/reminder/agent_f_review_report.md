# Agent F Review Report: Reminder Feature After Agent E

Date: 2026-06-09

## Verdict: CHANGES_REQUESTED

## Findings

1. **Selected-day Add can create a reminder on the wrong day near midnight.**

   `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:3385` falls back to `nextHourReminderTime(nowMillis)` for today's selected day whenever the 09:00/14:00/18:00 presets are past. `nextHourReminderTime` rounds to the next top-of-hour at `NotepadApp.kt:7703`, so at 23:xx the "Next hour" option becomes 00:00 on the following day. That violates the calendar Add contract: the user selected today's row, but the created reminder belongs to tomorrow.

   Concrete fix for Agent E: keep fallback options within `dayStart` by checking `startOfDayMillis(fallback) == dayStart`. If no same-day future preset remains, disable Add for today or show `reminderMustBeFuture`; alternatively add a same-day near-future option that cannot cross midnight. Add a focused test that sets `nowMillis` near 23:xx for `calendarReminderPresetOptions` and asserts no option is returned for another day.

2. **A new calendar test is time-fragile around midnight.**

   `app/src/androidTest/java/com/example/notepad/TextInputTest.kt:1001` seeds `todayReminderAt` as `System.currentTimeMillis() + 600_000`, but the test leaves the calendar on the default selected day and asserts the note is visible there. In the last ten minutes of a day, that timestamp is tomorrow, while `ReminderCalendarView` groups rows by `startOfDayMillis` at `NotepadApp.kt:3091` and defaults selection to today's `todayStart` at `NotepadApp.kt:3084`.

   Concrete fix for Agent E: either choose a future timestamp that is guaranteed to remain on the selected date, or navigate the calendar to `startOfDayMillis(todayReminderAt)` before asserting. The test name should avoid "today" if the seeded reminder can cross days.

## Missing Tests / Residual Risks

- The notification-delivery helper has pure status coverage, and I confirmed the changed UI paths gate text overflow, shared reminder controls, repeat changes, and calendar Add before persistence. There is still no UI/integration test that denies `POST_NOTIFICATIONS` or blocks app/channel notifications and asserts `reminderAt` remains null.
- Full `connectedDebugAndroidTest` still has not been run after these changes, per Agent E's report.
- Receiver behavior when notifications are disabled after a reminder was already saved remains intentionally unchanged.

## Confirmed By Inspection

- No exact-alarm scope slipped in: no `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `setExact*`, `setAlarmClock`, or exact-minute/guarantee copy was found.
- Calendar rows are no longer narrowed by active search/reminder filters: `calendarNotes` is derived from `allNotes` by folder/trash state only, then passed to `ReminderCalendarView`.
- Text-note edit metadata uses the shared `ReminderControls` and does not create duplicate `note_reminder_status` tags in the same edit screen.
- Reminder filter wiring uses a single `now` per filter pass and resets premium-only reminder filters when premium access is lost.

## Commands / Artifacts Inspected

- `AGENTS.md`
- `Doc/jobs_view/reminder/agent_b_implementation_plan.md`
- `Doc/jobs_view/reminder/agent_c_plan_review.md`
- `Doc/jobs_view/reminder/agent_d_implementation_report.md`
- `Doc/jobs_view/reminder/agent_e_fix_report.md`
- `git status --short`
- `git diff --stat`
- `git diff --name-status`
- `git diff -- app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `git diff -- app/src/main/java/com/example/notepad/ui/UiText.kt`
- `git diff -- app/src/main/java/com/example/notepad/viewmodel/NotepadViewModel.kt`
- `git diff -- app/src/main/java/com/example/notepad/reminder/ReminderScheduler.kt`
- `git diff -- app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- `git diff -- app/src/androidTest/java/com/example/notepad/reminder/ReminderNotificationTextTest.kt`
- `git diff --check` - passed
- `rg -n "SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM|setExact|setExactAndAllowWhileIdle|setAlarmClock|exact[- ]minute|exact minute|guaranteed" app/src/main app/src/androidTest app/src/test` - no matches

No code was modified, no commit/push/APK copy was performed, and no Gradle or connected test suite was run by Agent F.
