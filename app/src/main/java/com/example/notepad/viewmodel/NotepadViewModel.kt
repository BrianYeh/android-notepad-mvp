package com.example.notepad.viewmodel

import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notepad.billing.PremiumBilling
import com.example.notepad.billing.PremiumPlan
import com.example.notepad.data.DriveSyncResult
import com.example.notepad.data.EditorFontSize
import com.example.notepad.data.GoogleDriveSyncClient
import com.example.notepad.data.BackupData
import com.example.notepad.data.ChecklistJson
import com.example.notepad.data.DecodedBackup
import com.example.notepad.data.DrawingSaveEditGate
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteQuickFilter
import com.example.notepad.data.NoteListMode
import com.example.notepad.data.NoteSortOption
import com.example.notepad.data.NoteTypeFilter
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.NotepadRepository
import com.example.notepad.data.PrivacyPreferences
import com.example.notepad.data.ReminderFilter
import com.example.notepad.data.ReminderRepeat
import com.example.notepad.data.RestoreRollbackStore
import com.example.notepad.data.SyncDevice
import com.example.notepad.data.SyncError
import com.example.notepad.data.SyncErrorCode
import com.example.notepad.data.SyncMerge
import com.example.notepad.data.SyncMetadata
import com.example.notepad.data.SyncStatus
import com.example.notepad.data.TextImportFile
import com.example.notepad.data.buildSharedNoteTitle
import com.example.notepad.data.normalizedReminderRepeat
import com.example.notepad.debug.DebugPremiumAccess
import com.example.notepad.debug.DebugSaveFailure
import com.example.notepad.ocr.MlKitOcrTextRecognizer
import com.example.notepad.ocr.OcrNoteResult
import com.example.notepad.ocr.OcrNoteUseCase
import com.example.notepad.reminder.ReminderScheduler
import com.example.notepad.widget.NotepadWidgets
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
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
import java.io.File
import java.util.UUID

class NotepadViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PrivacyPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val repository = NotepadRepository(
        NotepadDatabase.getInstance(application).notepadDao(),
    )
    private val restoreRollbackStore = RestoreRollbackStore(
        File(application.filesDir, "restore-rollback-checkpoint.json"),
    )
    private val driveSyncClient = GoogleDriveSyncClient(application)
    private val premiumBilling = PremiumBilling(
        application = application,
        connectToPlay = DebugPremiumAccess.shouldConnectBilling(),
    )
    private val googleSyncMutex = Mutex()
    private val drawingSaveEditGates = mutableMapOf<Long, DrawingSaveEditGate>()
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
    private val _hideReminderNotificationContent = MutableStateFlow(
        PrivacyPreferences.hideReminderNotificationContent(application),
    )
    val hideReminderNotificationContent: StateFlow<Boolean> = _hideReminderNotificationContent
    private val _requireDeviceUnlock = MutableStateFlow(
        PrivacyPreferences.requireDeviceUnlock(application),
    )
    val requireDeviceUnlock: StateFlow<Boolean> = _requireDeviceUnlock
    private val _onlineSyncTargetUri = MutableStateFlow(preferences.getString("online_sync_target_uri", null))
    val onlineSyncTargetUri: StateFlow<String?> = _onlineSyncTargetUri
    private val _onlineSyncAutoOnStart = MutableStateFlow(preferences.getBoolean("online_sync_auto_on_start", false))
    val onlineSyncAutoOnStart: StateFlow<Boolean> = _onlineSyncAutoOnStart
    val debugPremiumToolsAvailable: Boolean = DebugPremiumAccess.isAvailable
    private val _lastOnlineSyncAt = MutableStateFlow(
        preferences.getLong("last_online_sync_at", 0L).takeIf { it > 0L },
    )
    val lastOnlineSyncAt: StateFlow<Long?> = _lastOnlineSyncAt
    private val _lastOnlineRestoreAt = MutableStateFlow(
        preferences.getLong("last_online_restore_at", 0L).takeIf { it > 0L },
    )
    val lastOnlineRestoreAt: StateFlow<Long?> = _lastOnlineRestoreAt
    private val _restoreRollbackCheckpoint = MutableStateFlow(restoreRollbackStore.load())
    val restoreRollbackCheckpoint: StateFlow<DecodedBackup?> = _restoreRollbackCheckpoint
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
    val premiumBillingState = premiumBilling.state
        .combine(DebugPremiumAccess.observe(application)) { billingState, debugPremiumOverride ->
            billingState.copy(debugPremiumOverride = debugPremiumOverride)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = premiumBilling.state.value.copy(debugPremiumOverride = DebugPremiumAccess.read(application)),
        )
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
    }.combine(reminderFilter) { filters, reminderFilter ->
        filters.copy(reminderFilter = reminderFilter)
    }

    private val noteFilters = baseNoteFilters

    val notes = allNotes
        .combine(noteFilters) { notes, filters ->
            val now = System.currentTimeMillis()
            notes
                .asSequence()
                .filter { note -> note.isDeleted == (filters.listMode == NoteListMode.Trash) }
                .filter { note -> filters.selectedFolderId == null || note.folderId == filters.selectedFolderId }
                .filter { note -> note.matchesQuickFilter(filters.quickFilter) }
                .filter { note -> note.matchesReminderFilter(filters.reminderFilter, now) }
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
        premiumBilling.start()
    }

    fun refreshPremiumEntitlement() {
        premiumBilling.refresh()
    }

    fun launchPremiumPurchase(activity: Activity, plan: PremiumPlan): Boolean {
        return premiumBilling.launchPurchase(activity, plan)
    }

    fun setDebugPremiumOverride(enabled: Boolean) {
        if (!DebugPremiumAccess.isAvailable) return
        DebugPremiumAccess.write(getApplication(), enabled)
    }

    override fun onCleared() {
        premiumBilling.close()
        super.onCleared()
    }

    fun observeNote(noteId: Long) = repository.observeNote(noteId)

    fun drawingSaveEditGate(noteId: Long): DrawingSaveEditGate {
        return synchronized(drawingSaveEditGates) {
            drawingSaveEditGates.getOrPut(noteId) { DrawingSaveEditGate() }
        }
    }

    suspend fun getActiveNote(noteId: Long): NoteEntity? {
        return withContext(Dispatchers.IO) {
            repository.getNote(noteId)?.takeUnless { it.isDeleted }
        }
    }

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

    fun setHideReminderNotificationContent(enabled: Boolean) {
        _hideReminderNotificationContent.value = enabled
        preferences.edit()
            .putBoolean(PrivacyPreferences.HIDE_REMINDER_NOTIFICATION_CONTENT_KEY, enabled)
            .apply()
    }

    fun setRequireDeviceUnlock(enabled: Boolean) {
        _requireDeviceUnlock.value = enabled
        preferences.edit()
            .putBoolean(PrivacyPreferences.REQUIRE_DEVICE_UNLOCK_KEY, enabled)
            .apply()
        refreshWidgets()
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
        } catch (exception: ApiException) {
            updateSyncFailure(exception.toSignInSyncError())
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

    private fun ApiException.toSignInSyncError(): SyncError {
        return when (statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR -> SyncError(
                code = SyncErrorCode.MissingGoogleConfiguration,
                message = "Google sign-in is not configured for this app signing certificate.",
            )
            CommonStatusCodes.NETWORK_ERROR -> SyncError(
                code = SyncErrorCode.NetworkUnavailable,
                message = "Network unavailable.",
            )
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> SyncError(
                code = SyncErrorCode.NotSignedIn,
                message = "Google sign-in was cancelled.",
            )
            else -> SyncError(
                code = SyncErrorCode.Unknown,
                message = "Google sign-in failed.",
            )
        }
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
                val oldReminderNotes = repository.getReminderNotes()
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
                    ReminderScheduler.cancelNotification(getApplication(), note.id)
                }
                ReminderScheduler.rescheduleFutureReminders(getApplication())
                refreshWidgets()
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
            refreshWidgets()
        }
    }

    fun createTextNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(repository.createTextNote(_selectedFolderId.value))
            refreshWidgets()
        }
    }

    fun createTextNoteWithReminder(
        reminderAt: Long,
        reminderRepeat: String = ReminderRepeat.None.code,
        onCreated: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            if (reminderAt <= System.currentTimeMillis()) return@launch
            val noteId = repository.createTextNote(_selectedFolderId.value)
            val normalizedRepeat = normalizedReminderRepeat(reminderRepeat)
            val scheduledReminderAt = if (normalizedRepeat == ReminderRepeat.None.code) {
                reminderAt
            } else {
                ReminderScheduler.nextRepeatTime(reminderAt, normalizedRepeat) ?: reminderAt
            }
            val note = repository.setNoteReminder(noteId, scheduledReminderAt, normalizedRepeat)
            if (note == null) {
                repository.permanentlyDeleteBlankTextDraft(noteId)
                ReminderScheduler.cancel(getApplication(), noteId)
                ReminderScheduler.cancelNotification(getApplication(), noteId)
                refreshWidgets()
                return@launch
            }
            ReminderScheduler.schedule(getApplication(), note)
            refreshWidgets()
            onCreated(noteId)
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
            refreshWidgets()
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
                    is OcrNoteResult.Created -> {
                        onCreated(result.noteId)
                        refreshWidgets()
                    }
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
            refreshWidgets()
        }
    }

    fun createChecklistNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(repository.createChecklistNote(_selectedFolderId.value))
            refreshWidgets()
        }
    }

    fun saveTextNote(noteId: Long, title: String, content: String, textFormattingJson: String? = null) {
        viewModelScope.launch {
            repository.saveTextNote(noteId, title, content, textFormattingJson)
            refreshWidgets()
        }
    }

    suspend fun saveTextNoteNow(
        noteId: Long,
        title: String,
        content: String,
        textFormattingJson: String? = null,
    ): Long? {
        if (DebugSaveFailure.consumeTextSaveFailure(noteId)) return null
        return repository.saveTextNote(noteId, title, content, textFormattingJson).also { refreshWidgets() }
    }

    fun saveDrawingNote(noteId: Long, title: String, drawingData: String) {
        viewModelScope.launch {
            repository.saveDrawingNote(noteId, title, drawingData)
            refreshWidgets()
        }
    }

    fun saveDrawingNoteIfCurrent(
        noteId: Long,
        title: String,
        drawingData: String,
        expectedUpdatedAt: Long,
        expectedTitle: String,
        expectedDrawingData: String,
        saveEditGate: DrawingSaveEditGate,
        isCurrentBeforeWrite: () -> Boolean,
    ) {
        viewModelScope.launch {
            saveDrawingNoteNow(
                noteId = noteId,
                title = title,
                drawingData = drawingData,
                expectedUpdatedAt = expectedUpdatedAt,
                expectedTitle = expectedTitle,
                expectedDrawingData = expectedDrawingData,
                saveEditGate = saveEditGate,
                isCurrentBeforeWrite = isCurrentBeforeWrite,
            )
        }
    }

    suspend fun saveDrawingNoteNow(
        noteId: Long,
        title: String,
        drawingData: String,
        expectedUpdatedAt: Long,
        expectedTitle: String,
        expectedDrawingData: String,
        saveEditGate: DrawingSaveEditGate,
        isCurrentBeforeWrite: () -> Boolean,
    ): Long? {
        DebugSaveFailure.delayDrawingSaveIfRequested(noteId)
        if (!isCurrentBeforeWrite()) return null
        if (DebugSaveFailure.consumeDrawingSaveFailure(noteId)) return null
        return repository.saveDrawingNoteIfCurrent(
            noteId = noteId,
            title = title,
            drawingData = drawingData,
            expectedUpdatedAt = expectedUpdatedAt,
            expectedTitle = expectedTitle,
            expectedDrawingData = expectedDrawingData,
            saveEditGate = saveEditGate,
            isCurrentBeforeWrite = isCurrentBeforeWrite,
        ).also { refreshWidgets() }
    }

    fun saveChecklistNote(noteId: Long, title: String, checklistJson: String) {
        viewModelScope.launch {
            repository.saveChecklistNote(noteId, title, checklistJson)
            refreshWidgets()
        }
    }

    suspend fun saveChecklistNoteNow(noteId: Long, title: String, checklistJson: String): Long? {
        return repository.saveChecklistNote(noteId, title, checklistJson).also { refreshWidgets() }
    }

    fun moveNote(noteId: Long, folderId: Long) {
        viewModelScope.launch {
            repository.moveNote(noteId, folderId)
            refreshWidgets()
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            ReminderScheduler.cancel(getApplication(), noteId)
            ReminderScheduler.cancelNotification(getApplication(), noteId)
            refreshWidgets()
        }
    }

    fun restoreNote(noteId: Long) {
        viewModelScope.launch {
            repository.restoreNote(noteId)?.let {
                ReminderScheduler.rescheduleFutureReminders(getApplication())
            }
            refreshWidgets()
        }
    }

    fun permanentlyDeleteNote(noteId: Long) {
        viewModelScope.launch {
            ReminderScheduler.cancel(getApplication(), noteId)
            ReminderScheduler.cancelNotification(getApplication(), noteId)
            repository.permanentlyDeleteNote(noteId)
            refreshWidgets()
        }
    }

    fun discardNewTextDraftIfBlank(
        noteId: Long,
        title: String,
        content: String,
        textFormattingJson: String?,
    ) {
        viewModelScope.launch {
            discardNewTextDraftIfBlankAndRefresh(noteId, title, content, textFormattingJson)
        }
    }

    suspend fun discardNewTextDraftIfBlankNow(
        noteId: Long,
        title: String,
        content: String,
        textFormattingJson: String?,
    ): Boolean {
        return discardNewTextDraftIfBlankAndRefresh(noteId, title, content, textFormattingJson)
    }

    fun discardNewDrawingDraftIfBlank(
        noteId: Long,
        isNewDraft: Boolean,
        hasUserIntent: Boolean,
        title: String,
        drawingData: String,
        saveEditGate: DrawingSaveEditGate? = null,
        isCurrentBeforeDelete: () -> Boolean = { true },
        expectedUpdatedAt: Long? = null,
        expectedTitle: String? = null,
        expectedDrawingData: String? = null,
    ) {
        viewModelScope.launch {
            discardNewDrawingDraftIfBlankAndRefresh(
                noteId = noteId,
                isNewDraft = isNewDraft,
                hasUserIntent = hasUserIntent,
                title = title,
                drawingData = drawingData,
                saveEditGate = saveEditGate,
                isCurrentBeforeDelete = isCurrentBeforeDelete,
                expectedUpdatedAt = expectedUpdatedAt,
                expectedTitle = expectedTitle,
                expectedDrawingData = expectedDrawingData,
            )
        }
    }

    suspend fun discardNewDrawingDraftIfBlankNow(
        noteId: Long,
        isNewDraft: Boolean,
        hasUserIntent: Boolean,
        title: String,
        drawingData: String,
        saveEditGate: DrawingSaveEditGate? = null,
        isCurrentBeforeDelete: () -> Boolean = { true },
        expectedUpdatedAt: Long? = null,
        expectedTitle: String? = null,
        expectedDrawingData: String? = null,
    ): Boolean {
        return discardNewDrawingDraftIfBlankAndRefresh(
            noteId = noteId,
            isNewDraft = isNewDraft,
            hasUserIntent = hasUserIntent,
            title = title,
            drawingData = drawingData,
            saveEditGate = saveEditGate,
            isCurrentBeforeDelete = isCurrentBeforeDelete,
            expectedUpdatedAt = expectedUpdatedAt,
            expectedTitle = expectedTitle,
            expectedDrawingData = expectedDrawingData,
        )
    }

    fun permanentlyDeleteBlankTextDraft(noteId: Long) {
        viewModelScope.launch {
            deleteBlankTextDraftAndRefresh(noteId)
        }
    }

    suspend fun permanentlyDeleteBlankTextDraftNow(noteId: Long): Boolean {
        return deleteBlankTextDraftAndRefresh(noteId)
    }

    private suspend fun discardNewTextDraftIfBlankAndRefresh(
        noteId: Long,
        title: String,
        content: String,
        textFormattingJson: String?,
    ): Boolean {
        val deleted = repository.discardNewTextDraftIfBlank(noteId, title, content, textFormattingJson)
        if (deleted) {
            ReminderScheduler.cancel(getApplication(), noteId)
            ReminderScheduler.cancelNotification(getApplication(), noteId)
            refreshWidgets()
        }
        return deleted
    }

    private suspend fun deleteBlankTextDraftAndRefresh(noteId: Long): Boolean {
        val deleted = repository.permanentlyDeleteBlankTextDraft(noteId)
        if (deleted) {
            ReminderScheduler.cancel(getApplication(), noteId)
            ReminderScheduler.cancelNotification(getApplication(), noteId)
            refreshWidgets()
        }
        return deleted
    }

    private suspend fun discardNewDrawingDraftIfBlankAndRefresh(
        noteId: Long,
        isNewDraft: Boolean,
        hasUserIntent: Boolean,
        title: String,
        drawingData: String,
        saveEditGate: DrawingSaveEditGate? = null,
        isCurrentBeforeDelete: () -> Boolean = { true },
        expectedUpdatedAt: Long? = null,
        expectedTitle: String? = null,
        expectedDrawingData: String? = null,
    ): Boolean {
        if (!isNewDraft || hasUserIntent) return false
        val deleted = repository.discardNewDrawingDraftIfBlank(
            noteId = noteId,
            isNewDraft = isNewDraft,
            title = title,
            drawingData = drawingData,
            saveEditGate = saveEditGate,
            isCurrentBeforeDelete = isCurrentBeforeDelete,
            expectedUpdatedAt = expectedUpdatedAt,
            expectedTitle = expectedTitle,
            expectedDrawingData = expectedDrawingData,
        )
        if (deleted) {
            ReminderScheduler.cancel(getApplication(), noteId)
            ReminderScheduler.cancelNotification(getApplication(), noteId)
            refreshWidgets()
        }
        return deleted
    }

    fun setNotePinned(noteId: Long, isPinned: Boolean) {
        viewModelScope.launch {
            repository.setNotePinned(noteId, isPinned)
            refreshWidgets()
        }
    }

    fun setNoteReminder(
        noteId: Long,
        reminderAt: Long?,
        reminderRepeat: String = ReminderRepeat.None.code,
    ) {
        viewModelScope.launch {
            ReminderScheduler.cancel(getApplication(), noteId)
            ReminderScheduler.cancelNotification(getApplication(), noteId)
            val normalizedRepeat = normalizedReminderRepeat(reminderRepeat)
            val scheduledReminderAt = if (reminderAt == null) {
                null
            } else if (normalizedRepeat == ReminderRepeat.None.code) {
                reminderAt
            } else {
                ReminderScheduler.nextRepeatTime(reminderAt, normalizedRepeat) ?: reminderAt
            }
            val note = repository.setNoteReminder(noteId, scheduledReminderAt, normalizedRepeat)
            if (note == null || scheduledReminderAt == null) {
                ReminderScheduler.cancel(getApplication(), noteId)
            } else {
                ReminderScheduler.schedule(getApplication(), note)
            }
            refreshWidgets()
        }
    }

    suspend fun exportBackupJson(): String {
        return repository.exportBackupJson()
    }

    suspend fun exportBatchZip(): ByteArray {
        return withContext(Dispatchers.IO) {
            repository.exportBatchZip()
        }
    }

    suspend fun importTextFiles(files: List<TextImportFile>): Int {
        return repository.importTextFiles(files).also {
            refreshWidgets()
        }
    }

    fun decodeBackupJson(json: String): DecodedBackup {
        return repository.decodeBackupJson(json)
    }

    suspend fun importBackupJson(json: String) {
        importBackupData(repository.decodeBackupJson(json).data)
    }

    suspend fun importBackupDataWithRollbackCheckpoint(backupData: BackupData): DecodedBackup {
        val rollbackCheckpoint = withContext(Dispatchers.IO) {
            restoreRollbackStore.save(repository.exportBackupJson())
        }
        _restoreRollbackCheckpoint.value = rollbackCheckpoint
        ReminderScheduler.cancelFutureReminders(getApplication())
        try {
            repository.importBackupData(backupData)
        } finally {
            ReminderScheduler.rescheduleFutureReminders(getApplication())
            refreshWidgets()
        }
        return rollbackCheckpoint
    }

    suspend fun restoreRollbackCheckpoint() {
        val checkpoint = _restoreRollbackCheckpoint.value
            ?: withContext(Dispatchers.IO) { restoreRollbackStore.load() }
            ?: error("No restore rollback checkpoint is available.")
        importBackupData(checkpoint.data)
        withContext(Dispatchers.IO) {
            restoreRollbackStore.clear()
        }
        _restoreRollbackCheckpoint.value = null
        refreshWidgets()
    }

    suspend fun importBackupData(backupData: BackupData) {
        ReminderScheduler.cancelFutureReminders(getApplication())
        try {
            repository.importBackupData(backupData)
        } finally {
            ReminderScheduler.rescheduleFutureReminders(getApplication())
            refreshWidgets()
        }
    }

    private fun refreshWidgets() {
        NotepadWidgets.refresh(getApplication())
    }
}

