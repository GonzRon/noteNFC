package com.loosecannon.servicetag.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.loosecannon.servicetag.ui.theme.ControlShape

/** One utility action of the 2×2 region in D12 §8. Logging verbs are outlined, navigation tonal. */
data class ActionSpec(
    val label: String,
    val icon: ImageVector,
    val outlined: Boolean,
    val onClick: () -> Unit,
)

/**
 * Outlined and tonal controls two to a row, not four colourful tiles (D12 §8). 44dp tall on the
 * 6dp control corner (G1 §1.1 "Quick actions"); this screen carries no FAB (G1 correction c).
 */
@Composable
fun ActionGrid(actions: List<ActionSpec>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { action -> ActionButton(action, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionButton(action: ActionSpec, modifier: Modifier) {
    val content: @Composable () -> Unit = {
        Icon(imageVector = action.icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = action.label, style = MaterialTheme.typography.labelLarge)
    }
    if (action.outlined) {
        OutlinedButton(
            onClick = action.onClick,
            shape = ControlShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            modifier = modifier.height(44.dp),
        ) { content() }
    } else {
        FilledTonalButton(
            onClick = action.onClick,
            shape = ControlShape,
            modifier = modifier.height(44.dp),
        ) { content() }
    }
}
