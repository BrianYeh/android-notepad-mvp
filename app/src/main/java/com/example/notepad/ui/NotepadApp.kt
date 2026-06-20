package com.example.notepad.ui

import android.content.Context
import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.notepad.IncomingTextShare
import com.example.notepad.PendingWidgetAction
import com.example.notepad.WidgetAction
import com.example.notepad.billing.PremiumBillingState
import com.example.notepad.billing.PremiumPlan
import com.example.notepad.billing.PremiumSubscriptionStatus
import com.example.notepad.data.ALL_NOTES_FILTER_NAME
import com.example.notepad.data.AppLanguage
import com.example.notepad.data.BackupData
import com.example.notepad.data.BackupPreview
import com.example.notepad.data.ChecklistItem
import com.example.notepad.data.ChecklistJson
import com.example.notepad.data.DEFAULT_FOLDER_ID
import com.example.notepad.data.DEFAULT_FOLDER_NAME
import com.example.notepad.data.DEFAULT_DRAWING_COLOR_ARGB
import com.example.notepad.data.DEFAULT_DRAWING_STROKE_WIDTH
import com.example.notepad.data.DecodedBackup
import com.example.notepad.data.DrawingJson
import com.example.notepad.data.DrawingPoint
import com.example.notepad.data.DrawingStroke
import com.example.notepad.data.DrawingTools
import com.example.notepad.data.DriveSyncResult
import com.example.notepad.data.EditorFontSize
import com.example.notepad.data.FolderEntity
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteQuickFilter
import com.example.notepad.data.NoteListMode
import com.example.notepad.data.NoteSortOption
import com.example.notepad.data.NoteTypeFilter
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.PrivacyPreferences
import com.example.notepad.data.ReminderFilter
import com.example.notepad.data.ReminderRepeat
import com.example.notepad.data.SyncMetadata
import com.example.notepad.data.SyncStatus
import com.example.notepad.data.TextImportFile
import com.example.notepad.data.TextFormatRange
import com.example.notepad.data.TextFormatType
import com.example.notepad.data.TextFormattingJson
import com.example.notepad.data.adjustTextFormattingAfterEdit
import com.example.notepad.data.defaultBatchExportFileName
import com.example.notepad.data.currentLineRange
import com.example.notepad.data.currentWordRange
import com.example.notepad.data.normalizedReminderRepeat
import com.example.notepad.data.normalizedFormatUrl
import com.example.notepad.data.renderDrawingPng
import com.example.notepad.data.selectedTextRange
import com.example.notepad.reminder.ReminderScheduler
import com.example.notepad.viewmodel.NotepadViewModel
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private sealed interface AppScreen {
    data object Main : AppScreen
    data object Settings : AppScreen
    data class Premium(val returnTo: AppScreen = Main) : AppScreen
    data class TextEditor(val noteId: Long, val isNewDraft: Boolean = false) : AppScreen
    data class DrawingEditor(val noteId: Long, val isNewDraft: Boolean = false) : AppScreen
    data class ChecklistEditor(val noteId: Long) : AppScreen
}

private data class PendingRestoreBackup(
    val data: BackupData,
    val preview: BackupPreview,
)

private const val BACKUP_FILE_NAME = "just-notes-backup.json"
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
    Synced,
    Saved,
    Failed,
}

private enum class MainTab {
    Notes,
    Search,
    Premium,
}

private enum class MainContentView {
    List,
    Calendar,
}

private enum class PremiumPlanSelection {
    Annual,
    Monthly,
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

private const val LAST_DRAWING_PEN_BRUSH_SIZE_KEY = "last_drawing_pen_brush_size"
private const val LAST_DRAWING_PEN_COLOR_KEY = "last_drawing_pen_color"

private fun Context.lastDrawingPreferences() = getSharedPreferences(PrivacyPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)

private fun Context.readLastDrawingPenBrushSize(): DrawingBrushSize {
    val savedName = lastDrawingPreferences().getString(LAST_DRAWING_PEN_BRUSH_SIZE_KEY, null)
    return DrawingBrushSize.entries.firstOrNull { it.name == savedName } ?: DrawingBrushSize.Medium
}

private fun Context.writeLastDrawingPenBrushSize(brushSize: DrawingBrushSize) {
    lastDrawingPreferences().edit().putString(LAST_DRAWING_PEN_BRUSH_SIZE_KEY, brushSize.name).apply()
}

private fun Context.readLastDrawingPenColor(): DrawingColorOption {
    val savedName = lastDrawingPreferences().getString(LAST_DRAWING_PEN_COLOR_KEY, null)
    return DrawingColorOption.entries.firstOrNull { it.name == savedName } ?: DrawingColorOption.Black
}

private fun Context.writeLastDrawingPenColor(color: DrawingColorOption) {
    lastDrawingPreferences().edit().putString(LAST_DRAWING_PEN_COLOR_KEY, color.name).apply()
}

private fun formatHeading1Label(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "標題 1" else "H1"

private fun formatHeading2Label(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "標題 2" else "H2"

private fun formatHighlightLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "螢光標記" else "Highlight"

private fun formatLinkLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "連結" else "Link"

private fun clearFormattingLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "清除格式" else "Clear format"

private fun formattingPremiumEntryLabel(language: AppLanguage): String {
    return if (language == AppLanguage.TraditionalChinese) "進階版文字格式" else "Premium formatting"
}

private fun formatBoldLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "粗體" else "Bold"

private fun formatItalicLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "斜體" else "Italic"

private fun formatUnderlineLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "底線" else "Underline"

private fun findInNoteActionLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "搜尋" else "Find"

private fun homeFiltersLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "篩選" else "Filters"

private fun hideHomeFiltersLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "收合" else "Hide"

private fun formattingPremiumRequiredLabel(language: AppLanguage): String {
    return if (language == AppLanguage.TraditionalChinese) "文字格式是進階版功能。" else "Text formatting is a Premium feature."
}

private fun doneLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "完成" else "Done"

private fun savedJustNowLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "剛剛已儲存" else "Saved just now"

private fun saveFailedLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "儲存失敗" else "Save failed"

private fun preparingPngLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "正在準備 PNG..." else "Preparing PNG..."

private fun retryLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "重試" else "Retry"

private fun openLinkFailedLabel(language: AppLanguage): String {
    return if (language == AppLanguage.TraditionalChinese) "無法開啟連結。" else "Could not open link."
}

private fun setReminderActionLabel(text: UiText, hasPremiumAccess: Boolean): String {
    return if (hasPremiumAccess) text.setReminder else "${text.setReminder} (${text.premium})"
}

@Composable
private fun rememberReminderDeliveryGate(text: UiText): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pendingReminderAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun showBlockedMessage() {
        Toast.makeText(context, text.reminderNotificationsBlockedLabel(), Toast.LENGTH_SHORT).show()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        val action = pendingReminderAction
        pendingReminderAction = null
        if (ReminderScheduler.notificationDeliveryStatus(context) == ReminderScheduler.NotificationDeliveryStatus.Ready) {
            action?.invoke()
        } else {
            showBlockedMessage()
        }
    }

    return { onReady ->
        when (ReminderScheduler.notificationDeliveryStatus(context)) {
            ReminderScheduler.NotificationDeliveryStatus.Ready -> onReady()
            ReminderScheduler.NotificationDeliveryStatus.PermissionRequired -> {
                pendingReminderAction = onReady
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ReminderScheduler.NotificationDeliveryStatus.AppNotificationsDisabled,
            ReminderScheduler.NotificationDeliveryStatus.ReminderChannelDisabled,
            -> showBlockedMessage()
        }
    }
}

@Composable
private fun rememberFutureReminderSubmissionGate(text: UiText): (Long, () -> Unit) -> Unit {
    val context = LocalContext.current
    val requireReminderDeliveryReady = rememberReminderDeliveryGate(text)
    return { reminderAt, onReady ->
        if (reminderAt <= System.currentTimeMillis()) {
            Toast.makeText(context, text.reminderMustBeFuture, Toast.LENGTH_SHORT).show()
        } else {
            requireReminderDeliveryReady(onReady)
        }
    }
}

private fun importExportTitleLabel(language: AppLanguage): String {
    return if (language == AppLanguage.TraditionalChinese) "匯入／匯出" else "Import / Export"
}

private fun importExportHintLabel(language: AppLanguage): String {
    return if (language == AppLanguage.TraditionalChinese) {
        "以文字檔或 ZIP 封存移動記事。這是本機檔案匯入／匯出，不是同步或備份。"
    } else {
        "Move notes as text files or a ZIP archive. This is local file import/export, not sync or backup."
    }
}

private fun selectTextToFormatLabel(language: AppLanguage): String {
    return if (language == AppLanguage.TraditionalChinese) "請先選取要套用格式的文字。" else "Select text to format first."
}

private fun linkUrlLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "連結網址" else "Link URL"

private fun applyLabel(language: AppLanguage): String = if (language == AppLanguage.TraditionalChinese) "套用" else "Apply"

private fun developerToolsLabel(language: AppLanguage): String {
    return if (language == AppLanguage.TraditionalChinese) "開發者工具" else "Developer tools"
}

private fun debugPremiumOverrideLabel(language: AppLanguage): String {
    return if (language == AppLanguage.TraditionalChinese) "開啟付費功能測試" else "Unlock premium feature gates"
}

private fun debugPremiumOverrideBody(language: AppLanguage): String {
    return if (language == AppLanguage.TraditionalChinese) {
        "僅限 debug 版，不會變更真實訂閱狀態。"
    } else {
        "Debug build only. Does not change purchase status."
    }
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
fun PrivacyLockScreen(
    text: UiText,
    canUseDeviceLock: Boolean,
    onUnlock: () -> Unit,
    onDisableLock: () -> Unit,
) {
    BackHandler(enabled = true) {}

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            change.consume()
                        }
                    }
                }
            },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text.unlockJustNotes,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (canUseDeviceLock) text.unlockRequiredBody else text.deviceLockUnavailable,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onUnlock,
                enabled = canUseDeviceLock,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .testTag("privacy_unlock_button"),
            ) {
                Text(text.unlockJustNotes)
            }
            if (!canUseDeviceLock) {
                TextButton(
                    onClick = onDisableLock,
                    modifier = Modifier.testTag("privacy_disable_lock_button"),
                ) {
                    Text(text.turnOffLock)
                }
            }
        }
    }
}

