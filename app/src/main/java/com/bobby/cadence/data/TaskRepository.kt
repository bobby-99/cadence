package com.bobby.cadence.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bobby.cadence.alarm.Notifier
import com.bobby.cadence.alarm.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TaskRepository(
    private val ctx: Context,
    private val dao: TaskDao,
    private val scheduler: ReminderScheduler,
    private val prefs: Prefs,
    private val focusDao: FocusDao,
) {
    val tasks: Flow<List<Task>> = dao.observeAll()

    suspend fun get(id: Long): Task? = dao.get(id)

    suspend fun add(t: Task): Long {
        val id = dao.insert(t.copy(id = 0))
        val saved = t.copy(id = id, seriesId = if (t.seriesId == 0L) id else t.seriesId)
        if (saved.seriesId != t.seriesId) dao.update(saved)
        scheduler.schedule(saved)
        return id
    }

    suspend fun update(t: Task) {
        dao.update(t)
        scheduler.schedule(t)
        if (t.completed) Notifier.dismiss(ctx, t.id)
    }

    /**
     * Keeps the phone-calendar event in step with the task. [want] = the "add to phone calendar" choice.
     * Returns false only when the user wanted an event but it could not be written.
     */
    suspend fun syncCalendar(t: Task, want: Boolean): Boolean = withContext(Dispatchers.IO) {
        val existing = t.calendarEventId
        if (!want) {
            if (existing != null) {
                CalendarBridge.delete(ctx, existing)
                dao.update(t.copy(calendarEventId = null))
            }
            return@withContext true
        }
        if (t.dueDate == null || !CalendarBridge.canWrite(ctx)) return@withContext false
        if (existing != null && CalendarBridge.update(ctx, existing, t)) return@withContext true
        val cal = CalendarBridge.chooseCalendar(ctx, prefs.state.value.calendarId) ?: return@withContext false
        val id = CalendarBridge.insert(ctx, t, cal.id) ?: return@withContext false
        dao.update(t.copy(calendarEventId = id))
        true
    }

    suspend fun delete(t: Task) {
        val ev = t.calendarEventId
        // a finished repeat keeps the series in the calendar
        if (ev != null && !(t.completed && t.recurrence != null)) withContext(Dispatchers.IO) { CalendarBridge.delete(ctx, ev) }
        dao.delete(t.id)
        scheduler.cancel(t.id)
        Notifier.dismiss(ctx, t.id)
    }

    /** Puts a deleted task back with its old id (used by Undo). */
    suspend fun restore(t: Task) {
        var task = t
        if (t.calendarEventId != null) {
            val newId = withContext(Dispatchers.IO) {
                CalendarBridge.chooseCalendar(ctx, prefs.state.value.calendarId)?.let { CalendarBridge.insert(ctx, t, it.id) }
            }
            task = t.copy(calendarEventId = newId)
        }
        // updatedAt = 0 makes the database stamp it as new, which also clears the "deleted" mark
        dao.insert(task.copy(updatedAt = 0L))
        scheduler.schedule(task)
    }

    /**
     * Completes a task. Repeating tasks get their next occurrence created automatically.
     * Returns the id of the new occurrence, if any.
     */
    suspend fun complete(t: Task, today: LocalDate = LocalDate.now()): Long? {
        val done = t.copy(completed = true, completedAt = System.currentTimeMillis(), snoozedUntil = null)
        dao.update(done)
        scheduler.cancel(t.id)
        Notifier.dismiss(ctx, t.id)

        val rule = t.rule
        val due = t.date
        if (rule != null && due != null) {
            val next = RecurrenceCalc.next(due, today, today, rule, t.occurrence)
            if (next != null) {
                val clone = t.copy(
                    id = 0,
                    uuid = "",
                    updatedAt = 0L,
                    completed = false,
                    completedAt = null,
                    dueDate = next.toEpochDay(),
                    occurrence = t.occurrence + 1,
                    snoozedUntil = null,
                    createdAt = System.currentTimeMillis(),
                )
                val newId = dao.insert(clone)
                scheduler.schedule(clone.copy(id = newId))
                return newId
            }
        }
        return null
    }

    suspend fun completeById(id: Long) {
        val t = dao.get(id) ?: return
        if (!t.completed) complete(t)
    }

    /** Reopens a completed task and removes the follow-up occurrence it created, if still untouched. */
    suspend fun uncomplete(t: Task) {
        if (t.recurrence != null && t.seriesId != 0L) {
            val followUp = dao.getAll().firstOrNull {
                it.seriesId == t.seriesId && it.occurrence == t.occurrence + 1 && !it.completed
            }
            if (followUp != null) {
                dao.delete(followUp.id)
                scheduler.cancel(followUp.id)
            }
        }
        val reopened = t.copy(completed = false, completedAt = null)
        dao.update(reopened)
        scheduler.schedule(reopened)
    }

    suspend fun skipById(id: Long) {
        val t = dao.get(id) ?: return
        if (!t.completed) skip(t)
        Notifier.dismiss(ctx, id)
    }

    /** Moves a repeating task to its next date without logging it as done. */
    suspend fun skip(t: Task, today: LocalDate = LocalDate.now()) {
        val rule = t.rule
        val due = t.date
        if (rule == null || due == null) return
        val next = RecurrenceCalc.next(due, today, today, rule, t.occurrence)
        if (next == null) {
            complete(t, today)
        } else {
            update(t.copy(dueDate = next.toEpochDay(), occurrence = t.occurrence + 1, snoozedUntil = null))
        }
    }

    /**
     * Moves every overdue task to today. A task whose time has already passed today becomes an
     * all-day task for today. Returns the tasks as they were, so the move can be undone.
     */
    suspend fun moveOverdueToToday(today: LocalDate, nowMin: Int): List<Task> {
        val old = dao.getAll().filter { !it.completed && it.dueDate != null && it.dueDate < today.toEpochDay() }
        for (t in old) {
            val keep = t.dueTime != null && t.dueTime > nowMin
            val moved = t.copy(dueDate = today.toEpochDay(), dueTime = if (keep) t.dueTime else null, snoozedUntil = null)
            update(moved)
            if (moved.calendarEventId != null && moved.recurrence == null) syncCalendar(moved, true)
        }
        return old
    }

    suspend fun putBack(old: List<Task>) {
        for (t in old) update(t)
    }

    suspend fun snooze(id: Long, minutes: Int) = snoozeUntil(id, System.currentTimeMillis() + minutes * 60_000L)

    /** Returns the task as it was before, so the snooze can be undone. */
    suspend fun snoozeUntil(id: Long, millis: Long): Task? {
        val t = dao.get(id) ?: return null
        val updated = t.copy(snoozedUntil = millis)
        dao.update(updated)
        scheduler.schedule(updated)
        Notifier.dismiss(ctx, id)
        return t
    }

    suspend fun onReminderFired(id: Long, idx: Int, at: Long = 0L) {
        val t = dao.get(id) ?: return
        if (at > 0L) com.bobby.cadence.alarm.FiredLog.mark(ctx, id, idx, at)
        if (t.completed) return
        var current = t
        if (idx == ReminderScheduler.SNOOZE_IDX) {
            current = t.copy(snoozedUntil = null)
            dao.update(current)
        }
        // the notification and the card are independent: one failing must never stop the other
        try {
            Notifier.show(ctx, current, idx, prefs.state.value.snoozeMin)
        } catch (e: Exception) {
        }
        if (current.priority >= 2 && prefs.state.value.priorityOverlay) {
            try {
                com.bobby.cadence.alarm.OverlayService.show(ctx, current)
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Shows, once and quietly, reminders from the last two hours that never appeared
     * (phone was off, or the system dropped the alarm).
     */
    suspend fun deliverMissed() {
        com.bobby.cadence.alarm.FiredLog.prune(ctx)
        for (t in dao.getAll()) {
            if (t.completed) continue
            val missed = scheduler.missed(t)
            if (missed.isEmpty()) continue
            for ((idx, at) in missed) com.bobby.cadence.alarm.FiredLog.mark(ctx, t.id, idx, at)
            var current = t
            if (missed.any { it.first == ReminderScheduler.SNOOZE_IDX }) {
                current = t.copy(snoozedUntil = null)
                dao.update(current)
            }
            try {
                Notifier.show(ctx, current, missed.last().first, prefs.state.value.snoozeMin, late = true)
            } catch (e: Exception) {
            }
        }
    }

    /** Re-arms every alarm. Never marks past reminders as shown, so [deliverMissed] can still see them. */
    suspend fun rescheduleAll() {
        Notifier.ensureChannel(ctx)
        for (t in dao.getAll()) if (!t.completed) scheduler.schedule(t, userEdit = false)
        val s = prefs.state.value
        com.bobby.cadence.alarm.SummaryScheduler.schedule(ctx, s)
        BackupRunner.schedule(ctx, s)
    }

    suspend fun cleanup() {
        val days = prefs.state.value.keepCompletedDays
        if (days > 0) dao.deleteCompletedBefore(System.currentTimeMillis() - days * 86_400_000L)
    }

    suspend fun clearCompleted(): Int = dao.deleteAllCompleted()

    suspend fun exportJson(): String =
        BackupManager.toJson(dao.getAll(), focusDao.getAll(), prefs.state.value)

    /**
     * Replaces everything with the backup: tasks, focus sessions and settings.
     * Returns the number of tasks imported, [IMPORT_INVALID] for a file that is not a Cadence backup,
     * or [IMPORT_TOO_NEW] for one written by a newer version of the app.
     */
    suspend fun importJson(text: String): Int {
        val backup = when (val p = BackupManager.parse(text)) {
            is BackupManager.Parsed.Ok -> p.backup
            BackupManager.Parsed.TooNew -> return IMPORT_TOO_NEW
            BackupManager.Parsed.Invalid -> return IMPORT_INVALID
        }
        for (t in dao.getAll()) {
            scheduler.cancel(t.id)
            Notifier.dismiss(ctx, t.id)
        }
        dao.deleteAll()
        dao.insertAll(backup.tasks)
        // version 1 files carry no sessions or settings: keep what is on the phone
        backup.sessions?.let { focusDao.deleteAll(); focusDao.insertAll(it) }
        backup.settings?.let { o -> prefs.update { BackupManager.applySettings(it, o) } }
        rescheduleAll()
        return backup.tasks.size
    }

    companion object {
        const val IMPORT_INVALID = -1
        const val IMPORT_TOO_NEW = -2
    }
}
