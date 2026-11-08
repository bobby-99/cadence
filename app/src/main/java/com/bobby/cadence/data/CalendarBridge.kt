package com.bobby.cadence.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Reads and writes the phone's own calendar (CalendarContract). Nothing here talks to the internet. */
object CalendarBridge {
    data class Cal(val id: Long, val name: String, val account: String, val color: Int)

    data class DeviceEvent(
        val id: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val color: Int,
        val calendar: String,
        val calendarId: Long = 0L,
    )

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun canRead(ctx: Context) = granted(ctx, Manifest.permission.READ_CALENDAR)
    fun canWrite(ctx: Context) = canRead(ctx) && granted(ctx, Manifest.permission.WRITE_CALENDAR)

    /** Every visible calendar on the phone (any account), for choosing what to show. */
    fun allCalendars(ctx: Context): List<Cal> {
        if (!canRead(ctx)) return emptyList()
        val out = ArrayList<Cal>()
        runCatching {
            ctx.contentResolver.query(
                Calendars.CONTENT_URI,
                arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.CALENDAR_COLOR),
                "${Calendars.VISIBLE} = 1", null,
                "${Calendars.ACCOUNT_NAME} ASC, ${Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) out += Cal(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: "", c.getInt(3))
            }
        }
        return out
    }

    /** Calendars Cadence is allowed to add events to. */
    fun writableCalendars(ctx: Context): List<Cal> {
        if (!canRead(ctx)) return emptyList()
        val out = ArrayList<Cal>()
        runCatching {
            ctx.contentResolver.query(
                Calendars.CONTENT_URI,
                arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.CALENDAR_COLOR),
                "${Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${Calendars.VISIBLE} = 1",
                arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                "${Calendars.IS_PRIMARY} DESC, ${Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) out += Cal(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: "", c.getInt(3))
            }
        }
        return out
    }

    /** The calendar you picked in Settings, else the first writable one. */
    fun chooseCalendar(ctx: Context, preferredId: Long): Cal? {
        val list = writableCalendars(ctx)
        return list.firstOrNull { it.id == preferredId } ?: list.firstOrNull()
    }

    // ---- write ------------------------------------------------------------------------------

    private val DAY = mapOf(
        DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
        DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
    )

    /** Calendar repeat rule for a task's repeat, or null when it cannot be expressed (counted-from-completion repeats). */
    fun rrule(r: Recurrence, due: LocalDate, allDay: Boolean): String? {
        if (r.basis == RepeatBasis.FROM_COMPLETION) return null
        val parts = ArrayList<String>()
        when (r.unit) {
            RepeatUnit.DAY -> parts += "FREQ=DAILY"
            RepeatUnit.WEEK -> {
                parts += "FREQ=WEEKLY"
                if (r.weekDays.isNotEmpty()) parts += "BYDAY=" + r.weekDays.sortedBy { it.value }.joinToString(",") { DAY.getValue(it) }
            }
            RepeatUnit.MONTH -> {
                parts += "FREQ=MONTHLY"
                when (r.monthMode) {
                    MonthMode.DAY_OF_MONTH -> parts += "BYMONTHDAY=" + (if (r.dayOfMonth > 0) r.dayOfMonth else due.dayOfMonth)
                    MonthMode.NTH_WEEKDAY -> {
                        val n = if (r.nth >= 5 || r.nth < 0) -1 else r.nth
                        parts += "BYDAY=$n" + DAY.getValue(r.nthWeekday)
                    }
                    MonthMode.LAST_DAY -> parts += "BYMONTHDAY=-1"
                }
            }
            RepeatUnit.YEAR -> parts += "FREQ=YEARLY"
        }
        if (r.interval > 1) parts += "INTERVAL=${r.interval}"
        when (r.endType) {
            EndType.ON_DATE -> r.endDate?.let {
                parts += "UNTIL=" + it.format(DateTimeFormatter.BASIC_ISO_DATE) + (if (allDay) "" else "T235959Z")
            }
            EndType.AFTER_COUNT -> if (r.endCount > 0) parts += "COUNT=${r.endCount}"
            else -> {}
        }
        return parts.joinToString(";")
    }

    private fun values(t: Task, calendarId: Long?): ContentValues? {
        val date = t.date ?: return null
        val v = ContentValues()
        if (calendarId != null) v.put(Events.CALENDAR_ID, calendarId)
        v.put(Events.TITLE, t.title)
        v.put(Events.DESCRIPTION, t.notes)
        v.put(Events.HAS_ALARM, 0) // Cadence already reminds you; no double notifications
        val time = t.time
        val rule = t.rule
        if (time == null) {
            val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val rr = rule?.let { rrule(it, date, true) }
            v.put(Events.ALL_DAY, 1)
            v.put(Events.EVENT_TIMEZONE, "UTC")
            v.put(Events.DTSTART, start)
            if (rr != null) {
                v.put(Events.RRULE, rr); v.put(Events.DURATION, "P1D"); v.putNull(Events.DTEND)
            } else {
                v.put(Events.DTEND, start + 86_400_000L); v.putNull(Events.RRULE); v.putNull(Events.DURATION)
            }
        } else {
            val zone = ZoneId.systemDefault()
            val start = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            val rr = rule?.let { rrule(it, date, false) }
            v.put(Events.ALL_DAY, 0)
            v.put(Events.EVENT_TIMEZONE, zone.id)
            v.put(Events.DTSTART, start)
            if (rr != null) {
                v.put(Events.RRULE, rr); v.put(Events.DURATION, "PT${(t.durationMin ?: 30)}M"); v.putNull(Events.DTEND)
            } else {
                v.put(Events.DTEND, start + (t.durationMin ?: 30) * 60_000L); v.putNull(Events.RRULE); v.putNull(Events.DURATION)
            }
        }
        return v
    }

    /** Adds the task as an event. Returns the new event id, or null if it could not be written. */
    fun insert(ctx: Context, t: Task, calendarId: Long): Long? {
        if (!canWrite(ctx)) return null
        val v = values(t, calendarId) ?: return null
        return runCatching { ctx.contentResolver.insert(Events.CONTENT_URI, v)?.lastPathSegment?.toLongOrNull() }.getOrNull()
    }

    fun update(ctx: Context, eventId: Long, t: Task): Boolean {
        if (!canWrite(ctx)) return false
        val v = values(t, null) ?: return false
        return runCatching {
            ctx.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), v, null, null) > 0
        }.getOrDefault(false)
    }

    fun delete(ctx: Context, eventId: Long): Boolean {
        if (!canWrite(ctx)) return false
        return runCatching {
            ctx.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null) > 0
        }.getOrDefault(false)
    }

    // ---- read -------------------------------------------------------------------------------

    private fun daysOf(e: DeviceEvent): List<LocalDate> {
        val zone = if (e.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val first = Instant.ofEpochMilli(e.begin).atZone(zone).toLocalDate()
        val last = if (e.end > e.begin) Instant.ofEpochMilli(e.end - 1).atZone(zone).toLocalDate() else first
        val out = ArrayList<LocalDate>()
        var d = first
        var guard = 0
        while (!d.isAfter(last) && guard++ < 62) { out += d; d = d.plusDays(1) }
        return out
    }

    /** Events from your phone calendars between two days, grouped by day. Events Cadence itself wrote are left out. */
    fun byDay(ctx: Context, from: LocalDate, to: LocalDate, hideIds: Set<Long>, hiddenCalendars: Set<Long> = emptySet()): Map<LocalDate, List<DeviceEvent>> {
        if (!canRead(ctx)) return emptyMap()
        val zone = ZoneId.systemDefault()
        val b = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val e = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, b)
            ContentUris.appendId(it, e)
        }.build()
        val out = HashMap<LocalDate, MutableList<DeviceEvent>>()
        runCatching {
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
                    Instances.ALL_DAY, Instances.CALENDAR_COLOR, Instances.CALENDAR_DISPLAY_NAME, Instances.CALENDAR_ID,
                ),
                null, null, "${Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val calId = c.getLong(7)
                    if (id in hideIds || calId in hiddenCalendars) continue
                    val ev = DeviceEvent(
                        id, c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)", c.getLong(2), c.getLong(3),
                        c.getInt(4) == 1, c.getInt(5), c.getString(6) ?: "", calId,
                    )
                    for (d in daysOf(ev)) if (!d.isBefore(from) && !d.isAfter(to)) out.getOrPut(d) { ArrayList() }.add(ev)
                }
            }
        }
        return out
    }
}
