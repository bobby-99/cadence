package com.bobby.cadence.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The backup file format.
 *
 * Rules that keep old and new backups compatible:
 *  - Every field is optional when reading; a missing field keeps the app's default (or current value).
 *  - Unknown fields are ignored.
 *  - Adding a field never needs a version bump. Bump [VERSION] only when the meaning of existing data changes.
 *  - A file written by a newer version of the app than this one is refused instead of half-read.
 *
 * Version 1 held tasks only. Version 2 adds focus sessions and portable settings.
 */
object BackupManager {
    const val VERSION = 2

    /** What a backup file holds. [sessions] and [settings] are null when the file predates them. */
    class Backup(val tasks: List<Task>, val sessions: List<FocusSession>?, val settings: JSONObject?)

    sealed class Parsed {
        class Ok(val backup: Backup) : Parsed()
        object Invalid : Parsed()
        object TooNew : Parsed()
    }

    fun toJson(tasks: List<Task>, sessions: List<FocusSession>, settings: AppSettings): String {
        val ta = JSONArray()
        for (t in tasks) ta.put(taskToJson(t))
        val fa = JSONArray()
        for (s in sessions) fa.put(sessionToJson(s))
        return JSONObject()
            .put("app", "cadence")
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("tasks", ta)
            .put("focusSessions", fa)
            .put("settings", settingsToJson(settings))
            .toString(2)
    }

    fun parse(text: String): Parsed = try {
        val root = JSONObject(text)
        if (root.optString("app") != "cadence") Parsed.Invalid
        else if (root.optInt("version", 1) > VERSION) Parsed.TooNew
        else {
            val tasks = ArrayList<Task>()
            val arr = root.getJSONArray("tasks")
            for (i in 0 until arr.length()) tasks.add(taskFromJson(arr.getJSONObject(i)))
            val sessions = root.optJSONArray("focusSessions")?.let { fa ->
                (0 until fa.length()).map { sessionFromJson(fa.getJSONObject(it)) }
            }
            Parsed.Ok(Backup(tasks, sessions, root.optJSONObject("settings")))
        }
    } catch (e: Exception) {
        Parsed.Invalid
    }

    // ---- tasks ----

