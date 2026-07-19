package com.tenanatc.cocktailmaker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFCC7722)
private val AmberDeep = Color(0xFF8C4F12)
private val Lime = Color(0xFF6B8E23)
private val Cream = Color(0xFFFFF8EE)
private val Charcoal = Color(0xFF221A12)

private val LightColors = lightColorScheme(
    primary = Amber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCBC),
    onPrimaryContainer = Color(0xFF3A2100),
    secondary = Lime,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EFC5),
    onSecondaryContainer = Color(0xFF1C2A00),
    background = Cream,
    onBackground = Charcoal,
    surface = Cream,
    onSurface = Charcoal,
    surfaceVariant = Color(0xFFF2E4D4),
    onSurfaceVariant = Color(0xFF52443A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB877),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = AmberDeep,
    onPrimaryContainer = Color(0xFFFFDCBC),
    secondary = Color(0xFFC7DB8A),
    onSecondary = Color(0xFF2F3B00),
    secondaryContainer = Color(0xFF44521A),
    onSecondaryContainer = Color(0xFFE3EFC5),
    background = Color(0xFF1A130C),
    onBackground = Color(0xFFEFE0D0),
    surface = Color(0xFF1A130C),
    onSurface = Color(0xFFEFE0D0),
    surfaceVariant = Color(0xFF3B3025),
    onSurfaceVariant = Color(0xFFD8C6B4),
)

@Composable
fun CocktailMakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
