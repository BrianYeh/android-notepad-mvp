package com.example.notepad.ui

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import com.example.notepad.billing.PremiumBillingState
import com.example.notepad.billing.PremiumSubscriptionSnapshot
import com.example.notepad.billing.PremiumSubscriptionStatus
import com.example.notepad.data.DrawingPoint
import com.example.notepad.data.DrawingStroke
import com.example.notepad.data.DrawingTools
import com.example.notepad.data.NoteEntity
import com.example.notepad.data.NoteTypes
import com.example.notepad.data.SyncMetadata
import com.example.notepad.data.SyncError
import com.example.notepad.data.SyncErrorCode
import com.example.notepad.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI

class NoteUiPureFunctionTest {
    @Test
    fun reminderCalendarOpenedFromTodayKeepsGlobalCrossFolderScope() {
        val selectedFolderNote = note(id = 1, folderId = 10)
        val otherFolderNote = note(id = 2, folderId = 20)
        val deletedOtherFolderNote = note(id = 3, folderId = 20, isDeleted = true)
        val allNotes = listOf(selectedFolderNote, otherFolderNote, deletedOtherFolderNote)

        assertEquals(
            listOf(selectedFolderNote, otherFolderNote),
            reminderCalendarNotes(
                allNotes = allNotes,
                selectedFolderNotes = listOf(selectedFolderNote),
                openedFromToday = true,
            ),
        )
        assertEquals(
            listOf(selectedFolderNote),
            reminderCalendarNotes(
                allNotes = allNotes,
                selectedFolderNotes = listOf(selectedFolderNote),
                openedFromToday = false,
            ),
        )
    }

    @Test
    fun complianceUrlsAreExactPublicHttpsPages() {
        assertEquals(
            "https://brianyeh.github.io/android-notepad-mvp/privacy-policy/",
            PRIVACY_POLICY_URL,
        )
        assertEquals(
            "https://brianyeh.github.io/android-notepad-mvp/terms-of-service/",
            TERMS_OF_SERVICE_URL,
        )
        assertEquals(
            "https://brianyeh.github.io/android-notepad-mvp/account-deletion/",
            ACCOUNT_DELETION_URL,
        )
        listOf(PRIVACY_POLICY_URL, TERMS_OF_SERVICE_URL, ACCOUNT_DELETION_URL).forEach { url ->
            assertEquals("https", URI(url).scheme)
        }
    }

    @Test
    fun googleAccountSyncUiShowsOnFreshInstall() {
        assertEquals(
            true,
            shouldShowGoogleAccountSyncUi(
                SyncMetadata(
                    deviceId = "device",
                    deviceName = "Pixel",
                    accountEmail = null,
                    status = SyncStatus.SignedOut,
                ),
            ),
        )
        assertEquals(
            true,
            shouldShowGoogleAccountSyncUi(
                SyncMetadata(
                    deviceId = "device",
                    deviceName = "Pixel",
                    accountEmail = "person@example.com",
                    status = SyncStatus.Idle,
                ),
            ),
        )
    }

    @Test
    fun networkSyncErrorUsesFriendlyLocalizedCopyWithoutLeakingTechnicalDetails() {
        val rawError = SyncError(
            code = SyncErrorCode.NetworkUnavailable,
            message = "Unable to resolve host \"www.googleapis.com\": No address associated with hostname",
        )

        val english = EnglishText.googleSyncErrorMessage(rawError.code)
        val traditionalChinese = TraditionalChineseText.googleSyncErrorMessage(rawError.code)

        assertEquals(
            "Unable to connect to Google Drive. Check your internet connection, Private DNS, or VPN, then try again.",
            english,
        )
        assertEquals(
            "無法連上 Google Drive。請檢查網路、私人 DNS 或 VPN 後再試一次。",
            traditionalChinese,
        )
        assertEquals(false, english.contains("googleapis.com"))
        assertEquals(false, traditionalChinese.contains("Unable to resolve host"))
    }

    @Test
    fun failedGoogleSyncKeepsManualRetryEnabled() {
        assertEquals(
            true,
            isGoogleSyncActionEnabled(
                status = SyncStatus.Failed,
                isGoogleSyncInProgress = false,
                isBackupInProgress = false,
                isRestoreInProgress = false,
            ),
        )
        assertEquals(
            false,
            isGoogleSyncActionEnabled(
                status = SyncStatus.Syncing,
                isGoogleSyncInProgress = false,
                isBackupInProgress = false,
                isRestoreInProgress = false,
            ),
        )
    }

