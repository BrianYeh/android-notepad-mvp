package com.example.notepad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.notepad.data.AppLanguage
import com.example.notepad.data.ChecklistJson
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteTemplate
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.TodayNoteSections

private object StationeryColors {
    val Paper = StationeryPalette.PaperRaised
    val Cream = StationeryPalette.Paper
    val Blush = StationeryPalette.Blush
    val Mint = StationeryPalette.ForestSoft
    val Lavender = StationeryPalette.Lavender
    val Butter = StationeryPalette.Butter
    val Forest = StationeryPalette.Forest
    val Rose = StationeryPalette.Berry
    val Ink = StationeryPalette.Ink
    val PencilLine = StationeryPalette.OutlineSoft
}

private val StationeryCardShape = StationeryShapes.extraLarge
private val StationeryButtonShape = StationeryShapes.medium

@Composable
private fun StationeryDoodle(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clearAndSetSemantics {},
    ) {
        val noteHeight = size.height * 0.68f
        val noteWidth = size.width * 0.25f
        val top = size.height * 0.16f
        val radius = CornerRadius(12.dp.toPx())

        drawRoundRect(
            color = StationeryColors.Butter,
            topLeft = Offset(size.width * 0.08f, top + size.height * 0.08f),
            size = Size(noteWidth, noteHeight),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = StationeryColors.Lavender,
            topLeft = Offset(size.width * 0.36f, top),
            size = Size(noteWidth, noteHeight),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = StationeryColors.Blush,
            topLeft = Offset(size.width * 0.64f, top + size.height * 0.06f),
            size = Size(noteWidth, noteHeight),
            cornerRadius = radius,
        )

        val dotRadius = 2.5.dp.toPx()
        drawCircle(
            color = StationeryColors.Forest.copy(alpha = 0.7f),
            radius = dotRadius,
            center = Offset(size.width * 0.16f, size.height * 0.55f),
        )
        drawCircle(
            color = StationeryColors.Forest.copy(alpha = 0.7f),
            radius = dotRadius,
            center = Offset(size.width * 0.44f, size.height * 0.47f),
        )
        drawCircle(
            color = StationeryColors.Rose.copy(alpha = 0.72f),
            radius = dotRadius,
            center = Offset(size.width * 0.72f, size.height * 0.53f),
        )
        drawLine(
            color = StationeryColors.Forest.copy(alpha = 0.55f),
            start = Offset(size.width * 0.87f, size.height * 0.8f),
            end = Offset(size.width * 0.91f, size.height * 0.28f),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawCircle(
            color = StationeryColors.Mint,
            radius = 5.dp.toPx(),
            center = Offset(size.width * 0.88f, size.height * 0.5f),
        )
        drawCircle(
            color = StationeryColors.Mint,
            radius = 4.dp.toPx(),
            center = Offset(size.width * 0.92f, size.height * 0.4f),
        )
    }
}

