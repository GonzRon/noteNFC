package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagStatus
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.nfc.TagPayload
import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.LinkRepository
import com.loosecannon.servicetag.core.ports.TagRepository
import com.loosecannon.servicetag.core.ports.UnitOfWork

/** Every way a scan can end (D3 §9). The UI switches on this and nothing else. */
sealed interface Resolution {
    data class OpenAsset(val tag: TagBinding, val asset: Asset) : Resolution
    data class LaunchLink(val tag: TagBinding, val link: ExternalLink) : Resolution
    data class Unbound(val tag: TagBinding) : Resolution
    data class Revoked(val tag: TagBinding) : Resolution
    data class UnknownV1(val tagId: TagId) : Resolution
    data class UnknownLegacy(val key: String) : Resolution
    data class NeedsNewerApp(val version: Int) : Resolution
    data class NotOurs(val payload: TagPayload) : Resolution
}

class ResolveTag(
    private val tags: TagRepository,
    private val assets: AssetRepository,
    private val links: LinkRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun run(payload: TagPayload): Resolution = when (payload) {
        is TagPayload.V1 -> known(PayloadFormat.V1, payload.tagId.value) ?: Resolution.UnknownV1(payload.tagId)
        is TagPayload.NewerVersion -> Resolution.NeedsNewerApp(payload.version)
        is TagPayload.Foreign, is TagPayload.Malformed, TagPayload.Empty -> Resolution.NotOurs(payload)
    }

    /** Lookup is by (format, key) — never by row id (D4 §3). A hit records the scan. */
    private suspend fun known(format: PayloadFormat, key: String): Resolution? = uow.write {
        val row = tags.findByPayload(format, key) ?: return@write null
        val tag = row.copy(lastScannedAt = clock.nowMillis())
        tags.upsert(tag)
        when {
            tag.status == TagStatus.LOST || tag.status == TagStatus.RETIRED -> Resolution.Revoked(tag)
            tag.status == TagStatus.UNBOUND -> Resolution.Unbound(tag)
            else -> when (val t = tag.target) {
                is TagTarget.AssetTarget -> assets.get(t.assetId)?.let { Resolution.OpenAsset(tag, it) } ?: Resolution.Unbound(tag)
                is TagTarget.LinkTarget -> links.get(t.linkId)?.let { Resolution.LaunchLink(tag, it) } ?: Resolution.Unbound(tag)
                TagTarget.None -> Resolution.Unbound(tag)
            }
        }
    }
}
