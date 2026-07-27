package com.example.notepad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.notepad.data.AppLanguage
import com.example.notepad.data.ChecklistJson
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteTemplate
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.TodayNoteSections

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
        ) {
            LazyColumn(
                modifier = Modifier.testTag("first_run_welcome_scroll"),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        text = text.welcomeTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("first_run_title"),
                    )
                }
                item {
                    Text(text.welcomeBody, style = MaterialTheme.typography.bodyLarge)
                }
                item {
                    Text(
                        text = text.localFirstBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("first_run_privacy_copy"),
                    )
                }
                item {
                    Button(
                        onClick = onNewNote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("first_run_new_note"),
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
                .padding(20.dp)
                .heightIn(max = 480.dp)
                .testTag("starter_hub"),
        ) {
            LazyColumn(
                modifier = Modifier.testTag("starter_hub_scroll"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = text.starterTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                item {
                    Text(
                        text = text.starterBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Button(
                        enabled = enabled,
                        onClick = onNewNote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("starter_new_note"),
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = text.today,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = text.todayBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                ) {
                    Text(text.newNote)
                }
                OutlinedButton(
                    onClick = onNewChecklist,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("today_new_checklist"),
                ) {
                    Text(text.checklist)
                }
                OutlinedButton(
                    onClick = onOpenReminders,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("today_open_reminders"),
                ) {
                    Text(text.reminder)
                }
            }
        }
        if (sections.isEmpty) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = text.todayEmpty,
                        modifier = Modifier
                            .padding(20.dp)
                            .testTag("today_empty"),
                    )
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("today_section_$tag"),
        )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("today_note_${note.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (preview.isNotBlank()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