@Composable
private fun StationeryTape(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .width(58.dp)
            .height(11.dp)
            .clearAndSetSemantics {},
    ) {
        drawRoundRect(
            color = color.copy(alpha = 0.78f),
            size = size,
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.42f),
            start = Offset(size.width * 0.14f, size.height * 0.48f),
            end = Offset(size.width * 0.86f, size.height * 0.48f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

internal data class V109Text(
    val welcomeTitle: String,
    val welcomeBody: String,
    val localFirstBody: String,
    val newNote: String,
    val chooseTemplate: String,
    val skip: String,
    val starterTitle: String,
    val starterBody: String,
    val templatesTitle: String,
    val cancel: String,
    val today: String,
    val todayBody: String,
    val checklist: String,
    val reminder: String,
    val overdue: String,
    val dueToday: String,
    val pinned: String,
    val recent: String,
    val todayEmpty: String,
    val clearSearchAndFilters: String,
    val untitled: String,
    val drawingNote: String,
)

internal fun v109Text(language: AppLanguage): V109Text {
    return when (language) {
        AppLanguage.English -> V109Text(
            welcomeTitle = "Capture it now. Find it when it matters",
            welcomeBody = "Start with a quick note or a ready-made template.",
            localFirstBody = "Your notes stay local-first on this device. You choose if and when to back them up.",
            newNote = "New note",
            chooseTemplate = "Choose template",
            skip = "Skip",
            starterTitle = "What do you want to remember?",
            starterBody = "Capture a thought now, or start from a simple template.",
            templatesTitle = "Choose a template",
            cancel = "Cancel",
            today = "Today",
            todayBody = "What needs your attention now",
            checklist = "Checklist",
            reminder = "Reminder",
            overdue = "Overdue",
            dueToday = "Due today",
            pinned = "Pinned",
            recent = "Recent",
            todayEmpty = "Nothing needs attention yet. Capture a note when something comes up.",
            clearSearchAndFilters = "Clear search and filters",
            untitled = "Untitled note",
            drawingNote = "Drawing note",
        )

        AppLanguage.TraditionalChinese -> V109Text(
            welcomeTitle = "現在記下，重要時找得到",
            welcomeBody = "快速新增記事，或從實用範本開始。",
            localFirstBody = "記事以本機優先保存在這台裝置；是否備份、何時備份都由你決定。",
            newNote = "新增記事",
            chooseTemplate = "選擇範本",
            skip = "略過",
            starterTitle = "現在想記住什麼？",
            starterBody = "先記下一個想法，或從簡單範本開始。",
            templatesTitle = "選擇範本",
            cancel = "取消",
            today = "今天",
            todayBody = "現在值得留意的內容",
            checklist = "待辦清單",
            reminder = "提醒",
            overdue = "已逾期",
            dueToday = "今天到期",
            pinned = "已釘選",
            recent = "最近",
            todayEmpty = "目前沒有需要留意的內容。有事情時，先快速記下來。",
            clearSearchAndFilters = "清除搜尋與篩選",
            untitled = "未命名記事",
            drawingNote = "繪圖記事",
        )
    }
}

@Composable
internal fun FirstRunWelcome(
    text: V109Text,
    onNewNote: () -> Unit,
    onChooseTemplate: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onSkip) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .testTag("first_run_welcome"),
            shape = StationeryCardShape,
            colors = CardDefaults.cardColors(containerColor = StationeryColors.Paper),
            border = BorderStroke(1.dp, StationeryColors.Blush),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            LazyColumn(
                modifier = Modifier.testTag("first_run_welcome_scroll"),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StationeryDoodle()
                        Text(
                            text = text.welcomeTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StationeryColors.Ink,
                            modifier = Modifier.testTag("first_run_title"),
                        )
                    }
                }
                item {
                    Text(
                        text = text.welcomeBody,
                        style = MaterialTheme.typography.bodyLarge,
                        color = StationeryColors.Ink,
                    )
                }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = StationeryColors.Mint,
                    ) {
                        Text(
                            text = text.localFirstBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StationeryColors.Ink,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("first_run_privacy_copy"),
                        )
                    }
                }
                item {
                    Button(
                        onClick = onNewNote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("first_run_new_note"),
                        shape = StationeryButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StationeryColors.Forest,
                            contentColor = StationeryColors.Paper,
                        ),
                    ) {
                        Text(text.newNote)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onChooseTemplate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("first_run_choose_template"),
                        shape = StationeryButtonShape,
                        border = BorderStroke(1.dp, StationeryColors.Forest),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = StationeryColors.Lavender.copy(alpha = 0.72f),
                            contentColor = StationeryColors.Forest,
                        ),
                    ) {
                        Text(text.chooseTemplate)
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.testTag("first_run_skip"),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = StationeryColors.Rose,
                            ),
                        ) {
                            Text(text.skip)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StarterHub(
    text: V109Text,
    enabled: Boolean,
    onNewNote: () -> Unit,
    onChooseTemplate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .heightIn(max = 480.dp)
                .testTag("starter_hub"),
            shape = StationeryCardShape,
            colors = CardDefaults.cardColors(containerColor = StationeryColors.Paper),
            border = BorderStroke(1.dp, StationeryColors.PencilLine),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            LazyColumn(
                modifier = Modifier.testTag("starter_hub_scroll"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StationeryDoodle()
                        Text(
                            text = text.starterTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = StationeryColors.Ink,
                        )
                    }
                }
                item {
                    Text(
                        text = text.starterBody,
                        color = StationeryColors.Ink.copy(alpha = 0.78f),
                    )
                }
                item {
                    Button(
                        enabled = enabled,
                        onClick = onNewNote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("starter_new_note"),
                        shape = StationeryButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StationeryColors.Forest,
                            contentColor = StationeryColors.Paper,
                        ),
                    ) {
                        Text(text.newNote)
                    }
                }
                item {
                    OutlinedButton(
                        enabled = enabled,
                        onClick = onChooseTemplate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("starter_choose_template"),
                        shape = StationeryButtonShape,
                        border = BorderStroke(1.dp, StationeryColors.Forest),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = StationeryColors.Blush.copy(alpha = 0.64f),
                            contentColor = StationeryColors.Forest,
                        ),
                    ) {
                        Text(text.chooseTemplate)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TemplatePickerDialog(
    templates: List<NoteTemplate>,
    text: V109Text,
    onSelect: (NoteTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("template_picker"),
        title = { Text(text.templatesTitle) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(templates, key = { template -> template.id }) { template ->
                    TextButton(
                        onClick = { onSelect(template) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("template_${template.id.name}"),
                    ) {
                        Text(
                            text = template.title,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("template_picker_cancel"),
            ) {
                Text(text.cancel)
            }
        },
    )
}

@Composable
internal fun TodayHub(
    sections: TodayNoteSections,
    text: V109Text,
    isPrivacyLocked: Boolean,
    onNewNote: () -> Unit,
    onNewChecklist: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenNote: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isPrivacyLocked) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("today_privacy_locked"),
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("today_hub"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = StationeryCardShape,
                colors = CardDefaults.cardColors(containerColor = StationeryColors.Cream),
                border = BorderStroke(1.dp, StationeryColors.Butter),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StationeryDoodle()
                    Text(
                        text = text.today,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StationeryColors.Ink,
                    )
                    Text(
                        text = text.todayBody,
                        color = StationeryColors.Ink.copy(alpha = 0.76f),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onNewNote,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("today_new_note"),
                    shape = StationeryButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StationeryColors.Forest,
                        contentColor = StationeryColors.Paper,
                    ),
                ) {
                    Text(text.newNote)
                }
                OutlinedButton(
                    onClick = onNewChecklist,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("today_new_checklist"),
                    shape = StationeryButtonShape,
                    border = BorderStroke(1.dp, StationeryColors.Forest),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StationeryColors.Mint,
                        contentColor = StationeryColors.Forest,
                    ),
                ) {
                    Text(text.checklist)
                }
                OutlinedButton(
                    onClick = onOpenReminders,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("today_open_reminders"),
                    shape = StationeryButtonShape,
                    border = BorderStroke(1.dp, StationeryColors.Rose),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StationeryColors.Lavender,
                        contentColor = StationeryColors.Rose,
                    ),
                ) {
                    Text(text.reminder)
                }
            }
        }
        if (sections.isEmpty) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = StationeryCardShape,
                    colors = CardDefaults.cardColors(containerColor = StationeryColors.Butter.copy(alpha = 0.68f)),
                    border = BorderStroke(1.dp, StationeryColors.PencilLine),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StationeryTape(color = StationeryColors.Blush)
                        Text(
                            text = text.todayEmpty,
                            color = StationeryColors.Ink,
                            modifier = Modifier.testTag("today_empty"),
                        )
                    }
                }
            }
        } else {
            todaySection(text.overdue, "overdue", sections.overdue, text, onOpenNote)
            todaySection(text.dueToday, "due_today", sections.dueToday, text, onOpenNote)
            todaySection(text.pinned, "pinned", sections.pinned, text, onOpenNote)
            todaySection(text.recent, "recent", sections.recent, text, onOpenNote)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.todaySection(
    title: String,
    tag: String,
    notes: List<NoteEntity>,
    text: V109Text,
    onOpenNote: (NoteEntity) -> Unit,
) {
    if (notes.isEmpty()) return
    item {
        val labelColor = when (tag) {
            "overdue" -> StationeryColors.Blush
            "due_today" -> StationeryColors.Butter
            "pinned" -> StationeryColors.Lavender
            else -> StationeryColors.Mint
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = labelColor,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = StationeryColors.Ink,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("today_section_$tag"),
            )
        }
    }
    items(notes, key = { note -> "$tag:${note.id}" }) { note ->
        TodayNoteCard(
            note = note,
            text = text,
            onOpen = { onOpenNote(note) },
        )
    }
}

