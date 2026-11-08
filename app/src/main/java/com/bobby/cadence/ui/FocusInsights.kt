package com.bobby.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobby.cadence.data.AppSettings
import com.bobby.cadence.data.FocusSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val UNTAGGED = "Untagged"

private fun dayOf(s: FocusSession, zone: ZoneId): LocalDate = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusInsights(
    sessions: List<FocusSession>,
    settings: AppSettings,
    today: LocalDate,
    onBack: () -> Unit,
    onDelete: (FocusSession) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var tagFilter by remember { mutableStateOf<String?>(null) } // null = all
    var range by remember { mutableStateOf(0) } // 0 week, 1 month, 2 all time
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    var deleting by remember { mutableStateOf<FocusSession?>(null) }

    val tags = remember(sessions, settings.focusTags) {
        val used = sessions.map { it.tag.ifBlank { UNTAGGED } }.toSet()
        (settings.focusTags + used.filter { it !in settings.focusTags }).distinct()
    }
    val filtered = remember(sessions, tagFilter) {
        if (tagFilter == null) sessions else sessions.filter { it.tag.ifBlank { UNTAGGED } == tagFilter }
    }
    val perDay = remember(filtered) { filtered.groupBy { dayOf(it, zone) }.mapValues { e -> e.value.sumOf { it.seconds } } }

    val rangeStart: LocalDate? = when (range) {
        0 -> today.minusDays(6)
        1 -> today.minusDays(29)
        else -> null
    }
    val inRange = remember(filtered, range) { filtered.filter { rangeStart == null || !dayOf(it, zone).isBefore(rangeStart) } }
    val totalSec = inRange.sumOf { it.seconds }
    val activeDays = inRange.map { dayOf(it, zone) }.toSet().size
    val spanDays = when {
        rangeStart != null -> if (range == 0) 7 else 30
        else -> (inRange.minOfOrNull { dayOf(it, zone) }?.let { today.toEpochDay() - it.toEpochDay() + 1 } ?: 1L).toInt().coerceAtLeast(1)
    }
    val streak = remember(perDay, today) {
        var d = if (perDay.containsKey(today)) today else today.minusDays(1)
        var n = 0
        while (perDay.containsKey(d)) { n++; d = d.minusDays(1) }
        n
    }

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 20.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.Default.Close, "Close insights", onBack, filled = false)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text("Insights", color = C.Text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)

            // filters
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("7 days", "30 days", "All time").forEachIndexed { i, l -> Pill(l, selected = range == i) { range = i } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("All tags", selected = tagFilter == null) { tagFilter = null }
                    tags.forEach { t -> Pill(t, selected = tagFilter == t) { tagFilter = if (tagFilter == t) null else t } }
                }
            }

            // tiles
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Focus time", if (totalSec == 0) "0m" else durText(totalSec), Modifier.weight(1f))
                    Tile("Sessions", inRange.size.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Daily average", durText(totalSec / spanDays), Modifier.weight(1f))
                    Tile("Streak", if (streak == 1) "1 day" else "$streak days", Modifier.weight(1f), accent = streak > 0)
                }
            }

            // heatmap
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Last 18 weeks", color = C.Accent)
                Heatmap(perDay, today, settings.weekStartMonday, if (settings.dailyGoalMin > 0) settings.dailyGoalMin else 120, picked) {
                    picked = if (picked == it) null else it
                }
                val p = picked
                val pickedText = if (p == null) "Tap a day to see its total." else {
                    val sec = perDay[p] ?: 0
                    val n = filtered.count { dayOf(it, zone) == p }
                    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(p) + " · " +
                        (if (sec == 0) "no focus" else durText(sec) + " · $n session" + if (n == 1) "" else "s")
                }
                Text(pickedText, color = C.Dim, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Less", color = C.Faint, style = MaterialTheme.typography.labelMedium)
                    for (lv in 0..4) Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(levelColor(lv)))
                    Text("More", color = C.Faint, style = MaterialTheme.typography.labelMedium)
                }
            }

            // bars
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(if (range == 0) "This week" else "Last 30 days", color = C.Accent)
                val days = if (range == 0) 7 else 30
                Bars(perDay, today, days, if (days == 7) 120 else 70)
            }

            // by tag
            val byTag = remember(inRange) {
                inRange.groupBy { it.tag.ifBlank { UNTAGGED } }.mapValues { e -> e.value.sumOf { it.seconds } }.entries.sortedByDescending { it.value }
            }
            if (byTag.isNotEmpty() && tagFilter == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("By tag", color = C.Accent)
                    val max = byTag.first().value.coerceAtLeast(1)
                    byTag.forEach { (t, sec) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row {
                                Text(t, color = C.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(durText(sec) + "  ·  " + (sec * 100 / totalSec.coerceAtLeast(1)) + "%", color = C.Dim, style = MaterialTheme.typography.bodySmall)
                            }
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(C.SurfaceHi)) {
                                Box(Modifier.fillMaxWidth(sec.toFloat() / max).height(8.dp).clip(CircleShape).background(C.Accent))
                            }
                        }
                    }
                }
            }

            // recent
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Recent sessions", color = C.Accent)
                if (filtered.isEmpty()) Text("Nothing here yet. Finish a focus round and it shows up.", color = C.Dim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                filtered.take(30).forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.completed || s.mode == 1) C.Accent else C.Faint))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.taskTitle.ifBlank { s.tag.ifBlank { if (s.mode == 1) "Freestyle" else "Focus" } },
                                color = C.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val at = Instant.ofEpochMilli(s.startedAt).atZone(zone)
                            Text(
                                DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.getDefault()).format(at) +
                                    (if (s.tag.isNotBlank() && s.taskTitle.isNotBlank()) " · ${s.tag}" else "") +
                                    (if (!s.completed && s.mode == 0) " · ended early" else ""),
                                color = C.Faint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(durText(s.seconds), color = C.Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Box(Modifier.size(32.dp).clip(CircleShape).tap { deleting = s }, contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(Icons.Default.Close, "Delete session", tint = C.Faint, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val del = deleting
    if (del != null) AlertDialog(
        onDismissRequest = { deleting = null },
        containerColor = C.SurfaceHi,
        title = { Text("Delete this session?", color = C.Text) },
        text = { Text("It will be removed from your insights.", color = C.Dim) },
        confirmButton = { TextButton(onClick = { deleting = null; onDelete(del) }) { Text("Delete", color = C.Danger) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = C.Dim) } },
    )
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier, accent: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(C.Surface).border(1.dp, C.Stroke, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), color = C.Faint, style = MaterialTheme.typography.labelSmall)
        Text(value, color = if (accent) C.Accent else C.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    }
}

