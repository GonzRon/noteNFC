package com.loosecannon.notenfc.attachments

import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.prefs.AppPrefs
import com.loosecannon.notenfc.prefs.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `state()` is the gate every attachment path passes through, so it is worth a JVM test of its
 * own. The `DocumentFile` is behind [AttachmentRoot] precisely so this can run without an
 * emulator; the real tree is proved by `SafTreeAttachmentStoreContractTest` (Task 10).
 */
class SafAttachmentStorageTest {

    private class MapStore : KeyValueStore {
        private val longs = mutableMapOf<String, Long>()
        private val strings = mutableMapOf<String, String>()
        override fun getLong(key: String): Long? = longs[key]
        override fun putLong(key: String, value: Long) { longs[key] = value }
        override fun getString(key: String): String? = strings[key]
        override fun putString(key: String, value: String) { strings[key] = value }
    }

    private class FakeRoot(
        override val displayName: String = "Attachments",
        override val authority: String = "com.example.documents",
        private val writable: Boolean = true,
    ) : AttachmentRoot {
        override fun canWrite(): Boolean = writable
        override fun store(): AttachmentStore = error("not needed for state()")
        override fun viewUri(locator: String) = null
    }

    private val prefs = AppPrefs(MapStore())

    private fun storage(
        root: AttachmentRoot? = FakeRoot(),
        granted: Boolean = true,
    ) = SafAttachmentStorage(prefs, { root }, { granted })

    @Test fun noPreferenceIsNotConfigured() {
        assertEquals(StoreState.NotConfigured, storage().state())
        assertNull(storage().store())
    }

    @Test fun aResolvableWritableGrantedTreeIsReady() {
        prefs.attachmentTreeUri = "content://com.example.documents/tree/spa"
        assertEquals(
            StoreState.Ready("Attachments", "com.example.documents"),
            storage().state(),
        )
    }

    @Test fun aRevokedGrantAnUnresolvableTreeAndAReadOnlyTreeAreAllAccessLost() {
        prefs.attachmentTreeUri = "content://com.example.documents/tree/spa"
        assertEquals(StoreState.AccessLost("Attachments"), storage(granted = false).state())
        assertEquals(StoreState.AccessLost("Attachments"), storage(root = FakeRoot(writable = false)).state())
        // an unresolvable root still names the folder from the last path segment
        assertEquals(StoreState.AccessLost("spa"), storage(root = null).state())
        assertNull(storage(granted = false).store())
    }

    @Test fun clearingThePreferenceGoesBackToNotConfigured() {
        prefs.attachmentTreeUri = "content://com.example.documents/tree/spa"
        prefs.attachmentTreeUri = null
        assertEquals(StoreState.NotConfigured, storage().state())
    }
}
