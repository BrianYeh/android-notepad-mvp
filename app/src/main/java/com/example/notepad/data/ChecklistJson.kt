package com.example.notepad.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val checked: Boolean = false,
)

object ChecklistJson {
    fun emptyItems(): List<ChecklistItem> = listOf(ChecklistItem(text = ""))

    fun encode(items: List<ChecklistItem>): String {
        return JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("id", item.id)
                        .put("text", item.text)
                        .put("checked", item.checked),
                )
            }
        }.toString()
    }

    fun decode(json: String?): List<ChecklistItem> {
        if (json.isNullOrBlank()) return emptyItems()
        return try {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        ChecklistItem(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            text = item.optString("text"),
                            checked = item.optBoolean("checked", false),
                        ),
                    )
                }
            }.ifEmpty { emptyItems() }
        } catch (_: Exception) {
            json.lines()
                .filter { it.isNotBlank() }
                .map { line -> ChecklistItem(text = line.trim()) }
                .ifEmpty { emptyItems() }
        }
    }

    fun preview(json: String?): String {
        val items = decode(json).filter { it.text.isNotBlank() }
        if (items.isEmpty()) return ""
        val done = items.count { it.checked }
        val previewItems = items.take(3).joinToString("  ") { item ->
            "${if (item.checked) "[x]" else "[ ]"} ${item.text}"
        }
        return "${done}/${items.size} ${previewItems}"
    }

    fun plainText(json: String?): String {
        return decode(json)
            .filter { it.text.isNotBlank() }
            .joinToString("\n") { item ->
                "${if (item.checked) "[x]" else "[ ]"} ${item.text}"
            }
    }
}
