package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = OnEmeraldPrimaryDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = CyanSecondaryDark,
    onSecondary = OnCyanSecondaryDark,
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = OnCyanContainerDark,
    tertiary = SlateTertiaryDark,
    onTertiary = OnSlateTertiaryDark,
    tertiaryContainer = SlateContainerDark,
    onTertiaryContainer = OnSlateContainerDark,
    background = NavyBackgroundDark,
    onBackground = OnNavyBackgroundDark,
    surface = NavySurfaceDark,
    onSurface = OnNavySurfaceDark,
    surfaceVariant = NavySurfaceVariantDark,
    onSurfaceVariant = OnNavySurfaceVariantDark,
    outline = NavyOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = OnEmeraldPrimaryLight,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    secondary = CyanSecondaryLight,
    onSecondary = OnCyanSecondaryLight,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = OnCyanContainerLight,
    tertiary = SlateTertiaryLight,
    onTertiary = OnSlateTertiaryLight,
    tertiaryContainer = SlateContainerLight,
    onTertiaryContainer = OnSlateContainerLight,
    background = SlateBackgroundLight,
    onBackground = OnSlateBackgroundLight,
    surface = SlateSurfaceLight,
    onSurface = OnSlateSurfaceLight,
    surfaceVariant = SlateSurfaceVariantLight,
    onSurfaceVariant = OnSlateSurfaceVariantLight,
    outline = SlateOutlineLight
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
        content = content
    )
}
