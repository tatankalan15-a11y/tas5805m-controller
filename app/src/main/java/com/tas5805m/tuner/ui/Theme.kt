package com.tas5805m.tuner.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tas5805m.tuner.data.ThemeMode

private val Dark = darkColorScheme(
    primary = Color(0xFF4DD0E1),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFA6EEFF),
    secondary = Color(0xFF80F0A8),
    onSecondary = Color(0xFF003919),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE3E8F2),
    surface = Color(0xFF0B1220),
    onSurface = Color(0xFFE3E8F2),
    surfaceVariant = Color(0xFF1F2A3D),
    onSurfaceVariant = Color(0xFFB4BED0),
    surfaceContainerLowest = Color(0xFF070C16),
    surfaceContainerLow = Color(0xFF0F1727),
    surfaceContainer = Color(0xFF131C2F),
    surfaceContainerHigh = Color(0xFF18233A),
    surfaceContainerHighest = Color(0xFF223049),
    outline = Color(0xFF6B7890),
    outlineVariant = Color(0xFF2C3A52),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6EEFF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF1B6B3A),
    tertiary = Color(0xFF8A5100),
    background = Color(0xFFF5F8FC),
    surface = Color(0xFFF5F8FC),
    surfaceVariant = Color(0xFFDDE4EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF3F9),
    surfaceContainer = Color(0xFFE9EEF5),
    surfaceContainerHigh = Color(0xFFE2E8F1),
    surfaceContainerHighest = Color(0xFFD8DFEA),
)

@Composable
fun TunerTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

val OkGreen = Color(0xFF4CD97B)
val WarnAmber = Color(0xFFFFB74D)
val BadRed = Color(0xFFFF6B6B)
