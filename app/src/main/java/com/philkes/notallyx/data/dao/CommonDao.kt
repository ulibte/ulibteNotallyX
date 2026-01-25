package com.philkes.notallyx.data.dao

import android.content.ContextWrapper
import androidx.room.Dao
import androidx.room.Transaction
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.data.model.LabelsInBaseNote
import com.philkes.notallyx.data.model.createNoteUrl
import com.philkes.notallyx.data.model.getNoteIdFromUrl
import com.philkes.notallyx.data.model.getNoteTypeFromUrl
import com.philkes.notallyx.data.model.isNoteUrl

@Dao
abstract class CommonDao(private val database: NotallyDatabase) {

    @Transaction
    open suspend fun deleteLabel(value: String) {
        val labelsInBaseNotes =
            database.getBaseNoteDao().getListOfBaseNotesByLabel(value).map { baseNote ->
                val labels = ArrayList(baseNote.labels)
                labels.remove(value)
                LabelsInBaseNote(baseNote.id, labels)
            }
        database.getBaseNoteDao().update(labelsInBaseNotes)
        database.getLabelDao().delete(value)
    }

    @Transaction
    open suspend fun updateLabel(oldValue: String, newValue: String) {
        val labelsInBaseNotes =
            database.getBaseNoteDao().getListOfBaseNotesByLabel(oldValue).map { baseNote ->
                val labels = ArrayList(baseNote.labels)
                labels.remove(oldValue)
                labels.add(newValue)
                LabelsInBaseNote(baseNote.id, labels)
            }
        database.getBaseNoteDao().update(labelsInBaseNotes)
        database.getLabelDao().update(oldValue, newValue)
    }

    @Transaction
    open suspend fun importBackup(
        context: ContextWrapper,
        baseNotes: List<BaseNote>,
        labels: List<Label>,
    ) {
        database.getBaseNoteDao().insertSafe(context, baseNotes)
        database.getLabelDao().insert(labels)
    }

    /**
     * Import backup with remapping of note links inside spans. Uses a single bulk insert to obtain
     * new IDs, builds an oldId->newId mapping based on [originalIds] order, then rewrites any
     * note:// links in spans to reference the newly created IDs.
     */
    @Transaction
    open suspend fun importBackup(
        baseNotes: List<BaseNote>,
        originalIds: List<Long>,
        labels: List<Label>,
    ) {
        val baseNoteDao = database.getBaseNoteDao()
        val newIds = baseNoteDao.insert(baseNotes)
        // Build old->new mapping using positional correspondence
        val idMap = HashMap<Long, Long>(originalIds.size)
        val count = minOf(originalIds.size, newIds.size)
        for (i in 0 until count) {
            idMap[originalIds[i]] = newIds[i]
        }

        // Remap note links in spans where necessary
        for (i in baseNotes.indices) {
            val note = baseNotes[i]
            val newId = newIds.getOrNull(i) ?: continue
            var changed = false
            val updatedSpans =
                note.spans.map { span ->
                    if (span.link && span.linkData?.isNoteUrl() == true) {
                        val url = span.linkData!!
                        val oldTargetId = url.getNoteIdFromUrl()
                        val type = url.getNoteTypeFromUrl()
                        val newTargetId = idMap[oldTargetId]
                        if (newTargetId != null) {
                            changed = true
                            span.copy(linkData = newTargetId.createNoteUrl(type))
                        } else {
                            span
                        }
                    } else span
                }
            if (changed) {
                baseNoteDao.updateSpans(newId, updatedSpans)
            }
        }

        database.getLabelDao().insert(labels)
    }
}
