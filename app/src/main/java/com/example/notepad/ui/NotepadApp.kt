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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
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
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
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

private const val BACKUP_FILE_NAME = "local-notepad-backup.json"
private const val DEFAULT_DRAWING_EXPORT_WIDTH = 1080
private const val DEFAULT_DRAWING_EXPORT_HEIGHT = 1440

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
    appLanguage: AppLanguage,
    editorFontSize: EditorFontSize,
    incomingTextShare: IncomingTextShare?,
    onIncomingTextShareHandled: (Long) -> Unit,
    viewModel: NotepadViewModel,
) {
    var screen: AppScreen by remember { mutableStateOf(AppScreen.Main) }
    val text = remember(appLanguage) { uiTextFor(appLanguage) }

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
            appLanguage = appLanguage,
            text = text,
            onSelectFolder = viewModel::selectFolder,
            onSearchQueryChange = viewModel::setSearchQuery,
            onListModeChange = viewModel::setListMode,
            onSortOptionChange = viewModel::setSortOption,
            onTypeFilterChange = viewModel::setTypeFilter,
            onReminderFilterChange = viewModel::setReminderFilter,
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
            onOpenNote = { note ->
                screen = if (note.type == NoteTypes.DRAWING) {
                    AppScreen.DrawingEditor(note.id)
                } else {
                    AppScreen.TextEditor(note.id)
                }
            },
            onMoveNote = viewModel::moveNote,
            onDeleteNote = viewModel::deleteNote,
            onRestoreNote = viewModel::restoreNote,
            onPermanentlyDeleteNote = viewModel::permanentlyDeleteNote,
            onTogglePinned = { note -> viewModel.setNotePinned(note.id, !note.isPinned) },
        )

        AppScreen.Settings -> SettingsScreen(
            text = text,
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
    appLanguage: AppLanguage,
    text: UiText,
    onSelectFolder: (Long?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onListModeChange: (NoteListMode) -> Unit,
    onSortOptionChange: (NoteSortOption) -> Unit,
    onTypeFilterChange: (NoteTypeFilter) -> Unit,
    onReminderFilterChange: (ReminderFilter) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onCreateTextNote: () -> Unit,
    onCreateDrawingNote: () -> Unit,
    onOpenNote: (NoteEntity) -> Unit,
    onMoveNote: (Long, Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onRestoreNote: (Long) -> Unit,
    onPermanentlyDeleteNote: (Long) -> Unit,
    onTogglePinned: (NoteEntity) -> Unit,
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var noteToMove by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToPermanentlyDelete by remember { mutableStateOf<NoteEntity?>(null) }
    val selectedFolder = folders.firstOrNull { it.id == selectedFolderId }
    val isTrash = listMode == NoteListMode.Trash

    Scaffold(
        topBar = {
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
        },
        floatingActionButton = {
            if (!isTrash) {
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
                typeFilter = typeFilter,
                reminderFilter = reminderFilter,
                text = text,
                onSortOptionChange = onSortOptionChange,
                onTypeFilterChange = onTypeFilterChange,
                onReminderFilterChange = onReminderFilterChange,
            )

            HorizontalDivider()

            NoteList(
                notes = notes,
                folders = folders,
                text = text,
                searchQuery = searchQuery,
                listMode = listMode,
                appLanguage = appLanguage,
                onOpenNote = onOpenNote,
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
            confirmText = text.delete,
            cancelText = text.cancel,
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
            onDismiss = { noteToPermanentlyDelete = null },
            onConfirm = {
                onPermanentlyDeleteNote(note.id)
                noteToPermanentlyDelete = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    text: UiText,
    editorFontSize: EditorFontSize,
    viewModel: NotepadViewModel,
    onEditorFontSizeChange: (EditorFontSize) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val backupJson = pendingBackupJson
        pendingBackupJson = null
        if (uri == null || backupJson == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    writeTextToUri(context, uri, backupJson)
                }
                Toast.makeText(context, text.backupComplete, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, text.backupFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                val backupJson = withContext(Dispatchers.IO) {
                    readTextFromUri(context, uri)
                }
                viewModel.importBackupJson(backupJson)
                Toast.makeText(context, text.restoreComplete, Toast.LENGTH_SHORT).show()
                onBack()
            } catch (_: Exception) {
                Toast.makeText(context, text.restoreFailed, Toast.LENGTH_SHORT).show()
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_button"),
            ) {
                Text(text.backupToGoogleDrive)
            }
            Button(
                onClick = {
                    restoreBackupLauncher.launch(
                        arrayOf("application/json", "text/plain", "*/*"),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restore_button"),
            ) {
                Text(text.restoreFromBackup)
            }
        }
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
    typeFilter: NoteTypeFilter,
    reminderFilter: ReminderFilter,
    text: UiText,
    onSortOptionChange: (NoteSortOption) -> Unit,
    onTypeFilterChange: (NoteTypeFilter) -> Unit,
    onReminderFilterChange: (ReminderFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortSelector(
                sortOption = sortOption,
                text = text,
                onSortOptionChange = onSortOptionChange,
                modifier = Modifier.weight(1f),
            )
            TypeFilterSelector(
                typeFilter = typeFilter,
                text = text,
                onTypeFilterChange = onTypeFilterChange,
                modifier = Modifier.weight(1f),
            )
        }
        ReminderFilterSelector(
            reminderFilter = reminderFilter,
            text = text,
            onReminderFilterChange = onReminderFilterChange,
        )
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
    listMode: NoteListMode,
    appLanguage: AppLanguage,
    onOpenNote: (NoteEntity) -> Unit,
    onMoveNote: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onRestoreNote: (NoteEntity) -> Unit,
    onPermanentlyDeleteNote: (NoteEntity) -> Unit,
    onTogglePinned: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notes.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    searchQuery.isNotBlank() -> text.noSearchResults
                    listMode == NoteListMode.Trash -> text.noDeletedNotes
                    else -> text.noNotes
                },
            )
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
                appLanguage = appLanguage,
                onOpen = { onOpenNote(note) },
                onMove = { onMoveNote(note) },
                onDelete = { onDeleteNote(note) },
                onRestore = { onRestoreNote(note) },
                onPermanentlyDelete = { onPermanentlyDeleteNote(note) },
                onTogglePinned = { onTogglePinned(note) },
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: NoteEntity,
    folderName: String,
    text: UiText,
    appLanguage: AppLanguage,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentlyDelete: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    Card(
        onClick = { if (!note.isDeleted) onOpen() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned && !note.isDeleted) {
                    Text(
                        text = "★ ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    text = noteTitle(note, text),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (note.type == NoteTypes.DRAWING) text.drawing else text.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
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
                        Text(text.delete)
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
    var content by remember(noteId) { mutableStateOf("") }
    var loadedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var saveStatus by remember(noteId) { mutableStateOf(SaveStatus.Saved) }
    var lastSavedAt by remember(noteId) { mutableStateOf<Long?>(null) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val titleFocusRequester = remember(noteId) { FocusRequester() }
    val contentFocusRequester = remember(noteId) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
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
        content = loaded.textContent.orEmpty()
        loadedNoteId = loaded.id
        lastSavedAt = loaded.updatedAt
        saveStatus = SaveStatus.Saved
    }

    LaunchedEffect(note?.updatedAt) {
        val loaded = note ?: return@LaunchedEffect
        if (loaded.id == noteId) {
            lastSavedAt = loaded.updatedAt
        }
    }

    LaunchedEffect(loadedNoteId) {
        if (loadedNoteId == noteId) {
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(noteId, loadedNoteId, title, content) {
        if (loadedNoteId == noteId) {
            val current = note ?: return@LaunchedEffect
            if (title == current.title && content == current.textContent.orEmpty()) {
                saveStatus = SaveStatus.Saved
                return@LaunchedEffect
            }
            saveStatus = SaveStatus.Saving
            delay(500)
            lastSavedAt = viewModel.saveTextNoteNow(noteId, title, content) ?: System.currentTimeMillis()
            saveStatus = SaveStatus.Saved
        }
    }

    fun saveAndBack() {
        scope.launch {
            saveStatus = SaveStatus.Saving
            lastSavedAt = viewModel.saveTextNoteNow(noteId, title, content) ?: lastSavedAt
            saveStatus = SaveStatus.Saved
            onBack()
        }
    }

    fun saveCurrentTextNoteThen(onSaved: (NoteEntity) -> Unit) {
        val currentNote = note ?: return
        scope.launch {
            saveStatus = SaveStatus.Saving
            val savedAt = viewModel.saveTextNoteNow(noteId, title, content) ?: currentNote.updatedAt
            lastSavedAt = savedAt
            saveStatus = SaveStatus.Saved
            onSaved(
                currentNote.copy(
                    title = title,
                    textContent = content,
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

    BackHandler(onBack = ::saveAndBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text.textNote) },
                navigationIcon = {
                    TextButton(
                        onClick = ::saveAndBack,
                        modifier = Modifier.testTag("back_button"),
                    ) {
                        Text(text.back)
                    }
                },
                actions = {
                    TextButton(
                        onClick = ::shareCurrentTextNote,
                        modifier = Modifier.testTag("share_text_note_button"),
                    ) {
                        Text(text.share)
                    }
                    TextButton(
                        onClick = ::exportCurrentTextNote,
                        modifier = Modifier.testTag("export_text_note_button"),
                    ) {
                        Text(text.exportTxt)
                    }
                    TextButton(onClick = { showDeleteDialog = true }) {
                        Text(text.delete)
                    }
                },
            )
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = text.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
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
                ReminderControls(
                    note = currentNote,
                    text = text,
                    appLanguage = appLanguage,
                    onSetReminder = { reminderAt -> viewModel.setNoteReminder(noteId, reminderAt) },
                    onClearReminder = { viewModel.setNoteReminder(noteId, null) },
                )
                Text(
                    text = text.content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text(text.content) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = editorFontSize.fontSizeSp.sp,
                        lineHeight = (editorFontSize.fontSizeSp + 8).sp,
                    ),
                    minLines = 12,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(contentFocusRequester)
                        .testTag("text_note_content"),
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = text.deleteNote,
            body = text.deleteNoteBody,
            confirmText = text.delete,
            cancelText = text.cancel,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteNote(noteId)
                showDeleteDialog = false
                onDeleted()
            },
        )
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
    var loadedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingPngBytes by remember { mutableStateOf<ByteArray?>(null) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val titleFocusRequester = remember(noteId) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
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
        if (loadedNoteId == noteId) {
            titleFocusRequester.requestFocus()
            keyboardController?.show()
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
        val width = canvasSize.width.takeIf { it > 0 } ?: DEFAULT_DRAWING_EXPORT_WIDTH
        val height = canvasSize.height.takeIf { it > 0 } ?: DEFAULT_DRAWING_EXPORT_HEIGHT
        return renderDrawingPng(currentStrokes, width, height)
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

    fun clearDrawing() {
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

    BackHandler(onBack = ::saveAndBack)

    Scaffold(
        topBar = {
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
                        Text(text.delete)
                    }
                },
            )
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
                DrawingToolBar(
                    strokes = strokes,
                    redoStrokes = redoStrokes,
                    selectedTool = selectedTool,
                    selectedBrushSize = selectedBrushSize,
                    selectedColor = selectedColor,
                    text = text,
                    onUndo = ::undoStroke,
                    onRedo = ::redoStroke,
                    onClear = ::clearDrawing,
                    onSharePng = ::shareCurrentDrawingPng,
                    onExportPng = ::exportCurrentDrawingPng,
                    onToolChange = { selectedTool = it },
                    onBrushSizeChange = { selectedBrushSize = it },
                    onColorChange = { selectedColor = it },
                )
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
                        .weight(1f),
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = text.deleteNote,
            body = text.deleteNoteBody,
            confirmText = text.delete,
            cancelText = text.cancel,
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            item {
                Button(
                    onClick = onSharePng,
                    modifier = Modifier.testTag("share_drawing_png_button"),
                ) {
                    Text(text.sharePng)
                }
            }
            item {
                Button(
                    onClick = onExportPng,
                    modifier = Modifier.testTag("export_drawing_png_button"),
                ) {
                    Text(text.exportPng)
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    val sizeLabel = if (selectedTool == DrawingTool.Eraser) text.eraserSize else text.penSize
                    FilterChip(
                        selected = selectedBrushSize == size,
                        onClick = { onBrushSizeChange(size) },
                        label = { Text("$sizeLabel: ${size.label(text, selectedTool)}") },
                        modifier = Modifier.testTag("drawing_brush_${size.name}"),
                    )
                }
            }
        }

        if (selectedTool == DrawingTool.Pen) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    var activeEraserPreview by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .onSizeChanged(onCanvasSizeChange)
            .pointerInput(Unit) {
                var baseStrokes = emptyList<DrawingStroke>()
                var activePoints = emptyList<DrawingPoint>()
                var activeStroke = DrawingStroke(emptyList())

                detectDragGestures(
                    onDragStart = { offset ->
                        baseStrokes = latestStrokes
                        activePoints = listOf(DrawingPoint(offset.x, offset.y))
                        activeEraserPreview = offset.takeIf { latestStrokeTool == DrawingTools.ERASER }
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
                        activePoints = activePoints + DrawingPoint(change.position.x, change.position.y)
                        activeEraserPreview = change.position.takeIf { latestStrokeTool == DrawingTools.ERASER }
                        activeStroke = activeStroke.copy(points = activePoints)
                        latestOnStrokesChange(baseStrokes + activeStroke)
                    },
                    onDragEnd = {
                        latestOnStrokeFinished(baseStrokes + activeStroke)
                        activeEraserPreview = null
                    },
                    onDragCancel = {
                        latestOnStrokeFinished(baseStrokes + activeStroke)
                        activeEraserPreview = null
                    },
                )
            },
    ) {
        drawDrawingStrokes(strokes)
        activeEraserPreview?.let { center ->
            if (strokeTool == DrawingTools.ERASER) {
                drawEraserPreview(center, brushWidthPx)
            }
        }
    }
}

private fun DrawScope.drawDrawingStrokes(strokes: List<DrawingStroke>) {
    drawRect(Color.White)
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
    strokes.forEach { stroke ->
        drawDrawingStroke(stroke)
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
        Text(
            text = text.notificationPermissionHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                error?.let {
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
                    val trimmed = name.trim()
                    error = validateFolderName(trimmed, folders, text, currentFolderId)
                    if (error == null) onConfirm(trimmed)
                },
            ) {
                Text(text.save)
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
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
