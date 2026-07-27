package com.example.notepad.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTemplatesTest {
    @Test
    fun bothLanguagesProvideEveryBuiltInTemplateWithUsableContent() {
        AppLanguage.entries.forEach { language ->
            val templates = builtInNoteTemplates(language)

            assertEquals(NoteTemplateId.entries.toSet(), templates.map { it.id }.toSet())
            assertEquals(NoteTemplateId.entries.size, templates.size)
            templates.forEach { template ->
                assertTrue(template.title.isNotBlank())
                assertEquals(NoteTypes.TEXT, template.type)
                assertTrue(template.textContent?.isNotBlank() == true)
            }
        }
    }

    @Test
    fun checklistStyleTemplatesUsePlainTextCheckboxesRatherThanStructuredChecklistNotes() {
        AppLanguage.entries.forEach { language ->
            val byId = builtInNoteTemplates(language).associateBy(NoteTemplate::id)

            listOf(
                NoteTemplateId.DailyChecklist,
                NoteTemplateId.ShoppingList,
                NoteTemplateId.TripPreparation,
            ).forEach { id ->
                assertEquals(NoteTypes.TEXT, byId.getValue(id).type)
                assertTrue(byId.getValue(id).textContent.orEmpty().contains("- [ ]"))
            }
        }
    }
}
