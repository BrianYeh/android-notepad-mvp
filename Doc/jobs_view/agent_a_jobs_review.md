# Agent A - Steve Jobs-Style Review

Role: review Just Notes from a Steve Jobs lens: ruthless focus on simplicity, clarity, taste, and removing confusing half-finished affordances.

No files were edited.

## 1. Clean Up Premium Screen

Problem: Premium opens with "Price not available", disabled subscribe, restore status, and billing errors before explaining value.

User impact: It feels broken instead of premium.

Precise change: When billing/prices are unavailable, hide plan rows, subscribe, restore, and billing error copy. Show only three premium benefits: Folders, text formatting, reminder/calendar tools.

Likely files/tests:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/main/java/com/example/notepad/ui/UiText.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

## 2. Hide Premium Formatting Controls For Free Users

Problem: Free users see H1/H2/B/I/U/highlight/link buttons that immediately bounce to Premium.

User impact: The editor feels like a trap instead of a writing surface.

Precise change: In free mode, show only free quick inserts like checkbox, bullet, and hide keyboard. Replace formatting buttons with one clear "Text formatting is Premium" entry, or hide them entirely until Premium.

Likely files/tests:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

## 3. Make Folder UI Disappear Until It Matters

Problem: Free/default state still shows All Notes, Uncategorized, New Folder, and Move, even though folders are Premium-only.

User impact: The app looks more complex than it is, and folder controls feel half-enabled.

Precise change: If the user is free and only has the default folder, hide the folder filter row and remove New Folder/Move from everyday surfaces. Keep folder access only for existing non-default folders or Premium.

Likely files/tests:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

## 4. Make Reminder/Calendar Gates Honest

Problem: Calendar filter and Set reminder controls appear normal, then route free users to Premium.

User impact: Users discover paywalls by failure, not clarity.

Precise change: Hide Calendar from the free filter panel, or label it Calendar Premium. In editor menus/buttons, use Set reminder Premium for free users.

Likely files/tests:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- Reminder gate tests in `TextInputTest.kt`

## 5. Split Free Import/Export From Backup/Sync

Problem: Settings mixes Google account sync, manual backup, batch export, and text import in one long section.

User impact: Import/export can look like cloud or Premium functionality even though it is free.

Precise change: Create distinct Settings sections: Google Sync, Backup & Restore, and Import / Export. Keep Import text files and Export notes ZIP visibly free and outside sync wording.

Likely files/tests:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/main/java/com/example/notepad/ui/UiText.kt`
- Settings UI assertions in `TextInputTest.kt`
