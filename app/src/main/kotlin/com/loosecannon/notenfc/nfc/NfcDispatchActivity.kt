package com.loosecannon.notenfc.nfc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.widget.Toast
import com.loosecannon.notenfc.MainActivity
import com.loosecannon.notenfc.NoteNfcApp
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.core.nfc.TagRoute
import com.loosecannon.notenfc.core.usecase.OpenLink
import com.loosecannon.notenfc.core.usecase.Resolution
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.links.LinkLauncher
import com.loosecannon.notenfc.ui.scan.FORMAT_NONE
import com.loosecannon.notenfc.ui.scan.asTagResult
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The one NFC-exported component (D3 §9, security doc "NFC dispatch"). Background scans arrive
 * here through the two `NDEF_DISCOVERED` filters; only `EXTRA_NDEF_MESSAGES`, `EXTRA_TAG` and the
 * data URI are read — every other extra is ignored.
 *
 * It has no UI at all: a link tag launches straight away (R-7) and everything else is handed to
 * `MainActivity` as a (format, key) pair, so the single activity owns every pixel the app draws.
 * The translucent theme is what keeps a window from flashing on the way through.
 */
class NfcDispatchActivity : Activity() {

    private val scope = MainScope()
    private val graph: AppGraph get() = (application as NoteNfcApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun handle(intent: Intent) {
        // A third-party app can aim any extras at an exported activity; on pre-33 devices the
        // untyped `getParcelableArrayExtra` unparcels whatever it is handed, so a hostile or simply
        // wrong bundle throws here rather than returning null. Treat it as "nothing to resolve".
        val payload = try {
            payloadOf(intent)
        } catch (e: Exception) {
            null
        }
        if (payload == null) {
            Toast.makeText(this, "Nothing to resolve.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        scope.launch {
            val resolution = try {
                graph.resolveTag.run(payload)
            } catch (e: Exception) {
                handOff(FORMAT_NONE, "could not resolve this tag: ${e.javaClass.simpleName}")
                return@launch
            }
            route(resolution)
        }
    }

    private fun payloadOf(intent: Intent): TagPayload? = when (intent.action) {
        NfcAdapter.ACTION_NDEF_DISCOVERED -> NdefCodec.decode(intent.ndefRecords().orEmpty())
        Intent.ACTION_VIEW -> tagRoute(intent.data)
        else -> null
    }

    private fun tagRoute(uri: Uri?): TagPayload? =
        TagRoute.parse(uri?.scheme, uri?.host, uri?.pathSegments.orEmpty())

    /**
     * A link tag launches its note here and now (R-7); everything else becomes the very route the
     * foreground scanner would have produced, so the two paths say the same words about a tag.
     */
    private fun route(r: Resolution) = when (r) {
        is Resolution.LaunchLink -> launch(r.link)
        else -> r.asTagResult().let { handOff(it.format, it.key) }
    }

    private fun launch(link: ExternalLink) {
        scope.launch {
            try {
                when (val out = graph.openLink.run(link.id)) {
                    is OpenLink.Outcome.Launch -> { LinkLauncher.open(this@NfcDispatchActivity, out.uri); finish() }
                    is OpenLink.Outcome.Refused -> handOff(FORMAT_NONE, "link refused: ${out.reason}")
                    is OpenLink.Outcome.Missing -> handOff(FORMAT_NONE, "the link this tag pointed at no longer exists")
                }
            } catch (e: Exception) {
                handOff(FORMAT_NONE, "could not open the link: ${e.javaClass.simpleName}")
            }
        }
    }

    /** The trampoline's only exit: the single activity renders the result, this one never does. */
    private fun handOff(format: String, key: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAG_FORMAT, format)
                .putExtra(MainActivity.EXTRA_TAG_KEY, key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
