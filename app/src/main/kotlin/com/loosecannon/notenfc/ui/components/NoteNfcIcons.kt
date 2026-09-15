package com.loosecannon.notenfc.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.loosecannon.notenfc.R

/**
 * The glyphs D12 §5/§8 asks for that `material-icons-core` does not ship. Screens name the icon,
 * never the drawable id; the tint stays the caller's decision.
 */
object NoteNfcIcons {
    val Contactless: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_contactless)
    val NfcTag: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_nfc_tag)
    val History: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_history)
    val Description: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_description)
    val Schedule: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_schedule)
    val Event: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_event)
    val CalendarMonth: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_calendar_month)
    val ArrowUpward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_upward)
    val ArrowDownward: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_arrow_downward)
    val PauseCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pause_circle)
    val NotificationsActive: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_notifications_active)
    val NotificationsOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_notifications_off)
    val CloudOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_cloud_off)
    val DeleteForever: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_delete_forever)
    val Speed: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_speed)
    val Backup: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_backup)

    /** An equals sign: the kind glyph on a reading that is computed rather than entered. */
    val Equal: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_equal)
}
