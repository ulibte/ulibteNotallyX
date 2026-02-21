package com.philkes.notallyx.presentation.activity.note.reminders

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.Reminder
import com.philkes.notallyx.utils.canScheduleAlarms
import com.philkes.notallyx.utils.cancelReminder
import com.philkes.notallyx.utils.createChannelIfNotExists
import com.philkes.notallyx.utils.getOpenNotePendingIntent
import com.philkes.notallyx.utils.scheduleReminder
import com.philkes.notallyx.utils.truncate
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * [BroadcastReceiver] for sending notifications via [NotificationManager] for [Reminder]s.
 * Reschedules reminders on [Intent.ACTION_BOOT_COMPLETED] or if
 * [AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED] has changed and exact alarms
 * are allowed. For [Reminder] that have [Reminder.repetition] it automatically reschedules the next
 * alarm.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive: ${intent?.action}")
        if (intent == null || context == null) {
            return
        }
        val canScheduleExactAlarms = context.canScheduleAlarms()
        goAsyncScope {
            if (intent.action == null) {
                if (!canScheduleExactAlarms) {
                    return@goAsyncScope
                }
                val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
                val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
                notify(context, noteId, reminderId)
            } else {
                when {
                    intent.action == Intent.ACTION_BOOT_COMPLETED -> {
                        if (canScheduleExactAlarms) {
                            rescheduleAlarms(context)
                        }
                        restoreRemindersNotifications(context)
                    }

                    intent.action ==
                        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                        if (canScheduleExactAlarms) {
                            rescheduleAlarms(context)
                        } else {
                            cancelAlarms(context)
                        }
                    }

                    intent.action == ACTION_NOTIFICATION_DISMISSED -> {
                        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
                        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
                        Log.d(TAG, "Notification dismissed for note: $noteId")
                        setIsNotificationVisible(false, context, noteId, reminderId)
                    }
                }
            }
        }
    }

    private suspend fun notify(
        context: Context,
        noteId: Long,
        reminderId: Long,
        schedule: Boolean = true,
    ) {
        Log.d(TAG, "notify: noteId: $noteId reminderId: $reminderId")
        val database = getDatabase(context)
        val manager = context.getSystemService<NotificationManager>()!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createChannelIfNotExists(
                NOTIFICATION_CHANNEL_ID,
                importance = NotificationManager.IMPORTANCE_HIGH,
            )
        }
        database.getBaseNoteDao().get(noteId)?.let { note ->
            val notification =
                NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.notebook)
                    .setContentTitle(note.title)
                    .setContentText(note.body.truncate(200))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .addAction(
                        R.drawable.visibility,
                        context.getString(R.string.open_note),
                        context.getOpenNotePendingIntent(note),
                    )
                    .setDeleteIntent(getDeletePendingIntent(context, noteId, reminderId))
                    .build()
            note.reminders
                .find { it.id == reminderId }
                ?.let { reminder: Reminder ->
                    manager.notify(note.id.toString(), reminderId.toInt(), notification)
                    if (schedule)
                        context.scheduleReminder(note.id, reminder, forceRepetition = true)
                    setIsNotificationVisible(true, context, note.id, reminderId)
                }
        }
    }

    private suspend fun rescheduleAlarms(context: Context) {
        val database = getDatabase(context)
        val now = Date()
        val noteReminders = database.getBaseNoteDao().getAllReminders()
        val noteRemindersWithFutureNotify =
            noteReminders.flatMap { (noteId, reminders) ->
                reminders
                    .filter { reminder ->
                        reminder.repetition != null || reminder.dateTime.after(now)
                    }
                    .map { reminder -> Pair(noteId, reminder) }
            }
        Log.d(TAG, "rescheduleAlarms: ${noteRemindersWithFutureNotify.size} alarms")
        noteRemindersWithFutureNotify.forEach { (noteId, reminder) ->
            context.scheduleReminder(noteId, reminder)
        }
    }

    private suspend fun cancelAlarms(context: Context) {
        val database = getDatabase(context)
        val noteReminders = database.getBaseNoteDao().getAllReminders()
        val noteRemindersWithFutureNotify =
            noteReminders.flatMap { (noteId, reminders) ->
                reminders.map { reminder -> Pair(noteId, reminder.id) }
            }
        Log.d(TAG, "cancelAlarms: ${noteRemindersWithFutureNotify.size} alarms")
        noteRemindersWithFutureNotify.forEach { (noteId, reminderId) ->
            context.cancelReminder(noteId, reminderId)
        }
    }

    private suspend fun setIsNotificationVisible(
        isNotificationVisible: Boolean,
        context: Context,
        noteId: Long,
        reminderId: Long,
    ) {
        val baseNoteDao = getDatabase(context).getBaseNoteDao()
        val note = baseNoteDao.get(noteId) ?: return
        val currentReminders = note.reminders.toMutableList()
        val index = currentReminders.indexOfFirst { it.id == reminderId }
        if (index != -1) {
            if (currentReminders[index].isNotificationVisible != isNotificationVisible) {
                currentReminders[index] =
                    currentReminders[index].copy(isNotificationVisible = isNotificationVisible)
                baseNoteDao.updateReminders(noteId, currentReminders)
            }
        }
    }

    private fun getDeletePendingIntent(
        context: Context,
        noteId: Long,
        reminderId: Long,
    ): PendingIntent {
        val deleteIntent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_NOTIFICATION_DISMISSED
                putExtra(EXTRA_NOTE_ID, noteId)
                putExtra(EXTRA_REMINDER_ID, reminderId)
            }
        val deletePendingIntent =
            PendingIntent.getBroadcast(
                context,
                "$noteId-$reminderId".hashCode(),
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return deletePendingIntent
    }

    private suspend fun restoreRemindersNotifications(context: Context) {
        val baseNoteDao = getDatabase(context).getBaseNoteDao()
        val allNotes = baseNoteDao.getAllNotes()
        allNotes.forEach { note ->
            val now = Date(System.currentTimeMillis())
            val mostRecentReminder =
                note.reminders
                    .filter { it.dateTime <= now } // Only reminders that have already passed
                    .maxByOrNull { it.dateTime } ?: return@forEach
            if (mostRecentReminder.isNotificationVisible) {
                notify(context, note.id, mostRecentReminder.id, schedule = false)
            }
        }
    }

    private fun getDatabase(context: Context): NotallyDatabase {
        return NotallyDatabase.getDatabase(context.applicationContext as Application, false).value
    }

    private fun goAsyncScope(codeBlock: suspend CoroutineScope.() -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                codeBlock()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"

        private const val NOTIFICATION_CHANNEL_ID = "Reminders"

        const val EXTRA_REMINDER_ID = "notallyx.intent.extra.REMINDER_ID"
        const val EXTRA_NOTE_ID = "notallyx.intent.extra.NOTE_ID"
        const val ACTION_NOTIFICATION_DISMISSED =
            "com.philkes.notallyx.ACTION_NOTIFICATION_DISMISSED"
    }
}
