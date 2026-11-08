package com.bobby.cadence.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bobby.cadence.CadenceApp
import com.bobby.cadence.MainActivity
import com.bobby.cadence.R
import com.bobby.cadence.data.Actions
import com.bobby.cadence.data.AppSettings
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.Task
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** One quiet notification each morning: what is due today and how much is overdue. */
object SummaryScheduler {
    const val CHANNEL = "daily_summary"
    private const val ID = 5001

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 77,
        Intent(ctx, SummaryReceiver::class.java).setAction("com.bobby.cadence.SUMMARY"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context, s: AppSettings) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(ctx))
            if (!s.summaryEnabled) return
            val now = ZonedDateTime.now()
            var at = now.toLocalDate().atTime(s.summaryTimeMin / 60, s.summaryTimeMin % 60).atZone(ZoneId.systemDefault())
            if (!at.isAfter(now)) at = at.plusDays(1)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending(ctx))
        } catch (e: Exception) {
        }
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Morning summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A daily overview of what is due"
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    fun post(ctx: Context, tasks: List<Task>) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val today = LocalDate.now().toEpochDay()
        val open = tasks.filter { !it.completed }
        val overdue = open.count { it.dueDate != null && it.dueDate < today }
        val todays = open.filter { it.dueDate == today }
            .sortedWith(compareBy<Task> { it.dueTime ?: -1 }.thenByDescending { it.priority })
        if (todays.isEmpty() && overdue == 0) return

        ensureChannel(ctx)
        val is24 = DateFormat.is24HourFormat(ctx)
        val title = if (todays.isNotEmpty()) "${todays.size} ${if (todays.size == 1) "task" else "tasks"} today"
        else "Nothing due today"
        val short = listOfNotNull(
            if (overdue > 0) "$overdue overdue" else null,
            todays.firstOrNull { it.priority >= 2 }?.let { "Important: ${it.title}" },
        ).joinToString(" · ").ifEmpty { todays.first().title }

        val style = NotificationCompat.InboxStyle()
        todays.take(6).forEach { t ->
            val time = t.dueTime?.let { Fmt.minutes(it, is24) + "  " } ?: ""
            style.addLine(time + t.title)
        }
        if (todays.size > 6) style.setSummaryText("+${todays.size - 6} more")
        else if (overdue > 0) style.setSummaryText("$overdue overdue")

        val open1 = PendingIntent.getActivity(
            ctx, 78,
            Intent(ctx, MainActivity::class.java).setAction(Actions.OPEN_TASKS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title)
            .setContentText(short)
            .setStyle(style)
            .setColor(0xFFA5B4FC.toInt())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(open1)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID, n)
        } catch (e: SecurityException) {
        }
    }
}

class SummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as CadenceApp
        runAsync {
            try {
                SummaryScheduler.post(app, app.db.taskDao().getAll())
            } finally {
                // always line up tomorrow's summary
                SummaryScheduler.schedule(app, app.prefs.state.value)
            }
        }
    }
}
