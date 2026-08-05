package com.stevenroot.mabibliotheque.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark, poster-forward palette echoing the reference app's collection screens.
val Charcoal = Color(0xFF141517)
val CharcoalSurface = Color(0xFF1E2023)
val CharcoalSurfaceVariant = Color(0xFF2A2D31)
val Gold = Color(0xFFE8B94D)
val GoldDim = Color(0xFFB08A34)
val OnCharcoal = Color(0xFFEDEDED)
val OnCharcoalMuted = Color(0xFFA0A3A8)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1500),
    secondary = GoldDim,
    background = Charcoal,
    onBackground = OnCharcoal,
    surface = CharcoalSurface,
    onSurface = OnCharcoal,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = OnCharcoalMuted,
    error = Color(0xFFCF6679),
)

private val LightColors = lightColorScheme(
    primary = GoldDim,
    secondary = Gold,
)

@Composable
fun MaBibliothequeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
