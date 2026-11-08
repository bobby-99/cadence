package com.bobby.cadence.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import com.bobby.cadence.data.CalendarBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.Task
import com.bobby.cadence.data.date
import com.bobby.cadence.data.time
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    tasks: List<Task>,
    today: LocalDate,
    weekStartMonday: Boolean,
    is24: Boolean,
    selected: LocalDate,
    showDeviceEvents: Boolean,
    hiddenCalendars: Set<Long>,
    onSelect: (LocalDate) -> Unit,
    onClose: () -> Unit,
    onAddForDay: (LocalDate) -> Unit,
    onShowInList: (LocalDate) -> Unit,
    onToggle: (Task) -> Unit,
    onOpen: (Task) -> Unit,
) {
    val ctx = LocalContext.current
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    var forward by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var monthView by remember { mutableStateOf(true) }
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    fun goto(m: YearMonth) {
        forward = m.isAfter(month)
        month = m
    }

    val first = month.atDay(1)
    val lead = if (weekStartMonday) first.dayOfWeek.value - 1 else first.dayOfWeek.value % 7
    val gridStart = first.minusDays(lead.toLong())
    val gridEnd = gridStart.plusDays(41)
    val marks = remember(tasks, month) { TaskLogic.marks(tasks, gridStart, gridEnd) }

    // events from the phone calendar (read only); events Cadence wrote itself are hidden so nothing shows twice
    val hideIds = remember(tasks) { tasks.mapNotNull { it.calendarEventId }.toSet() }
    var device by remember { mutableStateOf<Map<LocalDate, List<CalendarBridge.DeviceEvent>>>(emptyMap()) }
    LaunchedEffect(month, showDeviceEvents, hideIds, hiddenCalendars) {
        device = if (showDeviceEvents && CalendarBridge.canRead(ctx)) {
            withContext(Dispatchers.IO) { CalendarBridge.byDay(ctx, gridStart, gridEnd, hideIds, hiddenCalendars) }
        } else emptyMap()
    }

    val dayTasks = remember(tasks, selected) {
        tasks.filter { it.date == selected }.sortedWith(
            compareBy<Task> { it.completed }.thenBy { it.dueTime ?: -1 }.thenByDescending { it.priority },
        )
    }
    val projected = remember(tasks, selected) { TaskLogic.projectedOn(tasks, selected) }
    val dayEvents = device[selected].orEmpty()

    // one timeline: all-day first, then by time
    var nowMin by remember { mutableStateOf(java.time.LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            kotlinx.coroutines.delay(30_000)
        }
    }
    val rows = remember(dayTasks, projected, dayEvents, selected == today, nowMin) {
        val zone = ZoneId.systemDefault()
        val out = ArrayList<TLRow>()
        dayTasks.forEach { out += TLRow(it.dueTime ?: -1, 0, task = it) }
        projected.forEach { out += TLRow(it.dueTime ?: -1, 1, task = it, faded = true) }
        dayEvents.forEach { e ->
            val m = if (e.allDay) -1 else Instant.ofEpochMilli(e.begin).atZone(zone).let { it.hour * 60 + it.minute }
            out += TLRow(m, 2, event = e)
        }
        if (selected == today) out += TLRow(nowMin, -1, now = true)
        out.sortedWith(compareBy<TLRow> { it.minutes }.thenBy { it.order })
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 22.dp).height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(Icons.Default.Close, "Close calendar", onClose)
            Spacer(Modifier.weight(1f))
            IconTap(Icons.Default.Search, "Search", { searching = !searching; query = "" }, filled = false, tint = if (searching) C.Accent else C.Text)
            if (selected != today || month != YearMonth.from(today)) {
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, C.Stroke, RoundedCornerShape(18.dp))
                        .tap { goto(YearMonth.from(today)); onSelect(today) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Today", color = C.Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(month.year.toString(), color = C.Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    color = C.Text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold,
                )
            }
            Box(
                Modifier.height(44.dp).padding(end = 6.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface)
                    .border(1.dp, C.Stroke, RoundedCornerShape(14.dp)).tap { monthView = !monthView }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (monthView) "Week" else "Month", color = C.Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            NavBtn("‹") { goto(month.minusMonths(1)) }
            Spacer(Modifier.size(6.dp))
            NavBtn("›") { goto(month.plusMonths(1)) }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            val days = if (weekStartMonday) DayOfWeek.entries else listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.dropLast(1)
            days.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(),
                    color = C.Dim, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }
        }

        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(220)) { it * dir / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it * dir / 3 } + fadeOut(tween(120)))
            },
            label = "month",
            modifier = Modifier.padding(horizontal = 22.dp).pointerInput(month) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total > 90f) goto(month.minusMonths(1)) else if (total < -90f) goto(month.plusMonths(1))
                    },
                ) { _, delta -> total += delta }
            },
        ) { m ->
            val mFirst = m.atDay(1)
            val mLead = if (weekStartMonday) mFirst.dayOfWeek.value - 1 else mFirst.dayOfWeek.value % 7
            val start = mFirst.minusDays(mLead.toLong())
            Column(Modifier.animateContentSize(androidx.compose.animation.core.tween(240)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val selWeek = ((selected.toEpochDay() - start.toEpochDay()) / 7).toInt()
                for (week in 0 until 6) {
                    if (!monthView && week != selWeek.coerceIn(0, 5)) continue
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val date = start.plusDays((week * 7 + i).toLong())
                            DayCell(
                                date = date,
                                inMonth = YearMonth.from(date) == m,
                                isToday = date == today,
                                isSelected = date == selected,
                                mark = marks[date],
                                deviceCount = device[date]?.size ?: 0,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(date); if (YearMonth.from(date) != m) goto(YearMonth.from(date)) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 22.dp).fillMaxWidth().height(1.dp).background(C.Stroke))

        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                if (selected == today) "Today" else Fmt.dayLong(selected),
                color = C.Text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            val n = dayTasks.size + projected.size
            Text(
                buildString {
                    append(if (n == 0) "No tasks" else if (n == 1) "1 task" else "$n tasks")
                    if (dayEvents.isNotEmpty()) append(" · ${dayEvents.size} event" + if (dayEvents.size == 1) "" else "s")
                },
                color = C.Dim, style = MaterialTheme.typography.bodySmall,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty()) {
                EmptyState("A free day", "Nothing planned for ${Fmt.date(selected, today).lowercase()}.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 96.dp),
                ) {
                    items(rows.size) { i ->
                        val r = rows[i]
                        if (r.now) {
                            NowRow(is24 = is24, minutes = r.minutes)
                            return@items
                        }
                        TimelineRow(
                            minutes = r.minutes, is24 = is24, last = i == rows.lastIndex,
                            dot = when {
                                r.event != null -> TLDot.RING
                                r.task != null && r.task.completed -> TLDot.DONE
                                r.faded -> TLDot.RING
                                r.minutes >= 0 -> TLDot.FILLED
                                else -> TLDot.RING
                            },
                            highlight = r.task != null && !r.faded && r.minutes >= 0 && !r.task.completed,
                        ) {
                            val t = r.task
                            if (t != null) {
                                Row(
                                    Modifier.fillMaxWidth().tap { onOpen(t) }.padding(bottom = 12.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            t.title,
                                            color = if (t.completed || r.faded) C.Dim else C.Text,
                                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            textDecoration = if (t.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                        )
                                        val dur = t.durationMin
                                        val tm = t.time
                                        if (dur != null) Text(
                                            (if (tm != null) Fmt.time(tm, is24) + " \u2013 " + Fmt.time(tm.plusMinutes(dur.toLong()), is24) + "  \u00B7  " else "") + durText(dur * 60),
                                            color = C.Faint, style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (!r.faded) {
                                        Text(
                                            if (t.completed) "Undo" else "Done",
                                            color = C.Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 12.dp).tap { onToggle(t) }.padding(vertical = 2.dp),
                                        )
                                    } else {
                                        Text("repeats", color = C.Faint, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp))
                                    }
                                }
                            } else {
                                val e = r.event!!
                                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(3.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color).copy(alpha = 0.9f)))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            e.title, color = C.Text, style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        (if (e.allDay) "All day" else
                                            Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).toLocalTime().let { Fmt.time(it, is24) } + " \u2013 " +
                                                Instant.ofEpochMilli(e.end).atZone(ZoneId.systemDefault()).toLocalTime().let { Fmt.time(it, is24) } + "  \u00B7  ") +
                                            e.calendar.ifBlank { "Phone calendar" },
                                        color = C.Faint, style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 11.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(C.Text).tap { onAddForDay(selected) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Add, null, tint = C.Bg, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Add task on " + selected.dayOfMonth + " " + selected.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        color = C.Bg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    // ---- search ----
    LaunchedEffect(searching) {
        if (searching) { kotlinx.coroutines.delay(220); runCatching { searchFocus.requestFocus() } }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = searching,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) +
            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220)) { it / 10 },
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
    ) {
        val q = query.trim().lowercase()
        val hits = remember(q, tasks, device) {
            if (q.length < 1) emptyList() else buildList {
                tasks.filter { it.title.lowercase().contains(q) || it.notes.lowercase().contains(q) }
                    .sortedWith(compareBy({ it.date == null }, { it.date }))
                    .take(60).forEach { add(Hit(it.date, it.title, listOfNotNull(it.time?.let { tm -> Fmt.time(tm, is24) }, if (it.completed) "done" else null).joinToString(" \u00B7 "), it, false)) }
                device.values.flatten().distinctBy { it.id to it.begin }.filter { it.title.lowercase().contains(q) }.take(30).forEach {
                    val d = Instant.ofEpochMilli(it.begin).atZone(if (it.allDay) java.time.ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()
                    add(Hit(d, it.title, it.calendar.ifBlank { "Phone calendar" }, null, true))
                }
            }
        }
        Column(
            Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().padding(top = 52.dp).navigationBarsPadding().padding(horizontal = 22.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.Surface).border(1.dp, C.Stroke, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Search, null, tint = C.Dim, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search tasks and events", color = C.Faint, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = C.Text),
                        cursorBrush = SolidColor(C.Accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Default.Close, "Clear", tint = C.Dim, modifier = Modifier.size(20.dp).clip(CircleShape).tap { query = "" })
            }
            Spacer(Modifier.height(12.dp))
            if (q.isNotEmpty() && hits.isEmpty()) EmptyState("No matches", "Nothing called \"${query.trim()}\".")
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(hits.size) { i ->
                    val h = hits[i]
                    Row(
                        Modifier.fillMaxWidth().tap {
                            val d = h.date
                            if (d != null) { onSelect(d); goto(YearMonth.from(d)); searching = false; query = "" }
                            else h.task?.let { onOpen(it) }
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.width(70.dp)) {
                            Text(h.date?.let { Fmt.date(it, today) } ?: "No date", color = C.Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (h.device) Text("phone", color = C.Faint, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(h.title, color = C.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (h.sub.isNotBlank()) Text(h.sub, color = C.Faint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Hairline()
                }
            }
        }
    }
    }
}

private data class Hit(val date: LocalDate?, val title: String, val sub: String, val task: Task?, val device: Boolean)

@Composable
private fun NowRow(is24: Boolean, minutes: Int) {
    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.time(java.time.LocalTime.of(minutes / 60, minutes % 60), is24), color = C.Accent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(46.dp), textAlign = TextAlign.End)
        Box(Modifier.width(28.dp).height(26.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(C.Accent))
        }
        Box(Modifier.weight(1f).height(1.dp).background(C.Accent.copy(alpha = 0.5f)))
    }
}

private data class TLRow(
    val minutes: Int,
    val order: Int,
    val task: Task? = null,
    val event: CalendarBridge.DeviceEvent? = null,
    val faded: Boolean = false,
    val now: Boolean = false,
)

private enum class TLDot { FILLED, RING, DONE }

@Composable
private fun TimelineRow(minutes: Int, is24: Boolean, last: Boolean, dot: TLDot, highlight: Boolean, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(Modifier.width(46.dp).padding(top = 1.dp), horizontalAlignment = Alignment.End) {
            val hi = if (highlight) C.Accent else C.Dim
            if (minutes < 0) {
                Text("All", color = C.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("day", color = C.Dim, style = MaterialTheme.typography.labelSmall)
            } else {
                val parts = Fmt.minutes(minutes, is24).split(" ")
                Text(parts[0], color = hi, style = MaterialTheme.typography.labelMedium, fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Bold)
                if (parts.size > 1) Text(parts[1], color = hi, style = MaterialTheme.typography.labelSmall)
            }
        }
        Box(Modifier.width(28.dp).fillMaxHeight()) {
            if (!last) Box(Modifier.padding(start = 13.dp, top = 6.dp).width(1.dp).fillMaxHeight().background(C.Rail))
            when (dot) {
                TLDot.FILLED -> Box(Modifier.padding(start = 8.dp, top = 4.dp).size(11.dp).clip(CircleShape).background(C.Accent))
                TLDot.DONE -> Box(Modifier.padding(start = 8.dp, top = 4.dp).size(11.dp).clip(CircleShape).background(C.Done))
                TLDot.RING -> Box(Modifier.padding(start = 8.dp, top = 4.dp).size(11.dp).clip(CircleShape).background(C.Bg).border(2.dp, C.Faint, CircleShape))
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun NavBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface).border(1.dp, C.Stroke, RoundedCornerShape(14.dp)).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = C.Text, style = MaterialTheme.typography.headlineSmall) }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    mark: DayMark?,
    deviceCount: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.height(48.dp).tap(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val load = ((mark?.open ?: 0) + (mark?.done ?: 0) + (mark?.projected ?: 0) + deviceCount).coerceAtMost(5)
        val target = when {
            isSelected -> C.Accent
            inMonth && load > 0 -> C.Accent.copy(alpha = 0.04f + 0.035f * load)
            else -> Color.Transparent
        }
        val bg by androidx.compose.animation.animateColorAsState(target, androidx.compose.animation.core.tween(180), label = "cellbg")
        val shape = RoundedCornerShape(13.dp)
        Box(
            Modifier.size(36.dp).clip(shape).background(bg)
                .then(if (isToday && !isSelected) Modifier.border(1.dp, C.Accent, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                color = when {
                    isSelected -> C.AccentOn
                    !inMonth -> C.Faint.copy(alpha = 0.6f)
                    isToday -> C.Accent
                    else -> C.Text
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (inMonth) {
                if (mark != null) {
                    if (mark.open > 0) Dot(if (mark.high) C.Accent else C.Dim, 5.dp)
                    if (mark.done > 0 && mark.open == 0) Dot(C.Done, 5.dp)
                    if (mark.projected > 0) Box(Modifier.size(5.dp).clip(CircleShape).border(1.dp, C.Accent, CircleShape))
                }
                if (deviceCount > 0) Dot(C.Faint, 4.dp, alpha = 0.7f)
            }
        }
    }
}
