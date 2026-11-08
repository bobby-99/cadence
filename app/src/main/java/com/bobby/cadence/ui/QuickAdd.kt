package com.bobby.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bobby.cadence.data.AppSettings
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.describe
import com.bobby.cadence.nlp.ParsedTask
import com.bobby.cadence.nlp.TaskParser
import com.bobby.cadence.nlp.Token
import com.bobby.cadence.nlp.TokenKind
import com.bobby.cadence.Draft
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Colours the recognised phrases while you type, so you can see what the parser understood. */
private class ParseHighlight(private val tokens: List<Token>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val b = AnnotatedString.Builder(text)
        for (t in tokens) {
            if (t.start < 0 || t.end > text.length || t.start >= t.end) continue
            val color = when (t.kind) {
                TokenKind.DATE, TokenKind.TIME -> C.Accent
                TokenKind.REPEAT -> C.Done
                TokenKind.PRIORITY -> C.Medium
                TokenKind.REMINDER -> C.Low
                TokenKind.NOTE -> C.Faint
            }
            b.addStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium), t.start, t.end)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

fun draftFromParsed(p: ParsedTask, s: AppSettings, rawTitle: String? = null): Draft {
    val reminders = p.reminders ?: if (p.date != null && s.defaultReminder >= 0) listOf(s.defaultReminder) else emptyList()
    return Draft(
        title = rawTitle ?: p.title,
        notes = p.notes ?: "",
        date = p.date,
        time = p.time,
        priority = p.priority ?: 0,
        reminders = reminders,
        rule = p.recurrence,
        durationMin = p.durationMin,
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    defaultDate: LocalDate?,
    initialText: String = "",
    settings: AppSettings,
    is24: Boolean,
    today: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (Draft) -> Unit,
    onMore: (Draft) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parser = remember { TaskParser() }
    var value by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    val focus = remember { FocusRequester() }

    val parsed: ParsedTask? = remember(value.text, defaultDate, settings.smartParsing) {
        if (!settings.smartParsing) null else parser.parse(value.text, defaultDate)
    }
    val draft: Draft = remember(parsed, value.text, defaultDate, settings) {
        if (parsed != null) draftFromParsed(parsed, settings)
        else draftFromParsed(
            ParsedTask(value.text.trim(), null, defaultDate, null, null, null, null, emptyList()), settings,
        )
    }
    val canAdd = draft.title.isNotBlank()

    // let the sheet finish sliding up first; then the keyboard comes in underneath and lifts it
    LaunchedEffect(Unit) {
        withTimeoutOrNull(900) { snapshotFlow { sheet.currentValue }.first { it == SheetValue.Expanded } }
        delay(80)
        runCatching { focus.requestFocus() }
    }

    // dictation: the system speech dialog returns text that is appended to the field
    val qaCtx = androidx.compose.ui.platform.LocalContext.current
    val speech = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            val said = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) {
                val t = value.text.trimEnd()
                val n = if (t.isEmpty()) said.trim() else "$t ${said.trim()}"
                value = TextFieldValue(n, TextRange(n.length))
            }
        }
        runCatching { focus.requestFocus() }
    }
    fun dictate() {
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say your task")
        try {
            speech.launch(i)
        } catch (e: Exception) {
            android.widget.Toast.makeText(qaCtx, "Speech input is not available on this phone", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun append(word: String) {
        val t = value.text.trimEnd()
        val n = if (t.isEmpty()) word else "$t $word"
        value = TextFieldValue(n, TextRange(n.length))
    }

    fun submit() {
        if (canAdd) onAdd(draft)
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
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New task", color = C.Dim, style = MaterialTheme.typography.labelMedium)

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).padding(top = 6.dp)) {
                    if (value.text.isEmpty()) {
                        Text(
                            "Call mom tomorrow 6pm !!",
                            color = C.Faint,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = C.Text, fontWeight = FontWeight.Normal),
                        cursorBrush = SolidColor(C.Accent),
                        visualTransformation = ParseHighlight(parsed?.tokens ?: emptyList()),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        maxLines = 4,
                    )
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(C.SurfaceHi).border(1.dp, C.Stroke, CircleShape).tap { dictate() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MicIcon, "Speak the task", tint = C.Accent, modifier = Modifier.size(22.dp))
                }
            }

            // what the parser understood
            val chips = ArrayList<Pair<String, androidx.compose.ui.graphics.Color>>()
            draft.date?.let { chips += Fmt.date(it, today) to C.Accent }
            draft.time?.let { chips += Fmt.time(it, is24) to C.Accent }
            draft.rule?.let { chips += "↻ " + it.describe() to C.Done }
            if (draft.priority > 0) chips += priorityLabel(draft.priority) + " priority" to priorityColor(draft.priority)
            if (draft.date != null && parsed?.reminders != null) {
                if (draft.reminders.isEmpty()) chips += "No reminder" to C.Dim
                else draft.reminders.forEach { chips += Fmt.offsetLabel(it) to C.Low }
            }
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { (t, c) -> Tag(t, c) }
                }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Today", onClick = { append("today") })
                Pill("Tomorrow", onClick = { append("tomorrow") })
                Pill("Next week", onClick = { append("next week") })
                Pill("Evening", onClick = { append("this evening") })
                Pill("!!!", color = C.High, onClick = { append("!!!") })
                Pill("Daily", color = C.Done, onClick = { append("every day") })
                Pill("Weekly", color = C.Done, onClick = { append("every week") })
                Pill("Remind 10m", color = C.Low, onClick = { append("remind 10 min before") })
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("More options", onClick = { if (canAdd) onMore(draft) else onMore(draft.copy(title = "")) })
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (canAdd) C.Accent else C.SurfaceTop)
                        .tap(enabled = canAdd) { submit() }
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Add, null, tint = if (canAdd) C.AccentOn else C.Faint, modifier = Modifier.size(20.dp))
                        Text("Add task", color = if (canAdd) C.AccentOn else C.Faint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
