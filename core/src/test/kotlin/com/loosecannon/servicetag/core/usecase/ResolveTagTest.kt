package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagStatus
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.nfc.TagPayload
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.testing.FakeUnitOfWork
import com.loosecannon.servicetag.core.testing.InMemoryAssetRepository
import com.loosecannon.servicetag.core.testing.InMemoryLinkRepository
import com.loosecannon.servicetag.core.testing.InMemoryTagRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResolveTagTest {
    private val assets = InMemoryAssetRepository()
    private val tags = InMemoryTagRepository()
    private val links = InMemoryLinkRepository()
    private val uow = FakeUnitOfWork(assets, tags, links)
    private val clock = Clock { 9_000L }
    private val resolve = ResolveTag(tags, assets, links, uow, clock)

    private val v1Id = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val asset = Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)
    private val link = ExternalLink(LinkId("l1"), null, LinkKind.JOPLIN, "log", "joplin://x-callback-url/openNote?id=0123456789abcdef0123456789abcdef", 1L, null, 1L)

    private fun row(id: String, format: PayloadFormat, key: String, target: TagTarget, status: TagStatus = TagStatus.ACTIVE) =
        TagBinding(TagId(id), format, key, target, status, createdAt = 1L, updatedAt = 1L)

    @Test fun boundToAnAssetOpensIt() = runTest {
        assets.rows["a1"] = asset
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.AssetTarget(AssetId("a1")))
        val r = resolve.run(TagPayload.V1(v1Id))
        assertIs<Resolution.OpenAsset>(r)
        assertEquals(asset, r.asset)
        assertEquals(9_000L, r.tag.lastScannedAt)
        assertEquals(9_000L, tags.rows[v1Id.value]!!.lastScannedAt)
        assertEquals(1L, tags.rows[v1Id.value]!!.updatedAt)
    }
    @Test fun boundToALinkLaunchesIt() = runTest {
        links.rows["l1"] = link
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.LinkTarget(LinkId("l1")))
        val r = resolve.run(TagPayload.V1(v1Id))
        assertIs<Resolution.LaunchLink>(r)
        assertEquals(link, r.link)
    }
    @Test fun lostAndRetiredAreRevokedEvenWhenStillTargeted() = runTest {
        assets.rows["a1"] = asset
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.AssetTarget(AssetId("a1")), TagStatus.LOST)
        assertIs<Resolution.Revoked>(resolve.run(TagPayload.V1(v1Id)))
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.AssetTarget(AssetId("a1")), TagStatus.RETIRED)
        assertIs<Resolution.Revoked>(resolve.run(TagPayload.V1(v1Id)))
    }
    @Test fun unboundRowIsUnbound() = runTest {
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.None, TagStatus.UNBOUND)
        assertIs<Resolution.Unbound>(resolve.run(TagPayload.V1(v1Id)))
    }
    @Test fun activeRowWhoseTargetVanishedIsUnbound() = runTest {
        // ON DELETE SET NULL leaves an ACTIVE row with no target; a dangling id is treated the same
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.None)
        assertIs<Resolution.Unbound>(resolve.run(TagPayload.V1(v1Id)))
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.AssetTarget(AssetId("gone")))
        assertIs<Resolution.Unbound>(resolve.run(TagPayload.V1(v1Id)))
    }
    @Test fun unknownV1AndLegacyAreDistinct() = runTest {
        assertEquals(Resolution.UnknownV1(v1Id), resolve.run(TagPayload.V1(v1Id)))
        // the legacy half of this claim goes with Resolution.UnknownLegacy, in task 6
        assertTrue(tags.rows.isEmpty())
    }
    @Test fun lookupIsByFormatAndKeyNotById() = runTest {
        // a LEGACY row whose payload key happens to equal a v1 id string must not resolve a v1 scan
        tags.rows["t9"] = row("t9", PayloadFormat.LEGACY_MD5, v1Id.value, TagTarget.None)
        assertEquals(Resolution.UnknownV1(v1Id), resolve.run(TagPayload.V1(v1Id)))
    }
    @Test fun newerVersionAndForeignContentNeverTouchTheStore() = runTest {
        assertEquals(Resolution.NeedsNewerApp(3), resolve.run(TagPayload.NewerVersion(3)))
        assertEquals(Resolution.NotOurs(TagPayload.Empty), resolve.run(TagPayload.Empty))
        assertEquals(Resolution.NotOurs(TagPayload.Foreign("x")), resolve.run(TagPayload.Foreign("x")))
        assertEquals(Resolution.NotOurs(TagPayload.Malformed("y")), resolve.run(TagPayload.Malformed("y")))
        assertEquals(0, uow.commits)
    }
}
