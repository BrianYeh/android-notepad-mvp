# Google Account Sync Setup

True Google account sync is blocked until this checklist is complete. The app must not present manual Drive file-picker backup as account sync.

## Current Phase 0 Findings

- Package/application id: `com.example.notepad`
- Namespace: `com.example.notepad`
- Debug signing report from `./gradlew.bat signingReport --no-daemon`:
  - Store: `C:\Users\sbyai\.android\debug.keystore`
  - SHA-1: `0D:7D:F6:AB:E9:79:27:82:91:F8:E0:E5:1B:95:62:DF:71:63:8C:81`
  - SHA-256: `C8:95:F4:4A:37:BE:74:81:B6:B5:60:BF:F4:AC:73:DF:14:BD:25:86:B3:63:E1:4D:00:E6:7C:A4:BB:62:8D:02`
- No `google-services.json`, OAuth client file, release keystore, or Drive API client configuration is present in the repo.
- Gradle currently has no Google Sign-In or Google Drive REST dependencies.
- The manifest currently has no `android.permission.INTERNET` permission, which real Drive sync will need.
- Existing Settings backup is a manual JSON backup through Android's file picker. It is not account sync.

## Google Cloud Checklist

1. Create or select a Google Cloud project for Just Notes.
2. Configure the OAuth consent screen:
   - App name: `Just Notes`
   - User type: internal or external as appropriate
   - Add test users while the app is in testing
   - Add scopes only after deciding the storage model below
3. Enable the Google Drive API.
4. Create an Android OAuth client:
   - Package name: `com.example.notepad`
   - Debug SHA-1: `0D:7D:F6:AB:E9:79:27:82:91:F8:E0:E5:1B:95:62:DF:71:63:8C:81`
   - Add the release SHA-1 before shipping release builds
5. Choose the Drive storage scope:
   - Preferred: `https://www.googleapis.com/auth/drive.appdata` for `appDataFolder`
   - Fallback only with product approval: `https://www.googleapis.com/auth/drive.file` for an app-managed visible Drive file
6. Download and add required local configuration:
   - If using the Google Services Gradle plugin, place `google-services.json` at `app/google-services.json`.
   - If not using the plugin, store the OAuth client id in a local, non-committed config resource generated from `local.properties` or a secrets plugin.
   - Do not commit private release keystores or secrets.

## Implementation Checklist After Setup

- Add Google Sign-In/Credential Manager and Drive API dependencies.
- Add `android.permission.INTERNET` to the manifest.
- Implement a real `DriveSyncClient` for `appDataFolder` or the approved app-managed Drive file.
- Use existing stable `syncId` fields for folders and notes.
- Preserve `deletedAt` tombstones so deletes do not revive on another device.
- Show signed-in email, last sync time, current sync status, retryable errors, and clear sign-out behavior.
- Keep manual backup/restore separate from sync because restore replaces local data.

## Validation Gate Before Calling This Account Sync

- Fresh install can sign in with Google and display the selected account email.
- Notes created on device A appear on device B after sync.
- Edits, deletes, folder moves, drawing notes, pinned state, and reminders survive a bidirectional sync pass.
- Deleted notes do not reappear after syncing another device.
- Conflict cases create a conflict copy instead of silently discarding one side.
- Sign-out clears account state but does not delete local notes.
- Permission revocation, network failure, remote corrupt JSON, and account switching show understandable errors.
- Unit tests, migration tests, build, and connected UI evidence pass on a device or emulator.
