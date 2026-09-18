package com.loosecannon.servicetag.ui.scan

import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.ui.components.ServiceTagIcons
import com.loosecannon.servicetag.ui.components.QuietLine
import com.loosecannon.servicetag.ui.nav.Route
import com.loosecannon.servicetag.ui.nfc.ReaderMode
import com.loosecannon.servicetag.ui.nfc.TagSinkEffect
import com.loosecannon.servicetag.ui.theme.PlateShape
import com.loosecannon.servicetag.ui.theme.SheetSentence

/** The halo's full breath, in and out (D12 §11: "a very subtle slow breathing scale"). */
private const val BREATH_MILLIS = 3_200

/**
 * Foreground reader-mode scanning (D3 §9, G1 §1.4). The only `tertiaryContainer` surface in the
 * app sits here and the halo is the app's only animation; what a tag turns out to be is decided by
 * `ResolveTag` and shown on the result sheet.
 *
 * Reached as a pushed destination (Settings' Read / inspect tag row, or the dashboard's empty-state
 * action), never a tab (D12 §16 correction), so it always needs a way back.
 *
 * 2.7 (#37): the screen owns neither the reader-mode session — the nav shell holds one for the
 * whole tag flow — nor a route for its answer. The answer is drawn over this screen, so an inspect
 * never takes the screen out from under a tag that is still against the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    graph: AppGraph,
    readerMode: ReaderMode,
    onOpenAsset: (String) -> Unit,
    onNewAsset: () -> Unit,
    onWriteTag: (Route.WriteTag) -> Unit,
    onBack: () -> Unit,
) {
    val model: ScanViewModel = viewModel(key = "scan") { ScanViewModel(graph) }
    val state by model.state.collectAsStateWithLifecycle()

    // The activity owns the one session; this screen only says where its tags land while it is
    // resumed. Leaving it takes the sink away, and the nav shell decides about NFC itself.
    TagSinkEffect(readerMode) { tag -> model.onTag(tag) }

    // The answer, as the two strings the route used to carry — which is all it ever carried, and
    // which `rememberSaveable` can keep through process death without a `Saver` of its own.
    var format by rememberSaveable { mutableStateOf<String?>(null) }
    var key by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                is ScanEvent.Show -> { format = event.route.format; key = event.route.key }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Read / inspect tag") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReadyToScan(reading = state.reading)
            state.problem?.let { QuietLine(it) }
            NfcAvailability(readerMode)
        }
    }

    // Over this screen, not on top of it. Each way out clears the answer first, so coming back to
    // the inspector shows READY TO SCAN and not the answer to a tag that is long gone.
    format?.let { shown ->
        TagResultSheet(
            graph = graph,
            format = shown,
            key = key,
            onDismiss = { format = null },
            onWriteTag = { route -> format = null; onWriteTag(route) },
            onOpenAsset = { id -> format = null; onOpenAsset(id) },
            onNewAsset = { format = null; onNewAsset() },
        )
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
            imageVector = ServiceTagIcons.Contactless,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(64.dp),
        )
    }
}

/** What the phone can actually do, said once and quietly — never over the top of the card. */
@Composable
private fun NfcAvailability(readerMode: ReaderMode) {
    val line = when {
        !readerMode.present -> "Scanning needs the app's own window."
        !readerMode.available -> "This phone has no NFC hardware."
        !readerMode.enabled -> "NFC is turned off. Enable it in system settings, then come back."
        else -> null
    }
    line?.let { QuietLine(it) }
}
