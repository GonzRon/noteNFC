package com.loosecannon.notenfc.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Image thumbnails without an image library (spec §8.2, §11.9): bounds-only decode for the
 * sample size, a second decode at that size, JPEG 80 into the OS cache directory.
 *
 * The cache name carries the row's id *and* the first eight characters of its sha256, so bytes
 * that changed can never be served from a stale thumbnail, and the file for a deleted row is
 * simply never asked for again. The cache is `cacheDir`, so it may vanish at any time; nothing
 * depends on it existing.
 */
class Thumbnails(
    private val cacheDir: File,
    private val storage: AttachmentStorage,
    private val maxEdgePx: Int = 256,
) {
    /** `<cacheDir>/thumbs/<attachment-id>-<sha256 prefix 8>.jpg`. Pure, and the test's subject. */
    fun cacheFileFor(id: AttachmentId, sha256: String): File =
        File(File(cacheDir, "thumbs"), "${id.value}-${sha256.take(8)}.jpg")

    /** The cached thumbnail, decoding it first if need be. Null when it cannot be produced. */
    suspend fun thumbnail(attachment: Attachment): File? = withContext(Dispatchers.IO) {
        try {
            decode(attachment)
        } catch (e: IOException) {
            // A folder that went away is not worth a crash in a list row: no preview, no noise.
            null
        }
    }

    private suspend fun decode(attachment: Attachment): File? {
        val target = cacheFileFor(attachment.id, attachment.sha256)
        if (target.isFile && target.length() > 0L) return target
        val store = storage.store() ?: return null

        // Pass one measures. `decodeStream` returns null by design when `inJustDecodeBounds` is
        // set, so it is the *stream* that says whether the bytes are there and `outWidth` that
        // says whether they were an image at all.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val header = store.open(attachment.storageLocator) ?: return null
        header.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = store.open(attachment.storageLocator)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        target.parentFile?.mkdirs()
        val ok = runCatching {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }.getOrDefault(false)
        bitmap.recycle()
        if (ok) return target
        runCatching { target.delete() }
        return null
    }

    /** The smallest power of two that brings the long edge to [maxEdgePx] or below. */
    internal fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var edge = maxOf(width, height)
        while (edge / 2 >= maxEdgePx) {
            edge /= 2
            sample *= 2
        }
        return sample
    }
}
