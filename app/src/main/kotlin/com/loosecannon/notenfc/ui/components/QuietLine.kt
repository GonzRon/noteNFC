package com.loosecannon.notenfc.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The empty state of G1 §1.1: one quiet line, no red and no illustration —
 * "No schedule yet", "No entries yet · Log maintenance to start".
 */
@Composable
fun QuietLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
