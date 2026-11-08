package com.bobby.cadence.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.Task
import com.bobby.cadence.data.date
import com.bobby.cadence.data.describe
import com.bobby.cadence.data.reminderOffsets
import com.bobby.cadence.data.rule
import com.bobby.cadence.data.time
import java.time.LocalDate
import java.time.LocalTime

private val TIME_SPLIT = Regex("[\\s\\u202F\\u00A0]+")

/** Two short lines for the time column: "6:00" over "PM", "All" over "day", or the weekday of an overdue task. */
private fun timeLines(task: Task, today: LocalDate, is24: Boolean, overdueDay: Boolean): Pair<String, String?> {
    val date = task.date
    val time = task.time
    if (overdueDay && date != null) {
        val day = DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(date)
        return day to (time?.let { Fmt.time(it, is24) })
    }
    if (time == null) return if (date != null) ("All" to "day") else ("\u2013" to null)
    val text = Fmt.time(time, is24)
    if (is24) return text to null
    val parts = text.split(TIME_SPLIT)
    return if (parts.size >= 2) parts[0] to parts[1] else text to null
}

private val ACTION_W = 70.dp
private const val FLING_PX_S = 1200f

// colours of the four boxes under a task: soft tints that sit quietly on the dark background
private val EditBg = Color(0xFF332A22)
private val SnoozeBg = Color(0xFF1A2230)
private val FocusBg = Color(0xFF362A18)
private val DeleteBg = Color(0xFF301A1C)
private val SnoozeFg = Color(0xFF9DB0CC)

/**
 * Swipe right: one motion, pull far enough (or flick) and the task is completed.
 * Swipe left: the row slides over and rests open on four boxes: Edit, Snooze, Focus, Delete.
 * Tap a box to use it; tap the row or swipe back to close.
 */
@Composable
fun TaskRow(
    task: Task,
    today: LocalDate,
    is24: Boolean,
    showDate: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onSnooze: () -> Unit,
    onFocus: () -> Unit,
    onSwipeComplete: () -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    streak: Int = 0,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val openPx = with(density) { (ACTION_W * 4).toPx() }
    val minFlingPx = with(density) { 36.dp.toPx() }

    var width by remember { mutableFloatStateOf(1f) }
    var x by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    val complete by rememberUpdatedState(onSwipeComplete)
    val delete by rememberUpdatedState(onSwipeDelete)
    val edit by rememberUpdatedState(onEdit)
    val snooze by rememberUpdatedState(onSnooze)
    val focus by rememberUpdatedState(onFocus)
    val openNow by rememberUpdatedState(onOpen)
    val toggle by rememberUpdatedState(onToggle)

    fun glideTo(target: Float, after: () -> Unit = {}) {
        job?.cancel()
        job = scope.launch {
            animate(x, target, animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f)) { v, _ -> x = v }
            after()
        }
    }

    val drag = rememberDraggableState { d -> x = (x + d).coerceIn(-openPx * 1.06f, width * 0.96f) }

    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        // right swipe: a green field with a check that grows as you pull
        if (x > 1f) Box(
            Modifier.matchParentSize().padding(vertical = 0.dp),
        ) {
            val reach = (x / (width * 0.4f)).coerceIn(0f, 1f)
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(with(density) { x.toDp() })
                    .background(C.Done.copy(alpha = 0.35f + 0.65f * reach)),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (x > with(density) { 44.dp.toPx() }) {
                    Icon(
                        Icons.Default.Check, "Complete", tint = C.AccentOn,
                        modifier = Modifier.padding(start = 24.dp).size(22.dp).graphicsLayer { scaleX = 0.8f + 0.4f * reach; scaleY = 0.8f + 0.4f * reach },
                    )
                }
            }
        }
        // left swipe: four boxes at the right edge, revealed as the row slides over them
        if (x < -1f) Box(Modifier.matchParentSize()) {
            Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
                ActionCell(ACTION_W, EditBg, "Edit", C.Text, { glideTo(0f) { edit() } }) {
                    Icon(Icons.Default.Edit, null, tint = C.Text, modifier = Modifier.size(20.dp))
                }
                ActionCell(ACTION_W, SnoozeBg, "Snooze", SnoozeFg, { glideTo(0f) { snooze() } }) {
                    ClockGlyph(SnoozeFg, Modifier.size(20.dp))
                }
                ActionCell(ACTION_W, FocusBg, "Focus", C.Accent, { glideTo(0f) { focus() } }) {
                    GlyphIcon(Glyph.TIMER, C.Accent, size = 20.dp)
                }
                ActionCell(ACTION_W, DeleteBg, "Delete", C.Danger, { glideTo(0f) { delete() } }) {
                    Icon(Icons.Default.Delete, null, tint = C.Danger, modifier = Modifier.size(20.dp))
                }
            }
        }

        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { job?.cancel() },
                    onDragStopped = { v ->
                        when {
                            // right: pull far enough or flick, and the task is done
                            x > 0f && ((v > FLING_PX_S && x > minFlingPx) || x > width * 0.4f) ->
                                glideTo(width) { complete(); x = 0f }
                            x > 0f -> glideTo(0f)
                            // left: open on the four boxes, or close again
                            x < 0f && (-x > openPx * 0.3f || v < -500f) -> glideTo(-openPx)
                            else -> glideTo(0f)
                        }
                    },
                ),
        ) {
            TimelineRow(
                task, today, is24, showDate, highlight, streak,
                onToggle = { if (x > 1f || x < -1f) glideTo(0f) else toggle() },
                onOpen = { if (x > 1f || x < -1f) glideTo(0f) else openNow() },
            )
        }
    }
}

