package com.bobby.cadence.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class RepeatUnit { DAY, WEEK, MONTH, YEAR }
enum class MonthMode { DAY_OF_MONTH, NTH_WEEKDAY, LAST_DAY }
enum class EndType { NEVER, ON_DATE, AFTER_COUNT }
enum class RepeatBasis { FROM_DUE, FROM_COMPLETION }

/**
 * A fully customisable repeat rule.
 *
 *  - unit + interval            : every N days / weeks / months / years
 *  - weekDays                   : for WEEK, which weekdays (empty = same weekday as due date)
 *  - monthMode                  : day-of-month (dayOfMonth, 0 = follow due date), nth weekday, or last day
 *  - endType                    : never / on a date / after N occurrences
 *  - basis                      : next date counted from the due date or from the completion date
 */
data class Recurrence(
    val unit: RepeatUnit = RepeatUnit.DAY,
    val interval: Int = 1,
    val weekDays: Set<DayOfWeek> = emptySet(),
    val monthMode: MonthMode = MonthMode.DAY_OF_MONTH,
    val dayOfMonth: Int = 0,
    val nth: Int = 1,
    val nthWeekday: DayOfWeek = DayOfWeek.MONDAY,
    val endType: EndType = EndType.NEVER,
    val endDate: LocalDate? = null,
    val endCount: Int = 0,
    val basis: RepeatBasis = RepeatBasis.FROM_DUE,
)

private val WORKDAYS = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    return "$n$suffix"
}

fun Recurrence.describe(): String {
    val base = when (unit) {
        RepeatUnit.DAY -> if (interval == 1) "Every day" else "Every $interval days"
        RepeatUnit.WEEK -> {
            val days = weekDays.sortedBy { it.value }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            when {
                weekDays == WORKDAYS && interval == 1 -> "Every weekday"
                weekDays == WEEKEND && interval == 1 -> "Every weekend"
                weekDays.isEmpty() -> if (interval == 1) "Every week" else "Every $interval weeks"
                interval == 1 -> "Every $days"
                else -> "Every $interval weeks on $days"
            }
        }
        RepeatUnit.MONTH -> {
            val every = if (interval == 1) "Every month" else "Every $interval months"
            when (monthMode) {
                MonthMode.DAY_OF_MONTH -> if (dayOfMonth > 0) "$every on the ${ordinal(dayOfMonth)}" else every
                MonthMode.LAST_DAY -> "$every on the last day"
                MonthMode.NTH_WEEKDAY -> {
                    val which = if (nth >= 5) "last" else ordinal(nth)
                    "$every on the $which ${nthWeekday.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
                }
            }
        }
        RepeatUnit.YEAR -> if (interval == 1) "Every year" else "Every $interval years"
    }
    val tail = StringBuilder()
    when (endType) {
        EndType.ON_DATE -> endDate?.let {
            tail.append(", until ").append(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(it))
        }
        EndType.AFTER_COUNT -> if (endCount > 0) tail.append(", ").append(endCount).append(if (endCount == 1) " time" else " times")
        EndType.NEVER -> {}
    }
    if (basis == RepeatBasis.FROM_COMPLETION) tail.append(", after completion")
    return base + tail
}

object RecurrenceCalc {

    private fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    fun nthWeekdayOf(ym: YearMonth, weekday: DayOfWeek, nth: Int): LocalDate {
        if (nth >= 5) return ym.atEndOfMonth().with(TemporalAdjusters.previousOrSame(weekday))
        val first = ym.atDay(1).with(TemporalAdjusters.nextOrSame(weekday)).plusWeeks((nth - 1).toLong())
        return if (YearMonth.from(first) == ym) first
        else ym.atEndOfMonth().with(TemporalAdjusters.previousOrSame(weekday))
    }

