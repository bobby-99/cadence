package com.bobby.cadence.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bobby.cadence.MainActivity
import com.bobby.cadence.R
import com.bobby.cadence.data.Actions
import com.bobby.cadence.data.FocusDao
import com.bobby.cadence.data.FocusSession
import com.bobby.cadence.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Everything the focus timer needs, stored as plain timestamps so it survives the app being closed. */
data class FocusState(
    val active: Boolean = false,
    /** 0 = pomodoro, 1 = freestyle */
    val mode: Int = 0,
    /** 0 = focus, 1 = short break, 2 = long break */
    val phase: Int = 0,
    /** length of this phase; 0 for freestyle */
    val plannedMs: Long = 0L,
    /** when the running stretch started; 0 while paused or waiting */
    val segStart: Long = 0L,
    /** time already used in this phase before the running stretch */
    val elapsedBefore: Long = 0L,
    /** when this phase first started, for the session log */
    val phaseBegan: Long = 0L,
    /** focus rounds finished in the current set */
    val cycle: Int = 0,
    /** focus rounds finished since this session began */
    val rounds: Int = 0,
    val tag: String = "",
    val taskId: Long = 0L,
    val taskTitle: String = "",
    /** freestyle target in minutes, 0 = none */
    val goalMin: Int = 0,
) {
    val running: Boolean get() = segStart > 0L
    fun elapsed(now: Long): Long = elapsedBefore + if (running) (now - segStart).coerceAtLeast(0L) else 0L
    fun remaining(now: Long): Long = (plannedMs - elapsed(now)).coerceAtLeast(0L)
    /** true when it is a break the user has not started yet, or a focus waiting to start */
    val waiting: Boolean get() = active && !running && elapsedBefore == 0L
}

class FocusEngine(private val ctx: Context, private val dao: FocusDao, private val prefs: Prefs) {
    private val sp = ctx.getSharedPreferences("cadence_focus", Context.MODE_PRIVATE)
    private val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val flow = MutableStateFlow(load())
    val state: StateFlow<FocusState> = flow

    // ---- persistence ------------------------------------------------------------------------

    private fun load(): FocusState = FocusState(
        active = sp.getBoolean("active", false),
        mode = sp.getInt("mode", 0),
        phase = sp.getInt("phase", 0),
        plannedMs = sp.getLong("plannedMs", 0L),
        segStart = sp.getLong("segStart", 0L),
        elapsedBefore = sp.getLong("elapsedBefore", 0L),
        phaseBegan = sp.getLong("phaseBegan", 0L),
        cycle = sp.getInt("cycle", 0),
        rounds = sp.getInt("rounds", 0),
        tag = sp.getString("tag", "") ?: "",
        taskId = sp.getLong("taskId", 0L),
        taskTitle = sp.getString("taskTitle", "") ?: "",
        goalMin = sp.getInt("goalMin", 0),
    )

    private fun set(s: FocusState) {
        flow.value = s
        sp.edit()
            .putBoolean("active", s.active).putInt("mode", s.mode).putInt("phase", s.phase)
            .putLong("plannedMs", s.plannedMs).putLong("segStart", s.segStart)
            .putLong("elapsedBefore", s.elapsedBefore).putLong("phaseBegan", s.phaseBegan)
            .putInt("cycle", s.cycle).putInt("rounds", s.rounds)
            .putString("tag", s.tag).putLong("taskId", s.taskId).putString("taskTitle", s.taskTitle)
            .putInt("goalMin", s.goalMin)
            .apply()
        refreshAlarm(s)
        refreshNotification(s)
    }

    // ---- controls ---------------------------------------------------------------------------

    /** [minutes] overrides the focus length for this session (for example a task's own duration). */
    @Synchronized
    fun start(mode: Int, tag: String, taskId: Long, taskTitle: String, minutes: Int? = null) {
        val st = prefs.state.value
        val now = System.currentTimeMillis()
        val planned = if (mode == 0) (minutes ?: st.focusMin) * 60_000L else 0L
        set(
            FocusState(
                active = true, mode = mode, phase = 0, plannedMs = planned, segStart = now, elapsedBefore = 0L,
                phaseBegan = now, cycle = 0, rounds = 0, tag = tag, taskId = taskId, taskTitle = taskTitle,
                goalMin = if (mode == 1) st.freestyleGoalMin else 0,
            ),
        )
    }

    @Synchronized
    fun pause() {
        val s = flow.value
        if (!s.active || !s.running) return
        set(s.copy(elapsedBefore = s.elapsed(System.currentTimeMillis()), segStart = 0L))
    }

