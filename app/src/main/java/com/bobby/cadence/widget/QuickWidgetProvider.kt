package com.bobby.cadence.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.bobby.cadence.CadenceApp
import com.bobby.cadence.MainActivity
import com.bobby.cadence.R
import com.bobby.cadence.data.Actions
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.Task
import com.bobby.cadence.data.date
import com.bobby.cadence.data.time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Home-screen widget: the next three tasks, tick one off, or add one without opening the app. */
class QuickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { render(context, appWidgetManager, appWidgetIds) } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_DONE) {
            val id = intent.getLongExtra(EXTRA_ID, -1L)
            if (id <= 0) return
            val app = context.applicationContext as CadenceApp
            val pending = goAsync()
            scope.launch {
                try { app.repo.completeById(id) } catch (e: Exception) { }
                try { renderAll(context) } finally { pending.finish() }
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.bobby.cadence.WIDGET_DONE"
        private const val EXTRA_ID = "task_id"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Call whenever tasks change. */
        fun refresh(ctx: Context) {
            scope.launch { try { renderAll(ctx) } catch (e: Exception) { } }
        }

        private suspend fun renderAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, QuickWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        private fun openApp(ctx: Context, action: String?, code: Int, taskId: Long = -1L): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (action != null) i.setAction(action) else i.setAction(Intent.ACTION_VIEW)
            if (taskId > 0) i.putExtra(Actions.EXTRA_TASK_ID, taskId)
            return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private suspend fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val app = ctx.applicationContext as CadenceApp
            val all = app.db.taskDao().getAll()
            val today = LocalDate.now()
            val is24 = DateFormat.is24HourFormat(ctx)
            val open = all.filter { !it.completed }
            val order = compareBy<Task>({ it.dueDate ?: Long.MAX_VALUE }, { it.dueTime ?: 24 * 60 + 1 }, { -it.priority })
            val due = open.filter { it.dueDate != null && it.dueDate <= today.toEpochDay() }.sortedWith(order)
            val upcoming = if (due.isEmpty()) open.filter { it.dueDate != null && it.dueDate > today.toEpochDay() }.sortedWith(order) else emptyList()
            val shown = (if (due.isNotEmpty()) due else upcoming).take(3)
            val total = if (due.isNotEmpty()) due.size else upcoming.size

            val rowIds = intArrayOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)
            val chkIds = intArrayOf(R.id.w_chk1, R.id.w_chk2, R.id.w_chk3)
            val titleIds = intArrayOf(R.id.w_title1, R.id.w_title2, R.id.w_title3)
            val timeIds = intArrayOf(R.id.w_time1, R.id.w_time2, R.id.w_time3)

            for (wid in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_tasks)
                v.setTextViewText(R.id.w_title, if (due.isNotEmpty() || shown.isEmpty()) "Today" else "Next up")
                v.setTextViewText(
                    R.id.w_sub,
                    when {
                        shown.isEmpty() -> "Nothing left"
                        total > 3 -> "$total tasks \u00B7 ${total - 3} more"
                        total == 1 -> "1 task"
                        else -> "$total tasks"
                    },
                )
                v.setOnClickPendingIntent(R.id.w_list, openApp(ctx, Actions.OPEN_TASKS, 2))
                v.setOnClickPendingIntent(
                    R.id.w_add,
                    PendingIntent.getActivity(
                        ctx, 1, Intent(ctx, WidgetAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                for (i in 0 until 3) {
                    val t = shown.getOrNull(i)
                    if (t == null) {
                        v.setViewVisibility(rowIds[i], if (shown.isEmpty()) View.GONE else View.INVISIBLE)
                        continue
                    }
                    v.setViewVisibility(rowIds[i], View.VISIBLE)
                    v.setTextViewText(titleIds[i], t.title)
                    val d = t.date
                    val tm = t.time
                    val label = when {
                        d != null && d.isBefore(today) -> "Overdue"
                        d != null && d.isAfter(today) -> Fmt.date(d, today) + (tm?.let { " " + Fmt.time(it, is24) } ?: "")
                        tm != null -> Fmt.time(tm, is24)
                        else -> ""
                    }
                    v.setTextViewText(timeIds[i], label)
                    v.setTextColor(timeIds[i], if (label == "Overdue") 0xFFFF8F7A.toInt() else 0xFFF2B04A.toInt())
                    v.setOnClickPendingIntent(rowIds[i], openApp(ctx, null, 300000 + (t.id % 100000).toInt(), t.id))
                    v.setOnClickPendingIntent(
                        chkIds[i],
                        PendingIntent.getBroadcast(
                            ctx, 200000 + (t.id % 100000).toInt(),
                            Intent(ctx, QuickWidgetProvider::class.java).setAction(ACTION_DONE).putExtra(EXTRA_ID, t.id),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    )
                }
                v.setViewVisibility(R.id.w_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)
                mgr.updateAppWidget(wid, v)
            }
        }
    }
}
