package com.philkes.notallyx.utils

import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.dao.BaseNoteDao
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.ColorString
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.NoteViewMode
import com.philkes.notallyx.data.model.Reminder
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AutoRemoveDeletedNotesDbTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<ContextWrapper>()
        db = NotallyDatabase.getFreshDatabase(context, false)

        dao = db.getBaseNoteDao()
    }

    @After
    fun cleanup() {
        runBlocking { dao.deleteAll() }
    }

    companion object {
        private lateinit var db: NotallyDatabase
        private lateinit var dao: BaseNoteDao

        @JvmStatic
        @AfterClass
        fun tearDown() {
            db.close()
        }
    }

    private fun base(body: String, spans: List<SpanRepresentation> = emptyList()): BaseNote =
        BaseNote(
            id = 0,
            type = Type.NOTE,
            folder = Folder.NOTES,
            color = "DEFAULT" as ColorString,
            title = "T",
            pinned = false,
            timestamp = 0L,
            modifiedTimestamp = 0L,
            labels = emptyList(),
            body = body,
            spans = spans,
            items = emptyList<ListItem>(),
            images = emptyList<FileAttachment>(),
            files = emptyList<FileAttachment>(),
            audios = emptyList(),
            reminders = emptyList<Reminder>(),
            viewMode = NoteViewMode.EDIT,
        )

    @Test
    fun performAutoEmptyBin() = runBlocking {
        val ids = longArrayOf(dao.insert(base("Foo")), dao.insert(base("Bar")))
        val timestamp = Instant.now().minus(4, ChronoUnit.DAYS).toEpochMilli()
        val applicationContext = ApplicationProvider.getApplicationContext<ContextWrapper>()
        NotallyXPreferences.getInstance(applicationContext).autoRemoveDeletedNotesAfterDays.save(1)
        dao.move(ids, Folder.DELETED, timestamp = timestamp)

        applicationContext.removeOldDeletedNotes()

        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `performAutoEmptyBin do not delete recently deleted`() = runBlocking {
        val toBeRemovedId = dao.insert(base("Foo"))
        val toBeKeptId = dao.insert(base("Bar"))
        val expired = Instant.now().minus(4, ChronoUnit.DAYS).toEpochMilli()
        val applicationContext = ApplicationProvider.getApplicationContext<ContextWrapper>()
        NotallyXPreferences.getInstance(applicationContext).autoRemoveDeletedNotesAfterDays.save(1)
        dao.move(longArrayOf(toBeRemovedId), Folder.DELETED, timestamp = expired)
        dao.move(longArrayOf(toBeKeptId), Folder.DELETED, timestamp = System.currentTimeMillis())

        applicationContext.removeOldDeletedNotes()

        val remaining = dao.getAll()
        assertEquals(1, remaining.size)
        assertEquals(toBeKeptId, remaining.first().id)
    }
}
