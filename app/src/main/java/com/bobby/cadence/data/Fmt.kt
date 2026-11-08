package com.bobby.cadence.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Actions {
    const val QUICK_ADD = "com.bobby.cadence.QUICK_ADD"
    const val OPEN_TASKS = "com.bobby.cadence.OPEN_TASKS"
    const val OPEN_FOCUS = "com.bobby.cadence.OPEN_FOCUS"
    const val FOCUS_TASK = "com.bobby.cadence.FOCUS_TASK"
    const val EXTRA_TASK_ID = "task_id"
}

object Fmt {
    private val loc: Locale get() = Locale.getDefault()

    fun time(t: LocalTime, is24: Boolean): String =
        DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", loc).format(t)

    fun minutes(min: Int, is24: Boolean): String = time(LocalTime.of(min / 60, min % 60), is24)

    /** Short relative label: Today, Tomorrow, Fri, 5 Dec, 5 Dec 2027 */
    fun date(d: LocalDate, today: LocalDate): String {
        val diff = d.toEpochDay() - today.toEpochDay()
        return when {
            diff == 0L -> "Today"
            diff == 1L -> "Tomorrow"
            diff == -1L -> "Yesterday"
            diff in 2..6 -> DateTimeFormatter.ofPattern("EEEE", loc).format(d)
            d.year == today.year -> DateTimeFormatter.ofPattern("d MMM", loc).format(d)
            else -> DateTimeFormatter.ofPattern("d MMM yyyy", loc).format(d)
        }
    }

    /** "Tue, 3 Nov" */
    fun dayShort(d: LocalDate): String = DateTimeFormatter.ofPattern("EEE, d MMM", loc).format(d)

    /** "Tuesday, 3 November" */
    fun dayLong(d: LocalDate): String = DateTimeFormatter.ofPattern("EEEE, d MMMM", loc).format(d)

    fun monthYear(d: LocalDate): String = DateTimeFormatter.ofPattern("MMMM yyyy", loc).format(d)

    /** 10 -> "10 min", 60 -> "1 hour", 90 -> "1 h 30 min", 1440 -> "1 day" */
    fun offset(min: Int): String = when {
        min % 1440 == 0 -> "${min / 1440} day" + if (min / 1440 > 1) "s" else ""
        min % 60 == 0 -> "${min / 60} hour" + if (min / 60 > 1) "s" else ""
        min > 60 -> "${min / 60} h ${min % 60} min"
        else -> "$min min"
    }

    fun offsetLabel(min: Int): String = if (min <= 0) "At time" else "${offset(min)} before"
}
