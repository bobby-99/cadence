package com.bobby.cadence.nlp

import com.bobby.cadence.data.EndType
import com.bobby.cadence.data.MonthMode
import com.bobby.cadence.data.RecurrenceCalc
import com.bobby.cadence.data.Recurrence
import com.bobby.cadence.data.RepeatBasis
import com.bobby.cadence.data.RepeatUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class TokenKind { DATE, TIME, REPEAT, PRIORITY, REMINDER, NOTE }

/** A recognised phrase inside the typed text: [start, end) */
data class Token(val start: Int, val end: Int, val kind: TokenKind)

data class ParsedTask(
    val title: String,
    val notes: String?,
    val date: LocalDate?,
    val time: LocalTime?,
    /** 0 none, 1 low, 2 medium, 3 high; null = not specified */
    val priority: Int?,
    val recurrence: Recurrence?,
    /** minutes-before offsets; null = not specified, empty = explicitly none */
    val reminders: List<Int>?,
    val tokens: List<Token>,
    /** planned length from phrases like "for 45 min" or "for 2 hours" */
    val durationMin: Int? = null,
)

/** Tunable time-of-day defaults. */
object TimeDefaults {
    val MORNING: LocalTime = LocalTime.of(8, 0)
    val NOON: LocalTime = LocalTime.of(13, 0)
    val AFTERNOON: LocalTime = LocalTime.of(15, 0)
    val EVENING: LocalTime = LocalTime.of(18, 0)
    val NIGHT: LocalTime = LocalTime.of(22, 0)
    val EOD: LocalTime = LocalTime.of(18, 0)
}

// ---------------------------------------------------------------------------------------------
// Regex building blocks
// ---------------------------------------------------------------------------------------------

private const val DN = "(?:monday|mon|tuesday|tues|tue|wednesday|weds|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun)"
private const val DNW = "\\b$DN\\b"
private const val DAYLIST = "$DNW(?:(?:\\s*(?:,|&|/|\\+)\\s*|\\s+and\\s+|\\s+)$DNW)*"
private const val MONTHS = "(?:january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec)"
private const val WORDNUM = "(?:an?|one|two|three|four|five|six|seven|eight|nine|ten)"
private const val NUMBER = "(?:\\d{1,3}(?:\\.\\d+)?\\s*|$WORDNUM\\s+)"
private const val HM_UNIT = "(?:hours?|hrs?|h|minutes?|mins?|m)(?![a-z])"
private const val DURPART = "$NUMBER$HM_UNIT"
private const val DURATION = "(?:$DURPART\\s*(?:,\\s*|and\\s+)?)+"
private const val AMPM = "(a\\.?m\\.?|p\\.?m\\.?)"
private const val PREP = "(?:\\b(?:on|by|due(?:\\s+(?:on|by))?|before)\\s+)?"
private const val UNTIL = "\\b(?:until|till|thru|through)\\s+"
private const val TOD = "(?:morning|afternoon|evening|night)"
private const val REPEAT_PREFIX = "(?:repeats?\\s+)?"
private const val EVERY = "(?:every|each)"
private const val OF_MONTH = "(?:\\s+of\\s+(?:the\\s+|each\\s+|every\\s+)?month)?"

private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

private val DN_RE = r(DN)

private fun dowOf(s: String): DayOfWeek = when (s.trim().lowercase(Locale.ROOT).take(3)) {
    "mon" -> DayOfWeek.MONDAY
    "tue" -> DayOfWeek.TUESDAY
    "wed" -> DayOfWeek.WEDNESDAY
    "thu" -> DayOfWeek.THURSDAY
    "fri" -> DayOfWeek.FRIDAY
    "sat" -> DayOfWeek.SATURDAY
    else -> DayOfWeek.SUNDAY
}

private fun monthOf(s: String): Int = when (s.trim().lowercase(Locale.ROOT).take(3)) {
    "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
    "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; else -> 12
}

private val WORDS = mapOf(
    "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
    "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
)

private fun numberOf(s: String): Double? {
    val t = s.trim().lowercase(Locale.ROOT)
    return t.toDoubleOrNull() ?: WORDS[t]
}

private fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

private object Rx {
    // notes
    val notes = r("\\s*//\\s*(.+)$")