    private fun taskToJson(t: Task) = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("notes", t.notes)
        t.dueDate?.let { put("dueDate", it) }
        t.dueTime?.let { put("dueTime", it) }
        put("priority", t.priority)
        put("completed", t.completed)
        t.completedAt?.let { put("completedAt", it) }
        put("createdAt", t.createdAt)
        put("reminders", t.reminders)
        t.recurrence?.let { put("recurrence", it) }
        put("occurrence", t.occurrence)
        put("seriesId", t.seriesId)
        t.durationMin?.let { put("durationMin", it) }
        if (t.uuid.isNotEmpty()) put("uuid", t.uuid)
        put("updatedAt", t.updatedAt)
    }

    private fun taskFromJson(o: JSONObject) = Task(
        id = o.optLong("id", 0),
        title = o.optString("title", ""),
        notes = o.optString("notes", ""),
        dueDate = if (o.has("dueDate")) o.getLong("dueDate") else null,
        dueTime = if (o.has("dueTime")) o.getInt("dueTime") else null,
        priority = o.optInt("priority", 0).coerceIn(0, 3),
        completed = o.optBoolean("completed", false),
        completedAt = if (o.has("completedAt")) o.getLong("completedAt") else null,
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        reminders = o.optString("reminders", ""),
        recurrence = if (o.has("recurrence")) o.getString("recurrence") else null,
        occurrence = o.optInt("occurrence", 1),
        seriesId = o.optLong("seriesId", 0),
        durationMin = if (o.has("durationMin")) o.getInt("durationMin") else null,
        uuid = o.optString("uuid", ""),
        updatedAt = o.optLong("updatedAt", 0L),
    )

    // ---- focus sessions ----

    private fun sessionToJson(s: FocusSession) = JSONObject().apply {
        put("id", s.id)
        put("startedAt", s.startedAt)
        put("seconds", s.seconds)
        put("plannedSeconds", s.plannedSeconds)
        put("mode", s.mode)
        put("tag", s.tag)
        put("taskId", s.taskId)
        put("taskTitle", s.taskTitle)
        put("completed", s.completed)
    }

    private fun sessionFromJson(o: JSONObject) = FocusSession(
        id = o.optLong("id", 0),
        startedAt = o.optLong("startedAt", 0),
        seconds = o.optInt("seconds", 0),
        plannedSeconds = o.optInt("plannedSeconds", 0),
        mode = o.optInt("mode", 0),
        tag = o.optString("tag", ""),
        taskId = o.optLong("taskId", 0),
        taskTitle = o.optString("taskTitle", ""),
        completed = o.optBoolean("completed", true),
    )

    // ---- settings ----
    // Left out on purpose because they only make sense on the phone that made them:
    // backup folder and its status, the phone-calendar choices (calendar ids differ per phone)
    // and the "tutorial seen" flag.

    private fun settingsToJson(s: AppSettings) = JSONObject().apply {
        put("defaultTimeMin", s.defaultTimeMin)
        put("defaultReminder", s.defaultReminder)
        put("snoozeMin", s.snoozeMin)
        put("weekStartMonday", s.weekStartMonday)
        put("keepCompletedDays", s.keepCompletedDays)
        put("showCompleted", s.showCompleted)
        put("sort", s.sort.name)
        put("smartParsing", s.smartParsing)
        put("haptics", s.haptics)
        put("priorityOverlay", s.priorityOverlay)
        put("font", s.font)
        put("backupEveryDays", s.backupEveryDays)
        put("summaryEnabled", s.summaryEnabled)
        put("summaryTimeMin", s.summaryTimeMin)
        put("showDeviceEvents", s.showDeviceEvents)
        put("recentSnoozes", JSONArray(s.recentSnoozes))
        put("startView", s.startView)
        put("focusMin", s.focusMin)
        put("shortBreakMin", s.shortBreakMin)
        put("longBreakMin", s.longBreakMin)
        put("cyclesUntilLong", s.cyclesUntilLong)
        put("autoStartBreaks", s.autoStartBreaks)
        put("autoStartFocus", s.autoStartFocus)
        put("dailyGoalMin", s.dailyGoalMin)
        put("focusTags", JSONArray(s.focusTags))
        put("lastTag", s.lastTag)
        put("focusMode", s.focusMode)
        put("keepScreenOn", s.keepScreenOn)
        put("freestyleGoalMin", s.freestyleGoalMin)
    }

    /** Applies the saved settings on top of [cur]. Anything missing from the file keeps its current value. */
    fun applySettings(cur: AppSettings, o: JSONObject): AppSettings {
        fun ints(key: String, fallback: List<Int>): List<Int> =
            o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: fallback
        fun strings(key: String, fallback: List<String>): List<String> =
            o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: fallback
        return cur.copy(
            defaultTimeMin = o.optInt("defaultTimeMin", cur.defaultTimeMin).coerceIn(0, 1439),
            defaultReminder = o.optInt("defaultReminder", cur.defaultReminder),
            snoozeMin = o.optInt("snoozeMin", cur.snoozeMin).coerceAtLeast(1),
            weekStartMonday = o.optBoolean("weekStartMonday", cur.weekStartMonday),
            keepCompletedDays = o.optInt("keepCompletedDays", cur.keepCompletedDays).coerceAtLeast(0),
            showCompleted = o.optBoolean("showCompleted", cur.showCompleted),
            sort = SortMode.entries.firstOrNull { it.name == o.optString("sort") } ?: cur.sort,
            smartParsing = o.optBoolean("smartParsing", cur.smartParsing),
            haptics = o.optBoolean("haptics", cur.haptics),
            priorityOverlay = o.optBoolean("priorityOverlay", cur.priorityOverlay),
            font = o.optInt("font", cur.font).coerceIn(0, 2),
            backupEveryDays = o.optInt("backupEveryDays", cur.backupEveryDays).coerceAtLeast(1),
            summaryEnabled = o.optBoolean("summaryEnabled", cur.summaryEnabled),
            summaryTimeMin = o.optInt("summaryTimeMin", cur.summaryTimeMin).coerceIn(0, 1439),
            showDeviceEvents = o.optBoolean("showDeviceEvents", cur.showDeviceEvents),
            recentSnoozes = ints("recentSnoozes", cur.recentSnoozes),
            startView = o.optString("startView", cur.startView),
            focusMin = o.optInt("focusMin", cur.focusMin).coerceAtLeast(1),
            shortBreakMin = o.optInt("shortBreakMin", cur.shortBreakMin).coerceAtLeast(1),
            longBreakMin = o.optInt("longBreakMin", cur.longBreakMin).coerceAtLeast(1),
            cyclesUntilLong = o.optInt("cyclesUntilLong", cur.cyclesUntilLong).coerceAtLeast(1),
            autoStartBreaks = o.optBoolean("autoStartBreaks", cur.autoStartBreaks),
            autoStartFocus = o.optBoolean("autoStartFocus", cur.autoStartFocus),
            dailyGoalMin = o.optInt("dailyGoalMin", cur.dailyGoalMin).coerceAtLeast(0),
            focusTags = strings("focusTags", cur.focusTags),
            lastTag = o.optString("lastTag", cur.lastTag),
            focusMode = o.optInt("focusMode", cur.focusMode).coerceIn(0, 1),
            keepScreenOn = o.optBoolean("keepScreenOn", cur.keepScreenOn),
            freestyleGoalMin = o.optInt("freestyleGoalMin", cur.freestyleGoalMin).coerceAtLeast(0),
        )
    }
}
