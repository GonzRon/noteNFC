package com.loosecannon.servicetag.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A `Map`-backed fake of [KeyValueStore] so [AppPrefs] can be tested without Android. */
private class FakeKeyValueStore : KeyValueStore {
    private val longs = mutableMapOf<String, Long>()
    private val strings = mutableMapOf<String, String>()

    override fun getLong(key: String): Long? = longs[key]
    override fun putLong(key: String, value: Long) { longs[key] = value }
    override fun getString(key: String): String? = strings[key]
    override fun putString(key: String, value: String) { strings[key] = value }
}

class AppPrefsTest {

    @Test
    fun lastBackupAtIsNullUntilMarked() {
        val prefs = AppPrefs(FakeKeyValueStore())
        assertNull(prefs.lastBackupAt)
    }

    @Test
    fun markBackupExportedStoresTheInstant() {
        val prefs = AppPrefs(FakeKeyValueStore())
        prefs.markBackupExported(12_345L)
        assertEquals(12_345L, prefs.lastBackupAt)
    }

    @Test
    fun appearanceDefaultsToSystemAndRoundTrips() {
        val prefs = AppPrefs(FakeKeyValueStore())
        assertEquals(AppearanceMode.SYSTEM, prefs.appearanceMode)

        prefs.appearanceMode = AppearanceMode.DARK
        assertEquals(AppearanceMode.DARK, prefs.appearanceMode)

        prefs.appearanceMode = AppearanceMode.LIGHT
        assertEquals(AppearanceMode.LIGHT, prefs.appearanceMode)
    }

    @Test
    fun unknownStoredModeFallsBackToSystem() {
        val store = FakeKeyValueStore()
        store.putString("appearance_mode", "not_a_real_mode")
        val prefs = AppPrefs(store)
        assertEquals(AppearanceMode.SYSTEM, prefs.appearanceMode)
    }

    @Test
    fun attachmentTreeUriRoundTripsAndClears() {
        val prefs = AppPrefs(FakeKeyValueStore())
        assertNull(prefs.attachmentTreeUri)

        prefs.attachmentTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ANotes"
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ANotes",
            prefs.attachmentTreeUri,
        )

        prefs.attachmentTreeUri = null
        assertNull(prefs.attachmentTreeUri)
    }

    @Test
    fun lastRestoredBackupSetIdRoundTripsAndClears() {
        val prefs = AppPrefs(FakeKeyValueStore())
        assertNull(prefs.lastRestoredBackupSetId)

        prefs.lastRestoredBackupSetId = "set-42"
        assertEquals("set-42", prefs.lastRestoredBackupSetId)

        prefs.lastRestoredBackupSetId = null
        assertNull(prefs.lastRestoredBackupSetId)
    }
}
