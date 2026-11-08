package com.bobby.cadence

import com.bobby.cadence.data.AppSettings
import com.bobby.cadence.data.BackupManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Old and new backup files must keep loading. Add a case when the format changes. */
class BackupManagerTest {
    @Test
    fun versionOneFileLoadsWithoutSessionsOrSettings() {
        val r = BackupManager.parse("""{"app":"cadence","version":1,"tasks":[{"title":"A"}]}""")
        assertTrue(r is BackupManager.Parsed.Ok)
        val b = (r as BackupManager.Parsed.Ok).backup
        assertEquals(1, b.tasks.size)
        assertEquals("A", b.tasks[0].title)
        assertNull(b.sessions)
        assertNull(b.settings)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val r = BackupManager.parse("""{"app":"cadence","version":2,"future":{"x":1},"tasks":[{"title":"A","somethingNew":5}]}""")
        assertTrue(r is BackupManager.Parsed.Ok)
    }

    @Test
    fun newerFileIsRefused() {
        val r = BackupManager.parse("""{"app":"cadence","version":${BackupManager.VERSION + 1},"tasks":[]}""")
        assertTrue(r is BackupManager.Parsed.TooNew)
    }

    @Test
    fun otherJsonIsInvalid() {
        assertTrue(BackupManager.parse("""{"hello":"world"}""") is BackupManager.Parsed.Invalid)
        assertTrue(BackupManager.parse("not json") is BackupManager.Parsed.Invalid)
    }

    @Test
    fun missingSettingsKeepCurrentValues() {
        val cur = AppSettings()
        assertEquals(cur, BackupManager.applySettings(cur, JSONObject()))
    }
}
