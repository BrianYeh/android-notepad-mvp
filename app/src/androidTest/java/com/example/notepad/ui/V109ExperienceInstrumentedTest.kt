package com.example.notepad.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.notepad.data.AppLanguage
import com.example.notepad.data.DEFAULT_FOLDER_ID
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.TodayNoteSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class V109ExperienceInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstRunWelcomeShowsPrivacyPromiseAndExplicitActions() {
        var skipped = false
        val text = v109Text(AppLanguage.English)
        composeRule.setContent {
            MaterialTheme {
                FirstRunWelcome(
                    text = text,
                    onNewNote = {},
                    onChooseTemplate = {},
                    onSkip = { skipped = true },
                )
            }
        }

        composeRule.onNodeWithText("Capture it now. Find it when it matters").assertIsDisplayed()
        composeRule.onNodeWithTag("first_run_privacy_copy").assertIsDisplayed()
        composeRule.onNodeWithTag("first_run_new_note").assertIsDisplayed()
        composeRule.onNodeWithTag("first_run_choose_template").assertIsDisplayed()
        composeRule.onNodeWithTag("first_run_skip").performClick()

        composeRule.runOnIdle { assertTrue(skipped) }
    }

    @Test
    fun firstRunWelcomeActionsRemainReachableAtLargeFontAndShortHeight() {
        var choseTemplate = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaterialTheme {
                    FirstRunWelcome(
                        text = v109Text(AppLanguage.English),
                        onNewNote = {},
                        onChooseTemplate = { choseTemplate = true },
                        onSkip = {},
                        modifier = Modifier
                            .width(320.dp)
                            .height(220.dp),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("first_run_welcome_scroll").performScrollToIndex(4)
        composeRule.onNodeWithTag("first_run_choose_template")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(choseTemplate) }
    }

    @Test
    fun starterHubActionsRemainReachableAtLargeFontAndShortHeight() {
        var choseTemplate = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .height(220.dp),
                    ) {
                        StarterHub(
                            text = v109Text(AppLanguage.English),
                            enabled = true,
                            onNewNote = {},
                            onChooseTemplate = { choseTemplate = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("starter_hub_scroll").performScrollToIndex(3)
        composeRule.onNodeWithTag("starter_choose_template")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(choseTemplate) }
    }

    @Test
    fun todayChecklistQuickActionUsesProvidedExistingCreationCallback() {
        var checklistRequests = 0
        composeRule.setContent {
            MaterialTheme {
                TodayHub(
                    sections = TodayNoteSections(emptyList(), emptyList(), emptyList(), emptyList()),
                    text = v109Text(AppLanguage.English),
                    isPrivacyLocked = false,
                    onNewNote = {},
                    onNewChecklist = { checklistRequests += 1 },
                    onOpenReminders = {},
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag("today_new_checklist").performClick()

        composeRule.runOnIdle { assertEquals(1, checklistRequests) }
    }

    @Test
    fun privacyLockedTodayHubDoesNotComposeNoteContentOrActions() {
        val secretTitle = "Private title that must not appear"
        val note = NoteEntity(
            id = 99,
            folderId = DEFAULT_FOLDER_ID,
            type = NoteTypes.TEXT,
            title = secretTitle,
            textContent = "Private body",
            drawingData = null,
            createdAt = 1,
            updatedAt = 1,
        )
        composeRule.setContent {
            MaterialTheme {
                TodayHub(
                    sections = TodayNoteSections(emptyList(), emptyList(), emptyList(), listOf(note)),
                    text = v109Text(AppLanguage.English),
                    isPrivacyLocked = true,
                    onNewNote = {},
                    onNewChecklist = {},
                    onOpenReminders = {},
                    onOpenNote = {},
                )
            }
        }

        composeRule.onNodeWithTag("today_privacy_locked").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText(secretTitle).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("today_new_note").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun narrowTraditionalChineseBottomNavigationAlwaysShowsFourAccessibleItems() {
        val clicked = mutableListOf<MainTab>()
        val selected = mutableStateOf(MainTab.Today)
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    MainNavigationBar(
                        selectedTab = selected.value,
                        text = TraditionalChineseText,
                        v109Text = v109Text(AppLanguage.TraditionalChinese),
                        onOpenNotes = {
                            clicked += MainTab.Notes
                            selected.value = MainTab.Notes
                        },
                        onOpenToday = {
                            clicked += MainTab.Today
                            selected.value = MainTab.Today
                        },
                        onOpenSearch = {
                            clicked += MainTab.Search
                            selected.value = MainTab.Search
                        },
                        onOpenPremium = {
                            clicked += MainTab.Premium
                            selected.value = MainTab.Premium
                        },
                    )
                }
            }
        }

        listOf("notes_tab", "today_tab", "search_tab", "premium_tab").forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
        composeRule.onNodeWithText(TraditionalChineseText.notesTab).assertIsDisplayed()
        composeRule.onNodeWithText(v109Text(AppLanguage.TraditionalChinese).today).assertIsDisplayed()
        composeRule.onNodeWithText(TraditionalChineseText.search).assertIsDisplayed()
        composeRule.onNodeWithText(TraditionalChineseText.premium).assertIsDisplayed()
        composeRule.onNodeWithTag("premium_tab").assertTextEquals(TraditionalChineseText.premium)
        composeRule.onNodeWithTag("today_tab").assertIsSelected()

        composeRule.onNodeWithTag("notes_tab").performClick()
        composeRule.onNodeWithTag("notes_tab").assertIsSelected()
        composeRule.onNodeWithTag("search_tab").performClick()
        composeRule.onNodeWithTag("search_tab").assertIsSelected()
        composeRule.onNodeWithTag("premium_tab").performClick()
        composeRule.onNodeWithTag("premium_tab").assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(listOf(MainTab.Notes, MainTab.Search, MainTab.Premium), clicked)
        }
    }
}
