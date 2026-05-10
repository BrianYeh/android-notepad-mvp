package com.example.notepad.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notepad.data.AppLanguage
import com.example.notepad.data.EditorFontSize
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteListMode
import com.example.notepad.data.NoteSortOption
import com.example.notepad.data.NoteTypeFilter
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.NotepadRepository
import com.example.notepad.data.ReminderFilter
import com.example.notepad.data.buildSharedNoteTitle
import com.example.notepad.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotepadViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    private val repository = NotepadRepository(
        NotepadDatabase.getInstance(application).notepadDao(),
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
    private val _appLanguage = MutableStateFlow(
        AppLanguage.fromCode(preferences.getString("app_language", AppLanguage.English.code)),
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage
    private val _editorFontSize = MutableStateFlow(
        EditorFontSize.fromCode(preferences.getString("editor_font_size", EditorFontSize.Medium.code)),
    )
    val editorFontSize: StateFlow<EditorFontSize> = _editorFontSize

    val folders = repository.folders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val baseNoteFilters = combine(
        selectedFolderId,
        searchQuery,
        listMode,
        sortOption,
        typeFilter,
    ) { folderId, query, mode, sort, type ->
        NoteListFilters(
            selectedFolderId = folderId,
            searchQuery = query.trim(),
            listMode = mode,
            sortOption = sort,
            typeFilter = type,
            reminderFilter = ReminderFilter.All,
        )
    }

    private val noteFilters = baseNoteFilters
        .combine(reminderFilter) { filters, reminder ->
            filters.copy(reminderFilter = reminder)
        }

    val notes = repository.allNotes
        .combine(noteFilters) { notes, filters ->
            notes
                .asSequence()
                .filter { note -> note.isDeleted == (filters.listMode == NoteListMode.Trash) }
                .filter { note -> filters.selectedFolderId == null || note.folderId == filters.selectedFolderId }
                .filter { note -> note.matchesType(filters.typeFilter) }
                .filter { note -> note.matchesReminder(filters.reminderFilter) }
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

    fun setLanguage(language: AppLanguage) {
        _appLanguage.value = language
        preferences.edit()
            .putString("app_language", language.code)
            .apply()
    }

    fun setEditorFontSize(fontSize: EditorFontSize) {
        _editorFontSize.value = fontSize
        preferences.edit()
            .putString("editor_font_size", fontSize.code)
            .apply()
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

    suspend fun importBackupJson(json: String) {
        repository.importBackupJson(json)
        ReminderScheduler.rescheduleFutureReminders(getApplication())
    }
}

private data class NoteListFilters(
    val selectedFolderId: Long?,
    val searchQuery: String,
    val listMode: NoteListMode,
    val sortOption: NoteSortOption,
    val typeFilter: NoteTypeFilter,
    val reminderFilter: ReminderFilter,
)

private fun NoteEntity.matchesSearch(query: String): Boolean {
    return title.contains(query, ignoreCase = true) ||
        textContent.orEmpty().contains(query, ignoreCase = true)
}

private fun NoteEntity.matchesType(typeFilter: NoteTypeFilter): Boolean {
    return when (typeFilter) {
        NoteTypeFilter.All -> true
        NoteTypeFilter.Text -> type == NoteTypes.TEXT
        NoteTypeFilter.Drawing -> type == NoteTypes.DRAWING
    }
}

private fun NoteEntity.matchesReminder(reminderFilter: ReminderFilter): Boolean {
    val reminder = reminderAt
    val now = System.currentTimeMillis()
    return when (reminderFilter) {
        ReminderFilter.All -> true
        ReminderFilter.WithReminder -> reminder != null
        ReminderFilter.Overdue -> reminder != null && reminder <= now
        ReminderFilter.Upcoming -> reminder != null && reminder > now
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
