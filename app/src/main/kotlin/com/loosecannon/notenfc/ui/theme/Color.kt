package com.loosecannon.notenfc.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Foundational palette (D12 §2) — named so previews and tests can refer to them.
val Navy900 = Color(0xFF102F4A); val Navy700 = Color(0xFF1F4E78); val Navy600 = Color(0xFF2F648E); val Navy200 = Color(0xFFA9CBE5); val Navy100 = Color(0xFFD8E7F2)
val Steel900 = Color(0xFF25313A); val Steel700 = Color(0xFF52606A); val Steel300 = Color(0xFFB9C6CE); val Steel100 = Color(0xFFDDE3E7)
val Signal900 = Color(0xFF173A4A); val Signal700 = Color(0xFF336B87); val Signal300 = Color(0xFFA4C6D5); val Signal100 = Color(0xFFD7EAF1)
val Brick600 = Color(0xFFA43D36); val Brick300 = Color(0xFFF2B8B5); val Brick100 = Color(0xFFF8DAD6)
val Carbon = Color(0xFF10171D); val Ink = Color(0xFF1B1F22); val WarmIvory = Color(0xFFF7F5EF); val PaperWhite = Color(0xFFFCFAF5)

val LightColorScheme = lightColorScheme(
    primary = Navy700, onPrimary = Color.White, primaryContainer = Navy100, onPrimaryContainer = Navy900,
    secondary = Steel700, onSecondary = Color.White, secondaryContainer = Steel100, onSecondaryContainer = Steel900,
    tertiary = Signal700, onTertiary = Color.White, tertiaryContainer = Signal100, onTertiaryContainer = Signal900,
    error = Brick600, onError = Color.White, errorContainer = Brick100, onErrorContainer = Color(0xFF3B0807),
    background = WarmIvory, onBackground = Ink, surface = PaperWhite, onSurface = Ink,
    surfaceVariant = Color(0xFFE2E5E6), onSurfaceVariant = Color(0xFF444B50),
    surfaceDim = Color(0xFFD7D8D3), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F2EC), surfaceContainer = Color(0xFFECEBE5),
    surfaceContainerHigh = Color(0xFFE6E5DF), surfaceContainerHighest = Color(0xFFDFE0DB),
    outline = Color(0xFF737B80), outlineVariant = Color(0xFFC1C7CA),
    inverseSurface = Color(0xFF2E3438), inverseOnSurface = Color(0xFFF4F2EC), inversePrimary = Navy200,
    surfaceTint = Navy700, scrim = Color.Black,
)

val DarkColorScheme = darkColorScheme(
    primary = Navy200, onPrimary = Color(0xFF0A3452), primaryContainer = Color(0xFF214E70), onPrimaryContainer = Color(0xFFD7E9F6),
    secondary = Steel300, onSecondary = Color(0xFF243139), secondaryContainer = Color(0xFF394750), onSecondaryContainer = Color(0xFFDDE6EB),
    tertiary = Signal300, onTertiary = Signal900, tertiaryContainer = Color(0xFF2A5267), onTertiaryContainer = Signal100,
    error = Brick300, onError = Color(0xFF601410), errorContainer = Color(0xFF8C2E2A), onErrorContainer = Color(0xFFFFDAD7),
    background = Carbon, onBackground = Color(0xFFE5E7E8), surface = Color(0xFF151D23), onSurface = Color(0xFFE5E7E8),
    surfaceVariant = Color(0xFF3D474E), onSurfaceVariant = Color(0xFFB9C3C9), // G1 correction h
    surfaceDim = Carbon, surfaceBright = Color(0xFF353F46),
    surfaceContainerLowest = Color(0xFF0C1217), surfaceContainerLow = Color(0xFF151D23), surfaceContainer = Color(0xFF1A2229),
    surfaceContainerHigh = Color(0xFF202A32), surfaceContainerHighest = Color(0xFF27323A),
    outline = Color(0xFF8D979E), outlineVariant = Color(0xFF3F4A51),
    inverseSurface = Color(0xFFE5E7E8), inverseOnSurface = Color(0xFF2C3134), inversePrimary = Navy700,
    surfaceTint = Navy200, scrim = Color.Black,
)
