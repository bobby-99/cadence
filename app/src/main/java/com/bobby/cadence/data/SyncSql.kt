package com.bobby.cadence.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database triggers that keep the sync bookkeeping right without every piece of code having to
 * remember it:
 *  - a new task gets a uuid and an updatedAt, and any "deleted" mark for that uuid is cleared
 *  - a change to a synced field stamps updatedAt (changes to snooze or calendar-event id do not)
 *  - a deleted task leaves a tombstone
 */
object SyncSql {
    private const val NOW = "(strftime('%s','now') * 1000)"

    fun installTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS tasks_sync_ins AFTER INSERT ON tasks BEGIN " +
                "UPDATE tasks SET uuid = lower(hex(randomblob(16))) WHERE id = NEW.id AND uuid = ''; " +
                "UPDATE tasks SET updatedAt = $NOW WHERE id = NEW.id AND updatedAt = 0; " +
                "DELETE FROM deleted_tasks WHERE uuid = (SELECT uuid FROM tasks WHERE id = NEW.id); " +
                "END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS tasks_sync_upd AFTER UPDATE ON tasks WHEN " +
                "NEW.title IS NOT OLD.title OR NEW.notes IS NOT OLD.notes OR NEW.dueDate IS NOT OLD.dueDate OR " +
                "NEW.dueTime IS NOT OLD.dueTime OR NEW.priority IS NOT OLD.priority OR NEW.completed IS NOT OLD.completed OR " +
                "NEW.completedAt IS NOT OLD.completedAt OR NEW.reminders IS NOT OLD.reminders OR " +
                "NEW.recurrence IS NOT OLD.recurrence OR NEW.occurrence IS NOT OLD.occurrence OR " +
                "NEW.durationMin IS NOT OLD.durationMin " +
                "BEGIN UPDATE tasks SET updatedAt = $NOW WHERE id = NEW.id; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS tasks_sync_del AFTER DELETE ON tasks WHEN OLD.uuid != '' BEGIN " +
                "INSERT OR REPLACE INTO deleted_tasks (uuid, deletedAt) VALUES (OLD.uuid, $NOW); " +
                "END",
        )
    }
}
