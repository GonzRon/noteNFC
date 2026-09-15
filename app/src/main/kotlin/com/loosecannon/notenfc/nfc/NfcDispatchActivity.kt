package com.loosecannon.notenfc.nfc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.loosecannon.notenfc.NoteNfcApp
import com.loosecannon.notenfc.R
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.core.nfc.TagRoute
import com.loosecannon.notenfc.core.usecase.OpenLink
import com.loosecannon.notenfc.core.usecase.Resolution
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.links.LinkLauncher
import com.loosecannon.notenfc.ui.interim.TargetPicker
import com.loosecannon.notenfc.ui.interim.WriteTagActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The one NFC-exported component (D3 §9, security doc "NFC dispatch"). Background scans arrive
 * here through the two `NDEF_DISCOVERED` filters; `notenfc://tag/<uuid>` arrives through `VIEW`.
 * Only `EXTRA_NDEF_MESSAGES`, `EXTRA_TAG` and the data URI are read — every other extra is ignored.
 *
 * A link tag launches immediately and this activity finishes (R-7). Everything else renders on
 * the interim Phase 1B result screen below; Phase 1C replaces the rendering with Compose routes.
 */
class NfcDispatchActivity : Activity() {

    private val scope = MainScope()
    private val graph: AppGraph get() = (application as NoteNfcApp).graph
    private lateinit var status: TextView
    private lateinit var actions: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_dispatch)
        status = findViewById(R.id.dispatch_status)
        actions = findViewById(R.id.dispatch_actions)
        findViewById<Button>(R.id.dispatch_close).setOnClickListener { finish() }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun handle(intent: Intent) {
        val payload = payloadOf(intent)
        if (payload == null) {
            Toast.makeText(this, "Nothing to resolve.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        actions.removeAllViews()
        status.text = "Resolving…"
        scope.launch {
            val resolution = try {
                graph.resolveTag.run(payload)
            } catch (e: Exception) {
                status.text = "Could not resolve this tag: ${e.javaClass.simpleName}: ${e.message}"
                return@launch
            }
            render(resolution)
        }
    }

    private fun payloadOf(intent: Intent): TagPayload? = when (intent.action) {
        NfcAdapter.ACTION_NDEF_DISCOVERED -> NdefCodec.decode(intent.ndefRecords().orEmpty())
        Intent.ACTION_VIEW -> tagRoute(intent.data)
        else -> null
    }

    private fun tagRoute(uri: Uri?): TagPayload? =
        TagRoute.parse(uri?.scheme, uri?.host, uri?.pathSegments.orEmpty())

    private fun render(r: Resolution) {
        when (r) {
            is Resolution.LaunchLink -> launch(r.link)
            is Resolution.OpenAsset -> status.text =
                "Asset: ${r.asset.name}" + (r.asset.category.takeIf { it.isNotEmpty() }?.let { "\nCategory: $it" } ?: "") +
                    "\n\nTag ${r.tag.id.value}" + (r.tag.label?.let { " ($it)" } ?: "") +
                    "\n\n(The asset screen arrives in Phase 1C.)"
            is Resolution.Unbound -> {
                status.text = "Tag ${r.tag.id.value} is not bound to anything yet."
                addBindAction(r.tag.payloadFormat, r.tag.payloadKey)
            }
            is Resolution.Revoked -> status.text =
                "This tag was marked ${r.tag.status.name.lowercase()}.\n\n(Re-activation arrives with the Phase 1C bind/rebind screens.)"
            is Resolution.UnknownV1 -> {
                status.text = "Unknown noteNFC tag ${r.tagId.value}.\n\nThis phone has no record of it: restore a backup, or bind it now."
                addBindAction(PayloadFormat.V1, r.tagId.value)
            }
            is Resolution.UnknownLegacy -> {
                status.text = "Legacy noteNFC tag (${r.key}).\n\nBind it as-is, or rewrite it in payload format v1."
                addBindAction(PayloadFormat.LEGACY_MD5, r.key)
                addWriteAction("Rewrite as a new v1 tag for…")
            }
            is Resolution.NeedsNewerApp -> status.text =
                "This tag was written by a newer noteNFC (payload format ${r.version}). Update the app to use it."
            is Resolution.NotOurs -> {
                status.text = when (val p = r.payload) {
                    TagPayload.Empty -> "Empty tag."
                    is TagPayload.Foreign -> "Not a noteNFC tag: ${p.description}"
                    is TagPayload.Malformed -> "Unreadable noteNFC record: ${p.reason}"
                    else -> "Not a noteNFC tag."
                }
                addWriteAction("Write a new v1 tag over it for…")
            }
        }
    }

    private fun launch(link: ExternalLink) {
        scope.launch {
            when (val out = graph.openLink.run(link.id)) {
                is OpenLink.Outcome.Launch -> { LinkLauncher.open(this@NfcDispatchActivity, out.uri); finish() }
                is OpenLink.Outcome.Refused -> status.text = "Link refused: ${out.reason}\n\n${link.uri}"
                is OpenLink.Outcome.Missing -> status.text = "The link this tag pointed at no longer exists."
            }
        }
    }

    private fun addBindAction(format: PayloadFormat, key: String) = addAction("Bind to an asset or link…") {
        TargetPicker.show(this, graph, scope, allowNone = false) { target ->
            scope.launch {
                try {
                    val bound = graph.bindTag.run(format, key, target)
                    actions.removeAllViews()
                    status.text = "Bound tag ${bound.id.value} to ${describe(target)}.\n\nScan it again to see it resolve."
                } catch (e: Exception) {
                    status.text = "Could not bind: ${e.message}"
                }
            }
        }
    }

    private fun addWriteAction(label: String) = addAction(label) {
        TargetPicker.show(this, graph, scope, allowNone = true) { target ->
            WriteTagActivity.start(this, target)
            finish()
        }
    }

    private fun addAction(label: String, onClick: () -> Unit) {
        actions.addView(Button(this).apply { text = label; setOnClickListener { onClick() } })
    }

    private fun describe(t: TagTarget): String = when (t) {
        is TagTarget.AssetTarget -> "asset ${t.assetId.value}"
        is TagTarget.LinkTarget -> "link ${t.linkId.value}"
        TagTarget.None -> "nothing"
    }
}
