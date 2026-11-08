package com.bobby.cadence.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate

const val REPO_URL = "https://github.com/bobby-99/cadence"

private fun openUrl(ctx: android.content.Context, url: String) =
    launchOrToast(ctx, "No browser found", Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun buildNumber(ctx: android.content.Context): String = try {
    "build " + androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
} catch (e: Exception) {
    ""
}

// ---------------------------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------------------------

@Composable
fun AboutPage(back: () -> Unit, onOpen: (SPage) -> Unit, onReplayTutorial: () -> Unit) {
    val ctx = LocalContext.current
    SettingsPage("About", back) {
        // hero
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppHero(112.dp)
            Text("Cadence", color = C.Text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp))
            Text("Version ${appVersion(ctx)}  ·  ${buildNumber(ctx)}", color = C.Faint, style = MaterialTheme.typography.bodySmall)
            Text(
                "Just the tasks that matter, said the way you'd say them.",
                color = C.Dim, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
            )
        }

        // philosophy
        SGroup(title = "Philosophy") {
            row { Principle("One job, done well", "Cadence is a to-do list and nothing else. No projects, no subtasks, no accounts, no sync. What is left out is a feature too.") }
            row { Principle("Say it, don't fill it in", "Type \"call mom tomorrow 6pm !!\" and the date, time, repeat and priority are worked out for you. Forms are there when you want them, not when you need them.") }
            row { Principle("Quiet until it matters", "A reminder that shows up on time, every time. A card when you are using the phone, a notification when you are not. Never louder than it has to be.") }
            row { Principle("Yours, and only yours", "Everything lives on your phone. No ads, no tracking, no network. Back it up whenever you like, take it anywhere.") }
        }

        SGroup(title = "Learn") {
            row { NavRow("How Cadence understands you", "Live demos", hint = "Every phrase it knows, with a playground") { onOpen(SPage.PARSING) } }
            row { NavRow("Help and answers") { onOpen(SPage.HELP) } }
            row { ActionRow("Replay the tutorial") { onReplayTutorial() } }
        }

        SGroup(title = "Project") {
            row { NavRow("Source code on GitHub", "bobby-99/cadence") { openUrl(ctx, REPO_URL) } }
            row { NavRow("Report a problem or suggest something") { openUrl(ctx, "$REPO_URL/issues/new/choose") } }
            row { NavRow("Release notes", "") { openUrl(ctx, "$REPO_URL/releases") } }
            row { NavRow("Licence", "PolyForm Noncommercial") { openUrl(ctx, "$REPO_URL/blob/main/LICENSE") } }
        }

        Text(
            "Free for personal use. Not for sale, and not for use in a business.\nTypefaces: Manrope and Google Sans, both under the SIL Open Font Licence 1.1.\nMade by Bobby.",
            color = C.Faint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun Principle(title: String, text: String) = BodyRow {
    Text(title, color = C.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(text, color = C.Dim, style = MaterialTheme.typography.bodyMedium)
}

// ---------------------------------------------------------------------------------------------
// Parsing guide
// ---------------------------------------------------------------------------------------------

private class Technique(val title: String, val about: String, val examples: List<String>)

private val TECHNIQUES = listOf(
    Technique(
        "Dates and times",
        "Say it the way you would to a person. Words, weekdays, calendar dates and \"in 45 mins\" all work.",
        listOf("Call mom tomorrow 6pm", "Taxes dec 15 5pm", "Stretch in 45 mins"),
    ),
    Technique(
        "Repeats",
        "Daily, weekly or monthly, on particular days, or counted from when you finish. Add \"for 2 weeks\" to stop it.",
        listOf(
            "Gym every mon wed fri 7am", "Pay rent every month on the 5th", "Book club every 2nd tuesday",
            "Report every last day of the month", "Pills daily for 2 weeks", "Backup every 3 days after completion",
        ),
    ),
    Technique(
        "Priority",
        "Exclamation marks, p1 to p3, or the word urgent. Medium and High show the priority card.",
        listOf("Submit the form !!!", "Renew passport !!", "Pay the bill urgent"),
    ),
    Technique(
        "Reminders",
        "Ask for exactly the reminder you want, right in the sentence.",
        listOf("Standup every weekday 9:30am remind 10 min before"),
    ),
    Technique(
        "How long it takes",
        "Say how long it will take. It becomes the focus session length and the block in your phone calendar.",
        listOf("Study for 45 min tomorrow 6pm", "Deep work for 2 hours", "Run for 30m at 6am"),
    ),
    Technique(
        "Notes",
        "Everything after // is kept as the note and left out of the title.",
        listOf("Taxes dec 15 5pm urgent // bring the forms"),
    ),
)

@Composable
fun ParsingGuidePage(is24: Boolean, back: () -> Unit) {
    val today = remember { LocalDate.now() }
    var text by remember { mutableStateOf("Call mom tomorrow 6pm !!") }
    SettingsPage("How it understands you", back) {
        Text(
            "Everything you type in the new task box is read like this, live. Colours show what was understood: amber for dates and times, green for repeats, red for priority, blue for reminders.",
            color = C.Dim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 6.dp),
        )
        SGroup(title = "Playground", footer = "Nothing you type here is saved.") {
            row { BodyRow {
                Box(Modifier.fillMaxWidth()) {
                    if (text.isEmpty()) Text("Type a task…", color = C.Faint, style = MaterialTheme.typography.titleLarge)
                    BasicTextField(
                        value = text, onValueChange = { text = it.take(160) },
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = C.Text),
                        cursorBrush = SolidColor(C.Accent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (text.isNotBlank()) ParseDemo(text, today, is24)
            } }
        }
        TECHNIQUES.forEach { t ->
            SGroup(title = t.title, footer = t.about) {
                t.examples.forEach { ex ->
                    row { BodyRow { ParseDemo(ex, today, is24) } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Help
// ---------------------------------------------------------------------------------------------

private val FAQ = listOf(
    "How do I add a task?" to "Tap New task and type it the way you would say it, for example \"call mom tomorrow 6pm !!\". The date, time, repeat and priority are filled in for you. Tap the microphone to say it instead, or More options for the full editor.",
    "How do the swipes work?" to "Pull a task to the right to complete it, in one motion. Pull it to the left and it opens on four buttons: Edit, Snooze, Focus and Delete. Tap the row or swipe back to close them. Completing and deleting can be undone for a few seconds.",
    "A reminder did not show. Why?" to "Open Permissions and reliability: it lists everything that can stop a reminder and has a button to fix each one. The usual cause is battery optimisation or, on some phones, a separate \"autostart\" switch.",
    "What is the priority card?" to "For Medium and High priority tasks, a card appears over whatever you are doing, with Done and Snooze. It only appears while the phone is on and unlocked. On the lock screen or with the screen off you get the notification instead, and its Done and Snooze buttons work without unlocking.",
    "How do repeating tasks work?" to "Say it in the sentence: \"every mon wed fri\", \"every month on the 5th\", \"every 3 days after completion\". When you complete one, the next is created. Repeating tasks show a streak, and the notification has a Skip button to jump to the next date.",
    "What is Focus?" to "A timer for deep work, with Pomodoro and freestyle modes. Link a task, pick a tag, and your sessions build up in Insights. The timer keeps running with the app closed.",
    "Can I add a task from another app?" to "Yes. Select text anywhere and choose Cadence from the menu, or use Share and pick Cadence. The add sheet opens with that text filled in.",
    "Is my data synced or uploaded?" to "No. There is no account and no server. Tasks live on this phone. Use Backup and restore to keep a copy, or to move to a new phone.",
    "How do I move to a new phone?" to "Export a backup on the old phone, copy the file across, then Import it on the new one. Tasks, focus history and settings all come with it.",
)

@Composable
fun HelpPage(back: () -> Unit, onReplayTutorial: () -> Unit) {
    val ctx = LocalContext.current
    SettingsPage("Help", back) {
        SGroup(title = "Questions") {
            FAQ.forEach { (q, a) -> row { Faq(q, a) } }
        }
        SGroup(title = "Still stuck?") {
            row { ActionRow("Replay the tutorial") { onReplayTutorial() } }
            row { NavRow("Ask on GitHub") { openUrl(ctx, "$REPO_URL/issues/new/choose") } }
        }
    }
}

@Composable
private fun Faq(q: String, a: String) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(q, color = C.Text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (open) Text(a, color = C.Dim, style = MaterialTheme.typography.bodyMedium)
    }
}
