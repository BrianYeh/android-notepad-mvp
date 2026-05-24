package com.example.notepad.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notepad.data.DriveSyncResult
import com.example.notepad.data.EditorFontSize
import com.example.notepad.data.GoogleDriveSyncClient
import com.example.notepad.data.BackupData
import com.example.notepad.data.DecodedBackup
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteQuickFilter
import com.example.notepad.data.NoteListMode
import com.example.notepad.data.NoteSortOption
import com.example.notepad.data.NoteTypeFilter
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.NotepadRepository
import com.example.notepad.data.ReminderFilter
import com.example.notepad.data.SyncDevice
import com.example.notepad.data.SyncError
import com.example.notepad.data.SyncErrorCode
import com.example.notepad.data.SyncMerge
import com.example.notepad.data.SyncMetadata
import com.example.notepad.data.SyncStatus
import com.example.notepad.data.buildSharedNoteTitle
import com.example.notepad.ocr.MlKitOcrTextRecognizer
import com.example.notepad.ocr.OcrNoteResult
import com.example.notepad.ocr.OcrNoteUseCase
import com.example.notepad.reminder.ReminderScheduler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class NotepadViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    private val repository = NotepadRepository(
        NotepadDatabase.getInstance(application).notepadDao(),
    )
    private val driveSyncClient = GoogleDriveSyncClient(application)
    private val googleSyncMutex = Mutex()
    private val deviceId = preferences.getString("sync_device_id", null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString("sync_device_id", it).apply()
    }
    private val ocrNoteUseCase = OcrNoteUseCase(
        recognizer = MlKitOcrTextRecognizer(application),
        repository = repository,
    )

    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    val selectedFolderId: StateFlow<Long?> = _selectedFolderId
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _listMode = MutableStateFlow(NoteListMode.Active)
    val listMode: StateFlow<NoteListMode> = _listMode
    private val _sortOption = MutableStateFlow(NoteSortOption.UpdatedAt)
    val sortOption: StateFlow<NoteSortOption> = _sortOption
    private val _typeFilter = MutableStateFlow(NoteTypeFilter.All)
    val typeFilter: StateFlow<NoteTypeFilter> = _typeFilter
    private val _reminderFilter = MutableStateFlow(ReminderFilter.All)
    val reminderFilter: StateFlow<ReminderFilter> = _reminderFilter
    private val _quickFilter = MutableStateFlow(NoteQuickFilter.All)
    val quickFilter: StateFlow<NoteQuickFilter> = _quickFilter
    private val _editorFontSize = MutableStateFlow(
        EditorFontSize.fromCode(preferences.getString("editor_font_size", EditorFontSize.Medium.code)),
    )
    val editorFontSize: StateFlow<EditorFontSize> = _editorFontSize
    private val _onlineSyncTargetUri = MutableStateFlow(preferences.getString("online_sync_target_uri", null))
    val onlineSyncTargetUri: StateFlow<String?> = _onlineSyncTargetUri
    private val _onlineSyncAutoOnStart = MutableStateFlow(preferences.getBoolean("online_sync_auto_on_start", false))
    val onlineSyncAutoOnStart: StateFlow<Boolean> = _onlineSyncAutoOnStart
    private val _lastOnlineSyncAt = MutableStateFlow(
        preferences.getLong("last_online_sync_at", 0L).takeIf { it > 0L },
    )
    val lastOnlineSyncAt: StateFlow<Long?> = _lastOnlineSyncAt
    private val _lastOnlineRestoreAt = MutableStateFlow(
        preferences.getLong("last_online_restore_at", 0L).takeIf { it > 0L },
    )
    val lastOnlineRestoreAt: StateFlow<Long?> = _lastOnlineRestoreAt
    private val _lastGoogleSyncAt = MutableStateFlow(
        preferences.getLong("last_google_sync_at", 0L).takeIf {
            it > 0L && preferences.getString("last_google_sync_account", null) == driveSyncClient.accountEmail
        },
    )
    private val _syncMetadata = MutableStateFlow(
        SyncMetadata(
            deviceId = deviceId,
            deviceName = currentDeviceName(),
            accountEmail = driveSyncClient.accountEmail,
            lastSyncedAt = _lastGoogleSyncAt.value,
            status = if (driveSyncClient.accountEmail == null) SyncStatus.SignedOut else SyncStatus.Idle,
        ),
    )
    val syncMetadata: StateFlow<SyncMetadata> = _syncMetadata
    private val _isRecognizingText = MutableStateFlow(false)
    val isRecognizingText: StateFlow<Boolean> = _isRecognizingText

    val folders = repository.folders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val allNotes = repository.allNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val baseNoteFilters = combine(
        selectedFolderId,
        searchQuery,
        listMode,
        sortOption,
        quickFilter,
    ) { folderId, query, mode, sort, quickFilter ->
        NoteListFilters(
            selectedFolderId = folderId,
            searchQuery = query.trim(),
            listMode = mode,
            sortOption = sort,
            quickFilter = quickFilter,
        )
    }

    private val noteFilters = baseNoteFilters

    val notes = allNotes
        .combine(noteFilters) { notes, filters ->
            notes
                .asSequence()
                .filter { note -> note.isDeleted == (filters.listMode == NoteListMode.Trash) }
                .filter { note -> filters.selectedFolderId == null || note.folderId == filters.selectedFolderId }
                .filter { note -> note.matchesQuickFilter(filters.quickFilter) }
                .filter { note -> filters.searchQuery.isBlank() || note.matchesSearch(filters.searchQuery) }
                .toList()
                .sortedFor(filters)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch {
            repository.ensureDefaultFolder()
            ReminderScheduler.rescheduleFutureReminders(application)
        }
    }

    fun observeNote(noteId: Long) = repository.observeNote(noteId)

    fun selectFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setListMode(mode: NoteListMode) {
        _listMode.value = mode
    }

    fun setSortOption(option: NoteSortOption) {
        _sortOption.value = option
    }

    fun setTypeFilter(filter: NoteTypeFilter) {
        _typeFilter.value = filter
    }

    fun setReminderFilter(filter: ReminderFilter) {
        _reminderFilter.value = filter
    }

    fun setQuickFilter(filter: NoteQuickFilter) {
        _quickFilter.value = filter
    }

    fun setEditorFontSize(fontSize: EditorFontSize) {
        _editorFontSize.value = fontSize
        preferences.edit()
            .putString("editor_font_size", fontSize.code)
            .apply()
    }

    fun setOnlineSyncTargetUri(uri: String?) {
        _onlineSyncTargetUri.value = uri
        preferences.edit().apply {
            if (uri == null) {
                remove("online_sync_target_uri")
            } else {
                putString("online_sync_target_uri", uri)
            }
        }.apply()
    }

    fun setOnlineSyncAutoOnStart(enabled: Boolean) {
        _onlineSyncAutoOnStart.value = enabled
        preferences.edit()
            .putBoolean("online_sync_auto_on_start", enabled)
            .apply()
    }

    fun recordOnlineSync(timestamp: Long = System.currentTimeMillis()) {
        _lastOnlineSyncAt.value = timestamp
        preferences.edit()
            .putLong("last_online_sync_at", timestamp)
            .apply()
    }

    private fun recordGoogleSync(timestamp: Long = System.currentTimeMillis()) {
        _lastGoogleSyncAt.value = timestamp
        _syncMetadata.value = _syncMetadata.value.copy(
            lastSyncedAt = timestamp,
            status = SyncStatus.Succeeded,
            lastError = null,
        )
        preferences.edit()
            .putLong("last_google_sync_at", timestamp)
            .putString("last_google_sync_account", driveSyncClient.accountEmail)
            .apply()
    }

    fun recordOnlineRestore(timestamp: Long = System.currentTimeMillis()) {
        _lastOnlineRestoreAt.value = timestamp
        preferences.edit()
            .putLong("last_online_restore_at", timestamp)
            .apply()
    }

    fun disconnectOnlineSync() {
        _onlineSyncTargetUri.value = null
        _onlineSyncAutoOnStart.value = false
        _lastOnlineSyncAt.value = null
        _lastOnlineRestoreAt.value = null
        preferences.edit()
            .remove("online_sync_target_uri")
            .remove("last_online_sync_at")
            .remove("last_online_restore_at")
            .putBoolean("online_sync_auto_on_start", false)
            .apply()
    }

    fun googleSignInIntent(): Intent {
        return driveSyncClient.signInIntent()
    }

    fun connectGoogleAccountFromIntent(data: Intent?): Boolean {
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        } catch (_: ApiException) {
            null
        } ?: return false

        val previousAccount = _syncMetadata.value.accountEmail
        driveSyncClient.connect(account)
        if (previousAccount != account.email) {
            _lastGoogleSyncAt.value = null
            preferences.edit()
                .remove("last_google_sync_at")
                .putString("last_google_sync_account", account.email)
                .apply()
        }
        _syncMetadata.value = _syncMetadata.value.copy(
            accountEmail = account.email,
            lastSyncedAt = _lastGoogleSyncAt.value,
            status = SyncStatus.Idle,
            lastError = null,
        )
        return true
    }

    fun signOutGoogleAccount() {
        driveSyncClient.disconnect()
        _lastGoogleSyncAt.value = null
        preferences.edit()
            .remove("last_google_sync_at")
            .remove("last_google_sync_account")
            .apply()
        _syncMetadata.value = _syncMetadata.value.copy(
            accountEmail = null,
            lastSyncedAt = null,
            status = SyncStatus.SignedOut,
            lastError = null,
        )
    }

    suspend fun syncGoogleDrive(): DriveSyncResult<Unit> = googleSyncMutex.withLock {
        val now = System.currentTimeMillis()
        _syncMetadata.value = _syncMetadata.value.copy(status = SyncStatus.Syncing, lastError = null)
        val sourceDevice = SyncDevice(
            deviceId = deviceId,
            deviceName = currentDeviceName(),
            lastSyncAt = now,
        )
        val localExport = repository.exportRemoteSyncSnapshotWithFingerprint(
            sourceDevice = sourceDevice,
            accountEmail = driveSyncClient.accountEmail,
            now = now,
        )
        val remoteSnapshot = when (val result = withContext(Dispatchers.IO) { driveSyncClient.readSnapshot() }) {
            is DriveSyncResult.Success -> result.value
            is DriveSyncResult.Failure -> {
                updateSyncFailure(result.error)
                return result
            }
        }
        val mergeResult = SyncMerge.mergeSnapshots(
            local = localExport.snapshot,
            remote = remoteSnapshot,
            now = now,
            conflictModifiedAfterMillis = _lastGoogleSyncAt.value ?: if (remoteSnapshot != null) Long.MIN_VALUE else null,
        )
        return when (val result = withContext(Dispatchers.IO) { driveSyncClient.writeSnapshot(mergeResult.snapshot) }) {
            is DriveSyncResult.Success -> {
                val oldReminderNotes = repository.getFutureReminderNotes()
                val localReplaceSucceeded = repository.replaceWithRemoteSyncSnapshot(
                    snapshot = mergeResult.snapshot,
                    expectedFingerprint = localExport.fingerprint,
                )
                if (!localReplaceSucceeded) {
                    val error = SyncError(
                        code = SyncErrorCode.Conflict,
                        message = "Local notes changed during sync. Sync again.",
                    )
                    updateSyncFailure(error)
                    return DriveSyncResult.Failure(error)
                }
                oldReminderNotes.forEach { note ->
                    ReminderScheduler.cancel(getApplication(), note.id)
                }
                ReminderScheduler.rescheduleFutureReminders(getApplication())
                recordGoogleSync(now)
                _syncMetadata.value = _syncMetadata.value.copy(
                    accountEmail = driveSyncClient.accountEmail,
                    status = if (mergeResult.conflictCopies.isEmpty()) SyncStatus.Succeeded else SyncStatus.Conflict,
                    lastError = null,
                )
                DriveSyncResult.Success(Unit)
            }
            is DriveSyncResult.Failure -> {
                updateSyncFailure(result.error)
                result
            }
        }
    }

    private fun updateSyncFailure(error: SyncError) {
        _syncMetadata.value = _syncMetadata.value.copy(
            status = SyncStatus.Failed,
            lastError = error,
        )
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(name)
        }
    }

    fun renameFolder(folderId: Long, name: String) {
        viewModelScope.launch {
            repository.renameFolder(folderId, name)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
        }
    }

    fun createTextNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(repository.createTextNote(_selectedFolderId.value))
        }
    }

    fun createSharedTextNote(
        subject: String?,
        sharedText: String,
        defaultTitle: String,
        onCreated: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val title = buildSharedNoteTitle(subject, sharedText, defaultTitle)
            onCreated(repository.createSharedTextNote(title, sharedText))
        }
    }

    fun createOcrTextNote(
        imageUri: Uri,
        fallbackTitlePrefix: String,
        onCreated: (Long) -> Unit,
        onNoText: () -> Unit,
        onFailed: () -> Unit,
    ) {
        viewModelScope.launch {
            _isRecognizingText.value = true
            try {
                when (
                    val result = ocrNoteUseCase.createTextNoteFromImage(
                        uri = imageUri,
                        fallbackTitlePrefix = fallbackTitlePrefix,
                    )
                ) {
                    is OcrNoteResult.Created -> onCreated(result.noteId)
                    OcrNoteResult.NoText -> onNoText()
                    OcrNoteResult.Failed -> onFailed()
                }
            } finally {
                _isRecognizingText.value = false
            }
        }
    }

    fun createDrawingNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(repository.createDrawingNote(_selectedFolderId.value))
        }
    }

    fun saveTextNote(noteId: Long, title: String, content: String) {
        viewModelScope.launch {
            repository.saveTextNote(noteId, title, content)
        }
    }

    suspend fun saveTextNoteNow(noteId: Long, title: String, content: String): Long? {
        return repository.saveTextNote(noteId, title, content)
    }

    fun saveDrawingNote(noteId: Long, title: String, drawingData: String) {
        viewModelScope.launch {
            repository.saveDrawingNote(noteId, title, drawingData)
        }
    }

    suspend fun saveDrawingNoteNow(noteId: Long, title: String, drawingData: String): Long? {
        return repository.saveDrawingNote(noteId, title, drawingData)
    }

    fun moveNote(noteId: Long, folderId: Long) {
        viewModelScope.launch {
            repository.moveNote(noteId, folderId)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            ReminderScheduler.cancel(getApplication(), noteId)
        }
    }

    fun restoreNote(noteId: Long) {
        viewModelScope.launch {
            repository.restoreNote(noteId)?.let { note ->
                ReminderScheduler.schedule(getApplication(), note)
            }
        }
    }

    fun permanentlyDeleteNote(noteId: Long) {
        viewModelScope.launch {
            ReminderScheduler.cancel(getApplication(), noteId)
            repository.permanentlyDeleteNote(noteId)
        }
    }

    fun setNotePinned(noteId: Long, isPinned: Boolean) {
        viewModelScope.launch {
            repository.setNotePinned(noteId, isPinned)
        }
    }

    fun setNoteReminder(noteId: Long, reminderAt: Long?) {
        viewModelScope.launch {
            val note = repository.setNoteReminder(noteId, reminderAt)
            if (note == null || reminderAt == null) {
                ReminderScheduler.cancel(getApplication(), noteId)
            } else {
                ReminderScheduler.schedule(getApplication(), note)
            }
        }
    }

    suspend fun exportBackupJson(): String {
        return repository.exportBackupJson()
    }

    fun decodeBackupJson(json: String): DecodedBackup {
        return repository.decodeBackupJson(json)
    }

    suspend fun importBackupJson(json: String) {
        importBackupData(repository.decodeBackupJson(json).data)
    }

    suspend fun importBackupDataWithRollbackCheckpoint(backupData: BackupData): BackupData {
        ReminderScheduler.cancelFutureReminders(getApplication())
        try {
            return repository.importBackupDataWithRollbackCheckpoint(backupData)
        } finally {
            ReminderScheduler.rescheduleFutureReminders(getApplication())
        }
    }

    suspend fun importBackupData(backupData: BackupData) {
        ReminderScheduler.cancelFutureReminders(getApplication())
        try {
            repository.importBackupData(backupData)
        } finally {
            ReminderScheduler.rescheduleFutureReminders(getApplication())
        }
    }
}

