package com.example.notepad.ui

import android.content.Context
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notepad.IncomingTextShare
import com.example.notepad.data.ALL_NOTES_FILTER_NAME
import com.example.notepad.data.AppLanguage
import com.example.notepad.data.DEFAULT_FOLDER_ID
import com.example.notepad.data.DEFAULT_FOLDER_NAME
import com.example.notepad.data.DEFAULT_DRAWING_COLOR_ARGB
import com.example.notepad.data.DEFAULT_DRAWING_STROKE_WIDTH
import com.example.notepad.data.DrawingJson
import com.example.notepad.data.DrawingPoint
import com.example.notepad.data.DrawingStroke
import com.example.notepad.data.DrawingTools
import com.example.notepad.data.EditorFontSize
import com.example.notepad.data.FolderEntity
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteQuickFilter
import com.example.notepad.data.NoteListMode
import com.example.notepad.data.NoteSortOption
import com.example.notepad.data.NoteTypeFilter
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.ReminderFilter
import com.example.notepad.data.renderDrawingPng
import com.example.notepad.viewmodel.NotepadViewModel
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface AppScreen {
    data object Main : AppScreen
    data object Settings : AppScreen
    data class TextEditor(val noteId: Long) : AppScreen
    data class DrawingEditor(val noteId: Long) : AppScreen
}

private const val BACKUP_FILE_NAME = "claw-notes-backup.json"
private const val DEFAULT_DRAWING_EXPORT_WIDTH = 1080
private const val DEFAULT_DRAWING_EXPORT_HEIGHT = 1440
private const val MAX_DRAWING_EXPORT_DIMENSION = 4096
private const val NOTE_PREVIEW_MAX_CHARS = 160
private const val NOTE_PREVIEW_CONTEXT_BEFORE = 45
private const val NOTE_PREVIEW_CONTEXT_AFTER = 110
private val NOTE_PAPER_BACKGROUND = Color(0xFFFFF7D7)
private val NOTE_PAPER_SURFACE = Color(0xFFFFFBEA)

private enum class SaveStatus {
    Saving,
    Saved,
}

private enum class DrawingTool {
    Pen,
    Eraser,
}

private enum class DrawingBrushSize(
    val penWidthPx: Float,
    val eraserSizeDp: Float,
) {
    Thin(3f, 24f),
    Medium(DEFAULT_DRAWING_STROKE_WIDTH, 48f),
    Thick(10f, 80f),
}

private enum class DrawingColorOption(val colorArgb: Int) {
    Black(DEFAULT_DRAWING_COLOR_ARGB),
    Red(0xFFE53935.toInt()),
    Blue(0xFF1E88E5.toInt()),
    Green(0xFF43A047.toInt()),
}

@Composable
fun LocalNotepadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2F5D50),
            secondary = Color(0xFF6D5E2E),
            tertiary = Color(0xFF6A4C93),
            surface = Color(0xFFFFFBFE),
            background = Color(0xFFFAFAF7),
        ),
        typography = Typography(),
        content = content,
    )
}

@Composable
fun NotepadApp(
    folders: List<FolderEntity>,
    notes: List<NoteEntity>,
    selectedFolderId: Long?,
    searchQuery: String,
    listMode: NoteListMode,
    sortOption: NoteSortOption,
    typeFilter: NoteTypeFilter,
    reminderFilter: ReminderFilter,
    quickFilter: NoteQuickFilter,
    appLanguage: AppLanguage,
    editorFontSize: EditorFontSize,
    isRecognizingText: Boolean,
    incomingTextShare: IncomingTextShare?,
    onIncomingTextShareHandled: (Long) -> Unit,
    viewModel: NotepadViewModel,
) {
    var screen: AppScreen by remember { mutableStateOf(AppScreen.Main) }
    val text = remember(appLanguage) { uiTextFor(appLanguage) }
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.createOcrTextNote(
            imageUri = uri,
            fallbackTitlePrefix = text.ocrNoteDefaultTitle,
            onCreated = { noteId ->
                screen = AppScreen.TextEditor(noteId)
            },
            onNoText = {
                Toast.makeText(context, text.ocrNoText, Toast.LENGTH_SHORT).show()
            },
            onFailed = {
                Toast.makeText(context, text.ocrFailed, Toast.LENGTH_SHORT).show()
            },
        )
    }

    LaunchedEffect(incomingTextShare?.id) {
        val share = incomingTextShare ?: return@LaunchedEffect
        viewModel.createSharedTextNote(
            subject = share.subject,
            sharedText = share.text,
            defaultTitle = text.sharedNoteDefaultTitle,
        ) { noteId ->
            screen = AppScreen.TextEditor(noteId)
            onIncomingTextShareHandled(share.id)
        }
    }

    when (val currentScreen = screen) {
        AppScreen.Main -> MainScreen(
            folders = folders,
            notes = notes,
            selectedFolderId = selectedFolderId,
            searchQuery = searchQuery,
            listMode = listMode,
            sortOption = sortOption,
            typeFilter = typeFilter,
            reminderFilter = reminderFilter,
            quickFilter = quickFilter,
            appLanguage = appLanguage,
            text = text,
            onSelectFolder = viewModel::selectFolder,
            onSearchQueryChange = viewModel::setSearchQuery,
            onListModeChange = viewModel::setListMode,
            onSortOptionChange = viewModel::setSortOption,
            onTypeFilterChange = viewModel::setTypeFilter,
            onReminderFilterChange = viewModel::setReminderFilter,
            onQuickFilterChange = viewModel::setQuickFilter,
            onSelectLanguage = viewModel::setLanguage,
            onOpenSettings = { screen = AppScreen.Settings },
            onCreateFolder = viewModel::createFolder,
            onRenameFolder = viewModel::renameFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onCreateTextNote = {
                viewModel.createTextNote { noteId ->
                    screen = AppScreen.TextEditor(noteId)
                }
            },
            onCreateDrawingNote = {
                viewModel.createDrawingNote { noteId ->
                    screen = AppScreen.DrawingEditor(noteId)
                }
            },
            onCreateOcrNote = {
                imagePickerLauncher.launch("image/*")
            },
            onOpenNote = { note ->
                screen = if (note.type == NoteTypes.DRAWING) {
                    AppScreen.DrawingEditor(note.id)
                } else {
                    AppScreen.TextEditor(note.id)
                }
            },
            onMoveNote = viewModel::moveNote,
            onDeleteNote = viewModel::deleteNote,
            onDeleteNotes = { noteIds -> noteIds.forEach(viewModel::deleteNote) },
            onRestoreNote = viewModel::restoreNote,
            onPermanentlyDeleteNote = viewModel::permanentlyDeleteNote,
            onPermanentlyDeleteNotes = { noteIds -> noteIds.forEach(viewModel::permanentlyDeleteNote) },
            onTogglePinned = { note -> viewModel.setNotePinned(note.id, !note.isPinned) },
        )

        AppScreen.Settings -> SettingsScreen(
            text = text,
            appLanguage = appLanguage,
            editorFontSize = editorFontSize,
            viewModel = viewModel,
            onEditorFontSizeChange = viewModel::setEditorFontSize,
            onBack = { screen = AppScreen.Main },
        )

        is AppScreen.TextEditor -> TextEditorScreen(
            noteId = currentScreen.noteId,
            folders = folders,
            text = text,
            editorFontSize = editorFontSize,
            appLanguage = appLanguage,
            viewModel = viewModel,
            onBack = { screen = AppScreen.Main },
            onDeleted = { screen = AppScreen.Main },
        )

        is AppScreen.DrawingEditor -> DrawingEditorScreen(
            noteId = currentScreen.noteId,
            folders = folders,
            text = text,
            appLanguage = appLanguage,
            viewModel = viewModel,
            onBack = { screen = AppScreen.Main },
            onDeleted = { screen = AppScreen.Main },
        )
    }

    if (isRecognizingText) {
        OcrProgressDialog(text = text)
    }
}

