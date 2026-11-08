package com.bobby.cadence.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.describe
import com.bobby.cadence.nlp.ParsedTask
import com.bobby.cadence.nlp.TaskParser
import com.bobby.cadence.nlp.TokenKind
import java.time.LocalDate

/** The text with every phrase the parser recognised coloured by what it means. */
fun highlightParsed(text: String, parsed: ParsedTask?): AnnotatedString {
    val b = AnnotatedString.Builder(text)
    parsed?.tokens?.forEach { t ->
        if (t.start < 0 || t.end > text.length || t.start >= t.end) return@forEach
        val color = when (t.kind) {
            TokenKind.DATE, TokenKind.TIME -> C.Accent
            TokenKind.REPEAT -> C.Done
            TokenKind.PRIORITY -> C.High
            TokenKind.REMINDER -> C.Low
            TokenKind.NOTE -> C.Faint
        }
        b.addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), t.start, t.end)
    }
    return b.toAnnotatedString()
}

/** What the parser understood, as short labelled chips. */
fun parsedChips(p: ParsedTask, today: LocalDate, is24: Boolean): List<Pair<String, Color>> {
    val out = ArrayList<Pair<String, Color>>()
    p.date?.let { out += Fmt.date(it, today) to C.Accent }
    p.time?.let { out += Fmt.time(it, is24) to C.Accent }
    p.recurrence?.let { out += "↻ " + it.describe() to C.Done }
    p.priority?.takeIf { it > 0 }?.let { out += priorityLabel(it) + " priority" to priorityColor(it) }
    p.reminders?.let { r -> if (r.isEmpty()) out += "No reminder" to C.Dim else r.forEach { out += Fmt.offsetLabel(it) to C.Low } }
    p.durationMin?.let { m -> out += (if (m % 60 == 0) "${m / 60} h" else if (m > 60) "${m / 60} h ${m % 60} min" else "$m min") + " long" to C.Medium }
    p.notes?.takeIf { it.isNotBlank() }?.let { out += "Note: " + it.take(28) to C.Dim }
    return out
}

/** A sentence, highlighted, with the chips of what was understood underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParseDemo(text: String, today: LocalDate, is24: Boolean, big: Boolean = false) {
    val parser = remember { TaskParser() }
    val parsed = remember(text) { parser.parse(text, null) }
    val chips = remember(parsed, today, is24) { parsedChips(parsed, today, is24) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            highlightParsed(text, parsed), color = C.Text,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
        )
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (t, c) -> Tag(t, c) }
            }
        }
    }
}

/**
 * The Cadence mark: a small notepad with a checked box. Same drawing as the launcher icon,
 * so the About page and the app icon always match.
 */
@Composable
fun AppMark(size: Dp, tint: Color = C.Accent, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        // the launcher icon is drawn on a 108-unit grid; the mark sits in the middle 44 x 48 of it
        val u = this.size.width / 54f
        fun o(x: Float, y: Float) = Offset((x - 27f) * u + this.size.width / 2f, (y - 27f) * u + this.size.height / 2f)
        val round = Stroke(width = 3.0f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // sheet
        drawRoundRect(tint, o(8f, 8f), Size(38f * u, 40f * u), CornerRadius(7f * u), style = round)
        // binding tabs
        for (x in listOf(18f, 27f, 36f)) drawLine(tint, o(x, 4f), o(x, 12f), 3.0f * u, StrokeCap.Round)
        // checkbox
        drawRoundRect(tint, o(14f, 21f), Size(10f * u, 10f * u), CornerRadius(2.5f * u), style = Stroke(2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // tick
        val tick = androidx.compose.ui.graphics.Path().apply {
            val a = o(16.6f, 26.2f); val b = o(18.6f, 28f); val c = o(21.6f, 24.2f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y)
        }
        drawPath(tick, tint, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // text lines
        drawLine(tint, o(29f, 26f), o(39f, 26f), 2.6f * u, StrokeCap.Round)
        drawLine(tint, o(14f, 39f), o(39f, 39f), 2.6f * u, StrokeCap.Round)
    }
}

/** The mark on a glowing rounded tile, used as the hero on the About page and in the tutorial. */
@Composable
fun AppHero(size: Dp = 104.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(C.AccentCard),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        AppMark(size * 0.62f)
    }
}
