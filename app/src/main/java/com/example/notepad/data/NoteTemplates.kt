package com.example.notepad.data

enum class NoteTemplateId {
    DailyChecklist,
    ShoppingList,
    MeetingClassNotes,
    Journal,
    TripPreparation,
    ImportantDates,
}

data class NoteTemplate(
    val id: NoteTemplateId,
    val type: String,
    val title: String,
    val textContent: String? = null,
    val checklistItems: List<String> = emptyList(),
)

fun builtInNoteTemplates(language: AppLanguage): List<NoteTemplate> {
    return when (language) {
        AppLanguage.English -> englishTemplates
        AppLanguage.TraditionalChinese -> traditionalChineseTemplates
    }
}

private val englishTemplates = listOf(
    NoteTemplate(
        id = NoteTemplateId.DailyChecklist,
        type = NoteTypes.TEXT,
        title = "Daily checklist",
        textContent = "- [ ] Top priority\n- [ ] One small win\n- [ ] Prepare for tomorrow",
    ),
    NoteTemplate(
        id = NoteTemplateId.ShoppingList,
        type = NoteTypes.TEXT,
        title = "Shopping list",
        textContent = "- [ ] Groceries\n- [ ] Household item\n- [ ] Something easy to forget",
    ),
    NoteTemplate(
        id = NoteTemplateId.MeetingClassNotes,
        type = NoteTypes.TEXT,
        title = "Meeting / class notes",
        textContent = "Topic:\n\nKey points:\n\nDecisions or takeaways:\n\nNext steps:",
    ),
    NoteTemplate(
        id = NoteTemplateId.Journal,
        type = NoteTypes.TEXT,
        title = "Journal",
        textContent = "What happened today?\n\nWhat am I grateful for?\n\nWhat will I do next?",
    ),
    NoteTemplate(
        id = NoteTemplateId.TripPreparation,
        type = NoteTypes.TEXT,
        title = "Trip preparation",
        textContent = "- [ ] Tickets and reservations\n- [ ] Identification\n- [ ] Packing list\n- [ ] Home checklist",
    ),
    NoteTemplate(
        id = NoteTemplateId.ImportantDates,
        type = NoteTypes.TEXT,
        title = "Important dates",
        textContent = "Date — event — reminder\n\n",
    ),
)

private val traditionalChineseTemplates = listOf(
    NoteTemplate(
        id = NoteTemplateId.DailyChecklist,
        type = NoteTypes.TEXT,
        title = "每日待辦",
        textContent = "- [ ] 今天最重要的事\n- [ ] 完成一件小事\n- [ ] 為明天做準備",
    ),
    NoteTemplate(
        id = NoteTemplateId.ShoppingList,
        type = NoteTypes.TEXT,
        title = "購物清單",
        textContent = "- [ ] 食品雜貨\n- [ ] 居家用品\n- [ ] 容易忘記的東西",
    ),
    NoteTemplate(
        id = NoteTemplateId.MeetingClassNotes,
        type = NoteTypes.TEXT,
        title = "會議／課堂筆記",
        textContent = "主題：\n\n重點：\n\n決定或心得：\n\n下一步：",
    ),
    NoteTemplate(
        id = NoteTemplateId.Journal,
        type = NoteTypes.TEXT,
        title = "日記",
        textContent = "今天發生了什麼？\n\n今天感謝什麼？\n\n接下來要做什麼？",
    ),
    NoteTemplate(
        id = NoteTemplateId.TripPreparation,
        type = NoteTypes.TEXT,
        title = "旅行準備",
        textContent = "- [ ] 票券與預訂\n- [ ] 證件\n- [ ] 行李清單\n- [ ] 出門前居家確認",
    ),
    NoteTemplate(
        id = NoteTemplateId.ImportantDates,
        type = NoteTypes.TEXT,
        title = "重要日期",
        textContent = "日期 — 事件 — 提醒\n\n",
    ),
)
