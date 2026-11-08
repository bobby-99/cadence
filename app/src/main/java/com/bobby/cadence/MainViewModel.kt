package com.bobby.cadence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobby.cadence.data.AppSettings
import com.bobby.cadence.data.Recurrence
import com.bobby.cadence.data.RecurrenceJson
import com.bobby.cadence.data.MonthMode
import com.bobby.cadence.data.RepeatUnit
import com.bobby.cadence.data.Task
import com.bobby.cadence.data.date
import com.bobby.cadence.data.reminderOffsets
import com.bobby.cadence.data.rule
import com.bobby.cadence.data.time
import com.bobby.cadence.ui.Section
import com.bobby.cadence.ui.TaskLogic
import com.bobby.cadence.ui.UiFilter
import com.bobby.cadence.ui.ViewKind
import com.bobby.cadence.ui.DayMark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Editable copy of a task, used by the editor and quick add. */
data class Draft(
    val base: Task? = null,
    val title: String = "",
    val notes: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val priority: Int = 0,
    val reminders: List<Int> = emptyList(),
    val rule: Recurrence? = null,
    /** also save this task as an event in the phone calendar */
    val addToCalendar: Boolean = false,
    /** planned length in minutes */
    val durationMin: Int? = null,
) {
    companion object {
        fun from(t: Task) = Draft(t, t.title, t.notes, t.date, t.time, t.priority, t.reminderOffsets, t.rule, t.calendarEventId != null, t.durationMin)
    }

    fun toTask(): Task {
        var r = rule
        if (r != null && r.unit == RepeatUnit.MONTH && r.monthMode == MonthMode.DAY_OF_MONTH && r.dayOfMonth == 0 && date != null) {
            r = r.copy(dayOfMonth = date.dayOfMonth)
        }
        val rem = if (date == null) "" else reminders.distinct().sortedDescending().joinToString(",")
        val minutes = time?.let { it.hour * 60 + it.minute }
        val b = base
        return if (b != null) {
            b.copy(
                title = title.trim(), notes = notes.trim(), dueDate = date?.toEpochDay(), dueTime = minutes,
                priority = priority, reminders = rem, recurrence = r?.let { RecurrenceJson.toJson(it) },
                snoozedUntil = null, durationMin = durationMin,
            )
        } else {
            Task(
                title = title.trim(), notes = notes.trim(), dueDate = date?.toEpochDay(), dueTime = minutes,
                priority = priority, reminders = rem, recurrence = r?.let { RecurrenceJson.toJson(it) },
                durationMin = durationMin,
            )
        }
    }
}

fun startFilter(s: AppSettings): UiFilter {
    val v = runCatching { ViewKind.valueOf(s.startView) }.getOrDefault(ViewKind.ALL)
    return UiFilter(view = v, base = v)
}

