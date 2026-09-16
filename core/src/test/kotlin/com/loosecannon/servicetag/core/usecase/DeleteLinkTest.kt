package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.testing.FakeUnitOfWork
import com.loosecannon.servicetag.core.testing.InMemoryLinkRepository
import com.loosecannon.servicetag.core.testing.InMemoryTagRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DeleteLinkTest {
    private val links = InMemoryLinkRepository()
    private val tags = InMemoryTagRepository()
    private val uow = FakeUnitOfWork(links, tags)
    private val delete = DeleteLink(links, tags, uow)

    private val link = ExternalLink(
        id = LinkId("link-1"),
        kind = LinkKind.JOPLIN,
        label = "Pool log",
        uri = "joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun tag(id: String) = TagBinding(
        id = TagId(id),
        payloadFormat = PayloadFormat.V1,
        payloadKey = id,
        target = TagTarget.LinkTarget(link.id),
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test fun refusesWhileATagStillPointsAtTheLink() = runTest {
        links.upsert(link)
        tags.upsert(tag("00000000-0000-4000-8000-000000000001"))
        tags.upsert(tag("00000000-0000-4000-8000-000000000002"))

        // The count is part of the refusal so the screen can say how many tags to rewrite first.
        val refused = assertFailsWith<LinkStillBound> { delete.run(link.id) }
        assertEquals(2, refused.tagCount)
        assertEquals(link, links.rows[link.id.value])
    }

    @Test fun deletesALinkNoTagPointsAt() = runTest {
        links.upsert(link)
        delete.run(link.id)
        assertNull(links.rows[link.id.value])
    }
}
