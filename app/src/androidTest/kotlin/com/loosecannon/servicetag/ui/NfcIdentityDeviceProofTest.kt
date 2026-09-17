package com.loosecannon.servicetag.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.loosecannon.servicetag.BuildConfig
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagId
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.nfc.NdefCodec
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Ambient NFC as the normal read path (§21), on an emulator with no NFC radio: the intent the
 * platform would deliver is built in-process and left **implicit**, so the merged manifest's one
 * `NDEF_DISCOVERED` filter is what resolves it — filter → trampoline → real `NdefCodec` →
 * `ResolveTag` → real Room database → `MainActivity`'s sheet. What this cannot prove — formatting,
 * the measured `Ndef.maxSize`, a capacity refusal, a read-back, a lock — belongs to §D Session 1
 * on a physical NTAG213 and is not attempted here (G4).
 *
 * `NfcDispatchActivity` has no UI and finishes as soon as it has handed off, so there is no
 * scenario to track: the intent is started on the context and the assertions are made against
 * `MainActivity`'s tree through an empty Compose rule, the idiom `DeepLinkSmokeTest` uses.
 *
 * Two things worth knowing about what is and is not modelled here:
 *
 * - The data URI is what the filter matches; the **records** are what the codec judges, and
 *   `NfcDispatchActivity.payloadOf` reads only `EXTRA_NDEF_MESSAGES` for this action. So the two
 *   not-ours rows below deliberately arrive *through* our own filter carrying somebody else's
 *   record — the untrusted-input shape the activity's own KDoc is written for ("a third-party app
 *   can aim any extras at an exported activity"). A sibling *tag* never reaches us at all, which
 *   is `TagIdentityDispatchTest.theRetiredExternalTypeResolvesToNothingOfOurs`'s claim, not this
 *   class's.
 * - `NfcSheet` shouts its eyebrow (`eyebrow.uppercase()`, G1 §1.4), so the rows assert the
 *   uppercase form the tree actually carries, the way `ShareActivitySmokeTest` asserts "WEB PAGE",
 *   and each not-ours row also asserts the `QuietLine(result.reason)` prose that tells the two
 *   refusals apart.
 */
class NfcIdentityDeviceProofTest {

    @get:Rule val rule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The one Gradle-owned identity, read back the way the manifest placeholder was filled. */
    private val externalType: String =
        "${BuildConfig.NDEF_EXTERNAL_DOMAIN}:${BuildConfig.NDEF_TYPE_NAME}"

    @Before fun freshInstall() = clearInstall()

    /**
     * Starts the intent the platform builds for a tap, and lets the platform resolve it: no
     * component, no class — only the action, the NDEF extras and the `vnd.android.nfc://ext/…`
     * data URI our manifest filter declares.
     */
    private fun tap(records: Array<NdefRecord>) {
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
            .setData(Uri.parse("vnd.android.nfc://ext/$externalType"))
            .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(NdefMessage(records)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun ourRecords(tagId: String): Array<NdefRecord> =
        app.graph.ndefCodec.encodeV1(TagId(tagId))
            .map { NdefRecord(it.tnf.toShort(), it.type, ByteArray(0), it.payload) }
            .toTypedArray()

    private fun external(type: String, payload: ByteArray): NdefRecord =
        NdefRecord(
            NdefCodec.TNF_EXTERNAL_TYPE.toShort(),
            type.toByteArray(Charsets.US_ASCII),
            ByteArray(0),
            payload,
        )

    /**
     * A ServiceTag tag bound to an asset opens that asset: no chooser, no decision to make — but
     * there **is** a sheet. `TagResultSheet`'s `OpensAsset` branch names the asset and navigates in
     * the same composition, and the nav root drops the sheet entry as it pushes the detail screen.
     *
     * So the name alone proves nothing: the transient sheet carries it too. The detail screen
     * carries it **twice** — app-bar title and identity-plate model line — which is what
     * `AppSmokeTest.assetCanBeCreatedFromTheDashboardAndOpens` waits for, and a count of two is
     * reachable only once the sheet is gone and the detail screen is up.
     */
    @Test fun ourTagOpensTheAssetItIsBoundTo() {
        val tagId = "11111111-1111-4111-8111-111111111111"
        runBlocking {
            val asset = app.graph.createAsset.run(name = "Hot tub")
            app.graph.bindTag.run(PayloadFormat.V1, tagId, TagTarget.AssetTarget(asset.id))
        }

        tap(ourRecords(tagId))

        rule.awaitText("Hot tub", count = 2)
        rule.onAllNodesWithText("Hot tub").assertCountEquals(2)
        rule.onAllNodesWithText("Hot tub").onFirst().assertIsDisplayed()
    }

    /** A ServiceTag tag this install has no row for is named, not treated as damage. */
    @Test fun ourTagWithNoRowSaysSo() {
        tap(ourRecords("22222222-2222-4222-8222-222222222222"))

        rule.awaitText("This ServiceTag tag is not in this phone's records.")
        rule.onNodeWithText("This ServiceTag tag is not in this phone's records.").assertIsDisplayed()
        rule.onNodeWithText("UNREGISTERED TAG").assertIsDisplayed()
    }

    /**
     * A sibling's record is foreign **even carrying a byte-identical ServiceTag v1 body**: the
     * codec's type gate runs before any body parse (C8, invariant 1). The body here is not
     * hand-rolled — it is our own `v1Record`'s payload, re-typed under NoteTag's external type,
     * which is the device half of `NdefEnvelopeIsolationTest
     * .aSiblingRecordCarryingOurOwnPayloadIsStillForeign`.
     */
    @Test fun aNoteTagRecordIsNotOurs() {
        val ourBody = app.graph.ndefCodec.v1Record(TagId("33333333-3333-4333-8333-333333333333")).payload

        tap(arrayOf(external("com.loosecannon.notetag:tag", ourBody)))

        rule.awaitText("NOT A SERVICETAG TAG")
        rule.onNodeWithText("This tag holds something else.").assertIsDisplayed()
        // Foreign, and the reason names the type that was refused — not a length complaint.
        rule.onNodeWithText("not a ServiceTag tag: tnf=4 type=com.loosecannon.notetag:tag")
            .assertIsDisplayed()
    }

    /**
     * Our type, a body that cannot be parsed: **ours**, and refused as unreadable rather than
     * foreign. The distinction is invisible in the eyebrow and the sentence — both refusals share
     * them — so it is the `QuietLine` reason that carries it, and this row asserts the
     * length message `NdefCodec.decodeV1` produces. JVM half:
     * `NdefEnvelopeIsolationTest.ourTypeWithASiblingBodyIsOursAndMalformed`.
     */
    @Test fun ourTypeWithAShortBodyIsUnreadable() {
        tap(arrayOf(external(externalType, byteArrayOf(0x01, 0x00, 0x01))))

        rule.awaitText("NOT A SERVICETAG TAG")
        rule.onNodeWithText("This tag holds something else.").assertIsDisplayed()
        // Ours: an unreadable ServiceTag record, named by length, not a foreign-type refusal.
        rule.onNodeWithText("unreadable ServiceTag record: payload is 3 bytes, expected 18")
            .assertIsDisplayed()
    }
}