private data class NoteListFilters(
    val selectedFolderId: Long?,
    val searchQuery: String,
    val listMode: NoteListMode,
    val sortOption: NoteSortOption,
    val quickFilter: NoteQuickFilter,
)

private fun NoteEntity.matchesSearch(query: String): Boolean {
    return title.contains(query, ignoreCase = true) ||
        textContent.orEmpty().contains(query, ignoreCase = true)
}

private fun NoteEntity.matchesQuickFilter(filter: NoteQuickFilter): Boolean {
    return when (filter) {
        NoteQuickFilter.All -> true
        NoteQuickFilter.Text -> type == NoteTypes.TEXT
        NoteQuickFilter.Drawing -> type == NoteTypes.DRAWING
        NoteQuickFilter.HasReminder -> reminderAt != null
        NoteQuickFilter.Pinned -> isPinned
    }
}

private fun List<NoteEntity>.sortedFor(filters: NoteListFilters): List<NoteEntity> {
    val sorted = when (filters.sortOption) {
        NoteSortOption.UpdatedAt -> sortedByDescending { it.updatedAt }
        NoteSortOption.CreatedAt -> sortedByDescending { it.createdAt }
        NoteSortOption.Title -> sortedWith(
            compareBy<NoteEntity> { it.title.lowercase() }
                .thenByDescending { it.updatedAt },
        )
    }

    return if (filters.listMode == NoteListMode.Active) {
        sorted.sortedByDescending { it.isPinned }
    } else {
        sorted
    }
}

private fun currentDeviceName(): String {
    return listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Android device" }
}