    @Test
    fun premiumUiModeUsesUnavailablePreviewWhenBillingHasNoPrices() {
        assertEquals(
            PremiumUiMode.PreviewUnavailable,
            premiumUiMode(PremiumBillingState(loading = false)),
        )
        assertEquals(
            PremiumUiMode.PreviewUnavailable,
            premiumUiMode(
                PremiumBillingState(
                    billingAvailable = true,
                    loading = false,
                    lastError = "Products are unavailable",
                ),
            ),
        )
    }

    @Test
    fun premiumUiModeKeepsConnectedBillingWithoutPricesInCheckingState() {
        assertEquals(
            PremiumUiMode.CheckingAvailability,
            premiumUiMode(PremiumBillingState(billingAvailable = true, loading = false)),
        )
    }

    @Test
    fun premiumUiModeKeepsLoadingSeparateFromUnavailablePreview() {
        assertEquals(
            PremiumUiMode.CheckingAvailability,
            premiumUiMode(PremiumBillingState()),
        )
    }

    @Test
    fun premiumUiModePreservesInFlightPurchaseStatusWithoutPrices() {
        assertEquals(
            PremiumUiMode.AccountStatus,
            premiumUiMode(
                PremiumBillingState(
                    subscription = PremiumSubscriptionSnapshot(
                        status = PremiumSubscriptionStatus.PendingPurchase,
                    ),
                    loading = false,
                ),
            ),
        )
        assertEquals(
            PremiumUiMode.AccountStatus,
            premiumUiMode(
                PremiumBillingState(
                    subscription = PremiumSubscriptionSnapshot(
                        status = PremiumSubscriptionStatus.VerificationPending,
                    ),
                    loading = false,
                ),
            ),
        )
    }

    @Test
    fun premiumUiModeUsesCommerceOnlyWhenARealPriceIsAvailable() {
        assertEquals(
            PremiumUiMode.CommerceReady,
            premiumUiMode(
                PremiumBillingState(
                    billingAvailable = true,
                    loading = false,
                    monthlyPrice = "$3.99",
                ),
            ),
        )
        assertEquals(
            PremiumUiMode.CommerceReady,
            premiumUiMode(
                PremiumBillingState(
                    billingAvailable = true,
                    loading = false,
                    annualPrice = "$39.99",
                ),
            ),
        )
        assertEquals(
            PremiumUiMode.CheckingAvailability,
            premiumUiMode(
                PremiumBillingState(
                    billingAvailable = true,
                    loading = false,
                    monthlyPrice = " ",
                ),
            ),
        )
        assertEquals(
            PremiumUiMode.PreviewUnavailable,
            premiumUiMode(
                PremiumBillingState(
                    billingAvailable = true,
                    loading = false,
                    monthlyPrice = " ",
                    lastError = "No configured offer",
                ),
            ),
        )
    }

    @Test
    fun premiumPriceDisplayStaysVisibleWhenPurchaseCannotLaunch() {
        val state = PremiumBillingState(
            billingAvailable = true,
            loading = false,
            monthlyPrice = "$3.99",
            annualPrice = "$39.99",
        )

        assertEquals(PremiumUiMode.CommerceReady, premiumUiMode(state))
        assertEquals(
            true,
            shouldShowPremiumSubscribeButton(
                billingState = state,
                displayMode = PremiumUiMode.CommerceReady,
                selectedPriceAvailable = true,
            ),
        )
        assertEquals(false, shouldEnablePremiumSubscribeButton(state, backendPurchaseFlowEnabled = true))
    }

    @Test
    fun subscribeDisablesDuringContextOrVerificationAndReenablesAfterSafeFailure() {
        val ready = PremiumBillingState(
            billingAvailable = true,
            backendPurchaseReady = true,
            monthlyPrice = "$3.99",
        )

        assertEquals(true, shouldEnablePremiumSubscribeButton(ready, backendPurchaseFlowEnabled = true))
        assertEquals(
            false,
            shouldEnablePremiumSubscribeButton(
                ready.copy(purchaseLaunching = true),
                backendPurchaseFlowEnabled = true,
            ),
        )
        assertEquals(
            false,
            shouldEnablePremiumSubscribeButton(
                ready.copy(purchaseVerificationInFlight = true),
                backendPurchaseFlowEnabled = true,
            ),
        )
        assertEquals(
            true,
            shouldEnablePremiumSubscribeButton(
                ready.copy(lastError = "Purchase verification is temporarily unavailable."),
                backendPurchaseFlowEnabled = true,
            ),
        )
    }

