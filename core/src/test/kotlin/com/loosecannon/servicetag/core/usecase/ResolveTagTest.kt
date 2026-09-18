package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagStatus
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.nfc.TagPayload
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.testing.FakeUnitOfWork
import com.loosecannon.servicetag.core.testing.InMemoryAssetRepository
import com.loosecannon.servicetag.core.testing.InMemoryTagRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResolveTagTest {
    private val assets = InMemoryAssetRepository()
    private val tags = InMemoryTagRepository()
    private val uow = FakeUnitOfWork(assets, tags)
    private val clock = Clock { 9_000L }
    private val resolve = ResolveTag(tags, assets, uow, clock)

    private val v1Id = TagId("123e4567-e89b-12d3-a456-426614174000")
    private val asset = Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)

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
    /**
     * 2.6 — a tag bound to a pre-split link resolves to its own outcome. No link row is read (the
     * resolver has no `LinkRepository` at all), nothing is launched, and it is never mistaken for
     * an asset even when an asset with the link's id exists.
     */
    @Test fun aLinkBoundTagIsPreSplitAndNeverAnAsset() = runTest {
        assets.rows["l1"] = Asset(AssetId("l1"), "Not this", createdAt = 1L, updatedAt = 1L)
        tags.rows[v1Id.value] = row(v1Id.value, PayloadFormat.V1, v1Id.value, TagTarget.LinkTarget(LinkId("l1")))
        val r = resolve.run(TagPayload.V1(v1Id))
        assertIs<Resolution.PreSplitLink>(r)
        assertEquals(v1Id.value, r.tag.payloadKey)
        assertEquals(9_000L, r.tag.lastScannedAt)
        // the scan is still recorded; the row is not rewritten, retargeted or deleted
        assertEquals(TagTarget.LinkTarget(LinkId("l1")), tags.rows[v1Id.value]!!.target)
        assertEquals(1L, tags.rows[v1Id.value]!!.updatedAt)
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
    @Test fun unknownV1ScanResolvesUnknownV1WithoutTouchingTheStore() = runTest {
        assertEquals(Resolution.UnknownV1(v1Id), resolve.run(TagPayload.V1(v1Id)))
        assertTrue(tags.rows.isEmpty())
    }
    @Test fun lookupIsByPayloadKeyNotByRowId() = runTest {
        // the row id is "t9"; the scan only ever matches on the payload key it carries
        tags.rows["t9"] = row("t9", PayloadFormat.V1, v1Id.value, TagTarget.None)
        assertIs<Resolution.Unbound>(resolve.run(TagPayload.V1(v1Id)))
    }
    @Test fun newerVersionAndForeignContentNeverTouchTheStore() = runTest {
        assertEquals(Resolution.NeedsNewerApp(3), resolve.run(TagPayload.NewerVersion(3)))
        assertEquals(Resolution.NotOurs(TagPayload.Empty), resolve.run(TagPayload.Empty))
        assertEquals(Resolution.NotOurs(TagPayload.Foreign("x")), resolve.run(TagPayload.Foreign("x")))
        assertEquals(Resolution.NotOurs(TagPayload.Malformed("y")), resolve.run(TagPayload.Malformed("y")))
        assertEquals(0, uow.commits)
    }
}
