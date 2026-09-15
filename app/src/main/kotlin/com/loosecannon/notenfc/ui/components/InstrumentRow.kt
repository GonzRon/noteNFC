package com.loosecannon.notenfc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loosecannon.notenfc.core.journal.RangeState
import com.loosecannon.notenfc.ui.journal.stateColors
import com.loosecannon.notenfc.ui.journal.stateIcon
import com.loosecannon.notenfc.ui.journal.stateLabel
import com.loosecannon.notenfc.ui.theme.MeasurementEntryText
import com.loosecannon.notenfc.ui.theme.NoteNfcTheme

/**
 * One line of the field test sheet of D12 §9: name and reference interval on the left, the value
 * large and monospaced in its own column, the state as a small marker under it. Deliberately not
 * a card and deliberately not a row filled orange — the word does the work, the colour only backs
 * it up (D12 §5). [InstrumentList] supplies the hairline between rows.
 *
 * [value] is already formatted; null means nothing has been logged for this definition yet, which
 * reads as an em dash and carries no state.
 */
@Composable
fun InstrumentRow(
    label: String,
    target: String,
    value: String?,
    unit: String,
    state: RangeState?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = target,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.width(VALUE_COLUMN),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = value ?: "—",
                style = MeasurementEntryText,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (unit.isNotBlank() && value != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.width(STATE_COLUMN), horizontalAlignment = Alignment.End) {
            if (state != null) {
                StatusBadge(
                    label = stateLabel(state),
                    colors = stateColors(state, NoteNfcTheme.semanticColors),
                    icon = stateIcon(state),
                )
            }
        }
    }
}

/** Stacks [InstrumentRow]s with the ledger's 1dp `outlineVariant` rule between them, none around. */
@Composable
fun InstrumentList(count: Int, modifier: Modifier = Modifier, row: @Composable (Int) -> Unit) {
    Column(modifier = modifier) {
        repeat(count) { index ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            row(index)
        }
    }
}

/** Wide enough for "-999.9" at the 22sp entry size, so the decimal points line up down the column. */
private val VALUE_COLUMN = 92.dp

/** "NO TARGET SET" wraps to two short lines here rather than stealing width from the label. */
private val STATE_COLUMN = 72.dp
