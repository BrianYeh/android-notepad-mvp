# Local Notepad

Native Android notepad MVP built with Kotlin, Jetpack Compose, Room, and MVVM.

## Features

- Real default folder: `Uncategorized`
- `All Notes` is a UI filter only
- Create, rename, and delete folders
- Deleting a folder moves its notes to `Uncategorized`
- Create, edit, move, pin, soft-delete, restore, and permanently delete text notes
- Long-form text editor with save status, last updated time, and configurable editor font size
- Search notes by title and text note content with highlighted matches
- Find within a single text note, highlight all matches, and jump to previous or next matches
- Knowledge-base filters for all notes, text notes, drawing notes, notes with reminders, and pinned notes
- Recently updated quick sort and visible result counts
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
- In-app language selector for English and Traditional Chinese
- Manual JSON backup and restore from Settings; choose Google Drive in the Android file picker to store a backup
- No automatic sync, login system, image export, or app-managed cloud storage

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

## Storage

All notes and folders are stored in the local Room database `local_notepad.db`. Deleted notes stay in the local database until permanently deleted from Trash. Android cloud backup is disabled for the app. Manual backups are user-created JSON exports saved to a location selected in the Android file picker.

## Notifications

Note reminders are scheduled locally with Android `AlarmManager` and fire once. Android 13 and newer require notification permission; the app requests it when setting a reminder. Future reminders are rescheduled when the app starts, after device reboot, and after restoring a backup.

## Sharing And Export

Open a text note and tap `Share` to send the title, folder, reminder time when set, last updated time, and body text through the Android share sheet. Tap `Export .txt` to choose a destination such as Downloads or Google Drive with the Android file picker.

Open a drawing note and use the drawing toolbar to choose pen or pixel-style eraser, size, and pen color. Pen sizes stay tuned for drawing lines; eraser sizes are much larger and show a square preview while pressing or dragging. The eraser stores real `ERASER` strokes and clears previous pixels during editing and PNG rendering; it is not a white pen. Undo and redo work at the stroke level, including eraser strokes. Tap `Share PNG` to send a PNG image through the Android share sheet, or `Export PNG` to save a PNG image to a destination such as Downloads or Google Drive. Drawing stroke JSON stays inside the app and is not included in shared content.

From another Android app, share plain text to `Local Notepad`. The app creates a new text note in `Uncategorized` using the shared subject when available, otherwise a preview of the shared text.

## OCR

Use `OCR from image` from the add menu to pick a photo or screenshot without storage permission. The app uses on-device ML Kit Text Recognition to turn images such as whiteboards, receipts, and book pages into searchable text notes, then opens the new text note for editing.