private fun levelColor(level: Int): Color = when (level) {
    0 -> Color(0xFF1C1B18)
    1 -> Color(0xFF4A3A1C)
    2 -> Color(0xFF8A6A2C)
    3 -> Color(0xFFC98F38)
    else -> Color(0xFFF2B04A)
}

private fun levelFor(sec: Int, goalMin: Int): Int {
    if (sec <= 0) return 0
    val f = sec / 60f / goalMin
    return when {
        f < 0.25f -> 1
        f < 0.6f -> 2
        f < 1f -> 3
        else -> 4
    }
}

@Composable
private fun Heatmap(perDay: Map<LocalDate, Int>, today: LocalDate, mondayFirst: Boolean, goalMin: Int, picked: LocalDate?, onPick: (LocalDate) -> Unit) {
    val weeks = 18
    val gap = 3.dp
    val first = if (mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    // the column that holds today, then walk back
    var weekStart = today
    while (weekStart.dayOfWeek != first) weekStart = weekStart.minusDays(1)
    val gridStart = weekStart.minusWeeks((weeks - 1).toLong())
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelW = 22.dp
        val cell: Dp = ((maxWidth - labelW - gap * weeks) / weeks).coerceAtLeast(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.width(labelW), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0 until 7) {
                    Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                        if (r % 2 == 0) Text(
                            first.plus(r.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            color = C.Faint, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        )
                    }
                }
            }
            for (w in 0 until weeks) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (r in 0 until 7) {
                        val d = gridStart.plusDays((w * 7 + r).toLong())
                        if (d.isAfter(today)) {
                            Box(Modifier.size(cell))
                        } else {
                            Box(
                                Modifier.size(cell).clip(RoundedCornerShape(3.dp)).background(levelColor(levelFor(perDay[d] ?: 0, goalMin)))
                                    .then(if (d == picked) Modifier.border(1.5.dp, C.Text, RoundedCornerShape(3.dp)) else Modifier)
                                    .tap { onPick(d) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bars(perDay: Map<LocalDate, Int>, today: LocalDate, days: Int, heightDp: Int) {
    val list = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val max = list.maxOf { perDay[it] ?: 0 }.coerceAtLeast(60)
    Row(Modifier.fillMaxWidth().height((heightDp + 26).dp), horizontalArrangement = Arrangement.spacedBy(if (days == 7) 10.dp else 3.dp), verticalAlignment = Alignment.Bottom) {
        list.forEach { d ->
            val sec = perDay[d] ?: 0
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (days == 7 && sec > 0) Text(durText(sec), color = C.Dim, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier.fillMaxWidth().height(((heightDp - 16) * sec.toFloat() / max).coerceAtLeast(if (sec > 0) 4f else 2f).dp)
                        .clip(RoundedCornerShape(if (days == 7) 8.dp else 3.dp))
                        .background(if (sec > 0) (if (d == today) C.Accent else C.Accent.copy(alpha = 0.55f)) else C.SurfaceHi),
                )
                Spacer(Modifier.height(6.dp))
                if (days == 7) Text(
                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    color = if (d == today) C.Accent else C.Faint, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                ) else Spacer(Modifier.height(16.dp))
            }
        }
    }
}
