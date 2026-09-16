package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.AttachmentStore
import java.io.IOException
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