    @Test
    fun premiumUiModeKeepsActiveEntitlementAheadOfUnavailablePrices() {
        assertEquals(
            PremiumUiMode.ActiveEntitlement,
            premiumUiMode(PremiumBillingState(debugPremiumOverride = true)),
        )
    }

    @Test
    fun highlightRangesAreCaseInsensitive() {
        assertEquals(listOf(0..4, 11..15), "Alpha note alpha".highlightRanges("alpha"))
        assertEquals(listOf(0..1, 4..5), "中文內容中文".highlightRanges("中文"))
    }

    @Test
    fun searchHighlightUsesAccentWhitespaceAndTokenFallbackRanges() {
        val value = "Meet José   after class"
        val accent = highlightedText(value, "jose", Color.Yellow)
        val collapsedPhrase = highlightedText(value, "jose after class", Color.Yellow)
        val reorderedTokens = highlightedText(value, "class meet", Color.Yellow)

        assertEquals(listOf(5..8), accent.spanStyles.map { it.start until it.end })
        assertEquals(listOf(5..22), collapsedPhrase.spanStyles.map { it.start until it.end })
        assertEquals(
            listOf(0..3, 18..22),
            reorderedTokens.spanStyles.map { it.start until it.end },
        )
    }

    @Test
    fun findInNoteMatchesAreCaseInsensitiveAndSupportChinese() {
        assertEquals(listOf(0..4, 11..15), findInNoteMatches("Alpha note alpha", "ALPHA"))
        assertEquals(listOf(0..1, 4..5), findInNoteMatches("中文內容中文", "中文"))
    }

    @Test
    fun findInNoteNavigationWrapsAround() {
        assertEquals(0, nextFindMatchIndex(4, 5))
        assertEquals(4, previousFindMatchIndex(0, 5))
        assertEquals(2, nextFindMatchIndex(1, 5))
        assertEquals(1, previousFindMatchIndex(2, 5))
    }

    @Test
    fun findMatchScrollTargetKeepsActiveMatchVisible() {
        assertEquals(
            676,
            findMatchScrollTarget(
                currentScroll = 0,
                viewportHeight = 400,
                matchTop = 950f,
                matchBottom = 980f,
                maxScroll = 2_000,
                viewportPaddingPx = 96f,
            ),
        )
        assertEquals(
            104,
            findMatchScrollTarget(
                currentScroll = 800,
                viewportHeight = 400,
                matchTop = 200f,
                matchBottom = 230f,
                maxScroll = 2_000,
                viewportPaddingPx = 96f,
            ),
        )
        assertEquals(
            null,
            findMatchScrollTarget(
                currentScroll = 500,
                viewportHeight = 400,
                matchTop = 650f,
                matchBottom = 680f,
                maxScroll = 2_000,
                viewportPaddingPx = 96f,
            ),
        )
    }

    @Test
    fun findMatchScrollTargetUsesSmallerPaddingForShortViewports() {
        assertEquals(
            270,
            findMatchScrollTarget(
                currentScroll = 0,
                viewportHeight = 180,
                matchTop = 360f,
                matchBottom = 390f,
                maxScroll = 1_000,
                viewportPaddingPx = 96f,
            ),
        )
    }

    @Test
    fun cursorScrollTargetKeepsTypingCaretVisibleNearViewportBottom() {
        assertEquals(
            656,
            cursorScrollTarget(
                currentScroll = 0,
                viewportHeight = 400,
                cursorTop = 980f,
                cursorBottom = 1_000f,
                maxScroll = 2_000,
                viewportBottomPaddingPx = 56f,
            ),
        )
        assertEquals(
            76,
            cursorScrollTarget(
                currentScroll = 500,
                viewportHeight = 400,
                cursorTop = 100f,
                cursorBottom = 120f,
                maxScroll = 2_000,
                viewportTopPaddingPx = 24f,
            ),
        )
        assertEquals(
            null,
            cursorScrollTarget(
                currentScroll = 500,
                viewportHeight = 400,
                cursorTop = 620f,
                cursorBottom = 650f,
                maxScroll = 2_000,
                viewportBottomPaddingPx = 56f,
            ),
        )
    }

