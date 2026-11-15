package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = Emerald80,
    onPrimary = Emerald20,
    primaryContainer = Emerald30,
    onPrimaryContainer = Emerald90,
    secondary = Cyan80,
    onSecondary = Cyan20,
    secondaryContainer = Cyan30,
    onSecondaryContainer = Cyan90,
    tertiary = Indigo80,
    onTertiary = Indigo20,
    tertiaryContainer = Indigo30,
    onTertiaryContainer = Indigo90,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    background = Neutral05,
    onBackground = Neutral90,
    surface = Neutral15,
    onSurface = Neutral90,
    surfaceVariant = Neutral25,
    onSurfaceVariant = Neutral60,
    outline = Neutral40,
    outlineVariant = Neutral30
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald40,
    onPrimary = PureWhite,
    primaryContainer = Emerald90,
    onPrimaryContainer = Emerald10,
    secondary = Cyan40,
    onSecondary = PureWhite,
    secondaryContainer = Cyan90,
    onSecondaryContainer = Cyan10,
    tertiary = Indigo40,
    onTertiary = PureWhite,
    tertiaryContainer = Indigo90,
    onTertiaryContainer = Indigo10,
    error = Error40,
    onError = PureWhite,
    errorContainer = Error90,
    onErrorContainer = Error10,
    background = Neutral96,
    onBackground = Neutral10,
    surface = PureWhite,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral50,
    outline = Neutral60,
    outlineVariant = Neutral80
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