    // recurrence
    val nthWeekday = r("\\b$REPEAT_PREFIX$EVERY\\s+(1st|2nd|3rd|4th|first|second|third|fourth|last)\\s+($DNW)$OF_MONTH")
    val lastDay = r("\\b$REPEAT_PREFIX(?:$EVERY\\s+(?:month\\s*-?\\s*end|last\\s+day$OF_MONTH)|monthly\\s+on\\s+the\\s+last\\s+day)\\b")
    val everyOrdinal = r("\\b$REPEAT_PREFIX$EVERY\\s+(\\d{1,2})(?:st|nd|rd|th)(?:\\s+(?:day\\s+)?of\\s+(?:the\\s+|each\\s+|every\\s+)?month)?\\b")
    val monthlyOn = r("\\b$REPEAT_PREFIX(?:monthly|$EVERY\\s+(?:(\\d{1,2})\\s+)?months?)\\s+on\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\b")
    val everyNWeeksOn = r("\\b$REPEAT_PREFIX$EVERY\\s+(?:(other|\\d{1,2})\\s+)?weeks?\\s+(?:on\\s+)?($DAYLIST)")
    val everyDays = r("\\b$REPEAT_PREFIX$EVERY\\s+(other\\s+)?($DAYLIST)")
    val weekdays = r("\\b$REPEAT_PREFIX(?:$EVERY\\s+(?:week\\s*day|work\\s*day|business\\s+day)s?|week\\s*days|work\\s*days)\\b")
    val weekends = r("\\b$REPEAT_PREFIX(?:$EVERY\\s+weekends?|weekends)\\b")
    val pluralDays = r("\\b(?:$EVERY\\s+)?(mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays)\\b")
    val everyUnit = r("\\b$REPEAT_PREFIX$EVERY\\s+(?:(\\d{1,3}|other|second|third)\\s+)?(days?|weeks?|months?|years?|fortnights?)\\b")
    val adverb = r("\\b$REPEAT_PREFIX(daily|everyday|weekly|monthly|yearly|annually|biweekly|fortnightly|bimonthly|quarterly)\\b")
    val everyTod = r("\\b$EVERY\\s+(?=$TOD\\b)")

    // recurrence modifiers
    val fromCompletion = r("\\b(?:after|from)\\s+(?:completion|completing|complete|done|finishing|finished)\\b")
    val endCount = r("\\b(?:for\\s+)?(\\d{1,3})\\s*(?:times|occurrences)\\b")
    val endCountX = r("\\bx(\\d{1,3})\\b")
    val endSpan = r("\\bfor\\s+(\\d{1,3})\\s+(days?|weeks?|months?|years?)\\b")

    // reminders
    val reminder = r("\\b(?:and\\s+)?(?:(?:remind|alert|notify)(?:\\s+me)?\\s+)?($NUMBER)(days?|d|hours?|hrs?|hr|h|minutes?|mins?|min|m)\\s+(?:before|prior|early|ahead)\\b")
    val noReminder = r("\\b(?:no|without|skip)\\s+(?:reminders?|alerts?|notifications?)\\b")
    val remindLead = r("^\\s*(?:please\\s+)?(?:remind\\s+me(?:\\s+to|\\s+about)?|reminder\\s*(?:to|:|-)?)\\s+")

    // priority
    val bangs = r("(?<!\\S)(!{1,3})(?!\\S)")
    val pLevel = r("(?<!\\S)p([0-3])(?!\\S)")
    val urgent = r("\\b(urgent|asap)\\b")
    val prioWord = r("\\b(?:(high|medium|med|low|normal|no)\\s+(?:priority|prio)|(?:priority|prio)\\s*[:=]?\\s*(high|medium|med|low|none|normal))\\b")

    // durations
    val inHalfHour = r("\\bin\\s+half\\s+an?\\s+hour\\b")
    val inDuration = r("\\bin\\s+($DURATION)")
    val lengthFor = r("\\bfor\\s+(\\d{1,3})\\s*(minutes?|mins?|hours?|hrs?|h|m)\\b")
    val durationLater = r("\\b($DURATION)(?:from\\s+now|later)\\b")
    val durPart = r(DURPART)
    val inDays = r("\\bin\\s+($NUMBER)(days?|weeks?|months?|years?)\\b")

