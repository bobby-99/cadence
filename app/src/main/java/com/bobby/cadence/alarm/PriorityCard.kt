package com.bobby.cadence.alarm

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bobby.cadence.CadenceApp
import com.bobby.cadence.MainActivity
import com.bobby.cadence.data.Actions
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.Task
import com.bobby.cadence.data.date
import com.bobby.cadence.data.time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Task data carried inside intents so the card never has to wait for the database. */
fun Intent.putTask(t: Task): Intent = apply {
    putExtra("t_id", t.id)
    putExtra("t_title", t.title)
    putExtra("t_notes", t.notes)
    putExtra("t_prio", t.priority)
    putExtra("t_date", t.dueDate ?: -1L)
    putExtra("t_time", t.dueTime ?: -1)
}

fun Intent.getTask(): Task? {
    if (!hasExtra("t_id")) return null
    val d = getLongExtra("t_date", -1L)
    val tm = getIntExtra("t_time", -1)
    return Task(
        id = getLongExtra("t_id", 0L),
        title = getStringExtra("t_title") ?: return null,
        notes = getStringExtra("t_notes") ?: "",
        priority = getIntExtra("t_prio", 0),
        dueDate = if (d >= 0) d else null,
        dueTime = if (tm >= 0) tm else null,
    )
}

/** The dark card shown over other apps (phone on and unlocked) for Medium and High priority tasks. */
object PriorityCard {
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    class Handlers(
        val onDone: () -> Unit,
        val onSnooze: (Int) -> Unit,
        val onOpen: () -> Unit,
        val onDismiss: () -> Unit,
    )

    fun handlers(ctx: Context, t: Task, close: () -> Unit): Handlers {
        val app = ctx.applicationContext as CadenceApp
        return Handlers(
            onDone = { bg.launch { app.repo.completeById(t.id) }; close() },
            onSnooze = { m -> bg.launch { app.repo.snooze(t.id, m) }; close() },
            onOpen = {
                try {
                    ctx.startActivity(
                        Intent(ctx, MainActivity::class.java)
                            .putExtra(Actions.EXTRA_TASK_ID, t.id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                } catch (e: Exception) {
                }
                close()
            },
            onDismiss = close,
        )
    }

    private fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(ctx: Context, color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = ctx.dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(ctx.dp(1), strokeColor)
        }

    private fun label(ctx: Context, text: String, sp: Float, color: Int, family: String = "sans-serif"): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = sp
            setTextColor(color)
            typeface = Typeface.create(family, Typeface.NORMAL)
        }

    fun build(ctx: Context, t: Task, h: Handlers): View {
        val app = ctx.applicationContext as CadenceApp
        val is24 = DateFormat.is24HourFormat(ctx)
        val accent = 0xFFF2B04A.toInt()
        val high = t.priority >= 3
        val tone = if (high) 0xFFFF7A85.toInt() else 0xFFF5B66B.toInt()

        val root = FrameLayout(ctx).apply {
            setBackgroundColor(0xE6000000.toInt())
            isClickable = true
            isFocusableInTouchMode = true
            setOnClickListener { h.onDismiss() }
            setOnKeyListener { _, keyCode, ev ->
                if (keyCode == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) { h.onDismiss(); true } else false
            }
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(ctx, 0xFF121110.toInt(), 28, 0xFF2E2B22.toInt())
            setPadding(ctx.dp(24), ctx.dp(24), ctx.dp(24), ctx.dp(18))
            isClickable = true
        }
        val width = minOf(ctx.resources.displayMetrics.widthPixels - ctx.dp(40), ctx.dp(420))
        root.addView(card, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        card.addView(label(ctx, if (high) "HIGH PRIORITY" else "MEDIUM PRIORITY", 11f, tone, "sans-serif-medium").apply {
            letterSpacing = 0.14f
        })

        card.addView(label(ctx, t.title, 25f, 0xFFF2F2F5.toInt(), "sans-serif-medium").apply {
            maxLines = 4
            setLineSpacing(0f, 1.05f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(10) })

        val whenText = listOfNotNull(
            t.date?.let { Fmt.date(it, LocalDate.now()) },
            t.time?.let { Fmt.time(it, is24) },
        ).joinToString(" · ")
        if (whenText.isNotEmpty()) {
            card.addView(label(ctx, whenText, 15f, accent), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(6) })
        }
        if (t.notes.isNotBlank()) {
            card.addView(label(ctx, t.notes, 14f, 0xFF9A9AA6.toInt()).apply { maxLines = 3 },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(8) })
        }

        // Done
        card.addView(
            label(ctx, "Mark done", 16f, 0xFF1A1405.toInt(), "sans-serif-medium").apply {
                gravity = Gravity.CENTER
                background = rounded(ctx, accent, 26)
                setOnClickListener { h.onDone() }
            },
            LinearLayout.LayoutParams(-1, ctx.dp(54)).apply { topMargin = ctx.dp(22) },
        )

        // Snooze chips
        card.addView(label(ctx, "SNOOZE", 11f, 0xFF63636E.toInt(), "sans-serif-medium").apply { letterSpacing = 0.14f },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(20) })

        fun chip(text: String, minutes: () -> Int): TextView = label(ctx, text, 13f, 0xFFF4F1EA.toInt(), "sans-serif-medium").apply {
            gravity = Gravity.CENTER
            background = rounded(ctx, 0xFF1A1917.toInt(), 22, 0xFF2B2A27.toInt())
            setOnClickListener { h.onSnooze(minutes()) }
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("10 min" to 10, "30 min" to 30, "1 hour" to 60).forEachIndexed { i, (text, m) ->
            row.addView(chip(text) { m }, LinearLayout.LayoutParams(0, ctx.dp(44), 1f).apply { if (i > 0) leftMargin = ctx.dp(8) })
        }
        card.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(8) })

        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        var n = 0
        if (SnoozeTargets.eveningAvailable()) {
            row2.addView(chip("This evening") { SnoozeTargets.minutesUntil(SnoozeTargets.eveningMillis()) }, LinearLayout.LayoutParams(0, ctx.dp(44), 1f))
            n++
        }
        row2.addView(
            chip("Tomorrow morning") { SnoozeTargets.minutesUntil(SnoozeTargets.tomorrowMorningMillis()) },
            LinearLayout.LayoutParams(0, ctx.dp(44), 1f).apply { if (n > 0) leftMargin = ctx.dp(8) },
        )
        card.addView(row2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(8) })

        // Open / Dismiss
        val bottom = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(label(ctx, "Open task", 14f, accent, "sans-serif-medium").apply {
            gravity = Gravity.CENTER
            setOnClickListener { h.onOpen() }
        }, LinearLayout.LayoutParams(0, ctx.dp(48), 1f))
        bottom.addView(label(ctx, "Dismiss", 14f, 0xFF9A9AA6.toInt(), "sans-serif-medium").apply {
            gravity = Gravity.CENTER
            setOnClickListener { h.onDismiss() }
        }, LinearLayout.LayoutParams(0, ctx.dp(48), 1f))
        card.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(8) })

        card.alpha = 0f
        card.scaleX = 0.94f
        card.scaleY = 0.94f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
        return root
    }

    @Suppress("unused")
    private val keepColorImport = Color.TRANSPARENT
}
