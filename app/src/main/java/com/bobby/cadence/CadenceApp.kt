package com.bobby.cadence

import android.app.Application
import com.bobby.cadence.alarm.Notifier
import com.bobby.cadence.alarm.RescheduleWorker
import com.bobby.cadence.alarm.ReminderScheduler
import com.bobby.cadence.data.AppDatabase
import com.bobby.cadence.data.Prefs
import com.bobby.cadence.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CadenceApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var scheduler: ReminderScheduler
        private set
    lateinit var repo: TaskRepository
        private set
    lateinit var focus: com.bobby.cadence.alarm.FocusEngine
        private set

    // a failure in background work must never take the whole app (and its reminders) down
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> },
    )

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        db = AppDatabase.build(this)
        scheduler = ReminderScheduler(this, prefs)
        repo = TaskRepository(this, db.taskDao(), scheduler, prefs, db.focusDao())
        focus = com.bobby.cadence.alarm.FocusEngine(this, db.focusDao(), prefs)
        Notifier.ensureChannel(this)
        appScope.launch {
            try {
                repo.cleanup()
                repo.rescheduleAll()
                focus.onBoot()
            } catch (e: Exception) {
            }
        }
        // keep the home-screen widget in step with the task list
        appScope.launch {
            db.taskDao().observeAll().collect {
                kotlinx.coroutines.delay(300)
                try {
                    com.bobby.cadence.widget.QuickWidgetProvider.refresh(this@CadenceApp)
                } catch (e: Exception) {
                }
            }
        }
        try {
            RescheduleWorker.enqueue(this)
        } catch (e: Exception) {
        }
    }
}