@Composable
private fun OcrProgressDialog(text: UiText) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text.recognizingText) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(text.recognizingText)
            }
        },
        confirmButton = {},
        modifier = Modifier.testTag("ocr_progress_dialog"),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    folders: List<FolderEntity>,
    notes: List<NoteEntity>,
    selectedFolderId: Long?,
    searchQuery: String,
    listMode: NoteListMode,
    sortOption: NoteSortOption,
    typeFilter: NoteTypeFilter,
    reminderFilter: ReminderFilter,
    quickFilter: NoteQuickFilter,
    appLanguage: AppLanguage,
    text: UiText,
    onSelectFolder: (Long?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onListModeChange: (NoteListMode) -> Unit,
    onSortOptionChange: (NoteSortOption) -> Unit,
    onTypeFilterChange: (NoteTypeFilter) -> Unit,
    onReminderFilterChange: (ReminderFilter) -> Unit,
    onQuickFilterChange: (NoteQuickFilter) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onCreateTextNote: () -> Unit,
    onCreateDrawingNote: () -> Unit,
    onCreateOcrNote: () -> Unit,
    onOpenNote: (NoteEntity) -> Unit,
    onMoveNote: (Long, Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onDeleteNotes: (Set<Long>) -> Unit,
    onRestoreNote: (Long) -> Unit,
    onPermanentlyDeleteNote: (Long) -> Unit,
    onPermanentlyDeleteNotes: (Set<Long>) -> Unit,
    onTogglePinned: (NoteEntity) -> Unit,
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var noteToMove by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToPermanentlyDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var selectedNoteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedNotesToDelete by remember { mutableStateOf<Set<Long>?>(null) }
    val selectedFolder = folders.firstOrNull { it.id == selectedFolderId }
    val isTrash = listMode == NoteListMode.Trash
    val isSelectionMode = selectedNoteIds.isNotEmpty()
    val visibleNoteIds = remember(notes) { notes.map { it.id }.toSet() }
    fun clearNoteSelection() {
        selectedNoteIds = emptySet()
        selectedNotesToDelete = null
    }

    LaunchedEffect(visibleNoteIds) {
        selectedNoteIds = selectedNoteIds.intersect(visibleNoteIds)
    }

    LaunchedEffect(listMode, selectedFolderId, searchQuery, quickFilter) {
        clearNoteSelection()
    }

    BackHandler(enabled = isSelectionMode, onBack = ::clearNoteSelection)

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = text.selectedNotesCount(selectedNoteIds.size),
                            modifier = Modifier.testTag("selected_notes_count"),
                        )
                    },
                    navigationIcon = {
                        TextButton(
                            onClick = ::clearNoteSelection,
                            modifier = Modifier.testTag("cancel_note_selection_button"),
                        ) {
                            Text(text.cancel)
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { selectedNotesToDelete = selectedNoteIds },
                            modifier = Modifier.testTag("delete_selected_notes_button"),
                        ) {
                            Text(if (isTrash) text.permanentlyDelete else text.moveToTrash)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(text.appName) },
                    actions = {
                        TextButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("settings_button"),
                        ) {
                            Text(text.settings)
                        }
                        LanguageSelector(
                            appLanguage = appLanguage,
                            text = text,
                            onSelectLanguage = onSelectLanguage,
                        )
                    },
                )
            }
        },
        floatingActionButton = {
            if (!isTrash && !isSelectionMode) {
                Box {
                    FloatingActionButton(
                        onClick = { addMenuExpanded = true },
                        modifier = Modifier.testTag("add_note_button"),
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                    DropdownMenu(
                        expanded = addMenuExpanded,
                        onDismissRequest = { addMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(text.newTextNote) },
                            modifier = Modifier.testTag("new_text_note_menu_item"),
                            onClick = {
                                addMenuExpanded = false
                                onCreateTextNote()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text.newDrawingNote) },
                            modifier = Modifier.testTag("new_drawing_note_menu_item"),
                            onClick = {
                                addMenuExpanded = false
                                onCreateDrawingNote()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text.ocrFromImage) },
                            modifier = Modifier.testTag("ocr_from_image_menu_item"),
                            onClick = {
                                addMenuExpanded = false
                                onCreateOcrNote()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text.newFolder) },
                            modifier = Modifier.testTag("new_folder_menu_item"),
                            onClick = {
                                addMenuExpanded = false
                                showCreateFolderDialog = true
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("knowledge_header"),
            ) {
                ListModeRow(
                    listMode = listMode,
                    text = text,
                    onListModeChange = onListModeChange,
                )

                FolderFilterRow(
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    text = text,
                    onSelectFolder = onSelectFolder,
                )

                selectedFolder?.let { folder ->
                    FolderActionRow(
                        folder = folder,
                        text = text,
                        onRename = { folderToRename = folder },
                        onDelete = { folderToDelete = folder },
                    )
                }

                SearchBar(
                    searchQuery = searchQuery,
                    text = text,
                    onSearchQueryChange = onSearchQueryChange,
                )

                NoteFilterRow(
                    sortOption = sortOption,
                    quickFilter = quickFilter,
                    text = text,
                    onSortOptionChange = onSortOptionChange,
                    onQuickFilterChange = onQuickFilterChange,
                )

                Text(
                    text = text.resultCount(notes.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("note_result_count"),
                )

                HorizontalDivider()
            }

            NoteList(
                notes = notes,
                folders = folders,
                text = text,
                searchQuery = searchQuery,
                hasActiveFilters = quickFilter != NoteQuickFilter.All,
                listMode = listMode,
                appLanguage = appLanguage,
                selectedNoteIds = selectedNoteIds,
                onOpenNote = onOpenNote,
                onToggleNoteSelection = { note ->
                    selectedNoteIds = if (note.id in selectedNoteIds) {
                        selectedNoteIds - note.id
                    } else {
                        selectedNoteIds + note.id
                    }
                },
                onStartNoteSelection = { note ->
                    selectedNoteIds = selectedNoteIds + note.id
                },
                onMoveNote = { noteToMove = it },
                onDeleteNote = { noteToDelete = it },
                onRestoreNote = { note -> onRestoreNote(note.id) },
                onPermanentlyDeleteNote = { noteToPermanentlyDelete = it },
                onTogglePinned = onTogglePinned,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showCreateFolderDialog) {
        FolderNameDialog(
            title = text.newFolder,
            initialName = "",
            folders = folders,
            text = text,
            currentFolderId = null,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                onCreateFolder(name)
                showCreateFolderDialog = false
            },
        )
    }

    folderToRename?.let { folder ->
        FolderNameDialog(
            title = text.renameFolder,
            initialName = folder.name,
            folders = folders,
            text = text,
            currentFolderId = folder.id,
            onDismiss = { folderToRename = null },
            onConfirm = { name ->
                onRenameFolder(folder.id, name)
                folderToRename = null
            },
        )
    }

    folderToDelete?.let { folder ->
        ConfirmDialog(
            title = text.deleteFolder,
            body = text.deleteFolderBody(folderDisplayName(folder, text)),
            confirmText = text.delete,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { folderToDelete = null },
            onConfirm = {
                onDeleteFolder(folder.id)
                folderToDelete = null
            },
        )
    }

    noteToMove?.let { note ->
        MoveNoteDialog(
            folders = folders,
            text = text,
            currentFolderId = note.folderId,
            onDismiss = { noteToMove = null },
            onMove = { folderId ->
                onMoveNote(note.id, folderId)
                noteToMove = null
            },
        )
    }

    noteToDelete?.let { note ->
        ConfirmDialog(
            title = text.deleteNote,
            body = text.deleteNoteBody,
            confirmText = text.moveToTrash,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { noteToDelete = null },
            onConfirm = {
                onDeleteNote(note.id)
                noteToDelete = null
            },
        )
    }

    noteToPermanentlyDelete?.let { note ->
        ConfirmDialog(
            title = text.permanentlyDeleteNote,
            body = text.permanentlyDeleteNoteBody,
            confirmText = text.permanentlyDelete,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { noteToPermanentlyDelete = null },
            onConfirm = {
                onPermanentlyDeleteNote(note.id)
                noteToPermanentlyDelete = null
            },
        )
    }

    selectedNotesToDelete?.let { noteIds ->
        ConfirmDialog(
            title = if (isTrash) text.permanentlyDeleteSelectedNotes else text.deleteSelectedNotes,
            body = if (isTrash) text.permanentlyDeleteSelectedNotesBody else text.deleteSelectedNotesBody,
            confirmText = if (isTrash) text.permanentlyDelete else text.moveToTrash,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { selectedNotesToDelete = null },
            onConfirm = {
                if (isTrash) {
                    onPermanentlyDeleteNotes(noteIds)
                } else {
                    onDeleteNotes(noteIds)
                }
                clearNoteSelection()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    text: UiText,
    appLanguage: AppLanguage,
    editorFontSize: EditorFontSize,
    viewModel: NotepadViewModel,
    onEditorFontSizeChange: (EditorFontSize) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var lastBackupAt by remember { mutableStateOf<Long?>(null) }
    var lastRestoreAt by remember { mutableStateOf<Long?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var isBackupInProgress by remember { mutableStateOf(false) }
    var isRestoreInProgress by remember { mutableStateOf(false) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val backupJson = pendingBackupJson
        pendingBackupJson = null
        if (uri == null || backupJson == null) return@rememberLauncherForActivityResult

        scope.launch {
            isBackupInProgress = true
            try {
                withContext(Dispatchers.IO) {
                    writeTextToUri(context, uri, backupJson)
                }
                lastBackupAt = System.currentTimeMillis()
                Toast.makeText(context, text.backupComplete, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, text.backupFailed, Toast.LENGTH_SHORT).show()
            } finally {
                isBackupInProgress = false
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            isRestoreInProgress = true
            try {
                val backupJson = withContext(Dispatchers.IO) {
                    readTextFromUri(context, uri)
                }
                viewModel.importBackupJson(backupJson)
                lastRestoreAt = System.currentTimeMillis()
                Toast.makeText(context, text.restoreComplete, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, text.restoreFailed, Toast.LENGTH_SHORT).show()
            } finally {
                isRestoreInProgress = false
            }
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text.settings) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text.back)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = text.textEditor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = text.editorFontSize,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EditorFontSizeRow(
                editorFontSize = editorFontSize,
                text = text,
                onEditorFontSizeChange = onEditorFontSizeChange,
            )
            HorizontalDivider()
            Text(
                text = text.googleDriveBackup,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = text.backupTargetHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            lastBackupAt?.let { backedUpAt ->
                Text(
                    text = "${text.lastBackup}: ${formatTime(backedUpAt, appLanguage)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("last_backup_status"),
                )
            }
            lastRestoreAt?.let { restoredAt ->
                Text(
                    text = "${text.lastRestore}: ${formatTime(restoredAt, appLanguage)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("last_restore_status"),
                )
            }
            if (isBackupInProgress || isRestoreInProgress) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("backup_restore_progress"),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = if (isBackupInProgress) text.backupInProgress else text.restoreInProgress,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        try {
                            pendingBackupJson = viewModel.exportBackupJson()
                            createBackupLauncher.launch(BACKUP_FILE_NAME)
                        } catch (_: Exception) {
                            Toast.makeText(context, text.backupFailed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isBackupInProgress && !isRestoreInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_button"),
            ) {
                Text(text.backupToGoogleDrive)
            }
            Button(
                onClick = { showRestoreConfirmDialog = true },
                enabled = !isBackupInProgress && !isRestoreInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restore_button"),
            ) {
                Text(text.restoreFromBackup)
            }
        }
    }

    if (showRestoreConfirmDialog) {
        ConfirmDialog(
            title = text.restoreBackupConfirmTitle,
            body = text.restoreBackupConfirmBody,
            confirmText = text.restoreFromBackup,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { showRestoreConfirmDialog = false },
            onConfirm = {
                showRestoreConfirmDialog = false
                restoreBackupLauncher.launch(
                    arrayOf("application/json", "text/plain", "*/*"),
                )
            },
        )
    }
}

@Composable
private fun EditorFontSizeRow(
    editorFontSize: EditorFontSize,
    text: UiText,
    onEditorFontSizeChange: (EditorFontSize) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditorFontSize.entries.forEach { size ->
            item {
                FilterChip(
                    selected = editorFontSize == size,
                    onClick = { onEditorFontSizeChange(size) },
                    label = { Text(size.label(text)) },
                    modifier = Modifier.testTag("font_size_${size.name}"),
                )
            }
        }
    }
}

@Composable
private fun ListModeRow(
    listMode: NoteListMode,
    text: UiText,
    onListModeChange: (NoteListMode) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = listMode == NoteListMode.Active,
                onClick = { onListModeChange(NoteListMode.Active) },
                label = { Text(text.activeNotes) },
                modifier = Modifier.testTag("active_notes_filter"),
            )
        }
        item {
            FilterChip(
                selected = listMode == NoteListMode.Trash,
                onClick = { onListModeChange(NoteListMode.Trash) },
                label = { Text(text.trash) },
                modifier = Modifier.testTag("trash_filter"),
            )
        }
    }
}

@Composable
private fun NoteFilterRow(
    sortOption: NoteSortOption,
    quickFilter: NoteQuickFilter,
    text: UiText,
    onSortOptionChange: (NoteSortOption) -> Unit,
    onQuickFilterChange: (NoteQuickFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortSelector(
                sortOption = sortOption,
                text = text,
                onSortOptionChange = onSortOptionChange,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = sortOption == NoteSortOption.UpdatedAt,
                onClick = { onSortOptionChange(NoteSortOption.UpdatedAt) },
                label = { Text(text.recentlyUpdated) },
                modifier = Modifier.testTag("recently_updated_chip"),
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NoteQuickFilter.entries.forEach { filter ->
                item {
                    FilterChip(
                        selected = quickFilter == filter,
                        onClick = { onQuickFilterChange(filter) },
                        label = { Text(filter.label(text)) },
                        modifier = Modifier.testTag("quick_filter_${filter.name}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortSelector(
    sortOption: NoteSortOption,
    text: UiText,
    onSortOptionChange: (NoteSortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "${text.sortBy}: ${sortOption.label(text)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            NoteSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label(text)) },
                    modifier = Modifier.testTag("sort_${option.name}"),
                    onClick = {
                        expanded = false
                        onSortOptionChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun TypeFilterSelector(
    typeFilter: NoteTypeFilter,
    text: UiText,
    onTypeFilterChange: (NoteTypeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "${text.noteType}: ${typeFilter.label(text)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            NoteTypeFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.label(text)) },
                    modifier = Modifier.testTag("type_${filter.name}"),
                    onClick = {
                        expanded = false
                        onTypeFilterChange(filter)
                    },
                )
            }
        }
    }
}

@Composable
private fun ReminderFilterSelector(
    reminderFilter: ReminderFilter,
    text: UiText,
    onReminderFilterChange: (ReminderFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "${text.reminderFilter}: ${reminderFilter.label(text)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ReminderFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.label(text)) },
                    modifier = Modifier.testTag("reminder_${filter.name}"),
                    onClick = {
                        expanded = false
                        onReminderFilterChange(filter)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    searchQuery: String,
    text: UiText,
    onSearchQueryChange: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        label = { Text(text.search) },
        placeholder = { Text(text.searchPlaceholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { keyboardController?.hide() },
        ),
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                TextButton(onClick = { onSearchQueryChange("") }) {
                    Text(text.clear)
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("note_search_input"),
    )
}

@Composable
private fun LanguageSelector(
    appLanguage: AppLanguage,
    text: UiText,
    onSelectLanguage: (AppLanguage) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("language_button"),
        ) {
            Text("${text.language}: ${appLanguage.displayName}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        expanded = false
                        onSelectLanguage(language)
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderFilterRow(
    folders: List<FolderEntity>,
    selectedFolderId: Long?,
    text: UiText,
    onSelectFolder: (Long?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedFolderId == null,
                onClick = { onSelectFolder(null) },
                label = { Text(text.allNotes) },
            )
        }
        items(folders, key = { it.id }) { folder ->
            FilterChip(
                selected = selectedFolderId == folder.id,
                onClick = { onSelectFolder(folder.id) },
                label = {
                    Text(
                        folderDisplayName(folder, text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun FolderActionRow(
    folder: FolderEntity,
    text: UiText,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = folderDisplayName(folder, text),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (folder.id != DEFAULT_FOLDER_ID) {
            TextButton(onClick = onRename) {
                Text(text.rename)
            }
            TextButton(onClick = onDelete) {
                Text(text.delete)
            }
        }
    }
}

@Composable
private fun NoteList(
    notes: List<NoteEntity>,
    folders: List<FolderEntity>,
    text: UiText,
    searchQuery: String,
    hasActiveFilters: Boolean,
    listMode: NoteListMode,
    appLanguage: AppLanguage,
    selectedNoteIds: Set<Long>,
    onOpenNote: (NoteEntity) -> Unit,
    onToggleNoteSelection: (NoteEntity) -> Unit,
    onStartNoteSelection: (NoteEntity) -> Unit,
    onMoveNote: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onRestoreNote: (NoteEntity) -> Unit,
    onPermanentlyDeleteNote: (NoteEntity) -> Unit,
    onTogglePinned: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelectionMode = selectedNoteIds.isNotEmpty()

    if (notes.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .padding(20.dp)
                    .testTag("note_empty_state"),
            ) {
                Text(
                    text = when {
                        searchQuery.isNotBlank() || hasActiveFilters -> text.noSearchOrFilterResults
                        listMode == NoteListMode.Trash -> text.noDeletedNotes
                        else -> text.noNotes
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(notes, key = { it.id }) { note ->
            NoteRow(
                note = note,
                folderName = folderDisplayNameById(note.folderId, folders, text),
                text = text,
                searchQuery = searchQuery,
                appLanguage = appLanguage,
                isSelectionMode = isSelectionMode,
                isSelected = note.id in selectedNoteIds,
                onOpen = { onOpenNote(note) },
                onToggleSelection = { onToggleNoteSelection(note) },
                onStartSelection = { onStartNoteSelection(note) },
                onMove = { onMoveNote(note) },
                onDelete = { onDeleteNote(note) },
                onRestore = { onRestoreNote(note) },
                onPermanentlyDelete = { onPermanentlyDeleteNote(note) },
                onTogglePinned = { onTogglePinned(note) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: NoteEntity,
    folderName: String,
    text: UiText,
    searchQuery: String,
    appLanguage: AppLanguage,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onStartSelection: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentlyDelete: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else if (!note.isDeleted) {
                        onOpen()
                    }
                },
                onLongClick = onStartSelection,
            )
            .testTag("note_card_${note.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        modifier = Modifier.testTag("note_selection_checkbox_${note.id}"),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (note.isPinned && !note.isDeleted) {
                    Text(
                        text = "★ ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    text = highlightedText(
                        value = noteTitle(note, text),
                        query = searchQuery,
                        highlightColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (note.type == NoteTypes.DRAWING) text.drawing else text.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("note_type_chip"),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = noteMetadata(note, folderName, text, appLanguage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            notePreview(note, searchQuery)?.let { preview ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = highlightedText(
                        value = preview,
                        query = searchQuery,
                        highlightColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("note_preview_${note.id}"),
                )
            }
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (note.isDeleted) {
                        TextButton(onClick = onRestore) {
                            Text(text.restore)
                        }
                        TextButton(onClick = onPermanentlyDelete) {
                            Text(text.permanentlyDelete)
                        }
                    } else {
                        TextButton(
                            onClick = onTogglePinned,
                            modifier = Modifier.testTag("pin_note_${note.id}"),
                        ) {
                            Text(if (note.isPinned) text.unpin else text.pin)
                        }
                        TextButton(onClick = onMove) {
                            Text(text.move)
                        }
                        TextButton(onClick = onDelete) {
                            Text(text.moveToTrash)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditorScreen(
    noteId: Long,
    folders: List<FolderEntity>,
    text: UiText,
    editorFontSize: EditorFontSize,
    appLanguage: AppLanguage,
    viewModel: NotepadViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val note by viewModel.observeNote(noteId).collectAsStateWithLifecycle(initialValue = null)
    var title by remember(noteId) { mutableStateOf("") }
    var contentField by remember(noteId) { mutableStateOf(TextFieldValue("")) }
    var loadedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var modeInitializedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var isEditing by remember(noteId) { mutableStateOf(false) }
    var isFocusWriting by remember(noteId) { mutableStateOf(false) }
    var isContentFocused by remember(noteId) { mutableStateOf(false) }
    var isMetadataExpanded by remember(noteId) { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isMoreMenuExpanded by remember { mutableStateOf(false) }
    var isFindVisible by remember(noteId) { mutableStateOf(false) }
    var findQuery by remember(noteId) { mutableStateOf("") }
    var activeFindIndex by remember(noteId) { mutableStateOf(0) }
    var saveStatus by remember(noteId) { mutableStateOf(SaveStatus.Saved) }
    var isSavingAndLeaving by remember(noteId) { mutableStateOf(false) }
    var lastSavedAt by remember(noteId) { mutableStateOf<Long?>(null) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var pendingReminderAt by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val titleFocusRequester = remember(noteId) { FocusRequester() }
    val contentFocusRequester = remember(noteId) { FocusRequester() }
    val findFocusRequester = remember(noteId) { FocusRequester() }
    val editContentScrollState = rememberScrollState()
    val readScrollState = rememberScrollState()
    var editContentLayout by remember(noteId) { mutableStateOf<TextLayoutResult?>(null) }
    var editContentViewportHeight by remember(noteId) { mutableStateOf(0) }
    var readContentLayout by remember(noteId) { mutableStateOf<TextLayoutResult?>(null) }
    var readViewportHeight by remember(noteId) { mutableStateOf(0) }
    var readViewportTopInRoot by remember(noteId) { mutableStateOf(0f) }
    var readContentTopInScroll by remember(noteId) { mutableStateOf(0f) }
    val autoSaveVersion = remember(noteId) { AtomicLong(0L) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val editContentPaddingPx = with(LocalDensity.current) { 16.dp.toPx() }
    val editCursorTopPaddingPx = with(LocalDensity.current) { 18.dp.toPx() }
    val editCursorBottomPaddingPx = with(LocalDensity.current) { 56.dp.toPx() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val content = contentField.text
    val findMatches = remember(content, findQuery) { findInNoteMatches(content, findQuery) }
    val currentFindIndex = activeFindIndex.coerceIn(0, (findMatches.size - 1).coerceAtLeast(0))
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        pendingReminderAt?.let { reminderAt ->
            viewModel.setNoteReminder(noteId, reminderAt)
        }
        pendingReminderAt = null
    }
    val exportTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val exportText = pendingExportText
        pendingExportText = null
        if (uri == null || exportText == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    writeTextToUri(context, uri, exportText)
                }
                Toast.makeText(context, text.exportComplete, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, text.exportFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(note?.id) {
        val loaded = note ?: return@LaunchedEffect
        title = loaded.title
        contentField = TextFieldValue(loaded.textContent.orEmpty())
        loadedNoteId = loaded.id
        lastSavedAt = loaded.updatedAt
        saveStatus = SaveStatus.Saved
        if (modeInitializedNoteId != loaded.id) {
            isEditing = loaded.title.isBlank() && loaded.textContent.orEmpty().isBlank()
            modeInitializedNoteId = loaded.id
        }
    }

    LaunchedEffect(note?.updatedAt) {
        val loaded = note ?: return@LaunchedEffect
        if (loaded.id == noteId) {
            lastSavedAt = loaded.updatedAt
        }
    }

    LaunchedEffect(loadedNoteId) {
        if (loadedNoteId == noteId && isEditing) {
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(isFindVisible) {
        if (isFindVisible) {
            findFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(findQuery, content, findMatches.size) {
        activeFindIndex = if (findMatches.isEmpty()) {
            0
        } else {
            activeFindIndex.coerceIn(0, findMatches.lastIndex)
        }
    }

    LaunchedEffect(noteId, loadedNoteId, title, content) {
        if (loadedNoteId == noteId) {
            val current = note ?: return@LaunchedEffect
            if (title == current.title && content == current.textContent.orEmpty()) {
                saveStatus = SaveStatus.Saved
                return@LaunchedEffect
            }
            val pendingVersion = autoSaveVersion.get()
            saveStatus = SaveStatus.Saving
            delay(500)
            if (pendingVersion != autoSaveVersion.get()) return@LaunchedEffect
            lastSavedAt = viewModel.saveTextNoteNow(noteId, title, content) ?: System.currentTimeMillis()
            saveStatus = SaveStatus.Saved
        }
    }

    fun hasUnsavedTextNote(
        currentNote: NoteEntity? = note,
        titleValue: String = title,
        contentValue: String = content,
        loadedId: Long? = loadedNoteId,
    ): Boolean {
        val current = currentNote ?: return false
        return loadedId == noteId &&
            (titleValue != current.title || contentValue != current.textContent.orEmpty())
    }

    fun savePendingTextNote() {
        val titleToSave = title
        val contentToSave = contentField.text
        if (hasUnsavedTextNote(note, titleToSave, contentToSave, loadedNoteId)) {
            viewModel.saveTextNote(noteId, titleToSave, contentToSave)
        }
    }

    DisposableEffect(lifecycleOwner, noteId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                savePendingTextNote()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            savePendingTextNote()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun saveAndBack() {
        if (isSavingAndLeaving) return
        isSavingAndLeaving = true
        autoSaveVersion.incrementAndGet()
        val titleToSave = title
        val contentToSave = contentField.text
        keyboardController?.hide()
        scope.launch {
            saveStatus = SaveStatus.Saving
            lastSavedAt = viewModel.saveTextNoteNow(noteId, titleToSave, contentToSave) ?: lastSavedAt
            saveStatus = SaveStatus.Saved
            onBack()
        }
    }

    fun selectFindMatch(index: Int) {
        val nextIndex = index.normalizeFindMatchIndex(findMatches.size)
        if (nextIndex < 0) return
        val range = findMatches[nextIndex]
        activeFindIndex = nextIndex
        if (isEditing) {
            contentField = contentField.copy(
                selection = TextRange(range.first, range.last + 1),
            )
            contentFocusRequester.requestFocus()
        }
        scope.launch {
            if (isEditing) {
                editContentScrollState.scrollMatchIntoView(
                    textLayoutResult = editContentLayout,
                    matchRange = range,
                    viewportHeight = editContentViewportHeight,
                    contentTopPx = editContentPaddingPx,
                )
            } else {
                readScrollState.scrollMatchIntoView(
                    textLayoutResult = readContentLayout,
                    matchRange = range,
                    viewportHeight = readViewportHeight,
                    contentTopPx = readContentTopInScroll,
                )
            }
        }
    }

    LaunchedEffect(
        isEditing,
        isFindVisible,
        currentFindIndex,
        findMatches,
        editContentLayout,
        editContentViewportHeight,
        editContentScrollState.maxValue,
    ) {
        if (!isEditing || !isFindVisible) return@LaunchedEffect
        editContentScrollState.scrollMatchIntoView(
            textLayoutResult = editContentLayout,
            matchRange = findMatches.getOrNull(currentFindIndex),
            viewportHeight = editContentViewportHeight,
            contentTopPx = editContentPaddingPx,
        )
    }

    LaunchedEffect(
        isEditing,
        isContentFocused,
        isFocusWriting,
        contentField.selection,
        contentField.text.length,
        editContentLayout,
        editContentViewportHeight,
        editContentScrollState.maxValue,
    ) {
        if (!isEditing || (!isContentFocused && !isFocusWriting)) return@LaunchedEffect
        editContentScrollState.scrollCursorIntoView(
            textLayoutResult = editContentLayout,
            cursorOffset = contentField.selection.max,
            viewportHeight = editContentViewportHeight,
            contentTopPx = editCursorTopPaddingPx,
            viewportBottomPaddingPx = editCursorBottomPaddingPx,
        )
    }

    LaunchedEffect(
        isEditing,
        isFindVisible,
        currentFindIndex,
        findMatches,
        readContentLayout,
        readViewportHeight,
        readContentTopInScroll,
        readScrollState.maxValue,
    ) {
        if (isEditing || !isFindVisible) return@LaunchedEffect
        readScrollState.scrollMatchIntoView(
            textLayoutResult = readContentLayout,
            matchRange = findMatches.getOrNull(currentFindIndex),
            viewportHeight = readViewportHeight,
            contentTopPx = readContentTopInScroll,
        )
    }

    fun saveCurrentTextNoteThen(onSaved: (NoteEntity) -> Unit) {
        val currentNote = note ?: return
        val titleToSave = title
        val contentToSave = contentField.text
        autoSaveVersion.incrementAndGet()
        scope.launch {
            saveStatus = SaveStatus.Saving
            val savedAt = viewModel.saveTextNoteNow(noteId, titleToSave, contentToSave) ?: currentNote.updatedAt
            lastSavedAt = savedAt
            saveStatus = SaveStatus.Saved
            onSaved(
                currentNote.copy(
                    title = titleToSave,
                    textContent = contentToSave,
                    drawingData = null,
                    updatedAt = savedAt,
                ),
            )
        }
    }

    fun shareCurrentTextNote() {
        saveCurrentTextNoteThen { savedNote ->
            val folderName = folderDisplayNameById(savedNote.folderId, folders, text)
            sharePlainText(
                context = context,
                subject = noteTitle(savedNote, text),
                body = buildTextNoteDocumentText(savedNote, folderName, text, appLanguage),
                chooserTitle = text.shareChooserTitle,
            )
        }
    }

    fun exportCurrentTextNote() {
        saveCurrentTextNoteThen { savedNote ->
            val folderName = folderDisplayNameById(savedNote.folderId, folders, text)
            pendingExportText = buildTextNoteDocumentText(savedNote, folderName, text, appLanguage)
            exportTextLauncher.launch(defaultTextExportFileName(savedNote, text))
        }
    }

    fun submitReminder(reminderAt: Long) {
        if (reminderAt <= System.currentTimeMillis()) {
            Toast.makeText(context, text.reminderMustBeFuture, Toast.LENGTH_SHORT).show()
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingReminderAt = reminderAt
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNoteReminder(noteId, reminderAt)
        }
    }

    fun openDateTimePicker(currentReminderAt: Long?) {
        val calendar = Calendar.getInstance()
        val initialReminderAt = currentReminderAt?.takeIf { it > System.currentTimeMillis() }
        if (initialReminderAt == null) {
            calendar.add(Calendar.HOUR_OF_DAY, 1)
        } else {
            calendar.timeInMillis = initialReminderAt
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val selected = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        submitReminder(selected.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun insertIntoContent(prefix: String) {
        val currentContent = contentField.text
        val selection = contentField.selection
        val start = selection.min.coerceIn(0, currentContent.length)
        val end = selection.max.coerceIn(start, currentContent.length)
        val updatedContent = buildString {
            append(currentContent.substring(0, start))
            append(prefix)
            append(currentContent.substring(end))
        }
        autoSaveVersion.incrementAndGet()
        contentField = TextFieldValue(
            text = updatedContent,
            selection = TextRange(start + prefix.length),
        )
        contentFocusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(isEditing, isFocusWriting) {
        if (isEditing && loadedNoteId == noteId) {
            if (isFocusWriting) {
                contentFocusRequester.requestFocus()
            } else {
                titleFocusRequester.requestFocus()
            }
        } else if (!isEditing) {
            isFocusWriting = false
        }
    }

    LaunchedEffect(isContentFocused) {
        if (isContentFocused) {
            isMetadataExpanded = false
        }
    }

    val currentNote = note
    val isCompactEditor = isEditing && (isFocusWriting || isContentFocused)

    BackHandler(onBack = ::saveAndBack)

    Scaffold(
        containerColor = NOTE_PAPER_BACKGROUND,
        topBar = {
            TopAppBar(
                title = {
                    if (isEditing) {
                        Column {
                            Text(
                                title.ifBlank { text.untitledTextNote },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                saveStatus.label(text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier.testTag("text_note_top_save_status"),
                            )
                        }
                    } else {
                        Text(
                            text.textNote,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(
                        onClick = ::saveAndBack,
                        modifier = Modifier.testTag("back_button"),
                    ) {
                        Text(text.back)
                    }
                },
                actions = {
                    if (currentNote != null && !isEditing) {
                        TextButton(
                            onClick = { isEditing = true },
                            modifier = Modifier.testTag("edit_note_button"),
                        ) {
                            Text(text.edit)
                        }
                    }
                    TextButton(
                        onClick = { isFindVisible = true },
                        modifier = Modifier.testTag("find_in_note_button"),
                    ) {
                        Text(text.findInNote.take(4), maxLines = 1)
                    }
                    Box {
                        TextButton(
                            onClick = {
                                keyboardController?.hide()
                                isMoreMenuExpanded = true
                            },
                            modifier = Modifier.testTag("more_note_button"),
                        ) {
                            Text("...")
                        }
                        DropdownMenu(
                            expanded = isMoreMenuExpanded,
                            onDismissRequest = { isMoreMenuExpanded = false },
                            modifier = Modifier.testTag("text_note_overflow_menu"),
                        ) {
                            currentNote?.let { loaded ->
                                DropdownMenuItem(
                                    text = { Text(text.share) },
                                    modifier = Modifier.testTag("share_text_note_menu_item"),
                                    onClick = {
                                        isMoreMenuExpanded = false
                                        shareCurrentTextNote()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text.exportTxt) },
                                    modifier = Modifier.testTag("export_text_note_menu_item"),
                                    onClick = {
                                        isMoreMenuExpanded = false
                                        exportCurrentTextNote()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text.setReminder) },
                                    modifier = Modifier.testTag("set_reminder_menu_item"),
                                    onClick = {
                                        isMoreMenuExpanded = false
                                        openDateTimePicker(loaded.reminderAt)
                                    },
                                )
                                if (loaded.reminderAt != null) {
                                    DropdownMenuItem(
                                        text = { Text(text.clearReminder) },
                                        modifier = Modifier.testTag("clear_reminder_menu_item"),
                                        onClick = {
                                            isMoreMenuExpanded = false
                                            viewModel.setNoteReminder(noteId, null)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (loaded.isPinned) text.unpin else text.pin) },
                                    modifier = Modifier.testTag("toggle_pin_menu_item"),
                                    onClick = {
                                        isMoreMenuExpanded = false
                                        viewModel.setNotePinned(noteId, !loaded.isPinned)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text.moveToTrash) },
                                    modifier = Modifier.testTag("delete_text_note_menu_item"),
                                    onClick = {
                                        isMoreMenuExpanded = false
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (currentNote == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(text.noteNotFound)
            }
        } else if (isEditing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(NOTE_PAPER_BACKGROUND)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isFindVisible && !isMoreMenuExpanded) {
                    FindInNoteBar(
                        query = findQuery,
                        currentIndex = currentFindIndex,
                        matchCount = findMatches.size,
                        text = text,
                        findFocusRequester = findFocusRequester,
                        onQueryChange = {
                            findQuery = it
                            activeFindIndex = 0
                        },
                        onPrevious = {
                            selectFindMatch(previousFindMatchIndex(currentFindIndex, findMatches.size))
                        },
                        onNext = {
                            selectFindMatch(nextFindMatchIndex(currentFindIndex, findMatches.size))
                        },
                        onClearSearch = {
                            isFindVisible = false
                            findQuery = ""
                            activeFindIndex = 0
                        },
                    )
                }
                if (isCompactEditor) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NOTE_PAPER_SURFACE, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("text_note_focus_mode"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("text_note_compact_metadata"),
                        ) {
                            Text(
                                text = title.ifBlank { text.untitledTextNote },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = saveStatus.label(text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("text_note_save_status"),
                            )
                        }
                        TextButton(
                            onClick = { isMetadataExpanded = !isMetadataExpanded },
                            modifier = Modifier.testTag("toggle_metadata_button"),
                        ) {
                            Text(text.details, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(
                            onClick = {
                                isFocusWriting = false
                                savePendingTextNote()
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.testTag("toggle_focus_writer_button"),
                        ) {
                            Text(text.exitFocusWriting)
                        }
                    }
                }
                if (!isCompactEditor || isMetadataExpanded) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("text_note_edit_metadata"),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NOTE_PAPER_SURFACE)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = text.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            OutlinedTextField(
                                value = title,
                                onValueChange = {
                                    autoSaveVersion.incrementAndGet()
                                    title = it
                                },
                                placeholder = { Text(text.title) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { contentFocusRequester.requestFocus() },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(titleFocusRequester)
                                    .testTag("text_note_title"),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                NoteFolderSelector(
                                    folders = folders,
                                    text = text,
                                    currentFolderId = currentNote.folderId,
                                    onMove = { folderId -> viewModel.moveNote(noteId, folderId) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = saveStatus.label(text),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("text_note_save_status"),
                                    )
                                    Text(
                                        text = "${text.lastUpdated}: ${formatTime(lastSavedAt ?: currentNote.updatedAt, appLanguage)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("text_note_updated_time"),
                                    )
                                }
                            }
                            Text(
                                text = reminderStatus(currentNote.reminderAt, text, appLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentNote.reminderAt != null && currentNote.reminderAt <= System.currentTimeMillis()) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.testTag("note_reminder_status"),
                            )
                            if (currentNote.isPinned) {
                                Text(
                                    text = "★ ${text.pinned}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.testTag("text_note_pinned_indicator"),
                                )
                            }
                        }
                    }
                }
                if (!isCompactEditor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = text.content,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                isFocusWriting = true
                                contentFocusRequester.requestFocus()
                                keyboardController?.show()
                            },
                            modifier = Modifier.testTag("toggle_focus_writer_button"),
                        ) {
                            Text(text.focusWriting)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(NOTE_PAPER_SURFACE, RoundedCornerShape(10.dp))
                        .onSizeChanged { editContentViewportHeight = it.height }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                contentFocusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        }
                        .verticalScroll(editContentScrollState)
                        .testTag("text_note_content_scroll"),
                ) {
                    if (contentField.text.isBlank()) {
                        Text(
                            text = text.content,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = editorFontSize.fontSizeSp.sp,
                                lineHeight = (editorFontSize.fontSizeSp + 8).sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        )
                    }
                    BasicTextField(
                        value = contentField,
                        onValueChange = {
                            autoSaveVersion.incrementAndGet()
                            contentField = it
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = editorFontSize.fontSizeSp.sp,
                            lineHeight = (editorFontSize.fontSizeSp + 8).sp,
                        ),
                        visualTransformation = FindInNoteVisualTransformation(
                            query = findQuery,
                            activeMatchIndex = currentFindIndex,
                            matchColor = MaterialTheme.colorScheme.tertiaryContainer,
                            activeMatchColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        onTextLayout = { editContentLayout = it },
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 72.dp)
                            .focusRequester(contentFocusRequester)
                            .onFocusChanged { isContentFocused = it.isFocused }
                            .testTag("text_note_content"),
                    )
                }
                if (isCompactEditor) {
                    TextEditorAccessoryBar(
                        text = text,
                        onInsertCheckbox = { insertIntoContent("- [ ] ") },
                        onInsertBullet = { insertIntoContent("- ") },
                        onHideKeyboard = {
                            savePendingTextNote()
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(NOTE_PAPER_BACKGROUND)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .testTag("text_note_read_mode"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (isFindVisible && !isMoreMenuExpanded) {
                    FindInNoteBar(
                        query = findQuery,
                        currentIndex = currentFindIndex,
                        matchCount = findMatches.size,
                        text = text,
                        findFocusRequester = findFocusRequester,
                        onQueryChange = {
                            findQuery = it
                            activeFindIndex = 0
                        },
                        onPrevious = {
                            selectFindMatch(previousFindMatchIndex(currentFindIndex, findMatches.size))
                        },
                        onNext = {
                            selectFindMatch(nextFindMatchIndex(currentFindIndex, findMatches.size))
                        },
                        onClearSearch = {
                            isFindVisible = false
                            findQuery = ""
                            activeFindIndex = 0
                        },
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { readViewportHeight = it.height }
                        .onGloballyPositioned { readViewportTopInRoot = it.positionInRoot().y }
                        .verticalScroll(readScrollState)
                        .testTag("text_note_read_scroll"),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NOTE_PAPER_SURFACE)
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = title.ifBlank { text.untitledTextNote },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("text_note_read_title"),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = folderDisplayNameById(currentNote.folderId, folders, text),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (currentNote.isPinned) {
                                    Text(
                                        text = "★ ${text.pinned}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.testTag("text_note_pinned_indicator"),
                                    )
                                }
                            }
                            Text(
                                text = "${text.lastUpdated}: ${formatTime(lastSavedAt ?: currentNote.updatedAt, appLanguage)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = reminderStatus(currentNote.reminderAt, text, appLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentNote.reminderAt != null && currentNote.reminderAt <= System.currentTimeMillis()) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.testTag("note_reminder_status"),
                            )
                            HorizontalDivider()
                            Text(
                                text = findHighlightedText(
                                    value = content.ifBlank { text.content },
                                    query = findQuery,
                                    activeMatchIndex = currentFindIndex,
                                    matchColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    activeMatchColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = editorFontSize.fontSizeSp.sp,
                                    lineHeight = (editorFontSize.fontSizeSp + 10).sp,
                                ),
                                color = if (content.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                onTextLayout = { readContentLayout = it },
                                modifier = Modifier
                                    .onGloballyPositioned { coordinates ->
                                        readContentTopInScroll =
                                            coordinates.positionInRoot().y -
                                                readViewportTopInRoot +
                                                readScrollState.value
                                    }
                                    .testTag("text_note_read_content"),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = text.deleteNote,
            body = text.deleteNoteBody,
            confirmText = text.moveToTrash,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteNote(noteId)
                showDeleteDialog = false
                onDeleted()
            },
        )
    }
}

@Composable
private fun FindInNoteBar(
    query: String,
    currentIndex: Int,
    matchCount: Int,
    text: UiText,
    findFocusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClearSearch: () -> Unit,
) {
    val statusText = when {
        query.isBlank() -> ""
        matchCount <= 0 -> text.noMatches
        else -> formatFindMatchStatus(currentIndex, matchCount, text.noMatches)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NOTE_PAPER_SURFACE, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("find_in_note_bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(text.searchInNote) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .weight(1f)
                .focusRequester(findFocusRequester)
                .testTag("find_in_note_input"),
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(52.dp)
                .testTag("find_match_status"),
        )
        TextButton(
            onClick = onPrevious,
            enabled = query.isNotBlank() && matchCount > 0,
            modifier = Modifier
                .width(40.dp)
                .testTag("previous_find_match_button"),
        ) {
            Text("<", maxLines = 1)
        }
        TextButton(
            onClick = onNext,
            enabled = query.isNotBlank() && matchCount > 0,
            modifier = Modifier
                .width(40.dp)
                .testTag("next_find_match_button"),
        ) {
            Text(">", maxLines = 1)
        }
        TextButton(
            onClick = onClearSearch,
            modifier = Modifier
                .width(44.dp)
                .testTag("clear_find_in_note_button"),
        ) {
            Text("x", maxLines = 1)
        }
    }
}

@Composable
private fun TextEditorAccessoryBar(
    text: UiText,
    onInsertCheckbox: () -> Unit,
    onInsertBullet: () -> Unit,
    onHideKeyboard: () -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(NOTE_PAPER_SURFACE, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("text_editor_accessory_bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            TextButton(
                onClick = onInsertCheckbox,
                modifier = Modifier.testTag("quick_insert_checkbox_button"),
            ) {
                Text("[ ] ${text.checkboxItem}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            TextButton(
                onClick = onInsertBullet,
                modifier = Modifier.testTag("quick_insert_bullet_button"),
            ) {
                Text("- ${text.bulletItem}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            TextButton(
                onClick = onHideKeyboard,
                modifier = Modifier.testTag("hide_keyboard_button"),
            ) {
                Text(text.hideKeyboard, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawingEditorScreen(
    noteId: Long,
    folders: List<FolderEntity>,
    text: UiText,
    appLanguage: AppLanguage,
    viewModel: NotepadViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val note by viewModel.observeNote(noteId).collectAsStateWithLifecycle(initialValue = null)
    var title by remember(noteId) { mutableStateOf("") }
    var strokes by remember(noteId) { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var redoStrokes by remember(noteId) { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var selectedTool by remember(noteId) { mutableStateOf(DrawingTool.Pen) }
    var selectedBrushSize by remember(noteId) { mutableStateOf(DrawingBrushSize.Medium) }
    var selectedColor by remember(noteId) { mutableStateOf(DrawingColorOption.Black) }
    var canvasSize by remember(noteId) { mutableStateOf(IntSize.Zero) }
    var isFullscreenDrawing by remember(noteId) { mutableStateOf(false) }
    var loadedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var hasHandledInitialDrawingFocus by remember(noteId) { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingPngBytes by remember { mutableStateOf<ByteArray?>(null) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val titleFocusRequester = remember(noteId) { FocusRequester() }
    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val pngBytes = pendingPngBytes
        pendingPngBytes = null
        if (uri == null || pngBytes == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    writeBytesToUri(context, uri, pngBytes)
                }
                Toast.makeText(context, text.pngExportComplete, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, text.pngExportFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(note?.id) {
        val loaded = note ?: return@LaunchedEffect
        title = loaded.title
        strokes = DrawingJson.decode(loaded.drawingData)
        redoStrokes = emptyList()
        loadedNoteId = loaded.id
    }

    LaunchedEffect(loadedNoteId) {
        if (loadedNoteId == noteId && !hasHandledInitialDrawingFocus) {
            hasHandledInitialDrawingFocus = true
            if (title.isBlank() && strokes.isEmpty()) {
                isFullscreenDrawing = true
            }
        }
    }

    LaunchedEffect(noteId, loadedNoteId, title) {
        if (loadedNoteId == noteId) {
            delay(500)
            viewModel.saveDrawingNote(noteId, title, DrawingJson.encode(strokes))
        }
    }

    fun saveAndBack() {
        viewModel.saveDrawingNote(noteId, title, DrawingJson.encode(strokes))
        onBack()
    }

    fun saveCurrentDrawingNoteThen(onSaved: (NoteEntity, List<DrawingStroke>) -> Unit) {
        val currentNote = note ?: return
        val drawingData = DrawingJson.encode(strokes)
        scope.launch {
            val savedAt = viewModel.saveDrawingNoteNow(noteId, title, drawingData) ?: currentNote.updatedAt
            val savedNote = currentNote.copy(
                title = title,
                textContent = null,
                drawingData = drawingData,
                updatedAt = savedAt,
            )
            onSaved(savedNote, strokes)
        }
    }

    fun renderCurrentDrawingPng(currentStrokes: List<DrawingStroke>): ByteArray {
        val exportSize = drawingExportCanvasSizePx(
            strokes = currentStrokes,
            measuredCanvasSize = canvasSize,
            fallbackWidthPx = DEFAULT_DRAWING_EXPORT_WIDTH,
            fallbackHeightPx = DEFAULT_DRAWING_EXPORT_HEIGHT,
            maxDimensionPx = MAX_DRAWING_EXPORT_DIMENSION,
        )
        return renderDrawingPng(currentStrokes, exportSize.width, exportSize.height)
    }

    fun shareCurrentDrawingPng() {
        saveCurrentDrawingNoteThen { savedNote, savedStrokes ->
            scope.launch {
                try {
                    val pngBytes = withContext(Dispatchers.Default) {
                        renderCurrentDrawingPng(savedStrokes)
                    }
                    val uri = withContext(Dispatchers.IO) {
                        createCachedPngUri(
                            context = context,
                            fileName = defaultPngExportFileName(savedNote, text),
                            pngBytes = pngBytes,
                        )
                    }
                    sharePng(
                        context = context,
                        uri = uri,
                        subject = noteTitle(savedNote, text),
                        body = buildDrawingNoteShareText(
                            savedNote,
                            folderDisplayNameById(savedNote.folderId, folders, text),
                            text,
                            appLanguage,
                        ),
                        chooserTitle = text.shareChooserTitle,
                    )
                } catch (_: Exception) {
                    Toast.makeText(context, text.pngShareFailed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exportCurrentDrawingPng() {
        saveCurrentDrawingNoteThen { savedNote, savedStrokes ->
            scope.launch {
                try {
                    pendingPngBytes = withContext(Dispatchers.Default) {
                        renderCurrentDrawingPng(savedStrokes)
                    }
                    exportPngLauncher.launch(defaultPngExportFileName(savedNote, text))
                } catch (_: Exception) {
                    pendingPngBytes = null
                    Toast.makeText(context, text.pngExportFailed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun undoStroke() {
        val lastStroke = strokes.lastOrNull() ?: return
        val updatedStrokes = strokes.dropLast(1)
        strokes = updatedStrokes
        redoStrokes = redoStrokes + lastStroke
        viewModel.saveDrawingNote(noteId, title, DrawingJson.encode(updatedStrokes))
    }

    fun redoStroke() {
        val stroke = redoStrokes.lastOrNull() ?: return
        val updatedStrokes = strokes + stroke
        strokes = updatedStrokes
        redoStrokes = redoStrokes.dropLast(1)
        viewModel.saveDrawingNote(noteId, title, DrawingJson.encode(updatedStrokes))
    }

    fun clearDrawingNow() {
        strokes = emptyList()
        redoStrokes = emptyList()
        viewModel.saveDrawingNote(noteId, title, "[]")
    }

    fun finishStroke(updatedStrokes: List<DrawingStroke>) {
        strokes = updatedStrokes
        redoStrokes = emptyList()
        viewModel.saveDrawingNote(noteId, title, DrawingJson.encode(updatedStrokes))
    }

    fun activeStrokeTool(): String {
        return if (selectedTool == DrawingTool.Eraser) DrawingTools.ERASER else DrawingTools.PEN
    }

    fun activeStrokeWidth(): Float {
        return if (selectedTool == DrawingTool.Eraser) {
            with(density) { selectedBrushSize.eraserSizeDp.dp.toPx() }
        } else {
            selectedBrushSize.penWidthPx
        }
    }

    fun exitFullscreenDrawing() {
        isFullscreenDrawing = false
        viewModel.saveDrawingNote(noteId, title, DrawingJson.encode(strokes))
    }

    BackHandler {
        if (isFullscreenDrawing) {
            exitFullscreenDrawing()
        } else {
            saveAndBack()
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreenDrawing) {
                TopAppBar(
                    title = { Text(text.drawingNote) },
                    navigationIcon = {
                        TextButton(
                            onClick = ::saveAndBack,
                            modifier = Modifier.testTag("back_button"),
                        ) {
                            Text(text.back)
                        }
                    },
                    actions = {
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text(text.moveToTrash)
                        }
                    },
                )
            }
        },
    ) { padding ->
        val currentNote = note
        if (currentNote == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(text.noteNotFound)
            }
        } else if (isFullscreenDrawing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(NOTE_PAPER_BACKGROUND)
                    .testTag("fullscreen_drawing_mode"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = ::exitFullscreenDrawing,
                        modifier = Modifier.testTag("exit_fullscreen_drawing_button"),
                    ) {
                        Text(text.exitFocusWriting)
                    }
                    Text(
                        text = title.ifBlank { text.untitledDrawing },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                DrawingCanvas(
                    strokes = strokes,
                    onStrokesChange = { updatedStrokes -> strokes = updatedStrokes },
                    onStrokeFinished = ::finishStroke,
                    brushColorArgb = selectedColor.colorArgb,
                    brushWidthPx = activeStrokeWidth(),
                    strokeTool = activeStrokeTool(),
                    onCanvasSizeChange = { canvasSize = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("fullscreen_drawing_canvas"),
                )
                DrawingToolBar(
                    strokes = strokes,
                    redoStrokes = redoStrokes,
                    selectedTool = selectedTool,
                    selectedBrushSize = selectedBrushSize,
                    selectedColor = selectedColor,
                    text = text,
                    onUndo = ::undoStroke,
                    onRedo = ::redoStroke,
                    onClear = { showClearDialog = true },
                    onSharePng = ::shareCurrentDrawingPng,
                    onExportPng = ::exportCurrentDrawingPng,
                    onToolChange = { selectedTool = it },
                    onBrushSizeChange = { selectedBrushSize = it },
                    onColorChange = { selectedColor = it },
                    showFileActions = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                        .padding(12.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text.title) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .testTag("drawing_note_title"),
                )
                NoteFolderSelector(
                    folders = folders,
                    text = text,
                    currentFolderId = currentNote.folderId,
                    onMove = { folderId -> viewModel.moveNote(noteId, folderId) },
                )
                ReminderControls(
                    note = currentNote,
                    text = text,
                    appLanguage = appLanguage,
                    onSetReminder = { reminderAt -> viewModel.setNoteReminder(noteId, reminderAt) },
                    onClearReminder = { viewModel.setNoteReminder(noteId, null) },
                )
                DrawingCanvasWithFullscreenEntry(
                    strokes = strokes,
                    text = text,
                    onStrokesChange = { updatedStrokes -> strokes = updatedStrokes },
                    onStrokeFinished = ::finishStroke,
                    brushColorArgb = selectedColor.colorArgb,
                    brushWidthPx = activeStrokeWidth(),
                    strokeTool = activeStrokeTool(),
                    onCanvasSizeChange = { canvasSize = it },
                    onFullscreen = {
                        viewModel.saveDrawingNote(noteId, title, DrawingJson.encode(strokes))
                        isFullscreenDrawing = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                DrawingToolBar(
                    strokes = strokes,
                    redoStrokes = redoStrokes,
                    selectedTool = selectedTool,
                    selectedBrushSize = selectedBrushSize,
                    selectedColor = selectedColor,
                    text = text,
                    onUndo = ::undoStroke,
                    onRedo = ::redoStroke,
                    onClear = { showClearDialog = true },
                    onSharePng = ::shareCurrentDrawingPng,
                    onExportPng = ::exportCurrentDrawingPng,
                    onToolChange = { selectedTool = it },
                    onBrushSizeChange = { selectedBrushSize = it },
                    onColorChange = { selectedColor = it },
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = text.deleteNote,
            body = text.deleteNoteBody,
            confirmText = text.moveToTrash,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteNote(noteId)
                showDeleteDialog = false
                onDeleted()
            },
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = text.clearDrawing,
            body = text.clearDrawingBody,
            confirmText = text.clear,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { showClearDialog = false },
            onConfirm = {
                clearDrawingNow()
                showClearDialog = false
            },
        )
    }
}

@Composable
private fun DrawingToolBar(
    strokes: List<DrawingStroke>,
    redoStrokes: List<DrawingStroke>,
    selectedTool: DrawingTool,
    selectedBrushSize: DrawingBrushSize,
    selectedColor: DrawingColorOption,
    text: UiText,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSharePng: () -> Unit,
    onExportPng: () -> Unit,
    onToolChange: (DrawingTool) -> Unit,
    onBrushSizeChange: (DrawingBrushSize) -> Unit,
    onColorChange: (DrawingColorOption) -> Unit,
    modifier: Modifier = Modifier,
    showFileActions: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            item {
                Button(
                    onClick = onUndo,
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.testTag("drawing_undo_button"),
                ) {
                    Text(text.undo)
                }
            }
            item {
                Button(
                    onClick = onRedo,
                    enabled = redoStrokes.isNotEmpty(),
                    modifier = Modifier.testTag("drawing_redo_button"),
                ) {
                    Text(text.redo)
                }
            }
            item {
                Button(
                    onClick = onClear,
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.testTag("drawing_clear_button"),
                ) {
                    Text(text.clear)
                }
            }
            if (showFileActions) {
                item {
                    Button(
                        onClick = onSharePng,
                        modifier = Modifier.testTag("share_drawing_png_button"),
                    ) {
                        Text(text.share)
                    }
                }
                item {
                    Button(
                        onClick = onExportPng,
                        modifier = Modifier.testTag("export_drawing_png_button"),
                    ) {
                        Text(text.export)
                    }
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            DrawingTool.entries.forEach { tool ->
                item {
                    FilterChip(
                        selected = selectedTool == tool,
                        onClick = { onToolChange(tool) },
                        label = { Text(tool.label(text)) },
                        modifier = Modifier.testTag("drawing_tool_${tool.name}"),
                    )
                }
            }
            DrawingBrushSize.entries.forEach { size ->
                item {
                    FilterChip(
                        selected = selectedBrushSize == size,
                        onClick = { onBrushSizeChange(size) },
                        label = { Text(size.label(text, selectedTool)) },
                        modifier = Modifier.testTag("drawing_brush_${size.name}"),
                    )
                }
            }
        }

        if (selectedTool == DrawingTool.Pen) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                DrawingColorOption.entries.forEach { color ->
                    item {
                        FilterChip(
                            selected = selectedColor == color,
                            onClick = { onColorChange(color) },
                            label = { Text(color.label(text)) },
                            modifier = Modifier.testTag("drawing_color_${color.name}"),
                        )
                    }
                }
            }
        } else {
            Text(
                text = text.eraserSizeHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("drawing_eraser_hint"),
            )
        }
    }
}

@Composable
private fun DrawingCanvasWithFullscreenEntry(
    strokes: List<DrawingStroke>,
    text: UiText,
    onStrokesChange: (List<DrawingStroke>) -> Unit,
    onStrokeFinished: (List<DrawingStroke>) -> Unit,
    brushColorArgb: Int,
    brushWidthPx: Float,
    strokeTool: String,
    onCanvasSizeChange: (IntSize) -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        DrawingCanvas(
            strokes = strokes,
            onStrokesChange = onStrokesChange,
            onStrokeFinished = onStrokeFinished,
            brushColorArgb = brushColorArgb,
            brushWidthPx = brushWidthPx,
            strokeTool = strokeTool,
            onCanvasSizeChange = onCanvasSizeChange,
            modifier = Modifier.fillMaxSize(),
        )
        Button(
            onClick = onFullscreen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .testTag("drawing_fullscreen_button"),
        ) {
            Text(text.fullscreenWriting, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DrawingCanvas(
    strokes: List<DrawingStroke>,
    onStrokesChange: (List<DrawingStroke>) -> Unit,
    onStrokeFinished: (List<DrawingStroke>) -> Unit,
    brushColorArgb: Int,
    brushWidthPx: Float,
    strokeTool: String,
    onCanvasSizeChange: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestStrokes by rememberUpdatedState(strokes)
    val latestOnStrokesChange by rememberUpdatedState(onStrokesChange)
    val latestOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val latestBrushColorArgb by rememberUpdatedState(brushColorArgb)
    val latestBrushWidthPx by rememberUpdatedState(brushWidthPx)
    val latestStrokeTool by rememberUpdatedState(strokeTool)
    var measuredCanvasSize by remember { mutableStateOf(IntSize.Zero) }
    var isDrawing by remember { mutableStateOf(false) }
    var drawingStartViewportScale by remember { mutableStateOf(1f) }
    val fittedViewportScale = drawingViewportScale(strokes, measuredCanvasSize)
    val viewportScale = if (isDrawing) drawingStartViewportScale else fittedViewportScale
    val latestViewportScale by rememberUpdatedState(viewportScale)
    var activeEraserPreview by remember { mutableStateOf<DrawingPoint?>(null) }

    Canvas(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .onSizeChanged { size ->
                measuredCanvasSize = size
                onCanvasSizeChange(size)
            }
            .pointerInput(Unit) {
                var baseStrokes = emptyList<DrawingStroke>()
                var activePoints = emptyList<DrawingPoint>()
                var activeStroke = DrawingStroke(emptyList())

                fun Offset.toLogicalPoint(): DrawingPoint {
                    val scale = latestViewportScale.coerceAtLeast(0.001f)
                    return DrawingPoint(x / scale, y / scale)
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        drawingStartViewportScale = latestViewportScale
                        isDrawing = true
                        val point = offset.toLogicalPoint()
                        baseStrokes = latestStrokes
                        activePoints = listOf(point)
                        activeEraserPreview = point.takeIf { latestStrokeTool == DrawingTools.ERASER }
                        activeStroke = DrawingStroke(
                            points = activePoints,
                            colorArgb = latestBrushColorArgb,
                            widthPx = latestBrushWidthPx,
                            tool = latestStrokeTool,
                        )
                        latestOnStrokesChange(baseStrokes + activeStroke)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val point = change.position.toLogicalPoint()
                        activePoints = activePoints + point
                        activeEraserPreview = point.takeIf { latestStrokeTool == DrawingTools.ERASER }
                        activeStroke = activeStroke.copy(points = activePoints)
                        latestOnStrokesChange(baseStrokes + activeStroke)
                    },
                    onDragEnd = {
                        latestOnStrokeFinished(baseStrokes + activeStroke)
                        activeEraserPreview = null
                        isDrawing = false
                    },
                    onDragCancel = {
                        latestOnStrokeFinished(baseStrokes + activeStroke)
                        activeEraserPreview = null
                        isDrawing = false
                    },
                )
            },
    ) {
        drawDrawingStrokes(strokes, viewportScale)
        activeEraserPreview?.let { center ->
            if (strokeTool == DrawingTools.ERASER) {
                withTransform({ scale(viewportScale, viewportScale, Offset.Zero) }) {
                    drawEraserPreview(center.toOffset(), brushWidthPx)
                }
            }
        }
    }
}

fun drawingRequiredCanvasWidthPx(
    strokes: List<DrawingStroke>,
    minimumWidthPx: Float,
    paddingPx: Float = 48f,
): Float {
    val maxStrokeRight = strokes
        .filter { it.tool != DrawingTools.ERASER }
        .maxOfOrNull { stroke ->
            val maxPointX = stroke.points.maxOfOrNull { point -> point.x } ?: 0f
            maxPointX + stroke.widthPx / 2f
        } ?: 0f
    return max(minimumWidthPx, maxStrokeRight + paddingPx)
}

fun drawingRequiredCanvasHeightPx(
    strokes: List<DrawingStroke>,
    minimumHeightPx: Float,
    paddingPx: Float = 48f,
): Float {
    val maxStrokeBottom = strokes
        .filter { it.tool != DrawingTools.ERASER }
        .maxOfOrNull { stroke ->
            val maxPointY = stroke.points.maxOfOrNull { point -> point.y } ?: 0f
            maxPointY + stroke.widthPx / 2f
        } ?: 0f
    return max(minimumHeightPx, maxStrokeBottom + paddingPx)
}

fun drawingViewportScale(
    strokes: List<DrawingStroke>,
    measuredCanvasSize: IntSize,
    paddingPx: Float = 0f,
): Float {
    if (measuredCanvasSize.width <= 0 || measuredCanvasSize.height <= 0) return 1f
    val requiredWidth = drawingRequiredCanvasWidthPx(strokes, measuredCanvasSize.width.toFloat(), paddingPx)
    val requiredHeight = drawingRequiredCanvasHeightPx(strokes, measuredCanvasSize.height.toFloat(), paddingPx)
    return min(
        1f,
        min(
            measuredCanvasSize.width / requiredWidth,
            measuredCanvasSize.height / requiredHeight,
        ),
    ).coerceAtLeast(0.001f)
}

fun drawingExportCanvasSizePx(
    strokes: List<DrawingStroke>,
    measuredCanvasSize: IntSize,
    fallbackWidthPx: Int,
    fallbackHeightPx: Int,
    paddingPx: Float = 48f,
    maxDimensionPx: Int = MAX_DRAWING_EXPORT_DIMENSION,
): IntSize {
    val safeMaxDimension = max(1, maxDimensionPx)
    val measuredWidth = measuredCanvasSize.width.takeIf { it > 0 } ?: fallbackWidthPx
    val measuredHeight = measuredCanvasSize.height.takeIf { it > 0 } ?: fallbackHeightPx
    return IntSize(
        width = ceil(drawingRequiredCanvasWidthPx(strokes, measuredWidth.toFloat(), paddingPx))
            .toInt()
            .coerceAtMost(safeMaxDimension),
        height = ceil(drawingRequiredCanvasHeightPx(strokes, measuredHeight.toFloat(), paddingPx))
            .toInt()
            .coerceAtMost(safeMaxDimension),
    )
}

private fun DrawScope.drawDrawingStrokes(
    strokes: List<DrawingStroke>,
    viewportScale: Float = 1f,
) {
    drawRect(Color.White)
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
    withTransform({ scale(viewportScale, viewportScale, Offset.Zero) }) {
        strokes.forEach { stroke ->
            drawDrawingStroke(stroke)
        }
    }
    drawContext.canvas.restore()
}

private fun DrawScope.drawDrawingStroke(stroke: DrawingStroke) {
    val points = stroke.points
    if (points.isEmpty()) return

    val isEraser = stroke.tool == DrawingTools.ERASER
    if (isEraser) {
        drawEraserStroke(stroke)
        return
    }

    if (points.size == 1) {
        drawCircle(
            color = Color(stroke.colorArgb),
            radius = stroke.widthPx / 2f,
            center = Offset(points.first().x, points.first().y),
        )
        return
    }

    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point ->
            lineTo(point.x, point.y)
        }
    }
    drawPath(
        path = path,
        color = Color(stroke.colorArgb),
        style = Stroke(width = stroke.widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawEraserStroke(stroke: DrawingStroke) {
    sampleStrokeCenters(stroke).forEach { center ->
        drawEraserSquare(center, stroke.widthPx, BlendMode.Clear)
    }
}

private fun DrawScope.drawEraserSquare(
    center: Offset,
    sizePx: Float,
    blendMode: BlendMode,
) {
    val half = sizePx / 2f
    drawRect(
        color = Color.Transparent,
        topLeft = Offset(center.x - half, center.y - half),
        size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
        blendMode = blendMode,
    )
}

private fun DrawScope.drawEraserPreview(center: Offset, sizePx: Float) {
    val half = sizePx / 2f
    val topLeft = Offset(center.x - half, center.y - half)
    val previewSize = androidx.compose.ui.geometry.Size(sizePx, sizePx)
    drawRect(
        color = Color(0xFF9E9E9E).copy(alpha = 0.24f),
        topLeft = topLeft,
        size = previewSize,
    )
    drawRect(
        color = Color(0xFF424242).copy(alpha = 0.85f),
        topLeft = topLeft,
        size = previewSize,
        style = Stroke(width = 2f),
    )
}

private fun sampleStrokeCenters(stroke: DrawingStroke): List<Offset> {
    val points = stroke.points
    if (points.isEmpty()) return emptyList()
    if (points.size == 1) return listOf(points.first().toOffset())

    val stepPx = max(stroke.widthPx / 3f, 1f)
    return buildList {
        points.zipWithNext().forEach { (start, end) ->
            val dx = end.x - start.x
            val dy = end.y - start.y
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val steps = max(1, ceil(distance / stepPx).toInt())
            for (step in 0..steps) {
                val fraction = step / steps.toFloat()
                add(
                    Offset(
                        x = start.x + dx * fraction,
                        y = start.y + dy * fraction,
                    ),
                )
            }
        }
    }
}

private fun DrawingPoint.toOffset(): Offset {
    return Offset(x, y)
}

@Composable
private fun ReminderControls(
    note: NoteEntity,
    text: UiText,
    appLanguage: AppLanguage,
    onSetReminder: (Long) -> Unit,
    onClearReminder: () -> Unit,
) {
    val context = LocalContext.current
    var pendingReminderAt by remember { mutableStateOf<Long?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        pendingReminderAt?.let(onSetReminder)
        pendingReminderAt = null
    }

    fun submitReminder(reminderAt: Long) {
        if (reminderAt <= System.currentTimeMillis()) {
            Toast.makeText(context, text.reminderMustBeFuture, Toast.LENGTH_SHORT).show()
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingReminderAt = reminderAt
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onSetReminder(reminderAt)
        }
    }

    fun openDateTimePicker() {
        val calendar = Calendar.getInstance()
        val initialReminderAt = note.reminderAt?.takeIf { it > System.currentTimeMillis() }
        if (initialReminderAt == null) {
            calendar.add(Calendar.HOUR_OF_DAY, 1)
        } else {
            calendar.timeInMillis = initialReminderAt
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val selected = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        submitReminder(selected.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = reminderStatus(note.reminderAt, text, appLanguage),
            style = MaterialTheme.typography.bodySmall,
            color = if (note.reminderAt != null && note.reminderAt <= System.currentTimeMillis()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.testTag("note_reminder_status"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = ::openDateTimePicker,
                modifier = Modifier.testTag("set_reminder_button"),
            ) {
                Text(text.setReminder)
            }
            if (note.reminderAt != null) {
                TextButton(
                    onClick = onClearReminder,
                    modifier = Modifier.testTag("clear_reminder_button"),
                ) {
                    Text(text.clearReminder)
                }
            }
        }
    }
}

@Composable
private fun NoteFolderSelector(
    folders: List<FolderEntity>,
    text: UiText,
    currentFolderId: Long,
    onMove: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentFolderName = folderDisplayNameById(currentFolderId, folders, text)

    Box {
        Button(onClick = { expanded = true }) {
            Text(currentFolderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folderDisplayName(folder, text)) },
                    onClick = {
                        expanded = false
                        onMove(folder.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initialName: String,
    folders: List<FolderEntity>,
    text: UiText,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    val trimmedName = name.trim()
    val validationError = validateFolderName(trimmedName, folders, text, currentFolderId)
    val showValidationError = name.isNotBlank() && validationError != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text(text.folderName) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.testTag("folder_name_input"),
                )
                (error ?: if (showValidationError) validationError else null)?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = validationError
                    if (error == null) onConfirm(trimmedName)
                },
                enabled = validationError == null,
            ) {
                Text(if (currentFolderId == null) text.create else text.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text.cancel)
            }
        },
    )
}

@Composable
private fun MoveNoteDialog(
    folders: List<FolderEntity>,
    text: UiText,
    currentFolderId: Long,
    onDismiss: () -> Unit,
    onMove: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.moveNote) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                folders.forEach { folder ->
                    Button(
                        onClick = { onMove(folder.id) },
                        enabled = folder.id != currentFolderId,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            folderDisplayName(folder, text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text.cancel)
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    cancelText: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
                modifier = Modifier.testTag("confirm_dialog_confirm_button"),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("confirm_dialog_cancel_button"),
            ) {
                Text(cancelText)
            }
        },
    )
}

private fun validateFolderName(
    name: String,
    folders: List<FolderEntity>,
    text: UiText,
    currentFolderId: Long?,
): String? {
    if (name.isBlank()) return text.folderNameRequired
    if (
        name.equals(ALL_NOTES_FILTER_NAME, ignoreCase = true) ||
        name.equals(text.allNotes, ignoreCase = true)
    ) {
        return text.allNotesIsFilter
    }
    if (
        name.equals(DEFAULT_FOLDER_NAME, ignoreCase = true) ||
        name.equals(text.uncategorized, ignoreCase = true)
    ) {
        return text.uncategorizedIsReserved
    }
    if (folders.any { it.id != currentFolderId && folderDisplayName(it, text).equals(name, ignoreCase = true) }) {
        return text.folderAlreadyExists
    }
    return null
}

private fun folderDisplayName(folder: FolderEntity, text: UiText): String {
    return if (folder.id == DEFAULT_FOLDER_ID) text.uncategorized else folder.name
}

private fun folderDisplayNameById(
    folderId: Long,
    folders: List<FolderEntity>,
    text: UiText,
): String {
    val folder = folders.firstOrNull { it.id == folderId }
    return if (folder == null) text.uncategorized else folderDisplayName(folder, text)
}

private fun noteTitle(note: NoteEntity, text: UiText): String {
    return note.title.ifBlank {
        if (note.type == NoteTypes.DRAWING) text.untitledDrawing else text.untitledTextNote
    }
}

private fun notePreview(note: NoteEntity, query: String): String? {
    if (note.type != NoteTypes.TEXT) return null

    val content = note.textContent
        .orEmpty()
        .replace(Regex("\\s+"), " ")
        .trim()
    if (content.isBlank()) return null

    val trimmedQuery = query.trim()
    if (trimmedQuery.isNotBlank()) {
        val matchIndex = content.indexOf(trimmedQuery, ignoreCase = true)
        if (matchIndex >= 0) {
            val start = (matchIndex - NOTE_PREVIEW_CONTEXT_BEFORE).coerceAtLeast(0)
            val end = (matchIndex + trimmedQuery.length + NOTE_PREVIEW_CONTEXT_AFTER).coerceAtMost(content.length)
            val prefix = if (start > 0) "..." else ""
            val suffix = if (end < content.length) "..." else ""
            return "$prefix${content.substring(start, end)}$suffix"
        }
    }

    return content.take(NOTE_PREVIEW_MAX_CHARS)
}

fun highlightedText(
    value: String,
    query: String,
    highlightColor: Color,
): AnnotatedString {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return AnnotatedString(value)

    return buildAnnotatedString {
        append(value)
        value.highlightRanges(trimmedQuery).forEach { range ->
            addStyle(
                SpanStyle(background = highlightColor),
                start = range.first,
                end = range.last + 1,
            )
        }
    }
}

fun findHighlightedText(
    value: String,
    query: String,
    activeMatchIndex: Int,
    matchColor: Color,
    activeMatchColor: Color,
): AnnotatedString {
    val matches = findInNoteMatches(value, query)
    if (matches.isEmpty()) return AnnotatedString(value)

    val activeIndex = activeMatchIndex.normalizeFindMatchIndex(matches.size)
    return buildAnnotatedString {
        append(value)
        matches.forEachIndexed { index, range ->
            addStyle(
                SpanStyle(
                    background = if (index == activeIndex) activeMatchColor else matchColor,
                    fontWeight = if (index == activeIndex) FontWeight.Bold else null,
                ),
                start = range.first,
                end = range.last + 1,
            )
        }
    }
}

fun String.highlightRanges(query: String): List<IntRange> {
    if (query.isBlank()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var searchStart = 0
    while (searchStart <= length) {
        val index = indexOf(query, startIndex = searchStart, ignoreCase = true)
        if (index < 0) break
        ranges += index until index + query.length
        searchStart = index + query.length
    }
    return ranges
}

fun findInNoteMatches(content: String, query: String): List<IntRange> {
    return content.highlightRanges(query.trim())
}

fun nextFindMatchIndex(currentIndex: Int, matchCount: Int): Int {
    return (currentIndex + 1).normalizeFindMatchIndex(matchCount)
}

fun previousFindMatchIndex(currentIndex: Int, matchCount: Int): Int {
    return (currentIndex - 1).normalizeFindMatchIndex(matchCount)
}

fun formatFindMatchStatus(currentIndex: Int, matchCount: Int, noMatchesLabel: String): String {
    if (matchCount <= 0) return noMatchesLabel
    return "${currentIndex.normalizeFindMatchIndex(matchCount) + 1}/$matchCount"
}

fun Int.normalizeFindMatchIndex(matchCount: Int): Int {
    if (matchCount <= 0) return -1
    return ((this % matchCount) + matchCount) % matchCount
}

fun findMatchScrollTarget(
    currentScroll: Int,
    viewportHeight: Int,
    matchTop: Float,
    matchBottom: Float,
    maxScroll: Int,
    viewportPaddingPx: Float = 96f,
): Int? {
    if (viewportHeight <= 0 || maxScroll <= 0) return null
    val effectivePaddingPx = viewportPaddingPx.coerceAtMost(viewportHeight / 3f)
    val visibleTop = currentScroll + effectivePaddingPx
    val visibleBottom = currentScroll + viewportHeight - effectivePaddingPx
    val target = when {
        matchTop < visibleTop -> matchTop - effectivePaddingPx
        matchBottom > visibleBottom -> matchBottom - viewportHeight + effectivePaddingPx
        else -> return null
    }
    return target.roundToInt().coerceIn(0, maxScroll)
}

private suspend fun ScrollState.scrollMatchIntoView(
    textLayoutResult: TextLayoutResult?,
    matchRange: IntRange?,
    viewportHeight: Int,
    contentTopPx: Float,
) {
    val layout = textLayoutResult ?: return
    val range = matchRange ?: return
    val textLength = layout.layoutInput.text.text.length
    if (textLength <= 0) return
    val startOffset = range.first.coerceIn(0, textLength - 1)
    val endOffset = range.last.coerceIn(startOffset, textLength - 1)
    val startBox = layout.getBoundingBox(startOffset)
    val endBox = layout.getBoundingBox(endOffset)
    val target = findMatchScrollTarget(
        currentScroll = value,
        viewportHeight = viewportHeight,
        matchTop = contentTopPx + startBox.top,
        matchBottom = contentTopPx + endBox.bottom,
        maxScroll = maxValue,
    ) ?: return
    scrollTo(target)
}

fun cursorScrollTarget(
    currentScroll: Int,
    viewportHeight: Int,
    cursorTop: Float,
    cursorBottom: Float,
    maxScroll: Int,
    viewportTopPaddingPx: Float = 24f,
    viewportBottomPaddingPx: Float = 56f,
): Int? {
    if (viewportHeight <= 0 || maxScroll <= 0) return null
    val effectiveTopPaddingPx = viewportTopPaddingPx.coerceAtMost(viewportHeight / 3f)
    val effectiveBottomPaddingPx = viewportBottomPaddingPx.coerceAtMost(viewportHeight / 3f)
    val visibleTop = currentScroll + effectiveTopPaddingPx
    val visibleBottom = currentScroll + viewportHeight - effectiveBottomPaddingPx
    val target = when {
        cursorBottom > visibleBottom -> cursorBottom - viewportHeight + effectiveBottomPaddingPx
        cursorTop < visibleTop -> cursorTop - effectiveTopPaddingPx
        else -> return null
    }
    return target.roundToInt().coerceIn(0, maxScroll)
}

private suspend fun ScrollState.scrollCursorIntoView(
    textLayoutResult: TextLayoutResult?,
    cursorOffset: Int,
    viewportHeight: Int,
    contentTopPx: Float,
    viewportBottomPaddingPx: Float,
) {
    val layout = textLayoutResult ?: return
    val textLength = layout.layoutInput.text.text.length
    val safeOffset = cursorOffset.coerceIn(0, textLength)
    val cursorRect = layout.getCursorRect(safeOffset)
    val target = cursorScrollTarget(
        currentScroll = value,
        viewportHeight = viewportHeight,
        cursorTop = contentTopPx + cursorRect.top,
        cursorBottom = contentTopPx + cursorRect.bottom,
        maxScroll = maxValue,
        viewportBottomPaddingPx = viewportBottomPaddingPx,
    ) ?: return
    scrollTo(target)
}

private class FindInNoteVisualTransformation(
    private val query: String,
    private val activeMatchIndex: Int,
    private val matchColor: Color,
    private val activeMatchColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val matches = findInNoteMatches(text.text, query)
        if (matches.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val activeIndex = activeMatchIndex.normalizeFindMatchIndex(matches.size)
        val annotated = buildAnnotatedString {
            append(text.text)
            matches.forEachIndexed { index, range ->
                addStyle(
                    SpanStyle(
                        background = if (index == activeIndex) activeMatchColor else matchColor,
                        fontWeight = if (index == activeIndex) FontWeight.Bold else null,
                    ),
                    start = range.first,
                    end = range.last + 1,
                )
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

private fun noteMetadata(
    note: NoteEntity,
    folderName: String,
    text: UiText,
    appLanguage: AppLanguage,
): String {
    val timestamps = "${text.updated} ${formatTime(note.updatedAt, appLanguage)} • " +
        "${text.created} ${formatTime(note.createdAt, appLanguage)}"
    val pinned = if (note.isPinned && !note.isDeleted) " • ${text.pinned}" else ""
    val reminder = note.reminderAt?.let { " • ${reminderStatus(it, text, appLanguage)}" }.orEmpty()
    return "$folderName • $timestamps$pinned$reminder"
}

private fun NoteSortOption.label(text: UiText): String {
    return when (this) {
        NoteSortOption.UpdatedAt -> text.sortUpdated
        NoteSortOption.CreatedAt -> text.sortCreated
        NoteSortOption.Title -> text.sortTitle
    }
}

private fun NoteTypeFilter.label(text: UiText): String {
    return when (this) {
        NoteTypeFilter.All -> text.allTypes
        NoteTypeFilter.Text -> text.textNotes
        NoteTypeFilter.Drawing -> text.drawingNotes
    }
}

private fun NoteQuickFilter.label(text: UiText): String {
    return when (this) {
        NoteQuickFilter.All -> text.all
        NoteQuickFilter.Text -> text.textNotes
        NoteQuickFilter.Drawing -> text.drawingNotes
        NoteQuickFilter.HasReminder -> text.hasReminder
        NoteQuickFilter.Pinned -> text.pinned
    }
}

private fun ReminderFilter.label(text: UiText): String {
    return when (this) {
        ReminderFilter.All -> text.allReminders
        ReminderFilter.WithReminder -> text.withReminder
        ReminderFilter.Overdue -> text.overdueReminders
        ReminderFilter.Upcoming -> text.upcomingReminders
    }
}

private fun EditorFontSize.label(text: UiText): String {
    return when (this) {
        EditorFontSize.Small -> text.fontSmall
        EditorFontSize.Medium -> text.fontMedium
        EditorFontSize.Large -> text.fontLarge
    }
}

private fun DrawingTool.label(text: UiText): String {
    return when (this) {
        DrawingTool.Pen -> text.pen
        DrawingTool.Eraser -> text.eraser
    }
}

private fun DrawingBrushSize.label(text: UiText, tool: DrawingTool): String {
    return if (tool == DrawingTool.Eraser) {
        when (this) {
            DrawingBrushSize.Thin -> text.smallSize
            DrawingBrushSize.Medium -> text.medium
            DrawingBrushSize.Thick -> text.largeSize
        }
    } else {
        when (this) {
            DrawingBrushSize.Thin -> text.thin
            DrawingBrushSize.Medium -> text.medium
            DrawingBrushSize.Thick -> text.thick
        }
    }
}

private fun DrawingColorOption.label(text: UiText): String {
    return when (this) {
        DrawingColorOption.Black -> text.black
        DrawingColorOption.Red -> text.red
        DrawingColorOption.Blue -> text.blue
        DrawingColorOption.Green -> text.green
    }
}

private fun SaveStatus.label(text: UiText): String {
    return when (this) {
        SaveStatus.Saving -> text.saving
        SaveStatus.Saved -> text.saved
    }
}

private fun reminderStatus(
    reminderAt: Long?,
    text: UiText,
    appLanguage: AppLanguage,
): String {
    return if (reminderAt == null) {
        "${text.reminder}: ${text.noReminder}"
    } else {
        val status = if (reminderAt <= System.currentTimeMillis()) {
            text.reminderOverdue
        } else {
            text.reminderUpcoming
        }
        "${text.reminder}: ${formatTime(reminderAt, appLanguage)} ($status)"
    }
}

private fun formatTime(timestamp: Long, language: AppLanguage): String {
    val locale = when (language) {
        AppLanguage.English -> Locale.ENGLISH
        AppLanguage.TraditionalChinese -> Locale.TRADITIONAL_CHINESE
    }
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        .format(Date(timestamp))
}

private fun buildTextNoteDocumentText(
    note: NoteEntity,
    folderName: String,
    text: UiText,
    appLanguage: AppLanguage,
): String {
    return buildString {
        appendLine("${text.title}: ${noteTitle(note, text)}")
        appendLine("${text.folder}: $folderName")
        note.reminderAt?.let { appendLine(reminderStatus(it, text, appLanguage)) }
        appendLine("${text.lastUpdated}: ${formatTime(note.updatedAt, appLanguage)}")
        appendLine()
        appendLine(text.content)
        appendLine(note.textContent.orEmpty())
    }.trimEnd()
}

private fun buildDrawingNoteShareText(
    note: NoteEntity,
    folderName: String,
    text: UiText,
    appLanguage: AppLanguage,
): String {
    return buildString {
        appendLine("${text.title}: ${noteTitle(note, text)}")
        appendLine("${text.folder}: $folderName")
        note.reminderAt?.let { appendLine(reminderStatus(it, text, appLanguage)) }
        appendLine("${text.lastUpdated}: ${formatTime(note.updatedAt, appLanguage)}")
        appendLine()
        appendLine(text.drawingNoteDataNotice)
    }.trimEnd()
}

private fun defaultTextExportFileName(note: NoteEntity, text: UiText): String {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val title = safeFileNameBase(noteTitle(note, text))
    return "$title $date.txt"
}

private fun defaultPngExportFileName(note: NoteEntity, text: UiText): String {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val title = safeFileNameBase(noteTitle(note, text))
    return "$title $date.png"
}

private fun safeFileNameBase(title: String): String {
    return title
        .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)
        .trim()
        .ifBlank { "Note" }
}

private fun sharePlainText(
    context: Context,
    subject: String,
    body: String,
    chooserTitle: String,
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
}

private fun sharePng(
    context: Context,
    uri: Uri,
    subject: String,
    body: String,
    chooserTitle: String,
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, subject, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(chooser)
}

private fun createCachedPngUri(
    context: Context,
    fileName: String,
    pngBytes: ByteArray,
): Uri {
    val directory = File(context.cacheDir, "shared-drawings").apply {
        mkdirs()
    }
    val file = File(directory, fileName)
    file.outputStream().use { outputStream ->
        outputStream.write(pngBytes)
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

private fun writeTextToUri(context: Context, uri: Uri, text: String) {
    val outputStream = context.contentResolver.openOutputStream(uri)
        ?: error("Unable to open backup output stream.")
    outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(text)
    }
}

private fun writeBytesToUri(context: Context, uri: Uri, bytes: ByteArray) {
    val outputStream = context.contentResolver.openOutputStream(uri)
        ?: error("Unable to open output stream.")
    outputStream.use { stream ->
        stream.write(bytes)
    }
}

private fun readTextFromUri(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: error("Unable to open backup input stream.")
    return inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
        reader.readText()
    }
}
