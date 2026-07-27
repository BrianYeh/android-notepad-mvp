package com.example.notepad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notepad.billing.PremiumAcknowledgementStatus
import com.example.notepad.billing.PremiumBillingState
import com.example.notepad.billing.PremiumEntitlementSource
import com.example.notepad.billing.PremiumSubscriptionSnapshot
import com.example.notepad.billing.PremiumSubscriptionStatus
import com.example.notepad.ui.ACCOUNT_DELETION_URL
import com.example.notepad.ui.AccountDeletionLink
import com.example.notepad.ui.EnglishText
import com.example.notepad.ui.LocalNotepadTheme
import com.example.notepad.ui.PRIVACY_POLICY_URL
import com.example.notepad.ui.PremiumScreen
import com.example.notepad.ui.TERMS_OF_SERVICE_URL
import com.example.notepad.ui.v109Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComplianceLinksInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun premiumLinksRemainVisibleAndOpenExactUrlsInEveryMode() {
        val now = System.currentTimeMillis()
        val states = listOf(
            PremiumBillingState(
                subscription = PremiumSubscriptionSnapshot(
                    status = PremiumSubscriptionStatus.Active,
                    source = PremiumEntitlementSource.ReviewerGrant,
                    expiryTime = now + 60_000L,
                    acknowledgementStatus = PremiumAcknowledgementStatus.NotRequired,
                ),
                loading = false,
            ),
            PremiumBillingState(
                subscription = PremiumSubscriptionSnapshot(
                    status = PremiumSubscriptionStatus.VerificationPending,
                ),
                loading = false,
            ),
            PremiumBillingState(loading = true),
            PremiumBillingState(loading = false),
            PremiumBillingState(
                billingAvailable = true,
                backendPurchaseReady = true,
                monthlyPrice = "NT$33",
                annualPrice = "NT$330",
                loading = false,
            ),
        )
        var state by mutableStateOf(states.first())
        val opened = mutableListOf<String>()
        composeRule.setContent {
            LocalNotepadTheme {
                PremiumScreen(
                    text = EnglishText,
                    v109Text = v109Text(com.example.notepad.data.AppLanguage.English),
                    billingState = state,
                    onSubscribe = {},
                    onRefreshPurchaseStatus = {},
                    onBack = {},
                    onOpenNotes = {},
                    onOpenToday = {},
                    onOpenSearch = {},
                    onOpenComplianceUrl = opened::add,
                )
            }
        }

        states.forEach { nextState ->
            composeRule.runOnIdle { state = nextState }
            composeRule.onNodeWithTag("privacy_policy_link")
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
            composeRule.onNodeWithTag("terms_of_service_link")
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
        }

        composeRule.runOnIdle {
            assertEquals(states.size, opened.count { it == PRIVACY_POLICY_URL })
            assertEquals(states.size, opened.count { it == TERMS_OF_SERVICE_URL })
        }
    }

    @Test
    fun accountDeletionLinkOpensExactPublicPage() {
        var opened: String? = null
        composeRule.setContent {
            LocalNotepadTheme {
                AccountDeletionLink(
                    text = EnglishText,
                    onOpenComplianceUrl = { opened = it },
                )
            }
        }

        composeRule.onNodeWithTag("account_deletion_link")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(ACCOUNT_DELETION_URL, opened) }
    }
}
