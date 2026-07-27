package com.example.notepad.data

import java.time.Instant
import java.time.ZoneId

data class TodayNoteSections(
    val overdue: List<NoteEntity>,
    val dueToday: List<NoteEntity>,
    val pinned: List<NoteEntity>,
    val recent: List<NoteEntity>,
) {
    val isEmpty: Boolean
        get() = overdue.isEmpty() && dueToday.isEmpty() && pinned.isEmpty() && recent.isEmpty()
}

fun NoteEntity.effectiveAttentionAt(nowMillis: Long): Long? {
    val latestTransientAttention = listOfNotNull(
        reminderSnoozeUntil,
        activeReminderFiredAt,
    ).maxOrNull()
    val scheduledOccurrenceAlreadyDue = reminderAt?.takeIf { it <= nowMillis }

    return listOfNotNull(
        latestTransientAttention,
        scheduledOccurrenceAlreadyDue,
    ).maxOrNull() ?: reminderAt
}

fun buildTodayNoteSections(
    notes: List<NoteEntity>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    recentLimit: Int = 8,
): TodayNoteSections {
    val localDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val dayStart = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val nextDayStart = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val activeNotes = notes.filterNot(NoteEntity::isDeleted)
    val includedIds = mutableSetOf<Long>()
    val effectiveAttentionAt: (NoteEntity) -> Long? = { note ->
        note.effectiveAttentionAt(nowMillis)
    }

    fun unique(notesForSection: List<NoteEntity>): List<NoteEntity> {
        return notesForSection.filter { includedIds.add(it.id) }
    }

    val overdue = unique(
        activeNotes
            .filter { note -> effectiveAttentionAt(note)?.let { it < dayStart } == true }
            .sortedBy(effectiveAttentionAt),
    )
    val dueToday = unique(
        activeNotes
            .filter { note ->
                effectiveAttentionAt(note)?.let { attentionAt ->
                    attentionAt >= dayStart && attentionAt < nextDayStart
                } == true
            }
            .sortedBy(effectiveAttentionAt),
    )
    val pinned = unique(
        activeNotes
            .filter(NoteEntity::isPinned)
            .sortedByDescending { it.updatedAt },
    )
    val recent = unique(
        activeNotes
            .filterNot { note -> note.id in includedIds }
            .sortedByDescending { it.updatedAt }
            .take(recentLimit.coerceAtLeast(0)),
    )

    return TodayNoteSections(
        overdue = overdue,
        dueToday = dueToday,
        pinned = pinned,
        recent = recent,
    )
}
