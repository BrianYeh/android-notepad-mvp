# Agent C Plan Review: Reminder UX Improvements

Date: 2026-06-09

Plan reviewed: `Doc/jobs_view/reminder_feature/agent_b_implementation_plan.md`

Final verdict: APPROVE_WITH_CHANGES

## Summary

Agent B's plan is broadly feasible and aligned with Brian's ColorNote reference: keep reminders attached to normal notes, improve the premium calendar into a selected-day reminder view, make Add persistent, and avoid building a standalone calendar system.

The plan should not be implemented as a broad reminder/calendar rewrite. Agent D should implement a constrained first pass focused on the home entry, selected-day calendar UX, text-note reminder discoverability, existing reminder filters, and truthful notification readiness. Exact alarm work, full calendar-app features, and deep settings recovery should be deferred.

## Key Findings

1. Notification truth needs stricter constraints before implementation.

   Current code requests `POST_NOTIFICATIONS`, but the callback saves the reminder regardless of whether the user granted permission in both text-note overflow flow and shared `ReminderControls`. Current code also does not check app-level notification blocking or the reminder channel importance. Agent B correctly identifies this risk, but Agent D must implement it as a single shared gate before any reminder persistence.

   Android 13+ requires the `POST_NOTIFICATIONS` runtime permission for non-exempt notifications; if the user denies it, the app cannot send notifications and notification channels are blocked. Android 8+ notification channels are required, users can control channel behavior, and app-level blocking can be checked with `NotificationManagerCompat.areNotificationsEnabled()`. Sources: Android notification permission docs, notification channel docs, and `NotificationManagerCompat` API docs.

2. Premium gating is mostly correct today, but the new entry must not bypass it.

   Existing calendar access is hidden behind `hasPremiumAccess` in the filter panel, and non-premium `MainContentView.Calendar` is reset back to list. Agent D should make rendering and the new home entry explicitly require `hasPremiumAccess && !isTrash`. The new home action must stay hidden for free users, hidden in Trash, and must not expose `calendar_view_chip` or `quick_filter_HasReminder` to free users.

