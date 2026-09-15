package com.loosecannon.notenfc.ui.scan

import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.links.LinkLauncher
import com.loosecannon.notenfc.nfc.NfcReaderModeSession
import com.loosecannon.notenfc.ui.components.NoteNfcIcons
import com.loosecannon.notenfc.ui.components.QuietLine
import com.loosecannon.notenfc.ui.nav.Route
import com.loosecannon.notenfc.ui.theme.PlateShape
import com.loosecannon.notenfc.ui.theme.SheetSentence

/** The halo's full breath, in and out (D12 §11: "a very subtle slow breathing scale"). */
private const val BREATH_MILLIS = 3_200

/**
 * Foreground reader-mode scanning (D3 §9, G1 §1.4). The only `tertiaryContainer` surface in the
 * app sits here and the halo is the app's only animation; what a tag turns out to be is decided by
 * `ResolveTag` and shown on the result sheet — except a link tag, which launches its note with no
 * sheet at all (R-7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    graph: AppGraph,
    onResolved: (Route) -> Unit,
) {
    val model: ScanViewModel = viewModel(key = "scan") { ScanViewModel(graph) }
    val state by model.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    val session = remember(activity) {
        activity?.let { host -> NfcReaderModeSession(host) { tag -> model.onTag(NfcTagHandle(tag)) } }
    }
    // Reader mode belongs to the resumed screen and to nothing else: leaving this screen hands NFC
    // back to the system, which is what lets the background trampoline keep working.
    LifecycleResumeEffect(session) {
        session?.start()
        onPauseOrDispose { session?.stop() }
    }

    LaunchedEffect(model, activity) {
        model.events.collect { event ->
            when (event) {
                is ScanEvent.Show -> onResolved(event.route)
                is ScanEvent.Launch -> activity?.let { LinkLauncher.open(it, event.uri) }
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Scan") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReadyToScan(reading = state.reading)
            state.problem?.let { QuietLine(it) }
            NfcAvailability(session)
        }
    }
}

/** "READY TO SCAN": `tertiaryContainer`, the contactless glyph in a breathing halo, one sentence. */
@Composable
private fun ReadyToScan(reading: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = PlateShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
        ) {
            Halo()
            Text(
                text = if (reading) "READING TAG" else "READY TO SCAN",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Hold the top of your phone near the equipment tag.",
                style = SheetSentence,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A 64dp glyph inside a 96dp halo at 18% tertiary, breathing over 3.2 s. The breath stops when the
 * phone's animator scale is zero, which is how "reduce motion" reaches an app on Android.
 */
@Composable
private fun Halo() {
    val context = LocalContext.current
    val animate = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val scale = if (animate) {
        val breath = rememberInfiniteTransition(label = "halo")
        val animated by breath.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(BREATH_MILLIS), RepeatMode.Reverse),
            label = "halo-scale",
        )
        animated
    } else {
        1f
    }
    Box(contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
            shape = CircleShape,
            modifier = Modifier.size(96.dp).scale(scale),
            content = {},
        )
        Icon(
            imageVector = NoteNfcIcons.Contactless,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(64.dp),
        )
    }
}

/** What the phone can actually do, said once and quietly — never over the top of the card. */
@Composable
private fun NfcAvailability(session: NfcReaderModeSession?) {
    val line = when {
        session == null -> "Scanning needs the app's own window."
        !session.available -> "This phone has no NFC hardware."
        !session.enabled -> "NFC is turned off. Enable it in system settings, then come back."
        else -> null
    }
    line?.let { QuietLine(it) }
}
