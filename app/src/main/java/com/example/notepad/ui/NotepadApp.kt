package com.example.notepad.ui

import android.content.Context
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notepad.IncomingTextShare
import com.example.notepad.data.ALL_NOTES_FILTER_NAME
import com.example.notepad.data.AppLanguage
import com.example.notepad.data.DEFAULT_FOLDER_ID
import com.example.notepad.data.DEFAULT_FOLDER_NAME
import com.example.notepad.data.DrawingJson
import com.example.notepad.data.DrawingPoint
import com.example.notepad.data.DrawingStroke
import com.example.notepad.data.EditorFontSize
import com.example.notepad.data.FolderEntity
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteListMode
import com.example.notepad.data.NoteSortOption
import com.example.notepad.data.NoteTypeFilter
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.ReminderFilter
import com.example.notepad.viewmodel.NotepadViewModel
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

private enum class SaveStatus {
    Saving,
    Saved,
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
    var loadedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val titleFocusRequester = remember(noteId) { FocusRequester() }
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
        strokes = DrawingJson.decode(loaded.drawingData)
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

    fun shareCurrentDrawingNote() {
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
            val folderName = folderDisplayNameById(savedNote.folderId, folders, text)
            sharePlainText(
                context = context,
                subject = noteTitle(savedNote, text),
                body = buildDrawingNoteShareText(savedNote, folderName, text, appLanguage),
                chooserTitle = text.shareChooserTitle,
            )
        }
    }

    fun exportCurrentDrawingNote() {
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
            val folderName = folderDisplayNameById(savedNote.folderId, folders, text)
            pendingExportText = buildDrawingNoteShareText(savedNote, folderName, text, appLanguage)
            exportTextLauncher.launch(defaultTextExportFileName(savedNote, text))
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
                    TextButton(
                        onClick = ::shareCurrentDrawingNote,
                        modifier = Modifier.testTag("share_drawing_note_button"),
                    ) {
                        Text(text.share)
                    }
                    TextButton(
                        onClick = ::exportCurrentDrawingNote,
                        modifier = Modifier.testTag("export_drawing_note_button"),
                    ) {
                        Text(text.exportTxt)
                    }
                    TextButton(
                        onClick = {
                            strokes = emptyList()
                            viewModel.saveDrawingNote(noteId, title, "[]")
                        },
                    ) {
                        Text(text.clear)
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
                DrawingCanvas(
                    strokes = strokes,
                    onStrokesChange = { updatedStrokes -> strokes = updatedStrokes },
                    onStrokeFinished = { updatedStrokes ->
                        viewModel.saveDrawingNote(noteId, title, DrawingJson.encode(updatedStrokes))
                    },
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
private fun DrawingCanvas(
    strokes: List<DrawingStroke>,
    onStrokesChange: (List<DrawingStroke>) -> Unit,
    onStrokeFinished: (List<DrawingStroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestStrokes by rememberUpdatedState(strokes)
    val latestOnStrokesChange by rememberUpdatedState(onStrokesChange)
    val latestOnStrokeFinished by rememberUpdatedState(onStrokeFinished)

    Canvas(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                var baseStrokes = emptyList<DrawingStroke>()
                var activePoints = emptyList<DrawingPoint>()

                detectDragGestures(
                    onDragStart = { offset ->
                        baseStrokes = latestStrokes
                        activePoints = listOf(DrawingPoint(offset.x, offset.y))
                        latestOnStrokesChange(baseStrokes + DrawingStroke(activePoints))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        activePoints = activePoints + DrawingPoint(change.position.x, change.position.y)
                        latestOnStrokesChange(baseStrokes + DrawingStroke(activePoints))
                    },
                    onDragEnd = {
                        latestOnStrokeFinished(baseStrokes + DrawingStroke(activePoints))
                    },
                    onDragCancel = {
                        latestOnStrokeFinished(baseStrokes + DrawingStroke(activePoints))
                    },
                )
            },
    ) {
        strokes.forEach { stroke ->
            val points = stroke.points
            if (points.size == 1) {
                drawCircle(
                    color = Color.Black,
                    radius = 2.5f,
                    center = Offset(points.first().x, points.first().y),
                )
            } else if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { point ->
                        lineTo(point.x, point.y)
                    }
                }
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
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

private fun writeTextToUri(context: Context, uri: Uri, text: String) {
    val outputStream = context.contentResolver.openOutputStream(uri)
        ?: error("Unable to open backup output stream.")
    outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(text)
    }
}

private fun readTextFromUri(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: error("Unable to open backup input stream.")
    return inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
        reader.readText()
    }
}
