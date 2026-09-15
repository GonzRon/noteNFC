package com.loosecannon.notenfc.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.loosecannon.notenfc.ui.theme.Eyebrow
import com.loosecannon.notenfc.ui.theme.MonoText

/**
 * The technical information-grid cell of D12 §8: a tiny all-caps label over a larger value.
 * Serials, tag ids and readings pass `mono = true`; a screen that needs the G1 §1.1 CURRENT
 * value size passes its own `valueStyle`.
 */
@Composable
fun LabelValue(
    label: String,
    value: String,
    mono: Boolean = false,
    valueStyle: TextStyle? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = Eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = valueStyle ?: if (mono) MonoText else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
