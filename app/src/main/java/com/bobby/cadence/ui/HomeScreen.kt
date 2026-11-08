package com.bobby.cadence.ui

import android.text.format.DateFormat
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobby.cadence.Draft
import com.bobby.cadence.MainViewModel
import com.bobby.cadence.data.Actions
import com.bobby.cadence.data.CalendarBridge
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.Task
import com.bobby.cadence.data.rule
import com.bobby.cadence.data.time
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Overlay { NONE, CALENDAR, SETTINGS, FOCUS, TUTORIAL }

/** One-shot request from the launcher shortcut, widget or a notification tap. */
data class LaunchCommand(val action: String?, val taskId: Long, val serial: Long, val text: String? = null)

@Composable
fun HomeScreen(vm: MainViewModel, command: LaunchCommand?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }

    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val toasts = remember { mutableStateListOf<ToastItem>() }
    var overlay by remember { mutableStateOf(if (settings.onboarded) Overlay.NONE else Overlay.TUTORIAL) }
    val finishTutorial = { vm.update { it.copy(onboarded = true) }; overlay = Overlay.NONE }
    var quickAdd by remember { mutableStateOf<LocalDate?>(null) }
    var quickOpen by remember { mutableStateOf(false) }
    var quickText by remember { mutableStateOf("") }
    var micOpen by remember { mutableStateOf(false) }
    var recurring by remember { mutableStateOf<Task?>(null) }
    var editor by remember { mutableStateOf<Draft?>(null) }
    var snoozing by remember { mutableStateOf<Task?>(null) }
    var focusTask by remember { mutableStateOf<Task?>(null) }
    val focusState by vm.focusState.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val streaks = remember(tasks) { TaskLogic.streaks(tasks) }
    var calSelected by remember { mutableStateOf(LocalDate.now()) }
    var collapsed by remember { mutableStateOf(setOf("completed", "day-done")) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshToday() }

    // shortcuts, widget buttons and notification taps
    LaunchedEffect(command?.serial) {
        val c = command ?: return@LaunchedEffect
        when {
            c.action == Actions.QUICK_ADD -> { overlay = Overlay.NONE; quickAdd = null; quickText = c.text ?: ""; quickOpen = true }
            c.action == Actions.OPEN_TASKS -> { overlay = Overlay.NONE; vm.resetFilter() }
            c.action == Actions.OPEN_FOCUS -> { focusTask = null; overlay = Overlay.FOCUS }
            c.taskId > 0 -> {
                val t = vm.repo.get(c.taskId)
                if (t != null) { overlay = Overlay.NONE; if (t.rule != null && !t.completed) recurring = t else editor = Draft.from(t) }
            }
        }
    }

    BackHandler(enabled = overlay != Overlay.NONE) { if (overlay == Overlay.TUTORIAL) finishTutorial() else overlay = Overlay.NONE }
    BackHandler(enabled = overlay == Overlay.NONE && drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = overlay == Overlay.NONE && !drawer.isOpen && !filter.isDefault) { vm.resetFilter() }

    fun say(msg: String, action: String? = null, onAction: (() -> Unit)? = null) {
        toasts += ToastItem(System.nanoTime(), msg, action, onAction)
        while (toasts.size > 4) toasts.removeAt(0)
    }

    var calGranted by remember { mutableStateOf(CalendarBridge.canWrite(ctx)) }
    var pendingSave by remember { mutableStateOf<Draft?>(null) }
    fun doSave(d: Draft) {
        vm.save(d) { ok -> if (!ok) say("Saved, but it could not be added to your phone calendar") }
    }
    val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        calGranted = CalendarBridge.canWrite(ctx)
        val p = pendingSave
        pendingSave = null
        if (p != null) doSave(if (calGranted) p else p.copy(addToCalendar = false))
    }
    fun askCalendar() {
        calLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }
    fun saveDraft(d: Draft) {
        if (d.addToCalendar && !CalendarBridge.canWrite(ctx)) { pendingSave = d; askCalendar() } else doSave(d)
    }

    fun toggle(t: Task) {
        if (t.completed) {
            vm.uncomplete(t)
        } else {
            if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.complete(t)
            say("Completed", "Undo") { vm.uncomplete(t) }
        }
    }

    fun remove(t: Task) {
        vm.delete(t)
        say("Deleted", "Undo") { vm.restore(t) }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = overlay == Overlay.NONE,
        scrimColor = C.Scrim,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = C.Surface,
                drawerContentColor = C.Text,
                drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                modifier = Modifier.fillMaxHeight(),
            ) {
                DrawerContent(
                    filter = filter,
                    home = home,
                    settings = settings,
                    onView = { v -> vm.setView(v); scope.launch { drawer.close() } },
                    onPriority = { vm.setPriority(it) },
                    onQuery = { vm.setQuery(it) },
                    onSort = { m -> vm.update { it.copy(sort = m) } },
                    onShowCompleted = { v -> vm.update { it.copy(showCompleted = v) } },
                    onReset = { vm.resetFilter(); scope.launch { drawer.close() } },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            // ---- main list ----
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconTap(Icons.Default.Menu, "Open menu", { scope.launch { drawer.open() } }, filled = false)
                    Box(Modifier.weight(1f))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).tap { focusTask = null; overlay = Overlay.FOCUS },
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(Glyph.TIMER, if (focusState.active) C.Accent else C.Text) }
                    IconTap(Icons.Default.DateRange, "Calendar", { calSelected = filter.day ?: today; overlay = Overlay.CALENDAR }, filled = false)
                    IconTap(Icons.Default.Settings, "Settings", { overlay = Overlay.SETTINGS }, filled = false)
                }

                val nextUp: Task? = if (filter.day == null && (filter.view == ViewKind.ALL || filter.view == ViewKind.TODAY)) {
                    val now = java.time.LocalTime.now()
                    home.sections.firstOrNull { it.key == "today" }?.tasks
                        ?.firstOrNull { !it.completed && it.time != null && !it.time!!.isBefore(now) }
                } else null
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 8.dp, end = 20.dp, bottom = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item(key = "header") {
                        Header(filter, home, today, nextUp, onClearDay = { vm.setDay(null) }, onClearPriority = { vm.setPriority(null) }, onClearQuery = { vm.setQuery("") })
                    }

                    if (home.loaded && home.sections.isEmpty()) {
                        item(key = "empty") {
                            val (title, hint) = emptyText(filter)
                            EmptyState(title, hint)
                        }
                    }

                    home.sections.forEach { section ->
                        val isCollapsed = section.collapsible && section.key in collapsed
                        item(key = "h-${section.key}") {
                            SectionHeader(
                                section, isCollapsed,
                                if (section.collapsible) ({
                                    collapsed = if (section.key in collapsed) collapsed - section.key else collapsed + section.key
                                }) else null,
                                action = if (section.key == "overdue") ("Move all to today" to {
                                    vm.moveOverdue { old ->
                                        say("Moved ${old.size} to today", "Undo") { vm.putBack(old) }
                                    }
                                }) else null,
                            )
                        }
                        if (!isCollapsed) {
                            items(section.tasks, key = { it.id }) { t ->
                                TaskRow(
                                    task = t,
                                    today = today,
                                    is24 = is24,
                                    showDate = filter.day == null && !(section.key == "today"),
                                    onToggle = { toggle(t) },
                                    onOpen = { if (t.rule != null && !t.completed) recurring = t else editor = Draft.from(t) },
                                    onEdit = { editor = Draft.from(t) },
                                    onSnooze = { snoozing = t },
                                    onFocus = { focusTask = t; overlay = Overlay.FOCUS },
                                    streak = streaks[t.seriesId] ?: 0,
                                    highlight = t.id == nextUp?.id,
                                    onSwipeComplete = { toggle(t) },
                                    onSwipeDelete = { remove(t) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            // ---- floating add bar ----
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, C.Bg.copy(alpha = 0.94f), C.Bg)))
                    .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 16.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(C.Text)
                            .tap { quickAdd = null; quickText = ""; quickOpen = true }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Add, null, tint = C.Bg, modifier = Modifier.size(22.dp))
                        Text("New task", color = C.Bg, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    }
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(C.Surface)
                            .border(1.dp, C.Stroke, RoundedCornerShape(18.dp)).tap { micOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MicIcon, "Dictate a task", tint = C.Accent, modifier = Modifier.size(24.dp))
                    }
                }
            }

            ToastStack(
                toasts,
                onRemove = { toasts.remove(it) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(bottom = 92.dp, start = 20.dp, end = 20.dp),
            )

            // ---- calendar overlay ----
            AnimatedVisibility(
                visible = overlay == Overlay.CALENDAR,
                enter = slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(160)),
            ) {
                CalendarScreen(
                    tasks = tasks,
                    today = today,
                    weekStartMonday = settings.weekStartMonday,
                    is24 = is24,
                    selected = calSelected,
                    showDeviceEvents = settings.showDeviceEvents,
                    hiddenCalendars = settings.hiddenCalendars,
                    onSelect = { calSelected = it },
                    onClose = { overlay = Overlay.NONE },
                    onAddForDay = { quickAdd = it; quickText = ""; quickOpen = true },
                    onShowInList = { d -> vm.setDay(d); overlay = Overlay.NONE },
                    onToggle = { toggle(it) },
                    onOpen = { if (it.rule != null && !it.completed) recurring = it else editor = Draft.from(it) },
                )
            }

            // ---- focus overlay ----
            AnimatedVisibility(
                visible = overlay == Overlay.FOCUS,
                enter = slideInVertically(tween(280)) { it / 8 } + fadeIn(tween(240)),
                exit = slideOutVertically(tween(200)) { it / 8 } + fadeOut(tween(160)),
            ) {
                FocusScreen(
                    state = focusState,
                    settings = settings,
                    tasks = tasks,
                    sessions = sessions,
                    today = today,
                    linkTask = focusTask,
                    onClose = { overlay = Overlay.NONE },
                    onSettings = { f -> vm.update(f) },
                    onStart = { m, tag, t, mins -> vm.startFocus(m, tag, t, mins) },
                    onToggle = { vm.focus.toggle() },
                    onSkip = { vm.focus.skip() },
                    onStop = { vm.focus.stop() },
                    onPhaseEnd = { vm.focus.onPhaseEnd() },
                    onCompleteTask = { t -> toggle(t) },
                    onDeleteSession = { s -> vm.deleteSession(s) },
                )
            }

            // ---- settings overlay ----
            AnimatedVisibility(
                visible = overlay == Overlay.SETTINGS,
                enter = slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(160)),
            ) {
                SettingsScreen(
                    settings = settings,
                    is24 = is24,
                    onChange = { f -> vm.update(f) },
                    onClose = { overlay = Overlay.NONE },
                    onExport = { cb -> vm.export(cb) },
                    onImport = { text, cb -> vm.import(text, cb) },
                    onClearCompleted = { cb -> vm.clearCompleted(cb) },
                    onBackupNow = { cb -> vm.backupNow(cb) },
                    onReplayTutorial = { overlay = Overlay.TUTORIAL },
                )
            }

            // ---- first-run tutorial ----
            AnimatedVisibility(
                visible = overlay == Overlay.TUTORIAL,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
            ) {
                OnboardingScreen(is24 = is24, onFinish = { finishTutorial() })
            }
        }
    }

    // ---- sheets ----
    if (quickOpen) {
        val forDay = quickAdd
        QuickAddSheet(
            defaultDate = forDay,
            initialText = quickText,
            settings = settings,
            is24 = is24,
            today = today,
            onDismiss = { quickOpen = false; quickAdd = null; quickText = "" },
            onAdd = { d ->
                // added from the calendar view: also written to the phone calendar
                saveDraft(if (quickAdd != null && d.date != null) d.copy(addToCalendar = true) else d)
                quickOpen = false
                quickAdd = null
                val whenText = d.date?.let { Fmt.date(it, today) + (d.time?.let { t -> " · " + Fmt.time(t, is24) } ?: "") }
                say(if (whenText != null) "Added · $whenText" else "Added")
            },
            onMore = { d ->
                val fromCal = quickAdd != null && d.date != null
                quickOpen = false
                quickAdd = null
                editor = if (fromCal) d.copy(addToCalendar = true) else d
            },
        )
    }

    if (micOpen) {
        MicSheet(
            settings = settings,
            is24 = is24,
            today = today,
            onDismiss = { micOpen = false },
            onAdd = { d ->
                vm.save(d)
                micOpen = false
                val whenText = d.date?.let { Fmt.date(it, today) + (d.time?.let { t -> " \u00B7 " + Fmt.time(t, is24) } ?: "") }
                say(if (whenText != null) "Added \u00B7 $whenText" else "Added")
            },
            onEdit = { d -> micOpen = false; editor = d },
        )
    }

    val rt = recurring
    if (rt != null) {
        RecurringSheet(
            task = rt,
            today = today,
            is24 = is24,
            onDismiss = { recurring = null },
            onComplete = { recurring = null; toggle(rt) },
            onSkip = { recurring = null; vm.skip(rt); say("Skipped to the next one") },
            onEditRepeat = { recurring = null; editor = Draft.from(rt) },
            onDelete = { recurring = null; remove(rt) },
        )
    }

    val sn = snoozing
    if (sn != null) {
        SnoozeSheet(
            title = sn.title,
            is24 = is24,
            today = today,
            recent = settings.recentSnoozes,
            onDismiss = { snoozing = null },
            onSnooze = { millis, custom ->
                snoozing = null
                vm.snoozeUntil(sn, millis, custom) { old ->
                    val at = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
                    val whenText = Fmt.date(at.toLocalDate(), today) + " \u00B7 " + Fmt.time(at.toLocalTime(), is24)
                    say("Snoozed \u00B7 $whenText", if (old != null) "Undo" else null) { if (old != null) vm.putBack(listOf(old)) }
                }
            },
        )
    }

    val ed = editor
    if (ed != null) {
        EditorSheet(
            initial = ed,
            is24 = is24,
            today = today,
            defaultReminder = settings.defaultReminder,
            onDismiss = { editor = null },
            onSave = { d -> saveDraft(d); editor = null },
            calendarGranted = calGranted,
            onRequestCalendar = { askCalendar() },
            onDelete = ed.base?.let { base -> { editor = null; remove(base) } },
        )
    }
}

