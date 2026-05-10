package com.example.notepad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notepad.ui.LocalNotepadTheme
import com.example.notepad.ui.NotepadApp
import com.example.notepad.viewmodel.NotepadViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: NotepadViewModel = viewModel()
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            val notes by viewModel.notes.collectAsStateWithLifecycle()
            val selectedFolderId by viewModel.selectedFolderId.collectAsStateWithLifecycle()
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val listMode by viewModel.listMode.collectAsStateWithLifecycle()
            val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
            val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
            val reminderFilter by viewModel.reminderFilter.collectAsStateWithLifecycle()
            val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
            val editorFontSize by viewModel.editorFontSize.collectAsStateWithLifecycle()

            LocalNotepadTheme {
                NotepadApp(
                    folders = folders,
                    notes = notes,
                    selectedFolderId = selectedFolderId,
                    searchQuery = searchQuery,
                    listMode = listMode,
                    sortOption = sortOption,
                    typeFilter = typeFilter,
                    reminderFilter = reminderFilter,
                    appLanguage = appLanguage,
                    editorFontSize = editorFontSize,
                    viewModel = viewModel,
                )
            }
        }
    }
}
