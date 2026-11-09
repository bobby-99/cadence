package com.bobby.cadence

import com.bobby.cadence.data.SyncDoc
import com.bobby.cadence.data.SyncFormat
import com.bobby.cadence.data.SyncMerge
import com.bobby.cadence.data.SyncTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private val now = 1_800_000_000_000L

    private fun t(uuid: String, title: String, at: Long) = SyncTask(
        uuid, title, "", null, null, 0, false, null, 1L, "", null, 1, null, null, at,
    )

    private fun doc(vararg tasks: SyncTask, deleted: Map<String, Long> = emptyMap()) =
        SyncDoc(tasks.associateBy { it.uuid }, deleted)

    @Test fun newerEditWins() {
        val m = SyncMerge.merge(doc(t("a", "old", 100)), doc(t("a", "new", 200)), now)
        assertEquals("new", m.tasks["a"]!!.title)
    }

    @Test fun unionOfDifferentTasks() {
        val m = SyncMerge.merge(doc(t("a", "A", 1)), doc(t("b", "B", 1)), now)
        assertEquals(setOf("a", "b"), m.tasks.keys)
    }

    @Test fun deletionBeatsOlderEditAndNewerEditBeatsDeletion() {
        val gone = SyncMerge.merge(doc(t("a", "x", 100)), doc(deleted = mapOf("a" to now - 10)), now)
        assertNull(gone.tasks["a"]); assertEquals(now - 10, gone.deleted["a"])
        val back = SyncMerge.merge(doc(t("a", "x", now)), doc(deleted = mapOf("a" to now - 10)), now)
        assertEquals("x", back.tasks["a"]!!.title); assertNull(back.deleted["a"])
    }

    @Test fun mergeIsSymmetrical() {
        val a = doc(t("a", "A1", 5), t("c", "C", 9), deleted = mapOf("d" to now - 5))
        val b = doc(t("a", "A2", 7), t("d", "D", 1), deleted = mapOf("c" to now - 3))
        assertEquals(SyncMerge.merge(a, b, now), SyncMerge.merge(b, a, now))
    }

    @Test fun oldDeletionsAreForgotten() {
        val m = SyncMerge.merge(doc(deleted = mapOf("a" to now - SyncMerge.KEEP_DELETIONS_MS - 1)), doc(), now)
        assertTrue(m.deleted.isEmpty())
    }

    @Test fun fileRoundTrip() {
        val d = doc(t("a", "Call mom", 5).copy(dueDate = 20000, dueTime = 1080, priority = 3, recurrence = "{\"u\":\"DAY\"}", series = "a"), deleted = mapOf("z" to 9L))
        val p = SyncFormat.parse(SyncFormat.toJson(d, "phone", now))
        assertEquals(d, (p as SyncFormat.Parsed.Ok).doc)
    }

    @Test fun refusesNewerOrForeignFiles() {
        assertTrue(SyncFormat.parse("{\"format\":\"cadence-sync\",\"version\":99}") is SyncFormat.Parsed.TooNew)
        assertTrue(SyncFormat.parse("{\"format\":\"other\"}") is SyncFormat.Parsed.Invalid)
        assertTrue(SyncFormat.parse("nope") is SyncFormat.Parsed.Invalid)
    }
}
