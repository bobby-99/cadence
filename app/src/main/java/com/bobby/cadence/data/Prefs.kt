package com.bobby.cadence.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SortMode(val label: String) {
    SMART("Date & time"),
    PRIORITY("Priority"),
    NEWEST("Newest"),
    ALPHA("A to Z"),
}

data class AppSettings(
    /** time used for reminders of tasks that have a date but no time (minutes from midnight) */
    val defaultTimeMin: Int = 9 * 60,
    /** minutes before due for new tasks; -1 = no reminder */
    val defaultReminder: Int = 0,
    val snoozeMin: Int = 10,
    val weekStartMonday: Boolean = true,
    /** 0 = keep forever */
    val keepCompletedDays: Int = 30,
    val showCompleted: Boolean = true,
    val sort: SortMode = SortMode.SMART,
    val smartParsing: Boolean = true,
    val haptics: Boolean = true,
    /** full-screen card over any app / lock screen for Medium and High priority */
    val priorityOverlay: Boolean = true,
    /** 0 = Manrope, 1 = Google Sans, 2 = system */
    val font: Int = 0,
    val backupEnabled: Boolean = false,
    val backupFolderUri: String = "",
    val backupFolderName: String = "",
    /** 1 = daily, 7 = weekly, 30 = monthly */
    val backupEveryDays: Int = 7,
    val lastBackupAt: Long = 0L,
    val lastBackupMsg: String = "",
    val summaryEnabled: Boolean = false,
    val summaryTimeMin: Int = 8 * 60,
    /** show events from the phone calendar inside Cadence's calendar view */
    val showDeviceEvents: Boolean = true,
    /** phone calendar that tasks are saved to, -1 = the first writable one */
    val calendarId: Long = -1L,
    val calendarName: String = "",
    /** phone calendars hidden from Cadence's calendar view */
    val hiddenCalendars: Set<Long> = emptySet(),
    /** recently typed snooze lengths in minutes, newest first */
    val recentSnoozes: List<Int> = emptyList(),
    /** which list opens when the app starts: a ViewKind name */
    val startView: String = "ALL",
    // ---- focus ----
    val focusMin: Int = 25,
    val shortBreakMin: Int = 5,
    val longBreakMin: Int = 15,
    /** focus rounds before a long break */
    val cyclesUntilLong: Int = 4,
    val autoStartBreaks: Boolean = true,
    val autoStartFocus: Boolean = false,
    /** goal in minutes per day */
    val dailyGoalMin: Int = 120,
    val focusTags: List<String> = listOf("Study", "Work", "Reading", "Code"),
    val lastTag: String = "",
    val focusMode: Int = 0,
    val keepScreenOn: Boolean = true,
    /** freestyle: optional target in minutes, 0 = none */
    val freestyleGoalMin: Int = 0,
    /** the first-run tutorial has been shown (or skipped); not part of backups */
    val onboarded: Boolean = false,
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("cadence_prefs", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = flow

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            defaultTimeMin = sp.getInt("defaultTimeMin", d.defaultTimeMin),
            defaultReminder = sp.getInt("defaultReminder", d.defaultReminder),
            snoozeMin = sp.getInt("snoozeMin", d.snoozeMin),
            weekStartMonday = sp.getBoolean("weekStartMonday", d.weekStartMonday),
            keepCompletedDays = sp.getInt("keepCompletedDays", d.keepCompletedDays),
            showCompleted = sp.getBoolean("showCompleted", d.showCompleted),
            sort = SortMode.entries.getOrNull(sp.getInt("sort", 0)) ?: SortMode.SMART,
            smartParsing = sp.getBoolean("smartParsing", d.smartParsing),
            haptics = sp.getBoolean("haptics", d.haptics),
            priorityOverlay = sp.getBoolean("priorityOverlay", d.priorityOverlay),
            font = sp.getInt("font", d.font),
            backupEnabled = sp.getBoolean("backupEnabled", d.backupEnabled),
            backupFolderUri = sp.getString("backupFolderUri", d.backupFolderUri) ?: "",
            backupFolderName = sp.getString("backupFolderName", d.backupFolderName) ?: "",
            backupEveryDays = sp.getInt("backupEveryDays", d.backupEveryDays),
            lastBackupAt = sp.getLong("lastBackupAt", d.lastBackupAt),
            lastBackupMsg = sp.getString("lastBackupMsg", d.lastBackupMsg) ?: "",
            summaryEnabled = sp.getBoolean("summaryEnabled", d.summaryEnabled),
            summaryTimeMin = sp.getInt("summaryTimeMin", d.summaryTimeMin),
            showDeviceEvents = sp.getBoolean("showDeviceEvents", d.showDeviceEvents),
            calendarId = sp.getLong("calendarId", d.calendarId),
            calendarName = sp.getString("calendarName", d.calendarName) ?: "",
            startView = sp.getString("startView", d.startView) ?: "ALL",
            focusMin = sp.getInt("focusMin", d.focusMin),
            shortBreakMin = sp.getInt("shortBreakMin", d.shortBreakMin),
            longBreakMin = sp.getInt("longBreakMin", d.longBreakMin),
            cyclesUntilLong = sp.getInt("cyclesUntilLong", d.cyclesUntilLong),
            autoStartBreaks = sp.getBoolean("autoStartBreaks", d.autoStartBreaks),
            autoStartFocus = sp.getBoolean("autoStartFocus", d.autoStartFocus),
            dailyGoalMin = sp.getInt("dailyGoalMin", d.dailyGoalMin),
            focusTags = if (sp.contains("focusTags")) (sp.getString("focusTags", "") ?: "").split("|").filter { it.isNotBlank() } else d.focusTags,
            lastTag = sp.getString("lastTag", "") ?: "",
            focusMode = sp.getInt("focusMode", d.focusMode),
            keepScreenOn = sp.getBoolean("keepScreenOn", d.keepScreenOn),
            freestyleGoalMin = sp.getInt("freestyleGoalMin", d.freestyleGoalMin),
            onboarded = sp.getBoolean("onboarded", d.onboarded),
            recentSnoozes = (sp.getString("recentSnoozes", "") ?: "").split(",").mapNotNull { it.toIntOrNull() },
            hiddenCalendars = (sp.getString("hiddenCalendars", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.toSet(),
        )
    }

    fun update(block: (AppSettings) -> AppSettings) {
        val n = block(flow.value)
        flow.value = n
        sp.edit()
            .putInt("defaultTimeMin", n.defaultTimeMin)
            .putInt("defaultReminder", n.defaultReminder)
            .putInt("snoozeMin", n.snoozeMin)
            .putBoolean("weekStartMonday", n.weekStartMonday)
            .putInt("keepCompletedDays", n.keepCompletedDays)
            .putBoolean("showCompleted", n.showCompleted)
            .putInt("sort", n.sort.ordinal)
            .putBoolean("smartParsing", n.smartParsing)
            .putBoolean("haptics", n.haptics)
            .putBoolean("priorityOverlay", n.priorityOverlay)
            .putInt("font", n.font)
            .putBoolean("backupEnabled", n.backupEnabled)
            .putString("backupFolderUri", n.backupFolderUri)
            .putString("backupFolderName", n.backupFolderName)
            .putInt("backupEveryDays", n.backupEveryDays)
            .putLong("lastBackupAt", n.lastBackupAt)
            .putString("lastBackupMsg", n.lastBackupMsg)
            .putBoolean("summaryEnabled", n.summaryEnabled)
            .putInt("summaryTimeMin", n.summaryTimeMin)
            .putBoolean("showDeviceEvents", n.showDeviceEvents)
            .putLong("calendarId", n.calendarId)
            .putString("calendarName", n.calendarName)
            .putString("startView", n.startView)
            .putInt("focusMin", n.focusMin)
            .putInt("shortBreakMin", n.shortBreakMin)
            .putInt("longBreakMin", n.longBreakMin)
            .putInt("cyclesUntilLong", n.cyclesUntilLong)
            .putBoolean("autoStartBreaks", n.autoStartBreaks)
            .putBoolean("autoStartFocus", n.autoStartFocus)
            .putInt("dailyGoalMin", n.dailyGoalMin)
            .putString("focusTags", n.focusTags.joinToString("|"))
            .putString("lastTag", n.lastTag)
            .putInt("focusMode", n.focusMode)
            .putBoolean("keepScreenOn", n.keepScreenOn)
            .putInt("freestyleGoalMin", n.freestyleGoalMin)
            .putBoolean("onboarded", n.onboarded)
            .putString("recentSnoozes", n.recentSnoozes.joinToString(","))
            .putString("hiddenCalendars", n.hiddenCalendars.joinToString(","))
            .apply()
    }
}
