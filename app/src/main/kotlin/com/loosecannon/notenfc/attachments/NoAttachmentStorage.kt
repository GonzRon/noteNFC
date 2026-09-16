package com.loosecannon.notenfc.attachments

import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.StoreState

/**
 * No folder, ever. A placeholder while Task 6 lands the table ahead of Task 7's real resolver:
 * every attachment path refuses, which is the same answer a fresh install gives, so nothing can
 * quietly half-work in between.
 */
object NoAttachmentStorage : AttachmentStorage {
    override fun state(): StoreState = StoreState.NotConfigured
    override fun store(): AttachmentStore? = null
}
