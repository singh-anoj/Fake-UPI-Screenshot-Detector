package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color
)

private val DarkSemantic = SemanticColors(
    success = Color(0xFF00C9A0),
    warning = Color(0xFFFFC857),
    danger = Color(0xFFFF647C)
)

private val LightSemantic = SemanticColors(
    success = Color(0xFF00855F),
    warning = Color(0xFFB57A00),
    danger = Color(0xFFD0304D)
)

val LocalSemanticColors = staticCompositionLocalOf { DarkSemantic }

val MaterialTheme.semantic: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF00252E),
    primaryContainer = Color(0xFF004F5E),
    onPrimaryContainer = Color(0xFFB8F0FF),
    secondary = Color(0xFF00C9A0),
    onSecondary = Color(0xFF00201A),
    background = DarkBackground,
    onBackground = Color(0xFFEAF6FF),
    surface = DarkSurface,
    onSurface = Color(0xFFEAF6FF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = Color(0xFF3A5573),
    outlineVariant = Color(0xFF223B57),
    error = Color(0xFFFF647C),
    onError = Color(0xFF3B0010)
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFEFFB),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF00855F),
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF0B1B2B),
    surface = LightSurface,
    onSurface = Color(0xFF0B1B2B),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = Color(0xFF7C93A8),
    outlineVariant = Color(0xFFD0DCE8),
    error = Color(0xFFD0304D),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSemanticColors provides if (darkTheme) DarkSemantic else LightSemantic
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content
        )
    }
}
