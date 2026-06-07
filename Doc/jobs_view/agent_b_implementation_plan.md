# Agent B - Implementation Plan

Role: turn Agent A's five suggestions into a concrete implementation plan for Just Notes.

No files were edited.

## Product Rules

- Premium-only: folders, text formatting, reminder/calendar tools.
- Free: import/export, share/export note files, drawing PNG export, checklist notes, OCR.
- Hidden/not implemented: writing assistant.

## 1. Premium Screen

Files:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/main/java/com/example/notepad/ui/UiText.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

Behavior:

- When `billingState.billingAvailable` is false or either price is null, render a benefits-only Premium page.
- Hide `annual_plan_option`, `monthly_plan_option`, `premium_subscribe_button`, and `premium_restore_button`.
- Hide billing error, renewal, and trial copy.
- Show exactly three benefits: Folders, Text formatting, Reminder/calendar tools.
- Do not show import/export, checklist, writing assistant, or old planning-tools wording.
- Keep commerce UI if billing is available and both prices are present.

Tests:

- Update `premiumTabShowsSubscriptionPreview`.
- Assert plan/subscription/restore tags are absent in the current no-billing test environment.
- Assert `premium_folder_sample`, `premium_formatting_sample`, and `premium_schedule_sample` are visible.
- Keep assertions that Writing assistant, Checklist notes, and import/export are absent.

## 2. Free Formatting Controls

Files:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

Behavior:

- In `TextEditorAccessoryBar`, free users keep checkbox, bullet, and hide keyboard.
- Hide H1/H2/B/I/U/highlight/link/clear formatting buttons for free users.
- Show one `formatting_premium_entry_button` labelled clearly, such as "Text formatting Premium".
- Clicking it saves the note through the existing Premium navigation flow and opens Premium.
- Premium/debug premium shows the existing full formatting toolbar unchanged.
- Keep `requirePremiumFormatting()` as a defensive guard.

Tests:

- Update `textFormattingControlsRouteNonPremiumUsersToPremium`.
- Assert free quick controls remain visible.
- Assert individual formatting tags are absent.
- Click `formatting_premium_entry_button` and verify draft text survives.
- Keep premium/debug formatting persistence tests.

## 3. Folder UI Disappears Until Needed

Files:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

Behavior:

- Define `hasNonDefaultFolders = folders.any { it.id != DEFAULT_FOLDER_ID }`.
- Define `showFolderUi = hasPremiumAccess || hasNonDefaultFolders`.

For free users with only the default folder:

- Hide `folder_filter_row`.
- Hide selected folder action row.
- Hide `new_folder_menu_item`.
- Hide note-row Move.
- Hide folder picker buttons in text/checklist/drawing editors when the current folder is default.
- If `selectedFolderId` is non-null while folder UI is hidden, clear it with `onSelectFolder(null)`.

For free users with existing non-default folders:

- Show folder filter row so legacy/imported foldered notes remain reachable.
- Do not allow create/rename/delete folders.
- Show Move only for notes currently in non-default folders, so users can move them back to default.
- In `MoveNoteDialog`, free users should only see/select the default folder.

For premium/debug premium:

- Existing folder create/filter/rename/delete/move behavior remains.

Tests:

- Replace the prior free folder creation paywall test with a free-default hidden UI test.
- Assert add menu does not expose `new_folder_menu_item`.
- Assert `folder_filter_row` is absent on home.
- Assert default-note Move is absent.
- Add or adjust a debug-premium test showing `new_folder_menu_item` and `folder_filter_row`.
- Optional legacy test: seed a non-default folder/note and verify free users can reach it and move it back to default.

## 4. Reminder/Calendar Gates

Files:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

Behavior:

- Hide Calendar for free users.
- Pass `showCalendarView = !isTrash && hasPremiumAccess` into `NoteFilterRow`.
- Keep the existing effect that returns free users from Calendar to List.
- In text-note overflow, label the reminder action for free users as "Set reminder Premium"; clicking still saves draft and opens Premium.
- In checklist/drawing `ReminderControls`, label the button "Set reminder Premium" for free users.
- Keep Clear reminder free when an existing reminder exists.
- Hide repeat chips/menu entries for free users.
- Do not gate the passive Has Reminder quick filter in this pass.

Tests:

- Update reminder gate tests to expect the premium reminder label and save-before-premium behavior.
- Add a free filter-panel assertion that `calendar_view_chip` is absent.
- Update `reminderCalendarShowsTodayReminder` to enable debug premium first.

## 5. Split Settings Import/Export From Backup/Sync

Files:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/main/java/com/example/notepad/ui/UiText.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

Behavior:

- Settings sections should be visually distinct:
- Google Account Sync: existing Google sign-in/sync block.
- Backup & Restore: manual JSON backup/restore, backup target, restore rollback, auto-overwrite, choose/change backup file, forget file.
- Import / Export: only `batch_export_button` and `batch_import_button`, with wording that does not mention sync or backup.
- Import/export remains visible/free for non-premium users.

Tests:

- Update `settingsExposeManualBackupControls`.
- Assert `google_account_sync_title`, backup/restore title/status, and `import_export_title`.
- Assert `backup_button`, `restore_button`, `choose_sync_file_button`, `batch_export_button`, and `batch_import_button` are visible.
- Assert import/export controls are visible without premium override.

## Verification Plan

Run focused connected tests first:

- `TextInputTest#premiumTabShowsSubscriptionPreview`
- `TextInputTest#textFormattingControlsRouteNonPremiumUsersToPremium`
- `TextInputTest#reminderControlsRouteNonPremiumUsersToPremium`
- `TextInputTest#settingsExposeManualBackupControls`

Then run the full `TextInputTest` if emulator time allows.

Since code will be modified, run `codex xhigh/review` before reporting complete, per project rules.
