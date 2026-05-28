package com.example.notepad.data

import org.json.JSONArray
import org.json.JSONObject

enum class TextFormatType(val code: String) {
    Bold("BOLD"),
    Italic("ITALIC"),
    Underline("UNDERLINE"),
    Highlight("HIGHLIGHT"),
    Link("LINK"),
    Heading1("HEADING_1"),
    Heading2("HEADING_2"),
}

data class TextFormatRange(
    val start: Int,
    val end: Int,
    val type: TextFormatType,
    val url: String? = null,
) {
    fun overlaps(start: Int, end: Int): Boolean = this.start < end && start < this.end
}

object TextFormattingJson {
    fun encode(ranges: List<TextFormatRange>): String? {
        val sanitized = sanitize(ranges, Int.MAX_VALUE)
        if (sanitized.isEmpty()) return null
        return JSONArray().apply {
            sanitized.forEach { range ->
                put(
                    JSONObject()
                        .put("start", range.start)
                        .put("end", range.end)
                        .put("type", range.type.code)
                        .apply {
                            if (!range.url.isNullOrBlank()) put("url", range.url)
                        },
                )
            }
        }.toString()
    }

    fun decode(json: String?, textLength: Int = Int.MAX_VALUE): List<TextFormatRange> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val type = item.optString("type").toTextFormatTypeOrNull() ?: continue
                    val start = item.optInt("start", -1)
                    val end = item.optInt("end", -1)
                    val url = item.optionalString("url")
                    add(TextFormatRange(start = start, end = end, type = type, url = url))
                }
            }.let { sanitize(it, textLength) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun sanitize(ranges: List<TextFormatRange>, textLength: Int): List<TextFormatRange> {
        val maxLength = textLength.coerceAtLeast(0)
        return ranges.mapNotNull { range ->
            val start = range.start.coerceIn(0, maxLength)
            val end = range.end.coerceIn(start, maxLength)
            if (start >= end) {
                null
            } else {
                range.copy(
                    start = start,
                    end = end,
                    url = range.url?.trim()?.takeIf { it.isNotBlank() },
                )
            }
        }
            .distinct()
            .sortedWith(compareBy<TextFormatRange> { it.start }.thenBy { it.end }.thenBy { it.type.code })
    }

    fun toggle(
        ranges: List<TextFormatRange>,
        range: IntRange,
        type: TextFormatType,
        textLength: Int,
        url: String? = null,
    ): List<TextFormatRange> {
        val start = range.first.coerceIn(0, textLength)
        val end = (range.last + 1).coerceIn(start, textLength)
        if (start >= end) return sanitize(ranges, textLength)

        val sanitized = sanitize(ranges, textLength)
        val selectedSegmentAlreadyFormatted = sanitized.any {
            it.type == type &&
                it.start <= start &&
                it.end >= end &&
                (type != TextFormatType.Link || it.url == url)
        }
        val kept = sanitized.flatMap { range ->
            if (range.type == type && range.overlaps(start, end)) {
                range.minusSegment(start, end)
            } else {
                listOf(range)
            }
        }
        return if (selectedSegmentAlreadyFormatted) {
            sanitize(kept, textLength)
        } else {
            sanitize(kept + TextFormatRange(start = start, end = end, type = type, url = url), textLength)
        }
    }

    fun clear(ranges: List<TextFormatRange>, range: IntRange, textLength: Int): List<TextFormatRange> {
        val start = range.first.coerceIn(0, textLength)
        val end = (range.last + 1).coerceIn(start, textLength)
        if (start >= end) return sanitize(ranges, textLength)
        return sanitize(
            sanitize(ranges, textLength).flatMap { it.minusSegment(start, end) },
            textLength,
        )
    }
}

private fun TextFormatRange.minusSegment(start: Int, end: Int): List<TextFormatRange> {
    if (!overlaps(start, end)) return listOf(this)
    return buildList {
        if (this@minusSegment.start < start) {
            add(this@minusSegment.copy(end = start))
        }
        if (end < this@minusSegment.end) {
            add(this@minusSegment.copy(start = end))
        }
    }
}

fun adjustTextFormattingAfterEdit(
    ranges: List<TextFormatRange>,
    oldText: String,
    newText: String,
): List<TextFormatRange> {
    if (oldText == newText) return TextFormattingJson.sanitize(ranges, newText.length)

    val commonPrefix = oldText.commonPrefixLength(newText)
    val commonSuffix = oldText.commonSuffixLength(newText, commonPrefix)
    val oldEditEnd = oldText.length - commonSuffix
    val newEditEnd = newText.length - commonSuffix
    val delta = newText.length - oldText.length

    return TextFormattingJson.sanitize(
        ranges.mapNotNull { range ->
            when {
                range.end <= commonPrefix -> range
                range.start >= oldEditEnd -> range.copy(start = range.start + delta, end = range.end + delta)
                range.start < commonPrefix && range.end > oldEditEnd -> range.copy(end = range.end + delta)
                range.start < commonPrefix -> range.copy(end = commonPrefix)
                range.end > oldEditEnd -> range.copy(start = newEditEnd, end = range.end + delta)
                else -> null
            }
        },
        newText.length,
    )
}

fun selectedTextRange(selectionStart: Int, selectionEnd: Int, textLength: Int): IntRange? {
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, textLength)
    val endExclusive = maxOf(selectionStart, selectionEnd).coerceIn(start, textLength)
    if (start >= endExclusive) return null
    return start until endExclusive
}

fun currentLineRange(text: String, cursor: Int): IntRange? {
    if (text.isEmpty()) return null
    val safeCursor = cursor.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (safeCursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val nextBreak = text.indexOf('\n', safeCursor)
    val lineEnd = if (nextBreak < 0) text.length else nextBreak
    if (lineStart >= lineEnd) return null
    return lineStart until lineEnd
}

fun currentWordRange(text: String, cursor: Int): IntRange? {
    if (text.isEmpty()) return null
    val safeCursor = cursor.coerceIn(0, text.length)
    val leftStart = (safeCursor - 1).coerceAtLeast(0)
    var start = leftStart
    while (start > 0 && !text[start - 1].isWhitespace()) start -= 1
    var end = safeCursor
    while (end < text.length && !text[end].isWhitespace()) end += 1
    if (start >= end) return null
    return start until end
}

fun normalizedFormatUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

private fun String.toTextFormatTypeOrNull(): TextFormatType? {
    return TextFormatType.entries.firstOrNull { it.code == this }
}

private fun JSONObject.optionalString(name: String): String? {
    return if (has(name) && !isNull(name)) getString(name) else null
}

private fun String.commonPrefixLength(other: String): Int {
    val limit = minOf(length, other.length)
    var index = 0
    while (index < limit && this[index] == other[index]) index += 1
    return index
}

private fun String.commonSuffixLength(other: String, prefixLength: Int): Int {
    val limit = minOf(length, other.length) - prefixLength
    var count = 0
    while (count < limit && this[length - 1 - count] == other[other.length - 1 - count]) count += 1
    return count
}