    @Test
    fun drawingViewportScaleKeepsTallSavedStrokesVisibleWithoutResizingCanvas() {
        val tallStroke = DrawingStroke(
            points = listOf(DrawingPoint(40f, 20f), DrawingPoint(180f, 960f)),
            widthPx = 20f,
        )

        assertEquals(
            1_018f,
            drawingRequiredCanvasHeightPx(
                strokes = listOf(tallStroke),
                minimumHeightPx = 420f,
            ),
            0.001f,
        )
        assertEquals(
            0.619f,
            drawingViewportScale(
                strokes = listOf(tallStroke),
                measuredCanvasSize = IntSize(width = 360, height = 600),
            ),
            0.001f,
        )
    }

    @Test
    fun drawingExportCanvasSizePreservesTallSavedStrokeBottom() {
        val tallStroke = DrawingStroke(
            points = listOf(DrawingPoint(40f, 20f), DrawingPoint(180f, 960f)),
            widthPx = 20f,
        )

        assertEquals(
            IntSize(width = 360, height = 1_018),
            drawingExportCanvasSizePx(
                strokes = listOf(tallStroke),
                measuredCanvasSize = IntSize(width = 360, height = 600),
                fallbackWidthPx = 1080,
                fallbackHeightPx = 1440,
            ),
        )
    }

    @Test
    fun drawingBoundsIgnoreInvisibleEraserStrokes() {
        val penStroke = DrawingStroke(
            points = listOf(DrawingPoint(40f, 20f), DrawingPoint(180f, 300f)),
            widthPx = 20f,
        )
        val eraserStroke = DrawingStroke(
            points = listOf(DrawingPoint(40f, 20f), DrawingPoint(180f, 960f)),
            widthPx = 120f,
            tool = DrawingTools.ERASER,
        )

        assertEquals(
            358f,
            drawingRequiredCanvasHeightPx(
                strokes = listOf(penStroke, eraserStroke),
                minimumHeightPx = 320f,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            drawingViewportScale(
                strokes = listOf(penStroke, eraserStroke),
                measuredCanvasSize = IntSize(width = 360, height = 600),
            ),
            0.001f,
        )
    }

    @Test
    fun drawingExportCanvasSizeCapsHugeRestoredCoordinates() {
        val hugeStroke = DrawingStroke(
            points = listOf(DrawingPoint(40f, 20f), DrawingPoint(500_000f, 900_000f)),
            widthPx = 20f,
        )

        assertEquals(
            IntSize(width = 4_096, height = 4_096),
            drawingExportCanvasSizePx(
                strokes = listOf(hugeStroke),
                measuredCanvasSize = IntSize(width = 360, height = 600),
                fallbackWidthPx = 1080,
                fallbackHeightPx = 1440,
                maxDimensionPx = 4_096,
            ),
        )
    }

    @Test
    fun drawingExportCanvasSizeRoundsFractionalBoundsUp() {
        val fractionalStroke = DrawingStroke(
            points = listOf(DrawingPoint(100.1f, 50.1f)),
            widthPx = 5f,
        )

        assertEquals(
            IntSize(width = 151, height = 101),
            drawingExportCanvasSizePx(
                strokes = listOf(fractionalStroke),
                measuredCanvasSize = IntSize(width = 100, height = 100),
                fallbackWidthPx = 100,
                fallbackHeightPx = 100,
            ),
        )
    }

    @Test
    fun findInNoteNoMatchesAndEmptyQueryAreHandled() {
        assertEquals(emptyList<IntRange>(), findInNoteMatches("Alpha note", "missing"))
        assertEquals(emptyList<IntRange>(), findInNoteMatches("Alpha note", ""))
        assertEquals(-1, nextFindMatchIndex(0, 0))
        assertEquals(-1, previousFindMatchIndex(0, 0))
        assertEquals("No matches", formatFindMatchStatus(0, 0, "No matches"))
    }

    @Test
    fun noteContentUrlsPreserveBalancedParenthesesAndTrimSentencePunctuation() {
        val content = "Read https://en.wikipedia.org/wiki/Foo_(bar), then (https://example.com/path)."

        val urlRanges = content.webUrlRanges()

        assertEquals(2, urlRanges.size)
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", content.substring(urlRanges[0].range))
        assertEquals("https://example.com/path", content.substring(urlRanges[1].range))
    }

    private fun note(
        id: Long,
        folderId: Long,
        isDeleted: Boolean = false,
    ): NoteEntity {
        return NoteEntity(
            id = id,
            folderId = folderId,
            type = NoteTypes.TEXT,
            title = "Note $id",
            textContent = "",
            drawingData = null,
            createdAt = id,
            updatedAt = id,
            isDeleted = isDeleted,
        )
    }
}