    @Synchronized
    fun resume() {
        val s = flow.value
        if (!s.active || s.running) return
        val now = System.currentTimeMillis()
        set(s.copy(segStart = now, phaseBegan = if (s.elapsedBefore == 0L) now else s.phaseBegan))
    }

    @Synchronized
    fun toggle() {
        if (flow.value.running) pause() else resume()
    }

    /** Ends the session. Focus time so far is saved when it is long enough to count. */
    @Synchronized
    fun stop() {
        val s = flow.value
        if (!s.active) return
        val now = System.currentTimeMillis()
        if (s.phase == 0) logIfLongEnough(s, now, completed = false)
        cancelAll()
        set(FocusState())
    }

    /** Skips to the next phase (focus -> break, break -> focus). */
    @Synchronized
    fun skip() {
        val s = flow.value
        if (!s.active || s.mode != 0) return
        advance(natural = false)
    }

    /** Called by the alarm, or by the screen when the countdown reaches zero. */
    @Synchronized
    fun onPhaseEnd() {
        val s = flow.value
        if (!s.active || s.mode != 0 || !s.running) return
        if (s.remaining(System.currentTimeMillis()) > 2_000L) {
            refreshAlarm(s) // fired early (clock change): put it back
            return
        }
        advance(natural = true)
    }

    /** After a reboot or a process restart: re-arm the alarm or finish a phase that ended meanwhile. */
    @Synchronized
    fun onBoot() {
        val s = flow.value
        if (!s.active) return
        if (s.mode == 0 && s.running && s.remaining(System.currentTimeMillis()) <= 0L) advance(natural = true)
        else set(s)
    }

    private fun advance(natural: Boolean) {
        val s = flow.value
        val st = prefs.state.value
        val now = System.currentTimeMillis()
        var cycle = s.cycle
        var rounds = s.rounds
        val nextPhase: Int
        if (s.phase == 0) {
            if (natural) {
                logSession(s, s.plannedMs, completed = true)
                cycle++
                rounds++
            } else {
                logIfLongEnough(s, now, completed = false)
            }
            nextPhase = if (natural && cycle >= st.cyclesUntilLong.coerceAtLeast(1)) 2 else 1
            if (nextPhase == 2) cycle = 0
        } else {
            nextPhase = 0
        }
        val minutes = when (nextPhase) {
            0 -> st.focusMin
            1 -> st.shortBreakMin
            else -> st.longBreakMin
        }
        val auto = if (nextPhase == 0) st.autoStartFocus else st.autoStartBreaks
        val autoNow = natural && auto
        val next = s.copy(
            phase = nextPhase, plannedMs = minutes * 60_000L, elapsedBefore = 0L,
            segStart = if (autoNow) now else 0L, phaseBegan = now, cycle = cycle, rounds = rounds,
        )
        if (natural) alert(s, nextPhase, st.cyclesUntilLong, autoNow, rounds)
        set(next)
    }

    // ---- session log ------------------------------------------------------------------------

    private fun logSession(s: FocusState, focusedMs: Long, completed: Boolean) {
        val seconds = (focusedMs / 1000L).toInt()
        if (seconds <= 0) return
        val row = FocusSession(
            startedAt = s.phaseBegan.takeIf { it > 0L } ?: (System.currentTimeMillis() - focusedMs),
            seconds = seconds, plannedSeconds = (s.plannedMs / 1000L).toInt(), mode = s.mode,
            tag = s.tag, taskId = s.taskId, taskTitle = s.taskTitle, completed = completed,
        )
        scope.launch { try { dao.insert(row) } catch (e: Exception) { } }
    }

    private fun logIfLongEnough(s: FocusState, now: Long, completed: Boolean) {
        val ms = s.elapsed(now)
        if (ms >= MIN_LOG_MS) logSession(s, ms, completed)
    }

    // ---- alarm ------------------------------------------------------------------------------

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7001, Intent(ctx, FocusReceiver::class.java).setAction(ACTION_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        ctx, 7003,
        Intent(ctx, MainActivity::class.java).setAction(Actions.OPEN_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun cancelAll() {
        try { am.cancel(alarmIntent()) } catch (e: Exception) { }
        NotificationManagerCompat.from(ctx).cancel(NOTIF_RUN)
    }

    private fun refreshAlarm(s: FocusState) {
        try {
            am.cancel(alarmIntent())
            if (s.active && s.mode == 0 && s.running) {
                val at = System.currentTimeMillis() + s.remaining(System.currentTimeMillis())
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, openIntent()), alarmIntent())
            }
        } catch (e: Exception) {
        }
    }

