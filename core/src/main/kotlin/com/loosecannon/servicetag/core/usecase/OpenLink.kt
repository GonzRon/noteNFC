package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.links.LinkCheck
import com.loosecannon.servicetag.core.links.LinkLaunchPolicy
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.LinkRepository
import com.loosecannon.servicetag.core.ports.UnitOfWork

/** Launch-time half of the link policy: re-checks the stored URI and records the open. */
class OpenLink(
    private val links: LinkRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    sealed interface Outcome {
        data class Launch(val uri: String, val link: ExternalLink) : Outcome
        data class Refused(val link: ExternalLink, val reason: String) : Outcome
        data class Missing(val id: LinkId) : Outcome
    }

    suspend fun run(id: LinkId): Outcome = uow.write {
        val link = links.get(id) ?: return@write Outcome.Missing(id)
        val uri = when (val c = LinkLaunchPolicy.check(link.uri)) {
            is LinkCheck.Accepted -> c.uri
            is LinkCheck.NeedsConfirmation ->
                if (link.kind == LinkKind.OTHER) c.uri
                else return@write Outcome.Refused(link, "scheme '${c.scheme}' was never confirmed")
            is LinkCheck.Rejected -> return@write Outcome.Refused(link, c.reason)
        }
        links.upsert(link.copy(lastOpenedAt = clock.nowMillis()))
        Outcome.Launch(uri, link)
    }
}
