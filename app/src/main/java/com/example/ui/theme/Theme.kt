package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Slate950,
    primaryContainer = CyanPrimary.copy(alpha = 0.2f),
    onPrimaryContainer = CyanGlow,
    secondary = AmberGlow,
    onSecondary = Slate950,
    secondaryContainer = AmberWarning.copy(alpha = 0.2f),
    onSecondaryContainer = AmberGlow,
    tertiary = GreenGlow,
    onTertiary = Slate950,
    background = Slate950,
    onBackground = Slate100,
    surface = SurfaceDark,
    onSurface = Slate100,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = Slate200,
    error = RedOffline,
    onError = White,
    outline = BorderSubtle
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFFD97706),
    onSecondary = White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF334155),
    error = Color(0xFFDC2626),
    onError = White,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun NetAlertTheme(
    darkTheme: Boolean = true, // Default to deep high-visibility dark theme for security/monitor vibe
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
