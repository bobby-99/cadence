package com.bobby.cadence.data

import org.json.JSONArray
import org.json.JSONObject

/** One task as it travels between devices. Device-only things (snooze, calendar event id) are left out. */
data class SyncTask(
    val uuid: String,
    val title: String,
    val notes: String,
    val dueDate: Long?,
    val dueTime: Int?,
    val priority: Int,
    val completed: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val reminders: String,
    val recurrence: String?,
    val occurrence: Int,
    /** uuid of the first task of a repeating series, null if it is not part of one */
    val series: String?,
    val durationMin: Int?,
    val updatedAt: Long,
)

/** Everything one device knows: live tasks by uuid and deletions (uuid to when) by uuid. */
data class SyncDoc(val tasks: Map<String, SyncTask>, val deleted: Map<String, Long>)

/** The shared file. The format is written down in docs/SYNC.md. */
object SyncFormat {
    const val NAME = "cadence-sync"
    const val VERSION = 1
    const val FILE = "cadence-sync.json"

    sealed interface Parsed {
        data class Ok(val doc: SyncDoc) : Parsed
        data object Invalid : Parsed
        data object TooNew : Parsed
    }

    fun toJson(doc: SyncDoc, device: String, now: Long): String = JSONObject().apply {
        put("format", NAME)
        put("version", VERSION)
        put("device", device)
        put("writtenAt", now)
        put("tasks", JSONArray().also { arr ->
            for (t in doc.tasks.values.sortedBy { it.uuid }) arr.put(JSONObject().apply {
                put("uuid", t.uuid)
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
                t.series?.let { put("series", it) }
                t.durationMin?.let { put("durationMin", it) }
                put("updatedAt", t.updatedAt)
            })
        })
        put("deleted", JSONArray().also { arr ->
            for ((u, at) in doc.deleted.entries.sortedBy { it.key }) arr.put(JSONObject().put("uuid", u).put("at", at))
        })
    }.toString()

    fun parse(text: String): Parsed = try {
        val o = JSONObject(text)
        if (o.optString("format") != NAME) Parsed.Invalid
        else if (o.optInt("version", 0) > VERSION) Parsed.TooNew
        else {
            val tasks = LinkedHashMap<String, SyncTask>()
            val arr = o.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                val uuid = t.getString("uuid")
                if (uuid.isBlank()) continue
                tasks[uuid] = SyncTask(
                    uuid = uuid,
                    title = t.optString("title", ""),
                    notes = t.optString("notes", ""),
                    dueDate = if (t.has("dueDate") && !t.isNull("dueDate")) t.getLong("dueDate") else null,
                    dueTime = if (t.has("dueTime") && !t.isNull("dueTime")) t.getInt("dueTime") else null,
                    priority = t.optInt("priority", 0).coerceIn(0, 3),
                    completed = t.optBoolean("completed", false),
                    completedAt = if (t.has("completedAt") && !t.isNull("completedAt")) t.getLong("completedAt") else null,
                    createdAt = t.optLong("createdAt", 0L),
                    reminders = t.optString("reminders", ""),
                    recurrence = if (t.has("recurrence") && !t.isNull("recurrence")) t.getString("recurrence") else null,
                    occurrence = t.optInt("occurrence", 1),
                    series = if (t.has("series") && !t.isNull("series")) t.getString("series") else null,
                    durationMin = if (t.has("durationMin") && !t.isNull("durationMin")) t.getInt("durationMin") else null,
                    updatedAt = t.optLong("updatedAt", 0L),
                )
            }
            val deleted = LinkedHashMap<String, Long>()
            val d = o.optJSONArray("deleted") ?: JSONArray()
            for (i in 0 until d.length()) {
                val e = d.getJSONObject(i)
                deleted[e.getString("uuid")] = e.optLong("at", 0L)
            }
            Parsed.Ok(SyncDoc(tasks, deleted))
        }
    } catch (e: Exception) {
        Parsed.Invalid
    }
}

object SyncMerge {
    const val KEEP_DELETIONS_MS = 90L * 86_400_000L

    /**
     * Combines two views of the same list. For every uuid the newest change wins: the later
     * updatedAt, or the deletion if it is at least as new as the task. Deletions older than
     * [KEEP_DELETIONS_MS] are forgotten. The rule is symmetrical, so both devices end up equal.
     */
    fun merge(local: SyncDoc, remote: SyncDoc, now: Long): SyncDoc {
        val tasks = LinkedHashMap<String, SyncTask>()
        val deleted = LinkedHashMap<String, Long>()
        val keys = LinkedHashSet<String>().apply {
            addAll(local.tasks.keys); addAll(remote.tasks.keys); addAll(local.deleted.keys); addAll(remote.deleted.keys)
        }
        for (u in keys) {
            val lt = local.tasks[u]
            val rt = remote.tasks[u]
            val task = when {
                lt == null -> rt
                rt == null -> lt
                rt.updatedAt > lt.updatedAt -> rt
                else -> lt
            }
            val del = listOfNotNull(local.deleted[u], remote.deleted[u]).maxOrNull()
            if (del != null && (task == null || del >= task.updatedAt)) {
                if (del >= now - KEEP_DELETIONS_MS) deleted[u] = del
            } else if (task != null) {
                tasks[u] = task
            }
        }
        return SyncDoc(tasks, deleted)
    }
}

/** Converts between the database row and the shared form. */
object SyncMapper {
    fun toSync(all: List<Task>): Map<String, SyncTask> {
        val uuidById = all.associate { it.id to it.uuid }
        return all.filter { it.uuid.isNotEmpty() }.associate { t ->
            t.uuid to SyncTask(
                uuid = t.uuid, title = t.title, notes = t.notes, dueDate = t.dueDate, dueTime = t.dueTime,
                priority = t.priority, completed = t.completed, completedAt = t.completedAt, createdAt = t.createdAt,
                reminders = t.reminders, recurrence = t.recurrence, occurrence = t.occurrence,
                series = if (t.recurrence == null || t.seriesId == 0L) null else uuidById[t.seriesId]?.takeIf { it.isNotEmpty() },
                durationMin = t.durationMin, updatedAt = t.updatedAt,
            )
        }
    }

    /** Takes the shared fields from [s] and keeps this device's own (id, snooze, calendar event, series). */
    fun applyTo(local: Task?, s: SyncTask): Task = (local ?: Task(title = "")).copy(
        title = s.title, notes = s.notes, dueDate = s.dueDate, dueTime = s.dueTime, priority = s.priority,
        completed = s.completed, completedAt = s.completedAt, createdAt = s.createdAt, reminders = s.reminders,
        recurrence = s.recurrence, occurrence = s.occurrence, durationMin = s.durationMin,
        uuid = s.uuid, updatedAt = s.updatedAt,
        snoozedUntil = if (s.completed || local == null || local.dueDate != s.dueDate || local.dueTime != s.dueTime) null else local.snoozedUntil,
    )
}