private fun emptyText(f: UiFilter): Pair<String, String> = when {
    f.query.isNotBlank() -> "No matches" to "Nothing found for \"${f.query}\"."
    f.day != null -> "Nothing on this day" to "Tap the add bar to plan something."
    f.view == ViewKind.OVERDUE -> "Nothing overdue" to "You are all caught up."
    f.view == ViewKind.TODAY -> "Clear for today" to "Enjoy it, or add something."
    f.view == ViewKind.COMPLETED -> "Nothing completed yet" to "Finished tasks show up here."
    f.view == ViewKind.REPEATING -> "No repeating tasks" to "Try typing \"every monday\" in a task."
    f.view == ViewKind.UPCOMING -> "Nothing upcoming" to "Tasks with a future date appear here."
    else -> "No tasks yet" to "Tap the add bar and type anything.\nTry \"call mom tomorrow 6pm\"."
}

@Composable
private fun Header(
    f: UiFilter,
    home: com.bobby.cadence.HomeState,
    today: LocalDate,
    nextUp: Task?,
    onClearDay: () -> Unit,
    onClearPriority: () -> Unit,
    onClearQuery: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            Fmt.dayShort(today).replace(",", "").uppercase(),
            color = C.Accent,
            style = MaterialTheme.typography.labelSmall,
        )
        val left = home.totalToday - home.doneToday
        val dayView = f.day == null && (f.view == ViewKind.ALL || f.view == ViewKind.TODAY)
        val title = when {
            f.day != null -> Fmt.date(f.day, today)
            dayView && left <= 0 -> "All clear\ntoday."
            dayView -> (if (left == 1) "1 thing" else "$left things") + "\nleft today."
            else -> f.view.label
        }
        androidx.compose.animation.AnimatedContent(
            targetState = title,
            transitionSpec = {
                (androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260)) { it / 3 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260))) togetherWith
                    (androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(180)) { -it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)))
            },
            label = "headline",
        ) { t -> Text(t, color = C.Text, style = MaterialTheme.typography.displayLarge, maxLines = 2) }

        if (dayView) {
            val sub = buildList {
                val nextTime = nextUp?.time
                if (nextTime != null) {
                    val mins = java.time.Duration.between(java.time.LocalTime.now(), nextTime).toMinutes().toInt().coerceAtLeast(0)
                    add("Next up in " + (if (mins == 0) "a moment" else Fmt.offset(mins)))
                }
                if (home.totalToday > 0) add("${home.doneToday} of ${home.totalToday} done")
            }.joinToString("  \u00B7  ")
            if (sub.isNotEmpty()) Text(sub, color = C.Dim, style = MaterialTheme.typography.bodyMedium)
            if (home.overdue > 0) Text("${home.overdue} overdue", color = C.Danger, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(Fmt.dayLong(today), color = C.Dim, style = MaterialTheme.typography.bodyMedium)
        }

        val chips = ArrayList<Pair<String, () -> Unit>>()
        if (f.day != null) chips += ("Day: " + Fmt.dayShort(f.day) + "  \u2715") to onClearDay
        if (f.priority != null) chips += (priorityLabel(f.priority) + " priority  \u2715") to onClearPriority
        if (f.query.isNotBlank()) chips += ("\"${f.query.trim()}\"  \u2715") to onClearQuery
        if (chips.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { (t, c) -> Pill(t, selected = true, onClick = c) }
            }
        }
    }
}
