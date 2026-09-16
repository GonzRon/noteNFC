package com.loosecannon.servicetag.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loosecannon.servicetag.ui.theme.Eyebrow
import com.loosecannon.servicetag.ui.theme.PlateShape

/** One cell of the plate's label/value grid. Blank text renders as "—" (G1 §1.1 "Empty asset"). */
data class PlateValue(val text: String, val mono: Boolean = false)

/**
 * The defining Asset Identity Plate of D12 §8: `surfaceContainerLow` behind a 1dp `outlineVariant`
 * outline, 8dp radius, 14dp padding. It reads as a plate because of the outline, not the fill, so
 * it is a bordered `Surface` and never an elevated card (D12 §7 "Borders", "Elevation").
 *
 * [cells] is any number of label/value pairs, laid out two to a row: four of them make the 2×2 of
 * G1 §1.1 and the six of spec §9 make a 2×3. [badges] is a slot rather than a list because an
 * asset can be several things at once (retired *and* out of season), and each badge is the caller's
 * own `StatusBadge` in its own family; they wrap under the title instead of crowding the eyebrow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityPlate(
    category: String,
    model: String,
    name: String?,
    cells: List<Pair<String, PlateValue>>,
    icon: ImageVector,
    badges: (@Composable FlowRowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = PlateShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = category.uppercase(),
                    // G1 §1.1: the plate's category eyebrow is SemiBold; ordinary metadata
                    // labels (LabelValue) keep the theme's Medium Eyebrow.
                    style = Eyebrow.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(text = model, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (name != null) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),  // G1 §1.1: friendly name 15sp
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // An asset that is retired, archived or out of season says so under its own name
            // (spec §9); an ordinary one shows nothing, because "normal" needs no badge.
            if (badges != null) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = badges,
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cells.chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (label, value) ->
                            val blank = value.text.isBlank()
                            LabelValue(
                                label = label,
                                value = if (blank) "—" else value.text,
                                mono = value.mono && !blank,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
