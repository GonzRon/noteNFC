package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.ports.AssetRepository

class UnknownTarget(target: TagTarget) : IllegalArgumentException("target does not exist: $target")

/**
 * 2.6 — a link target is pre-split data. `external_link` survives as a tombstone, so the *shape*
 * is still expressible; binding or provisioning one is not. Refused here, in the one guard both
 * write paths run, so there is no second place to forget.
 */
class LinkTargetUnsupported : IllegalArgumentException("ServiceTag no longer binds a tag to a link")

/** A binding may only point at an asset that exists; `None` is always fine. */
internal suspend fun requireTargetExists(target: TagTarget, assets: AssetRepository) {
    when (target) {
        is TagTarget.AssetTarget -> assets.get(target.assetId) ?: throw UnknownTarget(target)
        is TagTarget.LinkTarget -> throw LinkTargetUnsupported()
        TagTarget.None -> Unit
    }
}
