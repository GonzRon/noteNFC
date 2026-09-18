package com.loosecannon.servicetag.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A title over a hairline rule, the Apollo Service Binder alternative to a stack of cards
 * (D12 §7 "Cards"). 12sp SemiBold uppercase, 1dp `outlineVariant` rule, 18dp above / 6dp below
 * (G1 §1.1).
 */
@Composable
fun SectionHeader(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 18.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
