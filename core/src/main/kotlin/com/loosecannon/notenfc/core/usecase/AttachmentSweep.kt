package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.AttachmentStore
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/**
 * The one place the best-effort byte sweep is spelled. Bytes go after the transaction that dropped
 * the rows naming them, so a store that will not co-operate leaves an orphaned file for 4B to
 * sweep — not a reason to undo a delete the person asked for (spec §6).
 *
 * The allowance is deliberately narrow: a broken destination only. [StoreIoException] and anything
 * else IO-shaped the store may raise is swallowed; a [CancellationException] is not a store
 * failure and must keep travelling, or a cancelled caller would see `run` finish normally.
 */
internal suspend fun AttachmentStore.deleteBestEffort(locator: String) {
    try {
        delete(locator)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        // com.loosecannon.notenfc.core.ports.StoreIoException is one of these: the row is already
        // gone, so the file is now an orphan and nothing is left to report it to.
    }
}

/** Resolves the store, if this storage has one, and sweeps every locator best effort. */
internal suspend fun AttachmentStorage.sweepBytes(locators: List<String>) {
    if (locators.isEmpty()) return
    val store = store() ?: return
    locators.forEach { locator -> store.deleteBestEffort(locator) }
}

/**
 * The digest of what is *already* at [locator], or null when nothing is there.
 *
 * `put` is the only place an attachment's sha256 is computed on the way *in* (spec §11.10); this is
 * the way back out, for the one caller that has to know whether bytes it is about to overwrite are
 * the bytes the row claims. The stream is hashed in place — an attachment can be hundreds of
 * megabytes, so it is never materialised — and `open` returning null is the whole absence answer,
 * which is why `exists` is not asked first.
 */
internal suspend fun AttachmentStore.sha256Of(locator: String): String? {
    val source = open(locator) ?: return null
    val digest = MessageDigest.getInstance("SHA-256")
    source.use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { b -> "%02x".format(b) }
}
