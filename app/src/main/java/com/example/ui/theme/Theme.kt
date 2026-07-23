package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val GeometricLightColorScheme = lightColorScheme(
    primary = OceanPrimary,
    onPrimary = OnOceanPrimary,
    primaryContainer = OceanPrimaryContainer,
    onPrimaryContainer = OnOceanPrimaryContainer,
    background = GeometricBackground,
    onBackground = OnGeometricSurface,
    surface = GeometricSurface,
    onSurface = OnGeometricSurface,
    surfaceVariant = GeometricSurface,
    onSurfaceVariant = TextMuted,
    outline = GeometricCardBorder
)

private val GeometricDarkColorScheme = darkColorScheme(
    primary = OceanPrimaryContainer,
    onPrimary = OnOceanPrimaryContainer,
    primaryContainer = OceanPrimary,
    onPrimaryContainer = OnOceanPrimary,
    background = OnGeometricSurface,
    onBackground = GeometricBackground,
    surface = TextMuted,
    onSurface = GeometricBackground,
    surfaceVariant = TextMuted,
    onSurfaceVariant = GeometricBackground,
    outline = OceanPrimary
)

@Composable
fun WaterReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set false to prioritize Geometric Balance theme
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GeometricDarkColorScheme
        else -> GeometricLightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