@Composable
private fun TimelineRow(
    task: Task,
    today: LocalDate,
    is24: Boolean,
    showDate: Boolean,
    highlight: Boolean,
    streak: Int,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val date = task.date
    val time = task.time
    val overdue = !task.completed && date != null && (date < today || (date == today && time != null && time < LocalTime.now()))
    val overdueDay = overdue && date != null && date < today
    val rule = task.rule
    val lit = highlight && !task.completed && !overdue
    val (l1, l2) = timeLines(task, today, is24, overdueDay)
    val timeColor = when {
        task.completed -> C.Faint
        overdue -> C.Danger
        lit -> C.Accent
        else -> C.Dim
    }
    val ringColor = if (task.priority > 0) priorityColor(task.priority) else C.Faint

    Row(Modifier.fillMaxWidth().background(C.Bg).height(IntrinsicSize.Min)) {
        // time
        Column(
            Modifier.width(40.dp).padding(top = if (lit) 22.dp else 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(l1, color = timeColor, maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp))
            if (l2 != null) Text(l2, color = if (overdue || lit) timeColor else C.Faint, maxLines = 1, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp))
        }

        // rail with the dot (tap to complete)
        Box(
            Modifier
                .width(44.dp)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(C.Rail, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
                }
                .tap(onClick = onToggle),
            contentAlignment = Alignment.TopCenter,
        ) {
            Canvas(Modifier.padding(top = if (lit) 17.dp else 9.dp).size(if (lit) 26.dp else 26.dp)) {
                val c = center
                val r = 6.dp.toPx()
                when {
                    task.completed -> drawCircle(C.Faint, radius = r)
                    lit -> {
                        drawCircle(C.Accent.copy(alpha = 0.18f), radius = r + 5.dp.toPx())
                        drawCircle(C.Accent, radius = r + 1.dp.toPx())
                    }
                    overdue -> drawCircle(C.Danger, radius = r)
                    else -> {
                        drawCircle(C.Bg, radius = r)
                        drawCircle(ringColor, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }

        // content
        val body: @Composable () -> Unit = {
            Text(
                task.title,
                color = if (task.completed) C.Faint else C.Text,
                style = if (lit) MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 23.sp) else MaterialTheme.typography.titleMedium,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val parts = ArrayList<Pair<String, androidx.compose.ui.graphics.Color>>()
            if (overdue && date != null) {
                val days = today.toEpochDay() - date.toEpochDay()
                parts += (if (days >= 1) "Overdue \u00B7 $days day" + (if (days > 1) "s" else "") else "Overdue") to C.Danger
            } else if (showDate && date != null && date != today) {
                parts += Fmt.date(date, today) to C.Dim
            }
            task.durationMin?.let { parts += durText(it * 60) to C.Dim }
            if (!task.completed && task.priority == 3) parts += "High" to C.High
            if (!task.completed && task.priority == 2) parts += "Medium" to C.Accent
            if (rule != null) parts += ("\u21BB " + rule.describe()) to C.Dim
            if (!task.completed && streak >= 2) parts += "$streak in a row" to C.Accent
            if (!task.completed && task.notes.isNotBlank()) parts += task.notes.lineSequence().first() to C.Dim
            if (parts.isNotEmpty()) {
                val text = buildAnnotatedString {
                    parts.forEachIndexed { i, (t, col) ->
                        if (i > 0) withStyle(SpanStyle(color = C.Faint)) { append("  \u00B7  ") }
                        withStyle(SpanStyle(color = col)) { append(t) }
                    }
                }
                Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (lit) {
            Column(
                Modifier.weight(1f).padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp))
                    .background(C.AccentCard).border(1.dp, C.AccentLine, RoundedCornerShape(20.dp))
                    .tap(onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp),
            ) { body() }
        } else {
            Column(Modifier.weight(1f).tap(onClick = onOpen).padding(top = 12.dp, bottom = 12.dp)) { body() }
        }
    }
}

@Composable
fun SectionHeader(section: Section, collapsed: Boolean, onToggle: (() -> Unit)?, action: Pair<String, () -> Unit>? = null) {
    val color = when (section.tone) {
        Tone.DANGER -> C.Danger
        Tone.ACCENT -> C.Accent
        Tone.DIM -> C.Faint
        Tone.NORMAL -> C.Dim
    }
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onToggle != null) it.tap(onClick = onToggle) else it }
            .heightIn(min = 44.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(40.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            section.title.uppercase(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.weight(1f).padding(horizontal = 12.dp).height(1.dp).background(C.Rail))
        if (action != null) {
            Text(
                action.first.uppercase(),
                color = C.Accent,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.tap(onClick = action.second).padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
        Text(
            if (onToggle != null) (if (collapsed) "${section.tasks.size}  \u25B8" else "${section.tasks.size}  \u25BE") else "${section.tasks.size}",
            color = C.Faint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(C.SurfaceHi).border(1.dp, C.Stroke, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, null, tint = C.Accent, modifier = Modifier.size(28.dp))
        }
        Text(title, color = C.Text, style = MaterialTheme.typography.titleLarge)
        Text(hint, color = C.Dim, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun CollapsibleVisible(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible) { content() }
}

/** A small clock, drawn by hand because the icon set in use does not include one. */
@Composable
fun ClockGlyph(tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = 2.dp.toPx()
        val r = size.minDimension / 2 - w / 2
        drawCircle(tint, radius = r, style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        val c = center
        drawLine(tint, c, Offset(c.x, c.y - r * 0.55f), w, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(tint, c, Offset(c.x + r * 0.42f, c.y + r * 0.2f), w, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/** One box of the swipe actions: icon over a one-line label, centred in a fixed-width tinted cell. */
@Composable
private fun ActionCell(
    width: androidx.compose.ui.unit.Dp,
    bg: Color,
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        Modifier.width(width).fillMaxHeight().background(bg).clipToBounds().tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            icon()
            Text(label, color = labelColor, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
        }
    }
}
