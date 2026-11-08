package com.bobby.cadence

import com.bobby.cadence.nlp.TaskParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The parser is plain Kotlin, so it is tested on the JVM. Add a case here with every change to it. */
class TaskParserTest {
    // a fixed "now" keeps the tests stable: Sunday 8 November 2026, 10:00
    private val parser = TaskParser { LocalDateTime.of(2026, 11, 8, 10, 0) }

    @Test
    fun tomorrowAtSixPm() {
        val p = parser.parse("Call mom tomorrow 6pm")
        assertEquals(LocalDate.of(2026, 11, 9), p.date)
        assertEquals(LocalTime.of(18, 0), p.time)
    }

    @Test
    fun durationInMinutes() {
        assertEquals(45, parser.parse("Study for 45 min tomorrow 6pm").durationMin)
        assertEquals(30, parser.parse("Run for 30m at 6am").durationMin)
    }

    @Test
    fun durationInHours() {
        assertEquals(120, parser.parse("Deep work for 2 hours").durationMin)
    }

    @Test
    fun repeatEndingIsNotADuration() {
        val p = parser.parse("Pills daily for 2 weeks")
        assertNull(p.durationMin)
        assertNotNull(p.recurrence)
    }
}
