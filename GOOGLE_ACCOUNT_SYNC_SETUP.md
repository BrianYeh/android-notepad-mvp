# Google Account Sync Setup

Google account sync now has Android-side implementation and the Settings entry is visible in release builds. Release readiness still depends on completing the Google Cloud checklist and validating the hardening cases below on real accounts/devices. The app must keep manual Drive file-picker backup separate from account sync.

## Current Phase 0 Findings

- Package/application id: `com.brianyeh.justnotes`
- Namespace: `com.example.notepad`
- Debug signing report from `./gradlew.bat signingReport --no-daemon`:
  - Store: `C:\Users\sbyai\.android\debug.keystore`
  - SHA-1: `0D:7D:F6:AB:E9:79:27:82:91:F8:E0:E5:1B:95:62:DF:71:63:8C:81`
  - SHA-256: `C8:95:F4:4A:37:BE:74:81:B6:B5:60:BF:F4:AC:73:DF:14:BD:25:86:B3:63:E1:4D:00:E6:7C:A4:BB:62:8D:02`
- No `google-services.json`, release keystore, or release OAuth client configuration is present in the repo.
- Play-distributed builds require the Google Play App Signing certificate SHA-1 in the Android OAuth client. The upload key SHA-1 is not enough after Play re-signs the app.
- Gradle now includes Google Sign-In and Google Drive REST dependencies.
- The manifest includes `android.permission.INTERNET` for Drive sync.
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
   - Package name: `com.brianyeh.justnotes`
   - Debug SHA-1: `0D:7D:F6:AB:E9:79:27:82:91:F8:E0:E5:1B:95:62:DF:71:63:8C:81`
   - Add the Play Console app signing SHA-1 before testing Play-installed builds:
     - Play Console > Just Notes > Setup > App integrity > App signing key certificate > SHA-1 certificate fingerprint
   - Add the upload key SHA-1 too if you sideload locally signed release artifacts outside Play
5. Choose the Drive storage scope:
   - Preferred: `https://www.googleapis.com/auth/drive.appdata` for `appDataFolder`
   - Fallback only with product approval: `https://www.googleapis.com/auth/drive.file` for an app-managed visible Drive file
6. Add required local configuration only if the chosen Google auth flow requires it:
   - The current Android implementation uses Google Sign-In + Drive `appDataFolder`; it needs the Android OAuth client registered with the package name and SHA-1.
   - A `google-services.json` file is not committed and is not required by the current code path unless the app later adopts the Google Services Gradle plugin or Firebase-backed config.
   - Do not commit private release keystores or secrets.

## Implementation Checklist After Setup

- Google Sign-In and Drive API dependencies are present.
- `android.permission.INTERNET` is present.
- `GoogleDriveSyncClient` writes append-only `just-notes-sync-v1-*.json` snapshots in Drive `appDataFolder` and merges all matching snapshots on read.
- Remote snapshots are never overwritten, which avoids Drive ETag availability issues and concurrent-device overwrite risk.
- Existing stable `syncId` fields are used for folders and notes.
- `deletedAt` tombstones are preserved for normal soft deletes.
- Permanent note deletes are retained as lightweight sync tombstones so another device cannot revive a purged note.
- Settings shows signed-in email, current sync status, retryable errors, and sign-out behavior.
- Settings shows the Google account sync sign-in entry on a fresh release install.
- Keep manual backup/restore separate from sync because restore replaces local data.

## Remaining Product Risks

- Google Sign-In uses the legacy Play Services API because it is the smallest compatible path for Drive scope consent in this app. It compiles with deprecation warnings; a future pass should evaluate Credential Manager if Drive scope support fits the UX.
- appDataFolder can accumulate multiple small sync snapshots over time. A future cleanup pass can compact old snapshots after connected-device validation.
- Remote corrupt JSON, permission revocation, and network failure are surfaced as sync errors, but they still need real-device UX validation.

## Validation Gate Before Calling This Account Sync

- Fresh install can sign in with Google and display the selected account email.
- Notes created on device A appear on device B after sync.
- Edits, deletes, folder moves, drawing notes, pinned state, and reminders survive a bidirectional sync pass.
- Deleted notes do not reappear after syncing another device.
- Conflict cases create a conflict copy instead of silently discarding one side.
- Sign-out clears account state but does not delete local notes.
- Permission revocation, network failure, remote corrupt JSON, and account switching show understandable errors.
- Unit tests, migration tests, build, and connected UI evidence pass on a device or emulator.
