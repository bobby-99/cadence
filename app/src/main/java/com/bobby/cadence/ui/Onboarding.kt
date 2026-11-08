package com.bobby.cadence.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt

private const val STEPS = 6

/**
 * The first-run tutorial: what Cadence is, how typing a task works (try it), the swipes, where the
 * calendar and focus live, and the permissions. [onFinish] is called when it is done or skipped.
 */
@Composable
fun OnboardingScreen(is24: Boolean, onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val perms = rememberPerms()
    val today = remember { LocalDate.now() }

    Column(
        Modifier.fillMaxSize().background(C.Bg)
            .pointerInput(Unit) { detectTapGestures { } } // swallow touches so nothing underneath reacts
            .statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        // top: skip
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            if (step < STEPS - 1) {
                Text("Skip", color = C.Dim, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onFinish() }.padding(8.dp))
            }
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (targetState > initialState) (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(140)))
                else (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(140)))
            },
            label = "onboarding",
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when (s) {
                    0 -> StepWelcome()
                    1 -> StepType(today, is24)
                    2 -> StepSwipe(today, is24)
                    3 -> StepTour()
                    4 -> StepPermissions(perms)
                    else -> StepDone()
                }
            }
        }

        // bottom: dots and the main button
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(STEPS) { i ->
                    val w by animateFloatAsState(if (i == step) 22f else 7f, tween(200), label = "dot")
                    Box(Modifier.height(7.dp).width(w.dp).clip(CircleShape).background(if (i == step) C.Accent else C.SurfaceTop))
                }
            }
            if (step > 0) {
                Text("Back", color = C.Dim, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { step-- }.padding(12.dp))
            }
            Box(
                Modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).background(C.Accent)
                    .clickable { if (step < STEPS - 1) step++ else onFinish() }.padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (step) { 0 -> "Get started"; STEPS - 1 -> "Start using Cadence"; else -> "Next" },
                    color = C.AccentOn, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun Title(text: String, sub: String) {
    Text(text, color = C.Text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
    Text(sub, color = C.Dim, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun StepWelcome() {
    Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppHero(132.dp)
        Text("Cadence", color = C.Text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp))
        Text(
            "A to-do list that does one thing well.\nNo accounts, no sync, no clutter. Everything stays on your phone.",
            color = C.Dim, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        )
        Text("This takes about a minute.", color = C.Faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepType(today: LocalDate, is24: Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    Title("Just type it", "Write a task the way you would say it. Cadence picks out the date, time, repeat and priority as you type.")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).border(1.dp, C.Stroke, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (text.isEmpty()) Text("Try: Call mom tomorrow 6pm !!", color = C.Faint, style = MaterialTheme.typography.titleLarge)
            BasicTextField(
                value = text, onValueChange = { text = it.take(120) },
                textStyle = MaterialTheme.typography.titleLarge.copy(color = C.Text),
                cursorBrush = SolidColor(C.Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (text.isNotBlank()) ParseDemo(text, today, is24)
    }
    Text("Or tap an example:", color = C.Faint, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Call mom tomorrow 6pm !!", "Gym every mon wed fri 7am", "Study for 45 min tomorrow 6pm", "Pay rent every month on the 5th").forEach { ex ->
            Pill(ex, onClick = { text = ex })
        }
    }
    Text("Nothing you type here is saved. The full list of what it understands is in Settings, About.", color = C.Faint, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun StepSwipe(today: LocalDate, is24: Boolean) {
    Title("Swipe a task", "This is a real task row. Try it: drag it left or right with your finger.")
    SwipeDemo(today, is24)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hint("Right", "One pull and the task is done.")
        Hint("Left", "Slides open on Edit, Snooze, Focus and Delete. Tap one.")
        Hint("Tap the dot", "Completes it right away. Undo stays on screen for a few seconds.")
    }
}

@Composable
private fun Hint(head: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(head, color = C.Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(84.dp))
        Text(text, color = C.Dim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** The real [TaskRow], on a practice task. Nothing here is saved; it just answers what you did. */
@Composable
private fun SwipeDemo(today: LocalDate, is24: Boolean) {
    var done by remember { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("Drag the task to the right or to the left.") }
    val task = remember(done) {
        com.bobby.cadence.data.Task(
            id = 1, title = "Call mom", dueDate = today.toEpochDay(), dueTime = 18 * 60, priority = 3, completed = done,
        )
    }
    // put the practice task back a moment after it was completed or deleted
    LaunchedEffect(done, gone) {
        if (done || gone) {
            delay(1800)
            done = false; gone = false
            msg = "Back again. Try the other direction."
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).background(C.Bg).border(1.dp, C.Stroke, RoundedCornerShape(16.dp))) {
            if (!gone) TaskRow(
                task = task, today = today, is24 = is24, showDate = false,
                onToggle = { done = !done; msg = if (done) "Completed from the dot." else "Un-completed." },
                onOpen = { msg = "Tapping a task opens it." },
                onEdit = { msg = "Edit opens the task so you can change anything." },
                onSnooze = { msg = "Snooze pushes the reminder back." },
                onFocus = { msg = "Focus starts a timer linked to this task." },
                onSwipeComplete = { done = true; msg = "Done. In the app you get an Undo for a few seconds." },
                onSwipeDelete = { gone = true; msg = "Deleted. In the app you get an Undo for a few seconds." },
            )
        }
        Text(msg, color = C.Accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun StepTour() {
    Title("Two more places", "Both are in the top right corner of your list.")
    TourCard(
        { IconTile(icon = Icons.Default.DateRange) },
        "Calendar",
        "Your week and month at a glance, with the events from your phone calendar. Search tasks and events, tap a day to see its timeline, and add a task straight onto any date.",
    )
    TourCard(
        { Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.TIMER, C.Accent, size = 20.dp) } },
        "Focus",
        "A timer for deep work. Pomodoro rounds or freestyle with an hourglass, a daily goal, tags, and Insights with a heatmap of your sessions. Swipe a task left to focus on it.",
    )
    TourCard(
        { IconTile(text = "+") },
        "Add from anywhere",
        "The widget on your home screen adds and completes tasks without opening the app. Selecting text in any app and choosing Cadence sends it here.",
    )
}

@Composable
private fun TourCard(icon: @Composable () -> Unit, title: String, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface).border(1.dp, C.Stroke, RoundedCornerShape(18.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = C.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text, color = C.Dim, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StepPermissions(p: Perms) {
    Title("Let reminders work", "Cadence is only useful if a reminder shows up on time. These switches make sure it does. You can change any of them later in Settings.")
    SGroup {
        row { StatusRow("Notifications", p.notifications, "Allowed", "Needed to show reminders", "Allow") { p.askNotifications() } }
        row { StatusRow("Unrestricted battery", p.battery, "Reminders will not be delayed", "Stops the system from delaying reminders", "Allow") { p.askBattery() } }
        row { StatusRow("Display over other apps", p.overlay, "The priority card can appear", "For the card on High and Medium priority tasks", "Allow") { p.askOverlay() } }
        row { StatusRow("Phone calendar", p.calendar, "Events can be shown and added", "Optional: show your events and add tasks to the calendar", "Allow") { p.askCalendar() } }
    }
}

@Composable
private fun StepDone() {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppHero(112.dp)
        Text("You're set", color = C.Text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Tap New task at the bottom and write your first one.\nThe tutorial and the full list of what Cadence understands are always in Settings, About.",
            color = C.Dim, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        )
    }
}
