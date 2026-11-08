package com.bobby.cadence.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bobby.cadence.CadenceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } catch (e: Exception) {
            // a failing reminder must never crash the process
        } finally {
            pending.finish()
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        val idx = intent.getIntExtra(ReminderScheduler.EXTRA_IDX, 0)
        val at = intent.getLongExtra(ReminderScheduler.EXTRA_AT, 0L)
        if (id < 0) return
        val app = context.applicationContext as CadenceApp
        runAsync { app.repo.onReminderFired(id, idx, at) }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        if (id < 0) return
        val app = context.applicationContext as CadenceApp
        when (intent.action) {
            Notifier.ACTION_DONE -> runAsync { app.repo.completeById(id) }
            Notifier.ACTION_SKIP -> runAsync { app.repo.skipById(id) }
            Notifier.ACTION_SNOOZE -> {
                val until = intent.getLongExtra(Notifier.EXTRA_UNTIL, 0L)
                val minutes = if (until > 0L) SnoozeTargets.minutesUntil(until) else intent.getIntExtra(Notifier.EXTRA_MINUTES, 10)
                runAsync { app.repo.snooze(id, minutes) }
            }
        }
    }
}

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as CadenceApp
        when (intent.action) {
            FocusEngine.ACTION_END -> runAsync { app.focus.onPhaseEnd() }
            FocusEngine.ACTION_TOGGLE -> runAsync { app.focus.toggle() }
            FocusEngine.ACTION_SKIP -> runAsync { app.focus.skip() }
            FocusEngine.ACTION_STOP -> runAsync { app.focus.stop() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED && action != Intent.ACTION_TIMEZONE_CHANGED
        ) return
        val app = context.applicationContext as CadenceApp
        // a reminder that came due while the phone was off is shown now, once
        val late = action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
        runAsync {
            if (late) app.repo.deliverMissed()
            app.repo.rescheduleAll()
            app.focus.onBoot()
        }
    }
}
