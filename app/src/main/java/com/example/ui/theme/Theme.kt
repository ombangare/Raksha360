package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ShieldBlue,
    onPrimary = Color.White,
    primaryContainer = ShieldBlueContainer,
    onPrimaryContainer = NavyDark,
    secondary = SafeTeal,
    onSecondary = Color.White,
    secondaryContainer = SafeGreenContainer,
    onSecondaryContainer = SafeGreen,
    tertiary = EvidenceOrange,
    onTertiary = Color.White,
    tertiaryContainer = EvidenceOrangeContainer,
    onTertiaryContainer = EvidenceOrange,
    error = EmergencyRed,
    onError = Color.White,
    errorContainer = EmergencyRedContainer,
    onErrorContainer = EmergencyRedDark,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondary,
    outline = OutlineBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = ShieldBlueLight,
    onPrimary = NavyDark,
    primaryContainer = NavyMuted,
    onPrimaryContainer = Color.White,
    secondary = SafeGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF132A1D),
    onSecondaryContainer = SafeGreen,
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3E2723),
    onTertiaryContainer = Color(0xFFFFCC80),
    error = Color(0xFFEF5350),
    onError = Color.White,
    errorContainer = Color(0xFF491212),
    onErrorContainer = Color(0xFFFFCDD2),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569)
)

@Composable
fun NaariKavachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // We intentionally maintain crisp brand identity with controlled colors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NaariKavachTheme(darkTheme = darkTheme, content = content)
}
