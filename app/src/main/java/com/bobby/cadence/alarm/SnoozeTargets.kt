package com.bobby.cadence.alarm

import java.time.Duration
import java.time.ZonedDateTime

/** "This evening" and "Tomorrow morning" snooze targets. Same defaults the task parser uses (morning 8 AM, evening 6 PM). */
object SnoozeTargets {
    const val MORNING_MIN = 8 * 60
    const val EVENING_MIN = 18 * 60

    private fun at(minuteOfDay: Int, dayOffset: Long, now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(dayOffset).atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(now.zone)

    fun eveningAvailable(now: ZonedDateTime = ZonedDateTime.now()): Boolean =
        Duration.between(now, at(EVENING_MIN, 0, now)).toMinutes() >= 1

    fun eveningMillis(now: ZonedDateTime = ZonedDateTime.now()): Long = at(EVENING_MIN, 0, now).toInstant().toEpochMilli()

    fun tomorrowMorningMillis(now: ZonedDateTime = ZonedDateTime.now()): Long = at(MORNING_MIN, 1, now).toInstant().toEpochMilli()

    /** Whole minutes from now until [millis], at least 1. */
    fun minutesUntil(millis: Long): Int =
        (((millis - System.currentTimeMillis()) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)

    /** The "later" choice for a notification button: this evening if it is still ahead, else tomorrow morning. */
    fun smartLater(): Pair<String, Long> =
        if (eveningAvailable()) "This evening" to eveningMillis() else "Tomorrow morning" to tomorrowMorningMillis()
}
