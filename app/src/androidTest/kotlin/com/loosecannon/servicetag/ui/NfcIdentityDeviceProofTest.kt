package com.loosecannon.servicetag.ui

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagTarget
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Ambient NFC as the normal read path (§21), on an emulator with no NFC radio: the intent the
 * platform would deliver is built in-process and handed to the real trampoline, which runs the
 * real codec against the real database. What this cannot prove — formatting, the measured
 * `Ndef.maxSize`, a capacity refusal, a read-back, a lock — belongs to §D Session 1 on a physical
 * NTAG213 and is not attempted here (G4).
 *
 * `NfcDispatchActivity` has no UI and finishes as soon as it has handed off, so there is no
 * scenario to track: the intent is started on the context and the assertions are made against
 * `MainActivity`'s tree through an empty Compose rule, the idiom `DeepLinkSmokeTest` uses.
 *
 * `NfcSheet` shouts its eyebrow (`eyebrow.uppercase()`, G1 §1.4), so the not-ours rows assert the
 * uppercase form the tree actually carries, the way `ShareActivitySmokeTest` asserts "WEB PAGE".
 */
class NfcIdentityDeviceProofTest {

    @get:Rule val rule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun freshInstall() = clearInstall()

    private fun tap(records: Array<NdefRecord>) {
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
            .setClassName(context, "com.loosecannon.servicetag.nfc.NfcDispatchActivity")
            .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(NdefMessage(records)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun ourRecords(tagId: String): Array<NdefRecord> =
        app.graph.ndefCodec.encodeV1(TagId(tagId))
            .map { NdefRecord(it.tnf.toShort(), it.type, ByteArray(0), it.payload) }
            .toTypedArray()

    /** A ServiceTag tag bound to an asset opens that asset, with no chooser and no sheet. */
    @Test fun ourTagOpensTheAssetItIsBoundTo() {
        val tagId = "11111111-1111-4111-8111-111111111111"
        runBlocking {
            val asset = app.graph.createAsset.run(name = "Hot tub")
            app.graph.bindTag.run(PayloadFormat.V1, tagId, TagTarget.AssetTarget(asset.id))
        }

        tap(ourRecords(tagId))

        rule.awaitText("Hot tub")
        rule.onAllNodesWithText("Hot tub").onFirst().assertIsDisplayed()
    }

    /** A ServiceTag tag this install has no row for is named, not treated as damage. */
    @Test fun ourTagWithNoRowSaysSo() {
        tap(ourRecords("22222222-2222-4222-8222-222222222222"))

        rule.awaitText("This ServiceTag tag is not in this phone's records.")
        rule.onNodeWithText("This ServiceTag tag is not in this phone's records.").assertIsDisplayed()
    }

    /** A sibling's tag is foreign on the device, exactly as `NdefEnvelopeIsolationTest` says in JVM. */
    @Test fun aNoteTagRecordIsNotOurs() {
        val sibling = NdefRecord(
            0x04.toShort(),
            "com.loosecannon.notetag:tag".toByteArray(Charsets.US_ASCII),
            ByteArray(0),
            byteArrayOf(0x01, 0x00) + ByteArray(16),
        )

        tap(arrayOf(sibling))

        rule.awaitText("NOT A SERVICETAG TAG")
        rule.onNodeWithText("This tag holds something else.").assertIsDisplayed()
    }

    /** Our type, a body that cannot be parsed: ours, and refused as unreadable. */
    @Test fun ourTypeWithAShortBodyIsUnreadable() {
        val short = NdefRecord(
            0x04.toShort(),
            app.graph.tagIdentity.externalType.toByteArray(Charsets.US_ASCII),
            ByteArray(0),
            byteArrayOf(0x01, 0x00, 0x01),
        )

        tap(arrayOf(short))

        rule.awaitText("NOT A SERVICETAG TAG")
        rule.onNodeWithText("This tag holds something else.").assertIsDisplayed()
    }
}