private data class NoteListFilters(
    val selectedFolderId: Long?,
    val searchQuery: String,
    val listMode: NoteListMode,
    val sortOption: NoteSortOption,
    val quickFilter: NoteQuickFilter,
    val reminderFilter: ReminderFilter = ReminderFilter.All,
)

private fun NoteEntity.matchesSearch(query: String): Boolean {
    return title.contains(query, ignoreCase = true) ||
        searchableText().contains(query, ignoreCase = true)
}

private fun NoteEntity.matchesQuickFilter(filter: NoteQuickFilter): Boolean {
    return when (filter) {
        NoteQuickFilter.All -> true
        NoteQuickFilter.Text -> type == NoteTypes.TEXT
        NoteQuickFilter.Drawing -> type == NoteTypes.DRAWING
        NoteQuickFilter.Checklist -> type == NoteTypes.CHECKLIST
        NoteQuickFilter.HasReminder -> reminderAt != null
        NoteQuickFilter.Pinned -> isPinned
    }
}

private fun NoteEntity.matchesReminderFilter(filter: ReminderFilter, now: Long): Boolean {
    val reminderAt = reminderAt
    return when (filter) {
        ReminderFilter.All -> true
        ReminderFilter.WithReminder -> reminderAt != null
        ReminderFilter.Overdue -> reminderAt != null && reminderAt <= now
        ReminderFilter.Upcoming -> reminderAt != null && reminderAt > now
    }
}

private fun NoteEntity.searchableText(): String {
    return if (type == NoteTypes.CHECKLIST) {
        ChecklistJson.plainText(textContent)
    } else {
        textContent.orEmpty()
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
