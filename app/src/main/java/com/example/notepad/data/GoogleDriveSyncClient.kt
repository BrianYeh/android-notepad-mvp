package com.example.notepad.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import java.io.IOException
import java.util.Locale

class GoogleDriveSyncClient(
    private val context: Context,
) : DriveSyncClient {
    private val appContext = context.applicationContext
    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    private var account: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(appContext)
        ?.takeIf { GoogleSignIn.hasPermissions(it, Scope(DriveScopes.DRIVE_APPDATA)) }
    private var drive: Drive? = null

    override val accountEmail: String?
        get() = account?.email

    fun signInIntent(): Intent {
        return GoogleSignIn.getClient(appContext, signInOptions).signInIntent
    }

    fun connect(account: GoogleSignInAccount) {
        this.account = account
        drive = null
    }

    fun disconnect() {
        account = null
        drive = null
        GoogleSignIn.getClient(appContext, signInOptions).signOut()
    }

    override suspend fun readSnapshot(): DriveSyncResult<RemoteSyncSnapshot?> {
        val service = driveService()
        if (service is DriveSyncResult.Failure) return service
        service as DriveSyncResult.Success

        return try {
            val files = service.value.syncFiles()
            if (files.isEmpty()) {
                return DriveSyncResult.Success(null)
            }
            val snapshots = files.mapNotNull { file ->
                readSnapshotFile(service.value, file)
            }
            val mergedSnapshot = RemoteSnapshotConsolidator.consolidate(
                snapshots = snapshots,
                now = System.currentTimeMillis(),
            ) ?: return DriveSyncResult.Failure(
                SyncError(
                    code = SyncErrorCode.RemoteDataCorrupt,
                    message = "Google Drive sync data is corrupt.",
                ),
            )
            DriveSyncResult.Success(mergedSnapshot)
        } catch (_: IllegalArgumentException) {
            DriveSyncResult.Failure(
                SyncError(
                    code = SyncErrorCode.RemoteDataCorrupt,
                    message = "Google Drive sync data is corrupt.",
                ),
            )
        } catch (exception: Exception) {
            DriveSyncResult.Failure(exception.toSyncError())
        }
    }

    override suspend fun writeSnapshot(snapshot: RemoteSyncSnapshot): DriveSyncResult<Unit> {
        val service = driveService()
        if (service is DriveSyncResult.Failure) return service
        service as DriveSyncResult.Success

        return try {
            val json = SyncSnapshotJson.encode(snapshot)
            val content = ByteArrayContent.fromString("application/json", json)
            val metadata = DriveFile()
                .setName(snapshotFileName(snapshot))
                .setMimeType("application/json")
                .setParents(listOf("appDataFolder"))
            service.value.files()
                .create(metadata, content)
                .setFields("id")
                .execute()
            DriveSyncResult.Success(Unit)
        } catch (exception: Exception) {
            DriveSyncResult.Failure(exception.toSyncError())
        }
    }

    private fun driveService(): DriveSyncResult<Drive> {
        val currentAccount = account ?: return DriveSyncResult.Failure(
            SyncError(
                code = SyncErrorCode.NotSignedIn,
                message = "Sign in with Google before syncing.",
            ),
        )
        val androidAccount = currentAccount.account ?: return DriveSyncResult.Failure(
            SyncError(
                code = SyncErrorCode.NotSignedIn,
                message = "Google account is unavailable.",
            ),
        )

        drive?.let { return DriveSyncResult.Success(it) }

        val credential = GoogleAccountCredential.usingOAuth2(
            appContext,
            listOf(DriveScopes.DRIVE_APPDATA),
        ).apply {
            selectedAccount = androidAccount
        }
        return DriveSyncResult.Success(
            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential,
            )
                .setApplicationName("Just Notes")
                .build()
                .also { drive = it },
        )
    }

    private fun Drive.syncFiles(): List<DriveFile> {
        val allFiles = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val page = files()
                .list()
                .setSpaces("appDataFolder")
                .setQ("name contains '$SYNC_FILE_PREFIX' and trashed = false")
                .setFields("nextPageToken,files(id,name,modifiedTime,version)")
                .setPageToken(pageToken)
                .execute()
            allFiles += page.files.orEmpty()
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return allFiles
            .sortedWith(
                compareByDescending<DriveFile> { it.modifiedTime?.value ?: 0L }
                    .thenBy { it.id },
            )
    }

    private fun readSnapshotFile(drive: Drive, file: DriveFile): RemoteSyncSnapshot? {
        val json = drive.files().get(file.id).executeMediaAsInputStream()
            .bufferedReader()
            .use { it.readText() }
        return SyncSnapshotJson.decode(json)
    }

    private fun snapshotFileName(snapshot: RemoteSyncSnapshot): String {
        val safeSnapshotId = snapshot.snapshotId
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9-]"), "-")
            .ifBlank { "snapshot" }
        return "$SYNC_FILE_PREFIX-$safeSnapshotId.json"
    }

    private fun Exception.toSyncError(): SyncError {
        return when (this) {
            is UserRecoverableAuthException,
            is UserRecoverableAuthIOException -> SyncError(
                code = SyncErrorCode.PermissionRevoked,
                message = "Google Drive permission needs to be granted again.",
            )
            is GoogleJsonResponseException -> SyncError(
                code = when (statusCode) {
                    401, 403 -> SyncErrorCode.PermissionRevoked
                    else -> SyncErrorCode.Unknown
                },
                message = details?.message ?: message ?: "Google Drive sync failed.",
            )
            is IOException -> SyncError(
                code = SyncErrorCode.NetworkUnavailable,
                message = message ?: "Network unavailable.",
            )
            else -> SyncError(
                code = SyncErrorCode.Unknown,
                message = message ?: "Google Drive sync failed.",
            )
        }
    }

    private companion object {
        const val SYNC_FILE_PREFIX = "just-notes-sync-v1"
    }
}
