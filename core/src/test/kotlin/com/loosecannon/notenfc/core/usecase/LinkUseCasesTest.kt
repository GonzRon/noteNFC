package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.testing.FakeUnitOfWork
import com.loosecannon.notenfc.core.testing.InMemoryLinkRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LinkUseCasesTest {
    private val links = InMemoryLinkRepository()
    private val uow = FakeUnitOfWork(links)
    private var seq = 0
    private val ids = IdGenerator { "00000000-0000-4000-8000-%012d".format(++seq) }
    private val clock = Clock { 5_000L }
    private val save = SaveLink(links, uow, ids, clock)
    private val open = OpenLink(links, uow, clock)
    private val joplin = "joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef"

    @Test fun savesAnAllowedLinkWithItsKind() = runTest {
        val link = save.run("  $joplin ", label = "Pool log")
        assertEquals(LinkKind.JOPLIN, link.kind)
        assertEquals(joplin, link.uri)
        assertEquals("Pool log", link.label)
        assertEquals(null, link.assetId)
        assertEquals(5_000L, link.createdAt)
        assertEquals(link, links.rows[link.id.value])
        assertEquals(1, uow.commits)
    }
    @Test fun blankLabelFallsBackToTheUri() = runTest {
        assertEquals(joplin, save.run(joplin, label = "  ").label)
        assertEquals(joplin, save.run(joplin, label = null).label)
    }
    @Test fun blockedUriIsRefusedAndNothingIsStored() = runTest {
        assertFailsWith<LinkRefused> { save.run("intent://scan/#Intent;scheme=zxing;end", label = "x") }
        assertTrue(links.rows.isEmpty())
        assertEquals(0, uow.commits)
    }
    @Test fun unknownSchemeNeedsConfirmationThenSavesAsOther() = runTest {
        assertFailsWith<LinkNeedsConfirmation> { save.run("bear://x-callback-url/open-note?id=1", label = "b") }
        assertTrue(links.rows.isEmpty())
        val link = save.run("bear://x-callback-url/open-note?id=1", label = "b", confirmedOther = true)
        assertEquals(LinkKind.OTHER, link.kind)
    }
    @Test fun openingRecordsLastOpenedAtAndReturnsTheCheckedUri() = runTest {
        val link = save.run(joplin, label = "Pool log")
        val out = open.run(link.id)
        assertEquals(OpenLink.Outcome.Launch(joplin, link), out)
        assertEquals(5_000L, links.rows[link.id.value]!!.lastOpenedAt)
    }
    @Test fun aConfirmedOtherLinkLaunches() = runTest {
        val link = save.run("bear://x-callback-url/open-note?id=1", label = "b", confirmedOther = true)
        assertIs<OpenLink.Outcome.Launch>(open.run(link.id))
    }
    @Test fun aStoredBlockedUriIsRefusedAtLaunchTime() = runTest {
        // e.g. restored from a hand-edited backup: the policy runs again at launch (D3 §10)
        val bad = ExternalLink(LinkId("l1"), null, LinkKind.WEB, "evil", "javascript:alert(1)", 1L, null, 1L)
        links.rows["l1"] = bad
        val out = open.run(LinkId("l1"))
        assertIs<OpenLink.Outcome.Refused>(out)
        assertEquals(null, links.rows["l1"]!!.lastOpenedAt)
    }
    @Test fun anUnconfirmedUnknownSchemeIsRefusedAtLaunchTime() = runTest {
        val odd = ExternalLink(LinkId("l2"), null, LinkKind.WEB, "odd", "bear://x", 1L, null, 1L)
        links.rows["l2"] = odd
        assertIs<OpenLink.Outcome.Refused>(open.run(LinkId("l2")))
    }
    @Test fun missingLinkIsReported() = runTest {
        assertEquals(OpenLink.Outcome.Missing(LinkId("nope")), open.run(LinkId("nope")))
    }
}
