package com.bobby.cadence.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobby.cadence.alarm.Notifier
import com.bobby.cadence.data.AppSettings
import com.bobby.cadence.data.BackupFolder
import com.bobby.cadence.data.CalendarBridge
import com.bobby.cadence.data.Fmt
import com.bobby.cadence.data.Task
import java.time.LocalDate
import java.time.LocalTime

/** The pages of the settings area. HUB is the list you land on. */
enum class SPage { HUB, APPEARANCE, TASKS, REMINDERS, CALENDAR, DATA, PERMISSIONS, ABOUT, PARSING, HELP }

@Composable
fun SettingsScreen(
    settings: AppSettings,
    is24: Boolean,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onClose: () -> Unit,
    onExport: ((String) -> Unit) -> Unit,
    onImport: (String, (Int) -> Unit) -> Unit,
    onClearCompleted: ((Int) -> Unit) -> Unit,
    onBackupNow: ((Boolean) -> Unit) -> Unit,
    onReplayTutorial: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SPage.HUB) }
    // sub-pages go back one level; the hub closes settings (HomeScreen handles that back press)
    BackHandler(enabled = page != SPage.HUB) {
        page = when (page) {
            SPage.PARSING, SPage.HELP -> SPage.ABOUT
            else -> SPage.HUB
        }
    }
    val perms = rememberPerms()

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState != SPage.HUB && initialState == SPage.HUB || (targetState in listOf(SPage.PARSING, SPage.HELP) && initialState == SPage.ABOUT)) {
                (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(240)) { -it / 6 } + fadeOut(tween(140)))
            } else {
                (slideInHorizontally(tween(240)) { -it / 6 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(140)))
            }
        },
        label = "settingsPage",
    ) { p ->
        val back = { page = if (p == SPage.PARSING || p == SPage.HELP) SPage.ABOUT else SPage.HUB }
        when (p) {
            SPage.HUB -> HubPage(settings, perms, onClose) { page = it }
            SPage.APPEARANCE -> AppearancePage(settings, onChange, back)
            SPage.TASKS -> TasksPage(settings, onChange, onClearCompleted, back)
            SPage.REMINDERS -> RemindersPage(settings, is24, onChange, perms, back)
            SPage.CALENDAR -> CalendarPage(settings, onChange, perms, back)
            SPage.DATA -> DataPage(settings, onChange, onExport, onImport, onBackupNow, back)
            SPage.PERMISSIONS -> PermissionsPage(settings, perms, back)
            SPage.ABOUT -> AboutPage(back, onOpen = { page = it }, onReplayTutorial = onReplayTutorial)
            SPage.PARSING -> ParsingGuidePage(is24, back)
            SPage.HELP -> HelpPage(back, onReplayTutorial)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hub
// ---------------------------------------------------------------------------------------------

@Composable
private fun HubPage(settings: AppSettings, perms: Perms, onClose: () -> Unit, go: (SPage) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 20.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.Default.Close, "Close settings", onClose)
        }
        Text(
            "Settings", color = C.Text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 16.dp),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SGroup {
                row { NavRow("Appearance", fontName(settings.font), icon = Icons.Default.Create) { go(SPage.APPEARANCE) } }
                row { NavRow("Tasks", startViewName(settings.startView), icon = Icons.Default.CheckCircle) { go(SPage.TASKS) } }
                row { NavRow("Reminders", reminderName(settings.defaultReminder), icon = Icons.Default.Notifications) { go(SPage.REMINDERS) } }
            }
            SGroup {
                row { NavRow("Phone calendar", if (perms.calendar) (if (settings.showDeviceEvents) "Shown" else "Hidden") else "Off", icon = Icons.Default.DateRange) { go(SPage.CALENDAR) } }
                row {
                    NavRow(
                        "Backup and restore",
                        if (settings.backupEnabled) "Automatic" else "Manual",
                        icon = Icons.Default.Share,
                    ) { go(SPage.DATA) }
                }
            }
            SGroup {
                row {
                    NavRow(
                        "Permissions and reliability",
                        if (perms.needAttention == 0) "All set" else "${perms.needAttention} to fix",
                        icon = Icons.Default.Lock,
                        valueColor = if (perms.needAttention == 0) C.Done else C.Medium,
                    ) { go(SPage.PERMISSIONS) }
                }
            }
            SGroup {
                row { NavRow("Help", iconText = "?") { go(SPage.HELP) } }
                row { NavRow("About Cadence", appVersion(ctx), icon = Icons.Default.Info) { go(SPage.ABOUT) } }
            }
        }
    }
}

