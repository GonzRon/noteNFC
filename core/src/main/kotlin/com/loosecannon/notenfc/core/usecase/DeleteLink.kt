package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Deleting the link would leave [tagCount] physical tags pointing at nothing, and a tag in a
 * drawer cannot be edited from here. The count travels with the refusal so the screen can say
 * how many tags have to be rewritten first.
 */
class LinkStillBound(val tagCount: Int) :
    IllegalStateException("$tagCount tag(s) still point at this link")

/**
 * Hard delete for a standalone link — unlike an asset, a link is a pointer and not a record, so
 * archive-first (R-9) does not apply to it. The bound-tag check is the whole use case: a link
 * whose tag still exists is not garbage, it is a working tag's destination.
 */
class DeleteLink(
    private val links: LinkRepository,
    private val tags: TagRepository,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: LinkId) {
        uow.write {
            val bound = tags.forLink(id)
            if (bound.isNotEmpty()) throw LinkStillBound(bound.size)
            links.delete(id)
        }
    }
}
