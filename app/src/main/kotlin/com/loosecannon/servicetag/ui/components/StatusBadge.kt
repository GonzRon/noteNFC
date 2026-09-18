package com.loosecannon.servicetag.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loosecannon.servicetag.ui.theme.BadgeShape
import com.loosecannon.servicetag.ui.theme.StatusColor

/**
 * A mildly rounded rectangle, not a giant pill (D12 §7 "Chips"). Every operational state carries
 * wording and a glyph as well as colour (D12 §5), so the label stays in the tree for TalkBack —
 * `clearAndSetSemantics` is deliberately not used here.
 */
@Composable
fun StatusBadge(
    label: String,
    colors: StatusColor,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(color = colors.container, contentColor = colors.foreground, shape = BadgeShape, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(horizontal = 7.dp, vertical = 2.dp)
                .semantics { contentDescription = label },
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = colors.foreground, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.foreground,
            )
        }
    }
}