    // times
    private const val TPRE = "(?:@\\s*|\\b(?:at|by|around|before|from)\\s+)?"
    val timeColon = r("$TPRE\\b(\\d{1,2})\\s*:\\s*(\\d{2})(?!\\d)(?:\\s*$AMPM(?![a-z]))?")
    val timeDot = r("$TPRE\\b(\\d{1,2})\\.(\\d{2})\\s*$AMPM(?![a-z])")
    val timeHour = r("$TPRE\\b(\\d{1,2})\\s*$AMPM(?![a-z])")
    val timeAtBare = r("(?:@\\s*|\\bat\\s+)(\\d{1,2})(?![\\d:.]|\\s*(?:am|pm|a\\.m|p\\.m|st\\b|nd\\b|rd\\b|th\\b|%|kg|min|hr))")
    val noon = r("\\b(?:(?:at|by|around)\\s+)?noon\\b")
    val midnight = r("\\b(?:(?:at|by|around)\\s+)?midnight\\b")
    val eod = r("\\b(?:(?:by|at)\\s+)?(?:eod|eob|end\\s+of\\s+(?:the\\s+)?(?:day|business))\\b")
    val todWord = r("\\b(?:(this|every|each|at|in\\s+the|on\\s+the|during\\s+the)\\s+)?($TOD)\\b")
}

private class DateRule(val body: String, val resolve: (MatchResult) -> LocalDate?) {
    val plain: Regex = r(PREP + body)
    val untilRe: Regex = r(UNTIL + body)
}

// ---------------------------------------------------------------------------------------------
// Parser
// ---------------------------------------------------------------------------------------------

class TaskParser(private val clock: () -> LocalDateTime = { LocalDateTime.now() }) {

    fun parse(input: String, defaultDate: LocalDate? = null): ParsedTask =
        Run(input, clock().withSecond(0).withNano(0), defaultDate).execute()

    private class Run(val text: String, val now: LocalDateTime, val defaultDate: LocalDate?) {
        val today: LocalDate = now.toLocalDate()
        val claimed = ArrayList<IntArray>()
        val tokens = ArrayList<Token>()

        var notes: String? = null
        var date: LocalDate? = null
        var time: LocalTime? = null
        var timeHint: LocalTime? = null
        var midnightHint = false
        var ambigHour: Int? = null
        var ambigMinute = 0
        var priority: Int? = null
        var rec: Recurrence? = null
        var reminders: MutableList<Int>? = null
        var spanEnd: Pair<Int, String>? = null
        var lengthMin: Int? = null

        // ---- scanning helpers -------------------------------------------------------------

        fun isFree(s: Int, e: Int) = claimed.none { s < it[1] && e > it[0] }

        fun claim(s: Int, e: Int, kind: TokenKind) {
            claimed.add(intArrayOf(s, e))
            tokens.add(Token(s, e, kind))
        }

        fun find(re: Regex, kind: TokenKind, accept: (MatchResult) -> Boolean): Boolean {
            var idx = 0
            while (idx <= text.length) {
                val m = re.find(text, idx) ?: return false
                val s = m.range.first
                val e = m.range.last + 1
                if (e > s && isFree(s, e) && accept(m)) {
                    claim(s, e, kind)
                    return true
                }
                idx = s + 1
            }
            return false
        }

        fun adjacentToDateOrRepeat(start: Int): Boolean = tokens.any {
            (it.kind == TokenKind.DATE || it.kind == TokenKind.REPEAT) &&
                it.end <= start && text.substring(it.end, start).isBlank()
        }

        // ---- main -------------------------------------------------------------------------

        fun execute(): ParsedTask {
            parseNotes()
            parseRecurrence()
            parseReminders()
            parseLead()
            parsePriority()
            parseDuration()
            parseLength()
            parseDate()
            parseTime()
            resolveDefaults()
            val title = buildTitle()
            return ParsedTask(
                title = title,
                notes = notes,
                date = date,
                time = time,
                priority = priority,
                recurrence = rec,
                reminders = reminders?.toList(),
                tokens = tokens.sortedBy { it.start },
                durationMin = lengthMin,
            )
        }

        // ---- notes ------------------------------------------------------------------------

        fun parseNotes() {
            find(Rx.notes, TokenKind.NOTE) { m ->
                val n = m.groupValues[1].trim()
                if (n.isNotEmpty()) notes = n
                true
            }
        }

        // ---- recurrence -------------------------------------------------------------------

        private fun tryRec(re: Regex, build: (MatchResult) -> Recurrence?): Boolean =
            find(re, TokenKind.REPEAT) { m ->
                val built = build(m)
                if (built != null) { rec = built; true } else false
            }

