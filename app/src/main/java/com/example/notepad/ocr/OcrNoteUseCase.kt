package com.example.notepad.ocr

import android.net.Uri
import com.example.notepad.data.NotepadRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val OCR_TITLE_MAX_LENGTH = 60

interface OcrTextRecognizer {
    suspend fun recognizeText(uri: Uri): String
}

sealed interface OcrNoteResult {
    data class Created(val noteId: Long) : OcrNoteResult
    data object NoText : OcrNoteResult
    data object Failed : OcrNoteResult
}

class OcrNoteUseCase(
    private val recognizer: OcrTextRecognizer,
    private val repository: NotepadRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun createTextNoteFromImage(
        uri: Uri,
        fallbackTitlePrefix: String,
    ): OcrNoteResult {
        return runCatching {
            val recognizedText = recognizer.recognizeText(uri).trim()
            if (recognizedText.isBlank()) {
                OcrNoteResult.NoText
            } else {
                OcrNoteResult.Created(
                    repository.createSharedTextNote(
                        title = buildOcrNoteTitle(
                            recognizedText = recognizedText,
                            fallbackTitlePrefix = fallbackTitlePrefix,
                            now = now(),
                        ),
                        content = recognizedText,
                    ),
                )
            }
        }.getOrElse {
            OcrNoteResult.Failed
        }
    }
}

fun buildOcrNoteTitle(
    recognizedText: String,
    fallbackTitlePrefix: String,
    now: Long,
): String {
    recognizedText
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.take(OCR_TITLE_MAX_LENGTH)
        ?.trim()
        ?.ifBlank { null }
        ?.let { return it }

    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(now))
    return "$fallbackTitlePrefix $timestamp"
}