3. Alarm reliability is acceptable only if copy stays honest.

   The existing scheduler uses `AlarmManager.setAndAllowWhileIdle` and has boot rescheduling through `BootReceiver`. That is a reasonable conservative path for this pass. Android documents that `setAndAllowWhileIdle` may be batched and delayed, especially in idle modes, while exact APIs require separate exact-alarm considerations. Agent D must not add `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `setExact*`, or `setAlarmClock` in this pass, and must not add UI copy that promises exact-minute delivery.

4. Text-note reminder controls are feasible, but duplicate tags are a test risk.

   Text notes already show `note_reminder_status` in edit metadata and read mode, while checklist and drawing editors use `ReminderControls`. If Agent D adds `ReminderControls` to text edit metadata, they should replace or fold in the existing edit-mode status line, not create two `note_reminder_status` nodes in the same screen. Existing tests use that tag directly and can become ambiguous.

5. Reminder filters are low risk if wired narrowly.

   `ReminderFilter` already exists as ViewModel state and localized UI text, but it is not part of the note filter pipeline or `NoteFilterRow`. Wiring it into filtering is feasible. Agent D should use one `now` value per filter pass and include `reminderFilter != All` in active-filter state and empty-state logic.

6. Calendar Add can create reminder notes, but it must use new-draft cleanup.

   Calendar Add should create a normal text note with a reminder and open it as a new text draft. Opening it without the existing `isNewDraft = true` flow would leave blank reminder notes behind if the user backs out without typing. Existing blank-draft cleanup cancels scheduled reminders when the blank draft is discarded, so Agent D should preserve that path.

## Implement Now

- Premium-only home Reminders/Calendar entry outside the collapsed filter panel.
- Selected-day calendar UX: month grid, selected-day highlight, date title, previous/next day controls, Today, count, reminder rows, and persistent Add.
- Minimal calendar Add flow for future reminders only. Presets are acceptable if they are small and do not become a scheduling subsystem.
- Text-note visible reminder controls in edit metadata, while preserving existing overflow reminder actions.
- Reminder row summary for notes with reminders, including time, overdue/upcoming state, and repeat state when active.
- `ReminderFilter` wiring in the ViewModel and filter UI for premium users.
- Shared notification delivery readiness check used by text overflow, shared `ReminderControls`, and calendar Add.
- Focused test updates around existing tags and behavior.

## Defer

- Standalone reminder table, event model, all-day events, multi-day events, agenda search, drag/drop, invitees, locations, or calendar import/export.
- Exact alarm permission work and exact-alarm APIs.
- A full visual clone of ColorNote, floating-card animation polish, or broad calendar redesign beyond the selected-day card/panel behavior.
- Deep notification settings recovery flows, such as routing to channel settings, unless Agent D can add them without expanding scope. A clear blocked message is enough for this pass.
- Automated tests that depend on mutating real device notification-channel settings. Prefer pure helper tests plus manual smoke notes.
- Broad refactors of editor architecture, billing, notification receiver token logic, recurrence logic, or scheduler internals.

## Required Constraints For Agent D

1. Keep all new calendar/reminder home entry points behind `hasPremiumAccess && !isTrash`. Prefer also rendering `ReminderCalendarView` only when premium is true, not just relying on a reset effect.

2. Preserve existing test tags unless every dependent test is intentionally updated in the same change: `calendar_view_chip`, `quick_filter_HasReminder`, `reminder_calendar`, `calendar_selected_day_count`, `set_reminder_menu_item`, `clear_reminder_menu_item`, `text_reminder_repeat_*`, `set_reminder_button`, and `note_reminder_status`.

3. Add stable tags for new core controls so tests do not rely on text labels. Recommended tags: `home_reminders_button`, `calendar_selected_day_title`, `calendar_previous_day`, `calendar_next_day`, and `calendar_add_reminder`.

4. Calendar Add must check notification readiness before creating or saving a reminder. If runtime permission is needed, request it first; after the callback, persist only when the result is granted and app/channel notifications are still enabled.

5. The notification readiness helper must ensure the reminder channel exists before checking its importance. It must account for:
   - Android 13+ `POST_NOTIFICATIONS`.
   - App-level notification blocking via `NotificationManagerCompat.areNotificationsEnabled()`.
   - Android O+ reminder channel blocked state, where channel importance is `IMPORTANCE_NONE`.

6. Do not call `viewModel.setNoteReminder` or create a calendar reminder note when notifications are blocked. Show concise user-facing copy such as "Turn on notifications to use reminders."

7. Use one shared reminder-submission path for text overflow, text metadata controls, checklist/drawing `ReminderControls`, and calendar Add, or keep wrappers thin enough that the permission-result bug cannot recur.

8. When adding text-note `ReminderControls`, avoid duplicate visible `note_reminder_status` nodes in the text editor. Replace the existing edit-mode status line or make the shared component configurable.

9. Calendar Add must only create future reminders. For today, hide/disable past presets and use a future fallback such as next hour. For past selected days, disable Add or show the existing future-reminder validation message.

10. Calendar-created text notes must open as `AppScreen.TextEditor(noteId, isNewDraft = true)` so blank-draft discard can remove the note and cancel the scheduled reminder.

11. Keep scheduling on the existing `ReminderScheduler.schedule` path. Do not add a second alarm scheduling path. If create-and-schedule is wrapped in a new ViewModel method, it must not leave a persisted reminder that was never scheduled.

12. Do not add exact-alarm permissions or exact alarm APIs. Keep copy to "reminder" and "upcoming/overdue"; do not promise "exact time" or "guaranteed alarm."

13. Reminder filters must reset to `All` when premium access is lost, matching the existing `HasReminder` quick-filter reset.

14. Add only focused tests:
   - free users do not see home Reminders/Calendar, `calendar_view_chip`, or `quick_filter_HasReminder`;
   - premium home Reminders/Calendar opens `reminder_calendar`;
   - selected empty future day shows Add and creates/opens a text draft with visible reminder status;
   - text edit metadata shows `set_reminder_button` while overflow actions still exist;
   - With reminder, Overdue, and Upcoming filters work with seeded notes;
   - row summary exposes overdue/upcoming and repeat text;
   - notification readiness helper logic is covered through pure status cases.

## Evidence Reviewed

- Current premium/calendar gate and filter chip flow: `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`.
- Current calendar view and selected-day reminder list: `ReminderCalendarView` in `NotepadApp.kt`.
- Current text overflow reminder flow and permission callback: `TextEditorScreen` in `NotepadApp.kt`.
- Current shared checklist/drawing reminder controls: `ReminderControls` in `NotepadApp.kt`.
- Current filter pipeline and reminder scheduling method: `app/src/main/java/com/example/notepad/viewmodel/NotepadViewModel.kt`.
- Current scheduler, receiver, and boot reschedule behavior: `app/src/main/java/com/example/notepad/reminder/ReminderScheduler.kt`, `ReminderReceiver.kt`, and `BootReceiver.kt`.
- Current manifest permissions: `app/src/main/AndroidManifest.xml`.
- Current instrumentation reminder tests: `app/src/androidTest/java/com/example/notepad/TextInputTest.kt` and `app/src/androidTest/java/com/example/notepad/reminder/ReminderNotificationTextTest.kt`.
- Android docs:
  - `https://developer.android.com/develop/ui/compose/notifications/notification-permission`
  - `https://developer.android.com/develop/ui/compose/notifications/channels`
  - `https://developer.android.com/reference/androidx/core/app/NotificationManagerCompat`
  - `https://developer.android.com/reference/android/app/AlarmManager`
