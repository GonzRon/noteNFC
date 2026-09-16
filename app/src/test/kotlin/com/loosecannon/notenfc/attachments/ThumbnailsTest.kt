package com.loosecannon.notenfc.attachments

import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentKind
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.StoreState
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val photo = Attachment(
        id = AttachmentId("att-1"),
        owner = AttachmentOwner.OfAsset(AssetId("a1")),
        kind = AttachmentKind.PHOTO,
        displayName = "photo.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 12L,
        sha256 = "a".repeat(64),
        storageLocator = "assets/a1/att-1.jpg",
        capturedOn = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

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
        assertEquals(2, thumbnails.sampleSize(400, 300))
        assertEquals(2, thumbnails.sampleSize(512, 384))
        assertEquals(16, thumbnails.sampleSize(4032, 3024))
        assertEquals(1, thumbnails.sampleSize(0, 0))
    }

    /** The rule behind the table: spec 8.2 asks for 256 px, not "no smaller than 256 px". */
    @Test fun theSampledLongEdgeIsNeverAboveTwoFiftySix() {
        listOf(256 to 128, 400 to 300, 512 to 384, 4032 to 3024, 0 to 0).forEach { (w, h) ->
            val edge = maxOf(w, h) / thumbnails.sampleSize(w, h)
            assertTrue("$w x $h sampled down to $edge", edge <= 256)
        }
    }

    @Test fun noFolderMeansNoThumbnail() = runTest {
        assertNull(thumbnails.thumbnail(photo))
    }
}