@Composable
fun NotepadApp(
    folders: List<FolderEntity>,
    allNotes: List<NoteEntity>,
    notes: List<NoteEntity>,
    selectedFolderId: Long?,
    searchQuery: String,
    listMode: NoteListMode,
    sortOption: NoteSortOption,
    typeFilter: NoteTypeFilter,
    reminderFilter: ReminderFilter,
    quickFilter: NoteQuickFilter,
    editorFontSize: EditorFontSize,
    isRecognizingText: Boolean,
    incomingTextShare: IncomingTextShare?,
    pendingWidgetAction: PendingWidgetAction?,
    isPrivacyLocked: Boolean,
    deviceUnlockAvailable: Boolean,
    onIncomingTextShareHandled: (Long) -> Unit,
    onWidgetActionHandled: (Long) -> Unit,
    viewModel: NotepadViewModel,
) {
    var screen: AppScreen by remember { mutableStateOf(AppScreen.Main) }
    val appLanguage = rememberSystemAppLanguage()
    val text = remember(appLanguage) { uiTextFor(appLanguage) }
    val context = LocalContext.current
    val billingState by viewModel.premiumBillingState.collectAsStateWithLifecycle()
    val onlineSyncTargetUri by viewModel.onlineSyncTargetUri.collectAsStateWithLifecycle()
    val onlineSyncAutoOnStart by viewModel.onlineSyncAutoOnStart.collectAsStateWithLifecycle()
    val hideReminderNotificationContent by viewModel.hideReminderNotificationContent.collectAsStateWithLifecycle()
    val requireDeviceUnlock by viewModel.requireDeviceUnlock.collectAsStateWithLifecycle()
    val lastOnlineSyncAt by viewModel.lastOnlineSyncAt.collectAsStateWithLifecycle()
    val lastOnlineRestoreAt by viewModel.lastOnlineRestoreAt.collectAsStateWithLifecycle()
    val restoreRollbackCheckpoint by viewModel.restoreRollbackCheckpoint.collectAsStateWithLifecycle()
    val syncMetadata by viewModel.syncMetadata.collectAsStateWithLifecycle()
    var onlineSyncAutoAttempted by remember { mutableStateOf(false) }
    val calendarNotes = remember(allNotes, selectedFolderId, listMode) {
        allNotes
            .filter { note -> note.isDeleted == (listMode == NoteListMode.Trash) }
            .filter { note -> selectedFolderId == null || note.folderId == selectedFolderId }
    }
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

    LaunchedEffect(pendingWidgetAction?.id) {
        val widgetAction = pendingWidgetAction ?: return@LaunchedEffect
        if (widgetAction.action == WidgetAction.NewTextNote) {
            onWidgetActionHandled(widgetAction.id)
            if (!billingState.hasPremiumAccess) {
                viewModel.selectFolder(null)
            }
            viewModel.createTextNote { noteId ->
                screen = AppScreen.TextEditor(noteId, isNewDraft = true)
            }
        }
    }

    LaunchedEffect(pendingWidgetAction?.id) {
        val widgetAction = pendingWidgetAction ?: return@LaunchedEffect
        val action = widgetAction.action as? WidgetAction.OpenNote ?: return@LaunchedEffect
        val note = viewModel.getActiveNote(action.noteId)
        if (note != null) {
            screen = note.toEditorScreen()
        } else {
            screen = AppScreen.Main
        }
        onWidgetActionHandled(widgetAction.id)
    }

    LaunchedEffect(onlineSyncAutoOnStart, onlineSyncTargetUri) {
        val targetUri = onlineSyncTargetUri
        if (!onlineSyncAutoOnStart || targetUri == null || onlineSyncAutoAttempted) return@LaunchedEffect
        onlineSyncAutoAttempted = true
        try {
            val backupJson = viewModel.exportBackupJson()
            withContext(Dispatchers.IO) {
                writeTextToUri(context, Uri.parse(targetUri), backupJson)
            }
            viewModel.recordOnlineSync()
        } catch (_: Exception) {
            Toast.makeText(context, text.backupFailed, Toast.LENGTH_SHORT).show()
        }
    }

    when (val currentScreen = screen) {
        AppScreen.Main -> MainScreen(
            folders = folders,
            notes = notes,
            calendarNotes = calendarNotes,
            selectedFolderId = selectedFolderId,
            searchQuery = searchQuery,
            listMode = listMode,
            sortOption = sortOption,
            typeFilter = typeFilter,
            reminderFilter = reminderFilter,
            quickFilter = quickFilter,
            appLanguage = appLanguage,
            text = text,
            hasPremiumAccess = billingState.hasPremiumAccess,
            isPrivacyLocked = isPrivacyLocked,
            onSelectFolder = viewModel::selectFolder,
            onSearchQueryChange = viewModel::setSearchQuery,
            onListModeChange = viewModel::setListMode,
            onSortOptionChange = viewModel::setSortOption,
            onTypeFilterChange = viewModel::setTypeFilter,
            onReminderFilterChange = viewModel::setReminderFilter,
            onQuickFilterChange = viewModel::setQuickFilter,
            onOpenSettings = { screen = AppScreen.Settings },
            onOpenPremium = { screen = AppScreen.Premium() },
            onCreateFolder = viewModel::createFolder,
            onRenameFolder = viewModel::renameFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onCreateTextNote = {
                viewModel.createTextNote { noteId ->
                    screen = AppScreen.TextEditor(noteId, isNewDraft = true)
                }
            },
            onCreateDrawingNote = {
                viewModel.createDrawingNote { noteId ->
                    screen = AppScreen.DrawingEditor(noteId, isNewDraft = true)
                }
            },
            onCreateChecklistNote = {
                viewModel.createChecklistNote { noteId ->
                    screen = AppScreen.ChecklistEditor(noteId)
                }
            },
            onCreateOcrNote = {
                imagePickerLauncher.launch("image/*")
            },
            onCreateReminderTextNote = { reminderAt ->
                viewModel.createTextNoteWithReminder(reminderAt) { noteId ->
                    screen = AppScreen.TextEditor(noteId, isNewDraft = true)
                }
            },
            onOpenNote = { note ->
                screen = when (note.type) {
                    NoteTypes.DRAWING -> AppScreen.DrawingEditor(note.id)
                    NoteTypes.CHECKLIST -> AppScreen.ChecklistEditor(note.id)
                    else -> AppScreen.TextEditor(note.id)
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
            hideReminderNotificationContent = hideReminderNotificationContent,
            requireDeviceUnlock = requireDeviceUnlock,
            deviceUnlockAvailable = deviceUnlockAvailable,
            currentBackupPreview = BackupPreview.from(folders = folders, notes = allNotes),
            onlineSyncTargetUri = onlineSyncTargetUri,
            onlineSyncAutoOnStart = onlineSyncAutoOnStart,
            lastOnlineSyncAt = lastOnlineSyncAt,
            lastOnlineRestoreAt = lastOnlineRestoreAt,
            syncMetadata = syncMetadata,
            billingState = billingState,
            debugPremiumToolsAvailable = viewModel.debugPremiumToolsAvailable,
            restoreRollbackCheckpoint = restoreRollbackCheckpoint,
            isPrivacyLocked = isPrivacyLocked,
            viewModel = viewModel,
            onEditorFontSizeChange = viewModel::setEditorFontSize,
            onHideReminderNotificationContentChange = viewModel::setHideReminderNotificationContent,
            onRequireDeviceUnlockChange = viewModel::setRequireDeviceUnlock,
            onOnlineSyncTargetChange = viewModel::setOnlineSyncTargetUri,
            onOnlineSyncAutoOnStartChange = viewModel::setOnlineSyncAutoOnStart,
            onDebugPremiumOverrideChange = viewModel::setDebugPremiumOverride,
            onOnlineSyncRecorded = { viewModel.recordOnlineSync() },
            onOnlineRestoreRecorded = { viewModel.recordOnlineRestore() },
            onOnlineSyncDisconnect = viewModel::disconnectOnlineSync,
            onBack = { screen = AppScreen.Main },
        )

        is AppScreen.Premium -> PremiumScreen(
            text = text,
            billingState = billingState,
            onSubscribe = { plan ->
                val activity = context as? Activity
                if (activity == null) {
                    false
                } else {
                    viewModel.launchPremiumPurchase(activity, plan)
                }
            },
            onRefreshPurchaseStatus = viewModel::refreshPremiumEntitlement,
            onBack = { screen = currentScreen.returnTo },
            onOpenNotes = { screen = currentScreen.returnTo },
        )

        is AppScreen.TextEditor -> TextEditorScreen(
            noteId = currentScreen.noteId,
            isNewDraft = currentScreen.isNewDraft,
            folders = folders,
            text = text,
            editorFontSize = editorFontSize,
            appLanguage = appLanguage,
            billingState = billingState,
            isPrivacyLocked = isPrivacyLocked,
            viewModel = viewModel,
            onOpenPremium = { screen = AppScreen.Premium(returnTo = currentScreen) },
            onBack = { screen = AppScreen.Main },
            onDeleted = { screen = AppScreen.Main },
        )

        is AppScreen.DrawingEditor -> DrawingEditorScreen(
            noteId = currentScreen.noteId,
            isNewDraft = currentScreen.isNewDraft,
            folders = folders,
            text = text,
            appLanguage = appLanguage,
            billingState = billingState,
            isPrivacyLocked = isPrivacyLocked,
            viewModel = viewModel,
            onOpenPremium = { screen = AppScreen.Premium(returnTo = currentScreen) },
            onOpenPremiumAfterDraftDiscard = { screen = AppScreen.Premium(returnTo = AppScreen.Main) },
            onBack = { screen = AppScreen.Main },
            onDeleted = { screen = AppScreen.Main },
        )

        is AppScreen.ChecklistEditor -> ChecklistEditorScreen(
            noteId = currentScreen.noteId,
            folders = folders,
            text = text,
            appLanguage = appLanguage,
            billingState = billingState,
            isPrivacyLocked = isPrivacyLocked,
            viewModel = viewModel,
            onOpenPremium = { screen = AppScreen.Premium(returnTo = currentScreen) },
            onBack = { screen = AppScreen.Main },
            onDeleted = { screen = AppScreen.Main },
        )
    }

    if (isRecognizingText && !isPrivacyLocked) {
        OcrProgressDialog(text = text)
    }
}

private fun NoteEntity.toEditorScreen(): AppScreen {
    return when (type) {
        NoteTypes.DRAWING -> AppScreen.DrawingEditor(id)
        NoteTypes.CHECKLIST -> AppScreen.ChecklistEditor(id)
        else -> AppScreen.TextEditor(id)
    }
}

@Composable
private fun rememberSystemAppLanguage(): AppLanguage {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val primaryLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
        AppLanguage.fromLocale(primaryLocale)
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

@Composable
private fun MainNavigationBar(
    selectedTab: MainTab,
    text: UiText,
    onOpenNotes: () -> Unit,
    onOpenSearch: (() -> Unit)? = null,
    onOpenPremium: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == MainTab.Notes,
            onClick = onOpenNotes,
            icon = { Text("N", fontWeight = FontWeight.Bold) },
            label = { Text(text.notesTab) },
            modifier = Modifier.testTag("notes_tab"),
        )
        if (onOpenSearch != null) {
            NavigationBarItem(
                selected = selectedTab == MainTab.Search,
                onClick = onOpenSearch,
                icon = { Icon(Icons.Filled.Search, contentDescription = text.search) },
                label = { Text(text.search) },
                modifier = Modifier.testTag("search_tab"),
            )
        }
        NavigationBarItem(
            selected = selectedTab == MainTab.Premium,
            onClick = onOpenPremium,
            icon = { Text("P", fontWeight = FontWeight.Bold) },
            label = { Text(text.premium) },
            modifier = Modifier.testTag("premium_tab"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumScreen(
    text: UiText,
    billingState: PremiumBillingState,
    onSubscribe: (PremiumPlan) -> Boolean,
    onRefreshPurchaseStatus: () -> Unit,
    onBack: () -> Unit,
    onOpenNotes: () -> Unit,
) {
    val context = LocalContext.current
    var selectedPlan by remember { mutableStateOf(PremiumPlanSelection.Annual) }
    val annualPriceAvailable = billingState.billingAvailable && billingState.annualPrice != null
    val monthlyPriceAvailable = billingState.billingAvailable && billingState.monthlyPrice != null
    val showCommerceUi = annualPriceAvailable || monthlyPriceAvailable
    val selectedBillingPlan = when (selectedPlan) {
        PremiumPlanSelection.Annual -> PremiumPlan.Annual
        PremiumPlanSelection.Monthly -> PremiumPlan.Monthly
    }
    val selectedPriceAvailable = when (selectedPlan) {
        PremiumPlanSelection.Annual -> annualPriceAvailable
        PremiumPlanSelection.Monthly -> monthlyPriceAvailable
    }

    LaunchedEffect(annualPriceAvailable, monthlyPriceAvailable) {
        if (selectedPlan == PremiumPlanSelection.Annual && !annualPriceAvailable && monthlyPriceAvailable) {
            selectedPlan = PremiumPlanSelection.Monthly
        } else if (selectedPlan == PremiumPlanSelection.Monthly && !monthlyPriceAvailable && annualPriceAvailable) {
            selectedPlan = PremiumPlanSelection.Annual
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text.premium) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text.back)
                    }
                },
            )
        },
        bottomBar = {
            MainNavigationBar(
                selectedTab = MainTab.Premium,
                text = text,
                onOpenNotes = onOpenNotes,
                onOpenPremium = {},
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("premium_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showCommerceUi) {
                if (annualPriceAvailable) {
                    PremiumPlanRow(
                        title = text.premiumAnnual,
                        price = billingState.annualPrice ?: text.premiumAnnualPrice,
                        originalPrice = text.premiumAnnualOriginalPrice.takeIf { it.isNotBlank() },
                        selected = selectedPlan == PremiumPlanSelection.Annual,
                        onClick = { selectedPlan = PremiumPlanSelection.Annual },
                        modifier = Modifier.testTag("annual_plan_option"),
                    )
                }
                if (monthlyPriceAvailable) {
                    PremiumPlanRow(
                        title = text.premiumMonthly,
                        price = billingState.monthlyPrice ?: text.premiumMonthlyPrice,
                        originalPrice = null,
                        selected = selectedPlan == PremiumPlanSelection.Monthly,
                        onClick = { selectedPlan = PremiumPlanSelection.Monthly },
                        modifier = Modifier.testTag("monthly_plan_option"),
                    )
                }
                Button(
                    onClick = {
                        if (!onSubscribe(selectedBillingPlan)) {
                            Toast.makeText(context, text.premiumBillingUnavailable, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = selectedPriceAvailable && !billingState.hasPremiumAccess && billingState.canLaunchPurchase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("premium_subscribe_button"),
                ) {
                    Text(if (billingState.hasPremiumAccess) text.premiumActive else text.premiumSubscribe)
                }
            }
            TextButton(
                onClick = onRefreshPurchaseStatus,
                modifier = Modifier.testTag("premium_restore_button"),
            ) {
                Text(text.premiumRestore)
            }
            Text(
                text = premiumStatusText(text, billingState),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = billingState.lastError ?: premiumDetailText(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = text.privacyPolicy,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.Underline,
                )
                Text(
                    text = text.termsOfService,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.Underline,
                )
            }
            Text(
                text = text.premiumFeatures,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp),
            )
            PremiumFeature(
                title = text.premiumFolders,
                body = text.premiumFoldersBody,
                sample = { PremiumFolderSample() },
            )
            PremiumFeature(
                title = text.premiumTextFormatting,
                body = text.premiumTextFormattingBody,
                sample = { PremiumFormattingSample() },
            )
            PremiumFeature(
                title = text.premiumIcons,
                body = text.premiumIconsBody,
                sample = { PremiumIconSample() },
            )
        }
    }
}

private fun premiumStatusText(text: UiText, billingState: PremiumBillingState): String {
    if (billingState.hasPremiumAccess) return text.premiumActive
    return when (billingState.subscription.status) {
        PremiumSubscriptionStatus.PendingPurchase,
        PremiumSubscriptionStatus.VerificationPending,
        -> text.premiumSubscribePending
        else -> text.premiumTrial
    }
}

private fun premiumDetailText(text: UiText): String {
    return text.premiumRenewal
}

@Composable
private fun PremiumPlanRow(
    title: String,
    price: String,
    originalPrice: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            originalPrice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
            Text(text = price, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun PremiumFeature(
    title: String,
    body: String,
    sample: @Composable (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        sample?.invoke()
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PremiumFolderSample() {
    Column(
        modifier = Modifier.testTag("premium_folder_sample"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PremiumFolderRow(
            background = Color(0xFFFFF7A8),
            accent = Color(0xFFEFD64A),
            label = "Folder",
        )
        PremiumFolderRow(
            background = Color(0xFFE9E9FF),
            accent = Color(0xFF9DB3FF),
            label = "Folder",
        )
    }
}

@Composable
private fun PremiumFolderRow(
    background: Color,
    accent: Color,
    label: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PremiumFolderIcon()
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "0",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .background(accent, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PremiumFolderIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.size(32.dp)) {
        val strokeWidth = 2.5.dp.toPx()
        val folderPath = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.32f)
            lineTo(size.width * 0.36f, size.height * 0.32f)
            lineTo(size.width * 0.46f, size.height * 0.46f)
            lineTo(size.width * 0.92f, size.height * 0.46f)
            lineTo(size.width * 0.92f, size.height * 0.82f)
            lineTo(size.width * 0.08f, size.height * 0.82f)
            close()
        }
        drawPath(
            path = folderPath,
            color = color,
            style = Stroke(width = strokeWidth, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun PremiumFormattingSample() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF7B8), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("premium_formatting_sample"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumFormatSampleText(
                label = "H1",
                modifier = Modifier.testTag("premium_format_sample_h1"),
                fontWeight = FontWeight.Bold,
            )
            PremiumFormatSampleText(
                label = "H2",
                modifier = Modifier.testTag("premium_format_sample_h2"),
                fontWeight = FontWeight.SemiBold,
            )
            PremiumFormatSampleText(
                label = "B",
                modifier = Modifier.testTag("premium_format_sample_bold"),
                fontWeight = FontWeight.Bold,
            )
            PremiumFormatSampleText(
                label = "I",
                modifier = Modifier.testTag("premium_format_sample_italic"),
                fontStyle = FontStyle.Italic,
            )
            PremiumFormatSampleText(
                label = "U",
                modifier = Modifier.testTag("premium_format_sample_underline"),
                textDecoration = TextDecoration.Underline,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumFormatSampleText(
                label = "Link",
                modifier = Modifier.testTag("premium_format_sample_link"),
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
            PremiumFormatSampleText(
                label = "Highlight",
                modifier = Modifier
                    .background(Color(0xFFFFF59D), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .testTag("premium_format_sample_highlight"),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PremiumFormatSampleText(
    label: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textDecoration = textDecoration,
        color = color,
        modifier = modifier,
        maxLines = 1,
    )
}

@Composable
private fun PremiumIconSample() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(8.dp)
            .testTag("premium_schedule_sample"),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT").forEachIndexed { index, day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day, style = MaterialTheme.typography.bodySmall)
                Text((index + 1).toString(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    folders: List<FolderEntity>,
    notes: List<NoteEntity>,
    calendarNotes: List<NoteEntity>,
    selectedFolderId: Long?,
    searchQuery: String,
    listMode: NoteListMode,
    sortOption: NoteSortOption,
    typeFilter: NoteTypeFilter,
    reminderFilter: ReminderFilter,
    quickFilter: NoteQuickFilter,
    appLanguage: AppLanguage,
    text: UiText,
    hasPremiumAccess: Boolean,
    isPrivacyLocked: Boolean,
    onSelectFolder: (Long?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onListModeChange: (NoteListMode) -> Unit,
    onSortOptionChange: (NoteSortOption) -> Unit,
    onTypeFilterChange: (NoteTypeFilter) -> Unit,
    onReminderFilterChange: (ReminderFilter) -> Unit,
    onQuickFilterChange: (NoteQuickFilter) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPremium: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onCreateTextNote: () -> Unit,
    onCreateDrawingNote: () -> Unit,
    onCreateChecklistNote: () -> Unit,
    onCreateOcrNote: () -> Unit,
    onCreateReminderTextNote: (Long) -> Unit,
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
    var contentView by remember { mutableStateOf(MainContentView.List) }
    var isSearchVisible by remember { mutableStateOf(searchQuery.isNotBlank()) }
    var searchFocusRequest by remember { mutableStateOf(0) }
    var areFiltersExpanded by remember { mutableStateOf(false) }
    val hasNonDefaultFolders = folders.any { it.id != DEFAULT_FOLDER_ID }
    val showFolderUi = hasPremiumAccess || hasNonDefaultFolders
    val selectedFolder = if (showFolderUi) folders.firstOrNull { it.id == selectedFolderId } else null
    val isTrash = listMode == NoteListMode.Trash
    val isSelectionMode = selectedNoteIds.isNotEmpty()
    val visibleNoteIds = remember(notes, calendarNotes, contentView) {
        val visibleNotes = if (contentView == MainContentView.Calendar) calendarNotes else notes
        visibleNotes.map { it.id }.toSet()
    }
    val hasActiveFilterPanel = quickFilter != NoteQuickFilter.All ||
        reminderFilter != ReminderFilter.All ||
        sortOption != NoteSortOption.UpdatedAt ||
        (!isTrash && contentView != MainContentView.List)
    val shouldShowSearchBar = isSearchVisible || searchQuery.isNotBlank()
    val shouldShowFilterPanel = areFiltersExpanded
    fun clearNoteSelection() {
        selectedNoteIds = emptySet()
        selectedNotesToDelete = null
    }

    fun createNoteWithAllowedFolder(createNote: () -> Unit) {
        if (!hasPremiumAccess) {
            onSelectFolder(null)
        }
        createNote()
    }

    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) {
            addMenuExpanded = false
            showCreateFolderDialog = false
            folderToRename = null
            folderToDelete = null
            noteToMove = null
            noteToDelete = null
            noteToPermanentlyDelete = null
            selectedNotesToDelete = null
            areFiltersExpanded = false
        }
    }

    LaunchedEffect(showFolderUi, selectedFolderId) {
        if (!showFolderUi && selectedFolderId != null) {
            onSelectFolder(null)
        }
    }

    LaunchedEffect(visibleNoteIds) {
        selectedNoteIds = selectedNoteIds.intersect(visibleNoteIds)
    }

    LaunchedEffect(listMode, selectedFolderId, searchQuery, quickFilter) {
        clearNoteSelection()
    }

    LaunchedEffect(listMode) {
        if (isTrash) contentView = MainContentView.List
    }

    LaunchedEffect(hasPremiumAccess, contentView) {
        if (!hasPremiumAccess && contentView == MainContentView.Calendar) {
            contentView = MainContentView.List
            clearNoteSelection()
        }
    }

    LaunchedEffect(hasPremiumAccess, quickFilter) {
        if (!hasPremiumAccess && quickFilter == NoteQuickFilter.HasReminder) {
            onQuickFilterChange(NoteQuickFilter.All)
        }
    }

    LaunchedEffect(hasPremiumAccess, reminderFilter) {
        if (!hasPremiumAccess && reminderFilter != ReminderFilter.All) {
            onReminderFilterChange(ReminderFilter.All)
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) isSearchVisible = true
    }

    LaunchedEffect(hasActiveFilterPanel) {
        if (hasActiveFilterPanel) areFiltersExpanded = true
    }

    fun closeSearch() {
        onSearchQueryChange("")
        isSearchVisible = false
    }

    BackHandler(enabled = isSelectionMode, onBack = ::clearNoteSelection)
    BackHandler(enabled = shouldShowSearchBar && !isSelectionMode, onBack = ::closeSearch)

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
                    },
                )
            }
        },
        bottomBar = {
            MainNavigationBar(
                selectedTab = if (shouldShowSearchBar) MainTab.Search else MainTab.Notes,
                text = text,
                onOpenNotes = ::closeSearch,
                onOpenSearch = {
                    isSearchVisible = true
                    searchFocusRequest += 1
                },
                onOpenPremium = onOpenPremium,
            )
        },
        floatingActionButton = {
            if (!isTrash && !isSelectionMode && contentView != MainContentView.Calendar) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box {
                        SmallFloatingActionButton(
                            onClick = {
                                if (!isPrivacyLocked) addMenuExpanded = true
                            },
                            modifier = Modifier
                                .semantics { contentDescription = text.noteOptions }
                                .testTag("add_note_options_button"),
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = addMenuExpanded && !isPrivacyLocked,
                            onDismissRequest = { addMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text.newChecklistNote) },
                                modifier = Modifier.testTag("new_checklist_note_menu_item"),
                                onClick = {
                                    addMenuExpanded = false
                                    createNoteWithAllowedFolder(onCreateChecklistNote)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text.newDrawingNote) },
                                modifier = Modifier.testTag("new_drawing_note_menu_item"),
                                onClick = {
                                    addMenuExpanded = false
                                    createNoteWithAllowedFolder(onCreateDrawingNote)
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
                            if (hasPremiumAccess) {
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
                    FloatingActionButton(
                        onClick = {
                            if (!isPrivacyLocked) createNoteWithAllowedFolder(onCreateTextNote)
                        },
                        modifier = Modifier
                            .semantics { contentDescription = text.newTextNote }
                            .testTag("add_note_button"),
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
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

                if (showFolderUi) {
                    FolderFilterRow(
                        folders = folders,
                        selectedFolderId = selectedFolderId,
                        text = text,
                        onSelectFolder = onSelectFolder,
                    )
                }

                selectedFolder?.let { folder ->
                    FolderActionRow(
                        folder = folder,
                        text = text,
                        canManageFolders = hasPremiumAccess,
                        onRename = { folderToRename = folder },
                        onDelete = { folderToDelete = folder },
                    )
                }

                if (shouldShowSearchBar) {
                    SearchBar(
                        searchQuery = searchQuery,
                        text = text,
                        focusRequest = searchFocusRequest,
                        onSearchQueryChange = onSearchQueryChange,
                        onDismissSearch = ::closeSearch,
                    )
                }

                HomeHeaderSummaryRow(
                    resultCount = notes.size,
                    text = text,
                    appLanguage = appLanguage,
                    filtersExpanded = shouldShowFilterPanel,
                    showHomeRemindersButton = hasPremiumAccess && !isTrash,
                    isCalendarSelected = contentView == MainContentView.Calendar,
                    onOpenReminders = {
                        contentView = MainContentView.Calendar
                        clearNoteSelection()
                    },
                    onToggleFilters = { areFiltersExpanded = !areFiltersExpanded },
                )

                if (shouldShowFilterPanel) {
                    NoteFilterRow(
                        sortOption = sortOption,
                        quickFilter = quickFilter,
                        reminderFilter = reminderFilter,
                        contentView = contentView,
                        text = text,
                        isPrivacyLocked = isPrivacyLocked,
                        showCalendarView = !isTrash && hasPremiumAccess,
                        showReminderQuickFilter = hasPremiumAccess,
                        showReminderFilter = hasPremiumAccess,
                        onSortOptionChange = onSortOptionChange,
                        onQuickFilterChange = onQuickFilterChange,
                        onReminderFilterChange = onReminderFilterChange,
                        onContentViewChange = { view ->
                            if (view == MainContentView.Calendar && !hasPremiumAccess) {
                                onOpenPremium()
                            } else {
                                contentView = view
                                clearNoteSelection()
                            }
                        },
                    )
                }

                HorizontalDivider()
            }

            val toggleNoteSelection: (NoteEntity) -> Unit = { note ->
                selectedNoteIds = if (note.id in selectedNoteIds) {
                    selectedNoteIds - note.id
                } else {
                    selectedNoteIds + note.id
                }
            }
            val startNoteSelection: (NoteEntity) -> Unit = { note ->
                selectedNoteIds = selectedNoteIds + note.id
            }
            if (contentView == MainContentView.Calendar && !isTrash && hasPremiumAccess) {
                ReminderCalendarView(
                    notes = calendarNotes,
                    folders = folders,
                    text = text,
                    searchQuery = searchQuery,
                    appLanguage = appLanguage,
                    isPrivacyLocked = isPrivacyLocked,
                    selectedNoteIds = selectedNoteIds,
                    onOpenNote = onOpenNote,
                    onToggleNoteSelection = toggleNoteSelection,
                    onStartNoteSelection = startNoteSelection,
                    canMoveNote = { note -> hasPremiumAccess || note.folderId != DEFAULT_FOLDER_ID },
                    onMoveNote = { note ->
                        if (hasPremiumAccess || note.folderId != DEFAULT_FOLDER_ID) {
                            noteToMove = note
                        } else {
                            onOpenPremium()
                        }
                    },
                    onDeleteNote = { noteToDelete = it },
                    onTogglePinned = onTogglePinned,
                    onCalendarDateChange = ::clearNoteSelection,
                    onCreateReminderAt = onCreateReminderTextNote,
                    modifier = Modifier.weight(1f),
                )
            } else {
                NoteList(
                    notes = notes,
                    folders = folders,
                    text = text,
                    searchQuery = searchQuery,
                    hasActiveFilters = quickFilter != NoteQuickFilter.All || reminderFilter != ReminderFilter.All,
                    listMode = listMode,
                    appLanguage = appLanguage,
                    showReminderSummary = reminderFilter != ReminderFilter.All ||
                        quickFilter == NoteQuickFilter.HasReminder,
                    isPrivacyLocked = isPrivacyLocked,
                    selectedNoteIds = selectedNoteIds,
                    onOpenNote = onOpenNote,
                    onToggleNoteSelection = toggleNoteSelection,
                    onStartNoteSelection = startNoteSelection,
                    canMoveNote = { note -> hasPremiumAccess || note.folderId != DEFAULT_FOLDER_ID },
                    onMoveNote = { note ->
                        if (hasPremiumAccess || note.folderId != DEFAULT_FOLDER_ID) {
                            noteToMove = note
                        } else {
                            onOpenPremium()
                        }
                    },
                    onDeleteNote = { noteToDelete = it },
                    onRestoreNote = { note -> onRestoreNote(note.id) },
                    onPermanentlyDeleteNote = { noteToPermanentlyDelete = it },
                    onTogglePinned = onTogglePinned,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showCreateFolderDialog && !isPrivacyLocked) {
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

    if (!isPrivacyLocked) folderToRename?.let { folder ->
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

    if (!isPrivacyLocked) folderToDelete?.let { folder ->
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

    if (!isPrivacyLocked) noteToMove?.let { note ->
        MoveNoteDialog(
            folders = folders,
            text = text,
            currentFolderId = note.folderId,
            hasPremiumAccess = hasPremiumAccess,
            onDismiss = { noteToMove = null },
            onOpenPremium = {
                noteToMove = null
                onOpenPremium()
            },
            onMove = { folderId ->
                onMoveNote(note.id, folderId)
                noteToMove = null
            },
        )
    }

    if (!isPrivacyLocked) noteToDelete?.let { note ->
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

    if (!isPrivacyLocked) noteToPermanentlyDelete?.let { note ->
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

    if (!isPrivacyLocked) selectedNotesToDelete?.let { noteIds ->
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
    hideReminderNotificationContent: Boolean,
    requireDeviceUnlock: Boolean,
    deviceUnlockAvailable: Boolean,
    currentBackupPreview: BackupPreview,
    onlineSyncTargetUri: String?,
    onlineSyncAutoOnStart: Boolean,
    lastOnlineSyncAt: Long?,
    lastOnlineRestoreAt: Long?,
    syncMetadata: SyncMetadata,
    billingState: PremiumBillingState,
    debugPremiumToolsAvailable: Boolean,
    restoreRollbackCheckpoint: DecodedBackup?,
    isPrivacyLocked: Boolean,
    viewModel: NotepadViewModel,
    onEditorFontSizeChange: (EditorFontSize) -> Unit,
    onHideReminderNotificationContentChange: (Boolean) -> Unit,
    onRequireDeviceUnlockChange: (Boolean) -> Unit,
    onOnlineSyncTargetChange: (String?) -> Unit,
    onOnlineSyncAutoOnStartChange: (Boolean) -> Unit,
    onDebugPremiumOverrideChange: (Boolean) -> Unit,
    onOnlineSyncRecorded: () -> Unit,
    onOnlineRestoreRecorded: () -> Unit,
    onOnlineSyncDisconnect: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var pendingBatchExportZip by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestoreBackup by remember { mutableStateOf<PendingRestoreBackup?>(null) }
    var pendingRestoreRollback by remember { mutableStateOf<DecodedBackup?>(null) }
    var showAccountSettingsDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showGoogleSignOutDialog by remember { mutableStateOf(false) }
    var isBackupInProgress by remember { mutableStateOf(false) }
    var isRestoreInProgress by remember { mutableStateOf(false) }
    var isGoogleSyncInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) {
            pendingRestoreBackup = null
            pendingRestoreRollback = null
            pendingBatchExportZip = null
            showAccountSettingsDialog = false
            showDisconnectDialog = false
            showGoogleSignOutDialog = false
        }
    }

    suspend fun runGoogleSync() {
        isGoogleSyncInProgress = true
        try {
            when (val result = viewModel.syncGoogleDrive()) {
                is DriveSyncResult.Success -> Toast.makeText(context, text.syncComplete, Toast.LENGTH_SHORT).show()
                is DriveSyncResult.Failure -> Toast.makeText(
                    context,
                    result.error.message.ifBlank { text.syncFailed },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } finally {
            isGoogleSyncInProgress = false
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (viewModel.connectGoogleAccountFromIntent(result.data)) {
            scope.launch { runGoogleSync() }
        } else {
            Toast.makeText(context, text.syncFailed, Toast.LENGTH_SHORT).show()
        }
    }

    fun startGoogleSync() {
        if (syncMetadata.accountEmail == null) {
            googleSignInLauncher.launch(viewModel.googleSignInIntent())
        } else {
            scope.launch { runGoogleSync() }
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val backupJson = pendingBackupJson
        pendingBackupJson = null
        if (uri == null || backupJson == null) return@rememberLauncherForActivityResult

        scope.launch {
            isBackupInProgress = true
            try {
                persistUriPermission(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                withContext(Dispatchers.IO) {
                    writeTextToUri(context, uri, backupJson)
                }
                onOnlineSyncTargetChange(uri.toString())
                onOnlineSyncRecorded()
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
                persistUriPermission(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val decodedBackup = withContext(Dispatchers.IO) {
                    viewModel.decodeBackupJson(readTextFromUri(context, uri))
                }
                pendingRestoreBackup = PendingRestoreBackup(
                    data = decodedBackup.data,
                    preview = decodedBackup.preview,
                )
            } catch (_: Exception) {
                Toast.makeText(context, text.restoreFailed, Toast.LENGTH_SHORT).show()
            } finally {
                isRestoreInProgress = false
            }
        }
    }

    val batchExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val exportZip = pendingBatchExportZip
        pendingBatchExportZip = null
        if (uri == null || exportZip == null) return@rememberLauncherForActivityResult

        scope.launch {
            isBackupInProgress = true
            try {
                withContext(Dispatchers.IO) {
                    writeBytesToUri(context, uri, exportZip)
                }
                Toast.makeText(context, text.batchExportComplete, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, text.batchExportFailed, Toast.LENGTH_SHORT).show()
            } finally {
                isBackupInProgress = false
            }
        }
    }

    val batchTextImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        scope.launch {
            isRestoreInProgress = true
            try {
                val files = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        runCatching {
                            TextImportFile(
                                name = displayNameForUri(context, uri),
                                content = readTextFromUri(context, uri),
                            )
                        }.getOrNull()
                    }
                }
                val imported = viewModel.importTextFiles(files)
                if (imported > 0) {
                    Toast.makeText(context, text.batchImportComplete(imported), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, text.batchImportFailed, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, text.batchImportFailed, Toast.LENGTH_SHORT).show()
            } finally {
                isRestoreInProgress = false
            }
        }
    }

    fun syncNow() {
        scope.launch {
            try {
                val backupJson = viewModel.exportBackupJson()
                val target = onlineSyncTargetUri
                if (target == null) {
                    pendingBackupJson = backupJson
                    createBackupLauncher.launch(BACKUP_FILE_NAME)
                    return@launch
                }

                isBackupInProgress = true
                withContext(Dispatchers.IO) {
                    writeTextToUri(context, Uri.parse(target), backupJson)
                }
                onOnlineSyncRecorded()
                Toast.makeText(context, text.backupComplete, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, text.backupFailed, Toast.LENGTH_SHORT).show()
            } finally {
                isBackupInProgress = false
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
            if (debugPremiumToolsAvailable) {
                HorizontalDivider()
                Text(
                    text = developerToolsLabel(appLanguage),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("debug_premium_section"),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = debugPremiumOverrideLabel(appLanguage),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = debugPremiumOverrideBody(appLanguage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = billingState.debugPremiumOverride,
                        onCheckedChange = onDebugPremiumOverrideChange,
                        modifier = Modifier.testTag("debug_premium_switch"),
                    )
                }
            }
            HorizontalDivider()
            Text(
                text = text.privacy,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hide_reminder_content_row"),
            ) {
                Checkbox(
                    checked = hideReminderNotificationContent,
                    onCheckedChange = onHideReminderNotificationContentChange,
                    modifier = Modifier.testTag("hide_reminder_content_checkbox"),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text.hideReminderNotificationContent,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = text.hideReminderNotificationContentBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("require_device_unlock_row"),
            ) {
                Checkbox(
                    checked = requireDeviceUnlock,
                    onCheckedChange = { enabled ->
                        if (enabled && !deviceUnlockAvailable) {
                            Toast.makeText(context, text.deviceLockUnavailable, Toast.LENGTH_SHORT).show()
                        } else {
                            onRequireDeviceUnlockChange(enabled)
                        }
                    },
                    enabled = deviceUnlockAvailable || requireDeviceUnlock,
                    modifier = Modifier.testTag("require_device_unlock_checkbox"),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text.requireDeviceUnlock,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (deviceUnlockAvailable) text.requireDeviceUnlockBody else text.deviceLockUnavailable,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            Text(
                text = text.googleAccountSync,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("google_account_sync_title"),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "G",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = syncMetadata.accountEmail?.let(text::signedInAsAccount) ?: text.notSignedIn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("google_account_status"),
                    )
                    Text(
                        text = "${text.syncStatus}: ${syncMetadata.status.label(text)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = text.googleAccountSyncHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            syncMetadata.lastSyncedAt?.let { syncedAt ->
                Text(
                    text = "${text.lastSync}: ${formatTime(syncedAt, appLanguage)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("google_last_sync_status"),
                )
            }
            if (isGoogleSyncInProgress || syncMetadata.status == SyncStatus.Syncing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("google_sync_progress"),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = text.syncStatus,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            syncMetadata.lastError?.let { error ->
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("google_sync_error"),
                )
            }
            Button(
                onClick = { startGoogleSync() },
                enabled = !isGoogleSyncInProgress && !isBackupInProgress && !isRestoreInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_sync_button"),
            ) {
                Text(if (syncMetadata.accountEmail == null) text.signInWithGoogle else text.syncNow)
            }
            if (syncMetadata.accountEmail != null) {
                TextButton(
                    onClick = { showGoogleSignOutDialog = true },
                    enabled = !isGoogleSyncInProgress,
                    modifier = Modifier.testTag("google_sign_out_button"),
                ) {
                    Text(text.signOut)
                }
            }
            HorizontalDivider()
            Text(
                text = text.googleDriveBackup,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("online_sync_title"),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "B",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text.onlineSyncProvider,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (onlineSyncTargetUri == null) {
                            text.onlineSyncTargetMissing
                        } else {
                            text.onlineSyncTargetConnected
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("online_sync_target_status"),
                    )
                }
            }
            Text(
                text = text.backupTargetHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text.syncNotesCount(currentBackupPreview.noteCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("online_sync_note_count"),
            )
            if (onlineSyncTargetUri != null) lastOnlineSyncAt?.let { backedUpAt ->
                Text(
                    text = "${text.lastBackup}: ${formatTime(backedUpAt, appLanguage)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("last_backup_status"),
                )
            }
            lastOnlineRestoreAt?.let { restoredAt ->
                Text(
                    text = "${text.lastRestore}: ${formatTime(restoredAt, appLanguage)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("last_restore_status"),
                )
            }
            restoreRollbackCheckpoint?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_rollback_row"),
                ) {
                    Text(
                        text = text.restoreRollbackAvailable,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { pendingRestoreRollback = restoreRollbackCheckpoint },
                        enabled = !isBackupInProgress && !isRestoreInProgress,
                        modifier = Modifier.testTag("restore_rollback_button"),
                    ) {
                        Text(text.undoRestore)
                    }
                }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("online_sync_auto_row"),
            ) {
                Checkbox(
                    checked = onlineSyncAutoOnStart,
                    onCheckedChange = onOnlineSyncAutoOnStartChange,
                    modifier = Modifier.testTag("online_sync_auto_checkbox"),
                )
                Text(
                    text = text.autoSyncOnStart,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Button(
                onClick = { syncNow() },
                enabled = !isBackupInProgress && !isRestoreInProgress,
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
                enabled = !isBackupInProgress && !isRestoreInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restore_button"),
            ) {
                Text(text.restoreFromBackup)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
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
                    modifier = Modifier.testTag("choose_sync_file_button"),
                ) {
                    Text(if (onlineSyncTargetUri == null) text.chooseGoogleDriveSyncFile else text.changeGoogleDriveSyncFile)
                }
                TextButton(
                    onClick = { showAccountSettingsDialog = true },
                    modifier = Modifier.testTag("account_settings_button"),
                ) {
                    Text(text.accountSettings)
                }
            }
            if (onlineSyncTargetUri != null) {
                TextButton(
                    onClick = { showDisconnectDialog = true },
                    modifier = Modifier.testTag("disconnect_sync_button"),
                ) {
                    Text(text.disconnectSync)
                }
            }
            HorizontalDivider()
            Text(
                text = importExportTitleLabel(appLanguage),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("import_export_title"),
            )
            Text(
                text = importExportHintLabel(appLanguage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                pendingBatchExportZip = viewModel.exportBatchZip()
                                batchExportLauncher.launch(defaultBatchExportFileName())
                            } catch (_: Exception) {
                                Toast.makeText(context, text.batchExportFailed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isBackupInProgress && !isRestoreInProgress,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("batch_export_button"),
                ) {
                    Text(text.batchExportNotes)
                }
                Button(
                    onClick = {
                        batchTextImportLauncher.launch(
                            arrayOf("text/plain", "text/*", "*/*"),
                        )
                    },
                    enabled = !isBackupInProgress && !isRestoreInProgress,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("batch_import_button"),
                ) {
                    Text(text.batchImportTextFiles)
                }
            }
        }
    }

    if (!isPrivacyLocked) pendingRestoreBackup?.let { pendingRestore ->
        ConfirmDialog(
            title = text.restoreBackupConfirmTitle,
            body = restoreBackupPreviewBody(
                backupPreview = pendingRestore.preview,
                currentPreview = currentBackupPreview,
                text = text,
                appLanguage = appLanguage,
            ),
            confirmText = text.restoreFromBackup,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { pendingRestoreBackup = null },
            onConfirm = {
                pendingRestoreBackup = null
                scope.launch {
                    isRestoreInProgress = true
                    try {
                        viewModel.importBackupDataWithRollbackCheckpoint(pendingRestore.data)
                        onOnlineRestoreRecorded()
                        Toast.makeText(context, text.restoreComplete, Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, text.restoreFailed, Toast.LENGTH_SHORT).show()
                    } finally {
                        isRestoreInProgress = false
                    }
                }
            },
        )
    }

    if (!isPrivacyLocked) pendingRestoreRollback?.let { rollback ->
        ConfirmDialog(
            title = text.undoRestore,
            body = restoreRollbackPreviewBody(
                rollbackPreview = rollback.preview,
                currentPreview = currentBackupPreview,
                text = text,
                appLanguage = appLanguage,
            ),
            confirmText = text.undoRestore,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { pendingRestoreRollback = null },
            onConfirm = {
                pendingRestoreRollback = null
                scope.launch {
                    isRestoreInProgress = true
                    try {
                        viewModel.restoreRollbackCheckpoint()
                        onOnlineRestoreRecorded()
                        Toast.makeText(context, text.restoreRollbackComplete, Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, text.restoreRollbackFailed, Toast.LENGTH_SHORT).show()
                    } finally {
                        isRestoreInProgress = false
                    }
                }
            },
        )
    }

    if (showAccountSettingsDialog && !isPrivacyLocked) {
        AlertDialog(
            onDismissRequest = { showAccountSettingsDialog = false },
            title = { Text(text.accountSettings) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text.googleDriveHandledByFiles)
                    Text("${text.syncTarget}: ${onlineSyncTargetUri ?: text.onlineSyncTargetMissing}")
                    Text("${text.connectedDevices}: ${text.thisDevice} ${Build.MODEL.orEmpty()}".trim())
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountSettingsDialog = false }) {
                    Text(text.back)
                }
            },
        )
    }

    if (showDisconnectDialog && !isPrivacyLocked) {
        ConfirmDialog(
            title = text.disconnectSyncTitle,
            body = text.disconnectSyncBody,
            confirmText = text.disconnectSync,
            cancelText = text.cancel,
            destructive = true,
            onDismiss = { showDisconnectDialog = false },
            onConfirm = {
                showDisconnectDialog = false
                onOnlineSyncDisconnect()
            },
        )
    }

    if (showGoogleSignOutDialog && !isPrivacyLocked) {
        ConfirmDialog(
            title = text.signOutGoogleTitle,
            body = text.signOutGoogleBody,
            confirmText = text.signOut,
            cancelText = text.cancel,
            destructive = false,
            onDismiss = { showGoogleSignOutDialog = false },
            onConfirm = {
                showGoogleSignOutDialog = false
                viewModel.signOutGoogleAccount()
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
    reminderFilter: ReminderFilter,
    contentView: MainContentView,
    text: UiText,
    isPrivacyLocked: Boolean,
    showCalendarView: Boolean,
    showReminderQuickFilter: Boolean,
    showReminderFilter: Boolean,
    onSortOptionChange: (NoteSortOption) -> Unit,
    onQuickFilterChange: (NoteQuickFilter) -> Unit,
    onReminderFilterChange: (ReminderFilter) -> Unit,
    onContentViewChange: (MainContentView) -> Unit,
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
                isPrivacyLocked = isPrivacyLocked,
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
            NoteQuickFilter.entries
                .filter { filter -> showReminderQuickFilter || filter != NoteQuickFilter.HasReminder }
                .forEach { filter ->
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
        if (showReminderFilter) {
            ReminderFilterSelector(
                reminderFilter = reminderFilter,
                text = text,
                isPrivacyLocked = isPrivacyLocked,
                onReminderFilterChange = onReminderFilterChange,
            )
        }
        if (showCalendarView) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = contentView == MainContentView.List,
                        onClick = { onContentViewChange(MainContentView.List) },
                        label = { Text(text.listView) },
                        modifier = Modifier.testTag("list_view_chip"),
                    )
                }
                item {
                    FilterChip(
                        selected = contentView == MainContentView.Calendar,
                        onClick = { onContentViewChange(MainContentView.Calendar) },
                        label = { Text(text.calendarView) },
                        modifier = Modifier.testTag("calendar_view_chip"),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeaderSummaryRow(
    resultCount: Int,
    text: UiText,
    appLanguage: AppLanguage,
    filtersExpanded: Boolean,
    showHomeRemindersButton: Boolean,
    isCalendarSelected: Boolean,
    onOpenReminders: () -> Unit,
    onToggleFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.resultCount(resultCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .testTag("note_result_count"),
        )
        if (showHomeRemindersButton) {
            TextButton(
                onClick = onOpenReminders,
                enabled = !isCalendarSelected,
                modifier = Modifier.testTag("home_reminders_button"),
            ) {
                Text(text.calendarView)
            }
        }
        TextButton(
            onClick = onToggleFilters,
            modifier = Modifier.testTag("filter_panel_toggle"),
        ) {
            val label = if (filtersExpanded) {
                hideHomeFiltersLabel(appLanguage)
            } else {
                homeFiltersLabel(appLanguage)
            }
            Text(label)
        }
    }
}

@Composable
private fun SortSelector(
    sortOption: NoteSortOption,
    text: UiText,
    isPrivacyLocked: Boolean,
    onSortOptionChange: (NoteSortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) expanded = false
    }

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
            expanded = expanded && !isPrivacyLocked,
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
    isPrivacyLocked: Boolean,
    onTypeFilterChange: (NoteTypeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) expanded = false
    }

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
            expanded = expanded && !isPrivacyLocked,
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
    isPrivacyLocked: Boolean,
    onReminderFilterChange: (ReminderFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) expanded = false
    }

    Box {
        Button(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reminder_filter_selector"),
        ) {
            Text(
                "${text.reminderFilter}: ${reminderFilter.label(text)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded && !isPrivacyLocked,
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
    focusRequest: Int,
    onSearchQueryChange: (String) -> Unit,
    onDismissSearch: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) focusRequester.requestFocus()
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(text.searchPlaceholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { keyboardController?.hide() },
        ),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            TextButton(
                onClick = {
                    if (searchQuery.isNotEmpty()) {
                        onSearchQueryChange("")
                    } else {
                        onDismissSearch()
                    }
                },
            ) {
                Text(if (searchQuery.isNotEmpty()) text.clear else text.cancel)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .focusRequester(focusRequester)
            .testTag("note_search_input"),
    )
}

@Composable
private fun FolderFilterRow(
    folders: List<FolderEntity>,
    selectedFolderId: Long?,
    text: UiText,
    onSelectFolder: (Long?) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("folder_filter_row"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedFolderId == null,
                onClick = { onSelectFolder(null) },
                label = { Text(text.allNotes) },
                modifier = Modifier.testTag("folder_filter_all"),
            )
        }
        items(folders, key = { it.id }) { folder ->
            FilterChip(
                selected = selectedFolderId == folder.id,
                onClick = { onSelectFolder(folder.id) },
                modifier = Modifier.testTag("folder_filter_${folder.id}"),
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
    canManageFolders: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("folder_action_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = folderDisplayName(folder, text),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (canManageFolders && folder.id != DEFAULT_FOLDER_ID) {
            TextButton(
                onClick = onRename,
                modifier = Modifier.testTag("rename_folder_button"),
            ) {
                Text(text.rename)
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_folder_button"),
            ) {
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
    showReminderSummary: Boolean,
    isPrivacyLocked: Boolean,
    selectedNoteIds: Set<Long>,
    onOpenNote: (NoteEntity) -> Unit,
    onToggleNoteSelection: (NoteEntity) -> Unit,
    onStartNoteSelection: (NoteEntity) -> Unit,
    canMoveNote: (NoteEntity) -> Boolean,
    onMoveNote: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onRestoreNote: (NoteEntity) -> Unit,
    onPermanentlyDeleteNote: (NoteEntity) -> Unit,
    onTogglePinned: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelectionMode = selectedNoteIds.isNotEmpty()
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }

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
                nowMillis = nowMillis,
                isSelectionMode = isSelectionMode,
                isSelected = note.id in selectedNoteIds,
                showReminderSummary = showReminderSummary,
                isPrivacyLocked = isPrivacyLocked,
                onOpen = { onOpenNote(note) },
                onToggleSelection = { onToggleNoteSelection(note) },
                onStartSelection = { onStartNoteSelection(note) },
                canMove = canMoveNote(note),
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
private fun ReminderCalendarView(
    notes: List<NoteEntity>,
    folders: List<FolderEntity>,
    text: UiText,
    searchQuery: String,
    appLanguage: AppLanguage,
    isPrivacyLocked: Boolean,
    selectedNoteIds: Set<Long>,
    onOpenNote: (NoteEntity) -> Unit,
    onToggleNoteSelection: (NoteEntity) -> Unit,
    onStartNoteSelection: (NoteEntity) -> Unit,
    canMoveNote: (NoteEntity) -> Boolean,
    onMoveNote: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onTogglePinned: (NoteEntity) -> Unit,
    onCalendarDateChange: () -> Unit,
    onCreateReminderAt: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val submitReminderWithDeliveryCheck = rememberFutureReminderSubmissionGate(text)
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val todayStart = startOfDayMillis(nowMillis)
    var visibleMonthStart by remember { mutableStateOf(startOfMonthMillis(todayStart)) }
    var selectedDayStart by remember { mutableStateOf(todayStart) }
    val reminderNotes = remember(notes) {
        notes.filter { !it.isDeleted && it.reminderAt != null }
            .sortedBy { it.reminderAt }
    }
    val remindersByDay = remember(reminderNotes) {
        reminderNotes.groupBy { startOfDayMillis(it.reminderAt ?: 0L) }
    }
    val monthDays = remember(visibleMonthStart) { monthDayStarts(visibleMonthStart) }
    val selectedDayNotes = remember(remindersByDay, selectedDayStart) {
        remindersByDay[selectedDayStart].orEmpty().sortedBy { it.reminderAt }
    }
    val isSelectionMode = selectedNoteIds.isNotEmpty()
    val canAddReminderOnSelectedDay = remember(selectedDayStart, nowMillis, text) {
        calendarCanAddReminderOnDay(selectedDayStart, nowMillis, text)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("reminder_calendar"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text.reminderCalendar,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = {
                    val previousMonthStart = addMonths(visibleMonthStart, -1)
                    visibleMonthStart = previousMonthStart
                    selectedDayStart = previousMonthStart
                    onCalendarDateChange()
                },
                modifier = Modifier.testTag("calendar_previous_month"),
            ) {
                Text("<")
            }
            TextButton(
                onClick = {
                    visibleMonthStart = startOfMonthMillis(todayStart)
                    selectedDayStart = todayStart
                    onCalendarDateChange()
                },
                modifier = Modifier.testTag("calendar_today_button"),
            ) {
                Text(text.today)
            }
            TextButton(
                onClick = {
                    val nextMonthStart = addMonths(visibleMonthStart, 1)
                    visibleMonthStart = nextMonthStart
                    selectedDayStart = nextMonthStart
                    onCalendarDateChange()
                },
                modifier = Modifier.testTag("calendar_next_month"),
            ) {
                Text(">")
            }
        }

        Text(
            text = calendarMonthTitle(visibleMonthStart, appLanguage),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("calendar_month_title"),
        )

        Column(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                weekdayLabels(appLanguage).forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.width(48.dp),
                    )
                }
            }
            monthDays.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { dayStart ->
                        CalendarDayCell(
                            dayStart = dayStart,
                            todayStart = todayStart,
                            selectedDayStart = selectedDayStart,
                            reminderCount = dayStart?.let { remindersByDay[it]?.size } ?: 0,
                            onSelectDay = {
                                selectedDayStart = it
                                onCalendarDateChange()
                            },
                            modifier = Modifier.width(48.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    val previousDayStart = addDays(selectedDayStart, -1)
                    selectedDayStart = previousDayStart
                    visibleMonthStart = startOfMonthMillis(previousDayStart)
                    onCalendarDateChange()
                },
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = text.previousDayLabel() }
                    .testTag("calendar_previous_day"),
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                text = calendarDateTitle(selectedDayStart, appLanguage),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .testTag("calendar_selected_day_title"),
            )
            IconButton(
                onClick = {
                    val nextDayStart = addDays(selectedDayStart, 1)
                    selectedDayStart = nextDayStart
                    visibleMonthStart = startOfMonthMillis(nextDayStart)
                    onCalendarDateChange()
                },
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = text.nextDayLabel() }
                    .testTag("calendar_next_day"),
            ) {
                Icon(Icons.Filled.ArrowForward, contentDescription = null)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text.remindersOnDate(selectedDayNotes.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("calendar_selected_day_count"),
            )
            TextButton(
                onClick = {
                    selectedDayStart = todayStart
                    visibleMonthStart = startOfMonthMillis(todayStart)
                    onCalendarDateChange()
                },
                modifier = Modifier.testTag("calendar_selected_day_today"),
            ) {
                Text(text.today)
            }
        }
        Button(
            onClick = { showAddReminderDialog = true },
            enabled = canAddReminderOnSelectedDay,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("calendar_add_reminder"),
        ) {
            Text(text.addReminderLabel())
        }

        if (selectedDayNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text.noRemindersOnSelectedDay,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("calendar_empty_day"),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calendar_selected_day_notes"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                selectedDayNotes.forEach { note ->
                    NoteRow(
                        note = note,
                        folderName = folderDisplayNameById(note.folderId, folders, text),
                        text = text,
                        searchQuery = searchQuery,
                        appLanguage = appLanguage,
                        nowMillis = nowMillis,
                        isSelectionMode = isSelectionMode,
                        isSelected = note.id in selectedNoteIds,
                        showReminderSummary = true,
                        isPrivacyLocked = isPrivacyLocked,
                        onOpen = { onOpenNote(note) },
                        onToggleSelection = { onToggleNoteSelection(note) },
                        onStartSelection = { onStartNoteSelection(note) },
                        canMove = canMoveNote(note),
                        onMove = { onMoveNote(note) },
                        onDelete = { onDeleteNote(note) },
                        onRestore = {},
                        onPermanentlyDelete = {},
                        onTogglePinned = { onTogglePinned(note) },
                    )
                }
            }
        }
    }

    if (showAddReminderDialog) {
        CalendarReminderPresetDialog(
            dayStart = selectedDayStart,
            nowMillis = nowMillis,
            text = text,
            onDismiss = { showAddReminderDialog = false },
            onSelect = { reminderAt ->
                showAddReminderDialog = false
                submitReminderWithDeliveryCheck(reminderAt) {
                    onCreateReminderAt(reminderAt)
                }
            },
        )
    }
}

internal data class ReminderPresetOption(
    val label: String,
    val reminderAt: Long,
    val tag: String,
)

@Composable
private fun CalendarReminderPresetDialog(
    dayStart: Long,
    nowMillis: Long,
    text: UiText,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val options = remember(dayStart, nowMillis, text) {
        calendarReminderPresetOptions(dayStart, nowMillis, text)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.addReminderLabel()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    Button(
                        onClick = { onSelect(option.reminderAt) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(option.tag),
                    ) {
                        Text(option.label)
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
        modifier = Modifier.testTag("calendar_add_reminder_dialog"),
    )
}

internal fun calendarReminderPresetOptions(
    dayStart: Long,
    nowMillis: Long,
    text: UiText,
): List<ReminderPresetOption> {
    val presets = listOf(
        ReminderPresetOption(text.reminderPresetMorningLabel(), reminderTimeOnDay(dayStart, hour = 9), "calendar_preset_morning"),
        ReminderPresetOption(text.reminderPresetAfternoonLabel(), reminderTimeOnDay(dayStart, hour = 14), "calendar_preset_afternoon"),
        ReminderPresetOption(text.reminderPresetEveningLabel(), reminderTimeOnDay(dayStart, hour = 18), "calendar_preset_evening"),
    ).filter { it.reminderAt > nowMillis }
    if (presets.isNotEmpty()) return presets
    val fallback = nextHourReminderTime(nowMillis)
    return if (dayStart == startOfDayMillis(nowMillis) && startOfDayMillis(fallback) == dayStart) {
        listOf(
            ReminderPresetOption(
                label = text.reminderPresetNextHourLabel(),
                reminderAt = fallback,
                tag = "calendar_preset_next_hour",
            ),
        )
    } else {
        emptyList()
    }
}

internal fun calendarCanAddReminderOnDay(
    dayStart: Long,
    nowMillis: Long,
    text: UiText,
): Boolean {
    return dayStart >= startOfDayMillis(nowMillis) &&
        calendarReminderPresetOptions(dayStart, nowMillis, text).isNotEmpty()
}

@Composable
private fun CalendarDayCell(
    dayStart: Long?,
    todayStart: Long,
    selectedDayStart: Long,
    reminderCount: Int,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = dayStart != null && dayStart == selectedDayStart
    val isToday = dayStart != null && dayStart == todayStart
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = if (reminderCount > 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .then(
                if (dayStart == null) {
                    Modifier
                } else {
                    Modifier
                        .clickable { onSelectDay(dayStart) }
                        .testTag("calendar_day_${dayOfMonth(dayStart)}")
                },
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (dayStart != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dayOfMonth(dayStart).toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (reminderCount > 0) {
                    Text(
                        text = reminderCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("calendar_day_count_${dayOfMonth(dayStart)}"),
                    )
                } else {
                    Text(
                        text = " ",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
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
    nowMillis: Long,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showReminderSummary: Boolean,
    isPrivacyLocked: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onStartSelection: () -> Unit,
    canMove: Boolean,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentlyDelete: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    var rowMenuExpanded by remember(note.id) { mutableStateOf(false) }
    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) rowMenuExpanded = false
    }

    fun handleClick() {
        if (isSelectionMode) {
            onToggleSelection()
        } else if (!note.isDeleted) {
            onOpen()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = ::handleClick,
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
                Text(
                    text = highlightedText(
                        value = noteTitle(note, text),
                        query = searchQuery,
                        highlightColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = ::handleClick,
                            onLongClick = onStartSelection,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isSelectionMode) {
                    Box {
                        IconButton(
                            onClick = {
                                if (!isPrivacyLocked) rowMenuExpanded = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = text.noteOptions }
                                .testTag("note_more_${note.id}"),
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = rowMenuExpanded && !isPrivacyLocked,
                            onDismissRequest = { rowMenuExpanded = false },
                        ) {
                            if (note.isDeleted) {
                                DropdownMenuItem(
                                    text = { Text(text.restore) },
                                    modifier = Modifier.testTag("note_restore_${note.id}"),
                                    onClick = {
                                        rowMenuExpanded = false
                                        onRestore()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text.permanentlyDelete) },
                                    modifier = Modifier.testTag("note_permanent_delete_${note.id}"),
                                    onClick = {
                                        rowMenuExpanded = false
                                        onPermanentlyDelete()
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(if (note.isPinned) text.unpin else text.pin) },
                                    modifier = Modifier.testTag("pin_note_${note.id}"),
                                    onClick = {
                                        rowMenuExpanded = false
                                        onTogglePinned()
                                    },
                                )
                                if (canMove) {
                                    DropdownMenuItem(
                                        text = { Text(text.move) },
                                        modifier = Modifier.testTag("move_note_${note.id}"),
                                        onClick = {
                                            rowMenuExpanded = false
                                            onMove()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(text.moveToTrash) },
                                    modifier = Modifier.testTag("delete_note_${note.id}"),
                                    onClick = {
                                        rowMenuExpanded = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                }
            }
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
            if (note.type == NoteTypes.DRAWING) {
                val drawingStrokes = remember(note.id, note.drawingData) {
                    DrawingJson.decode(note.drawingData)
                }
                Spacer(Modifier.height(8.dp))
                DrawingNoteThumbnail(
                    noteId = note.id,
                    strokes = drawingStrokes,
                    isEmpty = drawingStrokes.isEmpty(),
                    text = text,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showReminderSummary) {
                reminderRowSummary(note, text, appLanguage)?.let { summary ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (note.reminderAt != null && note.reminderAt <= nowMillis) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("note_reminder_summary_${note.id}"),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = relativeUpdatedTime(note.updatedAt, nowMillis, appLanguage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("note_relative_updated_${note.id}"),
            )
        }
    }
}

@Composable
private fun DrawingNoteThumbnail(
    noteId: Long,
    strokes: List<DrawingStroke>,
    isEmpty: Boolean,
    text: UiText,
    modifier: Modifier = Modifier,
) {
    var thumbnailSize by remember(noteId, strokes) { mutableStateOf(IntSize.Zero) }
    val viewportScale = drawingViewportScale(strokes, thumbnailSize)
    Canvas(
        modifier = modifier
            .height(84.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .onSizeChanged { thumbnailSize = it }
            .semantics { contentDescription = text.drawing }
            .testTag(if (isEmpty) "empty_drawing_thumbnail_$noteId" else "drawing_note_thumbnail_$noteId"),
    ) {
        drawDrawingStrokes(strokes, viewportScale)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditorScreen(
    noteId: Long,
    isNewDraft: Boolean,
    folders: List<FolderEntity>,
    text: UiText,
    editorFontSize: EditorFontSize,
    appLanguage: AppLanguage,
    billingState: PremiumBillingState,
    isPrivacyLocked: Boolean,
    viewModel: NotepadViewModel,
    onOpenPremium: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val note by viewModel.observeNote(noteId).collectAsStateWithLifecycle(initialValue = null)
    val titleState = remember(noteId) { mutableStateOf("") }
    var title by titleState
    val contentFieldState = remember(noteId) { mutableStateOf(TextFieldValue("")) }
    var contentField by contentFieldState
    val formatRangesState = remember(noteId) { mutableStateOf<List<TextFormatRange>>(emptyList()) }
    var formatRanges by formatRangesState
    val latestTitleText = remember(noteId) { AtomicReference("") }
    val latestContentText = remember(noteId) { AtomicReference("") }
    var loadedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var modeInitializedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var isEditing by remember(noteId) { mutableStateOf(false) }
    var isFocusWriting by remember(noteId) { mutableStateOf(false) }
    var isContentFocused by remember(noteId) { mutableStateOf(false) }
    var isMetadataExpanded by remember(noteId) { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isMoreMenuExpanded by remember { mutableStateOf(false) }
    var showReadDetailsDialog by remember { mutableStateOf(false) }
    var isFindVisible by remember(noteId) { mutableStateOf(false) }
    var findQuery by remember(noteId) { mutableStateOf("") }
    var activeFindIndex by remember(noteId) { mutableStateOf(0) }
    var saveStatus by remember(noteId) { mutableStateOf(SaveStatus.Synced) }
    var isSavingAndLeaving by remember(noteId) { mutableStateOf(false) }
    var lastSavedAt by remember(noteId) { mutableStateOf<Long?>(null) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkDraft by remember { mutableStateOf("") }
    var pendingLinkRange by remember { mutableStateOf<IntRange?>(null) }
    var activeDatePickerDialog by remember { mutableStateOf<DatePickerDialog?>(null) }
    var activeTimePickerDialog by remember { mutableStateOf<TimePickerDialog?>(null) }
    var titleFocusRequest by remember(noteId) { mutableStateOf(0) }
    val context = LocalContext.current
    val submitReminderWithDeliveryCheck = rememberFutureReminderSubmissionGate(text)
    val requireReminderDeliveryReady = rememberReminderDeliveryGate(text)
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
    fun encodedTextFormattingJson(
        ranges: List<TextFormatRange> = formatRanges,
        textLength: Int = contentField.text.length,
    ): String {
        return TextFormattingJson.encode(TextFormattingJson.sanitize(ranges, textLength)).orEmpty()
    }

    val textFormattingJson = remember(formatRanges, content) {
        encodedTextFormattingJson(formatRanges, content.length)
    }
    val latestNote by rememberUpdatedState(note)
    val latestLoadedNoteId by rememberUpdatedState(loadedNoteId)
    val latestTitle by rememberUpdatedState(title)
    val latestContentField by rememberUpdatedState(contentField)
    val latestTextFormattingJson by rememberUpdatedState(textFormattingJson)
    val findMatches = remember(content, findQuery) { findInNoteMatches(content, findQuery) }
    val currentFindIndex = activeFindIndex.coerceIn(0, (findMatches.size - 1).coerceAtLeast(0))
    val parsedReadContentLines = remember(content) { readContentLines(content) }
    val renderCheckboxRows = content.isNotBlank() && parsedReadContentLines.any { it.checkbox != null }
    val readLineTextLayouts = remember(noteId, content, isEditing, renderCheckboxRows) {
        mutableStateMapOf<Int, TextLayoutResult>()
    }
    val readLineTextTops = remember(noteId, content, isEditing, renderCheckboxRows) {
        mutableStateMapOf<Int, Float>()
    }
    val readLineTextBottoms = remember(noteId, content, isEditing, renderCheckboxRows) {
        mutableStateMapOf<Int, Float>()
    }
    val readLineRowTops = remember(noteId, content, isEditing, renderCheckboxRows) {
        mutableStateMapOf<Int, Float>()
    }
    val readLineRowBottoms = remember(noteId, content, isEditing, renderCheckboxRows) {
        mutableStateMapOf<Int, Float>()
    }
    var readRowLayoutVersion by remember(noteId, content, isEditing, renderCheckboxRows) { mutableStateOf(0) }

    fun recordReadLineTextLayout(lineIndex: Int, layout: TextLayoutResult) {
        val previous = readLineTextLayouts[lineIndex]
        readLineTextLayouts[lineIndex] = layout
        if (
            previous == null ||
                previous.layoutInput.text != layout.layoutInput.text ||
                previous.size != layout.size
        ) {
            readRowLayoutVersion += 1
        }
    }

    fun recordReadLineBounds(
        lineIndex: Int,
        coordinates: LayoutCoordinates,
        tops: MutableMap<Int, Float>,
        bottoms: MutableMap<Int, Float>,
    ) {
        val top = (
            coordinates.positionInRoot().y -
                readViewportTopInRoot +
                readScrollState.value
            ).roundToInt().toFloat()
        val bottom = top + coordinates.size.height
        if (tops[lineIndex] != top || bottoms[lineIndex] != bottom) {
            tops[lineIndex] = top
            bottoms[lineIndex] = bottom
            readRowLayoutVersion += 1
        }
    }

    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) {
            isMoreMenuExpanded = false
            showReadDetailsDialog = false
            showDeleteDialog = false
            showLinkDialog = false
            pendingLinkRange = null
            activeDatePickerDialog?.dismiss()
            activeTimePickerDialog?.dismiss()
            activeDatePickerDialog = null
            activeTimePickerDialog = null
        }
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
        formatRanges = TextFormattingJson.decode(loaded.textFormattingJson, loaded.textContent.orEmpty().length)
        latestTitleText.set(loaded.title)
        latestContentText.set(loaded.textContent.orEmpty())
        loadedNoteId = loaded.id
        lastSavedAt = loaded.updatedAt
        saveStatus = SaveStatus.Synced
        if (modeInitializedNoteId != loaded.id) {
            val isBlankLoadedTextNote = loaded.title.isBlank() && loaded.textContent.orEmpty().isBlank()
            isEditing = isBlankLoadedTextNote
            isFocusWriting = isNewDraft && isBlankLoadedTextNote
            isMetadataExpanded = isNewDraft && loaded.reminderAt != null
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
            if (isFocusWriting) {
                contentFocusRequester.requestFocus()
            } else {
                titleFocusRequester.requestFocus()
            }
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

    LaunchedEffect(noteId, loadedNoteId, isEditing, title, content, textFormattingJson) {
        if (loadedNoteId == noteId && isEditing) {
            val current = note ?: return@LaunchedEffect
            if (
                title == current.title &&
                content == current.textContent.orEmpty() &&
                textFormattingJson == current.textFormattingJson.orEmpty()
            ) {
                saveStatus = SaveStatus.Synced
                return@LaunchedEffect
            }
            val pendingVersion = autoSaveVersion.get()
            saveStatus = SaveStatus.Saving
            delay(500)
            if (pendingVersion != autoSaveVersion.get()) return@LaunchedEffect
            val savedAt = try {
                viewModel.saveTextNoteNow(noteId, title, content, textFormattingJson)
            } catch (_: Exception) {
                null
            }
            if (savedAt == null) {
                saveStatus = SaveStatus.Failed
            } else {
                lastSavedAt = savedAt
                saveStatus = SaveStatus.Saved
            }
        }
    }

    fun hasUnsavedTextNote(
        currentNote: NoteEntity? = note,
        titleValue: String = title,
        contentValue: String = content,
        formattingValue: String = encodedTextFormattingJson(textLength = contentValue.length),
        loadedId: Long? = loadedNoteId,
    ): Boolean {
        val current = currentNote ?: return false
        return loadedId == noteId &&
            (
                titleValue != current.title ||
                    contentValue != current.textContent.orEmpty() ||
                    formattingValue != current.textFormattingJson.orEmpty()
                )
    }

    fun isBlankDraftContent(
        titleValue: String = title,
        contentValue: String = contentField.text,
        formattingValue: String = encodedTextFormattingJson(textLength = contentValue.length),
    ): Boolean {
        return isBlankDraftValues(titleValue, contentValue, formattingValue)
    }

    fun savePendingTextNote() {
        val titleToSave = latestTitleText.get()
        val contentToSave = latestContentText.get()
        val formattingToSave = encodedTextFormattingJson(
            ranges = formatRangesState.value,
            textLength = contentToSave.length,
        )
        if (isNewDraft && isBlankDraftContent(titleToSave, contentToSave, formattingToSave)) {
            autoSaveVersion.incrementAndGet()
            viewModel.discardNewTextDraftIfBlank(noteId, titleToSave, contentToSave, formattingToSave)
            return
        }
        if (hasUnsavedTextNote(latestNote, titleToSave, contentToSave, formattingToSave, latestLoadedNoteId)) {
            viewModel.saveTextNote(noteId, titleToSave, contentToSave, formattingToSave)
        }
    }

    suspend fun saveTextNoteNowOrFail(
        titleValue: String,
        contentValue: String,
        formattingValue: String,
    ): Long? {
        return try {
            viewModel.saveTextNoteNow(noteId, titleValue, contentValue, formattingValue)
        } catch (_: Exception) {
            null
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
        val titleToSave = latestTitleText.get()
        val contentToSave = latestContentText.get()
        val formattingToSave = encodedTextFormattingJson(
            ranges = formatRangesState.value,
            textLength = contentToSave.length,
        )
        keyboardController?.hide()
        scope.launch {
            if (isNewDraft && isBlankDraftContent(titleToSave, contentToSave, formattingToSave)) {
                val deleted = viewModel.discardNewTextDraftIfBlankNow(
                    noteId,
                    titleToSave,
                    contentToSave,
                    formattingToSave,
                )
                if (deleted) {
                    onBack()
                } else {
                    saveStatus = SaveStatus.Failed
                    isSavingAndLeaving = false
                }
                return@launch
            }
            saveStatus = SaveStatus.Saving
            val savedAt = saveTextNoteNowOrFail(titleToSave, contentToSave, formattingToSave)
            if (savedAt == null) {
                saveStatus = SaveStatus.Failed
                isSavingAndLeaving = false
            } else {
                lastSavedAt = savedAt
                saveStatus = SaveStatus.Saved
                onBack()
            }
        }
    }

    fun retrySaveTextNote() {
        autoSaveVersion.incrementAndGet()
        val titleToSave = latestTitleText.get()
        val contentToSave = latestContentText.get()
        val formattingToSave = encodedTextFormattingJson(
            ranges = formatRangesState.value,
            textLength = contentToSave.length,
        )
        scope.launch {
            saveStatus = SaveStatus.Saving
            val savedAt = saveTextNoteNowOrFail(titleToSave, contentToSave, formattingToSave)
            if (savedAt == null) {
                saveStatus = SaveStatus.Failed
            } else {
                lastSavedAt = savedAt
                saveStatus = SaveStatus.Saved
            }
        }
    }

    LaunchedEffect(noteId, content, isEditing, renderCheckboxRows) {
        if (renderCheckboxRows || isEditing) {
            readContentLayout = null
        }
        readRowLayoutVersion += 1
    }

    suspend fun scrollReadRowMatchIntoView(matchRange: IntRange?) {
        val target = readContentMatchTargetForRange(parsedReadContentLines, matchRange) ?: return
        val rowTop = readLineRowTops[target.lineIndex] ?: readLineTextTops[target.lineIndex] ?: return
        val rowBottom = readLineRowBottoms[target.lineIndex] ?: readLineTextBottoms[target.lineIndex] ?: rowTop
        val matchTop: Float
        val matchBottom: Float
        if (target.hasVisibleText) {
            val layout = readLineTextLayouts[target.lineIndex] ?: return
            val textTop = readLineTextTops[target.lineIndex] ?: return
            val textLength = layout.layoutInput.text.text.length
            if (textLength <= 0) return
            val startOffset = target.localStart.coerceIn(0, textLength - 1)
            val endOffset = (target.localEndExclusive - 1).coerceIn(startOffset, textLength - 1)
            val startBox = layout.getBoundingBox(startOffset)
            val endBox = layout.getBoundingBox(endOffset)
            matchTop = textTop + startBox.top
            matchBottom = textTop + endBox.bottom
        } else {
            matchTop = rowTop
            matchBottom = rowBottom
        }
        val scrollTarget = findMatchScrollTarget(
            currentScroll = readScrollState.value,
            viewportHeight = readViewportHeight,
            matchTop = matchTop,
            matchBottom = matchBottom,
            maxScroll = readScrollState.maxValue,
        ) ?: return
        readScrollState.scrollTo(scrollTarget)
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
                if (renderCheckboxRows) {
                    scrollReadRowMatchIntoView(range)
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
        readRowLayoutVersion,
        renderCheckboxRows,
        readViewportHeight,
        readContentTopInScroll,
        readScrollState.maxValue,
    ) {
        if (isEditing || !isFindVisible) return@LaunchedEffect
        if (renderCheckboxRows) {
            scrollReadRowMatchIntoView(findMatches.getOrNull(currentFindIndex))
        } else {
            readScrollState.scrollMatchIntoView(
                textLayoutResult = readContentLayout,
                matchRange = findMatches.getOrNull(currentFindIndex),
                viewportHeight = readViewportHeight,
                contentTopPx = readContentTopInScroll,
            )
        }
    }

    fun saveCurrentTextNoteThen(onSaved: (NoteEntity) -> Unit) {
        val currentNote = latestNote ?: return
        val titleToSave = latestTitleText.get()
        val contentToSave = latestContentText.get()
        val formattingToSave = encodedTextFormattingJson(
            ranges = formatRangesState.value,
            textLength = contentToSave.length,
        )
        autoSaveVersion.incrementAndGet()
        scope.launch {
            saveStatus = SaveStatus.Saving
            val savedAt = saveTextNoteNowOrFail(titleToSave, contentToSave, formattingToSave)
            if (savedAt == null) {
                saveStatus = SaveStatus.Failed
                return@launch
            }
            lastSavedAt = savedAt
            saveStatus = SaveStatus.Saved
            onSaved(
                currentNote.copy(
                    title = titleToSave,
                    textContent = contentToSave,
                    drawingData = null,
                    textFormattingJson = formattingToSave.takeIf { it.isNotBlank() },
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

    fun openPremiumAfterSavingTextNote() {
        if (note == null) {
            onOpenPremium()
            return
        }
        saveCurrentTextNoteThen {
            onOpenPremium()
        }
    }

    fun toggleReadModeCheckbox(lineIndex: Int) {
        val currentContent = contentField.text
        val updatedContent = toggleMarkdownCheckboxLine(currentContent, lineIndex) ?: return
        autoSaveVersion.incrementAndGet()
        contentField = contentField.copy(text = updatedContent)
        latestContentText.set(updatedContent)
        scope.launch {
            saveStatus = SaveStatus.Saving
            val formattingToSave = encodedTextFormattingJson(textLength = updatedContent.length)
            val savedAt = saveTextNoteNowOrFail(title, updatedContent, formattingToSave)
            if (savedAt == null) {
                saveStatus = SaveStatus.Failed
            } else {
                lastSavedAt = savedAt
                saveStatus = SaveStatus.Saved
            }
        }
    }

    fun submitReminder(reminderAt: Long) {
        submitReminderWithDeliveryCheck(reminderAt) {
            viewModel.setNoteReminder(
                noteId = noteId,
                reminderAt = reminderAt,
                reminderRepeat = note?.reminderRepeat ?: ReminderRepeat.None.code,
            )
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

        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val timePickerDialog = TimePickerDialog(
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
                )
                timePickerDialog.setOnDismissListener {
                    if (activeTimePickerDialog === timePickerDialog) activeTimePickerDialog = null
                }
                activeTimePickerDialog = timePickerDialog
                timePickerDialog.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        datePickerDialog.setOnDismissListener {
            if (activeDatePickerDialog === datePickerDialog) activeDatePickerDialog = null
        }
        activeDatePickerDialog = datePickerDialog
        datePickerDialog.show()
    }

    fun insertIntoContent(prefix: String) {
        isFocusWriting = true
        isMetadataExpanded = false
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
        formatRanges = adjustTextFormattingAfterEdit(
            ranges = formatRanges,
            oldText = currentContent,
            newText = updatedContent,
        )
        contentField = TextFieldValue(
            text = updatedContent,
            selection = TextRange(start + prefix.length),
        )
        latestContentText.set(updatedContent)
        contentFocusRequester.requestFocus()
        keyboardController?.show()
    }

    fun removeEmptyListMarkerBeforeCursor(): Boolean {
        val currentContent = contentField.text
        val selection = contentField.selection
        if (!selection.collapsed) return false
        val cursor = selection.start.coerceIn(0, currentContent.length)
        val lineStart = if (cursor <= 0) {
            0
        } else {
            currentContent.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
        }
        val linePrefix = currentContent.substring(lineStart, cursor)
        if (linePrefix != "- " && linePrefix != "- [ ] " && linePrefix != "- [x] " && linePrefix != "- [X] ") {
            return false
        }
        val updatedContent = currentContent.removeRange(lineStart, cursor)
        autoSaveVersion.incrementAndGet()
        formatRanges = adjustTextFormattingAfterEdit(
            ranges = formatRanges,
            oldText = currentContent,
            newText = updatedContent,
        )
        contentField = TextFieldValue(
            text = updatedContent,
            selection = TextRange(lineStart),
        )
        latestContentText.set(updatedContent)
        return true
    }

    fun requirePremiumFormatting(): Boolean {
        if (billingState.hasPremiumAccess) return true
        Toast.makeText(context, formattingPremiumRequiredLabel(appLanguage), Toast.LENGTH_SHORT).show()
        openPremiumAfterSavingTextNote()
        return false
    }

    fun selectedRangeForFormatting(preferLine: Boolean = false): IntRange? {
        val selectedRange = selectedTextRange(
            selectionStart = contentField.selection.start,
            selectionEnd = contentField.selection.end,
            textLength = contentField.text.length,
        )
        return when {
            selectedRange != null -> selectedRange
            preferLine -> currentLineRange(contentField.text, contentField.selection.max)
            else -> currentWordRange(contentField.text, contentField.selection.max)
        }
    }

    fun applyFormatting(type: TextFormatType) {
        if (!requirePremiumFormatting()) return
        val range = selectedRangeForFormatting(preferLine = type == TextFormatType.Heading1 || type == TextFormatType.Heading2)
        if (range == null) {
            Toast.makeText(context, selectTextToFormatLabel(appLanguage), Toast.LENGTH_SHORT).show()
            return
        }
        autoSaveVersion.incrementAndGet()
        formatRanges = TextFormattingJson.toggle(
            ranges = formatRanges,
            range = range,
            type = type,
            textLength = contentField.text.length,
        )
        contentFocusRequester.requestFocus()
    }

    fun clearFormatting() {
        if (!requirePremiumFormatting()) return
        val range = selectedRangeForFormatting(preferLine = true)
        if (range == null) {
            Toast.makeText(context, selectTextToFormatLabel(appLanguage), Toast.LENGTH_SHORT).show()
            return
        }
        autoSaveVersion.incrementAndGet()
        formatRanges = TextFormattingJson.clear(formatRanges, range, contentField.text.length)
        contentFocusRequester.requestFocus()
    }

    fun prepareLinkFormatting() {
        if (!requirePremiumFormatting()) return
        val range = selectedRangeForFormatting()
        if (range == null) {
            Toast.makeText(context, selectTextToFormatLabel(appLanguage), Toast.LENGTH_SHORT).show()
            return
        }
        linkDraft = ""
        pendingLinkRange = range
        showLinkDialog = true
    }

    fun applyLinkFormatting(url: String) {
        val normalizedUrl = normalizedFormatUrl(url)
        val range = pendingLinkRange ?: selectedRangeForFormatting()
        if (normalizedUrl == null || range == null) {
            Toast.makeText(context, selectTextToFormatLabel(appLanguage), Toast.LENGTH_SHORT).show()
            return
        }
        autoSaveVersion.incrementAndGet()
        formatRanges = TextFormattingJson.toggle(
            ranges = formatRanges,
            range = range,
            type = TextFormatType.Link,
            textLength = contentField.text.length,
            url = normalizedUrl,
        )
        showLinkDialog = false
        pendingLinkRange = null
        contentFocusRequester.requestFocus()
    }

    fun editTitleFromReadMode() {
        isFocusWriting = false
        isMetadataExpanded = true
        isEditing = true
        titleFocusRequest += 1
    }

    fun focusTitleFromEditor() {
        isFocusWriting = false
        isContentFocused = false
        isMetadataExpanded = true
        titleFocusRequest += 1
    }

    fun editContentFromReadModeAtOffset(offset: Int?) {
        if (offset != null) {
            contentField = contentField.copy(
                selection = TextRange(offset.coerceIn(0, contentField.text.length)),
            )
        }
        isFocusWriting = true
        isMetadataExpanded = false
        isEditing = true
    }

    fun editContentFromReadMode(tapOffset: Offset? = null) {
        editContentFromReadModeAtOffset(tapOffset?.let { readContentLayout?.getOffsetForPosition(it) })
    }

    LaunchedEffect(isEditing, isFocusWriting, titleFocusRequest) {
        if (isEditing && loadedNoteId == noteId) {
            if (isFocusWriting) {
                contentFocusRequester.requestFocus()
            } else {
                titleFocusRequester.requestFocus()
            }
            keyboardController?.show()
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
    val bodyDerivedDisplayTitle = remember(content) { firstTextContentTitle(content) }
    val currentDisplayTitle = remember(title, bodyDerivedDisplayTitle, currentNote?.type) {
        if (currentNote?.type == NoteTypes.TEXT) {
            title.ifBlank { bodyDerivedDisplayTitle ?: text.untitledTextNote }
        } else {
            title.ifBlank { text.untitledTextNote }
        }
    }
    val usesBodyDerivedTitle = currentNote?.type == NoteTypes.TEXT &&
        title.isBlank() &&
        bodyDerivedDisplayTitle != null
    val isBlankStandardNewTextDraft = currentNote?.type == NoteTypes.TEXT &&
        isNewDraft &&
        currentNote.reminderAt == null &&
        isBlankDraftContent() &&
        saveStatus != SaveStatus.Failed
    val isCompactEditor = isEditing && (isFocusWriting || isContentFocused)
    val findActionLabel = findInNoteActionLabel(appLanguage)

    BackHandler(onBack = ::saveAndBack)

    Scaffold(
        containerColor = NOTE_PAPER_BACKGROUND,
        topBar = {
            TopAppBar(
                title = {
                    if (isEditing) {
                        if (!isBlankStandardNewTextDraft) {
                            Column {
                                Text(
                                    currentDisplayTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    saveStatus.label(text, appLanguage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("text_note_top_save_status"),
                                )
                            }
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
                        Text(if (isEditing) doneLabel(appLanguage) else text.back)
                    }
                },
                actions = {
                    if (currentNote != null && !isEditing) {
                        TextButton(
                            onClick = { editContentFromReadMode() },
                            modifier = Modifier.testTag("edit_note_button"),
                        ) {
                            Text(text.edit)
                        }
                    }
                    IconButton(
                        onClick = {
                            isMoreMenuExpanded = false
                            isFindVisible = true
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = text.findInNote }
                            .testTag("find_in_note_button"),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                    Box {
                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                isMoreMenuExpanded = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = text.more }
                                .testTag("more_note_button"),
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = isMoreMenuExpanded && !isPrivacyLocked,
                            onDismissRequest = { isMoreMenuExpanded = false },
                            modifier = Modifier.testTag("text_note_overflow_menu"),
                        ) {
                            currentNote?.let { loaded ->
                                if (isEditing) {
                                    DropdownMenuItem(
                                        text = { Text(text.details) },
                                        modifier = Modifier.testTag("text_note_edit_details_menu_item"),
                                        onClick = {
                                            isMoreMenuExpanded = false
                                            focusTitleFromEditor()
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(text.details) },
                                        modifier = Modifier.testTag("text_note_details_menu_item"),
                                        onClick = {
                                            isMoreMenuExpanded = false
                                            showReadDetailsDialog = true
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(text.findInNote) },
                                    modifier = Modifier.testTag("find_in_note_menu_item"),
                                    onClick = {
                                        isMoreMenuExpanded = false
                                        isFindVisible = true
                                    },
                                )
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
                                    text = { Text(setReminderActionLabel(text, billingState.hasPremiumAccess)) },
                                    modifier = Modifier.testTag("set_reminder_menu_item"),
                                    onClick = {
                                        isMoreMenuExpanded = false
                                        if (billingState.hasPremiumAccess) {
                                            openDateTimePicker(loaded.reminderAt)
                                        } else {
                                            openPremiumAfterSavingTextNote()
                                        }
                                    },
                                )
                                if (loaded.reminderAt != null) {
                                    if (billingState.hasPremiumAccess) ReminderRepeat.entries.forEach { repeat ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(text.reminderRepeat + ": " + reminderRepeatLabel(repeat.code, text))
                                            },
                                            modifier = Modifier.testTag("text_reminder_repeat_" + repeat.name),
                                            onClick = {
                                                isMoreMenuExpanded = false
                                                if (billingState.hasPremiumAccess) {
                                                    requireReminderDeliveryReady {
                                                        viewModel.setNoteReminder(noteId, loaded.reminderAt, repeat.code)
                                                    }
                                                } else {
                                                    openPremiumAfterSavingTextNote()
                                                }
                                            },
                                        )
                                    }
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
                if (isCompactEditor && !isMetadataExpanded && !isBlankStandardNewTextDraft) {
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
                                text = currentDisplayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clickable { focusTitleFromEditor() }
                                    .testTag("text_note_compact_title"),
                            )
                            Text(
                                text = saveStatus.label(text, appLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("text_note_save_status"),
                            )
                        }
                        if (saveStatus == SaveStatus.Failed) {
                            TextButton(
                                onClick = ::retrySaveTextNote,
                                modifier = Modifier.testTag("text_note_retry_save_button"),
                            ) {
                                Text(retryLabel(appLanguage), maxLines = 1)
                            }
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
                                    latestTitleText.set(it)
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
                                    isPrivacyLocked = isPrivacyLocked,
                                    hasPremiumAccess = billingState.hasPremiumAccess,
                                    onOpenPremium = ::openPremiumAfterSavingTextNote,
                                    onMove = { folderId -> viewModel.moveNote(noteId, folderId) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = saveStatus.label(text, appLanguage),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("text_note_save_status"),
                                    )
                                    if (saveStatus == SaveStatus.Failed) {
                                        TextButton(
                                            onClick = ::retrySaveTextNote,
                                            modifier = Modifier.testTag("text_note_retry_save_metadata_button"),
                                        ) {
                                            Text(retryLabel(appLanguage), maxLines = 1)
                                        }
                                    }
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
                                isPrivacyLocked = isPrivacyLocked,
                                hasPremiumAccess = billingState.hasPremiumAccess,
                                onOpenPremium = ::openPremiumAfterSavingTextNote,
                                onSetReminder = { reminderAt, repeat ->
                                    viewModel.setNoteReminder(noteId, reminderAt, repeat)
                                },
                                onClearReminder = { viewModel.setNoteReminder(noteId, null) },
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
                        onValueChange = { nextValue ->
                            val adjustedNextValue = continuedListValue(contentField, nextValue)
                            autoSaveVersion.incrementAndGet()
                            formatRanges = adjustTextFormattingAfterEdit(
                                ranges = formatRanges,
                                oldText = contentField.text,
                                newText = adjustedNextValue.text,
                            )
                            contentField = adjustedNextValue
                            latestContentText.set(adjustedNextValue.text)
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
                            formattingRanges = formatRanges,
                            linkColor = MaterialTheme.colorScheme.primary,
                            formatHighlightColor = Color(0xFFFFF59D),
                            matchColor = MaterialTheme.colorScheme.tertiaryContainer,
                            activeMatchColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        onTextLayout = { editContentLayout = it },
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 72.dp)
                            .onPreviewKeyEvent { event ->
                                event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Backspace &&
                                    removeEmptyListMarkerBeforeCursor()
                            }
                            .focusRequester(contentFocusRequester)
                            .onFocusChanged { isContentFocused = it.isFocused }
                            .testTag("text_note_content"),
                    )
                }
                if (isCompactEditor && !isBlankStandardNewTextDraft) {
                    TextEditorAccessoryBar(
                        text = text,
                        appLanguage = appLanguage,
                        hasPremiumAccess = billingState.hasPremiumAccess,
                        onInsertCheckbox = { insertIntoContent("- [ ] ") },
                        onInsertBullet = { insertIntoContent("- ") },
                        onOpenPremium = ::openPremiumAfterSavingTextNote,
                        onHeading1 = { applyFormatting(TextFormatType.Heading1) },
                        onHeading2 = { applyFormatting(TextFormatType.Heading2) },
                        onBold = { applyFormatting(TextFormatType.Bold) },
                        onItalic = { applyFormatting(TextFormatType.Italic) },
                        onUnderline = { applyFormatting(TextFormatType.Underline) },
                        onHighlight = { applyFormatting(TextFormatType.Highlight) },
                        onLink = ::prepareLinkFormatting,
                        onClearFormatting = ::clearFormatting,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NOTE_PAPER_SURFACE, RoundedCornerShape(8.dp))
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                            if (!usesBodyDerivedTitle) {
                                Text(
                                    text = currentDisplayTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .clickable { editTitleFromReadMode() }
                                        .testTag("text_note_read_title"),
                                )
                            }
                            if (saveStatus == SaveStatus.Failed) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = saveStatus.label(text, appLanguage),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("text_note_read_save_status"),
                                    )
                                    TextButton(
                                        onClick = ::retrySaveTextNote,
                                        modifier = Modifier.testTag("text_note_read_retry_save_button"),
                                    ) {
                                        Text(retryLabel(appLanguage), maxLines = 1)
                                    }
                                }
                            }
                            val readReminderAt = currentNote.reminderAt
                            if (readReminderAt != null) {
                                Text(
                                    text = reminderStatus(readReminderAt, text, appLanguage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (readReminderAt <= System.currentTimeMillis()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.testTag("note_reminder_status"),
                                )
                            }
                            if (saveStatus == SaveStatus.Failed || readReminderAt != null) {
                                HorizontalDivider()
                            }
                            val readBodyStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = editorFontSize.fontSizeSp.sp,
                                lineHeight = (editorFontSize.fontSizeSp + 10).sp,
                            )
                            val readMatchColor = MaterialTheme.colorScheme.tertiaryContainer
                            val readActiveMatchColor = MaterialTheme.colorScheme.primaryContainer
                            val readLinkColor = MaterialTheme.colorScheme.primary
                            val readFormatHighlightColor = Color(0xFFFFF59D)

                            fun handleReadSegmentTap(
                                line: ReadContentLine,
                                annotatedText: AnnotatedString,
                                tapOffset: Offset,
                            ) {
                                val localOffset = readLineTextLayouts[line.lineIndex]?.getOffsetForPosition(tapOffset)
                                val tappedUrl = localOffset?.let(annotatedText::webUrlAt)
                                if (tappedUrl != null && !openWebUrl(context, tappedUrl)) {
                                    Toast.makeText(context, openLinkFailedLabel(appLanguage), Toast.LENGTH_SHORT).show()
                                } else if (tappedUrl == null) {
                                    editContentFromReadModeAtOffset(line.rawOffsetForLocalOffset(localOffset ?: 0))
                                }
                            }

                            fun readSegmentText(line: ReadContentLine): AnnotatedString {
                                return findHighlightedLinkedTextSegment(
                                    value = line.displayText,
                                    absoluteStart = line.displayStart,
                                    absoluteEndExclusive = line.endExclusive,
                                    contentLength = content.length,
                                    globalMatches = findMatches,
                                    activeMatchIndex = currentFindIndex,
                                    formattingRanges = formatRanges,
                                    matchColor = readMatchColor,
                                    activeMatchColor = readActiveMatchColor,
                                    formatHighlightColor = readFormatHighlightColor,
                                    linkColor = readLinkColor,
                                    linkifyUrls = true,
                                )
                            }

                            if (renderCheckboxRows) {
                                Column(
                                    modifier = Modifier
                                        .onGloballyPositioned { coordinates ->
                                            readContentTopInScroll =
                                                coordinates.positionInRoot().y -
                                                    readViewportTopInRoot +
                                                    readScrollState.value
                                        }
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("text_note_read_content"),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    parsedReadContentLines.forEach { line ->
                                        val checkbox = line.checkbox
                                        val annotatedLineText = readSegmentText(line)
                                        val layoutText = if (annotatedLineText.length == 0) {
                                            AnnotatedString(" ")
                                        } else {
                                            annotatedLineText
                                        }
                                        if (checkbox == null) {
                                            Text(
                                                text = layoutText,
                                                style = readBodyStyle,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                onTextLayout = { recordReadLineTextLayout(line.lineIndex, it) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .pointerInput(line.lineIndex, annotatedLineText, contentField.text) {
                                                        detectTapGestures { tapOffset ->
                                                            handleReadSegmentTap(line, annotatedLineText, tapOffset)
                                                        }
                                                    }
                                                    .onGloballyPositioned { coordinates ->
                                                        recordReadLineBounds(
                                                            line.lineIndex,
                                                            coordinates,
                                                            readLineTextTops,
                                                            readLineTextBottoms,
                                                        )
                                                        recordReadLineBounds(
                                                            line.lineIndex,
                                                            coordinates,
                                                            readLineRowTops,
                                                            readLineRowBottoms,
                                                        )
                                                    },
                                            )
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onGloballyPositioned { coordinates ->
                                                        recordReadLineBounds(
                                                            line.lineIndex,
                                                            coordinates,
                                                            readLineRowTops,
                                                            readLineRowBottoms,
                                                        )
                                                    },
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Checkbox(
                                                    checked = checkbox.checked,
                                                    onCheckedChange = { toggleReadModeCheckbox(line.lineIndex) },
                                                    modifier = Modifier.testTag("text_note_read_checkbox_${line.lineIndex}"),
                                                )
                                                Text(
                                                    text = layoutText,
                                                    style = readBodyStyle,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    onTextLayout = { recordReadLineTextLayout(line.lineIndex, it) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(vertical = 2.dp)
                                                        .pointerInput(line.lineIndex, annotatedLineText, contentField.text) {
                                                            detectTapGestures { tapOffset ->
                                                                handleReadSegmentTap(line, annotatedLineText, tapOffset)
                                                            }
                                                        }
                                                        .onGloballyPositioned { coordinates ->
                                                            recordReadLineBounds(
                                                                line.lineIndex,
                                                                coordinates,
                                                                readLineTextTops,
                                                                readLineTextBottoms,
                                                            )
                                                        },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val readContentText = findHighlightedLinkedText(
                                    value = content.ifBlank { text.content },
                                    query = findQuery,
                                    activeMatchIndex = currentFindIndex,
                                    formattingRanges = if (content.isBlank()) emptyList() else formatRanges,
                                    matchColor = readMatchColor,
                                    activeMatchColor = readActiveMatchColor,
                                    formatHighlightColor = readFormatHighlightColor,
                                    linkColor = readLinkColor,
                                    linkifyUrls = content.isNotBlank(),
                                )
                                Text(
                                    text = readContentText,
                                    style = readBodyStyle,
                                    color = if (content.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    onTextLayout = { readContentLayout = it },
                                    modifier = Modifier
                                        .pointerInput(readContentLayout, readContentText, contentField.text) {
                                            detectTapGestures { tapOffset ->
                                                val tappedUrl = if (content.isNotBlank()) {
                                                    readContentLayout
                                                        ?.getOffsetForPosition(tapOffset)
                                                        ?.let(readContentText::webUrlAt)
                                                } else {
                                                    null
                                                }
                                                if (tappedUrl != null && !openWebUrl(context, tappedUrl)) {
                                                    Toast.makeText(context, openLinkFailedLabel(appLanguage), Toast.LENGTH_SHORT).show()
                                                } else if (tappedUrl == null) {
                                                    editContentFromReadMode(tapOffset)
                                                }
                                            }
                                        }
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

    if (showDeleteDialog && !isPrivacyLocked) {
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

    if (showReadDetailsDialog && currentNote != null && !isPrivacyLocked) {
        ReadNoteDetailsDialog(
            note = currentNote,
            folderName = folderDisplayNameById(currentNote.folderId, folders, text),
            saveStatus = saveStatus,
            lastSavedAt = lastSavedAt ?: currentNote.updatedAt,
            text = text,
            appLanguage = appLanguage,
            onDismiss = { showReadDetailsDialog = false },
        )
    }

    if (showLinkDialog && !isPrivacyLocked) {
        AlertDialog(
            onDismissRequest = {
                showLinkDialog = false
                pendingLinkRange = null
            },
            title = { Text(formatLinkLabel(appLanguage)) },
            text = {
                OutlinedTextField(
                    value = linkDraft,
                    onValueChange = { linkDraft = it },
                    label = { Text(linkUrlLabel(appLanguage)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("format_link_url_input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { applyLinkFormatting(linkDraft) },
                    modifier = Modifier.testTag("apply_link_format_button"),
                ) {
                    Text(applyLabel(appLanguage))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLinkDialog = false
                        pendingLinkRange = null
                    },
                ) {
                    Text(text.cancel)
                }
            },
        )
    }
}

@Composable
private fun ReadNoteDetailsDialog(
    note: NoteEntity,
    folderName: String,
    saveStatus: SaveStatus,
    lastSavedAt: Long,
    text: UiText,
    appLanguage: AppLanguage,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.details) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${text.folder}: $folderName",
                    modifier = Modifier.testTag("text_note_details_folder"),
                )
                Text(
                    text = "${text.lastUpdated}: ${formatTime(lastSavedAt, appLanguage)}",
                    modifier = Modifier.testTag("text_note_details_updated"),
                )
                Text(
                    text = "${text.created}: ${formatTime(note.createdAt, appLanguage)}",
                    modifier = Modifier.testTag("text_note_details_created"),
                )
                Text(
                    text = saveStatus.label(text, appLanguage),
                    modifier = Modifier.testTag("text_note_details_save_status"),
                )
                Text(
                    text = reminderStatus(note.reminderAt, text, appLanguage),
                    modifier = Modifier.testTag("text_note_details_reminder"),
                )
                if (note.isPinned && !note.isDeleted) {
                    Text(
                        text = "★ ${text.pinned}",
                        modifier = Modifier.testTag("text_note_details_pinned"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("text_note_details_done_button"),
            ) {
                Text(text.back)
            }
        },
        modifier = Modifier.testTag("text_note_details_dialog"),
    )
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
        IconButton(
            onClick = onPrevious,
            enabled = query.isNotBlank() && matchCount > 0,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = text.previousMatch }
                .testTag("previous_find_match_button"),
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null)
        }
        IconButton(
            onClick = onNext,
            enabled = query.isNotBlank() && matchCount > 0,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = text.nextMatch }
                .testTag("next_find_match_button"),
        ) {
            Icon(Icons.Filled.ArrowForward, contentDescription = null)
        }
        IconButton(
            onClick = onClearSearch,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = text.clearSearch }
                .testTag("clear_find_in_note_button"),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null)
        }
    }
}

@Composable
private fun TextEditorAccessoryBar(
    text: UiText,
    appLanguage: AppLanguage,
    hasPremiumAccess: Boolean,
    onInsertCheckbox: () -> Unit,
    onInsertBullet: () -> Unit,
    onOpenPremium: () -> Unit,
    onHeading1: () -> Unit,
    onHeading2: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onHighlight: () -> Unit,
    onLink: () -> Unit,
    onClearFormatting: () -> Unit,
    onHideKeyboard: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NOTE_PAPER_SURFACE, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("text_editor_accessory_bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextEditorIconToolbarButton(
            icon = Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = text.checkboxItem,
            onClick = onInsertCheckbox,
            testTag = "quick_insert_checkbox_button",
        )
        TextEditorIconToolbarButton(
            icon = Icons.Filled.FormatListBulleted,
            contentDescription = text.bulletItem,
            onClick = onInsertBullet,
            testTag = "quick_insert_bullet_button",
        )
        if (hasPremiumAccess) {
            TextEditorToolbarButton(
                label = formatHeading1Label(appLanguage),
                contentDescription = formatHeading1Label(appLanguage),
                onClick = onHeading1,
                testTag = "format_heading_1_button",
                width = 56.dp,
                fontWeight = FontWeight.Bold,
            )
            TextEditorToolbarButton(
                label = formatHeading2Label(appLanguage),
                contentDescription = formatHeading2Label(appLanguage),
                onClick = onHeading2,
                testTag = "format_heading_2_button",
                width = 56.dp,
                fontWeight = FontWeight.SemiBold,
            )
            TextEditorToolbarButton(
                label = "B",
                contentDescription = formatBoldLabel(appLanguage),
                onClick = onBold,
                testTag = "format_bold_button",
                fontWeight = FontWeight.Bold,
            )
            TextEditorToolbarButton(
                label = "I",
                contentDescription = formatItalicLabel(appLanguage),
                onClick = onItalic,
                testTag = "format_italic_button",
                fontStyle = FontStyle.Italic,
            )
            TextEditorToolbarButton(
                label = "U",
                contentDescription = formatUnderlineLabel(appLanguage),
                onClick = onUnderline,
                testTag = "format_underline_button",
                textDecoration = TextDecoration.Underline,
            )
            TextEditorIconToolbarButton(
                icon = Icons.Filled.FormatColorFill,
                contentDescription = formatHighlightLabel(appLanguage),
                onClick = onHighlight,
                testTag = "format_highlight_button",
            )
            TextEditorIconToolbarButton(
                icon = Icons.Filled.Link,
                contentDescription = formatLinkLabel(appLanguage),
                onClick = onLink,
                testTag = "format_link_button",
            )
            TextEditorIconToolbarButton(
                icon = Icons.Filled.FormatClear,
                contentDescription = clearFormattingLabel(appLanguage),
                onClick = onClearFormatting,
                testTag = "clear_formatting_button",
            )
        } else {
            IconButton(
                onClick = onOpenPremium,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = formattingPremiumEntryLabel(appLanguage) }
                    .testTag("formatting_premium_entry_button"),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null)
            }
        }
        IconButton(
            onClick = onHideKeyboard,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = text.hideKeyboard }
                .testTag("hide_keyboard_button"),
        ) {
            Icon(Icons.Filled.KeyboardHide, contentDescription = null)
        }
    }
}

@Composable
private fun TextEditorIconToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
    ) {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun TextEditorToolbarButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    width: androidx.compose.ui.unit.Dp = 48.dp,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    highlight: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Text(
            text = label,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (highlight) {
                Modifier
                    .background(Color(0xFFFFF59D), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            } else {
                Modifier
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChecklistEditorScreen(
    noteId: Long,
    folders: List<FolderEntity>,
    text: UiText,
    appLanguage: AppLanguage,
    billingState: PremiumBillingState,
    isPrivacyLocked: Boolean,
    viewModel: NotepadViewModel,
    onOpenPremium: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val note by viewModel.observeNote(noteId).collectAsStateWithLifecycle(initialValue = null)
    var title by remember(noteId) { mutableStateOf("") }
    var items by remember(noteId) { mutableStateOf(ChecklistJson.emptyItems()) }
    var loadedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var saveStatus by remember(noteId) { mutableStateOf(SaveStatus.Synced) }
    var lastSavedAt by remember(noteId) { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isSavingAndLeaving by remember(noteId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val titleFocusRequester = remember(noteId) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val autoSaveVersion = remember(noteId) { AtomicLong(0L) }
    val checklistJson = remember(items) { ChecklistJson.encode(items) }
    val latestNote by rememberUpdatedState(note)
    val latestTitle by rememberUpdatedState(title)
    val latestItems by rememberUpdatedState(items)
    val latestLoadedNoteId by rememberUpdatedState(loadedNoteId)
    val checkedCount = items.count { it.checked }
    val visibleItemCount = items.count { it.text.isNotBlank() }.coerceAtLeast(items.size)

    LaunchedEffect(note?.id) {
        val loaded = note ?: return@LaunchedEffect
        if (loadedNoteId == loaded.id) return@LaunchedEffect
        title = loaded.title
        items = ChecklistJson.decode(loaded.textContent)
        loadedNoteId = loaded.id
        lastSavedAt = loaded.updatedAt
        saveStatus = SaveStatus.Synced
    }

    LaunchedEffect(noteId, loadedNoteId, title, checklistJson) {
        if (loadedNoteId != noteId) return@LaunchedEffect
        val current = note ?: return@LaunchedEffect
        if (title == current.title && checklistJson == current.textContent.orEmpty()) {
            saveStatus = SaveStatus.Synced
            return@LaunchedEffect
        }
        val pendingVersion = autoSaveVersion.get()
        saveStatus = SaveStatus.Saving
        delay(500)
        if (pendingVersion != autoSaveVersion.get()) return@LaunchedEffect
        lastSavedAt = viewModel.saveChecklistNoteNow(noteId, title, checklistJson) ?: System.currentTimeMillis()
        saveStatus = SaveStatus.Saved
    }

    fun hasUnsavedChecklist(
        currentNote: NoteEntity? = latestNote,
        titleValue: String = latestTitle,
        checklistJsonValue: String = ChecklistJson.encode(latestItems),
        loadedId: Long? = latestLoadedNoteId,
    ): Boolean {
        val current = currentNote ?: return false
        return loadedId == noteId &&
            (titleValue != current.title || checklistJsonValue != current.textContent.orEmpty())
    }

    fun savePendingChecklist() {
        val titleToSave = latestTitle
        val checklistJsonToSave = ChecklistJson.encode(latestItems)
        if (hasUnsavedChecklist(latestNote, titleToSave, checklistJsonToSave, latestLoadedNoteId)) {
            viewModel.saveChecklistNote(noteId, titleToSave, checklistJsonToSave)
        }
    }

    DisposableEffect(lifecycleOwner, noteId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                savePendingChecklist()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            savePendingChecklist()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun saveAndBack() {
        if (isSavingAndLeaving) return
        isSavingAndLeaving = true
        autoSaveVersion.incrementAndGet()
        keyboardController?.hide()
        val titleToSave = title
        val checklistJsonToSave = ChecklistJson.encode(items)
        scope.launch {
            saveStatus = SaveStatus.Saving
            lastSavedAt = viewModel.saveChecklistNoteNow(noteId, titleToSave, checklistJsonToSave) ?: lastSavedAt
            saveStatus = SaveStatus.Saved
            onBack()
        }
    }

    fun openPremiumAfterSavingChecklist() {
        val currentNote = latestNote
        if (currentNote == null) {
            onOpenPremium()
            return
        }
        autoSaveVersion.incrementAndGet()
        val titleToSave = latestTitle
        val checklistJsonToSave = ChecklistJson.encode(latestItems)
        scope.launch {
            saveStatus = SaveStatus.Saving
            lastSavedAt = viewModel.saveChecklistNoteNow(noteId, titleToSave, checklistJsonToSave) ?: currentNote.updatedAt
            saveStatus = SaveStatus.Saved
            onOpenPremium()
        }
    }

    fun updateItem(id: String, transform: (ChecklistItem) -> ChecklistItem) {
        autoSaveVersion.incrementAndGet()
        items = items.map { item -> if (item.id == id) transform(item) else item }
    }

    fun addItem() {
        autoSaveVersion.incrementAndGet()
        items = items + ChecklistItem(text = "")
    }

    fun deleteItem(id: String) {
        autoSaveVersion.incrementAndGet()
        items = items.filterNot { it.id == id }.ifEmpty { ChecklistJson.emptyItems() }
    }

    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) {
            showDeleteDialog = false
            keyboardController?.hide()
        }
    }

    BackHandler(onBack = ::saveAndBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title.ifBlank { text.untitledChecklist },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            saveStatus.label(text, appLanguage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.testTag("checklist_save_status"),
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
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("delete_checklist_note_button"),
                    ) {
                        Text(text.moveToTrash)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .testTag("checklist_editor"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Card(shape = RoundedCornerShape(8.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = {
                                    autoSaveVersion.incrementAndGet()
                                    title = it
                                },
                                label = { Text(text.title) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(titleFocusRequester)
                                    .testTag("checklist_note_title"),
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
                                    isPrivacyLocked = isPrivacyLocked,
                                    hasPremiumAccess = billingState.hasPremiumAccess,
                                    onOpenPremium = ::openPremiumAfterSavingChecklist,
                                    onMove = { folderId -> viewModel.moveNote(noteId, folderId) },
                                )
                                Text(
                                    text = text.checkedItems(checkedCount, visibleItemCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("checklist_progress"),
                                )
                            }
                            ReminderControls(
                                note = currentNote,
                                text = text,
                                appLanguage = appLanguage,
                                isPrivacyLocked = isPrivacyLocked,
                                hasPremiumAccess = billingState.hasPremiumAccess,
                                onOpenPremium = ::openPremiumAfterSavingChecklist,
                                onSetReminder = { reminderAt, repeat ->
                                    viewModel.setNoteReminder(noteId, reminderAt, repeat)
                                },
                                onClearReminder = { viewModel.setNoteReminder(noteId, null) },
                            )
                        }
                    }
                }
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checklist_item_${item.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = item.checked,
                            onCheckedChange = { checked ->
                                updateItem(item.id) { it.copy(checked = checked) }
                            },
                            modifier = Modifier.testTag("checklist_item_checkbox"),
                        )
                        OutlinedTextField(
                            value = item.text,
                            onValueChange = { value ->
                                updateItem(item.id) { it.copy(text = value) }
                            },
                            singleLine = true,
                            placeholder = { Text(text.checklist) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("checklist_item_text"),
                        )
                        TextButton(
                            onClick = { deleteItem(item.id) },
                            modifier = Modifier.testTag("delete_checklist_item_button"),
                        ) {
                            Text("x", maxLines = 1)
                        }
                    }
                }
                item {
                    Button(
                        onClick = ::addItem,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_checklist_item_button"),
                    ) {
                        Text(text.addChecklistItem)
                    }
                }
            }
        }
    }

    if (showDeleteDialog && !isPrivacyLocked) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawingEditorScreen(
    noteId: Long,
    isNewDraft: Boolean,
    folders: List<FolderEntity>,
    text: UiText,
    appLanguage: AppLanguage,
    billingState: PremiumBillingState,
    isPrivacyLocked: Boolean,
    viewModel: NotepadViewModel,
    onOpenPremium: () -> Unit,
    onOpenPremiumAfterDraftDiscard: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val note by viewModel.observeNote(noteId).collectAsStateWithLifecycle(initialValue = null)
    var title by remember(noteId) { mutableStateOf("") }
    var strokes by remember(noteId) { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var redoStrokes by remember(noteId) { mutableStateOf<List<DrawingStroke>>(emptyList()) }
    var selectedTool by remember(noteId) { mutableStateOf(DrawingTool.Pen) }
    var selectedPenBrushSize by remember(noteId, context) { mutableStateOf(context.readLastDrawingPenBrushSize()) }
    var selectedEraserBrushSize by remember(noteId) { mutableStateOf(DrawingBrushSize.Medium) }
    var selectedColor by remember(noteId, context) { mutableStateOf(context.readLastDrawingPenColor()) }
    var canvasSize by remember(noteId) { mutableStateOf(IntSize.Zero) }
    var isFullscreenDrawing by remember(noteId) { mutableStateOf(false) }
    var loadedNoteId by remember(noteId) { mutableStateOf<Long?>(null) }
    var loadedContentUpdatedAt by remember(noteId) { mutableStateOf<Long?>(null) }
    var loadedContentTitle by remember(noteId) { mutableStateOf("") }
    var loadedContentDrawingData by remember(noteId) { mutableStateOf("[]") }
    var loadedContentEditVersion by remember(noteId) { mutableStateOf(0L) }
    var lastLocalDrawingCommitUpdatedAt by remember(noteId) { mutableStateOf<Long?>(null) }
    var lastLocalDrawingCommitTitle by remember(noteId) { mutableStateOf("") }
    var lastLocalDrawingCommitData by remember(noteId) { mutableStateOf("[]") }
    var hasHandledInitialDrawingFocus by remember(noteId) { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingPngBytes by remember(noteId) { mutableStateOf<ByteArray?>(null) }
    var saveStatus by remember(noteId) { mutableStateOf(SaveStatus.Synced) }
    var lastSavedAt by remember(noteId) { mutableStateOf<Long?>(null) }
    var isSavingAndLeaving by remember(noteId) { mutableStateOf(false) }
    var activeDrawingSaveCount by remember(noteId) { mutableStateOf(0) }
    var isPngRendering by remember(noteId) { mutableStateOf(false) }
    var drawingIoMessage by remember(noteId) { mutableStateOf<String?>(null) }
    var hasMetadataIntent by remember(noteId) { mutableStateOf(false) }
    var hasFailedDrawingSave by remember(noteId) { mutableStateOf(false) }
    val autoSaveVersion = remember(noteId) { AtomicLong(0L) }
    val drawingSaveVersion = remember(noteId) { AtomicLong(0L) }
    val drawingSaveEditGate = remember(noteId, viewModel) { viewModel.drawingSaveEditGate(noteId) }
    val drawingSaveMutex = remember(noteId) { Mutex() }

    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) {
            showDeleteDialog = false
            showClearDialog = false
        }
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val titleFocusRequester = remember(noteId) { FocusRequester() }
    val latestNote by rememberUpdatedState(note)
    val latestLoadedNoteId by rememberUpdatedState(loadedNoteId)
    val latestLoadedContentUpdatedAt by rememberUpdatedState(loadedContentUpdatedAt)
    val latestLoadedContentTitle by rememberUpdatedState(loadedContentTitle)
    val latestLoadedContentDrawingData by rememberUpdatedState(loadedContentDrawingData)
    val latestLoadedContentEditVersion by rememberUpdatedState(loadedContentEditVersion)
    val latestTitle by rememberUpdatedState(title)
    val latestStrokes by rememberUpdatedState(strokes)
    val latestSaveStatus by rememberUpdatedState(saveStatus)
    val latestActiveDrawingSaveCount by rememberUpdatedState(activeDrawingSaveCount)
    val latestHasMetadataIntent by rememberUpdatedState(hasMetadataIntent)
    val latestHasFailedDrawingSave by rememberUpdatedState(hasFailedDrawingSave)
    val selectedBrushSize = if (selectedTool == DrawingTool.Eraser) {
        selectedEraserBrushSize
    } else {
        selectedPenBrushSize
    }
    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val pngBytes = pendingPngBytes
        pendingPngBytes = null
        if (uri == null || pngBytes == null) {
            isPngRendering = false
            drawingIoMessage = null
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    writeBytesToUri(context, uri, pngBytes)
                }
                drawingIoMessage = null
                Toast.makeText(context, text.pngExportComplete, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                drawingIoMessage = text.pngExportFailed
                Toast.makeText(context, text.pngExportFailed, Toast.LENGTH_SHORT).show()
            } finally {
                pendingPngBytes = null
                isPngRendering = false
            }
        }
    }

    fun drawingContentMatches(
        currentNote: NoteEntity,
        titleValue: String,
        strokeValues: List<DrawingStroke>,
    ): Boolean {
        return titleValue == currentNote.title &&
            DrawingJson.encode(strokeValues) == currentNote.drawingData.orEmpty()
    }

    fun updateLoadedContentBaseline(
        loaded: NoteEntity,
        editVersion: Long = drawingSaveEditGate.currentEditVersion(),
    ) {
        loadedContentUpdatedAt = loaded.updatedAt
        loadedContentTitle = loaded.title
        loadedContentDrawingData = loaded.drawingData.orEmpty()
        loadedContentEditVersion = editVersion
    }

    fun updateLastLocalDrawingCommit(titleValue: String, drawingData: String, updatedAt: Long) {
        lastLocalDrawingCommitTitle = titleValue
        lastLocalDrawingCommitData = drawingData
        lastLocalDrawingCommitUpdatedAt = updatedAt
    }

    fun loadedContentMatches(loaded: NoteEntity): Boolean {
        return loaded.title == loadedContentTitle &&
            loaded.drawingData.orEmpty() == loadedContentDrawingData
    }

    fun loadDrawingContent(loaded: NoteEntity) {
        title = loaded.title
        strokes = DrawingJson.decode(loaded.drawingData)
        redoStrokes = emptyList()
        loadedNoteId = loaded.id
        updateLoadedContentBaseline(loaded)
        updateLastLocalDrawingCommit(loaded.title, loaded.drawingData.orEmpty(), loaded.updatedAt)
        lastSavedAt = loaded.updatedAt
        saveStatus = SaveStatus.Synced
        drawingIoMessage = null
        hasFailedDrawingSave = false
    }

    LaunchedEffect(note?.id) {
        val loaded = note ?: return@LaunchedEffect
        loadDrawingContent(loaded)
    }

    LaunchedEffect(note?.updatedAt) {
        val loaded = note ?: return@LaunchedEffect
        if (loaded.id == noteId) {
            lastSavedAt = loaded.updatedAt
            when {
                drawingContentMatches(loaded, title, strokes) -> {
                    updateLoadedContentBaseline(loaded)
                }
                loadedContentMatches(loaded) -> {
                    loadedContentUpdatedAt = loaded.updatedAt
                }
                drawingSaveEditGate.isCurrent(loadedContentEditVersion) -> {
                    loadDrawingContent(loaded)
                }
            }
        }
    }

    LaunchedEffect(loadedNoteId) {
        if (loadedNoteId == noteId && !hasHandledInitialDrawingFocus) {
            hasHandledInitialDrawingFocus = true
            if (strokes.isNotEmpty() || title.isBlank()) {
                isFullscreenDrawing = true
            }
        }
    }

    fun markDrawingEdited() {
        drawingSaveEditGate.markEdited()
        saveStatus = SaveStatus.Saving
    }

    fun replaceDrawingStrokes(updatedStrokes: List<DrawingStroke>) {
        if (updatedStrokes != strokes) {
            markDrawingEdited()
        }
        strokes = updatedStrokes
    }

    fun hasDrawingUserIntent(
        titleValue: String,
        strokeValues: List<DrawingStroke>,
        metadataIntent: Boolean,
        currentNote: NoteEntity?,
    ): Boolean {
        return titleValue.trim().isNotEmpty() ||
            strokeValues.isNotEmpty() ||
            metadataIntent ||
            currentNote?.reminderAt != null ||
            currentNote?.isPinned == true
    }

    fun hasUnsavedDrawingNote(
        currentNote: NoteEntity? = latestNote,
        titleValue: String = latestTitle,
        strokeValues: List<DrawingStroke> = latestStrokes,
        loadedId: Long? = latestLoadedNoteId,
    ): Boolean {
        val current = currentNote ?: return false
        return loadedId == noteId &&
            (
                titleValue != current.title ||
                    DrawingJson.encode(strokeValues) != current.drawingData.orEmpty()
                )
    }

    fun canDiscardBlankDrawingDraft(
        titleValue: String,
        strokeValues: List<DrawingStroke>,
        metadataIntent: Boolean = latestHasMetadataIntent,
        currentNote: NoteEntity? = latestNote,
    ): Boolean {
        return isNewDraft &&
            !hasDrawingUserIntent(titleValue, strokeValues, metadataIntent, currentNote) &&
            latestSaveStatus != SaveStatus.Failed &&
            !latestHasFailedDrawingSave
    }

    suspend fun saveDrawingSnapshot(
        titleValue: String,
        strokeValues: List<DrawingStroke>,
        expectedEditVersion: Long = drawingSaveEditGate.currentEditVersion(),
    ): Pair<NoteEntity, List<DrawingStroke>>? {
        val drawingData = DrawingJson.encode(strokeValues)
        val baselineTitle = latestLoadedContentTitle
        val baselineDrawingData = latestLoadedContentDrawingData
        val baselineUpdatedAt = latestLoadedContentUpdatedAt ?: return null
        val requestVersion = drawingSaveVersion.incrementAndGet()
        fun isCurrentSaveRequest(): Boolean {
            return requestVersion == drawingSaveVersion.get() &&
                drawingSaveEditGate.isCurrent(expectedEditVersion)
        }
        activeDrawingSaveCount += 1
        if (drawingSaveEditGate.isCurrent(expectedEditVersion)) {
            saveStatus = SaveStatus.Saving
            drawingIoMessage = null
        }
        var skippedAsStale = false
        var sourceNote: NoteEntity? = null
        var attemptedExpectedUpdatedAt: Long? = null
        var attemptedExpectedTitle: String? = null
        var attemptedExpectedDrawingData: String? = null
        return try {
            val savedAt = drawingSaveMutex.withLock {
                if (!isCurrentSaveRequest()) {
                    skippedAsStale = true
                    null
                } else {
                    val currentNote = viewModel.getActiveNote(noteId) ?: latestNote ?: return@withLock null
                    sourceNote = currentNote
                    val currentDrawingData = currentNote.drawingData.orEmpty()
                    val (expectedTitle, expectedDrawingData, expectedUpdatedAt) = when {
                        drawingContentMatches(currentNote, titleValue, strokeValues) -> {
                            Triple(currentNote.title, currentDrawingData, currentNote.updatedAt)
                        }
                        currentNote.title == lastLocalDrawingCommitTitle &&
                            currentDrawingData == lastLocalDrawingCommitData &&
                            currentNote.updatedAt == lastLocalDrawingCommitUpdatedAt -> {
                            Triple(currentNote.title, currentDrawingData, currentNote.updatedAt)
                        }
                        else -> {
                            Triple(
                                baselineTitle,
                                baselineDrawingData,
                                baselineUpdatedAt,
                            )
                        }
                    }
                    attemptedExpectedTitle = expectedTitle
                    attemptedExpectedDrawingData = expectedDrawingData
                    attemptedExpectedUpdatedAt = expectedUpdatedAt
                    try {
                        viewModel.saveDrawingNoteNow(
                            noteId = noteId,
                            title = titleValue,
                            drawingData = drawingData,
                            expectedUpdatedAt = expectedUpdatedAt,
                            expectedTitle = expectedTitle,
                            expectedDrawingData = expectedDrawingData,
                            saveEditGate = drawingSaveEditGate,
                            isCurrentBeforeWrite = { isCurrentSaveRequest() },
                        ).also {
                            if (it == null && !isCurrentSaveRequest()) {
                                skippedAsStale = true
                            }
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            if (savedAt == null) {
                val currentAfterFailure = if (!skippedAsStale) {
                    runCatching { viewModel.getActiveNote(noteId) }.getOrNull()
                } else {
                    null
                }
                val expectedTitle = attemptedExpectedTitle
                val expectedDrawingData = attemptedExpectedDrawingData
                val expectedUpdatedAt = attemptedExpectedUpdatedAt
                val isExternalContentConflict = currentAfterFailure != null &&
                    expectedTitle != null &&
                    expectedDrawingData != null &&
                    expectedUpdatedAt != null &&
                    !drawingContentMatches(currentAfterFailure, titleValue, strokeValues) &&
                    (
                        currentAfterFailure.title != expectedTitle ||
                            currentAfterFailure.drawingData.orEmpty() != expectedDrawingData
                        )
                if (
                    isExternalContentConflict &&
                    requestVersion == drawingSaveVersion.get() &&
                    drawingSaveEditGate.isCurrent(expectedEditVersion)
                ) {
                    loadDrawingContent(currentAfterFailure)
                    currentAfterFailure to DrawingJson.decode(currentAfterFailure.drawingData)
                } else {
                    if (!skippedAsStale) {
                        hasFailedDrawingSave = true
                    }
                    if (
                        !skippedAsStale &&
                        requestVersion == drawingSaveVersion.get() &&
                        drawingSaveEditGate.isCurrent(expectedEditVersion)
                    ) {
                        saveStatus = SaveStatus.Failed
                    }
                    null
                }
            } else {
                updateLastLocalDrawingCommit(titleValue, drawingData, savedAt)
                if (!isCurrentSaveRequest()) {
                    null
                } else {
                    lastSavedAt = savedAt
                    loadedContentUpdatedAt = savedAt
                    loadedContentTitle = titleValue
                    loadedContentDrawingData = drawingData
                    loadedContentEditVersion = expectedEditVersion
                    saveStatus = SaveStatus.Saved
                    hasFailedDrawingSave = false
                    val persistedNote = viewModel.getActiveNote(noteId) ?: latestNote ?: sourceNote
                    if (persistedNote == null || !isCurrentSaveRequest()) {
                        null
                    } else {
                        persistedNote.copy(
                            title = titleValue,
                            textContent = null,
                            drawingData = drawingData,
                            updatedAt = savedAt,
                        ) to strokeValues
                    }
                }
            }
        } finally {
            activeDrawingSaveCount = (activeDrawingSaveCount - 1).coerceAtLeast(0)
        }
    }

    fun requestDrawingSave(
        titleValue: String = latestTitle,
        strokeValues: List<DrawingStroke> = latestStrokes,
    ) {
        autoSaveVersion.incrementAndGet()
        val expectedEditVersion = drawingSaveEditGate.currentEditVersion()
        if (
            !hasUnsavedDrawingNote(titleValue = titleValue, strokeValues = strokeValues) &&
            expectedEditVersion == latestLoadedContentEditVersion &&
            latestSaveStatus != SaveStatus.Failed
        ) {
            return
        }
        scope.launch {
            saveDrawingSnapshot(titleValue, strokeValues, expectedEditVersion)
        }
    }

    LaunchedEffect(noteId, loadedNoteId, title) {
        if (loadedNoteId == noteId) {
            val pendingVersion = autoSaveVersion.get()
            delay(500)
            if (pendingVersion != autoSaveVersion.get()) return@LaunchedEffect
            val titleToSave = latestTitle
            val strokesToSave = latestStrokes
            val expectedEditVersion = drawingSaveEditGate.currentEditVersion()
            if (
                !hasUnsavedDrawingNote(titleValue = titleToSave, strokeValues = strokesToSave) &&
                expectedEditVersion == latestLoadedContentEditVersion &&
                latestSaveStatus != SaveStatus.Failed
            ) {
                if (latestActiveDrawingSaveCount == 0 && latestSaveStatus != SaveStatus.Failed) {
                    saveStatus = SaveStatus.Synced
                }
                return@LaunchedEffect
            }
            saveDrawingSnapshot(titleToSave, strokesToSave, expectedEditVersion)
        }
    }

    fun savePendingDrawingNote() {
        val titleToSave = latestTitle
        val strokesToSave = latestStrokes
        val drawingData = DrawingJson.encode(strokesToSave)
        val hasUserIntent = hasDrawingUserIntent(
            titleValue = titleToSave,
            strokeValues = strokesToSave,
            metadataIntent = latestHasMetadataIntent,
            currentNote = latestNote,
        )
        if (canDiscardBlankDrawingDraft(titleToSave, strokesToSave)) {
            val expectedEditVersion = drawingSaveEditGate.currentEditVersion()
            autoSaveVersion.incrementAndGet()
            viewModel.discardNewDrawingDraftIfBlank(
                noteId = noteId,
                isNewDraft = isNewDraft,
                hasUserIntent = hasUserIntent,
                title = titleToSave,
                drawingData = drawingData,
                saveEditGate = drawingSaveEditGate,
                isCurrentBeforeDelete = { drawingSaveEditGate.isCurrent(expectedEditVersion) },
                expectedUpdatedAt = latestLoadedContentUpdatedAt,
                expectedTitle = latestLoadedContentTitle,
                expectedDrawingData = latestLoadedContentDrawingData,
            )
            return
        }
        if (hasUnsavedDrawingNote(titleValue = titleToSave, strokeValues = strokesToSave)) {
            val currentNote = latestNote ?: return
            val (expectedTitle, expectedDrawingData, expectedUpdatedAt) = if (
                drawingContentMatches(currentNote, titleToSave, strokesToSave)
            ) {
                Triple(currentNote.title, currentNote.drawingData.orEmpty(), currentNote.updatedAt)
            } else {
                Triple(
                    latestLoadedContentTitle,
                    latestLoadedContentDrawingData,
                    latestLoadedContentUpdatedAt ?: return,
                )
            }
            val expectedEditVersion = drawingSaveEditGate.currentEditVersion()
            autoSaveVersion.incrementAndGet()
            viewModel.saveDrawingNoteIfCurrent(
                noteId = noteId,
                title = titleToSave,
                drawingData = drawingData,
                expectedUpdatedAt = expectedUpdatedAt,
                expectedTitle = expectedTitle,
                expectedDrawingData = expectedDrawingData,
                saveEditGate = drawingSaveEditGate,
                isCurrentBeforeWrite = { drawingSaveEditGate.isCurrent(expectedEditVersion) },
            )
        }
    }

    DisposableEffect(lifecycleOwner, noteId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                savePendingDrawingNote()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            pendingPngBytes = null
            isPngRendering = false
            savePendingDrawingNote()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun saveAndBack() {
        if (isSavingAndLeaving) return
        if (latestNote == null) {
            onBack()
            return
        }
        isSavingAndLeaving = true
        autoSaveVersion.incrementAndGet()
        val titleToSave = latestTitle
        val strokesToSave = latestStrokes
        val drawingData = DrawingJson.encode(strokesToSave)
        val hasUserIntent = hasDrawingUserIntent(
            titleValue = titleToSave,
            strokeValues = strokesToSave,
            metadataIntent = latestHasMetadataIntent,
            currentNote = latestNote,
        )
        val expectedEditVersion = drawingSaveEditGate.currentEditVersion()
        scope.launch {
            if (canDiscardBlankDrawingDraft(titleToSave, strokesToSave)) {
                val deleted = viewModel.discardNewDrawingDraftIfBlankNow(
                    noteId = noteId,
                    isNewDraft = isNewDraft,
                    hasUserIntent = hasUserIntent,
                    title = titleToSave,
                    drawingData = drawingData,
                    saveEditGate = drawingSaveEditGate,
                    isCurrentBeforeDelete = { drawingSaveEditGate.isCurrent(expectedEditVersion) },
                    expectedUpdatedAt = latestLoadedContentUpdatedAt,
                    expectedTitle = latestLoadedContentTitle,
                    expectedDrawingData = latestLoadedContentDrawingData,
                )
                if (deleted) {
                    onBack()
                } else {
                    saveStatus = SaveStatus.Failed
                    isSavingAndLeaving = false
                }
                return@launch
            }
            val saved = saveDrawingSnapshot(titleToSave, strokesToSave, expectedEditVersion)
            if (saved == null) {
                isSavingAndLeaving = false
            } else {
                onBack()
            }
        }
    }

    fun retrySaveDrawingNote() {
        requestDrawingSave()
    }

    fun openPremiumAfterSavingDrawingNote() {
        if (note == null) {
            onOpenPremium()
            return
        }
        val titleToSave = latestTitle
        val strokesToSave = latestStrokes
        autoSaveVersion.incrementAndGet()
        val expectedEditVersion = drawingSaveEditGate.currentEditVersion()
        scope.launch {
            if (canDiscardBlankDrawingDraft(titleToSave, strokesToSave)) {
                val deleted = viewModel.discardNewDrawingDraftIfBlankNow(
                    noteId = noteId,
                    isNewDraft = isNewDraft,
                    hasUserIntent = false,
                    title = titleToSave,
                    drawingData = DrawingJson.encode(strokesToSave),
                    saveEditGate = drawingSaveEditGate,
                    isCurrentBeforeDelete = { drawingSaveEditGate.isCurrent(expectedEditVersion) },
                    expectedUpdatedAt = latestLoadedContentUpdatedAt,
                    expectedTitle = latestLoadedContentTitle,
                    expectedDrawingData = latestLoadedContentDrawingData,
                )
                if (deleted) {
                    onOpenPremiumAfterDraftDiscard()
                } else {
                    saveStatus = SaveStatus.Failed
                }
                return@launch
            }
            if (saveDrawingSnapshot(titleToSave, strokesToSave, expectedEditVersion) != null) {
                onOpenPremium()
            }
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
        if (isPngRendering) return
        val titleToSave = latestTitle
        val strokesToSave = latestStrokes
        val expectedEditVersion = drawingSaveEditGate.currentEditVersion()
        autoSaveVersion.incrementAndGet()
        isPngRendering = true
        drawingIoMessage = preparingPngLabel(appLanguage)
        pendingPngBytes = null
        scope.launch {
            try {
                val saved = saveDrawingSnapshot(titleToSave, strokesToSave, expectedEditVersion)
                if (saved == null) {
                    drawingIoMessage = if (saveStatus == SaveStatus.Failed) saveFailedLabel(appLanguage) else null
                    return@launch
                }
                val savedNote = saved.first
                val savedStrokes = saved.second
                val pngBytes = withContext(Dispatchers.Default) {
                    renderCurrentDrawingPng(savedStrokes)
                }
                if (!drawingSaveEditGate.isCurrent(expectedEditVersion)) {
                    drawingIoMessage = null
                    return@launch
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
                drawingIoMessage = null
            } catch (_: Exception) {
                drawingIoMessage = text.pngShareFailed
                Toast.makeText(context, text.pngShareFailed, Toast.LENGTH_SHORT).show()
            } finally {
                pendingPngBytes = null
                isPngRendering = false
            }
        }
    }

    fun exportCurrentDrawingPng() {
        if (isPngRendering) return
        val titleToSave = latestTitle
        val strokesToSave = latestStrokes
        val expectedEditVersion = drawingSaveEditGate.currentEditVersion()
        autoSaveVersion.incrementAndGet()
        isPngRendering = true
        drawingIoMessage = preparingPngLabel(appLanguage)
        pendingPngBytes = null
        scope.launch {
            try {
                val saved = saveDrawingSnapshot(titleToSave, strokesToSave, expectedEditVersion)
                if (saved == null) {
                    drawingIoMessage = if (saveStatus == SaveStatus.Failed) saveFailedLabel(appLanguage) else null
                    isPngRendering = false
                    return@launch
                }
                val savedNote = saved.first
                val savedStrokes = saved.second
                pendingPngBytes = withContext(Dispatchers.Default) {
                    renderCurrentDrawingPng(savedStrokes)
                }
                if (!drawingSaveEditGate.isCurrent(expectedEditVersion)) {
                    pendingPngBytes = null
                    drawingIoMessage = null
                    isPngRendering = false
                    return@launch
                }
                exportPngLauncher.launch(defaultPngExportFileName(savedNote, text))
            } catch (_: Exception) {
                pendingPngBytes = null
                isPngRendering = false
                drawingIoMessage = text.pngExportFailed
                Toast.makeText(context, text.pngExportFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun undoStroke() {
        val lastStroke = strokes.lastOrNull() ?: return
        val updatedStrokes = strokes.dropLast(1)
        markDrawingEdited()
        strokes = updatedStrokes
        redoStrokes = redoStrokes + lastStroke
        requestDrawingSave(strokeValues = updatedStrokes)
    }

    fun redoStroke() {
        val stroke = redoStrokes.lastOrNull() ?: return
        val updatedStrokes = strokes + stroke
        markDrawingEdited()
        strokes = updatedStrokes
        redoStrokes = redoStrokes.dropLast(1)
        requestDrawingSave(strokeValues = updatedStrokes)
    }

    fun clearDrawingNow() {
        markDrawingEdited()
        strokes = emptyList()
        redoStrokes = emptyList()
        requestDrawingSave(strokeValues = emptyList())
    }

    fun finishStroke(updatedStrokes: List<DrawingStroke>) {
        if (updatedStrokes != strokes) {
            markDrawingEdited()
        }
        strokes = updatedStrokes
        redoStrokes = emptyList()
        requestDrawingSave(strokeValues = updatedStrokes)
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

    fun selectDrawingTool(tool: DrawingTool) {
        selectedTool = tool
    }

    fun selectDrawingBrushSize(brushSize: DrawingBrushSize) {
        if (selectedTool == DrawingTool.Pen) {
            selectedPenBrushSize = brushSize
            context.writeLastDrawingPenBrushSize(brushSize)
        } else {
            selectedEraserBrushSize = brushSize
        }
    }

    fun selectDrawingColor(color: DrawingColorOption) {
        selectedColor = color
        context.writeLastDrawingPenColor(color)
    }

    fun exitFullscreenDrawing() {
        isFullscreenDrawing = false
        requestDrawingSave()
    }

    BackHandler {
        if (latestNote == null) {
            onBack()
        } else if (isFullscreenDrawing) {
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
                Text(
                    text = text.noteNotFound,
                    modifier = Modifier.testTag("drawing_note_not_found"),
                )
            }
        } else if (isFullscreenDrawing) {
            val hidePristineChrome = isNewDraft &&
                !hasDrawingUserIntent(
                    titleValue = title,
                    strokeValues = strokes,
                    metadataIntent = hasMetadataIntent,
                    currentNote = currentNote,
                ) &&
                saveStatus != SaveStatus.Saving &&
                saveStatus != SaveStatus.Failed &&
                drawingIoMessage == null
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
                    IconButton(
                        onClick = ::exitFullscreenDrawing,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = text.exitFocusWriting }
                            .testTag("exit_fullscreen_drawing_button"),
                    ) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = null)
                    }
                    if (hidePristineChrome) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.ifBlank { text.untitledDrawing },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            DrawingStatusLine(
                                saveStatus = saveStatus,
                                lastSavedAt = lastSavedAt ?: currentNote.updatedAt,
                                drawingIoMessage = drawingIoMessage,
                                text = text,
                                appLanguage = appLanguage,
                                showUpdatedTime = false,
                                onRetry = ::retrySaveDrawingNote,
                            )
                        }
                    }
                    IconButton(
                        onClick = ::exitFullscreenDrawing,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = text.details }
                            .testTag("drawing_fullscreen_details_button"),
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                }
                DrawingCanvas(
                    strokes = strokes,
                    onStrokesChange = ::replaceDrawingStrokes,
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
                    onToolChange = ::selectDrawingTool,
                    onBrushSizeChange = ::selectDrawingBrushSize,
                    onColorChange = ::selectDrawingColor,
                    isPngRendering = isPngRendering,
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
                    onValueChange = {
                        autoSaveVersion.incrementAndGet()
                        markDrawingEdited()
                        title = it
                    },
                    label = { Text(text.title) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .testTag("drawing_note_title"),
                )
                DrawingStatusLine(
                    saveStatus = saveStatus,
                    lastSavedAt = lastSavedAt ?: currentNote.updatedAt,
                    drawingIoMessage = drawingIoMessage,
                    text = text,
                    appLanguage = appLanguage,
                    onRetry = ::retrySaveDrawingNote,
                    modifier = Modifier.fillMaxWidth(),
                )
                NoteFolderSelector(
                    folders = folders,
                    text = text,
                    currentFolderId = currentNote.folderId,
                    isPrivacyLocked = isPrivacyLocked,
                    hasPremiumAccess = billingState.hasPremiumAccess,
                    onOpenPremium = {
                        openPremiumAfterSavingDrawingNote()
                    },
                    onInteract = { hasMetadataIntent = true },
                    onMove = { folderId ->
                        hasMetadataIntent = true
                        viewModel.moveNote(noteId, folderId)
                    },
                )
                ReminderControls(
                    note = currentNote,
                    text = text,
                    appLanguage = appLanguage,
                    isPrivacyLocked = isPrivacyLocked,
                    hasPremiumAccess = billingState.hasPremiumAccess,
                    onOpenPremium = {
                        openPremiumAfterSavingDrawingNote()
                    },
                    onInteract = { hasMetadataIntent = true },
                    onSetReminder = { reminderAt, repeat ->
                        hasMetadataIntent = true
                        viewModel.setNoteReminder(noteId, reminderAt, repeat)
                    },
                    onClearReminder = {
                        hasMetadataIntent = true
                        viewModel.setNoteReminder(noteId, null)
                    },
                )
                DrawingCanvasWithFullscreenEntry(
                    strokes = strokes,
                    text = text,
                    onStrokesChange = ::replaceDrawingStrokes,
                    onStrokeFinished = ::finishStroke,
                    brushColorArgb = selectedColor.colorArgb,
                    brushWidthPx = activeStrokeWidth(),
                    strokeTool = activeStrokeTool(),
                    onCanvasSizeChange = { canvasSize = it },
                    onFullscreen = {
                        requestDrawingSave()
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
                    onToolChange = ::selectDrawingTool,
                    onBrushSizeChange = ::selectDrawingBrushSize,
                    onColorChange = ::selectDrawingColor,
                    isPngRendering = isPngRendering,
                )
            }
        }
    }

    if (showDeleteDialog && !isPrivacyLocked) {
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

    if (showClearDialog && !isPrivacyLocked) {
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
private fun DrawingStatusLine(
    saveStatus: SaveStatus,
    lastSavedAt: Long,
    drawingIoMessage: String?,
    text: UiText,
    appLanguage: AppLanguage,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showUpdatedTime: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = saveStatus.label(text, appLanguage),
                style = MaterialTheme.typography.bodySmall,
                color = if (saveStatus == SaveStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
                modifier = Modifier.testTag("drawing_note_save_status"),
            )
            if (saveStatus == SaveStatus.Failed) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("drawing_note_retry_save_button"),
                ) {
                    Text(retryLabel(appLanguage), maxLines = 1)
                }
            }
        }
        if (showUpdatedTime) {
            Text(
                text = "${text.lastUpdated}: ${formatTime(lastSavedAt, appLanguage)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("drawing_note_updated_time"),
            )
        }
        if (drawingIoMessage != null) {
            Text(
                text = drawingIoMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (saveStatus == SaveStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("drawing_png_status"),
            )
        }
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
    isPngRendering: Boolean,
    modifier: Modifier = Modifier,
    showFileActions: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawingIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = text.undo,
                enabled = strokes.isNotEmpty(),
                testTag = "drawing_undo_button",
                onClick = onUndo,
            )
            DrawingIconButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                label = text.redo,
                enabled = redoStrokes.isNotEmpty(),
                testTag = "drawing_redo_button",
                onClick = onRedo,
            )
            DrawingIconButton(
                icon = Icons.Filled.Delete,
                label = text.clearDrawing,
                enabled = strokes.isNotEmpty(),
                destructive = true,
                testTag = "drawing_clear_button",
                onClick = onClear,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showFileActions) {
                DrawingIconButton(
                    icon = Icons.Filled.Share,
                    label = text.sharePng,
                    enabled = !isPngRendering,
                    testTag = "share_drawing_png_button",
                    onClick = onSharePng,
                )
                DrawingIconButton(
                    icon = Icons.Filled.FileDownload,
                    label = text.exportPng,
                    enabled = !isPngRendering,
                    testTag = "export_drawing_png_button",
                    onClick = onExportPng,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawingTool.entries.forEach { tool ->
                DrawingToolSegmentButton(
                    tool = tool,
                    label = tool.label(text),
                    selected = selectedTool == tool,
                    onClick = { onToolChange(tool) },
                )
            }
            DrawingBrushSize.entries.forEach { size ->
                DrawingBrushSizeButton(
                    brushSize = size,
                    label = size.label(text, selectedTool),
                    selectedTool = selectedTool,
                    selected = selectedBrushSize == size,
                    onClick = { onBrushSizeChange(size) },
                )
            }
            if (selectedTool == DrawingTool.Pen) {
                DrawingColorOption.entries.forEach { color ->
                    DrawingColorSwatch(
                        color = color,
                        label = color.label(text),
                        selected = selectedColor == color,
                        onClick = { onColorChange(color) },
                    )
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
}

@Composable
private fun DrawingIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .testTag(testTag),
    ) {
        if (destructive) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        } else {
            Icon(icon, contentDescription = null)
        }
    }
}

@Composable
private fun DrawingToolSegmentButton(
    tool: DrawingTool,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(background, RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .testTag("drawing_tool_${tool.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (tool == DrawingTool.Pen) Icons.Filled.Edit else Icons.Filled.FormatClear,
            contentDescription = null,
            tint = foreground,
        )
    }
}

@Composable
private fun DrawingBrushSizeButton(
    brushSize: DrawingBrushSize,
    label: String,
    selectedTool: DrawingTool,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val lineColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val strokeWidth = if (selectedTool == DrawingTool.Eraser) {
        brushSize.eraserSizeDp.coerceAtMost(16f)
    } else {
        brushSize.penWidthPx.coerceAtMost(12f)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(background, RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .testTag("drawing_brush_${brushSize.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            drawLine(
                color = lineColor,
                start = Offset(3.dp.toPx(), center.y),
                end = Offset(size.width - 3.dp.toPx(), center.y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun DrawingColorSwatch(
    color: DrawingColorOption,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .testTag("drawing_color_${color.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(Color(color.colorArgb), RoundedCornerShape(13.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(13.dp)),
        )
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
        IconButton(
            onClick = onFullscreen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                .semantics { contentDescription = text.fullscreenWriting }
                .testTag("drawing_fullscreen_button"),
        ) {
            Icon(Icons.Filled.Fullscreen, contentDescription = null)
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
    isPrivacyLocked: Boolean,
    hasPremiumAccess: Boolean,
    onOpenPremium: () -> Unit,
    onInteract: () -> Unit = {},
    onSetReminder: (Long, String) -> Unit,
    onClearReminder: () -> Unit,
) {
    val context = LocalContext.current
    val submitReminderWithDeliveryCheck = rememberFutureReminderSubmissionGate(text)
    val requireReminderDeliveryReady = rememberReminderDeliveryGate(text)
    var activeDatePickerDialog by remember { mutableStateOf<DatePickerDialog?>(null) }
    var activeTimePickerDialog by remember { mutableStateOf<TimePickerDialog?>(null) }

    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) {
            activeDatePickerDialog?.dismiss()
            activeTimePickerDialog?.dismiss()
            activeDatePickerDialog = null
            activeTimePickerDialog = null
        }
    }

    fun submitReminder(reminderAt: Long) {
        submitReminderWithDeliveryCheck(reminderAt) {
            onSetReminder(reminderAt, note.reminderRepeat)
        }
    }

    fun setRepeat(repeat: ReminderRepeat) {
        if (!hasPremiumAccess) {
            onOpenPremium()
            return
        }
        note.reminderAt?.let { reminderAt ->
            requireReminderDeliveryReady {
                onSetReminder(reminderAt, repeat.code)
            }
        }
    }

    fun openDateTimePicker() {
        if (!hasPremiumAccess) {
            onOpenPremium()
            return
        }
        onInteract()
        val calendar = Calendar.getInstance()
        val initialReminderAt = note.reminderAt?.takeIf { it > System.currentTimeMillis() }
        if (initialReminderAt == null) {
            calendar.add(Calendar.HOUR_OF_DAY, 1)
        } else {
            calendar.timeInMillis = initialReminderAt
        }

        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val timePickerDialog = TimePickerDialog(
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
                )
                timePickerDialog.setOnDismissListener {
                    if (activeTimePickerDialog === timePickerDialog) activeTimePickerDialog = null
                }
                activeTimePickerDialog = timePickerDialog
                timePickerDialog.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        datePickerDialog.setOnDismissListener {
            if (activeDatePickerDialog === datePickerDialog) activeDatePickerDialog = null
        }
        activeDatePickerDialog = datePickerDialog
        datePickerDialog.show()
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
                Text(setReminderActionLabel(text, hasPremiumAccess))
            }
            if (note.reminderAt != null) {
                TextButton(
                    onClick = {
                        onInteract()
                        onClearReminder()
                    },
                    modifier = Modifier.testTag("clear_reminder_button"),
                ) {
                    Text(text.clearReminder)
                }
            }
        }
        if (note.reminderAt != null && hasPremiumAccess) {
            Text(
                text = text.reminderRepeat + ": " + reminderRepeatLabel(note.reminderRepeat, text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("reminder_repeat_status"),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderRepeat.entries.forEach { repeat ->
                    item {
                        FilterChip(
                            selected = normalizedReminderRepeat(note.reminderRepeat) == repeat.code,
                            onClick = { setRepeat(repeat) },
                            label = { Text(reminderRepeatLabel(repeat.code, text)) },
                            modifier = Modifier.testTag("reminder_repeat_" + repeat.name),
                        )
                    }
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
    isPrivacyLocked: Boolean,
    hasPremiumAccess: Boolean,
    onOpenPremium: () -> Unit,
    onInteract: () -> Unit = {},
    onMove: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentFolderName = folderDisplayNameById(currentFolderId, folders, text)
    LaunchedEffect(isPrivacyLocked) {
        if (isPrivacyLocked) expanded = false
    }
    val canOpenFolderPicker = hasPremiumAccess || currentFolderId != DEFAULT_FOLDER_ID
    val folderTargets = if (hasPremiumAccess) folders else folders.filter { it.id == DEFAULT_FOLDER_ID }

    if (!canOpenFolderPicker) return

    Box {
        Button(onClick = {
            expanded = true
        }, modifier = Modifier.testTag("note_folder_selector_button")) {
            Text(currentFolderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = expanded && !isPrivacyLocked,
            onDismissRequest = { expanded = false },
        ) {
            folderTargets.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folderDisplayName(folder, text)) },
                    onClick = {
                        onInteract()
                        expanded = false
                        if (hasPremiumAccess || folder.id == DEFAULT_FOLDER_ID) {
                            onMove(folder.id)
                        } else {
                            onOpenPremium()
                        }
                    },
                    enabled = folder.id != currentFolderId,
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
                modifier = Modifier.testTag("folder_name_confirm_button"),
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
    hasPremiumAccess: Boolean,
    onDismiss: () -> Unit,
    onOpenPremium: () -> Unit,
    onMove: (Long) -> Unit,
) {
    val folderTargets = if (hasPremiumAccess) folders else folders.filter { it.id == DEFAULT_FOLDER_ID }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.moveNote) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                folderTargets.forEach { folder ->
                    Button(
                        onClick = {
                            if (hasPremiumAccess || folder.id == DEFAULT_FOLDER_ID) {
                                onMove(folder.id)
                            } else {
                                onOpenPremium()
                            }
                        },
                        enabled = folder.id != currentFolderId,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("move_note_target_${folder.id}"),
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
    if (note.title.isNotBlank()) return note.title
    if (note.type == NoteTypes.TEXT) {
        firstTextContentTitle(note.textContent.orEmpty())?.let { return it }
    }
    return when (note.type) {
        NoteTypes.DRAWING -> text.untitledDrawing
        NoteTypes.CHECKLIST -> text.untitledChecklist
        else -> text.untitledTextNote
    }
}

private fun firstTextContentTitle(content: String): String? {
    return content
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.take(80)
}

private fun isBlankDraftValues(
    title: String,
    content: String,
    formatting: String,
): Boolean {
    return title.isBlank() && content.isBlank() && formatting.isBlank()
}

const val MARKDOWN_CHECKBOX_MARKER_LENGTH = 6

data class MarkdownCheckboxLine(
    val checked: Boolean,
    val label: String,
)

data class ReadContentLine(
    val lineIndex: Int,
    val start: Int,
    val endExclusive: Int,
    val text: String,
    val checkbox: MarkdownCheckboxLine?,
) {
    val labelStart: Int
        get() = if (checkbox == null) start else (start + MARKDOWN_CHECKBOX_MARKER_LENGTH).coerceAtMost(endExclusive)
    val displayStart: Int
        get() = if (checkbox == null) start else labelStart
    val displayEndExclusive: Int
        get() = endExclusive
    val displayText: String
        get() = checkbox?.label ?: text
    val hasVisibleText: Boolean
        get() = displayStart < displayEndExclusive

    fun rawOffsetForLocalOffset(localOffset: Int): Int {
        val safeLocalOffset = localOffset.coerceIn(0, displayText.length)
        return (displayStart + safeLocalOffset).coerceIn(start, endExclusive)
    }
}

data class ReadContentMatchTarget(
    val lineIndex: Int,
    val localStart: Int,
    val localEndExclusive: Int,
    val hasVisibleText: Boolean,
)

private fun parseMarkdownCheckboxLine(line: String): MarkdownCheckboxLine? {
    return when {
        line.startsWith("- [ ] ") -> MarkdownCheckboxLine(checked = false, label = line.removePrefix("- [ ] "))
        line.startsWith("- [x] ") -> MarkdownCheckboxLine(checked = true, label = line.removePrefix("- [x] "))
        line.startsWith("- [X] ") -> MarkdownCheckboxLine(checked = true, label = line.removePrefix("- [X] "))
        else -> null
    }
}

fun readContentLines(content: String): List<ReadContentLine> {
    val lines = mutableListOf<ReadContentLine>()
    var lineStart = 0
    var lineIndex = 0
    var index = 0
    fun appendLine(endExclusive: Int) {
        val lineText = content.substring(lineStart, endExclusive)
        lines += ReadContentLine(
            lineIndex = lineIndex,
            start = lineStart,
            endExclusive = endExclusive,
            text = lineText,
            checkbox = parseMarkdownCheckboxLine(lineText),
        )
        lineIndex += 1
    }

    while (index < content.length) {
        when (content[index]) {
            '\r' -> {
                appendLine(index)
                index += if (content.getOrNull(index + 1) == '\n') 2 else 1
                lineStart = index
            }
            '\n' -> {
                appendLine(index)
                index += 1
                lineStart = index
            }
            else -> index += 1
        }
    }
    appendLine(content.length)
    return lines
}

fun cropTextFormatRangesForSegment(
    ranges: List<TextFormatRange>,
    contentLength: Int,
    segmentStart: Int,
    segmentEndExclusive: Int,
    displayedStart: Int = segmentStart,
): List<TextFormatRange> {
    val safeContentLength = contentLength.coerceAtLeast(0)
    val safeSegmentStart = segmentStart.coerceIn(0, safeContentLength)
    val safeSegmentEnd = segmentEndExclusive.coerceIn(safeSegmentStart, safeContentLength)
    val localBase = displayedStart.coerceIn(0, safeContentLength)
    if (safeSegmentStart >= safeSegmentEnd) return emptyList()

    val croppedRanges = TextFormattingJson.sanitize(ranges, safeContentLength).mapNotNull { range ->
        if (!range.overlaps(safeSegmentStart, safeSegmentEnd)) return@mapNotNull null
        val croppedStart = max(range.start, safeSegmentStart)
        val croppedEnd = min(range.end, safeSegmentEnd)
        if (croppedStart >= croppedEnd) {
            null
        } else {
            range.copy(
                start = croppedStart - localBase,
                end = croppedEnd - localBase,
            )
        }
    }
    return TextFormattingJson.sanitize(croppedRanges, (safeSegmentEnd - localBase).coerceAtLeast(0))
}

fun readContentMatchTargetForRange(
    lines: List<ReadContentLine>,
    matchRange: IntRange?,
): ReadContentMatchTarget? {
    val range = matchRange ?: return null
    if (lines.isEmpty()) return null
    val matchStart = range.first
    val matchEndExclusive = range.last + 1
    val line = lines.firstOrNull { matchStart >= it.start && matchStart <= it.endExclusive }
        ?: lines.lastOrNull { matchStart >= it.start }
        ?: lines.first()
    val visibleStart = max(matchStart, line.displayStart)
    val visibleEndExclusive = min(matchEndExclusive, line.displayEndExclusive)
    return if (visibleStart < visibleEndExclusive) {
        ReadContentMatchTarget(
            lineIndex = line.lineIndex,
            localStart = visibleStart - line.displayStart,
            localEndExclusive = visibleEndExclusive - line.displayStart,
            hasVisibleText = true,
        )
    } else {
        ReadContentMatchTarget(
            lineIndex = line.lineIndex,
            localStart = 0,
            localEndExclusive = 0,
            hasVisibleText = false,
        )
    }
}

private fun toggleMarkdownCheckboxLine(content: String, lineIndex: Int): String? {
    val line = readContentLines(content).getOrNull(lineIndex) ?: return null
    val checkbox = line.checkbox ?: return null
    val replacementMarker = if (checkbox.checked) "- [ ] " else "- [x] "
    val markerEnd = line.start + MARKDOWN_CHECKBOX_MARKER_LENGTH
    return buildString(content.length) {
        append(content, 0, line.start)
        append(replacementMarker)
        append(content, markerEnd, content.length)
    }
}

private fun continuedListValue(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    if (!oldValue.selection.collapsed) return newValue
    val cursor = oldValue.selection.start
    if (newValue.text.length != oldValue.text.length + 1) return newValue
    if (newValue.text.getOrNull(cursor) != '\n') return newValue
    val lineStart = oldValue.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val currentLine = oldValue.text.substring(lineStart, cursor)
    val marker = when {
        currentLine.startsWith("- [ ] ") || currentLine.startsWith("- [x] ") || currentLine.startsWith("- [X] ") -> "- [ ] "
        currentLine.startsWith("- ") -> "- "
        else -> null
    } ?: return newValue
    val updated = buildString {
        append(newValue.text.substring(0, cursor + 1))
        append(marker)
        append(newValue.text.substring(cursor + 1))
    }
    return newValue.copy(
        text = updated,
        selection = TextRange(cursor + 1 + marker.length),
    )
}

private fun notePreview(note: NoteEntity, query: String): String? {
    if (note.type != NoteTypes.TEXT && note.type != NoteTypes.CHECKLIST) return null

    val content = if (note.type == NoteTypes.CHECKLIST) {
        ChecklistJson.preview(note.textContent)
    } else {
        note.textContent.orEmpty()
    }.replace(Regex("\\s+"), " ").trim()
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

const val WEB_URL_STRING_ANNOTATION_TAG = "web_url"

data class WebUrlRange(
    val range: IntRange,
    val normalizedUrl: String,
)

private val WebUrlRegex = Regex(
    """(?i)\b(?:(?:https?://|www\.)[^\s<>]+|(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}(?:/[^\s<>]*)?)""",
)

private val BareDomainAllowedTlds = setOf(
    "ai",
    "app",
    "biz",
    "ca",
    "cn",
    "co",
    "com",
    "de",
    "dev",
    "edu",
    "fr",
    "gov",
    "info",
    "io",
    "jp",
    "net",
    "org",
    "tw",
    "uk",
    "us",
)

private val ReverseDnsPackagePrefixes = setOf("com", "edu", "gov", "net", "org")

private val UrlTrailingPunctuation = setOf('.', ',', '!', '?', ';', ':', '"', '\'')

private val UrlClosingBrackets = mapOf(
    ')' to '(',
    ']' to '[',
    '}' to '{',
)

fun findHighlightedLinkedText(
    value: String,
    query: String,
    activeMatchIndex: Int,
    formattingRanges: List<TextFormatRange> = emptyList(),
    matchColor: Color,
    activeMatchColor: Color,
    formatHighlightColor: Color = Color.Yellow,
    linkColor: Color,
    linkifyUrls: Boolean = true,
): AnnotatedString {
    val matches = findInNoteMatches(value, query)
    val urls = if (linkifyUrls) value.webUrlRanges() else emptyList()
    val sanitizedFormats = TextFormattingJson.sanitize(formattingRanges, value.length)
    if (matches.isEmpty() && urls.isEmpty() && sanitizedFormats.isEmpty()) return AnnotatedString(value)

    val activeIndex = activeMatchIndex.normalizeFindMatchIndex(matches.size)
    return buildAnnotatedString {
        append(value)
        sanitizedFormats.forEach { formatRange ->
            addStyle(
                style = formatRange.spanStyle(
                    linkColor = linkColor,
                    highlightColor = formatHighlightColor,
                ),
                start = formatRange.start,
                end = formatRange.end,
            )
            if (formatRange.type == TextFormatType.Link && !formatRange.url.isNullOrBlank()) {
                addStringAnnotation(
                    tag = WEB_URL_STRING_ANNOTATION_TAG,
                    annotation = formatRange.url,
                    start = formatRange.start,
                    end = formatRange.end,
                )
            }
        }
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
        urls.forEach { urlRange ->
            addStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                ),
                start = urlRange.range.first,
                end = urlRange.range.last + 1,
            )
            addStringAnnotation(
                tag = WEB_URL_STRING_ANNOTATION_TAG,
                annotation = urlRange.normalizedUrl,
                start = urlRange.range.first,
                end = urlRange.range.last + 1,
            )
        }
    }
}

fun findHighlightedLinkedTextSegment(
    value: String,
    absoluteStart: Int,
    absoluteEndExclusive: Int,
    contentLength: Int,
    globalMatches: List<IntRange>,
    activeMatchIndex: Int,
    formattingRanges: List<TextFormatRange> = emptyList(),
    matchColor: Color,
    activeMatchColor: Color,
    formatHighlightColor: Color = Color.Yellow,
    linkColor: Color,
    linkifyUrls: Boolean = true,
): AnnotatedString {
    val urls = if (linkifyUrls) value.webUrlRanges() else emptyList()
    val croppedFormats = TextFormattingJson.sanitize(
        cropTextFormatRangesForSegment(
            ranges = formattingRanges,
            contentLength = contentLength,
            segmentStart = absoluteStart,
            segmentEndExclusive = absoluteEndExclusive,
            displayedStart = absoluteStart,
        ),
        value.length,
    )
    val activeIndex = activeMatchIndex.normalizeFindMatchIndex(globalMatches.size)
    val hasVisibleMatches = globalMatches.any { range ->
        val localStart = max(range.first, absoluteStart) - absoluteStart
        val localEndExclusive = min(range.last + 1, absoluteEndExclusive) - absoluteStart
        localStart.coerceIn(0, value.length) < localEndExclusive.coerceIn(0, value.length)
    }
    if (urls.isEmpty() && croppedFormats.isEmpty() && !hasVisibleMatches) return AnnotatedString(value)

    return buildAnnotatedString {
        append(value)
        croppedFormats.forEach { formatRange ->
            addStyle(
                style = formatRange.spanStyle(
                    linkColor = linkColor,
                    highlightColor = formatHighlightColor,
                ),
                start = formatRange.start,
                end = formatRange.end,
            )
            if (formatRange.type == TextFormatType.Link && !formatRange.url.isNullOrBlank()) {
                addStringAnnotation(
                    tag = WEB_URL_STRING_ANNOTATION_TAG,
                    annotation = formatRange.url,
                    start = formatRange.start,
                    end = formatRange.end,
                )
            }
        }
        globalMatches.forEachIndexed { index, range ->
            val localStart = (max(range.first, absoluteStart) - absoluteStart).coerceIn(0, value.length)
            val localEndExclusive = (min(range.last + 1, absoluteEndExclusive) - absoluteStart)
                .coerceIn(localStart, value.length)
            if (localStart < localEndExclusive) {
                addStyle(
                    SpanStyle(
                        background = if (index == activeIndex) activeMatchColor else matchColor,
                        fontWeight = if (index == activeIndex) FontWeight.Bold else null,
                    ),
                    start = localStart,
                    end = localEndExclusive,
                )
            }
        }
        urls.forEach { urlRange ->
            addStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                ),
                start = urlRange.range.first,
                end = urlRange.range.last + 1,
            )
            addStringAnnotation(
                tag = WEB_URL_STRING_ANNOTATION_TAG,
                annotation = urlRange.normalizedUrl,
                start = urlRange.range.first,
                end = urlRange.range.last + 1,
            )
        }
    }
}

private fun TextFormatRange.spanStyle(linkColor: Color, highlightColor: Color): SpanStyle {
    return when (type) {
        TextFormatType.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
        TextFormatType.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
        TextFormatType.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
        TextFormatType.Highlight -> SpanStyle(background = highlightColor)
        TextFormatType.Link -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        TextFormatType.Heading1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.35f.em)
        TextFormatType.Heading2 -> SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 1.18f.em)
    }
}

fun String.webUrlRanges(): List<WebUrlRange> {
    return WebUrlRegex.findAll(this).mapNotNull { match ->
        if (match.range.first > 0 && this[match.range.first - 1] == '@') return@mapNotNull null

        val matchEndExclusive = match.range.last + 1
        if (matchEndExclusive < length && this[matchEndExclusive] == '@') return@mapNotNull null

        val endExclusive = trimmedWebUrlEnd(match.range.first, matchEndExclusive)
        if (endExclusive <= match.range.first) return@mapNotNull null

        val rawUrl = substring(match.range.first, endExclusive)
        if (!rawUrl.shouldLinkifyWebUrl()) return@mapNotNull null

        val normalizedUrl = rawUrl.normalizedWebUrl()
        if (!normalizedUrl.startsWith("http://", ignoreCase = true) &&
            !normalizedUrl.startsWith("https://", ignoreCase = true)
        ) {
            null
        } else {
            WebUrlRange(
                range = match.range.first until endExclusive,
                normalizedUrl = normalizedUrl,
            )
        }
    }.toList()
}

private fun String.shouldLinkifyWebUrl(): Boolean {
    if (startsWith("http://", ignoreCase = true) ||
        startsWith("https://", ignoreCase = true) ||
        startsWith("www.", ignoreCase = true)
    ) {
        return true
    }

    val host = substringBefore('/')
    val labels = host.split('.')
    if (labels.size < 2) return false

    val firstLabel = labels.first().lowercase()
    val topLevelDomain = labels.last().lowercase()
    return topLevelDomain in BareDomainAllowedTlds &&
        !(labels.size >= 3 && firstLabel in ReverseDnsPackagePrefixes)
}

private fun String.trimmedWebUrlEnd(startInclusive: Int, endExclusive: Int): Int {
    var trimmedEnd = endExclusive
    while (trimmedEnd > startInclusive && this[trimmedEnd - 1] in UrlTrailingPunctuation) {
        trimmedEnd -= 1
    }
    while (trimmedEnd > startInclusive && hasUnmatchedTrailingClosingBracket(startInclusive, trimmedEnd)) {
        trimmedEnd -= 1
    }
    return trimmedEnd
}

private fun String.hasUnmatchedTrailingClosingBracket(startInclusive: Int, endExclusive: Int): Boolean {
    val closingBracket = this[endExclusive - 1]
    val openingBracket = UrlClosingBrackets[closingBracket] ?: return false
    var balance = 0
    for (index in startInclusive until endExclusive) {
        when (this[index]) {
            openingBracket -> balance += 1
            closingBracket -> balance -= 1
        }
    }
    return balance < 0
}

private fun String.normalizedWebUrl(): String {
    return if (startsWith("http://", ignoreCase = true) ||
        startsWith("https://", ignoreCase = true)
    ) {
        this
    } else {
        "https://$this"
    }
}

fun AnnotatedString.webUrlAt(offset: Int): String? {
    if (offset !in 0 until length) return null
    return getStringAnnotations(
        tag = WEB_URL_STRING_ANNOTATION_TAG,
        start = offset,
        end = offset + 1,
    ).firstOrNull()?.item
}

private fun openWebUrl(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    if (uri.scheme?.equals("http", ignoreCase = true) != true &&
        uri.scheme?.equals("https", ignoreCase = true) != true
    ) {
        return false
    }

    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        false
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
    private val formattingRanges: List<TextFormatRange> = emptyList(),
    private val linkColor: Color,
    private val formatHighlightColor: Color,
    private val matchColor: Color,
    private val activeMatchColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val annotated = findHighlightedLinkedText(
            value = text.text,
            query = query,
            activeMatchIndex = activeMatchIndex,
            formattingRanges = formattingRanges,
            matchColor = matchColor,
            activeMatchColor = activeMatchColor,
            formatHighlightColor = formatHighlightColor,
            linkColor = linkColor,
            linkifyUrls = false,
        )
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
    return "$folderName • $timestamps$pinned"
}

private fun relativeUpdatedTime(
    updatedAt: Long,
    nowMillis: Long,
    language: AppLanguage,
): String {
    val elapsedSeconds = ((nowMillis - updatedAt).coerceAtLeast(0L)) / 1_000L
    return when {
        elapsedSeconds < 60L -> {
            if (language == AppLanguage.TraditionalChinese) "剛剛" else "Just now"
        }
        elapsedSeconds < 3_600L -> {
            val minutes = (elapsedSeconds / 60L).coerceAtLeast(1L)
            if (language == AppLanguage.TraditionalChinese) "${minutes} 分鐘前" else "${minutes} min ago"
        }
        elapsedSeconds < 86_400L -> {
            val hours = (elapsedSeconds / 3_600L).coerceAtLeast(1L)
            if (language == AppLanguage.TraditionalChinese) "${hours} 小時前" else "${hours} hr ago"
        }
        elapsedSeconds < 172_800L -> {
            if (language == AppLanguage.TraditionalChinese) "昨天" else "Yesterday"
        }
        else -> {
            val locale = when (language) {
                AppLanguage.English -> Locale.ENGLISH
                AppLanguage.TraditionalChinese -> Locale.TRADITIONAL_CHINESE
            }
            DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(updatedAt))
        }
    }
}

private fun reminderRowSummary(
    note: NoteEntity,
    text: UiText,
    appLanguage: AppLanguage,
): String? {
    val reminderAt = note.reminderAt ?: return null
    val status = if (reminderAt <= System.currentTimeMillis()) {
        text.reminderOverdue
    } else {
        text.reminderUpcoming
    }
    val repeat = normalizedReminderRepeat(note.reminderRepeat)
        .takeIf { it != ReminderRepeat.None.code }
        ?.let { " • ${text.reminderRepeat}: ${reminderRepeatLabel(it, text)}" }
        .orEmpty()
    return "${text.reminder}: ${formatTime(reminderAt, appLanguage)} • $status$repeat"
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
        NoteTypeFilter.Checklist -> text.checklistNotes
    }
}

private fun NoteQuickFilter.label(text: UiText): String {
    return when (this) {
        NoteQuickFilter.All -> text.all
        NoteQuickFilter.Text -> text.textNotes
        NoteQuickFilter.Drawing -> text.drawingNotes
        NoteQuickFilter.Checklist -> text.checklistNotes
        NoteQuickFilter.HasReminder -> text.hasReminder
        NoteQuickFilter.Pinned -> text.pinned
    }
}

private fun noteTypeLabel(type: String, text: UiText): String {
    return when (type) {
        NoteTypes.DRAWING -> text.drawing
        NoteTypes.CHECKLIST -> text.checklist
        else -> text.text
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

private fun reminderRepeatLabel(reminderRepeat: String, text: UiText): String {
    return when (normalizedReminderRepeat(reminderRepeat)) {
        ReminderRepeat.Daily.code -> text.reminderRepeatDaily
        ReminderRepeat.Weekly.code -> text.reminderRepeatWeekly
        ReminderRepeat.Monthly.code -> text.reminderRepeatMonthly
        else -> text.reminderRepeatNone
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

private fun SaveStatus.label(text: UiText, appLanguage: AppLanguage): String {
    return when (this) {
        SaveStatus.Saving -> text.saving
        SaveStatus.Synced -> text.saved
        SaveStatus.Saved -> savedJustNowLabel(appLanguage)
        SaveStatus.Failed -> saveFailedLabel(appLanguage)
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

private fun startOfDayMillis(timestamp: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun startOfMonthMillis(timestamp: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun addMonths(monthStart: Long, months: Int): Long {
    return Calendar.getInstance().apply {
        timeInMillis = startOfMonthMillis(monthStart)
        add(Calendar.MONTH, months)
    }.timeInMillis
}

private fun addDays(dayStart: Long, days: Int): Long {
    return Calendar.getInstance().apply {
        timeInMillis = startOfDayMillis(dayStart)
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis
}

private fun reminderTimeOnDay(dayStart: Long, hour: Int, minute: Int = 0): Long {
    return Calendar.getInstance().apply {
        timeInMillis = startOfDayMillis(dayStart)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun nextHourReminderTime(nowMillis: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun monthDayStarts(monthStart: Long): List<Long?> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = startOfMonthMillis(monthStart)
    }
    val leadingBlankDays = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    return List(42) { index ->
        val dayOfMonth = index - leadingBlankDays + 1
        if (dayOfMonth in 1..daysInMonth) {
            Calendar.getInstance().apply {
                timeInMillis = monthStart
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else {
            null
        }
    }
}

private fun dayOfMonth(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.DAY_OF_MONTH)
}

private fun calendarMonthTitle(monthStart: Long, language: AppLanguage): String {
    val locale = when (language) {
        AppLanguage.English -> Locale.ENGLISH
        AppLanguage.TraditionalChinese -> Locale.TRADITIONAL_CHINESE
    }
    return SimpleDateFormat("LLLL yyyy", locale).format(Date(monthStart))
}

private fun calendarDateTitle(dayStart: Long, language: AppLanguage): String {
    val locale = when (language) {
        AppLanguage.English -> Locale.ENGLISH
        AppLanguage.TraditionalChinese -> Locale.TRADITIONAL_CHINESE
    }
    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(dayStart))
}

private fun weekdayLabels(language: AppLanguage): List<String> {
    return when (language) {
        AppLanguage.English -> listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        AppLanguage.TraditionalChinese -> listOf("日", "一", "二", "三", "四", "五", "六")
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

private fun restoreBackupPreviewBody(
    backupPreview: BackupPreview,
    currentPreview: BackupPreview,
    text: UiText,
    appLanguage: AppLanguage,
): String {
    val exportedAt = backupPreview.exportedAt
        ?.let { formatTime(it, appLanguage) }
        ?: if (appLanguage == AppLanguage.TraditionalChinese) "未知" else "Unknown"

    return if (appLanguage == AppLanguage.TraditionalChinese) {
        buildString {
            appendLine("備份檔：")
            appendLine("記事：${backupPreview.activeNoteCount} 筆一般，${backupPreview.deletedNoteCount} 筆${text.trash}")
            appendLine("資料夾：${backupPreview.folderCount} 個")
            appendLine("匯出時間：$exportedAt")
            appendLine()
            appendLine("此裝置目前：")
            appendLine("記事：${currentPreview.activeNoteCount} 筆一般，${currentPreview.deletedNoteCount} 筆${text.trash}")
            appendLine("資料夾：${currentPreview.folderCount} 個")
            appendLine()
            appendLine("還原會用這份備份取代目前所有本機記事與資料夾。")
            append("還原前會先在此裝置建立私有檢查點，還原後可在再次還原或復原前復原。")
        }
    } else {
        buildString {
            appendLine("Backup file:")
            appendLine("Notes: ${backupPreview.activeNoteCount} active, ${backupPreview.deletedNoteCount} in Trash")
            appendLine("Folders: ${backupPreview.folderCount}")
            appendLine("Exported: $exportedAt")
            appendLine()
            appendLine("This device now:")
            appendLine("Notes: ${currentPreview.activeNoteCount} active, ${currentPreview.deletedNoteCount} in Trash")
            appendLine("Folders: ${currentPreview.folderCount}")
            appendLine()
            appendLine("Restoring will replace all current local notes and folders with this backup.")
            append("Just Notes will first save a private checkpoint on this device, so you can undo the restore until another restore runs or you undo it.")
        }
    }
}

private fun restoreRollbackPreviewBody(
    rollbackPreview: BackupPreview,
    currentPreview: BackupPreview,
    text: UiText,
    appLanguage: AppLanguage,
): String {
    return if (appLanguage == AppLanguage.TraditionalChinese) {
        buildString {
            appendLine("還原前檢查點：")
            appendLine("記事：${rollbackPreview.activeNoteCount} 筆一般，${rollbackPreview.deletedNoteCount} 筆${text.trash}")
            appendLine("資料夾：${rollbackPreview.folderCount} 個")
            appendLine()
            appendLine("此裝置目前：")
            appendLine("記事：${currentPreview.activeNoteCount} 筆一般，${currentPreview.deletedNoteCount} 筆${text.trash}")
            appendLine("資料夾：${currentPreview.folderCount} 個")
            appendLine()
            append("復原會用檢查點取代目前所有本機記事與資料夾。")
        }
    } else {
        buildString {
            appendLine("Restore checkpoint:")
            appendLine("Notes: ${rollbackPreview.activeNoteCount} active, ${rollbackPreview.deletedNoteCount} in Trash")
            appendLine("Folders: ${rollbackPreview.folderCount}")
            appendLine()
            appendLine("This device now:")
            appendLine("Notes: ${currentPreview.activeNoteCount} active, ${currentPreview.deletedNoteCount} in Trash")
            appendLine("Folders: ${currentPreview.folderCount}")
            appendLine()
            append("Undo restore will replace all current local notes and folders with the checkpoint.")
        }
    }
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

private fun displayNameForUri(context: Context, uri: Uri): String {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                return cursor.getString(index).orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
            }
        }
    }
    return uri.lastPathSegment.orEmpty().ifBlank { "Imported note.txt" }
}

private fun persistUriPermission(context: Context, uri: Uri, modeFlags: Int) {
    listOf(Intent.FLAG_GRANT_READ_URI_PERMISSION, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        .filter { modeFlags and it != 0 }
        .forEach { flag ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, flag)
            } catch (_: SecurityException) {
                // Some providers grant only one-time access for this flag. The immediate action can still proceed.
            } catch (_: IllegalArgumentException) {
                // File providers that do not support persistable permissions are still valid for one-time use.
            }
        }
}

private fun SyncStatus.label(text: UiText): String {
    return when (this) {
        SyncStatus.SetupRequired -> text.notSignedIn
        SyncStatus.SignedOut -> text.notSignedIn
        SyncStatus.Idle -> text.syncNow
        SyncStatus.Syncing -> text.syncStatus
        SyncStatus.Succeeded -> text.syncComplete
        SyncStatus.Failed -> text.syncFailed
        SyncStatus.Conflict -> text.syncConflict
    }
}
