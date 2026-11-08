package com.bobby.cadence.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bobby.cadence.MainActivity
import com.bobby.cadence.data.Prefs
import com.bobby.cadence.data.Task
import com.bobby.cadence.data.date
import com.bobby.cadence.data.reminderOffsets
import com.bobby.cadence.data.time
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Every reminder is its own exact alarm, set with AlarmManager.setAlarmClock().
 * Alarm-clock alarms are exempt from Doze and battery optimisation, so they fire on time
 * even when the phone has been idle for hours. No special permission is needed.
 */
class ReminderScheduler(private val ctx: Context, private val prefs: Prefs) {

    companion object {
        const val ACTION_FIRE = "com.bobby.cadence.REMINDER_FIRE"
        const val EXTRA_ID = "task_id"
        const val EXTRA_IDX = "idx"
        const val EXTRA_AT = "at"
        /** reminders that were due this long ago and never showed are delivered late */
        const val CATCH_UP_MS = 2 * 60 * 60 * 1000L
        const val MAX_OFFSETS = 8
        const val SNOOZE_IDX = 15
    }

    private val am: AlarmManager get() = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun fireIntent(id: Long, idx: Int, at: Long = 0L): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("cadence://reminder/$id/$idx"))
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_IDX, idx)
            .putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(
            ctx, (id * 16 + idx).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Every trigger (index, epoch millis) of a task, past or future. */
    private fun allTriggers(t: Task): List<Pair<Int, Long>> {
        val out = ArrayList<Pair<Int, Long>>()
        val date = t.date
        if (date != null) {
            val dueTime = t.time ?: prefs.state.value.defaultTimeMin.let { LocalTime.of(it / 60, it % 60) }
            val due = ZonedDateTime.of(date, dueTime, ZoneId.systemDefault())
            t.reminderOffsets.take(MAX_OFFSETS).forEachIndexed { i, off ->
                out.add(i to due.minusMinutes(off.toLong()).toInstant().toEpochMilli())
            }
        }
        val snooze = t.snoozedUntil
        if (snooze != null) out.add(SNOOZE_IDX to snooze)
        return out
    }

    /** Trigger times (index, epoch millis) that are still in the future. */
    fun triggerTimes(t: Task): List<Pair<Int, Long>> {
        val now = System.currentTimeMillis()
        return allTriggers(t).filter { it.second > now }
    }

    /** Triggers from the last [CATCH_UP_MS] that never showed (phone was off, alarm was lost). */
    fun missed(t: Task): List<Pair<Int, Long>> {
        val now = System.currentTimeMillis()
        return allTriggers(t).filter { (idx, at) ->
            at <= now && now - at <= CATCH_UP_MS && !FiredLog.has(ctx, t.id, idx, at)
        }
    }

    /**
     * [userEdit] is true when the person just saved the task: reminder times already in the past are
     * then deliberately skipped, so they are not announced later as "missed".
     */
    fun schedule(t: Task, userEdit: Boolean = true) {
        cancel(t.id)
        if (t.completed) return
        val now = System.currentTimeMillis()
        for ((idx, millis) in allTriggers(t)) {
            if (millis <= now) {
                if (userEdit) FiredLog.mark(ctx, t.id, idx, millis)
                continue
            }
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent()), fireIntent(t.id, idx, millis))
            } catch (e: Exception) {
                // never let a scheduling problem crash the app
            }
        }
    }

    fun cancel(id: Long) {
        for (idx in 0 until MAX_OFFSETS) cancelOne(id, idx)
        cancelOne(id, SNOOZE_IDX)
    }

    private fun cancelOne(id: Long, idx: Int) {
        try {
            val pi = fireIntent(id, idx)
            am.cancel(pi)
            pi.cancel()
        } catch (e: Exception) {
        }
    }
}

/** Remembers which reminders already showed, so catching up never announces the same one twice. */
object FiredLog {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("cadence_fired", Context.MODE_PRIVATE)
    private fun key(id: Long, idx: Int, at: Long) = "$id:$idx:$at"

    fun has(ctx: Context, id: Long, idx: Int, at: Long): Boolean = sp(ctx).contains(key(id, idx, at))

    fun mark(ctx: Context, id: Long, idx: Int, at: Long) {
        sp(ctx).edit().putLong(key(id, idx, at), System.currentTimeMillis()).apply()
    }

    /** Forget entries older than three days. */
    fun prune(ctx: Context) {
        val cut = System.currentTimeMillis() - 3 * 86_400_000L
        val p = sp(ctx)
        val e = p.edit()
        for ((k, v) in p.all) if (v is Long && v < cut) e.remove(k)
        e.apply()
    }
}
