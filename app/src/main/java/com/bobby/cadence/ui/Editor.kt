package com.bobby.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bobby.cadence.Draft
import com.bobby.cadence.data.EndType
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.nlp.TaskParser
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import com.bobby.cadence.data.MonthMode
import com.bobby.cadence.data.Recurrence
import com.bobby.cadence.data.RecurrenceCalc
import com.bobby.cadence.data.RepeatBasis
import com.bobby.cadence.data.RepeatUnit
import com.bobby.cadence.data.describe
import com.bobby.cadence.data.ordinal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

private fun millisToDate(ms: Long): LocalDate = java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
private fun dateToMillis(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = dateToMillis(initial ?: LocalDate.now()))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(millisToDate(it)) }
                onDismiss()
            }) { Text("OK", color = C.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Dim) } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime?, is24: Boolean, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val base = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(base.hour, base.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.SurfaceHi,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK", color = C.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Dim) } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), color = C.Faint, style = MaterialTheme.typography.labelSmall)
        content()
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(C.SurfaceHi).border(1.dp, C.Stroke, CircleShape)
                .tap(enabled = value > min) { onChange(value - 1) },
            contentAlignment = Alignment.Center,
        ) { Text("−", color = if (value > min) C.Text else C.Faint, style = MaterialTheme.typography.titleLarge) }
        Text(
            value.toString(),
            color = C.Text,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(C.SurfaceHi).border(1.dp, C.Stroke, CircleShape)
                .tap(enabled = value < max) { onChange(value + 1) },
            contentAlignment = Alignment.Center,
        ) { Text("+", color = if (value < max) C.Text else C.Faint, style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun SoftSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = C.AccentOn, checkedTrackColor = C.Accent,
            uncheckedThumbColor = C.Dim, uncheckedTrackColor = C.SurfaceHi, uncheckedBorderColor = C.Stroke,
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Repeat builder
// ---------------------------------------------------------------------------------------------

private val WEEK_ORDER = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatBuilder(rule: Recurrence, anchor: LocalDate, onChange: (Recurrence) -> Unit) {
    var pickEnd by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // quick presets
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val wk = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
            Pill("Daily", selected = rule.unit == RepeatUnit.DAY && rule.interval == 1) {
                onChange(rule.copy(unit = RepeatUnit.DAY, interval = 1, weekDays = emptySet()))
            }
            Pill("Weekdays", selected = rule.unit == RepeatUnit.WEEK && rule.weekDays == wk && rule.interval == 1) {
                onChange(rule.copy(unit = RepeatUnit.WEEK, interval = 1, weekDays = wk))
            }
            Pill("Weekly", selected = rule.unit == RepeatUnit.WEEK && rule.weekDays.size <= 1 && rule.interval == 1) {
                onChange(rule.copy(unit = RepeatUnit.WEEK, interval = 1, weekDays = setOf(anchor.dayOfWeek)))
            }
            Pill("Monthly", selected = rule.unit == RepeatUnit.MONTH && rule.interval == 1) {
                onChange(rule.copy(unit = RepeatUnit.MONTH, interval = 1, monthMode = MonthMode.DAY_OF_MONTH, dayOfMonth = anchor.dayOfMonth))
            }
            Pill("Yearly", selected = rule.unit == RepeatUnit.YEAR && rule.interval == 1) {
                onChange(rule.copy(unit = RepeatUnit.YEAR, interval = 1))
            }
        }

        Field("Every") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper(rule.interval, 1, 99) { onChange(rule.copy(interval = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(RepeatUnit.DAY to "day", RepeatUnit.WEEK to "week", RepeatUnit.MONTH to "month", RepeatUnit.YEAR to "year").forEach { (u, n) ->
                        Pill(if (rule.interval > 1) n + "s" else n, selected = rule.unit == u) {
                            val next = rule.copy(unit = u)
                            onChange(
                                when (u) {
                                    RepeatUnit.WEEK -> if (next.weekDays.isEmpty()) next.copy(weekDays = setOf(anchor.dayOfWeek)) else next
                                    RepeatUnit.MONTH -> next.copy(monthMode = MonthMode.DAY_OF_MONTH, dayOfMonth = anchor.dayOfMonth)
                                    else -> next
                                },
                            )
                        }
                    }
                }
            }
        }

        if (rule.unit == RepeatUnit.WEEK) {
            Field("On") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEK_ORDER.forEach { d ->
                        val on = d in rule.weekDays
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (on) C.Accent else C.SurfaceHi)
                                .border(1.dp, if (on) C.Accent else C.Stroke, CircleShape)
                                .tap {
                                    val n = if (on) rule.weekDays - d else rule.weekDays + d
                                    if (n.isNotEmpty()) onChange(rule.copy(weekDays = n))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                color = if (on) C.AccentOn else C.Dim,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        if (rule.unit == RepeatUnit.MONTH) {
            Field("On") {
                val nth = (((anchor.dayOfMonth - 1) / 7) + 1).coerceAtMost(4)
                val isLastWeek = anchor.plusDays(7).monthValue != anchor.monthValue
                val dayName = anchor.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        "The ${ordinal(if (rule.dayOfMonth > 0) rule.dayOfMonth else anchor.dayOfMonth)}",
                        selected = rule.monthMode == MonthMode.DAY_OF_MONTH,
                    ) { onChange(rule.copy(monthMode = MonthMode.DAY_OF_MONTH, dayOfMonth = anchor.dayOfMonth)) }
                    Pill(
                        "${ordinal(nth)} $dayName",
                        selected = rule.monthMode == MonthMode.NTH_WEEKDAY && rule.nth == nth,
                    ) { onChange(rule.copy(monthMode = MonthMode.NTH_WEEKDAY, nth = nth, nthWeekday = anchor.dayOfWeek)) }
                    if (isLastWeek) {
                        Pill(
                            "Last $dayName",
                            selected = rule.monthMode == MonthMode.NTH_WEEKDAY && rule.nth == 5,
                        ) { onChange(rule.copy(monthMode = MonthMode.NTH_WEEKDAY, nth = 5, nthWeekday = anchor.dayOfWeek)) }
                    }
                    Pill("Last day", selected = rule.monthMode == MonthMode.LAST_DAY) {
                        onChange(rule.copy(monthMode = MonthMode.LAST_DAY))
                    }
                }
            }
        }

        Field("Ends") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Never", selected = rule.endType == EndType.NEVER) { onChange(rule.copy(endType = EndType.NEVER)) }
                Pill(
                    if (rule.endType == EndType.ON_DATE && rule.endDate != null) "On " + Fmt.dayShort(rule.endDate) else "On date",
                    selected = rule.endType == EndType.ON_DATE,
                ) { pickEnd = true }
                Pill("After", selected = rule.endType == EndType.AFTER_COUNT) {
                    onChange(rule.copy(endType = EndType.AFTER_COUNT, endCount = if (rule.endCount > 0) rule.endCount else 10))
                }
            }
            if (rule.endType == EndType.AFTER_COUNT) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stepper(rule.endCount.coerceAtLeast(1), 1, 999) { onChange(rule.copy(endCount = it)) }
                    Text("times", color = C.Dim, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Field("Next one is counted") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("From due date", selected = rule.basis == RepeatBasis.FROM_DUE) { onChange(rule.copy(basis = RepeatBasis.FROM_DUE)) }
                Pill("From completion", selected = rule.basis == RepeatBasis.FROM_COMPLETION) { onChange(rule.copy(basis = RepeatBasis.FROM_COMPLETION)) }
            }
        }

        Text(rule.describe(), color = C.Accent, style = MaterialTheme.typography.bodyMedium)
    }

    if (pickEnd) {
        DatePickDialog(rule.endDate ?: anchor.plusMonths(1), onDismiss = { pickEnd = false }) {
            onChange(rule.copy(endType = EndType.ON_DATE, endDate = it))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Editor sheet
// ---------------------------------------------------------------------------------------------

private val OFFSET_PRESETS = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorSheet(
    initial: Draft,
    is24: Boolean,
    today: LocalDate,
    defaultReminder: Int,
    onDismiss: () -> Unit,
    onSave: (Draft) -> Unit,
    onDelete: (() -> Unit)?,
    calendarGranted: Boolean = true,
    onRequestCalendar: () -> Unit = {},
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var d by remember { mutableStateOf(initial) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var customOffset by remember { mutableStateOf(false) }
    var showRepeat by remember { mutableStateOf(initial.rule != null) }
    var customLength by remember { mutableStateOf(false) }
    var nl by remember { mutableStateOf("") }
    var nlMsg by remember { mutableStateOf<String?>(null) }
    fun applyNl() {
        val cleaned = nl.replace(
            Regex("^\\s*(?:please\\s+)?(?:push|move|reschedule|postpone|shift|bump|change|set|make)(?:\\s+(?:it|this))?(?:\\s+(?:to|till|until|for|on|by))?\\s+", RegexOption.IGNORE_CASE), "",
        )
        val p = TaskParser().parse(cleaned, d.date)
        var nd = d
        val bits = ArrayList<String>()
        if (p.date != null) { nd = nd.copy(date = p.date); bits += Fmt.date(p.date, today) }
        if (p.time != null) {
            val day = p.date ?: nd.date ?: today
            nd = nd.copy(date = day, time = p.time)
            if (p.date == null) bits += Fmt.date(day, today)
            bits += Fmt.time(p.time, is24)
        }
        if (p.priority != null) { nd = nd.copy(priority = p.priority); bits += priorityLabel(p.priority) }
        if (p.recurrence != null) { nd = nd.copy(rule = p.recurrence, date = nd.date ?: today); bits += p.recurrence.describe(); showRepeat = true }
        if (p.reminders != null) { nd = nd.copy(reminders = p.reminders); bits += "reminders" }
        if (p.durationMin != null) { nd = nd.copy(durationMin = p.durationMin); bits += durText(p.durationMin * 60) }
        if (bits.isEmpty()) {
            nlMsg = "Nothing I recognise in that. Try \"friday 5pm\", \"tomorrow\", \"!!\" or \"every mon\"."
        } else {
            d = nd
            nlMsg = "Changed: " + bits.joinToString(" \u00B7 ")
            nl = ""
        }
    }
    val isNew = initial.base == null

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
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(if (isNew) "New task" else "Edit task", color = C.Dim, style = MaterialTheme.typography.labelMedium)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    if (d.title.isEmpty()) Text("Task title", color = C.Faint, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal))
                    BasicTextField(
                        value = d.title,
                        onValueChange = { d = d.copy(title = it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = C.Text, fontWeight = FontWeight.Normal),
                        cursorBrush = SolidColor(C.Accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                Box {
                    if (d.notes.isEmpty()) Text("Notes", color = C.Faint, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = d.notes,
                        onValueChange = { d = d.copy(notes = it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = C.Dim),
                        cursorBrush = SolidColor(C.Accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }

            if (!isNew) Field("Change by typing") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.SurfaceHi)
                            .border(1.dp, C.Stroke, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (nl.isEmpty()) Text("push to friday 5pm, tomorrow morning, !!", color = C.Faint, style = MaterialTheme.typography.bodyMedium)
                        BasicTextField(
                            value = nl,
                            onValueChange = { nl = it; nlMsg = null },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = C.Text),
                            cursorBrush = SolidColor(C.Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { applyNl() }),
                        )
                    }
                    nlMsg?.let { Text(it, color = if (it.startsWith("Changed")) C.Accent else C.Faint, style = MaterialTheme.typography.bodySmall) }
                }
            }

            Field("When") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(d.date?.let { Fmt.date(it, today) } ?: "No date", selected = d.date != null) { pickDate = true }
                    Pill(d.time?.let { Fmt.time(it, is24) } ?: "No time", selected = d.time != null) {
                        if (d.date == null) d = d.copy(date = today)
                        pickTime = true
                    }
                    Pill("Today") { d = d.copy(date = today) }
                    Pill("Tomorrow") { d = d.copy(date = today.plusDays(1)) }
                    if (d.time != null) Pill("Clear time", color = C.Dim) { d = d.copy(time = null) }
                    if (d.date != null) Pill("Clear date", color = C.Dim) { d = d.copy(date = null, time = null, rule = null, reminders = emptyList()); showRepeat = false }
                }
            }

            Field("Priority") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2, 3).forEach { p ->
                        Pill(priorityLabel(p), selected = d.priority == p, color = priorityColor(p).let { if (p == 0) C.Accent else it }) {
                            d = d.copy(priority = p)
                        }
                    }
                }
            }

            Field("Reminders") {
                if (d.date == null) {
                    Text("Set a date to get reminders.", color = C.Faint, style = MaterialTheme.typography.bodyMedium)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val all = (OFFSET_PRESETS + d.reminders).distinct().sorted()
                        all.forEach { off ->
                            val on = off in d.reminders
                            Pill(Fmt.offsetLabel(off), selected = on, color = C.Low) {
                                d = d.copy(reminders = if (on) d.reminders - off else d.reminders + off)
                            }
                        }
                        Pill("+ Custom", color = C.Dim) { customOffset = true }
                    }
                    if (d.time == null && d.reminders.isNotEmpty()) {
                        Text("No time set, so reminders count from your default reminder time.", color = C.Faint, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Field("Duration") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("None", selected = d.durationMin == null) { d = d.copy(durationMin = null) }
                    listOf(15, 25, 30, 45, 60, 90, 120).forEach { m ->
                        Pill(durText(m * 60), selected = d.durationMin == m) { d = d.copy(durationMin = m) }
                    }
                    val c = d.durationMin
                    if (c != null && c !in listOf(15, 25, 30, 45, 60, 90, 120)) Pill(durText(c * 60), selected = true) { customLength = true }
                    Pill("+ Custom", color = C.Dim) { customLength = true }
                }
                if (d.durationMin != null) Text("Used for calendar blocks and as the length of a focus session.", color = C.Faint, style = MaterialTheme.typography.bodySmall)
            }

            Field("Repeat") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d.rule?.describe() ?: "Does not repeat",
                        color = if (d.rule != null) C.Text else C.Dim,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    SoftSwitch(d.rule != null) { on ->
                        if (on) {
                            val date = d.date ?: today
                            val rule = Recurrence()
                            d = d.copy(date = RecurrenceCalc.firstOccurrence(date, rule), rule = rule)
                            showRepeat = true
                        } else {
                            d = d.copy(rule = null)
                            showRepeat = false
                        }
                    }
                }
                val r = d.rule
                if (r != null && showRepeat) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.SurfaceHi).border(1.dp, C.Stroke, RoundedCornerShape(20.dp)).padding(16.dp)) {
                        RepeatBuilder(r, d.date ?: today) { nr ->
                            val date = d.date ?: today
                            d = d.copy(rule = nr, date = if (nr.unit == RepeatUnit.WEEK && date.dayOfWeek !in nr.weekDays && nr.weekDays.isNotEmpty()) RecurrenceCalc.firstOccurrence(date, nr) else date)
                        }
                    }
                }
            }

            Field("Phone calendar") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Add to phone calendar",
                            color = if (d.addToCalendar) C.Text else C.Dim,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            if (d.date == null) "Pick a date first" else "Shows up in your calendar app too",
                            color = C.Faint, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    SoftSwitch(d.addToCalendar && d.date != null) { on ->
                        if (on && d.date == null) return@SoftSwitch
                        if (on && !calendarGranted) onRequestCalendar()
                        d = d.copy(addToCalendar = on)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) {
                    Pill("Delete", color = C.High, selected = true) { onDelete() }
                }
                Spacer(Modifier.weight(1f))
                val ok = d.title.isNotBlank()
                Box(
                    Modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).background(if (ok) C.Accent else C.SurfaceTop)
                        .tap(enabled = ok) { onSave(d) }.padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (isNew) "Add task" else "Save", color = if (ok) C.AccentOn else C.Faint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (pickDate) DatePickDialog(d.date, onDismiss = { pickDate = false }) { picked -> d = d.copy(date = picked) }
    if (pickTime) TimePickDialog(d.time, is24, onDismiss = { pickTime = false }) { picked ->
        d = d.copy(time = picked, reminders = if (d.reminders.isEmpty() && defaultReminder >= 0 && isNew) listOf(defaultReminder) else d.reminders)
    }
    if (customLength) CustomOffsetDialog(
        onDismiss = { customLength = false }, title = "Takes about", confirm = "Set", units = listOf("minutes", "hours"),
    ) { minutes -> d = d.copy(durationMin = minutes.coerceAtMost(720)) }
    if (customOffset) CustomOffsetDialog(onDismiss = { customOffset = false }) { minutes ->
        if (minutes !in d.reminders) d = d.copy(reminders = d.reminders + minutes)
    }
}

@Composable
private fun CustomOffsetDialog(
    onDismiss: () -> Unit,
    title: String = "Remind me before",
    confirm: String = "Add",
    units: List<String> = listOf("minutes", "hours", "days"),
    onPick: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(0) } // 0 min, 1 hour, 2 day
    val factor = intArrayOf(1, 60, 1440)[unit]
    val n = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.SurfaceHi,
        title = { Text(title, color = C.Text, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Surface).border(1.dp, C.Stroke, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    if (text.isEmpty()) Text("Amount", color = C.Faint)
                    BasicTextField(
                        value = text,
                        onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) text = v },
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = C.Text),
                        cursorBrush = SolidColor(C.Accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    units.forEachIndexed { i, l -> Pill(l, selected = unit == i) { unit = i } }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = n != null && n > 0, onClick = { onPick(n!! * factor); onDismiss() }) { Text(confirm, color = C.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Dim) } },
    )
}
