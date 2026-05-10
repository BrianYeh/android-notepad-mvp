# Local Notepad

Native Android notepad MVP built with Kotlin, Jetpack Compose, Room, and MVVM.

## Features

- Real default folder: `Uncategorized`
- `All Notes` is a UI filter only
- Create, rename, and delete folders
- Deleting a folder moves its notes to `Uncategorized`
- Create, edit, move, and delete text notes
- Search notes by title and text note content
- Create, draw, clear, move, delete, save, and reload drawing notes
- Drawing data is stored locally in Room as serialized JSON stroke data
- In-app language selector for English and Traditional Chinese
- No login, sync, sharing, image export, or cloud storage

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

All notes and folders are stored only in the local Room database `local_notepad.db`. Android cloud backup is disabled for the app.
