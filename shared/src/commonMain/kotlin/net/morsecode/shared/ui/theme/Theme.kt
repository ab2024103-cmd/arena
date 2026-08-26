package net.morsecode.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Brand: deep indigo/violet + teal accent (Section B palette)
val Indigo = androidx.compose.ui.graphics.Color(0xFF5B4BE8)
val IndigoDark = androidx.compose.ui.graphics.Color(0xFF3F33B8)
val IndigoLight = androidx.compose.ui.graphics.Color(0xFF7C6FF0)
val Teal = androidx.compose.ui.graphics.Color(0xFF35C4B5)
val TealLight = androidx.compose.ui.graphics.Color(0xFF5EEAD4)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE4DFFF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF17006B),
    secondary = Teal,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFC8F7F0),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF003733),
    background = androidx.compose.ui.graphics.Color(0xFFFDF8FF),
    surface = androidx.compose.ui.graphics.Color(0xFFFDF8FF),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE5E1EC),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF47464F),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF2400A6),
    primaryContainer = IndigoDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE4DFFF),
    secondary = Teal,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF003733),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF00504A),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFC8F7F0),
    background = androidx.compose.ui.graphics.Color(0xFF121218),
    surface = androidx.compose.ui.graphics.Color(0xFF121218),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2A2A33),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC8C5D0),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun MorseTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
