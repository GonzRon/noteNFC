package com.loosecannon.servicetag.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.loosecannon.servicetag.ui.theme.PlateShape
import com.loosecannon.servicetag.ui.theme.StatusColor

/**
 * The due/overdue block that sits under the identity plate (D12 §8, G1 §1.1). The words and the
 * glyph do most of the work; the semantic container only reinforces them. OVERDUE takes the 4dp
 * left rule (`leftRule = true`), DUE a full 1dp border.
 */
@Composable
fun StatusBlock(
    kind: StatusColor,
    headline: String,
    title: String,
    detail: String,
    icon: ImageVector,
    leftRule: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = kind.container,
        contentColor = kind.foreground,
        shape = PlateShape,
        border = if (leftRule) null else BorderStroke(1.dp, kind.foreground),
        modifier = modifier,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            if (leftRule) {
                Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(kind.foreground))
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(imageVector = icon, contentDescription = null, tint = kind.foreground, modifier = Modifier.size(16.dp))
                    Text(
                        text = headline.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = kind.foreground,
                    )
                }
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),  // G1 §1.1: detail 13sp
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
