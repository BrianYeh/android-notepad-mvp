# Agent B Implementation Plan: Reminder UX Improvements

## Scope for Agent D

Implement a conservative, note-based reminder UX pass. Keep reminders attached to existing `NoteEntity` records and reuse the current scheduler, receiver, premium entitlement, and editor architecture.

In scope:

- Add an obvious premium-only Reminders/Calendar entry on the home screen, outside the collapsed filter panel.
- Improve the existing premium `ReminderCalendarView` into a selected-day reminder view with date header, previous/next day controls, reminder rows, and a persistent Add action.
- Let an empty selected day create a reminder by creating a normal text note with a selected-day reminder time, then opening that note.
- Make text notes expose the same visible reminder controls already shown by checklist and drawing notes.
- Show reminder time, overdue/upcoming state, and repeat state in list/calendar reminder rows.
- Wire the existing `ReminderFilter` state into real filtering.
- Prevent the UI from claiming a reminder is set when notifications cannot be delivered because permission or channel notifications are off.

Explicit exclusions:

- No standalone reminder table or calendar-event model.
- No full calendar app features such as drag/drop events, all-day events, agenda search, invitees, location, or multi-day reminders.
- No new recurrence rules beyond existing none/daily/weekly/monthly.
- No exact-alarm permission work in this turn; keep using `setAndAllowWhileIdle` and avoid wording that promises exact-minute delivery.
- No premium entitlement redesign and no free-user exposure of home calendar/reminder premium controls.
- No APK copy, commit, push, or release work.

## File-Level Tasks

### `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`

Home entry:

- Extend `HomeHeaderSummaryRow` or the surrounding header call site to show a premium-only `Reminders` / `Calendar` action when `hasPremiumAccess && !isTrash`.
- The action should switch `contentView` to `MainContentView.Calendar`, clear note selection, and stay hidden for free users.
- Keep the existing filter-panel `calendar_view_chip` as a secondary path so current tests and discoverability are preserved.

Calendar selected-day UX:

- Refactor `ReminderCalendarView` into two clear sections: month grid and selected-day panel.
- Add selected-day panel content:
  - selected date header
  - previous-day and next-day buttons
  - Today shortcut
  - reminder count
  - reminder rows sorted by reminder time
  - persistent Add reminder button even when the day is empty
- Add an `onCreateReminderForDate(dayStart: Long)` callback from `ReminderCalendarView` up through `MainScreen`.
- Keep calendar unavailable in trash and unavailable to free users.
- For past selected days, either disable Add or show the existing "future reminder required" message; do not create an invalid reminder.

Date-specific Add behavior:

- Use a small Compose dialog/sheet or a direct default-time path, not a new calendar subsystem.
- Recommended minimal flow: Add opens a compact preset chooser for the selected day:
  - Morning 09:00
  - Afternoon 14:00
  - Evening 18:00
  - Next hour for today when presets are past
- On selection, create a text note with that reminder and open the editor.
- If the chosen timestamp is not in the future, block creation with `text.reminderMustBeFuture`.

Visible text-note reminder controls:

- In `TextEditorScreen`, add the shared `ReminderControls` component to the text note metadata area where the title, folder, save status, and current reminder status are shown.
- Preserve the existing overflow menu reminder actions for backward compatibility and current tests, but the visible metadata controls should be the primary path.
- Reuse the same premium route used today: non-premium attempts open Premium after saving the text note.

Reminder row summary:

- Add a reusable reminder summary helper for rows, for example `reminderRowSummary(note, text, appLanguage)`.
- Include time, overdue/upcoming state, and repeat label when repeat is not `None`.
- Show this as a separate visible row/line in `NoteRow` for notes with reminders instead of relying only on the long metadata string, which is currently easy to truncate.
- Use error color for overdue reminders and a normal/variant color for upcoming reminders.

Reminder filters:

- Pass `reminderFilter` into `NoteFilterRow` and render `ReminderFilterSelector` only when `hasPremiumAccess`.
- Include `reminderFilter != ReminderFilter.All` in the active-filter calculation.
- Reset `ReminderFilter` to `All` when premium access is lost, matching the existing `HasReminder` quick-filter reset.

Notification truth:

- Before persisting a reminder from `ReminderControls`, text-note reminder actions, or calendar Add, check whether notifications can actually alert:
  - Android 13+ `POST_NOTIFICATIONS` permission.
  - App notifications enabled.
  - Android O+ reminder channel not blocked / `IMPORTANCE_NONE`.
- After requesting notification permission, persist only if permission is granted and channel/app notifications are still enabled.
- If notifications are blocked, show concise user-facing copy such as "Turn on notifications to use reminders" and do not save the reminder as if it will notify.

### `app/src/main/java/com/example/notepad/viewmodel/NotepadViewModel.kt`

- Include `reminderFilter` in `baseNoteFilters` / `noteFilters`.
- Add filtering logic:
  - `All`: no change.
  - `WithReminder`: `reminderAt != null`.
  - `Overdue`: `reminderAt != null && reminderAt <= now`.
  - `Upcoming`: `reminderAt != null && reminderAt > now`.
- Use a single `now` per filter pass so overdue/upcoming behavior is stable while evaluating a list.
- Add a small creation method for calendar Add, for example `createTextNoteWithReminder(reminderAt, onCreated)`, using existing repository methods:
  - create text note in the current allowed folder
  - set reminder and normalized repeat
  - schedule via `ReminderScheduler.schedule`
  - refresh widgets
  - return/open the created note id
