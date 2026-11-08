package com.bobby.cadence.ui

import com.bobby.cadence.data.AppSettings
import com.bobby.cadence.data.RecurrenceCalc
import com.bobby.cadence.data.SortMode
import com.bobby.cadence.data.Task
import com.bobby.cadence.data.date
import com.bobby.cadence.data.rule
import java.time.LocalDate

enum class ViewKind(val label: String) {
    ALL("All tasks"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    OVERDUE("Overdue"),
    NO_DATE("No date"),
    REPEATING("Repeating"),
    COMPLETED("Completed"),
}

data class UiFilter(
    val view: ViewKind = ViewKind.ALL,
    /** a day picked from the calendar; wins over [view] */
    val day: LocalDate? = null,
    /** exact priority 0..3, null = any */
    val priority: Int? = null,
    val query: String = "",
    /** the list the app opens on; "default" means no filter is applied */
    val base: ViewKind = ViewKind.ALL,
) {
    val isDefault: Boolean get() = view == base && day == null && priority == null && query.isBlank()
}

enum class Tone { NORMAL, DANGER, ACCENT, DIM }

data class Section(
    val key: String,
    val title: String,
    val tone: Tone,
    val tasks: List<Task>,
    val collapsible: Boolean = false,
)

data class DayMark(val open: Int, val done: Int, val high: Boolean, val projected: Int)

object TaskLogic {
    /** For every repeating series with an open occurrence: how many occurrences in a row were completed right before it. */
    fun streaks(tasks: List<Task>): Map<Long, Int> {
        val done = tasks.filter { it.completed && it.recurrence != null && it.seriesId != 0L }
            .groupBy { it.seriesId }.mapValues { e -> e.value.map { it.occurrence }.toSet() }
        val out = HashMap<Long, Int>()
        for (t in tasks) {
            if (t.completed || t.recurrence == null || t.seriesId == 0L) continue
            val set = done[t.seriesId] ?: continue
            var k = t.occurrence - 1
            var n = 0
            while (k >= 1 && k in set) { n++; k-- }
            out[t.seriesId] = n
        }
        return out
    }


    private fun Task.isOpen() = !completed

    private val dateTimePriority: Comparator<Task> =
        compareBy<Task, Long?>(nullsLast<Long>()) { it.dueDate }
            .thenBy { it.dueTime ?: -1 }
            .thenByDescending { it.priority }
            .thenBy { it.createdAt }

    private fun comparatorFor(sort: SortMode): Comparator<Task> = when (sort) {
        SortMode.SMART -> dateTimePriority
        SortMode.PRIORITY -> compareByDescending<Task> { it.priority }.then(dateTimePriority)
        SortMode.NEWEST -> compareByDescending<Task> { it.createdAt }
        SortMode.ALPHA -> compareBy<Task> { it.title.lowercase() }
    }

    fun counts(tasks: List<Task>, today: LocalDate): Map<ViewKind, Int> {
        val t = today.toEpochDay()
        val open = tasks.filter { it.isOpen() }
        return mapOf(
            ViewKind.ALL to open.size,
            ViewKind.TODAY to open.count { it.dueDate != null && it.dueDate <= t },
            ViewKind.UPCOMING to open.count { it.dueDate != null && it.dueDate > t },
            ViewKind.OVERDUE to open.count { it.dueDate != null && it.dueDate < t },
            ViewKind.NO_DATE to open.count { it.dueDate == null },
            ViewKind.REPEATING to open.count { it.recurrence != null },
            ViewKind.COMPLETED to tasks.count { it.completed },
        )
    }

    fun matches(t: Task, f: UiFilter): Boolean {
        if (f.priority != null && t.priority != f.priority) return false
        val q = f.query.trim()
        if (q.isNotEmpty() && !t.title.contains(q, true) && !t.notes.contains(q, true)) return false
        return true
    }

