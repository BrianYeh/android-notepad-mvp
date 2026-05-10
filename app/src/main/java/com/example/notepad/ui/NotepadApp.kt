package com.example.notepad.ui

import android.content.Context
import android.net.Uri
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notepad.data.ALL_NOTES_FILTER_NAME
import com.example.notepad.data.AppLanguage
import com.example.notepad.data.DEFAULT_FOLDER_ID
import com.example.notepad.data.DEFAULT_FOLDER_NAME
import com.example.notepad.data.DrawingJson
import com.example.notepad.data.DrawingPoint
import com.example.notepad.data.DrawingStroke
import com.example.notepad.data.FolderEntity
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteListMode
import com.example.notepad.data.NoteSortOption
import com.example.notepad.data.NoteTypeFilter
import com.example.notepad.data.NoteTypes
import com.example.notepad.viewmodel.NotepadViewModel
import java.text.DateFormat
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
    appLanguage: AppLanguage,
    viewModel: NotepadViewModel,
) {
    var screen: AppScreen by remember { mutableStateOf(AppScreen.Main) }
    val text = remember(appLanguage) { uiTextFor(appLanguage) }

    when (val currentScreen = screen) {
        AppScreen.Main -> MainScreen(
            folders = folders,
            notes = notes,
            selectedFolderId = selectedFolderId,
            searchQuery = searchQuery,
            listMode = listMode,
            sortOption = sortOption,
            typeFilter = typeFilter,
            appLanguage = appLanguage,
            text = text,
            onSelectFolder = viewModel::selectFolder,
            onSearchQueryChange = viewModel::setSearchQuery,
            onListModeChange = viewModel::setListMode,
            onSortOptionChange = viewModel::setSortOption,
            onTypeFilterChange = viewModel::setTypeFilter,
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
            viewModel = viewModel,
            onBack = { screen = AppScreen.Main },
        )

        is AppScreen.TextEditor -> TextEditorScreen(
            noteId = currentScreen.noteId,
            folders = folders,
            text = text,
            viewModel = viewModel,
            onBack = { screen = AppScreen.Main },
            onDeleted = { screen = AppScreen.Main },
        )

        is AppScreen.DrawingEditor -> DrawingEditorScreen(
            noteId = currentScreen.noteId,
            folders = folders,
            text = text,
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
    appLanguage: AppLanguage,
    text: UiText,
    onSelectFolder: (Long?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onListModeChange: (NoteListMode) -> Unit,
    onSortOptionChange: (NoteSortOption) -> Unit,
    onTypeFilterChange: (NoteTypeFilter) -> Unit,
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
                text = text,
                onSortOptionChange = onSortOptionChange,
                onTypeFilterChange = onTypeFilterChange,
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
    viewModel: NotepadViewModel,
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
    text: UiText,
    onSortOptionChange: (NoteSortOption) -> Unit,
    onTypeFilterChange: (NoteTypeFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
    viewModel: NotepadViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val note by viewModel.observeNote(noteId).collectAsStateWithLifecycle(initialValue = null)
    var title by remember(noteId) { mutableStateOf("") }
    var content by remember(noteId) { mutableStateOf("") }
    var loadedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val titleFocusRequester = remember(noteId) { FocusRequester() }
    val contentFocusRequester = remember(noteId) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(note?.id) {
        val loaded = note ?: return@LaunchedEffect
        title = loaded.title
        content = loaded.textContent.orEmpty()
        loadedNoteId = loaded.id
    }

    LaunchedEffect(loadedNoteId) {
        if (loadedNoteId == noteId) {
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(noteId, loadedNoteId, title, content) {
        if (loadedNoteId == noteId) {
            delay(500)
            viewModel.saveTextNote(noteId, title, content)
        }
    }

    fun saveAndBack() {
        viewModel.saveTextNote(noteId, title, content)
        onBack()
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { contentFocusRequester.requestFocus() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .testTag("text_note_title"),
                )
                NoteFolderSelector(
                    folders = folders,
                    text = text,
                    currentFolderId = currentNote.folderId,
                    onMove = { folderId -> viewModel.moveNote(noteId, folderId) },
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(text.content) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
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
    viewModel: NotepadViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val note by viewModel.observeNote(noteId).collectAsStateWithLifecycle(initialValue = null)
    var title by remember(noteId) { mutableStateOf("") }
    var strokes by remember(noteId) { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var loadedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val titleFocusRequester = remember(noteId) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

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
    return "$folderName • $timestamps$pinned"
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

private fun formatTime(timestamp: Long, language: AppLanguage): String {
    val locale = when (language) {
        AppLanguage.English -> Locale.ENGLISH
        AppLanguage.TraditionalChinese -> Locale.TRADITIONAL_CHINESE
    }
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        .format(Date(timestamp))
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