- Keep existing blank-draft deletion behavior intact; if the created reminder note stays blank and is discarded, existing draft cleanup should cancel the scheduled reminder.

### `app/src/main/java/com/example/notepad/reminder/ReminderScheduler.kt`

- Add a helper that reports notification delivery readiness for reminder UI, for example `notificationDeliveryStatus(context)`.
- The helper should account for runtime permission, app notifications, and reminder channel state.
- Keep `ensureNotificationChannel`, `schedule`, repeat advancement, boot reschedule, and snooze behavior otherwise unchanged.
- Do not add `SCHEDULE_EXACT_ALARM` or switch scheduling APIs in this pass.

### `app/src/main/java/com/example/notepad/reminder/ReminderReceiver.kt`

- Keep the existing permission guard before showing notifications.
- If `ReminderScheduler` gets a delivery-status helper, reuse it here only if it does not change current fired-token, repeat, or snooze behavior.

### `app/src/main/java/com/example/notepad/ui/UiText.kt`

Add localized English and Traditional Chinese strings for:

- Home Reminders/Calendar entry if the current `calendarView` label is not sufficient.
- Selected-day Add button.
- Preset labels if using a preset chooser.
- Notification blocked/channel disabled message.
- Reminder set confirmation, if Agent D includes confirmation after set.

Prefer reusing existing labels where clear enough.

### Tests

Update existing tests rather than adding a new broad suite unless needed.

- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
  - Preserve/extend the existing free-user test that verifies `calendar_view_chip` and `quick_filter_HasReminder` are absent.
  - Add a premium test that the new home Reminders/Calendar action appears and opens `reminder_calendar`.
  - Add a premium calendar test that an empty selected day shows Add and that adding a preset creates/opens a text note with visible `note_reminder_status`.
  - Add a text-note test that visible `set_reminder_button` appears in metadata, while existing overflow reminder menu still works.
  - Add reminder filter tests for With reminder, Overdue, and Upcoming using seeded notes.
  - Add row-display assertions for overdue/upcoming and repeat text.
- `app/src/androidTest/java/com/example/notepad/reminder/ReminderNotificationTextTest.kt`
  - Keep current repeat/snooze/token tests.
  - Add focused tests for any pure notification-delivery helper behavior that can be exercised without device settings.

## UX Acceptance Criteria

- Premium users can reach the reminder calendar from home with one tap without expanding Filters.
- Free users do not see the home Reminders/Calendar entry, `calendar_view_chip`, or `quick_filter_HasReminder`; existing premium routing from editor reminder controls remains intact.
- Calendar view opens to today, shows month grid context, and shows a selected-day panel with a human-readable date header.
- Previous/next day controls update the selected-day panel and keep the month grid in sync when crossing month boundaries.
- Every selected day shows an Add action. Empty future days are not dead ends.
- Adding from a future empty day creates a normal text note with the chosen reminder date/time and opens that note.
- Text, checklist, and drawing notes all have visible reminder controls in their main editing surfaces.
- Reminder rows show at least title, time, overdue/upcoming state, and repeat state when repeat is active.
- Setting a reminder when notification permission/app notifications/channel are blocked does not silently save a reminder that will never notify.
- After a reminder is set successfully, the user sees either an updated visible reminder status or a short confirmation.

## Risk Controls

Premium gate:

- Keep all home calendar/reminder entry points behind `hasPremiumAccess && !isTrash`.
- Keep existing non-premium editor behavior that routes reminder actions to Premium.
- Preserve existing tests around hidden calendar controls for free users and reminder premium routing.

Notification truth:

- Do not call `setNoteReminder` after a permission request unless permission is granted and notifications/channel are enabled.
- Keep receiver-side permission checks as a final guard.
- Avoid language like "alarm guaranteed" or "exact time" because current scheduling uses `setAndAllowWhileIdle`.

Alarm reliability:

- Reuse `ReminderScheduler.schedule`, `rescheduleFutureReminders`, `BootReceiver`, and repeat advancement.
- Do not introduce a second scheduling path from calendar Add.
- If creation and scheduling are split across calls, ensure failure does not leave an unscheduled note that appears as a working reminder.

Existing tests:

- Do not remove current test tags unless tests are updated intentionally.
- Preserve `set_reminder_menu_item`, `clear_reminder_menu_item`, `text_reminder_repeat_*`, `set_reminder_button`, `note_reminder_status`, `calendar_view_chip`, and `reminder_calendar` unless Agent D updates every dependent test in the same change.

## Verification Plan

Static/local checks:

- `git diff --check`
- Windows PowerShell from `D:\AndroidStudioProjects` with Android Studio JBR:
  - `.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon`

Focused connected checks when emulator/device is available:

- Existing reminder tests in `TextInputTest`.
- New calendar Add/filter/text-control tests.
- `ReminderNotificationTextTest`.

Manual smoke on `LocalNotepad_API35` if time allows:

- Free user: home has no Reminders/Calendar entry; editor reminder actions route to Premium.
- Debug premium: home Reminders opens calendar; empty day Add creates/open note; text note shows visible reminder controls.
- Deny notification permission and verify the app does not show a reminder as successfully set.
- Disable the reminder notification channel and verify the UI blocks or warns instead of silently saving.

Before final implementation report, Agent D should run the required separate code review for modified code per workspace instructions, or state clearly why review could not be run and what verification completed instead.
