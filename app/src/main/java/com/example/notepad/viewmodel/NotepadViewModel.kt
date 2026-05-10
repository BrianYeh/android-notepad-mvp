package com.example.notepad.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notepad.data.AppLanguage
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NotepadDatabase
import com.example.notepad.data.NotepadRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    private val _appLanguage = MutableStateFlow(
        AppLanguage.fromCode(preferences.getString("app_language", AppLanguage.English.code)),
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage

    val folders = repository.folders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes = selectedFolderId
        .flatMapLatest { folderId -> repository.notes(folderId) }
        .combine(searchQuery) { notes, query ->
            val trimmedQuery = query.trim()
            if (trimmedQuery.isBlank()) {
                notes
            } else {
                notes.filter { note -> note.matchesSearch(trimmedQuery) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch {
            repository.ensureDefaultFolder()
        }
    }

    fun observeNote(noteId: Long) = repository.observeNote(noteId)

    fun selectFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLanguage(language: AppLanguage) {
        _appLanguage.value = language
        preferences.edit()
            .putString("app_language", language.code)
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

    fun saveDrawingNote(noteId: Long, title: String, drawingData: String) {
        viewModelScope.launch {
            repository.saveDrawingNote(noteId, title, drawingData)
        }
    }

    fun moveNote(noteId: Long, folderId: Long) {
        viewModelScope.launch {
            repository.moveNote(noteId, folderId)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
        }
    }
}

private fun NoteEntity.matchesSearch(query: String): Boolean {
    return title.contains(query, ignoreCase = true) ||
        textContent.orEmpty().contains(query, ignoreCase = true)
}