        fun parseRecurrence() {
            val found =
                tryRec(Rx.nthWeekday) { m ->
                    val nth = when (m.groupValues[1].lowercase(Locale.ROOT)) {
                        "1st", "first" -> 1
                        "2nd", "second" -> 2
                        "3rd", "third" -> 3
                        "4th", "fourth" -> 4
                        else -> 5
                    }
                    Recurrence(
                        unit = RepeatUnit.MONTH, monthMode = MonthMode.NTH_WEEKDAY,
                        nth = nth, nthWeekday = dowOf(m.groupValues[2]),
                    )
                } ||
                tryRec(Rx.lastDay) { Recurrence(unit = RepeatUnit.MONTH, monthMode = MonthMode.LAST_DAY) } ||
                tryRec(Rx.everyOrdinal) { m ->
                    val dom = m.groupValues[1].toIntOrNull()
                    if (dom == null || dom !in 1..31) null
                    else Recurrence(unit = RepeatUnit.MONTH, dayOfMonth = dom)
                } ||
                tryRec(Rx.monthlyOn) { m ->
                    val dom = m.groupValues[2].toIntOrNull()
                    val n = m.groupValues[1].toIntOrNull() ?: 1
                    if (dom == null || dom !in 1..31 || n < 1) null
                    else Recurrence(unit = RepeatUnit.MONTH, interval = n, dayOfMonth = dom)
                } ||
                tryRec(Rx.everyNWeeksOn) { m ->
                    val iv = m.groupValues[1].lowercase(Locale.ROOT)
                    val n = if (iv == "other") 2 else iv.toIntOrNull() ?: 1
                    val days = DN_RE.findAll(m.groupValues[2]).map { dowOf(it.value) }.toSet()
                    if (days.isEmpty() || n < 1) null
                    else Recurrence(unit = RepeatUnit.WEEK, interval = n, weekDays = days)
                } ||
                tryRec(Rx.everyDays) { m ->
                    val n = if (m.groupValues[1].isNotBlank()) 2 else 1
                    val days = DN_RE.findAll(m.groupValues[2]).map { dowOf(it.value) }.toSet()
                    if (days.isEmpty()) null
                    else Recurrence(unit = RepeatUnit.WEEK, interval = n, weekDays = days)
                } ||
                tryRec(Rx.weekdays) {
                    Recurrence(
                        unit = RepeatUnit.WEEK,
                        weekDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                    )
                } ||
                tryRec(Rx.weekends) {
                    Recurrence(unit = RepeatUnit.WEEK, weekDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
                } ||
                tryRec(Rx.pluralDays) { m ->
                    Recurrence(unit = RepeatUnit.WEEK, weekDays = setOf(dowOf(m.groupValues[1])))
                } ||
                tryRec(Rx.everyUnit) { m ->
                    val iv = m.groupValues[1].lowercase(Locale.ROOT)
                    val n = when (iv) {
                        "" -> 1
                        "other", "second" -> 2
                        "third" -> 3
                        else -> iv.toIntOrNull() ?: 1
                    }
                    val u = m.groupValues[2].lowercase(Locale.ROOT)
                    when {
                        n < 1 -> null
                        u.startsWith("d") -> Recurrence(unit = RepeatUnit.DAY, interval = n)
                        u.startsWith("w") -> Recurrence(unit = RepeatUnit.WEEK, interval = n)
                        u.startsWith("f") -> Recurrence(unit = RepeatUnit.WEEK, interval = n * 2)
                        u.startsWith("m") -> Recurrence(unit = RepeatUnit.MONTH, interval = n)
                        else -> Recurrence(unit = RepeatUnit.YEAR, interval = n)
                    }
                } ||
                tryRec(Rx.adverb) { m ->
                    when (m.groupValues[1].lowercase(Locale.ROOT)) {
                        "daily", "everyday" -> Recurrence(unit = RepeatUnit.DAY)
                        "weekly" -> Recurrence(unit = RepeatUnit.WEEK)
                        "biweekly", "fortnightly" -> Recurrence(unit = RepeatUnit.WEEK, interval = 2)
                        "monthly" -> Recurrence(unit = RepeatUnit.MONTH)
                        "bimonthly" -> Recurrence(unit = RepeatUnit.MONTH, interval = 2)
                        "quarterly" -> Recurrence(unit = RepeatUnit.MONTH, interval = 3)
                        else -> Recurrence(unit = RepeatUnit.YEAR)
                    }
                } ||
                // "every morning": the word "every" is eaten, "morning" is left for the time pass
                find(Rx.everyTod, TokenKind.REPEAT) { rec = Recurrence(unit = RepeatUnit.DAY); true }

            if (!found) return
            if (rec == null) return

            // modifiers only make sense together with a repeat
            if (find(Rx.fromCompletion, TokenKind.REPEAT) { true }) {
                rec = rec!!.copy(basis = RepeatBasis.FROM_COMPLETION)
            }
            var done = false
            for (rule in dateRules) {
                if (done) break
                done = find(rule.untilRe, TokenKind.REPEAT) { m ->
                    val d = rule.resolve(m)
                    if (d != null) { rec = rec!!.copy(endType = EndType.ON_DATE, endDate = d); true } else false
                }
            }
            if (!done) {
                done = find(Rx.endCount, TokenKind.REPEAT) { m ->
                    val n = m.groupValues[1].toIntOrNull() ?: 0
                    if (n > 0) { rec = rec!!.copy(endType = EndType.AFTER_COUNT, endCount = n); true } else false
                } || find(Rx.endCountX, TokenKind.REPEAT) { m ->
                    val n = m.groupValues[1].toIntOrNull() ?: 0
                    if (n > 0) { rec = rec!!.copy(endType = EndType.AFTER_COUNT, endCount = n); true } else false
                }
            }
            if (!done) {
                find(Rx.endSpan, TokenKind.REPEAT) { m ->
                    val n = m.groupValues[1].toIntOrNull() ?: 0
                    if (n > 0) { spanEnd = n to m.groupValues[2].lowercase(Locale.ROOT); true } else false
                }
            }
        }

        // ---- reminders --------------------------------------------------------------------

        fun parseReminders() {
            while (find(Rx.reminder, TokenKind.REMINDER) { m ->
                    val num = numberOf(m.groupValues[1])
                    val unit = m.groupValues[2].lowercase(Locale.ROOT)
                    if (num == null || num <= 0.0) false
                    else {
                        val minutes = when {
                            unit.startsWith("d") -> num * 1440
                            unit.startsWith("h") -> num * 60
                            else -> num
                        }.toInt()
                        if (minutes <= 0 || minutes > 60 * 24 * 60) false
                        else {
                            val list = reminders ?: mutableListOf<Int>().also { reminders = it }
                            if (minutes !in list) list.add(minutes)
                            true
                        }
                    }
                }) { /* keep collecting */ }
            if (reminders == null) {
                find(Rx.noReminder, TokenKind.REMINDER) { reminders = mutableListOf(); true }
            }
        }

        fun parseLead() {
            find(Rx.remindLead, TokenKind.REMINDER) { true }
        }

        // ---- priority ---------------------------------------------------------------------

        fun parsePriority() {
            find(Rx.bangs, TokenKind.PRIORITY) { m -> priority = m.groupValues[1].length; true }
            if (priority == null) {
                find(Rx.pLevel, TokenKind.PRIORITY) { m ->
                    priority = when (m.groupValues[1]) { "1" -> 3; "2" -> 2; "3" -> 1; else -> 0 }
                    true
                }
            }
            if (priority == null) {
                find(Rx.urgent, TokenKind.PRIORITY) { priority = 3; true }
            }
            if (priority == null) {
                find(Rx.prioWord, TokenKind.PRIORITY) { m ->
                    val w = (m.groupValues[1].ifEmpty { m.groupValues[2] }).lowercase(Locale.ROOT)
                    priority = when (w) {
                        "high" -> 3
                        "medium", "med" -> 2
                        "low" -> 1
                        else -> 0
                    }
                    true
                }
            }
        }

        // ---- relative durations ("in 45 mins") --------------------------------------------

        fun applyDuration(minutes: Long): Boolean {
            if (minutes <= 0) return false
            val target = now.plusMinutes(minutes)
            date = target.toLocalDate()
            time = target.toLocalTime().withSecond(0).withNano(0)
            return true
        }

        fun sumDuration(s: String): Long {
            val numRe = Regex("^(\\d+(?:\\.\\d+)?|an?|one|two|three|four|five|six|seven|eight|nine|ten)")
            var total = 0.0
            for (part in Rx.durPart.findAll(s)) {
                val p = part.value.trim().lowercase(Locale.ROOT)
                val nm = numRe.find(p) ?: continue
                val num = numberOf(nm.value) ?: continue
                val rest = p.substring(nm.value.length).trim()
                total += if (rest.startsWith("h")) num * 60 else num
            }
            return Math.round(total)
        }

        /** "for 45 min", "for 2 hours": how long the task takes */
        fun parseLength() {
            find(Rx.lengthFor, TokenKind.TIME) { m ->
                val n = m.groupValues[1].toIntOrNull() ?: 0
                val mins = if (m.groupValues[2].lowercase(Locale.ROOT).startsWith("h")) n * 60 else n
                if (mins in 1..720) { lengthMin = mins; true } else false
            }
        }

        fun parseDuration() {
            val ok = find(Rx.inHalfHour, TokenKind.DATE) { applyDuration(30) } ||
                find(Rx.inDuration, TokenKind.DATE) { m -> applyDuration(sumDuration(m.groupValues[1])) } ||
                find(Rx.durationLater, TokenKind.DATE) { m -> applyDuration(sumDuration(m.groupValues[1])) }
            if (ok) return
            find(Rx.inDays, TokenKind.DATE) { m ->
                val n = numberOf(m.groupValues[1])?.toLong() ?: 0L
                val u = m.groupValues[2].lowercase(Locale.ROOT)
                if (n <= 0) false
                else {
                    date = when {
                        u.startsWith("d") -> today.plusDays(n)
                        u.startsWith("w") -> today.plusWeeks(n)
                        u.startsWith("m") -> today.plusMonths(n)
                        else -> today.plusYears(n)
                    }
                    true
                }
            }
        }

        // ---- dates ------------------------------------------------------------------------

        fun yearless(month: Int, day: Int, year: Int?): LocalDate? {
            return try {
                if (year != null) LocalDate.of(year, month, day)
                else {
                    val d = LocalDate.of(today.year, month, day)
                    if (d.isBefore(today)) d.plusYears(1) else d
                }
            } catch (e: Exception) {
                null
            }
        }

        fun weekdayResolve(modifier: String?, dow: DayOfWeek): LocalDate {
            val diff = (dow.value - today.dayOfWeek.value + 7) % 7
            return when (modifier?.lowercase(Locale.ROOT)) {
                "this", "coming", "upcoming" -> today.plusDays(diff.toLong())
                "next" -> {
                    val up = today.plusDays(if (diff == 0) 7L else diff.toLong())
                    if (mondayOf(up) == mondayOf(today)) up.plusDays(7) else up
                }
                else -> today.plusDays(if (diff == 0) 7L else diff.toLong())
            }
        }

        val dateRules: List<DateRule> = listOf(
            DateRule("\\b(20\\d{2})-(\\d{1,2})-(\\d{1,2})\\b") { m ->
                runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
            },
            DateRule("\\b(\\d{1,2})-(\\d{1,2})-(20\\d{2})\\b") { m ->
                runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
            },
            DateRule("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?(?![/\\d])(?!\\s*(?:kg|g|ml|l|cups?|tsp|tbsp|lbs?|oz|%|hrs?|hours?|mins?|minutes?)\\b)") { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                var year = m.groupValues[3].toIntOrNull()
                if (year != null && year < 100) year += 2000
                // day/month by default; fall back to month/day when that is the only valid reading
                if (b in 1..12) yearless(b, a, year) ?: (if (a in 1..12) yearless(a, b, year) else null)
                else if (a in 1..12) yearless(a, b, year) else null
            },
            DateRule("\\b($MONTHS)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?!\\s*(?:am|pm|a\\.m|p\\.m|:\\d))(?:,?\\s+(20\\d{2})\\b)?") { m ->
                val day = m.groupValues[2].toInt()
                yearless(monthOf(m.groupValues[1]), day, m.groupValues[3].toIntOrNull())
            },
            DateRule("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($MONTHS)\\b(?:,?\\s+(20\\d{2})\\b)?") { m ->
                val day = m.groupValues[1].toInt()
                yearless(monthOf(m.groupValues[2]), day, m.groupValues[3].toIntOrNull())
            },
            DateRule("\\bday\\s+after\\s+(?:tomorrow|tmrw|tmr)\\b") { today.plusDays(2) },
            DateRule("\\b(?:tomorrow|tmrw|tmr|tmw|2moro|2morrow)\\b") { today.plusDays(1) },
            DateRule("\\b(?:today|2day|tdy)\\b") { today },
            DateRule("\\b(?:tonight|tonite)\\b") { timeHint = TimeDefaults.NIGHT; today },
            DateRule("\\bthis\\s+(morning|afternoon|evening)\\b") { m ->
                timeHint = when (m.groupValues[1].lowercase(Locale.ROOT)) {
                    "morning" -> TimeDefaults.MORNING
                    "afternoon" -> TimeDefaults.AFTERNOON
                    else -> TimeDefaults.EVENING
                }
                today
            },
            DateRule("\\bnext\\s+week\\b") { today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) },
            DateRule("\\bnext\\s+month\\b") { today.plusMonths(1).withDayOfMonth(1) },
            DateRule("\\b(this\\s+|next\\s+)?weekend\\b") { m ->
                val sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                val base = if (today.dayOfWeek == DayOfWeek.SUNDAY) today else sat
                if (m.groupValues[1].trim().equals("next", true)) sat.plusWeeks(1) else base
            },
            DateRule("\\b(?:end\\s+of\\s+(?:the\\s+)?month|eom)\\b") { YearMonth.from(today).atEndOfMonth() },
            DateRule("\\b(?:end\\s+of\\s+(?:the\\s+)?week|eow)\\b") { today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)) },
            DateRule("\\b(?:end\\s+of\\s+(?:the\\s+)?year|eoy)\\b") { LocalDate.of(today.year, 12, 31) },
            DateRule("\\b(?:(this|next|coming|upcoming)\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b") { m ->
                weekdayResolve(m.groupValues[1].ifEmpty { null }, dowOf(m.groupValues[2]))
            },
            DateRule("\\b(?:(?:on|by|due|before)\\s+|(this|next|coming)\\s+)(mon|tues?|weds?|thu|thurs?|fri|sat|sun)\\b") { m ->
                weekdayResolve(m.groupValues[1].ifEmpty { null }, dowOf(m.groupValues[2]))
            },
            DateRule("\\b(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)\\b") { m ->
                val day = m.groupValues[1].toInt()
                if (day !in 1..31) null
                else {
                    val thisMonth = YearMonth.from(today)
                    val cand = thisMonth.atDay(day.coerceAtMost(thisMonth.lengthOfMonth()))
                    if (!cand.isBefore(today)) cand
                    else {
                        val nm = thisMonth.plusMonths(1)
                        nm.atDay(day.coerceAtMost(nm.lengthOfMonth()))
                    }
                }
            },
        )

        fun parseDate() {
            if (date != null) return
            for (rule in dateRules) {
                val ok = find(rule.plain, TokenKind.DATE) { m ->
                    val d = rule.resolve(m)
                    if (d != null) { date = d; true } else false
                }
                if (ok) return
            }
        }

        // ---- times ------------------------------------------------------------------------

        fun to24(hourText: String, hour: Int, ampm: String?): Int? {
            if (ampm != null && ampm.isNotEmpty()) {
                if (hour !in 1..12) return null
                val pm = ampm.lowercase(Locale.ROOT).startsWith("p")
                return when {
                    hour == 12 -> if (pm) 12 else 0
                    pm -> hour + 12
                    else -> hour
                }
            }
            if (hour !in 0..23) return null
            if (hourText.length == 2 && hourText.startsWith("0")) return hour
            return when (hour) {
                in 1..6 -> hour + 12
                else -> hour
            }
        }

        fun setTime(h: Int?, m: Int): Boolean {
            if (h == null || m !in 0..59) return false
            time = LocalTime.of(h, m)
            return true
        }

        fun parseTime() {
            var got = false
            if (!got) got = find(Rx.timeColon, TokenKind.TIME) { m ->
                val hText = m.groupValues[1]
                val h = hText.toInt()
                val ok = setTime(to24(hText, h, m.groupValues[3]), m.groupValues[2].toInt())
                if (ok && m.groupValues[3].isEmpty() && h in 1..11 && !(hText.length == 2 && hText.startsWith("0"))) {
                    ambigHour = h
                    ambigMinute = m.groupValues[2].toInt()
                }
                ok
            }
            if (!got) got = find(Rx.timeDot, TokenKind.TIME) { m ->
                setTime(to24(m.groupValues[1], m.groupValues[1].toInt(), m.groupValues[3]), m.groupValues[2].toInt())
            }
            if (!got) got = find(Rx.timeHour, TokenKind.TIME) { m ->
                setTime(to24(m.groupValues[1], m.groupValues[1].toInt(), m.groupValues[2]), 0)
            }
            if (!got) got = find(Rx.timeAtBare, TokenKind.TIME) { m ->
                val h = m.groupValues[1].toInt()
                val h24 = when {
                    h in 1..6 -> h + 12
                    h in 7..23 || h == 0 -> h
                    else -> null
                }
                val ok = setTime(h24, 0)
                if (ok && h in 1..11) { ambigHour = h; ambigMinute = 0 }
                ok
            }
            // keyword times; claimed even when an explicit time already exists, so they never leak into the title
            find(Rx.noon, TokenKind.TIME) { if (time == null) time = TimeDefaults.NOON; true }
            find(Rx.midnight, TokenKind.TIME) {
                if (time == null) { time = LocalTime.MIDNIGHT; midnightHint = true }
                true
            }
            find(Rx.eod, TokenKind.TIME) { if (time == null) time = TimeDefaults.EOD; true }
            find(Rx.todWord, TokenKind.TIME) { m ->
                val lead = m.groupValues[1]
                val word = m.groupValues[2].lowercase(Locale.ROOT)
                val start = m.range.first
                // a bare "evening" counts when it ends the text and something precedes it ("call mom evening")
                val trailing = text.substring(m.range.last + 1).isBlank() && text.substring(0, start).isNotBlank()
                val ok = lead.isNotEmpty() || adjacentToDateOrRepeat(start) || trailing
                if (ok && time == null) {
                    time = when (word) {
                        "morning" -> TimeDefaults.MORNING
                        "afternoon" -> TimeDefaults.AFTERNOON
                        "evening" -> TimeDefaults.EVENING
                        else -> TimeDefaults.NIGHT
                    }
                }
                ok
            }
        }

        // ---- defaults / inference ---------------------------------------------------------

        fun resolveDefaults() {
            if (time == null && timeHint != null) time = timeHint

            // "at 7" without am/pm: for today, pick the next 7 that is still ahead of us
            val ah = ambigHour
            if (ah != null && time != null && (date == null || date == today)) {
                val am = LocalTime.of(ah, ambigMinute)
                val pm = LocalTime.of(ah + 12, ambigMinute)
                val nowT = now.toLocalTime()
                time = when {
                    am.isAfter(nowT) -> am
                    pm.isAfter(nowT) -> pm
                    else -> time
                }
            }

            val r = rec
            if (r != null && date == null) {
                val start = defaultDate ?: today
                var d = RecurrenceCalc.firstOccurrence(start, r)
                if (defaultDate == null && time != null && d == today && !time!!.isAfter(now.toLocalTime())) {
                    d = RecurrenceCalc.firstOccurrence(today.plusDays(1), r)
                }
                date = d
            } else if (date == null && time != null) {
                date = defaultDate ?: when {
                    midnightHint -> today.plusDays(1)
                    time!!.isAfter(now.toLocalTime()) -> today
                    else -> today.plusDays(1)
                }
            } else if (date == null && defaultDate != null) {
                date = defaultDate
            }
            if (midnightHint && date == today) date = today.plusDays(1)

            val se = spanEnd
            val d0 = date
            if (se != null && d0 != null && rec != null) {
                val end = when {
                    se.second.startsWith("d") -> d0.plusDays(se.first.toLong())
                    se.second.startsWith("w") -> d0.plusWeeks(se.first.toLong())
                    se.second.startsWith("m") -> d0.plusMonths(se.first.toLong())
                    else -> d0.plusYears(se.first.toLong())
                }
                rec = rec!!.copy(endType = EndType.ON_DATE, endDate = end)
            }
        }

        // ---- title ------------------------------------------------------------------------

        fun buildTitle(): String {
            val sb = StringBuilder()
            var i = 0
            val sorted = claimed.sortedBy { it[0] }
            for (c in sorted) {
                if (c[0] > i) sb.append(text, i, c[0])
                sb.append(' ')
                i = maxOf(i, c[1])
            }
            if (i < text.length) sb.append(text, i, text.length)
            var t = sb.toString().replace(Regex("\\s+"), " ").trim()
            val lead = Regex("^(?:(?:on|at|by|due|from|until|till|starting|starts|beginning|and|then)\\s+|[,;:\\-–—]\\s*)+", RegexOption.IGNORE_CASE)
            val trail = Regex("(?:\\s+(?:on|at|by|in|due|from|until|till|starting|starts|beginning|and|every|each|for|to|of|the|next|this|then)|\\s*[,;:\\-–—])+$", RegexOption.IGNORE_CASE)
            var guard = 0
            while (guard++ < 4) {
                val before = t
                t = lead.replace(t, "").trim()
                t = trail.replace(t, "").trim()
                if (t == before) break
            }
            if (t.isNotEmpty() && t[0].isLowerCase()) t = t.replaceFirstChar { it.titlecase(Locale.ROOT) }
            return t
        }
    }
}