private fun fontName(f: Int) = when (f) { 0 -> "Manrope"; 1 -> "Google Sans"; else -> "System" }
private fun startViewName(k: String) = START_VIEWS.firstOrNull { it.first == k }?.second ?: "All tasks"
private fun reminderName(m: Int) = REMINDERS.firstOrNull { it.first == m }?.second ?: "At time"

private val START_VIEWS = listOf("ALL" to "All tasks", "TODAY" to "Today", "UPCOMING" to "Upcoming", "OVERDUE" to "Overdue", "REPEATING" to "Repeating")
private val REMINDERS = listOf(-1 to "None", 0 to "At time", 5 to "5 min before", 10 to "10 min before", 30 to "30 min before", 60 to "1 hour before", 1440 to "1 day before")

// ---------------------------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------------------------

@Composable
private fun AppearancePage(s: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, back: () -> Unit) {
    SettingsPage("Appearance", back) {
        SGroup(footer = "The typeface applies everywhere, instantly.") {
            row { SegRow("Typeface", options = listOf(0 to "Manrope", 1 to "Google Sans", 2 to "System"), selected = s.font) { v -> onChange { it.copy(font = v) } } }
        }
        SGroup {
            row { SegRow("Week starts on", options = listOf(true to "Monday", false to "Sunday"), selected = s.weekStartMonday) { v -> onChange { it.copy(weekStartMonday = v) } } }
            row { SwitchRow("Haptics", "A small tick when you complete a task", s.haptics) { v -> onChange { it.copy(haptics = v) } } }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------------------------

@Composable
private fun TasksPage(
    s: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit,
    onClearCompleted: ((Int) -> Unit) -> Unit, back: () -> Unit,
) {
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    SettingsPage("Tasks", back) {
        SGroup {
            row { ChoiceRow("Open the app on", "The list you see first", START_VIEWS, s.startView) { v -> onChange { it.copy(startView = v) } } }
            row {
                SwitchRow(
                    "Smart parsing", "Understands \"call mom tomorrow 6pm !!\" while you type",
                    s.smartParsing,
                ) { v -> onChange { it.copy(smartParsing = v) } }
            }
        }
        SGroup(title = "Completed tasks") {
            row {
                ChoiceRow(
                    "Remove automatically", "Old completed tasks are cleared after this long",
                    listOf(7 to "After 7 days", 30 to "After 30 days", 90 to "After 90 days", 0 to "Never"), s.keepCompletedDays,
                ) { v -> onChange { it.copy(keepCompletedDays = v) } }
            }
            row { ActionRow("Delete all completed tasks", "Open tasks are not touched", C.Danger) { confirmClear = true } }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = C.SurfaceHi,
            title = { Text("Delete completed tasks?", color = C.Text) },
            text = { Text("This removes every completed task. Open tasks are not touched.", color = C.Dim) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearCompleted { n -> Toast.makeText(ctx, "Deleted $n tasks", Toast.LENGTH_SHORT).show() }
                }) { Text("Delete", color = C.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = C.Dim) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Reminders
// ---------------------------------------------------------------------------------------------

@Composable
private fun RemindersPage(s: AppSettings, is24: Boolean, onChange: ((AppSettings) -> AppSettings) -> Unit, perms: Perms, back: () -> Unit) {
    val ctx = LocalContext.current
    var pickTime by remember { mutableStateOf(false) }
    var pickSummary by remember { mutableStateOf(false) }
    SettingsPage("Reminders", back) {
        SGroup(title = "Defaults") {
            row { ChoiceRow("Reminder for new tasks", "Added to every task that has a date", REMINDERS, s.defaultReminder) { v -> onChange { it.copy(defaultReminder = v) } } }
            row { NavRow("Time for all-day tasks", Fmt.minutes(s.defaultTimeMin, is24), "Their reminders fire at this time") { pickTime = true } }
            row {
                ChoiceRow(
                    "Snooze length", "Used by the Snooze button on the notification",
                    listOf(5, 10, 15, 30, 60).map { it to Fmt.offset(it) }, s.snoozeMin,
                ) { v -> onChange { it.copy(snoozeMin = v) } }
            }
        }
        SGroup(
            title = "While you use the phone",
            footer = "On the lock screen, or with the screen off, you always get the notification only.",
        ) {
            row {
                SwitchRow(
                    "Priority card", "Medium and High priority reminders also show a card over any app",
                    s.priorityOverlay,
                ) { v -> onChange { it.copy(priorityOverlay = v) } }
            }
            if (s.priorityOverlay && !perms.overlay) {
                row { StatusRow("Display over other apps", false, "", "Needed for the card to appear", "Allow") { perms.askOverlay() } }
            }
        }
        SGroup(title = "Morning summary", footer = "One quiet notification with what is due today. Skipped on days with nothing due.") {
            row { SwitchRow("Daily summary", null, s.summaryEnabled) { v -> onChange { it.copy(summaryEnabled = v) } } }
            if (s.summaryEnabled) row { NavRow("Time", Fmt.minutes(s.summaryTimeMin, is24)) { pickSummary = true } }
        }
        SGroup(title = "Try it") {
            row {
                ActionRow("Send a test notification", "A real reminder, right now") {
                    val n = LocalTime.now()
                    Notifier.show(
                        ctx,
                        Task(id = 987654, title = "Test reminder", notes = "If you can see this, reminders work.", dueDate = LocalDate.now().toEpochDay(), dueTime = n.hour * 60 + n.minute),
                        0, s.snoozeMin,
                    )
                }
            }
            row {
                ActionRow("Show a test priority card", "How Medium and High priority reminders look") {
                    val n = LocalTime.now()
                    val test = Task(
                        id = 987655, title = "Test: high priority task", notes = "This is how Medium and High priority reminders appear.",
                        priority = 3, dueDate = LocalDate.now().toEpochDay(), dueTime = n.hour * 60 + n.minute,
                    )
                    if (!com.bobby.cadence.alarm.OverlayService.show(ctx, test)) {
                        Toast.makeText(ctx, "Allow \"Display over other apps\" first", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    if (pickTime) TimePickDialog(LocalTime.of(s.defaultTimeMin / 60, s.defaultTimeMin % 60), is24, onDismiss = { pickTime = false }) { t ->
        onChange { it.copy(defaultTimeMin = t.hour * 60 + t.minute) }
    }
    if (pickSummary) TimePickDialog(LocalTime.of(s.summaryTimeMin / 60, s.summaryTimeMin % 60), is24, onDismiss = { pickSummary = false }) { t ->
        onChange { it.copy(summaryTimeMin = t.hour * 60 + t.minute) }
    }
}

// ---------------------------------------------------------------------------------------------
// Phone calendar
// ---------------------------------------------------------------------------------------------

@Composable
private fun CalendarPage(s: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, perms: Perms, back: () -> Unit) {
    val ctx = LocalContext.current
    SettingsPage("Phone calendar", back) {
        SGroup(footer = "Tasks added from Cadence's calendar view are saved to the phone calendar. Normal adds are not, unless you choose it in the editor.") {
            row {
                StatusRow(
                    "Calendar access", perms.calendar,
                    "Cadence can read and add events", "Needed to show your events and add tasks", "Allow",
                ) { perms.askCalendar() }
            }
            if (perms.calendar) row { SwitchRow("Show calendar events", "Appear in Cadence's calendar view, read only", s.showDeviceEvents) { v -> onChange { it.copy(showDeviceEvents = v) } } }
        }
        if (perms.calendar) {
            val all = remember(perms.calendar) { CalendarBridge.allCalendars(ctx) }
            if (s.showDeviceEvents && all.isNotEmpty()) {
                all.groupBy { it.account }.forEach { (acct, list) ->
                    SGroup(title = if (acct.isBlank()) "On this phone" else acct) {
                        list.forEach { c ->
                            row {
                                SwitchRow(c.name, null, c.id !in s.hiddenCalendars) { _ ->
                                    onChange { it.copy(hiddenCalendars = if (c.id in it.hiddenCalendars) it.hiddenCalendars - c.id else it.hiddenCalendars + c.id) }
                                }
                            }
                        }
                    }
                }
            }
            val writable = remember(perms.calendar) { CalendarBridge.writableCalendars(ctx) }
            if (writable.isNotEmpty()) {
                val chosen = writable.firstOrNull { it.id == s.calendarId } ?: writable.first()
                SGroup {
                    row {
                        ChoiceRow(
                            "Save tasks to", "Calendar that receives tasks you add",
                            writable.map { it.id to (it.name + if (it.account.isNotBlank() && it.account != it.name) " · " + it.account.substringBefore('@') else "") },
                            chosen.id,
                        ) { id -> onChange { it.copy(calendarId = id, calendarName = writable.first { c -> c.id == id }.name) } }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Backup and restore
// ---------------------------------------------------------------------------------------------

@Composable
private fun DataPage(
    s: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit,
    onExport: ((String) -> Unit) -> Unit, onImport: (String, (Int) -> Unit) -> Unit,
    onBackupNow: ((Boolean) -> Unit) -> Unit, back: () -> Unit,
) {
    val ctx = LocalContext.current
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExport { json ->
            Thread {
                val ok = try {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                    true
                } catch (e: Exception) {
                    false
                }
                (ctx as? Activity)?.runOnUiThread {
                    Toast.makeText(ctx, if (ok) "Backup saved" else "Could not save backup", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: Exception) {
            }
            val folder = BackupFolder.folderName(ctx, uri)
            onChange { it.copy(backupEnabled = true, backupFolderUri = uri.toString(), backupFolderName = folder, lastBackupMsg = "") }
            onBackupNow { ok -> Toast.makeText(ctx, if (ok) "First backup saved" else "Could not write to that folder", Toast.LENGTH_SHORT).show() }
        }
    }

    val syncScope = rememberCoroutineScope()
    val syncApp = ctx.applicationContext as com.bobby.cadence.CadenceApp
    val syncFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: Exception) {
            }
            val folder = BackupFolder.folderName(ctx, uri)
            onChange { it.copy(syncEnabled = true, syncFolderUri = uri.toString(), syncFolderName = folder, lastSyncMsg = "") }
            syncApp.syncNow()
        }
    }

    SettingsPage("Backup and restore", back) {
        SGroup(
            title = "Sync with a computer",
            footer = "Cadence for Windows reads and writes one file, cadence-sync.json, in a folder that both devices can see (Syncthing, OneDrive, Google Drive and so on). No account and no server. Pick the same folder on both.",
        ) {
            row {
                SwitchRow("Sync through a folder", null, s.syncEnabled) { v ->
                    if (v && s.syncFolderUri.isBlank()) syncFolderLauncher.launch(null) else onChange { it.copy(syncEnabled = v) }
                }
            }
            if (s.syncEnabled) {
                row { NavRow("Folder", s.syncFolderName.ifBlank { "Choose" }) { syncFolderLauncher.launch(null) } }
                row {
                    val last = if (s.lastSyncAt > 0L) java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a").format(
                        java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(s.lastSyncAt), java.time.ZoneId.systemDefault()),
                    ) else "Never"
                    InfoRow("Last sync", last)
                }
                if (s.lastSyncMsg.isNotBlank()) row { InfoRow("Problem", s.lastSyncMsg, C.Danger) }
                row {
                    ActionRow("Sync now") {
                        syncScope.launch {
                            val ok = com.bobby.cadence.data.SyncRunner.run(syncApp)
                            Toast.makeText(ctx, if (ok) "Synced" else "Sync failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        SGroup(
            title = "Backup file",
            footer = "A backup holds your tasks, focus history and settings. It does not hold the backup folder or phone-calendar choices, which belong to this phone.",
        ) {
            row { ActionRow("Export to a file", "Save a copy anywhere you like") { exportLauncher.launch("cadence-backup.json") } }
            row {
                ActionRow("Import from a file", "Replaces everything on this phone with the backup") {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                }
            }
        }
        SGroup(title = "Automatic backup", footer = "Keeps the newest 5. A folder synced by Drive or similar also protects you if the phone is lost.") {
            row {
                SwitchRow("Back up automatically", null, s.backupEnabled) { v ->
                    if (v && s.backupFolderUri.isBlank()) folderLauncher.launch(null) else onChange { it.copy(backupEnabled = v) }
                }
            }
            if (s.backupEnabled) {
                row { NavRow("Folder", s.backupFolderName.ifBlank { "Choose" }) { folderLauncher.launch(null) } }
                row {
                    ChoiceRow("How often", null, listOf(1 to "Daily", 7 to "Weekly", 30 to "Monthly"), s.backupEveryDays) { v -> onChange { it.copy(backupEveryDays = v) } }
                }
                row {
                    val last = if (s.lastBackupAt > 0L) java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a").format(
                        java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(s.lastBackupAt), java.time.ZoneId.systemDefault()),
                    ) else "Never"
                    InfoRow("Last backup", last)
                }
                if (s.lastBackupMsg.isNotBlank()) row { InfoRow("Problem", s.lastBackupMsg, C.Danger) }
                row {
                    ActionRow("Back up now") {
                        onBackupNow { ok -> Toast.makeText(ctx, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        }
    }

    val uri = pendingImport
    if (uri != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = C.SurfaceHi,
            title = { Text("Replace everything?", color = C.Text) },
            text = { Text("Importing replaces your tasks, focus history and settings with the ones in the backup.", color = C.Dim) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    val text = try {
                        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    } catch (e: Exception) {
                        null
                    }
                    if (text == null) Toast.makeText(ctx, "Could not read that file", Toast.LENGTH_SHORT).show()
                    else onImport(text) { n ->
                        Toast.makeText(
                            ctx,
                            when {
                                n == com.bobby.cadence.data.TaskRepository.IMPORT_TOO_NEW -> "This backup is from a newer version of Cadence. Update the app first."
                                n < 0 -> "That is not a Cadence backup"
                                else -> "Imported $n tasks"
                            },
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }) { Text("Replace", color = C.Danger) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel", color = C.Dim) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Permissions and reliability
// ---------------------------------------------------------------------------------------------

@Composable
private fun PermissionsPage(s: AppSettings, p: Perms, back: () -> Unit) {
    SettingsPage("Permissions", back) {
        SGroup(
            title = "Needed for reliable reminders",
            footer = "Each one is optional, but together they make sure a reminder shows on time, every time.",
        ) {
            row { StatusRow("Notifications", p.notifications, "Allowed", "Blocked, reminders will not show", "Allow") { p.askNotifications() } }
            row {
                StatusRow(
                    "Reminder pop-ups", p.channel, "The reminder channel is on and set to pop up",
                    "The reminder channel is off or turned down in system settings", "Fix",
                ) { p.openNotificationSettings() }
            }
            row {
                StatusRow(
                    "Unrestricted battery", p.battery, "Cadence can run in the background freely",
                    "Stops the system from delaying reminders on some phones", "Allow",
                ) { p.askBattery() }
            }
            row {
                StatusRow(
                    "Display over other apps", p.overlay, "The priority card can appear over any app",
                    "Needed for the card that shows over other apps", "Allow",
                ) { p.askOverlay() }
            }
        }
        SGroup(title = "Optional") {
            row {
                StatusRow(
                    "Phone calendar", p.calendar, "Cadence can read and add events",
                    "Needed to show your events and add tasks to the calendar", "Allow",
                ) { p.askCalendar() }
            }
        }
        SGroup(
            title = "Still not on time?",
            footer = "Some phones (Xiaomi, Oppo, Vivo, Realme, OnePlus, Samsung) have an extra \"autostart\" or \"battery\" switch of their own. Open the app's system page and look for it. Swiping Cadence away from recents is fine; Force stop is not, because the system then cancels every alarm until you open the app again.",
        ) {
            row { ActionRow("Open Cadence in system settings") { p.openAppSettings() } }
            row { ActionRow("Notification settings") { p.openNotificationSettings() } }
        }
    }
}

internal fun appVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}