    /** The first occurrence strictly after [base] following the rule. */
    fun step(base: LocalDate, rec: Recurrence): LocalDate {
        val n = rec.interval.coerceAtLeast(1)
        return when (rec.unit) {
            RepeatUnit.DAY -> base.plusDays(n.toLong())
            RepeatUnit.WEEK -> {
                if (rec.weekDays.isEmpty()) base.plusWeeks(n.toLong())
                else {
                    val baseMonday = mondayOf(base)
                    var d = base.plusDays(1)
                    var guard = 0
                    while (guard++ < 7 * n + 14) {
                        val weeks = ChronoUnit.WEEKS.between(baseMonday, mondayOf(d))
                        if (d.dayOfWeek in rec.weekDays && weeks % n == 0L) return d
                        d = d.plusDays(1)
                    }
                    base.plusWeeks(n.toLong())
                }
            }
            RepeatUnit.MONTH -> {
                val ym = YearMonth.from(base).plusMonths(n.toLong())
                when (rec.monthMode) {
                    MonthMode.DAY_OF_MONTH -> {
                        val dom = if (rec.basis == RepeatBasis.FROM_DUE && rec.dayOfMonth > 0) rec.dayOfMonth else base.dayOfMonth
                        ym.atDay(dom.coerceAtMost(ym.lengthOfMonth()))
                    }
                    MonthMode.LAST_DAY -> ym.atEndOfMonth()
                    MonthMode.NTH_WEEKDAY -> nthWeekdayOf(ym, rec.nthWeekday, rec.nth)
                }
            }
            RepeatUnit.YEAR -> base.plusYears(n.toLong())
        }
    }

    /**
     * Next due date after completing an occurrence.
     * [count] = number of occurrences generated so far, including the one being completed.
     * Returns null when the series has ended.
     */
    fun next(due: LocalDate, completedOn: LocalDate, today: LocalDate, rec: Recurrence, count: Int): LocalDate? {
        if (rec.endType == EndType.AFTER_COUNT && rec.endCount > 0 && count >= rec.endCount) return null
        val base = if (rec.basis == RepeatBasis.FROM_COMPLETION) completedOn else due
        var n = step(base, rec)
        if (rec.basis == RepeatBasis.FROM_DUE) {
            var guard = 0
            while (n.isBefore(today) && guard++ < 4000) n = step(n, rec)
        }
        if (rec.endType == EndType.ON_DATE && rec.endDate != null && n.isAfter(rec.endDate)) return null
        return n
    }

    /** First date on or after [from] that fits the rule (used when a repeat is set without a date). */
    fun firstOccurrence(from: LocalDate, rec: Recurrence): LocalDate = when (rec.unit) {
        RepeatUnit.DAY, RepeatUnit.YEAR -> from
        RepeatUnit.WEEK -> {
            if (rec.weekDays.isEmpty()) from
            else {
                var d = from
                var guard = 0
                while (d.dayOfWeek !in rec.weekDays && guard++ < 8) d = d.plusDays(1)
                d
            }
        }
        RepeatUnit.MONTH -> {
            fun candidate(ym: YearMonth): LocalDate = when (rec.monthMode) {
                MonthMode.DAY_OF_MONTH -> {
                    val dom = if (rec.dayOfMonth > 0) rec.dayOfMonth else from.dayOfMonth
                    ym.atDay(dom.coerceAtMost(ym.lengthOfMonth()))
                }
                MonthMode.LAST_DAY -> ym.atEndOfMonth()
                MonthMode.NTH_WEEKDAY -> nthWeekdayOf(ym, rec.nthWeekday, rec.nth)
            }
            val thisMonth = candidate(YearMonth.from(from))
            if (!thisMonth.isBefore(from)) thisMonth else candidate(YearMonth.from(from).plusMonths(1))
        }
    }

    /** Future occurrences (after [due]) inside [from]..[to], for showing repeats on the calendar. */
    fun project(due: LocalDate, rec: Recurrence, count: Int, from: LocalDate, to: LocalDate, limit: Int = 6000): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var cur = due
        var c = count
        var guard = 0
        while (guard++ < limit) {
            if (rec.endType == EndType.AFTER_COUNT && rec.endCount > 0 && c >= rec.endCount) break
            val n = step(cur, rec)
            if (rec.endType == EndType.ON_DATE && rec.endDate != null && n.isAfter(rec.endDate)) break
            if (n.isAfter(to)) break
            c++
            if (!n.isBefore(from)) out.add(n)
            cur = n
        }
        return out
    }
}
