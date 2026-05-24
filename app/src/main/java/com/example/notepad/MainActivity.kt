package com.example.notepad

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notepad.ui.LocalNotepadTheme
import com.example.notepad.ui.NotepadApp
import com.example.notepad.viewmodel.NotepadViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val incomingTextShare = MutableStateFlow<IncomingTextShare?>(null)
    private var nextShareId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        handleIncomingShareIntent(intent)

        setContent {
            val viewModel: NotepadViewModel = viewModel()
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
            val notes by viewModel.notes.collectAsStateWithLifecycle()
            val selectedFolderId by viewModel.selectedFolderId.collectAsStateWithLifecycle()
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val listMode by viewModel.listMode.collectAsStateWithLifecycle()
            val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
            val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
            val reminderFilter by viewModel.reminderFilter.collectAsStateWithLifecycle()
            val quickFilter by viewModel.quickFilter.collectAsStateWithLifecycle()
            val editorFontSize by viewModel.editorFontSize.collectAsStateWithLifecycle()
            val isRecognizingText by viewModel.isRecognizingText.collectAsStateWithLifecycle()
            val sharedText by incomingTextShare.collectAsStateWithLifecycle()

            LocalNotepadTheme {
                NotepadApp(
                    folders = folders,
                    allNotes = allNotes,
                    notes = notes,
                    selectedFolderId = selectedFolderId,
                    searchQuery = searchQuery,
                    listMode = listMode,
                    sortOption = sortOption,
                    typeFilter = typeFilter,
                    reminderFilter = reminderFilter,
                    quickFilter = quickFilter,
                    editorFontSize = editorFontSize,
                    isRecognizingText = isRecognizingText,
                    incomingTextShare = sharedText,
                    onIncomingTextShareHandled = { handledId ->
                        if (incomingTextShare.value?.id == handledId) {
                            incomingTextShare.value = null
                            setIntent(Intent(Intent.ACTION_MAIN))
                        }
                    },
                    viewModel = viewModel,
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.rgb(255, 251, 254)
        window.navigationBarColor = Color.rgb(255, 247, 215)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        val sharedText = extractIncomingTextShare(intent) ?: return
        incomingTextShare.value = sharedText.copy(id = ++nextShareId)
    }

    private fun extractIncomingTextShare(intent: Intent?): IncomingTextShare? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("text/")) return null

        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.getStringExtra(Intent.EXTRA_TITLE)

        return IncomingTextShare(
            id = 0L,
            subject = subject?.trim()?.takeIf { it.isNotBlank() },
            text = text,
        )
    }
}

data class IncomingTextShare(
    val id: Long,
    val subject: String?,
    val text: String,
)
