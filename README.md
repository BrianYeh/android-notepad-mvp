# Just Notes

Native Android notepad built with Kotlin, Jetpack Compose, Room, and MVVM.

## Features

- Real default folder: `Uncategorized`
- `All Notes` is a UI filter only
- Create, rename, and delete folders
- Deleting a folder moves its notes to `Uncategorized`
- Create, edit, move, pin, soft-delete, restore, and permanently delete text notes
- Friendly text note screen with a clean reading mode, edit flow, warm paper-style background, compact toolbar, and keyboard-aware editor layout
- Long-form text editor with auto-collapsing metadata, a quick insert bar, save status, last updated time, safe save-on-back behavior, and configurable editor font size
- Search notes by title and text note content with highlighted matches
- Find within a single text note with a compact search toolbar, highlighted matches, and previous/next jump controls
- Knowledge-base filters for all notes, text notes, drawing notes, notes with reminders, and pinned notes
- Knowledge-base style main header, scannable note type chips, recently updated quick sort, and visible result counts
- Create searchable OCR text notes from whiteboard, receipt, and book-page images selected through the system picker
- Create, draw, clear, undo, redo, pixel-erase, move, pin, soft-delete, restore, permanently delete, save, and reload drawing notes
- Drawing notes support separate pen sizes, larger eraser sizes, square eraser preview, and black, red, blue, and green pen colors
- Trash view for deleted notes
- Sort notes by updated time, created time, or title
- Filter notes by all types, text notes, or drawing notes
- One-time local reminders for text and drawing notes
- Reminder filters for all, with reminder, overdue, and upcoming notes
- Share a single text note through the Android share sheet
- Share drawing notes as PNG images without exporting internal drawing JSON
- Export a text note as a human-readable `.txt` file through the Android system file picker
- Export a drawing note as a PNG image through the Android system file picker
- Receive `text/plain` shares from other apps and create a new text note in `Uncategorized`
- Drawing data is stored locally in Room as serialized JSON stroke data
- App language follows the Android system language, matching ColorNote-style behavior
- Manual JSON backup and restore from Settings; restore validates the selected backup and previews its note/folder counts before replacing local data
- Sync-ready local metadata for stable folder/note IDs and tombstones
- No Google account sync, login system, or app-managed Drive storage until the OAuth/Drive setup in `GOOGLE_ACCOUNT_SYNC_SETUP.md` is completed

## Requirements

- Android Studio installed at `D:\android\Android Studio`
- Android SDK installed at `D:\android\SDK`
- Android SDK Platform 35 installed

The project is configured with:

- Kotlin
- Jetpack Compose
- Room
- Minimum SDK 26
- Compile/target SDK 35

## Build

From PowerShell in `D:\AndroidStudioProjects`:

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

## Run

Open `D:\AndroidStudioProjects` in Android Studio, let Gradle sync finish, select an emulator, then run the `app` configuration.

Or install the debug APK from PowerShell:

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
.\gradlew.bat installDebug
```

## Tests

With an emulator running:

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
.\gradlew.bat connectedAndroidTest
```

See `UI_OPTIMIZATIONS.md` for the current UI optimization checklist and manual/ADB smoke test steps.

## Storage

All notes and folders are stored in the local Room database `local_notepad.db`. Deleted notes stay in the local database until permanently deleted from Trash. Deleted folders are retained as local tombstones for future sync safety. Android cloud backup is disabled for the app. Manual backups are user-created JSON exports saved to a location selected in the Android file picker.

## Google Account Sync

True Google account sync is intentionally blocked in this repository until Google Cloud OAuth and Drive API credentials are configured. See `GOOGLE_ACCOUNT_SYNC_SETUP.md` for the required package name, debug signing fingerprints, Drive scopes, dependencies, and local configuration files. The current Settings screen is labeled as manual backup so a Drive file-picker backup is not presented as account sync.

## Notifications

Note reminders are scheduled locally with Android `AlarmManager` and fire once. Android 13 and newer require notification permission; the app requests it when setting a reminder. Future reminders are rescheduled when the app starts, after device reboot, and after restoring a backup.

## Sharing And Export

Open a text note and tap `Share` to send the title, folder, reminder time when set, last updated time, and body text through the Android share sheet. Tap `Export .txt` to choose a destination such as Downloads or Google Drive with the Android file picker.

Open a drawing note and use the drawing toolbar to choose pen or pixel-style eraser, size, and pen color. Pen sizes stay tuned for drawing lines; eraser sizes are much larger and show a square preview while pressing or dragging. The eraser stores real `ERASER` strokes and clears previous pixels during editing and PNG rendering; it is not a white pen. Undo and redo work at the stroke level, including eraser strokes. Tap `Share PNG` to send a PNG image through the Android share sheet, or `Export PNG` to save a PNG image to a destination such as Downloads or Google Drive. Drawing stroke JSON stays inside the app and is not included in shared content.

From another Android app, share plain text to `Just Notes`. The app creates a new text note in `Uncategorized` using the shared subject when available, otherwise a preview of the shared text.

## OCR

Use `Scan text from image` from the add menu to pick a photo or screenshot without storage permission. The app uses on-device ML Kit Text Recognition to turn images such as whiteboards, receipts, and book pages into searchable text notes, then opens the new text note for editing.
