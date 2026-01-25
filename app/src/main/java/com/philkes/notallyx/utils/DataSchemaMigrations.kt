package com.philkes.notallyx.utils

import android.app.Application
import android.content.ContextWrapper
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.dao.BaseNoteDao.Companion.MAX_BODY_CHAR_LENGTH
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.createNoteUrl
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DataSchemaMigrations"

fun Application.checkForMigrations() {
    val preferences = NotallyXPreferences.getInstance(this)
    val dataSchemaId = preferences.dataSchemaId.value
    var newDataSchemaId = dataSchemaId

    MainScope().launch {
        withContext(Dispatchers.IO) {
            if (dataSchemaId < 1) {
                moveAttachments(preferences)
                newDataSchemaId = 1
            }
            if (newDataSchemaId < 2) {
                splitOversizedNotes(preferences)
                newDataSchemaId = 2
            }
            preferences.setDataSchemaId(newDataSchemaId)
        }
    }
}

private fun Application.moveAttachments(preferences: NotallyXPreferences) {
    val toPrivate = !preferences.dataInPublicFolder.value
    log(
        TAG,
        "Running migration 1: Moving attachments to ${if(toPrivate) "private" else "public"} folder",
    )
    migrateAllAttachments(toPrivate)
}

/**
 * Migration 2 Split existing notes whose body exceeds the newly introduced MAX_BODY_SIZE_MB limit.
 * If a note is too long, create additional notes with the remaining text and append a link at the
 * end of each truncated note that points to the next note. The link text is included in the body
 * and must also fit within the size limit.
 */
private suspend fun Application.splitOversizedNotes(preferences: NotallyXPreferences) {
    log(TAG, "Running migration 2: Splitting notes exceeding the body size limit")

    // Obtain a direct DB instance matching current storage location
    val db = NotallyDatabase.getDatabase(this as ContextWrapper, false).value
    val dao = db.getBaseNoteDao()

    // Process only text notes; lists don't use body for content length
    val allNotes = dao.getAllNotes()
    val linkText = "\n\nOpen next part"
    val linkTextLength = linkText.length

    var affected = 0
    allNotes.forEach { original ->
        if (original.type != Type.NOTE) return@forEach
        val bodyBytes = original.body.length
        if (bodyBytes <= MAX_BODY_CHAR_LENGTH) return@forEach

        affected += 1
        // Iteratively split this note into multiple parts while preserving spans
        var current = original
        var counter = 0
        val originalBody = original.body
        val originalSpans = original.spans
        var startIndex = 0

        val allowedWithLink = MAX_BODY_CHAR_LENGTH - linkTextLength

        while (true) {
            val remainingLen = originalBody.length - startIndex
            if (remainingLen <= MAX_BODY_CHAR_LENGTH) {
                // Final part: no link text, just update current with the rest and correct spans
                val finalBody = originalBody.substring(startIndex)
                val finalSpans =
                    sliceSpans(originalSpans, startIndex, originalBody.length, shift = -startIndex)
                val updatedFinal =
                    current.copy(body = finalBody, spans = finalSpans, items = emptyList())
                dao.insert(updatedFinal) // replace existing current
                break
            }

            // Non-final part: reserve space for link text
            val chunkLen = allowedWithLink.coerceAtLeast(0)
            val chunkBody = originalBody.substring(startIndex, startIndex + chunkLen)
            val chunkSpans =
                sliceSpans(originalSpans, startIndex, startIndex + chunkLen, shift = -startIndex)

            // Create the next note from the remaining text (beyond this chunk)
            val nextStart = startIndex + chunkLen
            val nextBodyFull = originalBody.substring(nextStart)
            val nextSpansFull =
                sliceSpans(originalSpans, nextStart, originalBody.length, shift = -nextStart)

            // Insert the next note first to obtain its id for the link
            val nextNoteToInsert =
                current.copy(
                    id = 0, // auto-generate new ID
                    title = "${original.title} (${++counter})",
                    body = nextBodyFull,
                    spans = nextSpansFull,
                    items = emptyList(),
                )
            val nextId = dao.insert(nextNoteToInsert)

            // Append link to current chunk
            val linkStart = chunkBody.length + ("\n\n").length
            val linkEnd = linkStart + ("Open next part").length
            val linkSpan =
                SpanRepresentation(
                    start = linkStart,
                    end = linkEnd,
                    link = true,
                    linkData = nextId.createNoteUrl(current.type),
                )

            val updatedCurrent =
                current.copy(
                    body = chunkBody + linkText,
                    spans = chunkSpans + linkSpan,
                    items = emptyList(),
                )
            dao.insert(updatedCurrent)

            // Continue with the newly created note as current
            current = nextNoteToInsert.copy(id = nextId)
            startIndex = nextStart
        }
    }

    log(TAG, "Migration 2 finished. Processed $affected oversized notes.")
}

/**
 * Returns spans that intersect with [rangeStart, rangeEnd) from [spans], with their positions
 * shifted by [shift]. Typically, use shift = -rangeStart so that returned spans are relative to the
 * start of the sliced body substring.
 */
private fun sliceSpans(
    spans: List<SpanRepresentation>,
    rangeStart: Int,
    rangeEnd: Int,
    shift: Int,
): List<SpanRepresentation> {
    if (spans.isEmpty()) return emptyList()
    val result = ArrayList<SpanRepresentation>()
    for (s in spans) {
        // no intersection
        if (s.end <= rangeStart || s.start >= rangeEnd) continue
        val newStart = maxOf(s.start, rangeStart) + shift
        val newEnd = minOf(s.end, rangeEnd) + shift
        if (newEnd <= newStart) continue
        result.add(
            SpanRepresentation(
                start = newStart,
                end = newEnd,
                bold = s.bold,
                link = s.link,
                linkData = s.linkData,
                italic = s.italic,
                monospace = s.monospace,
                strikethrough = s.strikethrough,
            )
        )
    }
    return result
}

private fun takeBytesUtf8(text: String, maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return text
    val truncated = bytes.sliceArray(0 until maxBytes)
    return String(truncated, Charsets.UTF_8)
}
