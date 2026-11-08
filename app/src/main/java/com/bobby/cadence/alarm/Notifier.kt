package com.bobby.cadence.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bobby.cadence.MainActivity
import com.bobby.cadence.R
import com.bobby.cadence.data.Actions
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.Task
import com.bobby.cadence.data.reminderOffsets
import com.bobby.cadence.data.time

object Notifier {
    const val CHANNEL = "task_reminders"
    /** task notifications carry a tag so a task id can never clash with the focus or summary notifications */
    private const val TAG = "task"
    const val ACTION_DONE = "com.bobby.cadence.ACTION_DONE"
    const val ACTION_SNOOZE = "com.bobby.cadence.ACTION_SNOOZE"
    const val ACTION_SKIP = "com.bobby.cadence.ACTION_SKIP"
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_UNTIL = "until"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminders for your tasks"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 120, 250)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun canPost(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** False when the "Task reminders" channel was switched off or turned down (no pop-up) in system settings. */
    fun channelOk(ctx: Context): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = nm.getNotificationChannel(CHANNEL) ?: return true
        return ch.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun dismiss(ctx: Context, id: Long) {
        NotificationManagerCompat.from(ctx).cancel(TAG, id.toInt())
    }

    private fun actionIntent(ctx: Context, action: String, id: Long, minutes: Int, until: Long = 0L): PendingIntent {
        val intent = Intent(ctx, ActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("cadence://action/$id/$action/$minutes/$until"))
            .putExtra(ReminderScheduler.EXTRA_ID, id)
            .putExtra(EXTRA_MINUTES, minutes)
            .putExtra(EXTRA_UNTIL, until)
        return PendingIntent.getBroadcast(
            ctx, (id * 5 + (if (action == ACTION_DONE) 0 else if (action == ACTION_SKIP) 4 else if (until > 0L) 3 else if (minutes <= 30) 1 else 2)).toInt() + 1_000_000, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Buttons that work straight from the lock screen, without unlocking the phone first. */
    private fun action(title: String, pi: PendingIntent): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, title, pi).setAuthenticationRequired(false).build()

    fun show(ctx: Context, task: Task, idx: Int, snoozeMin: Int, late: Boolean = false) {
        if (!canPost(ctx)) return
        ensureChannel(ctx)
        val is24 = DateFormat.is24HourFormat(ctx)
        val offsets = task.reminderOffsets
        val offset = if (idx in offsets.indices) offsets[idx] else 0
        val timeStr = task.time?.let { Fmt.time(it, is24) }
        val text = when {
            late -> "Missed reminder" + (timeStr?.let { " · was due $it" } ?: "")
            idx == ReminderScheduler.SNOOZE_IDX -> "Snoozed reminder" + (timeStr?.let { " · due $it" } ?: "")
            offset > 0 -> "In ${Fmt.offset(offset)}" + (timeStr?.let { " · $it" } ?: "")
            timeStr != null -> "Due now · $timeStr"
            else -> "Due today"
        }
        val open = PendingIntent.getActivity(
            ctx, task.id.toInt(),
            Intent(ctx, MainActivity::class.java)
                .putExtra(Actions.EXTRA_TASK_ID, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val later = SnoozeTargets.smartLater()
        val body = if (task.notes.isBlank()) text else "$text\n${task.notes}"
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(task.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFF2B04A.toInt())
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(action("Done", actionIntent(ctx, ACTION_DONE, task.id, 0)))
            .also { nb ->
                // repeating tasks get Skip (jump to the next date); others get the usual snooze length
                if (task.recurrence != null) nb.addAction(action("Skip", actionIntent(ctx, ACTION_SKIP, task.id, 0)))
                else nb.addAction(action("Snooze ${Fmt.offset(snoozeMin)}", actionIntent(ctx, ACTION_SNOOZE, task.id, snoozeMin)))
            }
            .addAction(action(later.first, actionIntent(ctx, ACTION_SNOOZE, task.id, 0, later.second)))

        val n = b.build()
        try {
            NotificationManagerCompat.from(ctx).notify(TAG, task.id.toInt(), n)
        } catch (e: SecurityException) {
        }
    }
}
