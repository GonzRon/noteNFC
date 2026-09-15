package com.loosecannon.notenfc.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Roles from D12 §6. Character comes from hierarchy, spacing and mono numerals, not a display face. */
val NoteNfcTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),              // asset name / screen title
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 26.sp),                 // plate model
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),                // manufacturer/model, event title
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),                 // metadata value
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),                  // timestamps
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),                 // buttons
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp), // section title (uppercase applied by SectionHeader)
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),   // metadata label / badge
)

/** Serials, tag ids, readings: monospace with tabular numerals (D12 §6 "Technical typeface"). */
val MonoText = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
val MeasurementEntryText = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 22.sp, fontFeatureSettings = "tnum")
val MeasurementHeroText = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 30.sp, fontFeatureSettings = "tnum")

/** Small all-caps label above a group (D12 §6). Uppercasing is applied by the caller. */
val Eyebrow = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp)
