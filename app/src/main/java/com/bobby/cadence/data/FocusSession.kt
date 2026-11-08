package com.bobby.cadence.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One finished (or ended early) stretch of focus. Breaks are not stored. */
@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** epoch millis when the focus stretch began */
    val startedAt: Long,
    /** seconds actually focused */
    val seconds: Int,
    /** seconds that were planned; 0 for freestyle */
    val plannedSeconds: Int = 0,
    /** 0 = pomodoro, 1 = freestyle */
    val mode: Int = 0,
    val tag: String = "",
    val taskId: Long = 0,
    val taskTitle: String = "",
    /** true when a pomodoro ran its full planned length */
    val completed: Boolean = true,
)

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<FocusSession>

    @Insert
    suspend fun insert(s: FocusSession): Long

    @Insert
    suspend fun insertAll(list: List<FocusSession>)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()
}
