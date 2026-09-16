package com.loosecannon.notenfc.attachments

import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.StoreState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The naming rule and the sample-size arithmetic, which are the two parts of [Thumbnails] that do
 * not need `BitmapFactory`. The decode itself is exercised on the emulator (Task 10).
 */
class ThumbnailsTest {

    private val noStore = object : AttachmentStorage {
        override fun state(): StoreState = StoreState.NotConfigured
        override fun store(): AttachmentStore? = null
    }
    private val thumbnails = Thumbnails(File("/tmp-not-touched"), noStore)

    @Test fun theCacheNameCarriesTheIdAndTheShaPrefix() {
        val file = thumbnails.cacheFileFor(AttachmentId("att-1"), "abcdef0123456789".repeat(4))
        assertEquals("att-1-abcdef01.jpg", file.name)
        assertEquals("thumbs", file.parentFile!!.name)
    }

    @Test fun bytesThatChangedCannotBeServedFromTheOldThumbnail() {
        val before = thumbnails.cacheFileFor(AttachmentId("att-1"), "a".repeat(64))
        val after = thumbnails.cacheFileFor(AttachmentId("att-1"), "b".repeat(64))
        assertNotEquals(before, after)
    }

    @Test fun sampleSizeBringsTheLongEdgeToTwoFiftySixOrBelow() {
        assertEquals(1, thumbnails.sampleSize(256, 128))
        assertEquals(1, thumbnails.sampleSize(400, 300))
        assertEquals(2, thumbnails.sampleSize(512, 384))
        assertEquals(8, thumbnails.sampleSize(4032, 3024))
        assertEquals(1, thumbnails.sampleSize(0, 0))
    }
}
