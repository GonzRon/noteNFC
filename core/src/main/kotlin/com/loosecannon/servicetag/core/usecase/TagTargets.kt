package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.core.ports.LinkRepository

class UnknownTarget(target: TagTarget) : IllegalArgumentException("target does not exist: $target")

/** A binding may only point at a row that exists; `None` is always fine. */
internal suspend fun requireTargetExists(target: TagTarget, assets: AssetRepository, links: LinkRepository) {
    when (target) {
        is TagTarget.AssetTarget -> assets.get(target.assetId) ?: throw UnknownTarget(target)
        is TagTarget.LinkTarget -> links.get(target.linkId) ?: throw UnknownTarget(target)
        TagTarget.None -> Unit
    }
}
