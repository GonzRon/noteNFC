package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.links.LinkCheck
import com.loosecannon.servicetag.core.links.LinkLaunchPolicy
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.IdGenerator
import com.loosecannon.servicetag.core.ports.LinkRepository
import com.loosecannon.servicetag.core.ports.UnitOfWork

class LinkRefused(reason: String) : IllegalArgumentException(reason)

class LinkNeedsConfirmation(val scheme: String) : IllegalArgumentException("scheme '$scheme' needs explicit confirmation")

/** Creates a standalone link (asset attachment is a Phase 1C/2 concern). The policy gate runs here. */
class SaveLink(
    private val links: LinkRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(uri: String, label: String?, confirmedOther: Boolean = false): ExternalLink {
        val (kind, cleanUri) = when (val c = LinkLaunchPolicy.check(uri)) {
            is LinkCheck.Accepted -> c.kind to c.uri
            is LinkCheck.NeedsConfirmation -> if (confirmedOther) LinkKind.OTHER to c.uri else throw LinkNeedsConfirmation(c.scheme)
            is LinkCheck.Rejected -> throw LinkRefused(c.reason)
        }
        val now = clock.nowMillis()
        val link = ExternalLink(
            id = LinkId(ids.newId()),
            assetId = null,
            kind = kind,
            label = label?.trim()?.takeIf { it.isNotEmpty() } ?: cleanUri,
            uri = cleanUri,
            createdAt = now,
            lastOpenedAt = null,
            updatedAt = now,
        )
        uow.write { links.upsert(link) }
        return link
    }
}
