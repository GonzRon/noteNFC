package com.loosecannon.servicetag.data.room

import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.AssetStatus
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryFlowsTest {

    private fun asset(id: String, name: String, status: AssetStatus) = Asset(
        id = AssetId(id),
        name = name,
        status = status,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun tag(id: String, assetId: String?) = TagBinding(
        id = TagId(id),
        payloadFormat = PayloadFormat.V1,
        payloadKey = "key-$id",
        target = assetId?.let { TagTarget.AssetTarget(AssetId(it)) } ?: TagTarget.None,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun link(id: String, label: String) = ExternalLink(
        id = LinkId(id),
        kind = LinkKind.WEB,
        label = label,
        uri = "https://example.invalid/$id",
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun assetsObserveAllEmitsOnUpsertAndOrdersActiveFirstThenName() = runTest {
        val db = inMemoryDb()
        try {
            val repo = RoomAssetRepository(db.assetDao())

            repo.upsert(asset("a1", "zeta", AssetStatus.ACTIVE))
            repo.upsert(asset("a2", "Alpha", AssetStatus.ARCHIVED))
            repo.upsert(asset("a3", "beta", AssetStatus.ACTIVE))

            val first = repo.observeAll().first()
            assertEquals(listOf("beta", "zeta", "Alpha"), first.map { it.name })

            repo.upsert(asset("a4", "aardvark", AssetStatus.ACTIVE))

            val second = repo.observeAll().first()
            assertEquals(listOf("aardvark", "beta", "zeta", "Alpha"), second.map { it.name })
        } finally {
            db.close()
        }
    }

    @Test
    fun tagsObserveForAssetFiltersByAsset() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1", "Hot tub", AssetStatus.ACTIVE).toEntity())
            db.assetDao().upsert(asset("a2", "Spa", AssetStatus.ACTIVE).toEntity())

            val repo = RoomTagRepository(db.nfcTagDao())
            repo.upsert(tag("t1", "a1"))
            repo.upsert(tag("t2", "a2"))
            repo.upsert(tag("t3", "a1"))

            val forA1 = repo.observeForAsset(AssetId("a1")).first()
            assertEquals(setOf("t1", "t3"), forA1.map { it.id.value }.toSet())

            repo.upsert(tag("t4", "a1"))

            val second = repo.observeForAsset(AssetId("a1")).first()
            assertEquals(setOf("t1", "t3", "t4"), second.map { it.id.value }.toSet())
        } finally {
            db.close()
        }
    }

    @Test
    fun linksObserveAllEmitsOnDelete() = runTest {
        val db = inMemoryDb()
        try {
            val repo = RoomLinkRepository(db.externalLinkDao())
            repo.upsert(link("l1", "Manual"))
            repo.upsert(link("l2", "Warranty"))

            val first = repo.observeAll().first()
            assertEquals(setOf("l1", "l2"), first.map { it.id.value }.toSet())

            repo.delete(LinkId("l1"))

            val second = repo.observeAll().first()
            assertEquals(listOf("l2"), second.map { it.id.value })
        } finally {
            db.close()
        }
    }
}