    fun sections(all: List<Task>, f: UiFilter, today: LocalDate, s: AppSettings): List<Section> {
        val t = today.toEpochDay()
        val cmp = comparatorFor(s.sort)
        val pool = all.filter { matches(it, f) }

        // a day picked on the calendar
        if (f.day != null) {
            val d = f.day.toEpochDay()
            val onDay = pool.filter { it.dueDate == d }
            val open = onDay.filter { it.isOpen() }.sortedWith(cmp)
            val done = onDay.filter { it.completed }.sortedBy { it.completedAt }
            return listOfNotNull(
                if (open.isNotEmpty()) Section("day-open", "To do", Tone.NORMAL, open) else null,
                if (done.isNotEmpty()) Section("day-done", "Completed", Tone.DIM, done, collapsible = true) else null,
            )
        }

        if (f.view == ViewKind.COMPLETED) {
            val done = pool.filter { it.completed }.sortedByDescending { it.completedAt ?: 0L }
            return if (done.isEmpty()) emptyList() else listOf(Section("completed", "Completed", Tone.DIM, done))
        }

        val open = pool.filter { it.isOpen() }.filter {
            when (f.view) {
                ViewKind.ALL -> true
                ViewKind.TODAY -> it.dueDate != null && it.dueDate <= t
                ViewKind.UPCOMING -> it.dueDate != null && it.dueDate > t
                ViewKind.OVERDUE -> it.dueDate != null && it.dueDate < t
                ViewKind.NO_DATE -> it.dueDate == null
                ViewKind.REPEATING -> it.recurrence != null
                ViewKind.COMPLETED -> false
            }
        }.sortedWith(cmp)

        val out = ArrayList<Section>()
        if (s.sort == SortMode.SMART) {
            val overdue = open.filter { it.dueDate != null && it.dueDate < t }
            val todayList = open.filter { it.dueDate == t }
            val later = open.filter { it.dueDate != null && it.dueDate > t }
            val none = open.filter { it.dueDate == null }
            if (overdue.isNotEmpty()) out += Section("overdue", "Overdue", Tone.DANGER, overdue)
            if (todayList.isNotEmpty()) out += Section("today", "Today", Tone.ACCENT, todayList)
            later.groupBy { it.dueDate!! }.toSortedMap().forEach { (day, list) ->
                val d = LocalDate.ofEpochDay(day)
                val title = when (day - t) {
                    1L -> "Tomorrow"
                    else -> com.bobby.cadence.data.Fmt.dayShort(d)
                }
                out += Section("d$day", title, Tone.NORMAL, list)
            }
            if (none.isNotEmpty()) out += Section("nodate", "No date", Tone.NORMAL, none)
        } else if (open.isNotEmpty()) {
            out += Section("flat", f.view.label, Tone.NORMAL, open)
        }

        if (f.view == ViewKind.ALL && s.showCompleted) {
            val done = pool.filter { it.completed }.sortedByDescending { it.completedAt ?: 0L }
            if (done.isNotEmpty()) out += Section("completed", "Completed", Tone.DIM, done, collapsible = true)
        }
        return out
    }

    /** Progress for the header ring: tasks dated today. */
    fun todayProgress(all: List<Task>, today: LocalDate): Pair<Int, Int> {
        val d = today.toEpochDay()
        val todays = all.filter { it.dueDate == d }
        return todays.count { it.completed } to todays.size
    }

    fun marks(all: List<Task>, from: LocalDate, to: LocalDate): Map<LocalDate, DayMark> {
        val open = HashMap<LocalDate, Int>()
        val done = HashMap<LocalDate, Int>()
        val high = HashSet<LocalDate>()
        val projected = HashMap<LocalDate, Int>()
        val f = from.toEpochDay()
        val e = to.toEpochDay()
        for (t in all) {
            val d = t.date ?: continue
            if (t.dueDate!! in f..e) {
                if (t.completed) done.merge(d, 1, Int::plus)
                else {
                    open.merge(d, 1, Int::plus)
                    if (t.priority == 3) high.add(d)
                }
            }
            val rule = t.rule
            if (rule != null && !t.completed) {
                for (p in RecurrenceCalc.project(d, rule, t.occurrence, from, to)) projected.merge(p, 1, Int::plus)
            }
        }
        val keys = open.keys + done.keys + projected.keys
        return keys.associateWith { DayMark(open[it] ?: 0, done[it] ?: 0, it in high, projected[it] ?: 0) }
    }

    /** Repeats of open tasks that fall on [day] but are not real rows yet. */
    fun projectedOn(all: List<Task>, day: LocalDate): List<Task> = all.filter { t ->
        val d = t.date
        val rule = t.rule
        !t.completed && d != null && rule != null && d != day &&
            RecurrenceCalc.project(d, rule, t.occurrence, day, day).isNotEmpty()
    }.sortedWith(dateTimePriority)
}
