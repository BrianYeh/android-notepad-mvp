package com.example.notepad.data

import java.text.Normalizer
import java.util.Locale

data class NoteSearchMatch(
    val matches: Boolean,
    val titleRanges: List<IntRange>,
    val contentRanges: List<IntRange>,
)

private data class NormalizedSearchText(
    val value: String,
    val originalStartIndices: List<Int>,
    val originalEndIndices: List<Int>,
) {
    fun originalRange(normalizedStart: Int, normalizedLength: Int): IntRange {
        val originalStart = originalStartIndices[normalizedStart]
        val originalEnd = originalEndIndices[normalizedStart + normalizedLength - 1]
        return originalStart..originalEnd
    }
}

internal fun normalizeNoteSearchText(value: String): String {
    return normalizeSearchTextWithMapping(value).value
}

fun findSearchMatchRanges(value: String, query: String): List<IntRange> {
    val normalizedValue = normalizeSearchTextWithMapping(value)
    val normalizedQuery = normalizeNoteSearchText(query)
    if (normalizedValue.value.isBlank() || normalizedQuery.isBlank()) return emptyList()

    val phraseRanges = normalizedValue.findAll(normalizedQuery)
    if (phraseRanges.isNotEmpty()) return phraseRanges

    return normalizedQuery.split(' ')
        .filter(String::isNotBlank)
        .distinct()
        .flatMap(normalizedValue::findAll)
        .distinct()
        .sortedBy(IntRange::first)
}

fun NoteEntity.noteSearchMatch(query: String): NoteSearchMatch {
    val normalizedQuery = normalizeNoteSearchText(query)
    if (normalizedQuery.isBlank()) {
        return NoteSearchMatch(matches = true, titleRanges = emptyList(), contentRanges = emptyList())
    }

    val searchableContent = when (type) {
        NoteTypes.CHECKLIST -> ChecklistJson.plainText(textContent)
        NoteTypes.TEXT -> textContent.orEmpty()
        else -> ""
    }
    val normalizedTitle = normalizeSearchTextWithMapping(title)
    val normalizedContent = normalizeSearchTextWithMapping(searchableContent)
    val titlePhraseRanges = normalizedTitle.findAll(normalizedQuery)
    val contentPhraseRanges = normalizedContent.findAll(normalizedQuery)
    if (titlePhraseRanges.isNotEmpty() || contentPhraseRanges.isNotEmpty()) {
        return NoteSearchMatch(
            matches = true,
            titleRanges = titlePhraseRanges,
            contentRanges = contentPhraseRanges,
        )
    }

    val titleTokenRanges = mutableListOf<IntRange>()
    val contentTokenRanges = mutableListOf<IntRange>()
    normalizedQuery.split(' ')
        .filter(String::isNotBlank)
        .distinct()
        .forEach { token ->
            val titleMatches = normalizedTitle.findAll(token)
            val contentMatches = normalizedContent.findAll(token)
            if (titleMatches.isEmpty() && contentMatches.isEmpty()) {
                return NoteSearchMatch(false, emptyList(), emptyList())
            }
            titleTokenRanges += titleMatches
            contentTokenRanges += contentMatches
        }
    return NoteSearchMatch(
        matches = true,
        titleRanges = titleTokenRanges.distinct().sortedBy(IntRange::first),
        contentRanges = contentTokenRanges.distinct().sortedBy(IntRange::first),
    )
}

fun NoteEntity.matchesNoteSearch(query: String): Boolean = noteSearchMatch(query).matches

private fun NormalizedSearchText.findAll(needle: String): List<IntRange> {
    if (needle.isBlank() || value.isBlank()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var startIndex = 0
    while (startIndex <= value.length - needle.length) {
        val matchIndex = value.indexOf(needle, startIndex)
        if (matchIndex < 0) break
        ranges += originalRange(matchIndex, needle.length)
        startIndex = matchIndex + needle.length
    }
    return ranges
}

private fun normalizeSearchTextWithMapping(value: String): NormalizedSearchText {
    val normalized = StringBuilder()
    val originalStartIndices = mutableListOf<Int>()
    val originalEndIndices = mutableListOf<Int>()
    var originalIndex = 0
    var pendingWhitespaceRange: IntRange? = null

    while (originalIndex < value.length) {
        val codePoint = value.codePointAt(originalIndex)
        val originalRange = originalIndex until (originalIndex + Character.charCount(codePoint))
        val source = String(Character.toChars(codePoint))
        val folded = Normalizer.normalize(source, Normalizer.Form.NFD)
            .lowercase(Locale.ROOT)
        folded.forEach { character ->
            if (character.isCombiningMark()) return@forEach
            if (character.isWhitespace()) {
                if (normalized.isNotEmpty()) {
                    pendingWhitespaceRange = pendingWhitespaceRange ?: originalRange
                }
            } else {
                pendingWhitespaceRange?.let { whitespaceRange ->
                    normalized.append(' ')
                    originalStartIndices += whitespaceRange.first
                    originalEndIndices += whitespaceRange.last
                }
                pendingWhitespaceRange = null
                normalized.append(character)
                originalStartIndices += originalRange.first
                originalEndIndices += originalRange.last
            }
        }
        originalIndex += Character.charCount(codePoint)
    }

    return NormalizedSearchText(
        value = normalized.toString(),
        originalStartIndices = originalStartIndices,
        originalEndIndices = originalEndIndices,
    )
}

private fun Char.isCombiningMark(): Boolean {
    return when (Character.getType(this)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        -> true

        else -> false
    }
}