@Composable
private fun TodayNoteCard(
    note: NoteEntity,
    text: V109Text,
    onOpen: () -> Unit,
) {
    val title = note.title.ifBlank {
        when (note.type) {
            NoteTypes.DRAWING -> text.drawingNote
            else -> text.untitled
        }
    }
    val preview = when (note.type) {
        NoteTypes.DRAWING -> ""
        NoteTypes.CHECKLIST -> ChecklistJson.preview(note.textContent)
        else -> note.textContent.orEmpty()
            .lineSequence()
            .firstOrNull { line -> line.isNotBlank() }
            .orEmpty()
    }
    val paperColor = when (((note.id % 4L) + 4L) % 4L) {
        0L -> StationeryColors.Cream
        1L -> StationeryColors.Blush.copy(alpha = 0.74f)
        2L -> StationeryColors.Mint.copy(alpha = 0.82f)
        else -> StationeryColors.Lavender.copy(alpha = 0.78f)
    }
    val tapeColor = when (((note.id % 3L) + 3L) % 3L) {
        0L -> StationeryColors.Blush
        1L -> StationeryColors.Mint
        else -> StationeryColors.Butter
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("today_note_${note.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = paperColor),
        border = BorderStroke(1.dp, StationeryColors.PencilLine.copy(alpha = 0.82f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            StationeryTape(
                color = tapeColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 7.dp),
            )
            Column(
                modifier = Modifier.padding(start = 18.dp, top = 24.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = StationeryColors.Ink,
                )
                if (preview.isNotBlank()) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StationeryColors.Ink.copy(alpha = 0.72f),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