data class HomeState(
    val sections: List<Section> = emptyList(),
    val counts: Map<ViewKind, Int> = emptyMap(),
    val doneToday: Int = 0,
    val totalToday: Int = 0,
    val overdue: Int = 0,
    val loaded: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val capp = app as CadenceApp
    val repo = capp.repo
    val prefs = capp.prefs

    val today = MutableStateFlow(LocalDate.now())
    val filter = MutableStateFlow(startFilter(prefs.state.value))
    val settings: StateFlow<AppSettings> = prefs.state

    private val loadedFlag = MutableStateFlow(false)

    // ---- focus ----
    val focus = capp.focus
    val focusState = capp.focus.state
    val sessions: StateFlow<List<com.bobby.cadence.data.FocusSession>> = capp.db.focusDao().observeAll().let { f ->
        val st = MutableStateFlow<List<com.bobby.cadence.data.FocusSession>>(emptyList())
        viewModelScope.launch { f.collect { st.value = it } }
        st
    }
    fun startFocus(mode: Int, tag: String, task: Task?, minutes: Int?) {
        capp.focus.start(mode, tag, task?.id ?: 0L, task?.title ?: "", minutes)
    }
    fun deleteSession(s: com.bobby.cadence.data.FocusSession) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { capp.db.focusDao().delete(s.id) }
    }

    val tasks: StateFlow<List<Task>> = repo.tasks.also { }.let { flow ->
        val state = MutableStateFlow<List<Task>>(emptyList())
        viewModelScope.launch {
            flow.collect {
                state.value = it
                loadedFlag.value = true
            }
        }
        state
    }

    val home: StateFlow<HomeState> = combine(tasks, filter, today, settings, loadedFlag) { all, f, td, s, loaded ->
        val counts = TaskLogic.counts(all, td)
        val (done, total) = TaskLogic.todayProgress(all, td)
        HomeState(
            sections = TaskLogic.sections(all, f, td, s),
            counts = counts,
            doneToday = done,
            totalToday = total,
            overdue = counts[ViewKind.OVERDUE] ?: 0,
            loaded = loaded,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    fun refreshToday() {
        val n = LocalDate.now()
        if (n != today.value) today.value = n
    }

    fun setView(v: ViewKind) { filter.value = filter.value.copy(view = v, day = null) }
    fun setDay(d: LocalDate?) { filter.value = filter.value.copy(day = d) }
    fun setPriority(p: Int?) { filter.value = filter.value.copy(priority = p) }
    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun resetFilter() { filter.value = startFilter(prefs.state.value) }

    /** [onSynced] is called with false when the task was saved but the phone-calendar event could not be written. */
    fun save(d: Draft, onSynced: ((Boolean) -> Unit)? = null) {
        if (d.title.isBlank()) return
        viewModelScope.launch {
            val t = d.toTask()
            val saved = if (d.base == null) (repo.get(repo.add(t)) ?: t) else { repo.update(t); t }
            val needsSync = d.addToCalendar || saved.calendarEventId != null
            if (needsSync) onSynced?.invoke(repo.syncCalendar(saved, d.addToCalendar)) else onSynced?.invoke(true)
        }
    }

    fun complete(t: Task) { viewModelScope.launch { repo.complete(t, today.value) } }
    fun uncomplete(t: Task) { viewModelScope.launch { repo.uncomplete(t) } }
    fun delete(t: Task) { viewModelScope.launch { repo.delete(t) } }
    fun restore(t: Task) { viewModelScope.launch { repo.restore(t) } }
    fun moveOverdue(onDone: (List<Task>) -> Unit) {
        viewModelScope.launch {
            val now = java.time.LocalTime.now()
            val old = repo.moveOverdueToToday(today.value, now.hour * 60 + now.minute)
            onDone(old)
        }
    }
    fun putBack(old: List<Task>) { viewModelScope.launch { repo.putBack(old) } }
    fun skip(t: Task) { viewModelScope.launch { repo.skip(t, today.value) } }
    fun snooze(t: Task, minutes: Int) { viewModelScope.launch { repo.snooze(t.id, minutes) } }
    /** Snoozes until [millis]; [onDone] gets the task as it was, for undo. Typed lengths are remembered. */
    fun snoozeUntil(t: Task, millis: Long, customMinutes: Int?, onDone: (Task?) -> Unit) {
        viewModelScope.launch {
            val old = repo.snoozeUntil(t.id, millis)
            if (customMinutes != null) prefs.update { s -> s.copy(recentSnoozes = (listOf(customMinutes) + s.recentSnoozes.filter { it != customMinutes }).take(4)) }
            onDone(old)
        }
    }

    fun marks(from: LocalDate, to: LocalDate): Map<LocalDate, DayMark> = TaskLogic.marks(tasks.value, from, to)

    fun update(block: (AppSettings) -> AppSettings) {
        prefs.update(block)
        // the default time changes when all-day reminders fire
        viewModelScope.launch { repo.rescheduleAll() }
    }

    fun clearCompleted(onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repo.clearCompleted()) }
    }

    fun backupNow(onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(com.bobby.cadence.data.BackupRunner.run(capp)) }
    }

    fun export(onJson: (String) -> Unit) {
        viewModelScope.launch { onJson(repo.exportJson()) }
    }

    fun import(text: String, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(repo.importJson(text)) }
    }
}
