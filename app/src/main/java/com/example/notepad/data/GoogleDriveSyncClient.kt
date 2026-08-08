package com.example.notepad.data

import android.content.Context
import android.content.Intent
import com.example.notepad.BuildConfig
import com.example.notepad.billing.BackendEntitlementAuth
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDriveSyncClient(
    private val context: Context,
) : DriveSyncClient {
    private val appContext = context.applicationContext
    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).apply {
        requestEmail()
        requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        }
    }.build()
    private val backendSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).apply {
        requestEmail()
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        }
    }.build()

    private var account: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(appContext)
        ?.takeIf { GoogleSignIn.hasPermissions(it, Scope(DriveScopes.DRIVE_APPDATA)) }
    private var drive: Drive? = null
    private var allowLastSignedInBackendAuth = true

    override val accountEmail: String?
        get() = account?.email

    internal suspend fun backendEntitlementAuth(): BackendEntitlementAuth? {
        return refreshedBackendEntitlementAuth(
            googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            cachedAccount = backendEntitlementAccount(),
            refreshAccount = { refreshSignedInAccount() },
            readIdToken = { it.idToken },
            readAccountKey = ::backendEntitlementAccountKey,
            cacheRefreshedAccount = ::cacheRefreshedDriveAccountIfPermitted,
        )
    }

    internal fun currentBackendEntitlementAccountKey(): String? {
        return backendEntitlementAccount()?.let(::backendEntitlementAccountKey)
    }

    fun signInIntent(): Intent {
        return GoogleSignIn.getClient(appContext, signInOptions).signInIntent
    }

    fun connect(account: GoogleSignInAccount) {
        allowLastSignedInBackendAuth = true
        this.account = account
        drive = null
    }

    fun disconnect() {
        allowLastSignedInBackendAuth = false
        account = null
        drive = null
        GoogleSignIn.getClient(appContext, signInOptions).signOut()
    }

    private fun hasDriveAppDataPermission(account: GoogleSignInAccount): Boolean {
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))
    }

    private fun backendEntitlementAccountKey(account: GoogleSignInAccount): String? {
        return account.account?.name ?: account.email ?: account.id
    }

    private fun backendEntitlementAccount(): GoogleSignInAccount? {
        return backendEntitlementAccountForAuth(
            driveAccount = account,
            lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(appContext),
            allowLastSignedInAccount = allowLastSignedInBackendAuth,
        )
    }

    private fun cacheRefreshedDriveAccountIfPermitted(refreshedAccount: GoogleSignInAccount) {
        if (!allowLastSignedInBackendAuth) return
        val currentAccount = account
        val nextAccount = driveAccountAfterBackendAuthRefresh(
            currentDriveAccount = currentAccount,
            refreshedAccount = refreshedAccount,
            refreshedHasDrivePermission = hasDriveAppDataPermission(refreshedAccount),
        )
        if (nextAccount !== currentAccount) {
            if (currentAccount?.account != nextAccount?.account) {
                drive = null
            }
            account = nextAccount
        }
        if (nextAccount != null) {
            allowLastSignedInBackendAuth = true
        }
    }

    private suspend fun refreshSignedInAccount(): GoogleSignInAccount? {
        return withContext(Dispatchers.IO) {
            try {
                Tasks.await(
                    GoogleSignIn.getClient(appContext, backendSignInOptions).silentSignIn(),
                    BACKEND_ID_TOKEN_REFRESH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                null
            }
        }
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
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            DriveSyncResult.Failure(exception.toDriveSyncError())
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
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            DriveSyncResult.Failure(exception.toDriveSyncError())
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

    private companion object {
        const val SYNC_FILE_PREFIX = "just-notes-sync-v1"
        const val BACKEND_ID_TOKEN_REFRESH_TIMEOUT_SECONDS = 10L
    }
}

internal fun Exception.toDriveSyncError(): SyncError {
    return when (this) {
        is UserRecoverableAuthException,
        is UserRecoverableAuthIOException -> SyncError(
            code = SyncErrorCode.PermissionRevoked,
            message = "Google Drive permission needs to be granted again.",
        )
        is GoogleJsonResponseException -> {
            if (statusCode == 401 || statusCode == 403) {
                SyncError(
                    code = SyncErrorCode.PermissionRevoked,
                    message = "Google Drive permission needs to be granted again.",
                )
            } else {
                SyncError(
                    code = SyncErrorCode.Unknown,
                    message = "Google Drive sync failed.",
                )
            }
        }
        is IOException -> SyncError(
            code = SyncErrorCode.NetworkUnavailable,
            message = "Network unavailable.",
        )
        else -> SyncError(
            code = SyncErrorCode.Unknown,
            message = "Google Drive sync failed.",
        )
    }
}

internal fun <Account> backendEntitlementAccountForAuth(
    driveAccount: Account?,
    lastSignedInAccount: Account?,
    allowLastSignedInAccount: Boolean,
): Account? {
    return driveAccount ?: lastSignedInAccount.takeIf { allowLastSignedInAccount }
}

internal fun <Account> driveAccountAfterBackendAuthRefresh(
    currentDriveAccount: Account?,
    refreshedAccount: Account,
    refreshedHasDrivePermission: Boolean,
): Account? {
    return if (refreshedHasDrivePermission) refreshedAccount else currentDriveAccount
}

internal suspend fun <Account> refreshedBackendEntitlementAuth(
    googleWebClientId: String,
    cachedAccount: Account?,
    refreshAccount: suspend () -> Account?,
    readIdToken: (Account) -> String?,
    readAccountKey: (Account) -> String?,
    cacheRefreshedAccount: (Account) -> Unit,
): BackendEntitlementAuth? {
    if (googleWebClientId.isBlank() || cachedAccount == null) return null
    val refreshedAccount = try {
        refreshAccount()
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        null
    }
    val accountForToken = refreshedAccount ?: cachedAccount
    if (refreshedAccount != null) {
        cacheRefreshedAccount(refreshedAccount)
    }
    val idToken = readIdToken(accountForToken)?.takeIf { it.isNotBlank() } ?: return null
    val accountKey = readAccountKey(accountForToken)?.takeIf { it.isNotBlank() } ?: return null
    return BackendEntitlementAuth(
        idToken = idToken,
        accountKey = accountKey,
    )
}
