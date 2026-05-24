package com.example.notepad

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notepad.data.AppLanguage
import com.example.notepad.ui.LocalNotepadTheme
import com.example.notepad.ui.NotepadApp
import com.example.notepad.ui.PrivacyLockScreen
import com.example.notepad.ui.uiTextFor
import com.example.notepad.viewmodel.NotepadViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val incomingTextShare = MutableStateFlow<IncomingTextShare?>(null)
    private var nextShareId = 0L
    private var lockOnStop: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        handleIncomingShareIntent(intent)

        setContent {
            val viewModel: NotepadViewModel = viewModel()
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
            val notes by viewModel.notes.collectAsStateWithLifecycle()
            val selectedFolderId by viewModel.selectedFolderId.collectAsStateWithLifecycle()
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val listMode by viewModel.listMode.collectAsStateWithLifecycle()
            val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
            val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
            val reminderFilter by viewModel.reminderFilter.collectAsStateWithLifecycle()
            val quickFilter by viewModel.quickFilter.collectAsStateWithLifecycle()
            val editorFontSize by viewModel.editorFontSize.collectAsStateWithLifecycle()
            val isRecognizingText by viewModel.isRecognizingText.collectAsStateWithLifecycle()
            val sharedText by incomingTextShare.collectAsStateWithLifecycle()
            val requireDeviceUnlock by viewModel.requireDeviceUnlock.collectAsStateWithLifecycle()
            val keyguardManager = remember {
                getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            }
            var deviceUnlockAvailable by remember { mutableStateOf(keyguardManager.isDeviceSecure) }
            var isAppUnlocked by remember { mutableStateOf(false) }
            var unlockRequestInFlight by remember { mutableStateOf(false) }
            val isLocked = requireDeviceUnlock && !isAppUnlocked
            val lifecycleOwner = LocalLifecycleOwner.current
            val focusManager = LocalFocusManager.current
            val keyboardController = LocalSoftwareKeyboardController.current
            val unlockLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                unlockRequestInFlight = false
                isAppUnlocked = result.resultCode == Activity.RESULT_OK
            }

            fun requestDeviceUnlock() {
                if (!requireDeviceUnlock || isAppUnlocked || unlockRequestInFlight || !deviceUnlockAvailable) {
                    return
                }
                val unlockIntent = keyguardManager.createConfirmDeviceCredentialIntent(
                    getString(R.string.app_name),
                    null,
                ) ?: return
                unlockRequestInFlight = true
                unlockLauncher.launch(unlockIntent)
            }

            LaunchedEffect(requireDeviceUnlock, deviceUnlockAvailable, isAppUnlocked) {
                if (!requireDeviceUnlock) {
                    isAppUnlocked = true
                } else if (!deviceUnlockAvailable) {
                    isAppUnlocked = false
                } else if (!isAppUnlocked) {
                    requestDeviceUnlock()
                }
            }

            DisposableEffect(lifecycleOwner, keyguardManager) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        deviceUnlockAvailable = keyguardManager.isDeviceSecure
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            DisposableEffect(requireDeviceUnlock) {
                if (requireDeviceUnlock) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                lockOnStop = {
                    if (requireDeviceUnlock) {
                        isAppUnlocked = false
                    }
                }
                onDispose {
                    lockOnStop = null
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(isLocked) {
                if (isLocked) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            }

            LocalNotepadTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = if (isLocked) {
                            Modifier
                                .fillMaxSize()
                                .focusProperties { canFocus = false }
                                .clearAndSetSemantics {}
                        } else {
                            Modifier.fillMaxSize()
                        },
                    ) {
                        NotepadApp(
                            folders = folders,
                            allNotes = allNotes,
                            notes = notes,
                            selectedFolderId = selectedFolderId,
                            searchQuery = searchQuery,
                            listMode = listMode,
                            sortOption = sortOption,
                            typeFilter = typeFilter,
                            reminderFilter = reminderFilter,
                            quickFilter = quickFilter,
                            editorFontSize = editorFontSize,
                            isRecognizingText = isRecognizingText,
                            incomingTextShare = if (isLocked) null else sharedText,
                            isPrivacyLocked = isLocked,
                            deviceUnlockAvailable = deviceUnlockAvailable,
                            onIncomingTextShareHandled = { handledId ->
                                if (incomingTextShare.value?.id == handledId) {
                                    incomingTextShare.value = null
                                    setIntent(Intent(Intent.ACTION_MAIN))
                                }
                            },
                            viewModel = viewModel,
                        )
                    }
                    if (isLocked) {
                        val text = uiTextFor(AppLanguage.fromLocale(Locale.getDefault()))
                        PrivacyLockScreen(
                            text = text,
                            canUseDeviceLock = deviceUnlockAvailable,
                            onUnlock = ::requestDeviceUnlock,
                            onDisableLock = {
                                viewModel.setRequireDeviceUnlock(false)
                                isAppUnlocked = true
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lockOnStop?.invoke()
    }

    @Suppress("DEPRECATION")
    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.rgb(255, 251, 254)
        window.navigationBarColor = Color.rgb(255, 247, 215)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        val sharedText = extractIncomingTextShare(intent) ?: return
        incomingTextShare.value = sharedText.copy(id = ++nextShareId)
    }

    private fun extractIncomingTextShare(intent: Intent?): IncomingTextShare? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("text/")) return null

        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.getStringExtra(Intent.EXTRA_TITLE)

        return IncomingTextShare(
            id = 0L,
            subject = subject?.trim()?.takeIf { it.isNotBlank() },
            text = text,
        )
    }
}

data class IncomingTextShare(
    val id: Long,
    val subject: String?,
    val text: String,
)
