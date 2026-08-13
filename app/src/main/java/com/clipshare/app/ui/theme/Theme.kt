package com.clipshare.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5EAA),
    secondary = Color(0xFF5576B8),
    tertiary = Color(0xFF14808A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BBBFF),
    secondary = Color(0xFFAFC6FF),
    tertiary = Color(0xFF6FD2DD),
)

@Composable
fun ClipShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
