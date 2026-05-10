package com.example.notepad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextInputTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun textNoteTitleAndContentAcceptInput() {
        composeRule.onNodeWithTag("add_note_button").performClick()
        composeRule.onNodeWithText("New Text Note").performClick()

        composeRule.onNodeWithTag("text_note_title")
            .assertIsDisplayed()
            .performTextInput("中文標題")

        composeRule.onNodeWithTag("text_note_content")
            .assertIsDisplayed()
            .performTextInput("這是中文內容")

        composeRule.onNodeWithText("中文標題").assertIsDisplayed()
        composeRule.onNodeWithText("這是中文內容").assertIsDisplayed()
    }
}
