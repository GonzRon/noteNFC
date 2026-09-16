package com.loosecannon.servicetag

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.prefs.AppearanceMode
import com.loosecannon.servicetag.ui.share.ShareFlow
import com.loosecannon.servicetag.ui.theme.NoteNfcTheme

/**
 * "Share a note link to noteNFC" lands here and nowhere near the back stack (D12 §9): its own
 * task, excluded from recents, gone the moment the flow finishes. `EXTRA_TEXT` is read as a
 * `CharSequence` because that is what the contract promises; the styling is dropped, not trusted.
 */
class ShareActivity : ComponentActivity() {

    private val graph: AppGraph get() = (application as NoteNfcApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)
        setContent {
            val dark = when (graph.prefs.appearanceMode) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            NoteNfcTheme(darkTheme = dark) { ShareFlow(graph, shared, onFinished = { finish() }) }
        }
    }
}
