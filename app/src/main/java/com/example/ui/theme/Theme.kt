package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = BepsEmerald,
    onPrimary = Color.White,
    primaryContainer = BepsEmeraldContainer,
    onPrimaryContainer = BepsOnEmeraldContainer,
    secondary = BepsGold,
    onSecondary = Color.White,
    secondaryContainer = BepsGoldContainer,
    onSecondaryContainer = BepsOnGoldContainer,
    tertiary = BepsTealInfo,
    onTertiary = Color.White,
    tertiaryContainer = BepsTealContainer,
    onTertiaryContainer = BepsInk,
    error = BepsCrimson,
    onError = Color.White,
    errorContainer = BepsCrimsonContainer,
    onErrorContainer = BepsOnCrimsonContainer,
    background = BepsBackgroundLight,
    onBackground = BepsInk,
    surface = BepsSurfaceLight,
    onSurface = BepsInk,
    surfaceVariant = BepsSurfaceVariantLight,
    onSurfaceVariant = BepsMutedInk,
    outline = BepsOutlineLight
)

private val DarkColorScheme = darkColorScheme(
    primary = BepsEmeraldLight,
    onPrimary = Color(0xFF003828),
    primaryContainer = BepsEmeraldContainerDark,
    onPrimaryContainer = BepsEmeraldContainer,
    secondary = BepsGoldLight,
    onSecondary = Color(0xFF3E2D00),
    secondaryContainer = BepsGoldContainerDark,
    onSecondaryContainer = BepsGoldContainer,
    tertiary = Color(0xFF7AD6DF),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF004F56),
    onTertiaryContainer = BepsTealContainer,
    error = BepsCrimsonLight,
    onError = Color(0xFF680010),
    errorContainer = BepsCrimsonContainerDark,
    onErrorContainer = BepsCrimsonContainer,
    background = BepsBackgroundDark,
    onBackground = BepsTextDark,
    surface = BepsSurfaceDark,
    onSurface = BepsTextDark,
    surfaceVariant = BepsSurfaceVariantDark,
    onSurfaceVariant = BepsMutedDark,
    outline = BepsOutlineDark
)

val BepsShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BepsConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BepsTypography,
        shapes = BepsShapes,
        content = content
    )
}
