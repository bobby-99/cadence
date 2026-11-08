package com.bobby.cadence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.RecurrenceCalc
import com.bobby.cadence.data.RepeatBasis
import com.bobby.cadence.data.Task
import com.bobby.cadence.data.date
import com.bobby.cadence.data.describe
import com.bobby.cadence.data.rule
import com.bobby.cadence.data.time
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun OccurrenceRow(top: String, bottom: String, title: String, current: Boolean, last: Boolean, fade: Float) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).alpha(fade)) {
        Column(Modifier.width(52.dp).padding(top = 1.dp), horizontalAlignment = Alignment.End) {
            Text(top, color = if (current) C.Accent else C.Dim, maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp))
            Text(bottom, color = if (current) C.Accent else C.Faint, maxLines = 1, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp))
        }
        Box(
            Modifier.width(40.dp).fillMaxHeight().drawBehind {
                if (!last) drawLine(C.Rail, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
            },
            contentAlignment = Alignment.TopCenter,
        ) {
            Canvas(Modifier.padding(top = 0.dp).size(22.dp)) {
                val r = 6.dp.toPx()
                if (current) {
                    drawCircle(C.Accent.copy(alpha = 0.18f), radius = r + 5.dp.toPx())
                    drawCircle(C.Accent, radius = r + 1.dp.toPx())
                } else {
                    drawCircle(
                        C.Accent, radius = r - 1.dp.toPx(),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
            Text(title, color = if (current) C.Text else C.Dim, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What opens when you tap a repeating task: the rule, the next dates, and what each button does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringSheet(
    task: Task,
    today: LocalDate,
    is24: Boolean,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onEditRepeat: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rule = task.rule
    val date = task.date
    val time = task.time
    val timeText = time?.let { Fmt.time(it, is24) }
    val wd = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val dm = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    val upcoming = remember(task) {
        if (rule == null || date == null || rule.basis == RepeatBasis.FROM_COMPLETION) emptyList()
        else RecurrenceCalc.project(date, rule, task.occurrence, date.plusDays(1), date.plusDays(400), limit = 60).take(3)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = C.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(C.SurfaceTop))
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 18.dp)) {
            val eyebrow = listOfNotNull(date?.let { Fmt.dayShort(it).replace(",", "").uppercase() }, timeText?.uppercase()).joinToString("  ·  ")
            if (eyebrow.isNotEmpty()) Text(eyebrow, color = C.Accent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 14.dp))
            Text(
                task.title, color = C.Text, style = MaterialTheme.typography.headlineMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
            )

            if (rule != null) {
                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.AccentCard)
                        .border(1.dp, C.AccentLine, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("↻", color = C.Accent, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f)) {
                        Text(rule.describe(), color = C.Text, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (rule.basis == RepeatBasis.FROM_COMPLETION) "Next one counted from when you finish" else "Next one counted from the due date",
                            color = C.Dim, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (date != null) {
                Text("COMING UP", color = C.Dim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 22.dp, bottom = 12.dp))
                OccurrenceRow(wd.format(date), dm.format(date), "This one" + (timeText?.let { " · $it" } ?: ""), current = true, last = upcoming.isEmpty(), fade = 1f)
                upcoming.forEachIndexed { i, d ->
                    OccurrenceRow(wd.format(d), dm.format(d), timeText ?: "All day", current = false, last = i == upcoming.lastIndex, fade = 1f - 0.18f * (i + 1))
                }
            }

            Text(
                "Only this one exists right now. Complete it and the next one is created for you.",
                color = C.Dim, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(18.dp)).background(C.Accent).tap { onComplete() },
                    contentAlignment = Alignment.Center,
                ) { Text("Complete", color = C.AccentOn, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)) }
                Box(
                    Modifier.width(112.dp).height(54.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, C.Stroke, RoundedCornerShape(18.dp)).tap { onSkip() },
                    contentAlignment = Alignment.Center,
                ) { Text("Skip", color = C.Text, style = MaterialTheme.typography.titleSmall) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Box(Modifier.height(44.dp).tap { onEditRepeat() }, contentAlignment = Alignment.CenterStart) {
                    Text("Edit task or repeat", color = C.Text, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.height(44.dp).tap { onDelete() }, contentAlignment = Alignment.CenterEnd) {
                    Text("Delete…", color = C.Danger, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
