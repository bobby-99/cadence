package com.bobby.cadence.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** epoch day, null = no date */
    val dueDate: Long? = null,
    /** minutes from midnight, null = all-day */
    val dueTime: Int? = null,
    /** 0 none, 1 low, 2 medium, 3 high */
    val priority: Int = 0,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** comma separated "minutes before due" offsets; empty = no reminder */
    val reminders: String = "",
    /** JSON of [Recurrence], null = does not repeat */
    val recurrence: String? = null,
    /** which occurrence of the series this row is (1-based) */
    val occurrence: Int = 1,
    val seriesId: Long = 0,
    val snoozedUntil: Long? = null,
    /** id of the matching event in the phone calendar, if the task was added to it */
    val calendarEventId: Long? = null,
    /** planned length in minutes; used for calendar blocks and focus sessions */
    val durationMin: Int? = null,
)

val Task.date: LocalDate? get() = dueDate?.let { LocalDate.ofEpochDay(it) }
val Task.time: LocalTime? get() = dueTime?.let { LocalTime.of(it / 60, it % 60) }
val Task.reminderOffsets: List<Int>
    get() = if (reminders.isBlank()) emptyList() else reminders.split(',').mapNotNull { it.trim().toIntOrNull() }
val Task.rule: Recurrence? get() = recurrence?.let { RecurrenceJson.parse(it) }

object RecurrenceJson {
    fun toJson(r: Recurrence): String = JSONObject().apply {
        put("u", r.unit.name)
        put("i", r.interval)
        put("wd", JSONArray(r.weekDays.map { it.value }))
        put("mm", r.monthMode.name)
        put("dom", r.dayOfMonth)
        put("nth", r.nth)
        put("nwd", r.nthWeekday.value)
        put("et", r.endType.name)
        r.endDate?.let { put("ed", it.toEpochDay()) }
        put("ec", r.endCount)
        put("b", r.basis.name)
    }.toString()

    fun parse(s: String): Recurrence? = try {
        val o = JSONObject(s)
        val days = HashSet<DayOfWeek>()
        val arr = o.optJSONArray("wd")
        if (arr != null) for (i in 0 until arr.length()) days.add(DayOfWeek.of(arr.getInt(i)))
        Recurrence(
            unit = RepeatUnit.valueOf(o.optString("u", "DAY")),
            interval = o.optInt("i", 1).coerceAtLeast(1),
            weekDays = days,
            monthMode = MonthMode.valueOf(o.optString("mm", "DAY_OF_MONTH")),
            dayOfMonth = o.optInt("dom", 0),
            nth = o.optInt("nth", 1),
            nthWeekday = DayOfWeek.of(o.optInt("nwd", 1)),
            endType = EndType.valueOf(o.optString("et", "NEVER")),
            endDate = if (o.has("ed")) LocalDate.ofEpochDay(o.getLong("ed")) else null,
            endCount = o.optInt("ec", 0),
            basis = RepeatBasis.valueOf(o.optString("b", "FROM_DUE")),
        )
    } catch (e: Exception) {
        null
    }
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>)

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("DELETE FROM tasks WHERE completed = 1 AND completedAt IS NOT NULL AND completedAt < :before")
    suspend fun deleteCompletedBefore(before: Long): Int

    @Query("DELETE FROM tasks WHERE completed = 1")
    suspend fun deleteAllCompleted(): Int
}

@Database(entities = [Task::class, FocusSession::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun focusDao(): FocusDao

    companion object {
        fun build(ctx: android.content.Context): AppDatabase =
            Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "cadence.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN durationMin INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS focus_sessions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startedAt INTEGER NOT NULL, " +
                        "seconds INTEGER NOT NULL, plannedSeconds INTEGER NOT NULL, mode INTEGER NOT NULL, " +
                        "tag TEXT NOT NULL, taskId INTEGER NOT NULL, taskTitle TEXT NOT NULL, completed INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN calendarEventId INTEGER")
            }
        }
    }
}
