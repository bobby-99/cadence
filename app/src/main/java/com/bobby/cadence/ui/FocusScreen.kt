package com.bobby.cadence.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobby.cadence.alarm.FocusState
import com.bobby.cadence.data.AppSettings
import com.bobby.cadence.data.FocusSession
import com.bobby.cadence.data.Task
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun clock(ms: Long): String {
    val t = (ms / 1000L).toInt().coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** "1h 25m", "45m", "30s" */
internal fun durText(seconds: Int): String {
    val m = seconds / 60
    return when {
        seconds < 60 -> "${seconds}s"
        m < 60 -> "${m}m"
        else -> "${m / 60}h" + (if (m % 60 != 0) " ${m % 60}m" else "")
    }
}

internal fun todaySeconds(sessions: List<FocusSession>, today: LocalDate): Int {
    val zone = ZoneId.systemDefault()
    return sessions.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today }.sumOf { it.seconds }
}

private val BREAK_GREEN = Color(0xFF9BD3A5)
private val BREAK_TEAL = Color(0xFF8FC7B8)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    state: FocusState,
    settings: AppSettings,
    tasks: List<Task>,
    sessions: List<FocusSession>,
    today: LocalDate,
    linkTask: Task?,
    onClose: () -> Unit,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    onStart: (mode: Int, tag: String, task: Task?, minutes: Int?) -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onPhaseEnd: () -> Unit,
    onCompleteTask: (Task) -> Unit,
    onDeleteSession: (FocusSession) -> Unit,
) {
    var showInsights by remember { mutableStateOf(false) }
    var showTune by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var linked by remember(linkTask?.id) { mutableStateOf(linkTask) }
    var tag by remember { mutableStateOf(settings.lastTag) }
    val mode = if (state.active) state.mode else settings.focusMode

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (true) {
            now = System.currentTimeMillis()
            if (state.active && state.mode == 0 && state.running && state.remaining(now) <= 0L) {
                onPhaseEnd()
                delay(1000)
            }
            delay(250)
        }
    }

    val view = LocalView.current
    val keepOn = settings.keepScreenOn && state.active && state.running
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = showInsights) { showInsights = false }

    val activeTask = if (state.active && state.taskId != 0L) tasks.firstOrNull { it.id == state.taskId } else linked
    val phaseColor = when (if (state.active) state.phase else 0) {
        1 -> BREAK_GREEN
        2 -> BREAK_TEAL
        else -> C.Accent
    }
    val idleMinutes = if (mode == 0) (linked?.durationMin ?: settings.focusMin) else 0
    val doneToday = todaySeconds(sessions, today) +
        (if (state.active && state.phase == 0) (state.elapsed(now) / 1000L).toInt() else 0)

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 20.dp).height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTap(Icons.Default.Close, "Close focus", onClose, filled = false)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(C.SurfaceHi).border(1.dp, C.Stroke, CircleShape).tap { showInsights = true },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.CHART, C.Accent) }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Focus", color = C.Text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )

                // mode switch, only while nothing is running
                AnimatedVisibility(!state.active, enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
                    Row(
                        Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface)
                            .border(1.dp, C.Stroke, RoundedCornerShape(16.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(0 to "Pomodoro", 1 to "Freestyle").forEach { (m, label) ->
                            val on = mode == m
                            Box(
                                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (on) C.Accent else Color.Transparent).tap { onSettings { it.copy(focusMode = m) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (on) C.AccentOn else C.Text, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(if (state.active) 26.dp else 22.dp))

                // ---- the timer ----
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "timer-mode",
                ) { m ->
                    if (m == 0) {
                        val total = if (state.active) state.plannedMs else idleMinutes * 60_000L
                        val progress = if (state.active && total > 0) (state.elapsed(now).toFloat() / total).coerceIn(0f, 1f) else 0f
                        val anim by animateFloatAsState(progress, tween(260, easing = LinearEasing), label = "ring")
                        Box(Modifier.widthIn(max = 300.dp).fillMaxWidth(0.82f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val sw = 14.dp.toPx()
                                val inset = sw / 2
                                val sz = Size(size.width - sw, size.height - sw)
                                drawArc(C.SurfaceHi, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
                                if (anim > 0f) drawArc(phaseColor, -90f, 360f * anim, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (state.active) clock(if (state.running || state.elapsedBefore > 0L) state.remaining(now) else state.plannedMs)
                                    else clock(idleMinutes * 60_000L),
                                    color = C.Text,
                                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 58.sp, fontFeatureSettings = "tnum"),
                                )
                                Text(
                                    when {
                                        !state.active -> "READY"
                                        state.phase == 0 -> "FOCUS"
                                        state.phase == 1 -> "SHORT BREAK"
                                        else -> "LONG BREAK"
                                    } + if (state.active && !state.running && !state.waiting) " · PAUSED" else "",
                                    color = phaseColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                val dots = settings.cyclesUntilLong.coerceIn(1, 8)
                                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    for (i in 0 until dots) {
                                        val filled = state.active && i < state.cycle
                                        val current = state.active && state.phase == 0 && i == state.cycle
                                        Box(
                                            Modifier.size(9.dp).clip(CircleShape)
                                                .background(if (filled) C.Accent else Color.Transparent)
                                                .border(1.5.dp, if (filled || current) C.Accent else C.Rail, CircleShape),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
                            GlyphIcon(Glyph.HOURGLASS, if (state.active && state.running) C.Accent else C.Dim, size = 96.dp)
                            Text(
                                clock(if (state.active) state.elapsed(now) else 0L),
                                color = C.Text, modifier = Modifier.padding(top = 18.dp),
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 58.sp, fontFeatureSettings = "tnum"),
                            )
                            val goal = if (state.active) state.goalMin else settings.freestyleGoalMin
                            Text(
                                when {
                                    !state.active -> "FREESTYLE" + if (goal > 0) " · GOAL ${durText(goal * 60)}" else ""
                                    goal > 0 && state.elapsed(now) >= goal * 60_000L -> "GOAL REACHED"
                                    !state.running -> "PAUSED"
                                    else -> "FOCUSING" + if (goal > 0) " · GOAL ${durText(goal * 60)}" else ""
                                },
                                color = C.Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ---- tag + task ----
                val tagShown = if (state.active) state.tag else tag
                if (state.active) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (tagShown.isNotBlank()) Pill(tagShown, selected = true)
                        if (state.taskTitle.isNotBlank()) Pill(state.taskTitle, color = C.Dim)
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("TAG", color = C.Faint, style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            settings.focusTags.forEach { t -> Pill(t, selected = tag == t) { tag = if (tag == t) "" else t } }
                            Pill("+ New", color = C.Dim) { newTag = true }
                        }
                        Text("TASK", color = C.Faint, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val l = linked
                            if (l == null) Pill("Link a task", color = C.Dim) { showPicker = true }
                            else {
                                Pill(l.title + (l.durationMin?.let { " · ${durText(it * 60)}" } ?: ""), selected = true) { showPicker = true }
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(C.SurfaceHi).tap { linked = null },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Default.Close, "Unlink task", tint = C.Dim, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }

                // ---- goal ----
                if (settings.dailyGoalMin > 0) {
                    val frac = (doneToday / 60f / settings.dailyGoalMin).coerceIn(0f, 1f)
                    val fr by animateFloatAsState(frac, tween(400), label = "goal")
                    Column(Modifier.fillMaxWidth().padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Text("Today", color = C.Dim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                "${durText(doneToday)} of ${durText(settings.dailyGoalMin * 60)}" + if (frac >= 1f) " · goal reached" else "",
                                color = if (frac >= 1f) C.Done else C.Dim, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(C.SurfaceHi)) {
                            Box(Modifier.fillMaxWidth(fr).height(6.dp).clip(CircleShape).background(if (frac >= 1f) C.Done else C.Accent))
                        }
                    }
                }

                // ---- break: offer to finish the linked task ----
                val at = activeTask
                if (state.active && state.phase != 0 && at != null && !at.completed) {
                    Row(
                        Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.AccentCard)
                            .border(1.dp, C.AccentLine, RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Finished \"${at.title}\"?", color = C.Text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Pill("Mark done", selected = true) { onCompleteTask(at) }
                    }
                }

                if (!state.active) {
                    Row(
                        Modifier.padding(top = 18.dp).clip(RoundedCornerShape(50)).border(1.dp, C.Stroke, RoundedCornerShape(50))
                            .tap { showTune = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlyphIcon(Glyph.TUNE, C.Dim, size = 16.dp)
                        Text(
                            if (mode == 0) "${settings.focusMin} · ${settings.shortBreakMin} · ${settings.longBreakMin} min   ×${settings.cyclesUntilLong}"
                            else "Customize",
                            color = C.Dim, style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ---- controls ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.active) {
                    Box(
                        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).background(C.Accent).tap {
                            onSettings { it.copy(lastTag = tag) }
                            onStart(mode, tag, linked, if (mode == 0) linked?.durationMin else null)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlyphIcon(Glyph.PLAY, C.AccentOn, size = 22.dp)
                            Text("Start focus", color = C.AccentOn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    RoundControl(Glyph.STOP, C.Danger, 56) { if (state.elapsed(now) > 60_000L) confirmStop = true else onStop() }
                    Spacer(Modifier.width(22.dp))
                    Box(
                        Modifier.size(80.dp).clip(CircleShape).background(phaseColor).tap(onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = state.running,
                            transitionSpec = { (scaleIn(tween(160)) + fadeIn(tween(160))) togetherWith (scaleOut(tween(120)) + fadeOut(tween(120))) },
                            label = "playpause",
                        ) { running -> GlyphIcon(if (running) Glyph.PAUSE else Glyph.PLAY, C.AccentOn, size = 34.dp) }
                    }
                    Spacer(Modifier.width(22.dp))
                    if (state.mode == 0) RoundControl(Glyph.SKIP, C.Text, 56, onSkip)
                    else Box(Modifier.size(56.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = showInsights,
            enter = slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(160)),
        ) {
            FocusInsights(sessions, settings, today, onBack = { showInsights = false }, onDelete = onDeleteSession)
        }
    }

    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        containerColor = C.SurfaceHi,
        title = { Text("End this session?", color = C.Text) },
        text = { Text("Time focused so far is saved.", color = C.Dim) },
        confirmButton = { TextButton(onClick = { confirmStop = false; onStop() }) { Text("End", color = C.Danger) } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going", color = C.Dim) } },
    )

    if (newTag) NewTagDialog(onDismiss = { newTag = false }) { name ->
        val clean = name.replace("|", " ").trim().take(18)
        if (clean.isNotBlank()) {
            onSettings { s -> if (clean in s.focusTags) s else s.copy(focusTags = s.focusTags + clean) }
            tag = clean
        }
    }

    if (showPicker) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            sheetState = sheet,
            containerColor = C.Surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            val open = tasks.filter { !it.completed }.sortedWith(compareBy<Task> { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.dueTime ?: 0 }).take(60)
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                Text("Link a task", color = C.Text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                if (open.isEmpty()) Text("No open tasks.", color = C.Dim, modifier = Modifier.padding(vertical = 24.dp))
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    open.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).tap { linked = t; showPicker = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t.title, color = C.Text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            t.durationMin?.let { Text(durText(it * 60), color = C.Accent, style = MaterialTheme.typography.labelMedium) }
                        }
                        Hairline()
                    }
                }
            }
        }
    }

    if (showTune) FocusSettingsSheet(settings, onSettings) { showTune = false }
}

@Composable
private fun RoundControl(glyph: Glyph, tint: Color, size: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(C.SurfaceHi).border(1.dp, C.Stroke, CircleShape).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, tint, size = 24.dp) }
}

@Composable
private fun NewTagDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.SurfaceHi,
        title = { Text("New tag", color = C.Text, style = MaterialTheme.typography.titleLarge) },
        text = {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Surface).border(1.dp, C.Stroke, RoundedCornerShape(14.dp)).padding(14.dp)) {
                if (text.isEmpty()) Text("Study, Gym, Writing…", color = C.Faint)
                BasicTextField(
                    value = text, onValueChange = { if (it.length <= 18) text = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = C.Text),
                    cursorBrush = SolidColor(C.Accent), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onAdd(text); onDismiss() }) { Text("Add", color = C.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Dim) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FocusSettingsSheet(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = C.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Focus settings", color = C.Text, style = MaterialTheme.typography.titleLarge)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PRESETS", color = C.Faint, style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple(25, 5, 15), Triple(50, 10, 30), Triple(90, 20, 30), Triple(15, 3, 10)).forEach { (f, s, l) ->
                        Pill(
                            "$f / $s", selected = settings.focusMin == f && settings.shortBreakMin == s,
                        ) { onChange { it.copy(focusMin = f, shortBreakMin = s, longBreakMin = l) } }
                    }
                }
            }

            Card2 {
                StepRow("Focus", "${settings.focusMin} min", { onChange { it.copy(focusMin = (it.focusMin - 5).coerceAtLeast(5)) } }, { onChange { it.copy(focusMin = (it.focusMin + 5).coerceAtMost(180)) } })
                StepRow("Short break", "${settings.shortBreakMin} min", { onChange { it.copy(shortBreakMin = (it.shortBreakMin - 1).coerceAtLeast(1)) } }, { onChange { it.copy(shortBreakMin = (it.shortBreakMin + 1).coerceAtMost(30)) } })
                StepRow("Long break", "${settings.longBreakMin} min", { onChange { it.copy(longBreakMin = (it.longBreakMin - 5).coerceAtLeast(5)) } }, { onChange { it.copy(longBreakMin = (it.longBreakMin + 5).coerceAtMost(60)) } })
                StepRow("Rounds before long break", "${settings.cyclesUntilLong}", { onChange { it.copy(cyclesUntilLong = (it.cyclesUntilLong - 1).coerceAtLeast(2)) } }, { onChange { it.copy(cyclesUntilLong = (it.cyclesUntilLong + 1).coerceAtMost(8)) } })
            }

            Card2 {
                StepRow(
                    "Daily goal", if (settings.dailyGoalMin == 0) "Off" else durText(settings.dailyGoalMin * 60),
                    { onChange { it.copy(dailyGoalMin = (it.dailyGoalMin - 15).coerceAtLeast(0)) } },
                    { onChange { it.copy(dailyGoalMin = (it.dailyGoalMin + 15).coerceAtMost(720)) } },
                )
                StepRow(
                    "Freestyle target", if (settings.freestyleGoalMin == 0) "None" else durText(settings.freestyleGoalMin * 60),
                    { onChange { it.copy(freestyleGoalMin = (it.freestyleGoalMin - 15).coerceAtLeast(0)) } },
                    { onChange { it.copy(freestyleGoalMin = (it.freestyleGoalMin + 15).coerceAtMost(480)) } },
                )
            }

            Card2 {
                SwitchRow("Start breaks automatically", settings.autoStartBreaks) { v -> onChange { it.copy(autoStartBreaks = v) } }
                SwitchRow("Start next focus automatically", settings.autoStartFocus) { v -> onChange { it.copy(autoStartFocus = v) } }
                SwitchRow("Keep screen on while running", settings.keepScreenOn) { v -> onChange { it.copy(keepScreenOn = v) } }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("YOUR TAGS", color = C.Faint, style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.focusTags.forEach { t ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(C.SurfaceHi).border(1.dp, C.Stroke, RoundedCornerShape(50))
                                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(t, color = C.Text, style = MaterialTheme.typography.labelLarge)
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).tap { onChange { it.copy(focusTags = it.focusTags - t, lastTag = if (it.lastTag == t) "" else it.lastTag) } },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Close, "Remove $t", tint = C.Dim, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
                Text("Sessions already logged keep their tag.", color = C.Faint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Card2(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.SurfaceHi).border(1.dp, C.Stroke, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
    ) { content() }
}

@Composable
private fun StepRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = C.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        StepBtn("−", onMinus)
        Text(value, color = C.Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(76.dp))
        StepBtn("+", onPlus)
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(C.Surface).border(1.dp, C.Stroke, CircleShape).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = C.Text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = C.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.AccentOn, checkedTrackColor = C.Accent,
                uncheckedThumbColor = C.Dim, uncheckedTrackColor = C.SurfaceHi, uncheckedBorderColor = C.Stroke,
            ),
        )
    }
}
