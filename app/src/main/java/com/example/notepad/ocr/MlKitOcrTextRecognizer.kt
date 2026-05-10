package com.example.notepad.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrTextRecognizer(
    private val context: Context,
) : OcrTextRecognizer {
    override suspend fun recognizeText(uri: Uri): String = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

        try {
            mergeRecognizedText(
                latinRecognizer.process(image).await().text,
                chineseRecognizer.process(image).await().text,
            )
        } finally {
            latinRecognizer.closeQuietly()
            chineseRecognizer.closeQuietly()
        }
    }
}

private fun mergeRecognizedText(
    latinText: String,
    chineseText: String,
): String {
    val latin = latinText.trim()
    val chinese = chineseText.trim()
    if (latin.isBlank()) return chinese
    if (chinese.isBlank()) return latin
    if (latin.contains(chinese)) return latin
    if (chinese.contains(latin)) return chinese

    return (latin.lineSequence() + chinese.lineSequence())
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(separator = "\n")
}

private suspend fun <T> Task<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
}

private fun TextRecognizer.closeQuietly() {
    runCatching { close() }
}
