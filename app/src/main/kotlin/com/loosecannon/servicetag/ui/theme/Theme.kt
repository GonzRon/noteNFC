package com.loosecannon.servicetag.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

object ServiceTagTheme {
    val semanticColors: ServiceTagSemanticColors
        @Composable @ReadOnlyComposable get() = LocalServiceTagSemanticColors.current
}

/**
 * Two layers (D12 §15): Material answers "how do generic components look", the semantic layer
 * answers "what does this operational state mean". Dynamic colour may replace the first, never
 * the second (D12 §13). The setting that turns it on is Phase 7; the parameter exists now so the
 * rule is enforced from the first screen.
 */
@Composable
fun ServiceTagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val materialColors = resolveServiceTagColorScheme(darkTheme, dynamicColor)
    val semanticColors = if (darkTheme) ServiceTagDarkSemanticColors else ServiceTagLightSemanticColors
    CompositionLocalProvider(LocalServiceTagSemanticColors provides semanticColors) {
        MaterialTheme(colorScheme = materialColors, typography = ServiceTagTypography, shapes = ServiceTagShapes, content = content)
    }
}

@Composable
private fun resolveServiceTagColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return if (darkTheme) DarkColorScheme else LightColorScheme
}
