package com.gameforge.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark arcade-inspired palette
private val DarkColors = darkColorScheme(
    primary = Color(0xFFe94560),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF533483),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFF533483),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A1A2E),
    onSecondaryContainer = Color(0xFFA0A0B0),
    tertiary = Color(0xFF0F3460),
    background = Color(0xFF0A0A1A),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF16213E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1A1A2E),
    onSurfaceVariant = Color(0xFFA0A0B0),
    error = Color(0xFFF44336),
    onError = Color.White,
)

@Composable
fun GameForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content,
    )
}