    // ---- notifications ----------------------------------------------------------------------

    private fun ensureChannels() {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH_RUN) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_RUN, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows the running focus timer"
                    setShowBadge(false)
                },
            )
        }
        if (nm.getNotificationChannel(CH_ALERT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ALERT, "Focus alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When a focus round or break ends"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 150, 300)
                },
            )
        }
    }

    private fun btn(title: String, action: String, code: Int): NotificationCompat.Action {
        val pi = PendingIntent.getBroadcast(
            ctx, 7100 + code, Intent(ctx, FocusReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(0, title, pi).setAuthenticationRequired(false).build()
    }

    private fun phaseName(p: Int) = when (p) {
        0 -> "Focus"
        1 -> "Short break"
        else -> "Long break"
    }

    private fun mmss(ms: Long): String {
        val t = (ms / 1000L).toInt()
        return "%02d:%02d".format(t / 60, t % 60)
    }

    private fun refreshNotification(s: FocusState) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!s.active) { nm.cancel(NOTIF_RUN); return }
        if (!nm.areNotificationsEnabled()) return
        ensureChannels()
        val now = System.currentTimeMillis()
        val sub = listOf(s.tag, s.taskTitle).filter { it.isNotBlank() }.joinToString(" · ")
        val b = NotificationCompat.Builder(ctx, CH_RUN)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setColor(0xFFF2B04A.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent())
        if (s.mode == 1) {
            b.setContentTitle(if (s.running) "Focus · freestyle" else "Focus · paused")
            b.setContentText(sub.ifBlank { "Counting up" })
            if (s.running) b.setUsesChronometer(true).setWhen(now - s.elapsed(now)).setShowWhen(true)
            else b.setContentText((if (sub.isBlank()) "" else "$sub · ") + mmss(s.elapsed(now)))
            b.addAction(btn(if (s.running) "Pause" else "Resume", ACTION_TOGGLE, 1))
            b.addAction(btn("Finish", ACTION_STOP, 3))
        } else {
            val name = phaseName(s.phase)
            when {
                s.running -> {
                    b.setContentTitle(name)
                    b.setContentText(sub.ifBlank { "Stay with it" })
                    b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(now + s.remaining(now)).setShowWhen(true)
                    b.addAction(btn("Pause", ACTION_TOGGLE, 1))
                    b.addAction(btn("Skip", ACTION_SKIP, 2))
                    b.addAction(btn("Stop", ACTION_STOP, 3))
                }
                s.waiting -> {
                    b.setContentTitle("$name is ready")
                    b.setContentText("${mmss(s.plannedMs)} · tap Start")
                    b.addAction(btn("Start", ACTION_TOGGLE, 1))
                    b.addAction(btn("Stop", ACTION_STOP, 3))
                }
                else -> {
                    b.setContentTitle("$name paused")
                    b.setContentText(mmss(s.remaining(now)) + " left" + (if (sub.isBlank()) "" else " · $sub"))
                    b.addAction(btn("Resume", ACTION_TOGGLE, 1))
                    b.addAction(btn("Stop", ACTION_STOP, 3))
                }
            }
        }
        try { nm.notify(NOTIF_RUN, b.build()) } catch (e: SecurityException) { }
    }

    private fun alert(from: FocusState, nextPhase: Int, cyclesUntilLong: Int, started: Boolean, rounds: Int) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels()
        val (title, text) = if (from.phase == 0) {
            "Focus complete" to (
                (if (nextPhase == 2) "Long break time. " else "Take a short break. ") +
                    "Round $rounds done" + (if (from.taskTitle.isNotBlank()) " · ${from.taskTitle}" else "")
                )
        } else {
            "Break over" to (if (started) "Next focus round has started." else "Ready for the next round?")
        }
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setColor(0xFFF2B04A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openIntent())
            .build()
        try { nm.notify(NOTIF_ALERT, n) } catch (e: SecurityException) { }
    }

    companion object {
        const val ACTION_END = "com.bobby.cadence.FOCUS_END"
        const val ACTION_TOGGLE = "com.bobby.cadence.FOCUS_TOGGLE"
        const val ACTION_SKIP = "com.bobby.cadence.FOCUS_SKIP"
        const val ACTION_STOP = "com.bobby.cadence.FOCUS_STOP"
        private const val CH_RUN = "focus_running"
        private const val CH_ALERT = "focus_alert"
        private const val NOTIF_RUN = 7010
        private const val NOTIF_ALERT = 7011
        /** a stretch shorter than this is not saved */
        const val MIN_LOG_MS = 30_000L
    }
}
