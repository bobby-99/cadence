package com.bobby.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bobby.cadence.alarm.SnoozeTargets
import com.bobby.cadence.data.Fmt
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

private data class Option(val title: String, val millis: Long)

private fun minutesText(m: Int): String = when {
    m % 1440 == 0 -> "${m / 1440} day" + if (m / 1440 == 1) "" else "s"
    m % 60 == 0 -> "${m / 60} hour" + if (m / 60 == 1) "" else "s"
    else -> "$m min"
}

/**
 * Snooze a task from the list. Pick a quick option, one of your recent custom lengths, type your own,
 * or choose an exact date and time. [onSnooze] gets the wake-up time and, for typed lengths, the minutes
 * so they can be remembered.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SnoozeSheet(
    title: String,
    is24: Boolean,
    today: LocalDate,
    recent: List<Int>,
    onDismiss: () -> Unit,
    onSnooze: (millis: Long, customMinutes: Int?) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = ZoneId.systemDefault()
    val now = remember { ZonedDateTime.now(zone) }

    fun label(millis: Long): String {
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        return Fmt.date(dt.toLocalDate(), today) + " · " + Fmt.time(dt.toLocalTime(), is24)
    }

    val options = remember {
        buildList {
            add(Option("10 minutes", now.plusMinutes(10).toInstant().toEpochMilli()))
            add(Option("30 minutes", now.plusMinutes(30).toInstant().toEpochMilli()))
            add(Option("1 hour", now.plusHours(1).toInstant().toEpochMilli()))
            add(Option("3 hours", now.plusHours(3).toInstant().toEpochMilli()))
            if (SnoozeTargets.eveningAvailable(now)) add(Option("This evening", SnoozeTargets.eveningMillis(now)))
            add(Option("Tomorrow morning", SnoozeTargets.tomorrowMorningMillis(now)))
            add(
                Option(
                    "Tomorrow evening",
                    now.toLocalDate().plusDays(1).atTime(SnoozeTargets.EVENING_MIN / 60, 0).atZone(zone).toInstant().toEpochMilli(),
                ),
            )
            add(
                Option(
                    "Next week",
                    now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                        .atTime(SnoozeTargets.MORNING_MIN / 60, 0).atZone(zone).toInstant().toEpochMilli(),
                ),
            )
        }
    }

    var text by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(0) } // 0 min, 1 hour, 2 day
    var pickDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val factor = intArrayOf(1, 60, 1440)[unit]
    val n = text.toIntOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = C.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(C.SurfaceTop))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("SNOOZE", color = C.Accent, style = MaterialTheme.typography.labelMedium)
                Text(title, color = C.Text, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.SurfaceHi).border(1.dp, C.Stroke, RoundedCornerShape(20.dp)),
            ) {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).tap { onSnooze(o.millis, null) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(o.title, color = C.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(label(o.millis), color = C.Accent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    if (i < options.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Stroke))
                }
            }

            if (recent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("YOUR RECENT", color = C.Faint, style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { m ->
                            Pill(minutesText(m)) { onSnooze(System.currentTimeMillis() + m * 60_000L, m) }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CUSTOM", color = C.Faint, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(C.SurfaceHi)
                            .border(1.dp, C.Stroke, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) Text("Amount", color = C.Faint)
                        BasicTextField(
                            value = text,
                            onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) text = v },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = C.Text),
                            cursorBrush = SolidColor(C.Accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    listOf("min", "hours", "days").forEachIndexed { i, l -> Pill(l, selected = unit == i) { unit = i } }
                }
                val ok = n != null && n > 0
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (ok) C.Accent else C.SurfaceTop)
                        .tap(enabled = ok) {
                            val m = n!! * factor
                            onSnooze(System.currentTimeMillis() + m * 60_000L, m)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (ok) "Snooze for ${minutesText(n!! * factor)}" else "Snooze",
                        color = if (ok) C.AccentOn else C.Faint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, C.Stroke, RoundedCornerShape(16.dp))
                        .tap { pickDate = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Pick a date and time", color = C.Text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (pickDate) DatePickDialog(today, onDismiss = { pickDate = false }) { d -> pickDate = false; pickedDate = d }
    val d = pickedDate
    if (d != null) TimePickDialog(LocalTime.of(SnoozeTargets.MORNING_MIN / 60, 0), is24, onDismiss = { pickedDate = null }) { t ->
        val at = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
        pickedDate = null
        onSnooze(at.coerceAtLeast(System.currentTimeMillis() + 60_000L), null)
    }
}
