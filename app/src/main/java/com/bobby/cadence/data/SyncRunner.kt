package com.bobby.cadence.data

import android.net.Uri
import androidx.room.withTransaction
import com.bobby.cadence.CadenceApp
import com.bobby.cadence.alarm.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One round of sync: read the shared file, merge, apply what is newer, write back if anything changed. */
object SyncRunner {
    private val lock = Mutex()

    suspend fun run(app: CadenceApp): Boolean = lock.withLock { withContext(Dispatchers.IO) { runLocked(app) } }

    private fun report(app: CadenceApp, ok: Boolean, msg: String) {
        app.prefs.update { it.copy(lastSyncAt = if (ok) System.currentTimeMillis() else it.lastSyncAt, lastSyncMsg = msg) }
    }

    private suspend fun runLocked(app: CadenceApp): Boolean {
        val s = app.prefs.state.value
        if (!s.syncEnabled || s.syncFolderUri.isBlank()) return false
        val tree = Uri.parse(s.syncFolderUri)
        return try {
            val remote = when (val r = SyncFolder.read(app, tree)) {
                SyncFolder.Read.Missing -> SyncDoc(emptyMap(), emptyMap())
                SyncFolder.Read.Failed -> { report(app, false, "Could not read the sync folder. Choose it again."); return false }
                is SyncFolder.Read.Text -> when (val p = SyncFormat.parse(r.text)) {
                    is SyncFormat.Parsed.Ok -> p.doc
                    SyncFormat.Parsed.TooNew -> { report(app, false, "The sync file is from a newer Cadence. Update this app."); return false }
                    SyncFormat.Parsed.Invalid -> { report(app, false, "The sync file is not readable. Nothing was changed."); return false }
                }
            }

            val dao = app.db.taskDao()
            val now = System.currentTimeMillis()
            val localTasks = dao.getAll()
            val local = SyncDoc(SyncMapper.toSync(localTasks), dao.getTombstones().associate { it.uuid to it.deletedAt })
            val merged = SyncMerge.merge(local, remote, now)

            val byUuid = localTasks.filter { it.uuid.isNotEmpty() }.associateBy { it.uuid }
            val toUpsert = merged.tasks.values.filter { byUuid[it.uuid]?.updatedAt != it.updatedAt }
            val toDelete = merged.deleted.keys.filter { byUuid.containsKey(it) }
            val changed = ArrayList<Task>()

            if (toUpsert.isNotEmpty() || toDelete.isNotEmpty() || merged.deleted != local.deleted) {
                app.db.withTransaction {
                    // tasks that arrive without their series' first task get one id per series
                    val seriesIds = HashMap<String, Long>()
                    for (st in toUpsert) {
                        val existing = byUuid[st.uuid]
                        val saved: Task
                        if (existing != null) {
                            saved = SyncMapper.applyTo(existing, st)
                            dao.update(saved)
                        } else {
                            val id = dao.insert(SyncMapper.applyTo(null, st))
                            saved = SyncMapper.applyTo(null, st).copy(id = id)
                        }
                        dao.setUpdatedAt(saved.id, st.updatedAt)
                        val seriesOwner = st.series?.let { u -> byUuid[u]?.id ?: dao.getAll().firstOrNull { it.uuid == u }?.id }
                        val sid = when {
                            st.recurrence == null -> 0L
                            seriesOwner != null -> seriesOwner
                            st.series != null -> seriesIds.getOrPut(st.series) { saved.id }
                            else -> saved.id
                        }
                        dao.setSeries(saved.id, sid)
                        changed.add(saved.copy(seriesId = sid, updatedAt = st.updatedAt))
                    }
                    for (u in toDelete) dao.deleteByUuid(u)
                    for ((u, at) in merged.deleted) dao.putTombstone(Tombstone(u, at))
                    dao.pruneTombstones(now - SyncMerge.KEEP_DELETIONS_MS)
                }
                // reminders follow what was applied
                for (u in toDelete) byUuid[u]?.let { app.scheduler.cancel(it.id); Notifier.dismiss(app, it.id) }
                for (t in changed) {
                    if (t.completed) { app.scheduler.cancel(t.id); Notifier.dismiss(app, t.id) } else app.scheduler.schedule(t)
                }
            }

            if (merged != remote) {
                val ok = SyncFolder.write(app, tree, SyncFormat.toJson(merged, android.os.Build.MODEL ?: "phone", now))
                if (!ok) { report(app, false, "Could not write to the sync folder. Choose it again."); return false }
            }
            report(app, true, "")
            true
        } catch (e: Exception) {
            report(app, false, "Sync failed: " + (e.message ?: "unknown error"))
            false
        }
    }
}
